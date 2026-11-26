package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class RowActivityTest {

    @Test
    fun sumberTanpaStatusIsIdle() {
        assertEquals(RowActivity.IDLE, activityOf("drive:a@x.id", emptySet(), emptySet(), emptySet()))
    }

    @Test
    fun sedangSyncMengalahkanAntreDanMuat() {
        val key = "github:o/r"
        assertEquals(
            RowActivity.RUNNING,
            activityOf(key, loading = setOf(key), queued = setOf(key), busy = setOf(key))
        )
    }

    @Test
    fun antreMengalahkanMuat() {
        val key = "github:o/r"
        assertEquals(RowActivity.QUEUED, activityOf(key, loading = setOf(key), queued = setOf(key), busy = emptySet()))
    }

    @Test
    fun hanyaMuatBerartiLoading() {
        assertEquals(RowActivity.LOADING, activityOf("drive:a", loading = setOf("drive:a"), queued = emptySet(), busy = emptySet()))
    }
}
