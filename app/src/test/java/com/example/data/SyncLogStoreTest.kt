package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sync.SkipDetail
import com.example.sync.SkipReason
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncLogStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sync_log", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun entry(i: Int, details: List<SkipDetail> = emptyList(), counts: Map<SkipReason, Int> = emptyMap()) =
        SyncLogEntry(
            atMillis = i.toLong(),
            source = "Drive · a@b.c",
            label = "folder$i",
            upload = false,
            status = SyncStatus.SEBAGIAN,
            transferred = 1,
            skipped = 2,
            failed = 0,
            error = null,
            hidden = 1,
            skipDetails = details,
            skipCounts = counts
        )

    @Test
    fun roundTrip_keepsSkipDetailsAndCounts() {
        val store = SyncLogStore(context)
        val details = listOf(
            SkipDetail("big.zip", SkipReason.TOO_LARGE_FOR_GITHUB),
            SkipDetail("same.txt", SkipReason.SAME_SIZE)
        )
        val counts = mapOf(SkipReason.TOO_LARGE_FOR_GITHUB to 1, SkipReason.SAME_SIZE to 1)
        store.append(entry(1, details, counts))

        val loaded = SyncLogStore(context).loadAll().single()
        assertEquals(details, loaded.skipDetails)
        assertEquals(counts, loaded.skipCounts)
        assertEquals(1, loaded.hidden)
    }

    @Test
    fun append_keepsNewestFirstAndCapsAtMaxEntries() {
        val store = SyncLogStore(context)
        for (i in 1..(SyncLogStore.MAX_ENTRIES + 5)) store.append(entry(i))
        val all = store.loadAll()
        assertEquals(SyncLogStore.MAX_ENTRIES, all.size)
        assertEquals(SyncLogStore.MAX_ENTRIES + 5L, all.first().atMillis)
    }

    @Test
    fun fromJson_oldEntryWithoutNewFields_stillReads() {
        val old = JSONObject()
            .put("at", 10L)
            .put("source", "GitHub · o/r")
            .put("label", "r")
            .put("upload", true)
            .put("status", "OK")
            .put("transferred", 4)
            .put("skipped", 0)
            .put("failed", 0)
            .put("error", JSONObject.NULL)
        val e = SyncLogEntry.fromJson(old)
        assertEquals(0, e.hidden)
        assertTrue(e.skipDetails.isEmpty())
        assertTrue(e.skipCounts.isEmpty())
        assertEquals(SyncStatus.OK, e.status)
    }

    @Test
    fun sourceKey_roundTripsAndFiltersPerSource() {
        val store = SyncLogStore(context)
        store.append(entry(1).copy(sourceKey = "drive:a@b.c"))
        store.append(entry(2).copy(sourceKey = "github:o/r"))
        store.append(entry(3).copy(sourceKey = "drive:a@b.c"))

        val drive = SyncLogStore(context).loadFor("drive:a@b.c")
        assertEquals(listOf(3L, 1L), drive.map { it.atMillis })
        assertEquals(listOf("drive:a@b.c", "drive:a@b.c"), drive.map { it.sourceKey })
        assertEquals(1, SyncLogStore(context).loadFor("github:o/r").size)
        assertTrue(SyncLogStore(context).loadFor("github:lain/repo").isEmpty())
    }

    @Test
    fun fromJson_oldEntryWithoutSourceKey_isEmpty() {
        val old = JSONObject()
            .put("at", 5L)
            .put("source", "Drive · a@b.c")
            .put("label", "f")
            .put("upload", false)
            .put("status", "OK")
            .put("transferred", 1)
            .put("skipped", 0)
            .put("failed", 0)
            .put("error", JSONObject.NULL)
        assertEquals("", SyncLogEntry.fromJson(old).sourceKey)
    }
}
