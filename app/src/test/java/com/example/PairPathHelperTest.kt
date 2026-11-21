package com.example

import com.example.data.PairPathHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class PairPathHelperTest {

    @Test
    fun resolveLocalTargetPath_appendsSourceFolderNameWhenDifferent() {
        val result = PairPathHelper.resolveLocalTargetPath(
            basePath = "/storage/emulated/0/Documents",
            sourceFolderName = "docs"
        )
        assertEquals("/storage/emulated/0/Documents/docs", result)
    }

    @Test
    fun resolveLocalTargetPath_doesNotDuplicateWhenBaseAlreadyMatchesSourceName() {
        val result = PairPathHelper.resolveLocalTargetPath(
            basePath = "/storage/emulated/0/Documents/docs",
            sourceFolderName = "docs"
        )
        assertEquals("/storage/emulated/0/Documents/docs", result)
    }

    @Test
    fun sourceFolderNameForGithub_usesLeafSegmentOfNestedFolder() {
        assertEquals("guides", PairPathHelper.sourceFolderNameForGithub("docs/guides/"))
        assertEquals("docs", PairPathHelper.sourceFolderNameForGithub("/docs"))
        assertEquals("", PairPathHelper.sourceFolderNameForGithub(""))
    }

    @Test
    fun pairNameForGithub_combinesRepoAndSubfolder() {
        assertEquals("lotura/docs", PairPathHelper.pairNameForGithub("lotura", "docs"))
        assertEquals("lotura/docs/guides", PairPathHelper.pairNameForGithub("lotura", "/docs/guides/"))
    }

    @Test
    fun resolveDefaultLocalTargetPath_usesDocumentsBaseDir() {
        assertEquals(
            "/storage/emulated/0/Documents/Catatan",
            PairPathHelper.resolveDefaultLocalTargetPath("Catatan")
        )
        assertEquals(
            "/storage/emulated/0/Documents",
            PairPathHelper.resolveDefaultLocalTargetPath("")
        )
    }
}
