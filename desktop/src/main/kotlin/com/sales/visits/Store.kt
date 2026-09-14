package com.sales.visits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Properties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Everything serialized to data.json. */
@Serializable
private data class StoredData(
    val visits: List<Visit> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val plan: List<PlanItem> = emptyList(),
    // Round-tripped so PC↔phone sync never drops data the desktop UI doesn't show yet.
    val tasks: List<PlanItem> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val opportunities: List<Opportunity> = emptyList(),
    val orders: List<Order> = emptyList(),
    val activities: List<Activity> = emptyList(),
    val quotes: List<Quote> = emptyList(),
    val products: List<ProductKnowledge> = emptyList(),
    val objections: List<Objection> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val projects: List<Project> = emptyList(),
)

/**
 * Desktop persistence: one JSON file for the lists plus a small properties file for settings.
 * Stores data under %APPDATA%/VisitFlow (Windows) or ~/.visitflow.
 */
class Store(private val dir: File = defaultDataDir()) {

    private val json = Json { ignoreUnknownKeys = true }
    private val prettyJson = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val prefsFile = File(dir, "prefs.properties")
    private val dataFile = File(dir, "data.json")

    init {
        dir.mkdirs()
    }

    private val prefs: Properties =
        Properties().apply { if (prefsFile.exists()) prefsFile.inputStream().use { load(it) } }

    private fun prop(key: String, default: String): String = prefs.getProperty(key) ?: default

    private fun setProp(key: String, value: String) {
        prefs.setProperty(key, value)
        prefsFile.outputStream().use { prefs.store(it, "VisitFlow settings") }
    }

    // Small pref accessors the cloud-sync layer uses (tokens, last-synced snapshot, uid).
    internal fun cloudPref(key: String, default: String = ""): String = prop(key, default)
    internal fun setCloudPref(key: String, value: String) = setProp(key, value)

    /** Drop the previous account's local data when a different account signs in (isolation). */
    internal fun prepareForAccount(uid: String) {
        val prev = prop("cloud_uid", "")
        if (prev.isNotBlank() && prev != uid) {
            visits = emptyList(); customers = emptyList(); plan = emptyList()
            tasks = emptyList(); inventory = emptyList(); opportunities = emptyList(); orders = emptyList(); activities = emptyList(); quotes = emptyList(); products = emptyList(); objections = emptyList(); attachments = emptyList(); projects = emptyList()
            saveData()
        }
        setProp("cloud_uid", uid)
    }

    // ---- persisted lists ----
    private fun loadData(): StoredData =
        runCatching { json.decodeFromString<StoredData>(dataFile.readText()) }.getOrDefault(StoredData())

    private fun saveData() {
        dataFile.writeText(json.encodeToString(StoredData(visits, customers, plan, tasks, inventory, opportunities, orders, activities, quotes, products, objections, attachments, projects)))
        if (!applyingRestore) onDataChanged?.invoke()
    }

    // Synced data. Tasks & opportunities are now shown on desktop (plan 6.5); the rest are pass-through.
    var tasks by mutableStateOf(loadData().tasks)
        private set
    var opportunities by mutableStateOf(loadData().opportunities)
        private set
    private var inventory: List<InventoryItem> = loadData().inventory
    var orders by mutableStateOf(loadData().orders)
        private set
    var activities by mutableStateOf(loadData().activities)
        private set
    var quotes by mutableStateOf(loadData().quotes)
        private set
    private var products: List<ProductKnowledge> = loadData().products
    private var objections: List<Objection> = loadData().objections
    private var attachments: List<Attachment> = loadData().attachments
    private var projects: List<Project> = loadData().projects

    /** Add a follow-up task from desktop (e.g. via the assistant); returns its id. */
    fun addTask(action: String, client: String, date: String = todayIso(), time: String = ""): String {
        if (action.isBlank() && client.isBlank()) return ""
        val item = PlanItem(
            id = System.currentTimeMillis().toString(36) + (1000..9999).random().toString(36),
            client = client.trim(), date = date, action = action.trim(), time = time,
            customerId = customerFor(client)?.id.orEmpty(), source = "AI", status = "OPEN",
        )
        tasks = tasks + item
        saveData()
        return item.id
    }

