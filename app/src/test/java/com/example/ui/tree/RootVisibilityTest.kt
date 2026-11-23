package com.example.ui.tree

import com.example.sync.TreeItem
import com.example.sync.isHiddenPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootVisibilityTest {

    private fun folder(path: String) = TreeItem("s", path, path.substringAfterLast('/'), true, null, null, null)
    private fun file(path: String) = TreeItem("s", path, path.substringAfterLast('/'), false, 10L, "id", null)

    @Test
    fun `hanya folder dasar yang tampil`() {
        val items = listOf(
            folder("Foto"),
            folder("Foto/2025"),
            file("catatan.txt"),
            file("Foto/a.jpg")
        )
        assertEquals(listOf("Foto"), rootVisibleItems(items).map { it.path })
    }

    @Test
    fun `item tersembunyi dan file dasar tidak tampil`() {
        val items = listOf(
            folder(".git"),
            folder(".config"),
            folder("Dokumen"),
            file(".env")
        )
        assertEquals(listOf("Dokumen"), rootVisibleItems(items).map { it.path })
    }

    @Test
    fun `urutan folder dasar alfabetis tanpa peduli huruf besar`() {
        val items = listOf(folder("zeta"), folder("Alfa"), folder("beta"))
        assertEquals(listOf("Alfa", "beta", "zeta"), rootVisibleItems(items).map { it.path })
    }

    @Test
    fun `isHiddenPath mendeteksi segmen tersembunyi di level mana pun`() {
        assertTrue(isHiddenPath(".github/workflows/ci.yml"))
        assertTrue(isHiddenPath("src/.env"))
        assertTrue(isHiddenPath("src/.cache/x"))
        assertFalse(isHiddenPath("src/main/Main.kt"))
        assertFalse(isHiddenPath("docs/a.b"))
    }
}
