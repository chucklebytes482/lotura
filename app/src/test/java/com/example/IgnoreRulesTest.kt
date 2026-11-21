package com.example

import com.example.sync.IgnoreRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgnoreRulesTest {

    @Test
    fun ignoresStandardGitTmpAndInternalFiles() {
        val rules = IgnoreRules(
            """
            # Komentar harus diabaikan
            .git/
            *.tmp
            *.swp
            .DS_Store
            build/output/
            """.trimIndent()
        )

        assertTrue(rules.isIgnored(".git/HEAD"))
        assertTrue(rules.isIgnored("sub/.git/config"))
        assertTrue(rules.isIgnored("catatan.tmp"))
        assertTrue(rules.isIgnored("folder/draft.swp"))
        assertTrue(rules.isIgnored("docs/.DS_Store"))
        assertTrue(rules.isIgnored("build/output/app.apk"))
        assertTrue(rules.isIgnored(".syncbebas_dl_123.tmp"))

        assertFalse(rules.isIgnored("docs/catatan.md"))
        assertFalse(rules.isIgnored("laporan.docx"))
        assertFalse(rules.isIgnored("src/main/App.kt"))
    }
}
