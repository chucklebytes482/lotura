package com.example.remote

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RemoteNamesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `hidden and ambiguous names are rejected before upload`() {
        listOf(".env", ".github", ".gitkeep", " .env", ".", "..", "folder/.env", "a\\b", "a\n", "", " ").forEach {
            assertFalse(it, RemoteNames.validNewName(it))
        }
        listOf("README", "catatan.txt", "Folder baru", "data-2026", "naïve").forEach {
            assertTrue(it, RemoteNames.validNewName(it))
        }
        assertFalse(RemoteNames.visible(".syncbebas_tmp"))
        assertFalse(RemoteNames.visible(".github"))
        assertFalse(RemoteNames.validNewName("é".repeat(128)))
    }

    @Test fun `downloading sanitizes components and avoids overwriting files`() {
        val parent = temp.newFolder()
        val old = File(parent, "foo_bar.txt").apply { writeText("keep") }
        val next = RemoteNames.unusedChild(parent, "foo/bar.txt", false)
        assertEquals("foo_bar (2).txt", next.name)
        assertEquals("keep", old.readText())
        assertEquals(parent, next.parentFile)
        assertEquals("_.._escape", RemoteNames.localName("/../escape"))
        assertTrue(RemoteNames.localName("🙂".repeat(200)).toByteArray().size <= 230)
    }

    @Test fun `duplicate directory names get suffixes without treating dots as extensions`() {
        val parent = temp.newFolder()
        assertTrue(File(parent, "project.v1").mkdir())
        assertEquals("project.v1 (2)", RemoteNames.unusedChild(parent, "project.v1", true).name)
    }
}
