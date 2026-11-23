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
    fun rootGithubFolderName_extractsTopLevelFolderSegment() {
        assertEquals("docs", PairPathHelper.rootGithubFolderName("/docs/guides/"))
        assertEquals("panduan", PairPathHelper.rootGithubFolderName("panduan"))
        assertEquals("", PairPathHelper.rootGithubFolderName(" / "))
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

    @Test
    fun resolveDefaultLocalPathForDrive_usesGoogleDriveSourceFolder() {
        assertEquals(
            "/storage/emulated/0/Documents/Google Drive/Catatan Kerja",
            PairPathHelper.resolveDefaultLocalPathForDrive("Catatan Kerja")
        )
        assertEquals(
            "/storage/emulated/0/Documents/Google Drive",
            PairPathHelper.resolveDefaultLocalPathForDrive("")
        )
    }

    @Test
    fun resolveDefaultLocalPathForGithub_nestsGithubSourceRepoAndFolderSegments() {
        assertEquals(
            "/storage/emulated/0/Documents/GitHub/Petapikir/panduan",
            PairPathHelper.resolveDefaultLocalPathForGithub("Petapikir", "panduan")
        )
        assertEquals(
            "/storage/emulated/0/Documents/GitHub/Petapikir/docs/guides",
            PairPathHelper.resolveDefaultLocalPathForGithub("Petapikir", "/docs/guides/")
        )
        assertEquals(
            "/storage/emulated/0/Documents/GitHub/Repo_A/sub_1/folder_2",
            PairPathHelper.resolveDefaultLocalPathForGithub("Repo:A", "sub*1/folder?2")
        )
    }

    @Test
    fun normalizeGithubFolderPath_trimsSlashesAndSpaces() {
        assertEquals("docs/guides", PairPathHelper.normalizeGithubFolderPath(" /docs//guides/ "))
        assertEquals("", PairPathHelper.normalizeGithubFolderPath(" / "))
    }
}
