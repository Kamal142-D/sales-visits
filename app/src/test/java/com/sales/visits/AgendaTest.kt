package com.sales.visits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaTest {

    private val today = "2026-09-13"

    @Test fun tasksBucketByDate() {
        val tasks = listOf(
            PlanItem(id = "t1", client = "A", date = "2026-09-10", action = "call", status = "OPEN"),   // overdue
            PlanItem(id = "t2", client = "B", date = today, action = "meet", status = "OPEN"),           // today
            PlanItem(id = "t3", client = "C", date = "2026-09-20", action = "later", status = "OPEN"),   // future → not shown
        )
        val a = buildAgenda(today, tasks, emptyList(), emptyList(), emptyList())
        assertEquals(listOf("t1"), a.overdue.map { it.refId })
        assertEquals(listOf("t2"), a.today.map { it.refId })
    }

    @Test fun doneAndCancelledTasksAreExcluded() {
        val tasks = listOf(
            PlanItem(id = "t1", client = "A", date = today, status = "DONE"),
            PlanItem(id = "t2", client = "B", date = today, status = "CANCELLED"),
            PlanItem(id = "t3", client = "C", date = today, status = "OPEN"),
        )
        val a = buildAgenda(today, tasks, emptyList(), emptyList(), emptyList())
        assertEquals(listOf("t3"), a.today.map { it.refId })
    }

    @Test fun appointmentReasonWhenTimeSet() {
        val tasks = listOf(PlanItem(id = "t1", client = "A", date = today, time = "10:00", status = "OPEN"))
        val a = buildAgenda(today, tasks, emptyList(), emptyList(), emptyList())
        assertEquals(AgendaReason.APPOINTMENT, a.today.single().reason)
    }

    @Test fun activeOppWithNoNextStepBecomesSuggestion() {
        val opps = listOf(Opportunity(id = "o1", title = "Deal", customerName = "A", stage = "QUALIFYING"))
        val a = buildAgenda(today, emptyList(), emptyList(), opps, emptyList())
        assertEquals(AgendaReason.NEEDS_ACTION, a.suggestions.single().reason)
        assertEquals("o1", a.suggestions.single().refId)
    }

    @Test fun closedOppDoesNotChase() {
        val opps = listOf(
            Opportunity(id = "won", title = "W", stage = "WON"),
            Opportunity(id = "lost", title = "L", stage = "LOST"),
            Opportunity(id = "parked", title = "P", stage = "POSTPONED"),
        )
        val a = buildAgenda(today, emptyList(), emptyList(), opps, emptyList())
        assertTrue(a.overdue.isEmpty() && a.today.isEmpty() && a.suggestions.isEmpty())
    }

    @Test fun oppWithOpenTaskIsNotDoubleCounted() {
        val opps = listOf(Opportunity(id = "o1", title = "Deal", customerName = "A", stage = "QUOTE", nextDate = today))
        val tasks = listOf(PlanItem(id = "t1", client = "A", date = today, opportunityId = "o1", status = "OPEN"))
        val a = buildAgenda(today, tasks, emptyList(), opps, emptyList())
        // Only the task appears for today, not the opportunity's own next-step.
        assertEquals(listOf("TASK"), a.today.map { it.kind })
    }

    @Test fun expiredSentQuoteIsSuggestion() {
        val quotes = listOf(
            Quote(id = "q1", number = "Q-1", customerName = "A", status = "SENT", expiryDate = "2026-09-01"),
            Quote(id = "q2", number = "Q-2", customerName = "A", status = "DRAFT", expiryDate = "2026-09-01"),
        )
        val a = buildAgenda(today, emptyList(), emptyList(), emptyList(), quotes)
        assertEquals(listOf("q1"), a.suggestions.map { it.refId })   // only the SENT one
        assertEquals(AgendaReason.EXPIRED, a.suggestions.single().reason)
    }

    @Test fun visitFollowUpsBucket() {
        val visits = listOf(
            Visit(id = "v1", client = "A", date = "2026-08-01", next = "call back", nextDate = "2026-09-10"),
            Visit(id = "v2", client = "B", date = "2026-08-01", next = "send doc", nextDate = today),
        )
        val a = buildAgenda(today, emptyList(), visits, emptyList(), emptyList())
        assertEquals(listOf("v1"), a.overdue.map { it.refId })
        assertEquals(listOf("v2"), a.today.map { it.refId })
    }
}
