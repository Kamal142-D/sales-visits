package com.sales.visits

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Simple synchronous persistence backed by SharedPreferences with a JSON blob.
 * Exposes Compose-observable state so screens recompose on change.
 */
class Store(context: Context) {
    private val appContext = context.applicationContext
    private val sp = appContext.getSharedPreferences("sales_visits", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val prettyJson = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var applyingCloud = false
    internal var onDataChanged: (() -> Unit)? = null

    var visits by mutableStateOf(load())
        private set

    var theme by mutableStateOf(sp.getString("theme", "auto") ?: "auto")
        private set

    var palette by mutableStateOf(sp.getString("palette", "classic") ?: "classic")
        private set

    var langMode by mutableStateOf(sp.getString("lang", "auto") ?: "auto")
        private set

    // AI note formatting (bring-your-own-key). Empty key = feature disabled.
    var apiKey by mutableStateOf(sp.getString("ai_key", "") ?: "")
        private set

    var aiModel by mutableStateOf(sp.getString("ai_model", "gemini-2.5-flash") ?: "gemini-2.5-flash")
        private set

    var lang by mutableStateOf(resolveLanguage(langMode))
        private set

    var customers by mutableStateOf(loadCustomers())
        private set

    init {
        I18n.en = (lang == "en")
        migrateCustomersFromVisits()
        ReminderScheduler.reschedule(appContext, visits)
    }

    fun chooseLang(l: String) {
        langMode = l
        lang = resolveLanguage(l)
        I18n.en = (lang == "en")
        sp.edit().putString("lang", l).apply()
    }

    private fun resolveLanguage(mode: String): String {
        if (mode == "ar" || mode == "en") return mode
        val deviceLanguage = appContext.resources.configuration.locales[0]?.language.orEmpty()
        return if (deviceLanguage.equals("ar", ignoreCase = true)) "ar" else "en"
    }

    private fun load(): List<Visit> =
        runCatching { json.decodeFromString<List<Visit>>(sp.getString("visits", "[]") ?: "[]") }
            .getOrDefault(emptyList())

    private fun persist() {
        sp.edit().putString("visits", json.encodeToString(visits)).apply()
        if (!applyingCloud) onDataChanged?.invoke()
    }

    fun upsert(v: Visit) {
        visits = if (visits.any { it.id == v.id }) visits.map { if (it.id == v.id) v else it }
        else visits + v
        persist()
        syncCustomerFromVisit(v)
        ReminderScheduler.schedule(appContext, v)
    }

    fun delete(id: String) {
        visits = visits.filter { it.id != id }
        persist()
        ReminderScheduler.cancel(appContext, id)
    }

    fun clearAll() {
        visits.forEach { ReminderScheduler.cancel(appContext, it.id) }
        visits = emptyList()
        persist()
    }

    // ---- Customers ----

    private fun customerKey(name: String): String = name.trim().lowercase()

    private fun loadCustomers(): List<Customer> =
        runCatching { json.decodeFromString<List<Customer>>(sp.getString("customers", "[]") ?: "[]") }
            .getOrDefault(emptyList())

    private fun persistCustomers() {
        sp.edit().putString("customers", json.encodeToString(customers)).apply()
        if (!applyingCloud) onDataChanged?.invoke()
    }

    private fun migrateCustomersFromVisits() {
        if (visits.isEmpty()) return
        var changed = false
        val byName = customers.associateBy { customerKey(it.name) }.toMutableMap()
        visits.sortedBy { it.date + it.time }.forEach { v ->
            if (v.client.isBlank()) return@forEach
            val key = customerKey(v.client)
            val old = byName[key]
            val next = if (old == null) {
                changed = true
                Customer(
                    id = customerId(), name = v.client.trim(), contact = v.contact,
                    phone = v.phone, address = v.address, createdAt = v.date,
                )
            } else {
                val updated = old.copy(
                    contact = v.contact.ifBlank { old.contact },
                    phone = v.phone.ifBlank { old.phone },
                    address = v.address.ifBlank { old.address },
                )
                if (updated != old) changed = true
                updated
            }
            byName[key] = next
        }
        if (changed) {
            customers = byName.values.sortedBy { it.name.lowercase() }
            persistCustomers()
        }
    }

    private fun syncCustomerFromVisit(v: Visit) {
        if (v.client.isBlank()) return
        val key = customerKey(v.client)
        val old = customers.firstOrNull { customerKey(it.name) == key }
        val customer = old?.copy(
            contact = v.contact.ifBlank { old.contact },
            phone = v.phone.ifBlank { old.phone },
            address = v.address.ifBlank { old.address },
        ) ?: Customer(
            id = customerId(), name = v.client.trim(), contact = v.contact,
            phone = v.phone, address = v.address, createdAt = v.date,
        )
        upsertCustomer(customer)
    }

    private fun customerId(): String =
        System.currentTimeMillis().toString(36) + (1000..9999).random().toString(36)

    fun upsertCustomer(customer: Customer) {
        val clean = customer.copy(
            name = customer.name.trim(), contact = customer.contact.trim(),
            phone = customer.phone.trim(), address = customer.address.trim(), notes = customer.notes.trim(),
            createdAt = customer.createdAt.ifBlank { todayIso() },
        )
        if (clean.name.isBlank()) return
        val original = customers.firstOrNull { it.id == clean.id }
        val sameName = customers.firstOrNull {
            it.id != clean.id && customerKey(it.name) == customerKey(clean.name)
        }
        val merged = if (sameName == null) clean else sameName.copy(
            name = clean.name,
            contact = clean.contact.ifBlank { sameName.contact },
            phone = clean.phone.ifBlank { sameName.phone },
            address = clean.address.ifBlank { sameName.address },
            notes = clean.notes.ifBlank { sameName.notes },
        )
        if (original != null && customerKey(original.name) != customerKey(clean.name)) {
            visits = visits.map { v ->
                if (customerKey(v.client) == customerKey(original.name)) v.copy(client = clean.name) else v
            }
            plan = plan.map { item ->
                if (customerKey(item.client) == customerKey(original.name)) item.copy(client = clean.name) else item
            }
            persist()
            persistPlan()
            visits.filter { customerKey(it.client) == customerKey(clean.name) }
                .forEach { ReminderScheduler.schedule(appContext, it) }
        }
        customers = customers.filter { it.id != clean.id && it.id != sameName?.id } + merged
        customers = customers.sortedBy { it.name.lowercase() }
        persistCustomers()
    }

    fun deleteCustomer(id: String) {
        customers = customers.filter { it.id != id }
        persistCustomers()
    }

    fun visitsFor(customer: Customer): List<Visit> =
        visits.filter { customerKey(it.client) == customerKey(customer.name) }
            .sortedByDescending { it.date + it.time }

    fun customerFor(name: String): Customer? =
        customers.firstOrNull { customerKey(it.name) == customerKey(name) }

    fun dueFollowUps(): List<Visit> = visits
        .filter { it.next.isNotBlank() && it.nextDate.isNotBlank() && it.nextDate <= todayIso() }
        .sortedBy { it.nextDate }

    // ---- Portable backup ----

    fun exportBackup(): String = prettyJson.encodeToString(
        AppBackup(
            exportedAt = java.time.Instant.now().toString(), visits = visits,
            customers = customers, plan = plan,
        )
    )

    internal fun cloudSnapshot(): String = json.encodeToString(
        AppBackup(exportedAt = "", visits = visits, customers = customers, plan = plan)
    )

    internal fun mergeCloudSnapshot(raw: String): String? = runCatching {
        val remote = json.decodeFromString<AppBackup>(raw)
        require(remote.version == 1)
        json.encodeToString(
            mergeBackups(
                AppBackup(exportedAt = "", visits = visits, customers = customers, plan = plan),
                remote,
            )
        )
    }.getOrNull()

    internal fun restoreCloudSnapshot(raw: String): Boolean {
        applyingCloud = true
        return try { restoreBackup(raw) } finally { applyingCloud = false }
    }

    fun restoreBackup(raw: String): Boolean = runCatching {
        val backup = json.decodeFromString<AppBackup>(raw)
        require(backup.version == 1)
        visits.forEach { ReminderScheduler.cancel(appContext, it.id) }
        visits = backup.visits
        customers = backup.customers
        plan = backup.plan
        persist()
        persistCustomers()
        persistPlan()
        ReminderScheduler.reschedule(appContext, visits)
        true
    }.getOrDefault(false)

    // ---- Today plan (roadmap) ----
    var plan by mutableStateOf(loadPlan())
        private set

    private fun loadPlan(): List<PlanItem> =
        runCatching { json.decodeFromString<List<PlanItem>>(sp.getString("plan", "[]") ?: "[]") }
            .getOrDefault(emptyList())

    private fun persistPlan() {
        sp.edit().putString("plan", json.encodeToString(plan)).apply()
        if (!applyingCloud) onDataChanged?.invoke()
    }

    private fun pid(): String =
        System.currentTimeMillis().toString(36) + (1000..9999).random().toString(36)

    fun planFor(date: String): List<PlanItem> = plan.filter { it.date == date }

    fun addPlan(client: String, date: String = todayIso()) {
        if (client.isBlank()) return
        plan = plan + PlanItem(pid(), client.trim(), date)
        persistPlan()
    }

    fun togglePlan(id: String) {
        plan = plan.map { if (it.id == id) it.copy(done = !it.done) else it }
        persistPlan()
    }

    fun deletePlan(id: String) {
        plan = plan.filter { it.id != id }
        persistPlan()
    }

    fun chooseTheme(t: String) {
        theme = t
        sp.edit().putString("theme", t).apply()
    }

    fun choosePalette(value: String) {
        palette = value
        sp.edit().putString("palette", value).apply()
    }

    fun chooseApiKey(value: String) {
        apiKey = value.trim()
        sp.edit().putString("ai_key", apiKey).apply()
    }

    fun chooseAiModel(value: String) {
        aiModel = value.trim().ifBlank { "gemini-2.5-flash" }
        sp.edit().putString("ai_model", aiModel).apply()
    }

    // Runs past the editor's lifetime so formatting finishes even after the screen closes.
    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Formats a visit's raw note into both languages in the background, then saves the result. */
    fun autoFormat(visitId: String, rawNote: String) {
        if (apiKey.isBlank() || rawNote.isBlank()) return
        aiScope.launch {
            val r = runCatching { AiFormatter.format(apiKey, aiModel, rawNote) }.getOrNull() ?: return@launch
            val current = visits.firstOrNull { it.id == visitId } ?: return@launch
            if (r.ar.isNotBlank() || r.en.isNotBlank()) upsert(current.copy(notesAr = r.ar, notesEn = r.en))
        }
    }

    fun countThisWeek(): Int = visits.count { inWeek(it.date, 0) }
}
