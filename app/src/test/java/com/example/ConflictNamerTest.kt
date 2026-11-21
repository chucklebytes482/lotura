package com.example

import com.example.sync.ConflictNamer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ConflictNamerTest {

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, min: Int, sec: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(year, month - 1, day, hour, min, sec)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun buildConflictPath_insertsTimestampBeforeExtension() {
        val ts = utcMillis(2026, 11, 20, 8, 30, 15)
        val result = ConflictNamer.buildConflictPath(
            relativePath = "catatan/harian.md",
            timestampMillis = ts,
            timeZone = TimeZone.getTimeZone("UTC")
        )
        assertEquals("catatan/harian (konflik 20261120-083015).md", result)
        assertTrue(ConflictNamer.isConflictCopy(result))
    }

    @Test
    fun buildConflictPath_handlesFilesWithoutExtensionAndDotfiles() {
        val ts = utcMillis(2026, 11, 20, 9, 5, 0)
        val noExt = ConflictNamer.buildConflictPath("README", ts, TimeZone.getTimeZone("UTC"))
        assertEquals("README (konflik 20261120-090500)", noExt)
        assertTrue(ConflictNamer.isConflictCopy(noExt))

        val dotfile = ConflictNamer.buildConflictPath(".env", ts, TimeZone.getTimeZone("UTC"))
        assertEquals(".env (konflik 20261120-090500)", dotfile)
        assertFalse(ConflictNamer.isConflictCopy("catatan/harian.md"))
    }
}
