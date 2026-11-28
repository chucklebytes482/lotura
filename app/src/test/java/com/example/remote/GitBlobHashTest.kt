package com.example.remote

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GitBlobHashTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `empty file matches git blob hash`() {
        val file = File(temp.root, "empty.txt").apply { writeText("") }
        assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", GitBlobHash.of(file))
    }

    @Test fun `text file matches git blob hash`() {
        val file = File(temp.root, "hello.txt").apply { writeText("hello\n") }
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", GitBlobHash.of(file))
    }
}
