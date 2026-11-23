package com.example

import com.example.sync.SyncDecisions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDecisionsTest {

    @Test
    fun sameSize_skips() {
        assertFalse(SyncDecisions.shouldTransfer(sourceSize = 120L, targetSize = 120L))
    }

    @Test
    fun differentSize_transfers() {
        assertTrue(SyncDecisions.shouldTransfer(sourceSize = 120L, targetSize = 80L))
    }

    @Test
    fun missingTarget_transfers() {
        assertTrue(SyncDecisions.shouldTransfer(sourceSize = 120L, targetSize = null))
    }

    @Test
    fun nativeGoogleDoc_alwaysTransfers() {
        assertTrue(SyncDecisions.shouldTransfer(sourceSize = null, targetSize = null))
        assertTrue(SyncDecisions.shouldTransfer(sourceSize = null, targetSize = 10L))
        assertTrue(SyncDecisions.shouldTransfer(sourceSize = 10L, targetSize = null))
    }
}
