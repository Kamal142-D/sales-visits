package com.sales.visits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTest {

    private val today = "2026-09-13"

    @Test fun sentQuoteNeedsFollowUpOnce() {
        val q = Quote(id = "q1", customerName = "A", status = "SENT")
        assertTrue(shouldCreateQuoteFollowUp(q, emptyList()))
        // Once a QUOTE-sourced task for this quote exists, no duplicate is created (dedup).
        val existing = listOf(PlanItem(id = "t1", client = "A", date = today, source = "QUOTE", sourceRef = "q1"))
        assertFalse(shouldCreateQuoteFollowUp(q, existing))
    }

    @Test fun draftQuoteDoesNotTrigger() {
        assertFalse(shouldCreateQuoteFollowUp(Quote(id = "q1", status = "DRAFT"), emptyList()))
    }

    @Test fun dedupIsPerQuoteNotGlobal() {
        // A task from a DIFFERENT quote must not block this quote's follow-up.
        val other = listOf(PlanItem(id = "t1", client = "A", date = today, source = "QUOTE", sourceRef = "qX"))
        assertTrue(shouldCreateQuoteFollowUp(Quote(id = "q1", status = "SENT"), other))
    }

    @Test fun followUpDateUsesExpiryThenSuggestion() {
        // Customer's explicit expiry wins.
        assertEquals("2026-10-01", followUpDateForQuote(Quote(id = "q", status = "SENT", expiryDate = "2026-10-01"), today))
        // No expiry → an editable +3-day suggestion (never a hard-coded fact elsewhere).
        assertEquals("2026-09-16", followUpDateForQuote(Quote(id = "q", status = "SENT"), today, suggestDays = 3))
    }

    @Test fun advanceOnlyFromEarlierActiveStage() {
        assertTrue(shouldAdvanceOppToQuote(Opportunity(id = "o", title = "d", stage = "QUALIFYING")))
        assertFalse(shouldAdvanceOppToQuote(Opportunity(id = "o", title = "d", stage = "QUOTE")))       // already there
        assertFalse(shouldAdvanceOppToQuote(Opportunity(id = "o", title = "d", stage = "NEGOTIATION"))) // don't go backwards
        assertFalse(shouldAdvanceOppToQuote(Opportunity(id = "o", title = "d", stage = "WON")))         // closed
    }

    @Test fun closingCancelsOnlyOpenFutureLinkedTasks() {
        val tasks = listOf(
            PlanItem(id = "keep_done", client = "A", date = "2026-09-20", opportunityId = "o1", status = "DONE"),
            PlanItem(id = "cancel1", client = "A", date = "2026-09-20", opportunityId = "o1", status = "OPEN"),
            PlanItem(id = "cancel_today", client = "A", date = today, opportunityId = "o1", status = "OPEN"),
            PlanItem(id = "past", client = "A", date = "2026-09-01", opportunityId = "o1", status = "OPEN"),   // past → left alone
            PlanItem(id = "other_opp", client = "A", date = "2026-09-20", opportunityId = "o2", status = "OPEN"),
        )
        val ids = openFutureTasksForOpp("o1", tasks, today).toSet()
        assertEquals(setOf("cancel1", "cancel_today"), ids)
    }
}
