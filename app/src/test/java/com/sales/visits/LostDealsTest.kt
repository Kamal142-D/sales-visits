package com.sales.visits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LostDealsTest {

    @Test fun lastStageBeforeLostFromHistory() {
        val o = Opportunity(
            id = "o1", title = "A", stage = "LOST",
            history = listOf(
                StageChange(from = "QUOTE", to = "NEGOTIATION", at = "2026-09-05"),
                StageChange(from = "NEGOTIATION", to = "LOST", at = "2026-09-10"),
            ),
        )
        assertEquals("NEGOTIATION", LostDeals.lastStageBeforeLost(o))
    }

    @Test fun noHistoryGivesBlank() {
        assertEquals("", LostDeals.lastStageBeforeLost(Opportunity(id = "o", title = "A", stage = "LOST")))
    }

    @Test fun patternsNeedAtLeastTwoCases() {
        val lost = listOf(
            Opportunity(id = "1", title = "A", stage = "LOST", competitor = "Rival", lossReason = "price"),
            Opportunity(id = "2", title = "B", stage = "LOST", competitor = "Rival", lossReason = "price"),
            Opportunity(id = "3", title = "C", stage = "LOST", competitor = "Other", lossReason = "timing"),  // singletons
        )
        val p = LostDeals.patterns(lost)
        // "vs Rival" (2) and "price" (2) qualify; single "Other"/"timing" do not.
        assertTrue(p.any { it.first == "vs Rival" && it.second == 2 })
        assertTrue(p.any { it.first == "price" && it.second == 2 })
        assertTrue(p.none { it.first.contains("Other") || it.first == "timing" })
    }

    @Test fun singleLossHasNoPattern() {
        val lost = listOf(Opportunity(id = "1", title = "A", stage = "LOST", competitor = "Rival", lossReason = "price"))
        assertTrue(LostDeals.patterns(lost).isEmpty())
    }
}
