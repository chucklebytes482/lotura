package com.example.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncModelsTest {

    @Test
    fun syncPath_allExtensionsAndNoExtension_yes() {
        assertTrue(isSyncPath("encrypt/ketikku.vxenc"))
        assertTrue(isSyncPath("catatan.md"))
        assertTrue(isSyncPath("disk/brankas.hc"))
        assertTrue(isSyncPath("Brankas.VXENC"))
        assertTrue(isSyncPath("gambar.png"))
        assertTrue(isSyncPath("README"))
        assertTrue(isSyncPath("nested/a.tmp"))
    }

    @Test
    fun syncPath_hiddenOrInvalid_no() {
        assertFalse(isSyncPath("a/.env"))
        assertFalse(isSyncPath("a/../file"))
        assertFalse(isSyncPath("/absolute/file"))
        assertFalse(isSyncPath(".rahasia/ketikku.vxenc"))
    }

    @Test
    fun skip_sameSize_countsAsSkippedWithReason() {
        val r = OpResult.skip(SkipReason.SAME_SIZE, "a/b.md")
        assertEquals(1, r.skipped)
        assertEquals(0, r.hidden)
        assertEquals(listOf(SkipDetail("a/b.md", SkipReason.SAME_SIZE)), r.skipDetails)
    }

    @Test
    fun skip_hidden_countsAsHiddenNotSkipped() {
        val r = OpResult.skip(SkipReason.HIDDEN, ".git")
        assertEquals(0, r.skipped)
        assertEquals(1, r.hidden)
        assertEquals(1, r.skipDetails.size)
    }

    @Test
    fun plus_sumsCountsAndConcatenatesDetails() {
        val a = OpResult(transferred = 1, skipped = 1, skipDetails = listOf(SkipDetail("x", SkipReason.SAME_SIZE)))
        val b = OpResult(failed = 2, hidden = 1, skipDetails = listOf(SkipDetail(".y", SkipReason.HIDDEN)))
        val sum = a + b
        assertEquals(1, sum.transferred)
        assertEquals(1, sum.skipped)
        assertEquals(2, sum.failed)
        assertEquals(1, sum.hidden)
        assertEquals(listOf("x", ".y"), sum.skipDetails.map { it.path })
    }

    @Test
    fun invariant_detailsMatchSkippedPlusHidden() {
        val total = listOf(
            OpResult.skip(SkipReason.SAME_SIZE, "a"),
            OpResult.skip(SkipReason.TOO_LARGE_FOR_GITHUB, "b"),
            OpResult.skip(SkipReason.HIDDEN, ".c"),
            OpResult(transferred = 3)
        ).fold(OpResult()) { acc, r -> acc + r }
        assertEquals(total.skipped + total.hidden, total.skipDetails.size)
    }
}
