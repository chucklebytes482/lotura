package com.example.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceTreeRulesTest {

    private val drive = "drive:a@x.id"
    private val repo = "github:owner/repo"

    private fun item(sourceKey: String, path: String, isFolder: Boolean = false) = TreeItem(
        sourceKey = sourceKey,
        path = path,
        name = path.substringAfterLast('/'),
        isFolder = isFolder,
        size = if (isFolder) null else 1L,
        remoteId = null,
        nativeMime = null
    )

    @Test
    fun labelUntukDriveDanGithub() {
        assertEquals("Drive · a@x.id", SourceTreeRules.sourceLabel(drive))
        assertEquals("GitHub · owner/repo", SourceTreeRules.sourceLabel(repo))
    }

    @Test
    fun ownsKeyHanyaCocokSumberDanTurunannya() {
        assertTrue(SourceTreeRules.ownsKey(drive, drive))
        assertTrue(SourceTreeRules.ownsKey(drive, "$drive|Folder"))
        assertFalse(SourceTreeRules.ownsKey(drive, "drive:a@x.id.lain|Folder"))
        assertFalse(SourceTreeRules.ownsKey(drive, repo))
    }

    @Test
    fun loadedWithoutSourceMembuangSemuaTurunan() {
        val loaded = setOf(drive, "$drive|A", repo, "drive:b@x.id")
        assertEquals(setOf(repo, "drive:b@x.id"), SourceTreeRules.loadedWithoutSource(loaded, drive))
    }

    @Test
    fun childPrefixAkarDanFolder() {
        assertEquals("$drive|", SourceTreeRules.childPrefix(drive, ""))
        assertEquals("$drive|A/B/", SourceTreeRules.childPrefix(drive, "A/B"))
    }

    @Test
    fun childPathTanpadanDenganInduk() {
        assertEquals("Berkas", SourceTreeRules.childPath("", "Berkas"))
        assertEquals("A/B/Berkas", SourceTreeRules.childPath("A/B", "Berkas"))
    }

    @Test
    fun replaceDriveLevelAkarMembuangSemuaLamaLaluGanti() {
        val existing = listOf(
            item(drive, "Lama", isFolder = true),
            item(drive, "Lama/anak.md"),
            item(repo, "Lain.md")
        )
        val fresh = listOf(item(drive, "Baru", isFolder = true))
        val result = SourceTreeRules.replaceDriveLevel(existing, drive, "", fresh)
        assertEquals(listOf(repo to "Lain.md", drive to "Baru"), result.map { it.sourceKey to it.path })
    }

    @Test
    fun replaceDriveLevelFolderHanyaGantiAnaknya() {
        val existing = listOf(
            item(drive, "A", isFolder = true),
            item(drive, "A/lama.md"),
            item(drive, "A/B", isFolder = true),
            item(drive, "A/B/dalam.md"),
            item(drive, "C.md")
        )
        val fresh = listOf(item(drive, "A/baru.md"))
        val result = SourceTreeRules.replaceDriveLevel(existing, drive, "A", fresh)
        assertEquals(
            listOf("A", "C.md", "A/baru.md"),
            result.map { it.path }
        )
    }

    @Test
    fun loadedWithoutPrefixMembuangSubtree() {
        val loaded = setOf(drive, "$drive|A", "$drive|A/B", "$drive|AB", repo)
        assertEquals(
            setOf(drive, "$drive|A", "$drive|AB", repo),
            SourceTreeRules.loadedWithoutPrefix(loaded, "$drive|A/")
        )
    }
}
