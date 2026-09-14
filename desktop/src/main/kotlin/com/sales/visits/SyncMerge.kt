package com.sales.visits

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Deterministic three-way sync merge (plan phase 1.4) — the desktop copy of the Android engine, kept
 * byte-for-byte compatible so both platforms converge on the same result. See the Android
 * `SyncMerge.kt` for the full contract. Pure and JVM-testable.
 */
object SyncMerge {
    @PublishedApi internal val enc = Json { encodeDefaults = true }

    data class Result(val backup: AppBackup, val conflicts: Int)

    fun merge(base: AppBackup, local: AppBackup, remote: AppBackup): Result {
        var conflicts = 0
        fun <T> tally(r: ListResult<T>): List<T> { conflicts += r.conflicts; return r.items }

        val out = AppBackup(
            version = maxOf(local.version, remote.version, CURRENT_BACKUP_VERSION),
            exportedAt = "",
            visits = tally(mergeList(base.visits, local.visits, remote.visits, { it.id }, { v, id -> v.copy(id = id) })),
            customers = tally(mergeList(base.customers, local.customers, remote.customers, { it.id }, { v, id -> v.copy(id = id) })),
            plan = tally(mergeList(base.plan, local.plan, remote.plan, { it.id }, { v, id -> v.copy(id = id) })),
            tasks = tally(mergeList(base.tasks, local.tasks, remote.tasks, { it.id }, { v, id -> v.copy(id = id) })),
            inventory = tally(mergeList(base.inventory, local.inventory, remote.inventory, { it.id }, { v, id -> v.copy(id = id) })),
            opportunities = tally(mergeList(base.opportunities, local.opportunities, remote.opportunities, { it.id }, { v, id -> v.copy(id = id) })),
            orders = tally(mergeList(base.orders, local.orders, remote.orders, { it.id }, { v, id -> v.copy(id = id) })),
            activities = tally(mergeList(base.activities, local.activities, remote.activities, { it.id }, { v, id -> v.copy(id = id) })),
            quotes = tally(mergeList(base.quotes, local.quotes, remote.quotes, { it.id }, { v, id -> v.copy(id = id) })),
            products = tally(mergeList(base.products, local.products, remote.products, { it.id }, { v, id -> v.copy(id = id) })),
            objections = tally(mergeList(base.objections, local.objections, remote.objections, { it.id }, { v, id -> v.copy(id = id) })),
            attachments = tally(mergeList(base.attachments, local.attachments, remote.attachments, { it.id }, { v, id -> v.copy(id = id) })),
            projects = tally(mergeList(base.projects, local.projects, remote.projects, { it.id }, { v, id -> v.copy(id = id) })),
        )
        return Result(out, conflicts)
    }

    data class ListResult<T>(val items: List<T>, val conflicts: Int)

    inline fun <reified T> mergeList(
        base: List<T>, local: List<T>, remote: List<T>,
        idOf: (T) -> String, withId: (T, String) -> T,
    ): ListResult<T> {
        val b = base.associateBy(idOf)
        val l = local.associateBy(idOf)
        val r = remote.associateBy(idOf)
        val ids = LinkedHashSet<String>().apply { addAll(l.keys); addAll(r.keys); addAll(b.keys) }
        val out = ArrayList<T>(ids.size)
        var conflicts = 0
        for (id in ids) {
            val bi = b[id]; val li = l[id]; val ri = r[id]
            when {
                li == null && ri == null -> Unit
                li == null -> { if (!(bi != null && ri == bi)) out.add(ri!!) }
                ri == null -> { if (!(bi != null && li == bi)) out.add(li) }
                li == ri -> out.add(li)
                bi != null && li == bi -> out.add(ri)
                bi != null && ri == bi -> out.add(li)
                else -> {
                    val ls = enc.encodeToString(li); val rs = enc.encodeToString(ri)
                    val localWins = ls <= rs
                    out.add(if (localWins) li else ri)
                    val cid = "$id~c"
                    if (!ids.contains(cid)) { out.add(withId(if (localWins) ri else li, cid)); conflicts++ }
                }
            }
        }
        return ListResult(out, conflicts)
    }
}
