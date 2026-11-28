package com.example.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class RemoteZipTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `zip keeps nested files under the root name`() {
        val dir = temp.newFolder("docs").apply {
            File(this, "a.md").writeText("satu")
            File(this, "sub").mkdirs()
            File(this, "sub/b.md").writeText("dua")
        }
        val out = File(temp.root, "docs.zip")
        RemoteZip.zipDirectory(dir, "docs", out)

        assertTrue(out.isFile)
        ZipFile(out).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertTrue(names.contains("docs/a.md"))
            assertTrue(names.contains("docs/sub/b.md"))
            assertEquals("dua", zip.getInputStream(zip.getEntry("docs/sub/b.md")).bufferedReader().readText())
            assertEquals("satu", zip.getInputStream(zip.getEntry("docs/a.md")).bufferedReader().readText())
        }
    }

    @Test fun `zip stores the given comment`() {
        val dir = temp.newFolder("notes").apply { File(this, "a.md").writeText("satu") }
        val out = File(temp.root, "notes.zip")
        RemoteZip.zipDirectory(dir, "notes", out, comment = "tree-sha-123")

        ZipFile(out).use { zip -> assertEquals("tree-sha-123", zip.comment) }
    }
}
