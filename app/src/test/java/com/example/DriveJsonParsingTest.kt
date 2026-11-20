package com.example

import com.example.drive.DriveJsonParser
import com.example.drive.DriveMimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DriveJsonParsingTest {

    @Test
    fun parseFilePage_parsesFilesNativeDocsShortcutsAndPagination() {
        val json = """
            {
              "nextPageToken": "page-2-token",
              "files": [
                {
                  "id": "file-1",
                  "name": "catatan.md",
                  "mimeType": "text/markdown",
                  "modifiedTime": "2026-11-20T08:00:00.000Z",
                  "size": "1024",
                  "md5Checksum": "abc123",
                  "trashed": false
                },
                {
                  "id": "doc-1",
                  "name": "Rencana Proyek",
                  "mimeType": "application/vnd.google-apps.document",
                  "modifiedTime": "2026-11-20T09:15:30Z",
                  "trashed": false
                },
                {
                  "id": "short-1",
                  "name": "Tautan Folder",
                  "mimeType": "application/vnd.google-apps.shortcut",
                  "shortcutDetails": {
                    "targetId": "folder-real-99",
                    "targetMimeType": "application/vnd.google-apps.folder"
                  }
                }
              ]
            }
        """.trimIndent()

        val page = DriveJsonParser.parseFilePage(json)
        assertEquals("page-2-token", page.nextPageToken)
        assertEquals(3, page.files.size)

        val md = page.files[0]
        assertEquals("file-1", md.id)
        assertEquals(1024L, md.size)
        assertEquals("abc123", md.md5Checksum)
        assertTrue(md.modifiedTimeMillis > 0L)

        val doc = page.files[1]
        val localDocName = DriveMimeTypes.localFileNameForDriveItem(doc.name, doc.mimeType)
        assertEquals("Rencana Proyek.docx", localDocName)
        assertNotNull(DriveMimeTypes.exportSpecFor(doc.mimeType))

        val shortcut = page.files[2]
        assertEquals("folder-real-99", shortcut.shortcutTargetId)
        assertEquals(DriveMimeTypes.FOLDER, shortcut.shortcutTargetMimeType)
    }

    @Test
    fun parseUserInfo_extractsEmailAndDisplayName() {
        val json = """{"user": {"emailAddress": "dev@contoh.id", "displayName": "Pengguna Bebas"}}"""
        val user = DriveJsonParser.parseUserInfo(json)
        assertEquals("dev@contoh.id", user.email)
        assertEquals("Pengguna Bebas", user.displayName)
        assertNull(DriveMimeTypes.exportSpecFor("text/markdown"))
    }
}
