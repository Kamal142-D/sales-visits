package com.sales.visits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpportunityLogicTest {

    private fun opp(stage: String = "NEW") = Opportunity(id = "o1", title = "Deal", stage = stage)

    @Test fun legacyStageMapping() {
        assertEquals(OppStage.QUALIFYING, mapLegacyStage("FOLLOW"))   // old follow → active stage, not "new"
        assertEquals(OppStage.NEW, mapLegacyStage("NEW"))
        assertEquals(OppStage.WON, mapLegacyStage("WON"))
        assertEquals(OppStage.LOST, mapLegacyStage("LOST"))
        assertEquals(OppStage.NEED, mapLegacyStage("NEED"))
        assertEquals(OppStage.NEW, mapLegacyStage(""))
        assertEquals(OppStage.NEW, mapLegacyStage("garbage"))
    }

    @Test fun activeFlag() {
        assertTrue(OppStage.QUALIFYING.isActive)
        assertTrue(OppStage.QUOTE.isActive)
        assertFalse(OppStage.WON.isActive)
        assertFalse(OppStage.LOST.isActive)
        assertFalse(OppStage.POSTPONED.isActive)
    }

    @Test fun withStageRecordsATransition() {
        val moved = opp("NEW").withStage(OppStage.QUOTE, at = "2026-09-12", by = "me")
        assertEquals("QUOTE", moved.stage)
        assertEquals("2026-09-12", moved.stageChangedAt)
        assertEquals(1, moved.history.size)
        assertEquals("NEW", moved.history.single().from)
        assertEquals("QUOTE", moved.history.single().to)
        assertEquals("me", moved.history.single().by)
    }

    @Test fun withStageNoOpWhenUnchanged() {
        val same = opp("QUOTE").withStage(OppStage.QUOTE, at = "2026-09-12")
        assertTrue(same.history.isEmpty())
        assertEquals("", same.stageChangedAt)
    }

    @Test fun withStageAccumulatesHistory() {
        val a = opp("NEW").withStage(OppStage.QUALIFYING, at = "d1")
        val b = a.withStage(OppStage.QUOTE, at = "d2")
        assertEquals(2, b.history.size)
        assertEquals(listOf("QUALIFYING", "QUOTE"), b.history.map { it.to })
    }

    @Test fun missingForQuoteListsGaps() {
        val bare = Opportunity(id = "o", title = "d")   // nothing filled, no decision maker
        val gaps = bare.missingForQuote(hasDecisionMaker = false)
        assertTrue(gaps.containsAll(listOf("need", "product", "decision_maker", "timing", "budget")))

        val complete = Opportunity(
            id = "o", title = "d", need = "faster printing", product = "HP 700",
            closeDate = "2026-10-01", budgetStatus = "HAS_BUDGET",
        )
        assertTrue(complete.missingForQuote(hasDecisionMaker = true).isEmpty())
    }

    @Test fun budgetMappingDefaults() {
        assertEquals(BudgetStatus.HAS_BUDGET, budgetEnum("HAS_BUDGET"))
        assertEquals(BudgetStatus.NO_BUDGET, budgetEnum("no_budget"))
        assertEquals(BudgetStatus.UNKNOWN, budgetEnum(""))
        assertEquals(BudgetStatus.UNKNOWN, budgetEnum("weird"))
    }
}
