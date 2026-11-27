package com.example.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorAutoSyncTest {
    @Test
    fun labelsMatchIntervalOptions() {
        assertEquals("Setiap 1 jam", MirrorAutoSync.label(1))
        assertEquals("Setiap 6 jam", MirrorAutoSync.label(6))
        assertEquals("Setiap 1 hari", MirrorAutoSync.label(24))
    }

    @Test
    fun jobKeyCombinesEmailParentAndFolder() {
        assertEquals("a@b.com|root|Foto", MirrorAutoSync.mirrorJobKey("a@b.com", "root", "Foto"))
    }
}
