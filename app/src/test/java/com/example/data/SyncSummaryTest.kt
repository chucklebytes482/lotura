package com.example.data

import com.example.sync.OpResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncSummaryTest {

    @Test
    fun fromResult_allOk_isOk() {
        val s = SyncSummary.fromResult(OpResult(transferred = 2, skipped = 3, failed = 0, hidden = 1), 100L)
        assertEquals(SyncStatus.OK, s.status)
        assertEquals(5, s.okCount)
        assertEquals(0, s.failCount)
        assertEquals(1, s.hiddenSkipped)
        assertEquals(100L, s.lastSyncAtMillis)
    }

    @Test
    fun fromResult_someFailed_isPartial() {
        val s = SyncSummary.fromResult(OpResult(transferred = 1, failed = 2), 1L)
        assertEquals(SyncStatus.SEBAGIAN, s.status)
        assertEquals(1, s.okCount)
        assertEquals(2, s.failCount)
    }

    @Test
    fun fromResult_allFailed_isFailed() {
        val s = SyncSummary.fromResult(OpResult(failed = 4), 1L)
        assertEquals(SyncStatus.GAGAL, s.status)
        assertEquals(0, s.okCount)
    }

    @Test
    fun encodeDecode_roundTrip() {
        val original = SyncSummary(1700000000123L, SyncStatus.SEBAGIAN, 7, 2, 3)
        assertEquals(original, SyncSummary.decode(SyncSummary.encode(original)))
    }

    @Test
    fun decode_invalidInput_returnsNull() {
        assertNull(SyncSummary.decode(""))
        assertNull(SyncSummary.decode("1|OK|1|2"))
        assertNull(SyncSummary.decode("abc|OK|1|2|3"))
        assertNull(SyncSummary.decode("1|NOPE|1|2|3"))
    }
}
