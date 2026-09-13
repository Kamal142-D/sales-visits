package com.sales.visits

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandTest {

    private val tasks = listOf(
        PlanItem(id = "t1", client = "Ahmed", date = "2026-09-17", action = "call back", status = "OPEN"),
        PlanItem(id = "t2", client = "Sara", date = "2026-09-17", action = "send catalog", status = "CANCELLED"),
    )

    @Test fun dedupDetectsExistingOpenTask() {
        assertTrue(taskExists(tasks, "Ahmed", "call back", "2026-09-17"))
        assertTrue(taskExists(tasks, "ahmed", "CALL BACK", "2026-09-17"))   // case-insensitive
    }

    @Test fun dedupIgnoresCancelledAndDifferentDate() {
        // A cancelled task doesn't block re-adding.
        assertFalse(taskExists(tasks, "Sara", "send catalog", "2026-09-17"))
        // Different date is a different task.
        assertFalse(taskExists(tasks, "Ahmed", "call back", "2026-09-18"))
    }

    @Test fun commandIntentDefaultsToUnknown() {
        assertTrue(CommandIntent().action == CommandAction.UNKNOWN)
    }
}
