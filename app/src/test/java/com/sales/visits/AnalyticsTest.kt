package com.sales.visits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalyticsTest {

    private val range = Analytics.DateRange("2026-09-01", "2026-09-30")

    private fun compute(
        customers: List<Customer> = emptyList(),
        visits: List<Visit> = emptyList(),
        activities: List<Activity> = emptyList(),
        quotes: List<Quote> = emptyList(),
        opps: List<Opportunity> = emptyList(),
        orders: List<Order> = emptyList(),
        tasks: List<PlanItem> = emptyList(),
    ) = Analytics.compute(range, customers, visits, activities, quotes, opps, orders, tasks, "SAR")

    @Test fun newProspectsCountedByCreatedDateAndRelationship() {
        val customers = listOf(
            Customer(id = "c1", name = "A", relationship = "PROSPECT", createdAt = "2026-09-05"),
            Customer(id = "c2", name = "B", relationship = "CUSTOMER", createdAt = "2026-09-05"),   // not a prospect
            Customer(id = "c3", name = "C", relationship = "PROSPECT", createdAt = "2026-08-01"),   // out of range
        )
        assertEquals(1, compute(customers = customers).newProspects)
    }

    @Test fun interactionsSeparateNoAnswer() {
        val visits = listOf(Visit(id = "v1", client = "A", date = "2026-09-10"))
        val acts = listOf(
            Activity(id = "a1", date = "2026-09-11", type = "CALL", result = "CONNECTED"),
            Activity(id = "a2", date = "2026-09-11", type = "CALL", result = "NO_ANSWER"),
        )
        val m = compute(visits = visits, activities = acts)
        assertEquals(2, m.completedInteractions)   // 1 visit + 1 connected call
        assertEquals(1, m.callsNoAnswer)
    }

    @Test fun wonLostByCloseDateAndPerCurrency() {
        val opps = listOf(
            Opportunity(id = "o1", title = "A", stage = "WON", stageChangedAt = "2026-09-10", value = 1000.0, currency = "SAR"),
            Opportunity(id = "o2", title = "B", stage = "WON", stageChangedAt = "2026-09-10", value = 500.0, currency = "USD"),
            Opportunity(id = "o3", title = "C", stage = "LOST", stageChangedAt = "2026-09-12"),
            Opportunity(id = "o4", title = "D", stage = "WON", stageChangedAt = "2026-08-01", value = 999.0, currency = "SAR"), // out of range
        )
        val m = compute(opps = opps)
        assertEquals(2, m.won)
        assertEquals(1, m.lost)
        assertEquals(mapOf("SAR" to 1000.0, "USD" to 500.0), m.wonValueByCurrency)   // never summed together
    }

    @Test fun pipelineIsActiveOnlyPerCurrency() {
        val opps = listOf(
            Opportunity(id = "o1", title = "A", stage = "QUOTE", value = 200.0, currency = "SAR"),
            Opportunity(id = "o2", title = "B", stage = "WON", value = 999.0, currency = "SAR"),   // closed → excluded
            Opportunity(id = "o3", title = "C", stage = "NEGOTIATION", value = 300.0, currency = "SAR"),
        )
        assertEquals(mapOf("SAR" to 500.0), compute(opps = opps).pipelineByCurrency)
    }

    @Test fun salesFromOrdersSeparateFromPipeline() {
        val orders = listOf(
            Order(id = "or1", customerName = "A", amount = 700.0, currency = "SAR", date = "2026-09-15"),
            Order(id = "or2", customerName = "B", amount = 100.0, currency = "SAR", date = "2026-08-01"),  // out of range
        )
        assertEquals(mapOf("SAR" to 700.0), compute(orders = orders).salesByCurrency)
    }

    @Test fun quotesSentBySentDateNotDrafts() {
        val quotes = listOf(
            Quote(id = "q1", status = "SENT", sentDate = "2026-09-03"),
            Quote(id = "q2", status = "DRAFT", sentDate = ""),
            Quote(id = "q3", status = "SENT", sentDate = "2026-08-01"),  // out of range
        )
        assertEquals(1, compute(quotes = quotes).quotesSent)
    }

    @Test fun adherenceCountsOnTimeCompletions() {
        val tasks = listOf(
            PlanItem(id = "t1", client = "A", date = "2026-09-10", status = "DONE", completedAt = "2026-09-09"), // on time
            PlanItem(id = "t2", client = "B", date = "2026-09-10", status = "DONE", completedAt = "2026-09-12"), // late
            PlanItem(id = "t3", client = "C", date = "2026-09-10", status = "OPEN"),                             // not done
            PlanItem(id = "t4", client = "D", date = "2026-09-10", status = "CANCELLED"),                        // excluded
        )
        val m = compute(tasks = tasks)
        assertEquals(1, m.followUpOnTime)
        assertEquals(3, m.followUpTotal)   // cancelled excluded
    }

    @Test fun adherenceNullWhenNoDueTasks() {
        assertNull(compute().adherence)   // no divide-by-zero
    }
}
