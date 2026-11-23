package com.example

import com.example.data.SyncCardMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCardMetricsTest {

    @Test
    fun parse_driveStatusWithExportsMergesExportsIntoDownloaded() {
        val metrics = SyncCardMetrics.parse(
            lastStatus = "Drive: ↑3 ↓2 Ekspor:4 Hapus:1 Konflik:2",
            lastError = null
        )
        assertEquals(3, metrics.uploaded)
        assertEquals(6, metrics.downloaded)
        assertEquals(1, metrics.deleted)
        assertEquals(2, metrics.conflicts)
        assertFalse(metrics.hasError)
    }

    @Test
    fun parse_githubStatusExtractsAllFourMetrics() {
        val metrics = SyncCardMetrics.parse(
            lastStatus = "GitHub: ↑5 ↓1 Hapus:2 Konflik:0",
            lastError = null
        )
        assertEquals(5, metrics.uploaded)
        assertEquals(1, metrics.downloaded)
        assertEquals(2, metrics.deleted)
        assertEquals(0, metrics.conflicts)
        assertFalse(metrics.hasError)
    }

    @Test
    fun parse_idleOrNoChangesReturnsAllZeroesWithoutError() {
        val idle = SyncCardMetrics.parse("Siap disinkronkan", null)
        assertEquals(SyncCardMetrics(0, 0, 0, 0, false), idle)

        val clean = SyncCardMetrics.parse("Drive selaras • Tidak ada perubahan", null)
        assertEquals(SyncCardMetrics(0, 0, 0, 0, false), clean)
    }

    @Test
    fun parse_detectsErrorFromLastErrorOrErrorStatus() {
        val fromErrorField = SyncCardMetrics.parse("Siap disinkronkan", "Koneksi terputus")
        assertTrue(fromErrorField.hasError)

        val fromStatus = SyncCardMetrics.parse("Perlu otorisasi Google", null)
        assertTrue(fromStatus.hasError)
    }
}
