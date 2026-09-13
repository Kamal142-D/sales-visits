package com.sales.visits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataMigrationTest {

    // Deterministic id generator so assertions are stable.
    private fun counter(): () -> String {
        var n = 0
        return { "id${++n}" }
    }

    private fun backup(
        customers: List<Customer> = emptyList(),
        visits: List<Visit> = emptyList(),
        orders: List<Order> = emptyList(),
        opps: List<Opportunity> = emptyList(),
        plan: List<PlanItem> = emptyList(),
    ) = AppBackup(
        exportedAt = "", visits = visits, customers = customers, plan = plan,
        opportunities = opps, orders = orders,
    )

    // ---- legacy visit-type mapping (plan 1.6) ----

    @Test fun legacyFollowIsNotAFirstVisit() {
        assertEquals(VisitType.CLIENT_FOLLOW, mapLegacyVisitType("FOLLOW"))
        assertNotEquals(VisitType.FIRST_VISIT, mapLegacyVisitType("FOLLOW"))
    }

    @Test fun legacyDesktopEnumMapsExplicitly() {
        assertEquals(VisitType.FIRST_VISIT, mapLegacyVisitType("FIRST"))
        assertEquals(VisitType.DELIVERY, mapLegacyVisitType("DELIVER"))
        assertEquals(VisitType.PAYMENT, mapLegacyVisitType("COLLECT"))
        assertEquals(VisitType.TECH_SUPPORT, mapLegacyVisitType("COMPLAINT"))
        assertEquals(VisitType.COURTESY, mapLegacyVisitType("OTHER"))
    }

    @Test fun currentValuesMapToThemselves() {
        VisitType.values().forEach { assertEquals(it, mapLegacyVisitType(it.name)) }
    }

    @Test fun unknownTypeIsNeutralNotFirstVisit() {
        assertEquals(VisitType.COURTESY, mapLegacyVisitType("ZZZ"))
        assertEquals(VisitType.COURTESY, mapLegacyVisitType(""))
        assertEquals(VisitType.COURTESY, mapLegacyVisitType(null))
    }

    // ---- unambiguous customer linking (plan 1.2) ----

    @Test fun unambiguousNameGetsLinked() {
        val cu = Customer(id = "c1", name = "Acme")
        val out = DataMigration.backfillLinks(
            backup(customers = listOf(cu), visits = listOf(Visit(id = "v1", client = "acme", date = "2026-01-01")))
        )
        assertEquals("c1", out.visits.single().customerId)
    }

    @Test fun ambiguousNameIsLeftUnlinked() {
        val a = Customer(id = "c1", name = "Acme")
        val b = Customer(id = "c2", name = "ACME")   // same normalized name → ambiguous
        val out = DataMigration.backfillLinks(
            backup(customers = listOf(a, b), visits = listOf(Visit(id = "v1", client = "Acme", date = "2026-01-01")))
        )
        assertEquals("", out.visits.single().customerId)
    }

    @Test fun alreadyLinkedRecordIsUntouched() {
        val a = Customer(id = "c1", name = "Acme")
        val v = Visit(id = "v1", client = "Acme", date = "2026-01-01", customerId = "manual")
        val out = DataMigration.backfillLinks(backup(customers = listOf(a), visits = listOf(v)))
        assertEquals("manual", out.visits.single().customerId)
    }

    @Test fun ordersAndOppsAndPlanAreLinkedToo() {
        val cu = Customer(id = "c1", name = "Acme")
        val out = DataMigration.backfillLinks(
            backup(
                customers = listOf(cu),
                orders = listOf(Order(id = "o1", customerName = "Acme")),
                opps = listOf(Opportunity(id = "p1", title = "Deal", customerName = "acme")),
                plan = listOf(PlanItem(id = "t1", client = "ACME", date = "2026-01-01")),
            )
        )
        assertEquals("c1", out.orders.single().customerId)
        assertEquals("c1", out.opportunities.single().customerId)
        assertEquals("c1", out.plan.single().customerId)
    }

    // ---- contact ids (plan 1.2) ----

    @Test fun contactIdsAreBackfilledAndStable() {
        val cu = Customer(
            id = "c1", name = "Acme",
            contacts = listOf(ContactPerson(name = "Ali"), ContactPerson(name = "Sara", id = "keep")),
        )
        val out = DataMigration.ensureContactIds(listOf(cu), counter())
        val contacts = out.single().contacts
        assertTrue(contacts[0].id.isNotBlank())
        assertEquals("keep", contacts[1].id)   // existing id preserved
    }

    // ---- idempotency + versioning (plan 1.3) ----

    @Test fun migrateIsIdempotent() {
        val cu = Customer(id = "c1", name = "Acme", contacts = listOf(ContactPerson(name = "Ali")))
        val src = backup(
            customers = listOf(cu),
            visits = listOf(Visit(id = "v1", client = "Acme", date = "2026-01-01")),
            orders = listOf(Order(id = "o1", customerName = "Acme")),
        )
        val once = DataMigration.migrate(src, counter())
        val twice = DataMigration.migrate(once, counter())
        assertEquals(once, twice)                       // second run changes nothing
        assertEquals(CURRENT_BACKUP_VERSION, once.version)
        assertEquals("c1", once.visits.single().customerId)
        assertEquals("c1", once.orders.single().customerId)
        assertTrue(once.customers.single().contacts.single().id.isNotBlank())
    }

    @Test fun migrateDoesNotInventNewIdsOnSecondRun() {
        val cu = Customer(id = "c1", name = "Acme", contacts = listOf(ContactPerson(name = "Ali")))
        val once = DataMigration.migrate(backup(customers = listOf(cu)), counter())
        val firstContactId = once.customers.single().contacts.single().id
        val twice = DataMigration.migrate(once, counter())
        assertEquals(firstContactId, twice.customers.single().contacts.single().id)
    }
}
