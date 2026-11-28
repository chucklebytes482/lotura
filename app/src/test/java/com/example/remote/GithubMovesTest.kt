package com.example.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubMovesTest {
    @Test fun `target equal to source folder is rejected`() {
        assertEquals("Pilih folder lain sebagai tujuan.", GithubMoves.invalidTarget(listOf("a.md"), "docs", "docs"))
    }

    @Test fun `moving a folder into itself or its descendant is rejected`() {
        val msg = "Folder tujuan ada di dalam item yang dipindah."
        assertEquals(msg, GithubMoves.invalidTarget(listOf("docs"), "", "docs"))
        assertEquals(msg, GithubMoves.invalidTarget(listOf("docs"), "", "docs/2026"))
        assertNull(GithubMoves.invalidTarget(listOf("docs"), "", "docs-lama"))
    }

    @Test fun `repo root is a valid target from a subfolder`() {
        assertNull(GithubMoves.invalidTarget(listOf("notes/a.md"), "notes", ""))
    }

    @Test fun `folder needs gitkeep only when it becomes empty and is not root`() {
        assertTrue(GithubMoves.needsKeep("docs", emptyList()))
        assertFalse(GithubMoves.needsKeep("docs", listOf("a.md")))
        assertFalse(GithubMoves.needsKeep("", emptyList()))
    }
}
