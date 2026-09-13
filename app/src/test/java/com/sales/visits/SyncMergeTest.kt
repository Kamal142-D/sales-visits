package com.sales.visits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private fun bk(vararg visits: Visit) = AppBackup(
        exportedAt = "", visits = visits.toList(), customers = emptyList(), plan = emptyList(),
    )

    private fun v(id: String, client: String) = Visit(id = id, client = client, date = "2026-01-01")

    private fun visitsOf(base: AppBackup, local: AppBackup, remote: AppBackup) =
        SyncMerge.merge(base, local, remote).backup.visits.associateBy { it.id }

    // plan test #5a: two devices edit DIFFERENT records → both survive.
    @Test fun differentRecordsBothKept() {
        val base = bk(v("v1", "base1"), v("v2", "base2"))
        val local = bk(v("v1", "localEdit"), v("v2", "base2"))
        val remote = bk(v("v1", "base1"), v("v2", "remoteEdit"))
        val out = visitsOf(base, local, remote)
        assertEquals("localEdit", out["v1"]?.client)
        assertEquals("remoteEdit", out["v2"]?.client)
    }

    // plan test #5b: two devices edit the SAME record differently → keep BOTH (no silent loss).
    @Test fun sameRecordConflictKeepsBoth() {
        val base = bk(v("v1", "base"))
        val local = bk(v("v1", "AAA"))
        val remote = bk(v("v1", "ZZZ"))
        val result = SyncMerge.merge(base, local, remote)
        val out = result.backup.visits.associateBy { it.id }
        assertEquals(1, result.conflicts)
        assertNotNull(out["v1"])                    // a winner keeps the id
        assertNotNull(out["v1~c"])                  // the loser is preserved as a conflict copy
        val clients = out.values.map { it.client }.toSet()
        assertTrue("AAA" in clients && "ZZZ" in clients)   // neither version lost
    }

    // Conflict resolution is deterministic & symmetric: swapping local/remote yields the same result.
    @Test fun conflictIsSymmetric() {
        val base = bk(v("v1", "base"))
        val a = bk(v("v1", "AAA"))
        val b = bk(v("v1", "ZZZ"))
        val ab = SyncMerge.merge(base, a, b).backup.visits.associateBy { it.id }
        val ba = SyncMerge.merge(base, b, a).backup.visits.associateBy { it.id }
        assertEquals(ab["v1"]?.client, ba["v1"]?.client)
        assertEquals(ab["v1~c"]?.client, ba["v1~c"]?.client)
    }

    // Re-merging the merged result against itself as base creates no further conflict copies.
    @Test fun conflictConverges() {
        val base = bk(v("v1", "base"))
        val merged = SyncMerge.merge(base, bk(v("v1", "AAA")), bk(v("v1", "ZZZ"))).backup
        val again = SyncMerge.merge(merged, merged, merged)
        assertEquals(0, again.conflicts)
        assertEquals(merged.visits.map { it.id }.toSet(), again.backup.visits.map { it.id }.toSet())
    }

    // plan: delete on one side, the other side unchanged → honor the delete.
    @Test fun deleteHonoredWhenOtherUnchanged() {
        val base = bk(v("v1", "base"))
        val local = bk()                       // deleted locally
        val remote = bk(v("v1", "base"))       // unchanged
        assertNull(visitsOf(base, local, remote)["v1"])
    }

    // plan test #5c: delete on one side vs edit on the other → keep the edit (never lose data).
    @Test fun deleteVsEditKeepsTheEdit() {
        val base = bk(v("v1", "base"))
        val local = bk()                       // deleted locally
        val remote = bk(v("v1", "edited"))     // edited remotely
        assertEquals("edited", visitsOf(base, local, remote)["v1"]?.client)
    }

    // A record created after the base on one side is never mistaken for a delete on the other.
    @Test fun newLocalRecordSurvives() {
        val base = bk()
        val local = bk(v("v9", "brandNew"))
        val remote = bk()
        assertEquals("brandNew", visitsOf(base, local, remote)["v9"]?.client)
    }

    // Empty base (first ever three-way): union both sides, lose nothing.
    @Test fun emptyBaseUnionsBothSides() {
        val out = visitsOf(bk(), bk(v("a", "L")), bk(v("b", "R")))
        assertEquals("L", out["a"]?.client)
        assertEquals("R", out["b"]?.client)
    }
}
