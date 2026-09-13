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

    var weekStartDay by mutableStateOf(sp.getString("week_start", "SATURDAY") ?: "SATURDAY")
        private set

    /** Language of the weekly report / Excel export: "en" or "ar". */
    var reportLang by mutableStateOf(sp.getString("report_lang", "en") ?: "en")
        private set

    /** Whether the specific code automations (plan 3.4) run. On by default; user can turn off. */
    var smartAutomation by mutableStateOf(sp.getBoolean("smart_automation", true))
        private set

    fun chooseSmartAutomation(v: Boolean) {
        smartAutomation = v
        sp.edit().putBoolean("smart_automation", v).apply()
    }

    /** Whether the user has acknowledged that AI features send selected data to the AI provider (4.6). */
    var aiConsent by mutableStateOf(sp.getBoolean("ai_consent", false))
        private set

    fun grantAiConsent() {
        aiConsent = true
        sp.edit().putBoolean("ai_consent", true).apply()
    }

    /** A weekly self-improvement goal (plan 5.5): a behavior to work on, plus the week it was set for. */
    var weeklyGoal by mutableStateOf(sp.getString("weekly_goal", "") ?: "")
        private set
    var weeklyGoalWeek by mutableStateOf(sp.getString("weekly_goal_week", "") ?: "")
        private set

    fun setWeeklyGoal(text: String, weekStartIso: String) {
        weeklyGoal = text.trim()
        weeklyGoalWeek = weekStartIso
        sp.edit().putString("weekly_goal", weeklyGoal).putString("weekly_goal_week", weekStartIso).apply()
    }

    /** Time of day follow-up reminders fire, as "HH:mm" (default 09:00). */
    var reminderTime by mutableStateOf(sp.getString("reminder_time", "09:00") ?: "09:00")
        private set

    /** Daily target number of visits (0 = no goal set). Independent of the day's plan size. */
    var dailyGoal by mutableStateOf(sp.getInt("daily_goal", 0))
        private set

    /** Local file path of the user's profile photo ("" = none; kept on-device, per account). */
    var profilePhotoPath by mutableStateOf(sp.getString("profile_photo", "") ?: "")
        private set

    /** Default currency used for prices/values (auto-detected from the device, changeable in settings). */
    var defaultCurrency by mutableStateOf(sp.getString("currency", "") ?: "")
        private set

    private fun deriveCurrency(country: String): String =
        runCatching { java.util.Currency.getInstance(java.util.Locale("", country)).currencyCode }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "SAR"

    fun chooseCurrency(code: String) {
        defaultCurrency = code.trim()
        sp.edit().putString("currency", defaultCurrency).apply()
    }

    fun setProfilePhoto(path: String) {
        profilePhotoPath = path
        sp.edit().putString("profile_photo", path).apply()
    }

    /** Reusable quick note snippets the rep can tap to drop into a visit note. */
    var noteTemplates by mutableStateOf(loadTemplates())
        private set

    private fun loadTemplates(): List<String> {
        val saved = sp.getString("note_templates", null)
        if (saved != null) return runCatching { json.decodeFromString<List<String>>(saved) }.getOrDefault(defaultTemplates())
        return defaultTemplates()
    }

    private fun defaultTemplates(): List<String> = listOf(
        "تم الاجتماع مع العميل ومناقشة احتياجاته.",
        "العميل مهتم بـ",
        "طلب العميل عرض سعر لـ",
        "Met the client and discussed their needs.",
        "Client is interested in",
        "Client requested a quote for",
    )

    private fun persistTemplates() {
        sp.edit().putString("note_templates", json.encodeToString(noteTemplates)).apply()
    }

    fun addTemplate(text: String) {
        val v = text.trim()
        if (v.isBlank() || v in noteTemplates) return
        noteTemplates = noteTemplates + v
        persistTemplates()
    }

    fun removeTemplate(text: String) {
        noteTemplates = noteTemplates.filter { it != text }
        persistTemplates()
    }

    var lang by mutableStateOf(resolveLanguage(langMode))
        private set

    var customers by mutableStateOf(loadCustomers())
        private set

    init {
        I18n.en = (lang == "en")
        WeekConfig.startDay = parseWeekDay(weekStartDay)
        applyReminderTime(reminderTime)
        val country = appContext.resources.configuration.locales[0]?.country.orEmpty()
        setDefaultCountryFromIso(country)
        if (defaultCurrency.isBlank()) {
            defaultCurrency = deriveCurrency(country)
            sp.edit().putString("currency", defaultCurrency).apply()
        }
        migrateCustomersFromVisits()
        ReminderScheduler.reschedule(appContext, visits)
    }

    private fun applyReminderTime(hm: String) {
        val parts = hm.split(":")
        ReminderConfig.hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        ReminderConfig.minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    }

    fun chooseReminderTime(hm: String) {
        reminderTime = hm
        applyReminderTime(hm)
        sp.edit().putString("reminder_time", hm).apply()
        ReminderScheduler.reschedule(appContext, visits)   // re-fire all follow-ups at the new time
    }

    private fun parseWeekDay(day: String): java.time.DayOfWeek =
        runCatching { java.time.DayOfWeek.valueOf(day) }.getOrDefault(java.time.DayOfWeek.SATURDAY)

    fun chooseWeekStart(day: String) {
        weekStartDay = day
        WeekConfig.startDay = parseWeekDay(day)
        sp.edit().putString("week_start", day).apply()
    }

    fun chooseDailyGoal(n: Int) {
        dailyGoal = n.coerceIn(0, 99)
        sp.edit().putInt("daily_goal", dailyGoal).apply()
    }

    /** How many visits were actually recorded on [date] — the real progress toward the daily goal. */
    fun visitsOn(date: String): Int = visits.count { it.date == date }

    fun chooseReportLang(lang: String) {
        reportLang = lang
        sp.edit().putString("report_lang", lang).apply()
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

    fun upsert(v0: Visit) {
        // Ensure the linked customer exists, then stamp the stable customerId onto the visit so the
        // link survives a later rename (the name is kept only as a display snapshot).
        syncCustomerFromVisit(v0)
        val cid = v0.customerId.ifBlank { customerFor(v0.client)?.id.orEmpty() }
        val v = if (cid != v0.customerId) v0.copy(customerId = cid) else v0
        visits = if (visits.any { it.id == v.id }) visits.map { if (it.id == v.id) v else it }
        else visits + v
        persist()
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

    /** Records belonging to a customer: linked by stable id, or by name for not-yet-linked records. */
    private fun Visit.belongsTo(cust: Customer): Boolean =
        (customerId.isNotBlank() && customerId == cust.id) ||
            (customerId.isBlank() && customerKey(client) == customerKey(cust.name))

    fun visitsFor(customer: Customer): List<Visit> =
        visits.filter { it.belongsTo(customer) }
            .sortedByDescending { it.date + it.time }

    fun customerFor(name: String): Customer? =
        customers.firstOrNull { customerKey(it.name) == customerKey(name) }

    fun customerById(id: String): Customer? =
        if (id.isBlank()) null else customers.firstOrNull { it.id == id }

    fun dueFollowUps(): List<Visit> = visits
        .filter { it.next.isNotBlank() && it.nextDate.isNotBlank() && it.nextDate <= todayIso() }
        .sortedBy { it.nextDate }

    // ---- Portable backup ----

    private fun snapshot(exportedAt: String) = AppBackup(
        exportedAt = exportedAt, visits = visits, customers = customers,
        plan = plan, tasks = tasks, inventory = inventory, opportunities = opportunities, orders = orders,
        activities = activities, quotes = quotes, products = products, objections = objections,
    )

    fun exportBackup(): String = prettyJson.encodeToString(snapshot(java.time.Instant.now().toString()))

    internal fun cloudSnapshot(): String = json.encodeToString(snapshot(""))

    internal fun mergeCloudSnapshot(raw: String): String? = runCatching {
        val remote = json.decodeFromString<AppBackup>(raw)
        require(remote.version in 1..CURRENT_BACKUP_VERSION)   // refuse a future schema we can't read
        val merged = mergeBackups(snapshot(""), remote)
        json.encodeToString(DataMigration.migrate(merged, ::pid))
    }.getOrNull()

    /** Number of conflict copies produced by the most recent three-way merge (thread-safe read). */
    @Volatile var lastMergeConflicts: Int = 0
        private set

    private fun decodeBackup(raw: String?): AppBackup =
        raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<AppBackup>(it) }.getOrNull() }
            ?: AppBackup(exportedAt = "", visits = emptyList(), customers = emptyList(), plan = emptyList())

    /**
     * Three-way merge of [remoteRaw] into [localRaw] against a common [baseRaw] (the last snapshot the
     * two sides agreed on). Pure: touches no Store state, so it is safe to call from a sync transaction
     * on any thread. Returns migrated merged JSON, or null if the remote schema is unreadable/too new.
     */
    internal fun threeWayMerge(baseRaw: String?, localRaw: String, remoteRaw: String): String? = runCatching {
        val remote = json.decodeFromString<AppBackup>(remoteRaw)
        require(remote.version in 1..CURRENT_BACKUP_VERSION)   // refuse a future schema we can't read
        val base = decodeBackup(baseRaw)
        val local = decodeBackup(localRaw)
        val result = SyncMerge.merge(base, local, remote)
        lastMergeConflicts = result.conflicts
        json.encodeToString(DataMigration.migrate(result.backup, ::pid))
    }.getOrNull()

    /** Convenience: merge remote into the current live local state against [baseRaw]. */
    internal fun threeWayMergeCloud(baseRaw: String?, remoteRaw: String): String? =
        threeWayMerge(baseRaw, cloudSnapshot(), remoteRaw)

    internal fun restoreCloudSnapshot(raw: String): Boolean {
        applyingCloud = true
        return try { restoreBackup(raw) } finally { applyingCloud = false }
    }

    /**
     * Called on the first sync after a sign-in. If a DIFFERENT account was last synced on this
     * device, the previous account's local data is dropped so it can't leak into — or merge with —
     * the account now signing in. First-time (guest → first account) keeps the local data.
     */
    internal fun prepareForAccount(uid: String) {
        val prev = sp.getString("last_account_uid", null)
        if (prev != null && prev != uid) {
            visits.forEach { ReminderScheduler.cancel(appContext, it.id) }
            visits = emptyList(); customers = emptyList(); plan = emptyList()
            tasks = emptyList(); inventory = emptyList(); opportunities = emptyList(); orders = emptyList(); activities = emptyList(); quotes = emptyList(); products = emptyList(); objections = emptyList()
            persist(); persistCustomers(); persistPlan(); persistTasks(); persistInventory(); persistOpportunities(); persistOrders(); persistActivities(); persistQuotes(); persistProducts(); persistObjections()
            setProfilePhoto("")   // the photo is per-account and local; drop the previous one
        }
        sp.edit().putString("last_account_uid", uid).apply()
    }

    fun restoreBackup(raw: String): Boolean = runCatching {
        val decoded = json.decodeFromString<AppBackup>(raw)
        require(decoded.version in 1..CURRENT_BACKUP_VERSION)   // refuse a future schema we can't read
        val backup = DataMigration.migrate(decoded, ::pid)      // link + backfill before it becomes live
        visits.forEach { ReminderScheduler.cancel(appContext, it.id) }
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
        persist()
        persistCustomers()
        persistPlan()
        persistTasks()
        persistInventory()
        persistOpportunities()
        persistOrders()
        persistActivities()
        persistQuotes()
        persistProducts()
        persistObjections()
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

    fun addPlan(action: String, client: String, date: String = todayIso(), time: String = "", minutes: Int = 0) {
        if (action.isBlank() && client.isBlank()) return
        plan = plan + PlanItem(pid(), client.trim(), date, action = action.trim(), time = time, minutes = minutes)
        persistPlan()
    }

    fun updatePlan(id: String, action: String, client: String, time: String, minutes: Int) {
        plan = plan.map {
            if (it.id == id) it.copy(client = client.trim(), action = action.trim(), time = time, minutes = minutes) else it
        }
        persistPlan()
    }

    fun togglePlan(id: String) {
        plan = plan.map { if (it.id == id) it.copy(done = !it.done) else it }
        persistPlan()
    }

    /** Moves an unfinished task to another day (keeps its details). */
    fun reschedulePlan(id: String, date: String) {
        plan = plan.map { if (it.id == id) it.copy(date = date) else it }
        persistPlan()
    }

    fun deletePlan(id: String) {
        plan = plan.filter { it.id != id }
        persistPlan()
    }

    fun deletePlans(ids: Collection<String>) {
        if (ids.isEmpty()) return
        plan = plan.filter { it.id !in ids }
        persistPlan()
    }

    /** Moves a plan item up/down among the items sharing its date. */
    fun movePlan(id: String, up: Boolean) {
        val item = plan.firstOrNull { it.id == id } ?: return
        val sameDate = plan.filter { it.date == item.date }
        val pos = sameDate.indexOfFirst { it.id == id }
        val target = if (up) pos - 1 else pos + 1
        if (target < 0 || target >= sameDate.size) return
        val otherId = sameDate[target].id
        val i1 = plan.indexOfFirst { it.id == id }
        val i2 = plan.indexOfFirst { it.id == otherId }
        plan = plan.toMutableList().also { val tmp = it[i1]; it[i1] = it[i2]; it[i2] = tmp }
        persistPlan()
    }

    fun deleteVisits(ids: Collection<String>) {
        if (ids.isEmpty()) return
        ids.forEach { ReminderScheduler.cancel(appContext, it) }
        visits = visits.filter { it.id !in ids }
        persist()
    }

    // ---- Daily tasks (separate from the route plan) ----
    var tasks by mutableStateOf(loadTasks())
        private set

    private fun loadTasks(): List<PlanItem> =
        runCatching { json.decodeFromString<List<PlanItem>>(sp.getString("tasks", "[]") ?: "[]") }
            .getOrDefault(emptyList())

    private fun persistTasks() {
        sp.edit().putString("tasks", json.encodeToString(tasks)).apply()
        if (!applyingCloud) onDataChanged?.invoke()
    }

    fun tasksFor(date: String): List<PlanItem> = tasks.filter { it.date == date }

    /** Creates a follow-up task and returns its id, so callers can link it or set its status. */
    fun addTask(
        action: String, client: String, date: String = todayIso(), time: String = "", minutes: Int = 0,
        opportunityId: String = "", source: TaskSource = TaskSource.MANUAL,
    ): String {
        if (action.isBlank() && client.isBlank()) return ""
        val cid = customerFor(client)?.id.orEmpty()
        val item = PlanItem(
            pid(), client.trim(), date, action = action.trim(), time = time, minutes = minutes,
            customerId = cid, opportunityId = opportunityId, source = source.name, status = TaskStatus.OPEN.name,
        )
        tasks = tasks + item
        persistTasks()
        return item.id
    }

    fun updateTask(id: String, action: String, client: String, time: String, minutes: Int, opportunityId: String? = null) {
        tasks = tasks.map {
            if (it.id == id) it.copy(
                client = client.trim(), action = action.trim(), time = time, minutes = minutes,
                customerId = customerFor(client)?.id.orEmpty().ifBlank { it.customerId },
                opportunityId = opportunityId ?: it.opportunityId,
            ) else it
        }
        persistTasks()
    }

    /** Sets a task's status (done/cancelled/postponed/open), stamping completion date and keeping the
     *  legacy `done` flag in sync. Distinct from [rescheduleTask], which only moves the date. */
    fun setTaskStatus(id: String, status: TaskStatus) {
        tasks = tasks.map {
            if (it.id == id) it.copy(
                status = status.name, done = status == TaskStatus.DONE,
                completedAt = if (status == TaskStatus.DONE) todayIso() else "",
            ) else it
        }
        persistTasks()
    }

    /** Moves a task to another day WITHOUT changing its status (a reschedule, not a "postpone"). */
    fun rescheduleTask(id: String, date: String) {
        tasks = tasks.map { if (it.id == id) it.copy(date = date) else it }
        persistTasks()
    }

    fun toggleTask(id: String) {
        val cur = tasks.firstOrNull { it.id == id } ?: return
        setTaskStatus(id, if (cur.statusEnum() == TaskStatus.DONE) TaskStatus.OPEN else TaskStatus.DONE)
    }

    fun deleteTask(id: String) {
        tasks = tasks.filter { it.id != id }
        persistTasks()
    }

    // ---- Inventory (stock + prices) ----
    var inventory by mutableStateOf(loadInventory())
        private set

    private fun loadInventory(): List<InventoryItem> =
        runCatching { json.decodeFromString<List<InventoryItem>>(sp.getString("inventory", "[]") ?: "[]") }
            .getOrDefault(emptyList())

    private fun persistInventory() {
        sp.edit().putString("inventory", json.encodeToString(inventory)).apply()
        if (!applyingCloud) onDataChanged?.invoke()
    }

    fun upsertInventory(item: InventoryItem) {
        inventory = if (inventory.any { it.id == item.id }) inventory.map { if (it.id == item.id) item else it }
        else inventory + item
        persistInventory()
    }

    fun deleteInventory(id: String) {
        inventory = inventory.filter { it.id != id }
        persistInventory()
    }

    fun deleteInventoryItems(ids: Collection<String>) {
        if (ids.isEmpty()) return
        inventory = inventory.filter { it.id !in ids }
        persistInventory()
    }

    // ---- Sales opportunities (pipeline) ----
    var opportunities by mutableStateOf(loadOpportunities())
        private set

    private fun loadOpportunities(): List<Opportunity> =
        runCatching { json.decodeFromString<List<Opportunity>>(sp.getString("opportunities", "[]") ?: "[]") }
            .getOrDefault(emptyList())

    private fun persistOpportunities() {
        sp.edit().putString("opportunities", json.encodeToString(opportunities)).apply()
        if (!applyingCloud) onDataChanged?.invoke()
    }

    fun upsertOpportunity(o0: Opportunity) {
        val cid = o0.customerId.ifBlank { customerFor(o0.customerName)?.id.orEmpty() }
        val o = if (cid != o0.customerId) o0.copy(customerId = cid) else o0
        opportunities = if (opportunities.any { it.id == o.id }) opportunities.map { if (it.id == o.id) o else it }
        else opportunities + o
        persistOpportunities()
    }

    fun deleteOpportunity(id: String) {
        opportunities = opportunities.filter { it.id != id }
        persistOpportunities()
    }

    fun newOpportunityId(): String = pid()

    // ---- Activities (interaction log: calls / meetings / logged messages) ----
    var activities by mutableStateOf(loadActivities())
        private set

    private fun loadActivities(): List<Activity> =
        runCatching { json.decodeFromString<List<Activity>>(sp.getString("activities", "[]") ?: "[]") }
            .getOrDefault(emptyList())

    private fun persistActivities() {
        sp.edit().putString("activities", json.encodeToString(activities)).apply()
        if (!applyingCloud) onDataChanged?.invoke()
    }

    fun upsertActivity(a0: Activity) {
        val cid = a0.customerId.ifBlank { customerFor(a0.customerName)?.id.orEmpty() }
        val a = if (cid != a0.customerId) a0.copy(customerId = cid) else a0
        activities = if (activities.any { it.id == a.id }) activities.map { if (it.id == a.id) a else it } else activities + a
        persistActivities()
    }

    fun deleteActivity(id: String) {
        activities = activities.filter { it.id != id }
        persistActivities()
    }

    fun newActivityId(): String = pid()

    /** Activities for a customer: linked by stable id, or by name for not-yet-linked ones. */
    fun activitiesForCustomer(cust: Customer): List<Activity> =
        activities.filter {
            (it.customerId.isNotBlank() && it.customerId == cust.id) ||
                (it.customerId.isBlank() && it.customerName.trim().lowercase() == customerKey(cust.name))
        }.sortedByDescending { it.date + it.time }

    // ---- Quotes (price quotes with line items) ----
    var quotes by mutableStateOf(loadQuotes())
        private set

    private fun loadQuotes(): List<Quote> =
        runCatching { json.decodeFromString<List<Quote>>(sp.getString("quotes", "[]") ?: "[]") }
            .getOrDefault(emptyList())

    private fun persistQuotes() {
        sp.edit().putString("quotes", json.encodeToString(quotes)).apply()
        if (!applyingCloud) onDataChanged?.invoke()
    }

    fun upsertQuote(q0: Quote) {
        val cid = q0.customerId.ifBlank { customerFor(q0.customerName)?.id.orEmpty() }
        val q = if (cid != q0.customerId) q0.copy(customerId = cid) else q0
        quotes = if (quotes.any { it.id == q.id }) quotes.map { if (it.id == q.id) q else it } else quotes + q
        persistQuotes()
    }

    fun deleteQuote(id: String) {
        quotes = quotes.filter { it.id != id }
        persistQuotes()
    }

    fun newQuoteId(): String = pid()

    /** Next quote number, e.g. Q-0007 — simple sequential based on the current count. */
    fun nextQuoteNumber(): String = "Q-" + (quotes.size + 1).toString().padStart(4, '0')

    fun quotesForCustomer(cust: Customer): List<Quote> =
        quotes.filter {
            (it.customerId.isNotBlank() && it.customerId == cust.id) ||
                (it.customerId.isBlank() && it.customerName.trim().lowercase() == customerKey(cust.name))
        }.sortedByDescending { it.createdAt }

    /**
     * Creates an order from an ACCEPTED quote — an explicit, one-time action. Returns the quote linked
     * to the new order (or unchanged if it already produced one, preventing duplicates).
     */
    fun createOrderFromQuote(q: Quote): Quote {
        if (q.orderId.isNotBlank()) return q   // already converted — never duplicate
        val orderId = newOrderId()
        upsertOrder(
            Order(
                id = orderId, customerName = q.customerName, customerId = q.customerId,
                title = q.lines.joinToString(", ") { it.description }.take(120).ifBlank { q.number },
                amount = QuoteMath.total(q), currency = q.currency, date = todayIso(), status = "ORDERED",
            )
        )
        val linked = q.copy(orderId = orderId)
        upsertQuote(linked)
        return linked
    }

    // ---- Specific automations (plan 3.4) — deterministic, de-duplicated, offline. ----

    /** On a quote being SENT: create a follow-up (once) and advance the linked deal to the Quote stage. */
    fun onQuoteSent(quote: Quote) {
        if (!smartAutomation) return
        if (shouldCreateQuoteFollowUp(quote, tasks)) {
            val date = followUpDateForQuote(quote, todayIso())
            val action = if (I18n.en) "Follow up on quote ${quote.number}".trim() else "متابعة عرض السعر ${quote.number}".trim()
            tasks = tasks + PlanItem(
                pid(), quote.customerName.trim(), date, action = action,
                customerId = quote.customerId, opportunityId = quote.opportunityId,
                source = TaskSource.QUOTE.name, sourceRef = quote.id, status = TaskStatus.OPEN.name,
            )
            persistTasks()
        }
        val opp = opportunities.firstOrNull { it.id == quote.opportunityId }
        if (opp != null && shouldAdvanceOppToQuote(opp)) {
            upsertOpportunity(opp.withStage(OppStage.QUOTE, todayIso(), by = opp.owner))
        }
    }

    /** On a deal being WON/LOST: auto-cancel its still-open future follow-ups (moot after close). */
    fun onOpportunityClosed(opp: Opportunity) {
        if (!smartAutomation) return
        if (opp.stageEnum() != OppStage.WON && opp.stageEnum() != OppStage.LOST) return
        val ids = openFutureTasksForOpp(opp.id, tasks, todayIso()).toSet()
        if (ids.isEmpty()) return
        tasks = tasks.map { if (it.id in ids) it.copy(status = TaskStatus.CANCELLED.name, done = false) else it }
        persistTasks()
    }

    // ---- Product knowledge (specs/applications — separate from stock/price) ----
    var products by mutableStateOf(loadProducts())
        private set

    private fun loadProducts(): List<ProductKnowledge> =
        runCatching { json.decodeFromString<List<ProductKnowledge>>(sp.getString("products", "[]") ?: "[]") }.getOrDefault(emptyList())

    private fun persistProducts() {
        sp.edit().putString("products", json.encodeToString(products)).apply()
        if (!applyingCloud) onDataChanged?.invoke()
    }

    fun upsertProduct(p: ProductKnowledge) {
        products = if (products.any { it.id == p.id }) products.map { if (it.id == p.id) p else it } else products + p
        persistProducts()
    }

    fun deleteProduct(id: String) { products = products.filter { it.id != id }; persistProducts() }
    fun newProductId(): String = pid()

    // ---- Objection library ----
    var objections by mutableStateOf(loadObjections())
        private set

    private fun loadObjections(): List<Objection> =
        runCatching { json.decodeFromString<List<Objection>>(sp.getString("objections", "[]") ?: "[]") }.getOrDefault(emptyList())

    private fun persistObjections() {
        sp.edit().putString("objections", json.encodeToString(objections)).apply()
        if (!applyingCloud) onDataChanged?.invoke()
    }

    fun upsertObjection(o: Objection) {
        objections = if (objections.any { it.id == o.id }) objections.map { if (it.id == o.id) o else it } else objections + o
        persistObjections()
    }

    fun deleteObjection(id: String) { objections = objections.filter { it.id != id }; persistObjections() }
    fun newObjectionId(): String = pid()

    // ---- Orders / purchases (linked to a customer by name) ----
    var orders by mutableStateOf(loadOrders())
        private set

    private fun loadOrders(): List<Order> =
        runCatching { json.decodeFromString<List<Order>>(sp.getString("orders", "[]") ?: "[]") }
            .getOrDefault(emptyList())

    private fun persistOrders() {
        sp.edit().putString("orders", json.encodeToString(orders)).apply()
        if (!applyingCloud) onDataChanged?.invoke()
    }

    fun upsertOrder(o0: Order) {
        val cid = o0.customerId.ifBlank { customerFor(o0.customerName)?.id.orEmpty() }
        val o = if (cid != o0.customerId) o0.copy(customerId = cid) else o0
        orders = if (orders.any { it.id == o.id }) orders.map { if (it.id == o.id) o else it } else orders + o
        persistOrders()
    }

    fun deleteOrder(id: String) {
        orders = orders.filter { it.id != id }
        persistOrders()
    }

    fun newOrderId(): String = pid()

    fun ordersFor(customerName: String): List<Order> {
        val key = customerName.trim().lowercase()
        return orders.filter { it.customerName.trim().lowercase() == key }.sortedByDescending { it.date }
    }

    /** Orders for a customer: linked by stable id, or by name for not-yet-linked orders. */
    fun ordersForCustomer(cust: Customer): List<Order> =
        orders.filter {
            (it.customerId.isNotBlank() && it.customerId == cust.id) ||
                (it.customerId.isBlank() && it.customerName.trim().lowercase() == customerKey(cust.name))
        }.sortedByDescending { it.date }

    /**
     * Total purchased grouped by currency — never mixes SAR and USD into one number (plan 1.6).
     * A blank order currency falls back to the account default so it still groups sensibly.
     */
    fun purchasedByCurrency(cust: Customer): Map<String, Double> =
        ordersForCustomer(cust)
            .groupBy { it.currency.trim().ifBlank { defaultCurrency } }
            .mapValues { (_, os) -> os.sumOf { it.amount } }
            .filterValues { it != 0.0 }

    fun newInventoryId(): String = pid()

    /** A compact text view of the stock, handed to the AI so it can decide the next step. */
    fun inventoryContext(): String {
        if (inventory.isEmpty()) return "(no stock records)"
        return inventory.joinToString("\n") { i ->
            val stock = if (i.inStock) "in stock: ${i.quantity}" else "out of stock"
            val price = if (i.hasPrice) "price: ${i.price}${if (i.currency.isNotBlank()) " " + i.currency else ""}" else "price: unknown"
            "- ${i.name} | $stock | $price"
        }
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

    /**
     * Backfills stable customer links and contact ids on the locally-stored data (idempotent).
     * Runs once at startup; a second run is a no-op. Declared last so every collection above is
     * already initialized when it executes.
     */
    private fun migrateLoaded() {
        val before = snapshot("")
        val after = DataMigration.migrate(before, ::pid)
        if (after == before) return
        visits = after.visits
        customers = after.customers
        plan = after.plan
        tasks = after.tasks
        opportunities = after.opportunities
        orders = after.orders
        activities = after.activities
        quotes = after.quotes
        products = after.products
        objections = after.objections
        persist(); persistCustomers(); persistPlan(); persistTasks(); persistOpportunities(); persistOrders(); persistActivities(); persistQuotes(); persistProducts(); persistObjections()
    }

    init { migrateLoaded() }
}
