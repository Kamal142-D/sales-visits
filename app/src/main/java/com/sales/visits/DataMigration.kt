package com.sales.visits

/**
 * Pure, side-effect-free data migration and linking. No Android types, so it is unit-testable on the
 * JVM. Every function here is **idempotent**: running it again on already-migrated data changes
 * nothing and never mints new ids for records that already have them.
 *
 * The core job (plan phase 1.2/1.3): back-fill stable `customerId` links onto records that currently
 * point at a customer only by name, WITHOUT guessing when the name is ambiguous.
 */
object DataMigration {

    /** Normalized match key for a customer name — same rule the Store uses for name lookups. */
    fun nameKey(name: String): String = name.trim().lowercase()

    /**
     * Index of name-key → the single customer id that owns it. A name shared by two or more
     * customers is intentionally left OUT of the index, so those records stay unlinked ("needs
     * linking") rather than being attached to an arbitrary customer.
     */
    private fun unambiguousByName(customers: List<Customer>): Map<String, String> {
        val counts = HashMap<String, Int>()
        val firstId = HashMap<String, String>()
        for (cu in customers) {
            val k = nameKey(cu.name)
            if (k.isEmpty()) continue
            counts[k] = (counts[k] ?: 0) + 1
            if (k !in firstId) firstId[k] = cu.id
        }
        return firstId.filterKeys { (counts[it] ?: 0) == 1 }
    }

    /** Assigns a stable id to any contact that lacks one. Existing ids are preserved. */
    fun ensureContactIds(customers: List<Customer>, nextId: () -> String): List<Customer> =
        customers.map { cu ->
            if (cu.contacts.all { it.id.isNotBlank() }) cu
            else cu.copy(contacts = cu.contacts.map { p -> if (p.id.isBlank()) p.copy(id = nextId()) else p })
        }

    /**
     * Back-fills `customerId` on visits, orders, opportunities and plan items whose id is blank,
     * using an unambiguous name match. Records already linked, or whose name matches zero or many
     * customers, are returned unchanged.
     */
    fun backfillLinks(backup: AppBackup): AppBackup {
        val index = unambiguousByName(backup.customers)
        fun resolve(name: String): String = index[nameKey(name)].orEmpty()
        return backup.copy(
            visits = backup.visits.map { if (it.customerId.isBlank()) it.copy(customerId = resolve(it.client)) else it },
            orders = backup.orders.map { if (it.customerId.isBlank()) it.copy(customerId = resolve(it.customerName)) else it },
            opportunities = backup.opportunities.map { if (it.customerId.isBlank()) it.copy(customerId = resolve(it.customerName)) else it },
            plan = backup.plan.map { if (it.customerId.isBlank()) it.copy(customerId = resolve(it.client)) else it },
            tasks = backup.tasks.map { if (it.customerId.isBlank()) it.copy(customerId = resolve(it.client)) else it },
            activities = backup.activities.map { if (it.customerId.isBlank()) it.copy(customerId = resolve(it.customerName)) else it },
            quotes = backup.quotes.map { if (it.customerId.isBlank()) it.copy(customerId = resolve(it.customerName)) else it },
        )
    }

    /**
     * Full migration to [CURRENT_BACKUP_VERSION]: give contacts ids, link records to customers, and
     * stamp the current version. Idempotent — safe to run on every load and after every restore.
     */
    fun migrate(backup: AppBackup, nextId: () -> String): AppBackup {
        val withContacts = backup.copy(customers = ensureContactIds(backup.customers, nextId))
        val linked = backfillLinks(withContacts)
        return linked.copy(version = CURRENT_BACKUP_VERSION)
    }
}
