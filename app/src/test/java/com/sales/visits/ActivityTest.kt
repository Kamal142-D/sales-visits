package com.sales.visits

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityTest {

    @Test fun timelineMergesNewestFirst() {
        val visits = listOf(
            Visit(id = "v1", client = "Acme", date = "2026-09-10", time = "09:00"),
            Visit(id = "v2", client = "Acme", date = "2026-09-12", time = "11:00"),
        )
        val acts = listOf(
            Activity(id = "a1", customerName = "Acme", date = "2026-09-11", time = "15:00", type = "CALL"),
            Activity(id = "a2", customerName = "Acme", date = "2026-09-12", time = "08:00", type = "MEETING"),
        )
        val tl = mergeTimeline(visits, acts)
        assertEquals(4, tl.size)
        // Newest first: v2 (09-12 11:00) > a2 (09-12 08:00) > a1 (09-11) > v1 (09-10)
        assertEquals("2026-09-12T11:00", tl[0].sortKey)
        assertEquals("2026-09-12T08:00", tl[1].sortKey)
        assertTrue(tl[0] is VisitItem && (tl[0] as VisitItem).visit.id == "v2")
        assertTrue(tl[2] is ActivityItem && (tl[2] as ActivityItem).activity.id == "a1")
    }

    @Test fun activityJsonRoundTrip() {
        val json = Json { ignoreUnknownKeys = true }
        val a = Activity(
            id = "a1", customerId = "c1", customerName = "Acme", opportunityId = "o1",
            type = "CALL", date = "2026-09-12", summary = "Discussed pricing", result = "NO_ANSWER",
        )
        val back = json.decodeFromString(Activity.serializer(), json.encodeToString(Activity.serializer(), a))
        assertEquals(a, back)
    }

    @Test fun resultDefaultsAreHonest() {
        // A logged call is not "connected" unless explicitly set; blank/unknown → NONE.
        assertEquals(ActivityResult.NONE, activityResultEnum(""))
        assertEquals(ActivityResult.NONE, activityResultEnum(null))
        assertEquals(ActivityResult.NO_ANSWER, activityResultEnum("NO_ANSWER"))
        assertEquals(ActivityType.MEETING, activityTypeEnum("meeting"))
        assertEquals(ActivityType.CALL, activityTypeEnum("garbage"))
    }

    @Test fun migrationLinksActivityToCustomerAndSurvivesInBackup() {
        val cu = Customer(id = "c1", name = "Acme")
        val backup = AppBackup(
            exportedAt = "", visits = emptyList(), customers = listOf(cu), plan = emptyList(),
            activities = listOf(Activity(id = "a1", customerName = "acme", type = "CALL")),
        )
        val migrated = DataMigration.migrate(backup) { "id1" }
        assertEquals("c1", migrated.activities.single().customerId)
    }

    @Test fun syncMergeKeepsActivitiesFromBothSides() {
        val base = AppBackup(exportedAt = "", visits = emptyList(), customers = emptyList(), plan = emptyList())
        val local = base.copy(activities = listOf(Activity(id = "a1", summary = "local")))
        val remote = base.copy(activities = listOf(Activity(id = "a2", summary = "remote")))
        val merged = SyncMerge.merge(base, local, remote).backup
        assertEquals(setOf("a1", "a2"), merged.activities.map { it.id }.toSet())
    }
}
