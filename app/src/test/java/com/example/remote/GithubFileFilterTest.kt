package com.example.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubFileFilterTest {
    @Test
    fun allowsMarkdownJsonImagesAndDocuments() {
        listOf("README.md", "data.JSON", "logo.PNG", "photo.jpeg", "spec.pdf", "notes.docx", "sheet.xlsx", "slides.pptx", "a.csv", "b.txt")
            .forEach { assertTrue(it, GithubFileFilter.allows(it)) }
    }

    @Test
    fun rejectsOtherFiles() {
        listOf("main.kt", "app.js", "archive.zip", "video.mp4", "Makefile", ".md", "noext", "trailing.")
            .forEach { assertFalse(it, GithubFileFilter.allows(it)) }
    }
}