    /** Move a task to another date (keeps its status). */
    fun rescheduleTask(id: String, date: String) {
        tasks = tasks.map { if (it.id == id) it.copy(date = date) else it }
        saveData()
    }

    /** Toggle a task done/open from desktop (kept in sync with the phone). */
    fun toggleTask(id: String) {
        tasks = tasks.map {
            if (it.id != id) it else {
                val nowDone = !it.done
                it.copy(done = nowDone, status = if (nowDone) "DONE" else "OPEN", completedAt = if (nowDone) todayIso() else "")
            }
        }
        saveData()
    }

    var visits by mutableStateOf(loadData().visits)
        private set

    var customers by mutableStateOf(loadData().customers)
        private set

    var plan by mutableStateOf(loadData().plan)
        private set

    // ---- settings ----
    var theme by mutableStateOf(prop("theme", "auto"))
        private set

    var palette by mutableStateOf(prop("palette", "classic"))
        private set

    var langMode by mutableStateOf(prop("lang", "auto"))
        private set

    var apiKey by mutableStateOf(prop("ai_key", ""))
        private set

    var aiModel by mutableStateOf(prop("ai_model", "gemini-2.5-flash"))
        private set

    var weekStartDay by mutableStateOf(prop("week_start", "SATURDAY"))
        private set

    var lang by mutableStateOf(resolveLanguage(langMode))
        private set

    internal var onDataChanged: (() -> Unit)? = null

    private var applyingRestore = false

    init {
        I18n.en = (lang == "en")
        WeekConfig.startDay = parseWeekDay(weekStartDay)
        migrateCustomersFromVisits()
    }

    private fun parseWeekDay(day: String): java.time.DayOfWeek =
        runCatching { java.time.DayOfWeek.valueOf(day) }.getOrDefault(java.time.DayOfWeek.SATURDAY)

    private fun resolveLanguage(mode: String): String = if (mode == "ar" || mode == "en") mode else "en"

    fun chooseWeekStart(day: String) {
        weekStartDay = day
        WeekConfig.startDay = parseWeekDay(day)
        setProp("week_start", day)
    }

    fun chooseLang(l: String) {
        langMode = l
        lang = resolveLanguage(l)
        I18n.en = (lang == "en")
        setProp("lang", l)
    }

    fun chooseTheme(t: String) {
        theme = t
        setProp("theme", t)
    }

    fun choosePalette(value: String) {
        palette = value
        setProp("palette", value)
    }

    fun chooseApiKey(value: String) {
        apiKey = value.trim()
        setProp("ai_key", apiKey)
    }

    fun chooseAiModel(value: String) {
        aiModel = value.trim().ifBlank { "gemini-2.5-flash" }
        setProp("ai_model", aiModel)
    }

    // ---- visits ----
    fun upsert(v: Visit) {
        visits = if (visits.any { it.id == v.id }) visits.map { if (it.id == v.id) v else it }
        else visits + v
        saveData()
        syncCustomerFromVisit(v)
    }

    fun delete(id: String) {
        visits = visits.filter { it.id != id }
        saveData()
    }

    fun clearAll() {
        visits = emptyList()
        saveData()
    }

