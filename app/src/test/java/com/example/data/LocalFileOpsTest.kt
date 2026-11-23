package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class LocalFileOpsTest {

    @Test
    fun validName_trimsAndAcceptsPlainNames() {
        assertEquals("a.txt", LocalFileOps.validName("  a.txt  "))
    }

    @Test
    fun validName_rejectsEmptyDotsAndSeparators() {
        assertThrows(IllegalArgumentException::class.java) { LocalFileOps.validName("   ") }
        assertThrows(IllegalArgumentException::class.java) { LocalFileOps.validName(".") }
        assertThrows(IllegalArgumentException::class.java) { LocalFileOps.validName("..") }
        assertThrows(IllegalArgumentException::class.java) { LocalFileOps.validName("a/b") }
    }

    @Test
    fun gitBlobSha_matchesGitForEmptyAndSmallFiles() {
        val empty = File.createTempFile("blob-empty", ".txt")
        val hello = File.createTempFile("blob-hello", ".txt")
        try {
            hello.writeText("hello\n")
            assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", LocalFileOps.gitBlobSha(empty))
            assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", LocalFileOps.gitBlobSha(hello))
        } finally {
            empty.delete()
            hello.delete()
        }
    }
}
