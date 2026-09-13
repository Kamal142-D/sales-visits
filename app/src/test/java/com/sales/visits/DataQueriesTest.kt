package com.sales.visits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataQueriesTest {

    private val today = "2026-09-13"

    @Test fun sentQuotesWithoutLoggedFollowup() {
        val quotes = listOf(
            Quote(id = "q1", number = "Q-1", customerName = "Acme", status = "SENT", sentDate = "2026-09-01"),
            Quote(id = "q2", number = "Q-2", customerName = "Beta", status = "SENT", sentDate = "2026-09-01"),
            Quote(id = "q3", number = "Q-3", customerName = "Gamma", status = "DRAFT", sentDate = ""),
        )
        // Beta got a follow-up activity after the send; Acme did not.
        val acts = listOf(Activity(id = "a1", customerName = "Beta", date = "2026-09-05", type = "CALL"))
        val r = DataQueries.sentQuotesNoFollowup(quotes, acts).map { it.id }
        assertEquals(listOf("q1"), r)   // q2 has follow-up; q3 is a draft
    }

    @Test fun activeDealsMissingDecisionMaker() {
        val customers = listOf(
            Customer(id = "c1", name = "Acme", contacts = listOf(ContactPerson(name = "Ali", roles = listOf("DECISION_MAKER")))),
            Customer(id = "c2", name = "Beta", contacts = listOf(ContactPerson(name = "Sara", roles = listOf("TECHNICAL")))),
        )
        val opps = listOf(
            Opportunity(id = "o1", title = "A", customerId = "c1", stage = "QUOTE"),   // has DM
            Opportunity(id = "o2", title = "B", customerId = "c2", stage = "QUOTE"),   // no DM
            Opportunity(id = "o3", title = "C", customerId = "c2", stage = "WON"),     // closed → ignored
        )
        assertEquals(listOf("o2"), DataQueries.oppsNoDecisionMaker(opps, customers).map { it.id })
    }

    @Test fun activeDealsWithoutNextStep() {
        val opps = listOf(
            Opportunity(id = "o1", title = "A", stage = "QUOTE", nextStep = "call", nextDate = "2026-09-20"),
            Opportunity(id = "o2", title = "B", stage = "QUOTE", nextStep = "", nextDate = ""),
            Opportunity(id = "o3", title = "C", stage = "LOST"),
        )
        assertEquals(listOf("o2"), DataQueries.oppsNoNextStep(opps).map { it.id })
    }

    @Test fun prospectsFilteredByCity() {
        val customers = listOf(
            Customer(id = "c1", name = "Acme", relationship = "PROSPECT", city = "Riyadh"),
            Customer(id = "c2", name = "Beta", relationship = "PROSPECT", city = "Jeddah"),
            Customer(id = "c3", name = "Gamma", relationship = "CUSTOMER", city = "Riyadh"),
        )
        assertEquals(setOf("c1", "c2"), DataQueries.prospects(customers).map { it.id }.toSet())
        assertEquals(listOf("c1"), DataQueries.prospects(customers, "Riyadh").map { it.id })
    }

    @Test fun overdueTasksExcludeDoneAndFuture() {
        val tasks = listOf(
            PlanItem(id = "t1", client = "A", date = "2026-09-10", status = "OPEN"),   // overdue
            PlanItem(id = "t2", client = "B", date = "2026-09-10", status = "DONE"),    // done
            PlanItem(id = "t3", client = "C", date = "2026-09-20", status = "OPEN"),    // future
        )
        assertEquals(listOf("t1"), DataQueries.overdueTasks(tasks, today).map { it.id })
    }
}
