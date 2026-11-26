package com.example.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UploadTreeTest {
    private fun file(name: String, id: String = name) = UploadDocument(name, id, false)
    private fun folder(name: String, id: String = name) = UploadDocument(name, id, true)

    @Test fun `single and bulk files become leaves`() = runBlocking {
        val files = listOf(file("notes.txt"), file("image.png"))
        for (selection in listOf(files.take(1), files)) {
            val result = scanUploadTrees(selection) { error("Files are not traversed") }
            assertEquals(selection.map { GitUploadFile(it.name, it.content) }, result.items.gitUploadFiles())
        }
    }

    @Test fun `bulk folders preserve visible nested files and empty folders`() = runBlocking {
        val local = mapOf(
            "photos" to listOf(file("same.jpg", "photo1"), file(".env"), folder(".secrets"), folder("trip")),
            "trip" to listOf(file("same.jpg", "photo2"), folder("empty")),
            "empty" to emptyList(),
            "work" to listOf(file(" .env"))
        )
        val result = scanUploadTrees(listOf(folder("photos"), folder("work")), local::getValue)
        assertEquals(3, result.skipped)
        assertEquals(listOf("photos", "work"), result.items.map { it.name })
        assertEquals(listOf(
            GitUploadFile("photos/same.jpg", "photo1"),
            GitUploadFile("photos/trip/same.jpg", "photo2"),
            GitUploadFile("photos/trip/empty/.gitkeep", null),
            GitUploadFile("work/.gitkeep", null)
        ), result.items.gitUploadFiles())
        assertEquals(listOf("photo1", "photo2"), result.items.mapUploadFiles { it.content }.gitUploadFiles().mapNotNull { it.content })
    }

    @Test fun `hidden selections and invalid child path components are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { scanUploadTrees(listOf(folder(".github"))) { error("Hidden folder was opened") } }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { scanUploadTrees(listOf(folder("visible"))) { listOf(file("dir/escape")) } }
        }
    }

    @Test fun `duplicate names within the same folder are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { scanUploadTrees(listOf(folder("parent"))) { listOf(file("same"), folder("same", "dir")) } }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { scanUploadTrees(listOf(folder("same", "a"), folder("same", "b"))) { emptyList() } }
        }
    }

    @Test fun `cyclic document providers are rejected`() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { scanUploadTrees(listOf(folder("parent", "same-id"))) { listOf(folder("loop", "same-id")) } }
        }
    }
}