    // ---- customers ----
    private fun customerKey(name: String): String = name.trim().lowercase()

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
            saveData()
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
            saveData()
        }
        customers = customers.filter { it.id != clean.id && it.id != sameName?.id } + merged
        customers = customers.sortedBy { it.name.lowercase() }
        saveData()
    }

    fun deleteCustomer(id: String) {
        customers = customers.filter { it.id != id }
        saveData()
    }

    fun visitsFor(customer: Customer): List<Visit> =
        visits.filter { customerKey(it.client) == customerKey(customer.name) }
            .sortedByDescending { it.date + it.time }

    fun customerFor(name: String): Customer? =
        customers.firstOrNull { customerKey(it.name) == customerKey(name) }

    fun dueFollowUps(): List<Visit> = visits
        .filter { it.next.isNotBlank() && it.nextDate.isNotBlank() && it.nextDate <= todayIso() }
        .sortedBy { it.nextDate }

    // ---- Today plan ----
    fun planFor(date: String): List<PlanItem> = plan.filter { it.date == date }

    fun addPlan(client: String, date: String = todayIso()) {
        if (client.isBlank()) return
        plan = plan + PlanItem(pid(), client.trim(), date)
        saveData()
    }

    fun togglePlan(id: String) {
        plan = plan.map { if (it.id == id) it.copy(done = !it.done) else it }
        saveData()
    }

    fun deletePlan(id: String) {
        plan = plan.filter { it.id != id }
        saveData()
    }

    private fun pid(): String =
        System.currentTimeMillis().toString(36) + (1000..9999).random().toString(36)

    // ---- portable backup ----
    fun exportBackup(): String = prettyJson.encodeToString(
        AppBackup(
            exportedAt = java.time.Instant.now().toString(), visits = visits,
            customers = customers, plan = plan, tasks = tasks, inventory = inventory,
            opportunities = opportunities, orders = orders, activities = activities, quotes = quotes,
            products = products, objections = objections, attachments = attachments, projects = projects,
        )
    )

    fun restoreBackup(raw: String): Boolean {
        applyingRestore = true
        return try {
            runCatching {
                val backup = json.decodeFromString<AppBackup>(raw)
                require(backup.version in 1..CURRENT_BACKUP_VERSION)
                visits = backup.visits
                customers = backup.customers
                plan = backup.plan
                tasks = backup.tasks
                inventory = backup.inventory
                opportunities = backup.opportunities
                orders = backup.orders
                activities = backup.activities
                quotes = backup.quotes
                products = backup.products
                objections = backup.objections
                attachments = backup.attachments
                projects = backup.projects
                saveData()
                true
            }.getOrDefault(false)
        } finally {
            applyingRestore = false
        }
    }

    // ---- cloud sync (shares the same Firestore document as the Android app) ----
    private fun snapshotBackup() = AppBackup(
        exportedAt = "", visits = visits, customers = customers,
        plan = plan, tasks = tasks, inventory = inventory, opportunities = opportunities, orders = orders,
        activities = activities, quotes = quotes, products = products, objections = objections, attachments = attachments,
        projects = projects,
    )

    internal fun cloudSnapshot(): String = json.encodeToString(snapshotBackup())

    internal fun mergeCloudSnapshot(raw: String): String? = runCatching {
        val remote = json.decodeFromString<AppBackup>(raw)
        require(remote.version in 1..CURRENT_BACKUP_VERSION)
        json.encodeToString(mergeBackups(snapshotBackup(), remote))
    }.getOrNull()

    @Volatile var lastMergeConflicts: Int = 0
        private set

    private fun decodeBackup(raw: String?): AppBackup =
        raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<AppBackup>(it) }.getOrNull() }
            ?: AppBackup(exportedAt = "", visits = emptyList(), customers = emptyList(), plan = emptyList())

    /** Three-way merge of [remoteRaw] into [localRaw] against a common [baseRaw]. Pure; see SyncMerge. */
    internal fun threeWayMerge(baseRaw: String?, localRaw: String, remoteRaw: String): String? = runCatching {
        val remote = json.decodeFromString<AppBackup>(remoteRaw)
        require(remote.version in 1..CURRENT_BACKUP_VERSION)
        val result = SyncMerge.merge(decodeBackup(baseRaw), decodeBackup(localRaw), remote)
        lastMergeConflicts = result.conflicts
        json.encodeToString(result.backup)
    }.getOrNull()

    internal fun threeWayMergeCloud(baseRaw: String?, remoteRaw: String): String? =
        threeWayMerge(baseRaw, cloudSnapshot(), remoteRaw)

    internal fun restoreCloudSnapshot(raw: String): Boolean = restoreBackup(raw)

    // ---- AI note formatting ----
    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

private fun defaultDataDir(): File {
    val base = System.getenv("APPDATA")
        ?: System.getProperty("user.home")
    return File(base, "VisitFlow")
}