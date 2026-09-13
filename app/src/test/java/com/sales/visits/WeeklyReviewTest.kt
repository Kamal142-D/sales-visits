package com.sales.visits

import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyReviewTest {

    @Test fun focusPrioritizesOverdueFirst() {
        assertEquals("FOCUS_OVERDUE", WeeklyReview.focus(overdue = 2, quotesNoFollowup = 3, oppsNoDecisionMaker = 1, oppsNoNextStep = 4))
    }

    @Test fun focusFallsThroughInOrder() {
        assertEquals("FOCUS_QUOTES", WeeklyReview.focus(0, 1, 1, 1))
        assertEquals("FOCUS_NEXTSTEP", WeeklyReview.focus(0, 0, 1, 1))
        assertEquals("FOCUS_DM", WeeklyReview.focus(0, 0, 1, 0))
    }

    @Test fun focusOkWhenNothingFlagged() {
        assertEquals("FOCUS_OK", WeeklyReview.focus(0, 0, 0, 0))
    }
}
