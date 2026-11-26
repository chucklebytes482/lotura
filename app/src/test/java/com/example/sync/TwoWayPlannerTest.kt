package com.example.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoWayPlannerTest {

    private fun local(hash: String) = LocalFile(hash)
    private fun remote(hash: String?, ref: String = "ref-1", native: String? = null) =
        RemoteFile(ref = ref, hash = hash, nativeMime = native)
    private fun base(localHash: String?, remoteHash: String?, ref: String? = "ref-1") =
        PathState(localHash, remoteHash, ref)

    // ---------- Tanpa base (belum pernah sync) ----------

    @Test
    fun noBase_localOnly_uploadsAsNew() {
        val a = TwoWayPlanner.plan("a.md", local("h1"), null, null)
        assertEquals(PlannedAction.Upload("a.md", local("h1"), null), a)
    }

    @Test
    fun noBase_remoteOnly_downloads() {
        val a = TwoWayPlanner.plan("a.md", null, remote("r1"), null)
        assertEquals(PlannedAction.Download("a.md", remote("r1")), a)
    }

    @Test
    fun noBase_sameContent_recordsWithoutTransfer() {
        val a = TwoWayPlanner.plan("a.md", local("same"), remote("same"), null)
        assertTrue(a is PlannedAction.Record)
    }

    @Test
    fun noBase_differentContent_isConflict() {
        val a = TwoWayPlanner.plan("a.md", local("h1"), remote("r1"), null)
        assertTrue(a is PlannedAction.Conflict)
    }

    @Test
    fun noBase_nativeRemote_neverConflicts() {
        val a = TwoWayPlanner.plan("Doc", local("h1"), remote(null, native = "application/vnd.google-apps.document"), null)
        assertTrue(a is PlannedAction.Record)
    }

    @Test
    fun noBothSides_isUnchanged() {
        assertEquals(PlannedAction.Unchanged("x"), TwoWayPlanner.plan("x", null, null, null))
    }

    // ---------- Dengan base ----------

    @Test
    fun withBase_noChanges_isUnchanged() {
        val a = TwoWayPlanner.plan("a.md", local("h1"), remote("r1"), base("h1", "r1"))
        assertEquals(PlannedAction.Unchanged("a.md"), a)
    }

    @Test
    fun withBase_localChangedOnly_uploadsOverExisting() {
        val rem = remote("r1")
        val a = TwoWayPlanner.plan("a.md", local("h2"), rem, base("h1", "r1"))
        assertEquals(PlannedAction.Upload("a.md", local("h2"), rem), a)
    }

    @Test
    fun withBase_remoteChangedOnly_downloads() {
        val rem = remote("r2")
        val a = TwoWayPlanner.plan("a.md", local("h1"), rem, base("h1", "r1"))
        assertEquals(PlannedAction.Download("a.md", rem), a)
    }

    @Test
    fun withBase_bothChangedDifferently_isConflict() {
        val a = TwoWayPlanner.plan("a.md", local("h2"), remote("r2"), base("h1", "r1"))
        assertTrue(a is PlannedAction.Conflict)
    }

    @Test
    fun withBase_bothChangedToSameContent_records() {
        val a = TwoWayPlanner.plan("a.md", local("x"), remote("x"), base("h1", "r1"))
        assertTrue(a is PlannedAction.Record)
    }

    @Test
    fun withBase_remoteDeletedLocalUnchanged_trashesLocal() {
        val a = TwoWayPlanner.plan("a.md", local("h1"), null, base("h1", "r1"))
        assertEquals(PlannedAction.TrashLocal("a.md"), a)
    }

    @Test
    fun withBase_remoteDeletedButLocalEdited_uploadsAsNew() {
        val a = TwoWayPlanner.plan("a.md", local("h2"), null, base("h1", "r1"))
        assertEquals(PlannedAction.Upload("a.md", local("h2"), null), a)
    }

    @Test
    fun withBase_localDeletedRemoteUnchanged_trashesRemote() {
        val rem = remote("r1")
        val a = TwoWayPlanner.plan("a.md", null, rem, base("h1", "r1"))
        assertEquals(PlannedAction.TrashRemote("a.md", rem), a)
    }

    @Test
    fun withBase_localDeletedRemoteChanged_downloadsBack() {
        val rem = remote("r2")
        val a = TwoWayPlanner.plan("a.md", null, rem, base("h1", "r1"))
        assertEquals(PlannedAction.Download("a.md", rem), a)
    }

    @Test
    fun withBase_bothGone_forgets() {
        assertEquals(PlannedAction.Forget("a.md"), TwoWayPlanner.plan("a.md", null, null, base("h1", "r1")))
    }

    @Test
    fun withBase_nativeUnchanged_isUnchanged() {
        val nat = remote(null, native = "application/vnd.google-apps.spreadsheet")
        val a = TwoWayPlanner.plan("Sheet", local("h1"), nat, base("h1", null))
        assertEquals(PlannedAction.Unchanged("Sheet"), a)
    }

    @Test
    fun withBase_nativeLocalChanged_uploadsKeepingNativeInfo() {
        val nat = remote(null, native = "application/vnd.google-apps.document")
        val a = TwoWayPlanner.plan("Doc", local("h2"), nat, base("h1", null))
        assertEquals(PlannedAction.Upload("Doc", local("h2"), nat), a)
    }

    // ---------- Planner untuk banyak path ----------

    @Test
    fun planAll_sortsByPath_andCoversAllSides() {
        val actions = TwoWayPlanner.planAll(
            local = mapOf("b.md" to local("hb")),
            remote = mapOf("a.md" to remote("ra")),
            base = emptyMap()
        )
        assertEquals(listOf("a.md", "b.md"), actions.map { it.path })
        assertTrue(actions[0] is PlannedAction.Download)
        assertTrue(actions[1] is PlannedAction.Upload)
    }

    // ---------- Nama salinan konflik ----------

    @Test
    fun conflictCopy_insertsStampBeforeExtension() {
        assertEquals("notes/a (konflik 20260101-120000).md",
            TwoWayPlanner.conflictCopyPath("notes/a.md", "20260101-120000"))
    }

    @Test
    fun conflictCopy_usesLastDotOnly() {
        assertEquals("x.tar (konflik S).gz", TwoWayPlanner.conflictCopyPath("x.tar.gz", "S"))
    }

    @Test
    fun conflictCopy_noExtension() {
        assertEquals("README (konflik S)", TwoWayPlanner.conflictCopyPath("README", "S"))
    }

    @Test
    fun conflictCopy_dotFileKeepsWholeName() {
        assertEquals(".env (konflik S)", TwoWayPlanner.conflictCopyPath(".env", "S"))
    }

    @Test
    fun conflictCopy_rootLevelFile() {
        assertEquals("b (konflik S).txt", TwoWayPlanner.conflictCopyPath("b.txt", "S"))
    }
}
