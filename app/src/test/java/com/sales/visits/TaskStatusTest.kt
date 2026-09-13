package com.sales.visits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStatusTest {

    private fun task(done: Boolean = false, status: String = "") =
        PlanItem(id = "t1", client = "Acme", date = "2026-09-13", done = done, status = status)

    @Test fun legacyDoneMapsToStatus() {
        assertEquals(TaskStatus.DONE, task(done = true).statusEnum())
        assertEquals(TaskStatus.OPEN, task(done = false).statusEnum())
    }

    @Test fun explicitStatusWins() {
        assertEquals(TaskStatus.CANCELLED, task(status = "CANCELLED").statusEnum())
        assertEquals(TaskStatus.POSTPONED, task(status = "POSTPONED").statusEnum())
        // A blank/garbage status falls back to the legacy flag.
        assertEquals(TaskStatus.DONE, task(done = true, status = "junk").statusEnum())
    }

    @Test fun cancelledAndPostponedAreNotDone() {
        assertFalse(task(status = "CANCELLED").isDoneTask())
        assertFalse(task(status = "POSTPONED").isDoneTask())
        assertTrue(task(status = "DONE").isDoneTask())
    }

    @Test fun sourceEnumLabels() {
        assertEquals("Assistant", TaskSource.AI.label(true))
        assertEquals("زيارة", TaskSource.VISIT.label(false))
    }

    @Test fun taskRoundTripKeepsNewFields() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val p = task(status = "POSTPONED").copy(opportunityId = "o1", source = "AI", result = "left message", completedAt = "")
        val back = json.decodeFromString(PlanItem.serializer(), json.encodeToString(PlanItem.serializer(), p))
        assertEquals(p, back)
    }
}
