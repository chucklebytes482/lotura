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
    fun resolveDefaultLocalPathForDrive_usesGoogleDriveEmailAndFolderRoot() {
        assertEquals(
            "/storage/emulated/0/Documents/Google Drive user@gmail.com/Catatan Kerja",
            PairPathHelper.resolveDefaultLocalPathForDrive("user@gmail.com", "Catatan Kerja")
        )
        assertEquals(
            "/storage/emulated/0/Documents/Google Drive user@gmail.com",
            PairPathHelper.resolveDefaultLocalPathForDrive("user@gmail.com", "")
        )
        assertEquals(
            "/storage/emulated/0/Documents/Google Drive",
            PairPathHelper.resolveDefaultLocalPathForDrive("", "")
        )
    }

    @Test
    fun resolveDefaultLocalPathForGithub_usesUsernameRepoAndFolderSegments() {
        assertEquals(
            "/storage/emulated/0/Documents/GitHub chucklebytes482/lotura/panduan",
            PairPathHelper.resolveDefaultLocalPathForGithub("chucklebytes482", "lotura", "panduan")
        )
        assertEquals(
            "/storage/emulated/0/Documents/GitHub chucklebytes482/lotura/docs/guides",
            PairPathHelper.resolveDefaultLocalPathForGithub("chucklebytes482", "lotura", "/docs/guides/")
        )
        assertEquals(
            "/storage/emulated/0/Documents/GitHub Repo_A/Repo_B/sub_1/folder_2",
            PairPathHelper.resolveDefaultLocalPathForGithub("Repo:A", "Repo*B", "sub*1/folder?2")
        )
    }

    @Test
    fun normalizeGithubFolderPath_trimsSlashesAndSpaces() {
        assertEquals("docs/guides", PairPathHelper.normalizeGithubFolderPath(" /docs//guides/ "))
        assertEquals("", PairPathHelper.normalizeGithubFolderPath(" / "))
    }
}
