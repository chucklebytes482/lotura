package com.example.ui.guide

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownLiteTest {

    @Test
    fun headings_countLevelAndNeedSpace() {
        val blocks = parseMarkdownLite("# Judul\n## Sub\n#tanpa spasi")
        assertEquals(MdBlock.Heading(1, "Judul"), blocks[0])
        assertEquals(MdBlock.Heading(2, "Sub"), blocks[1])
        assertEquals(MdBlock.Paragraph("#tanpa spasi"), blocks[2])
    }

    @Test
    fun paragraphLinesJoinUntilBlankLine() {
        val blocks = parseMarkdownLite("baris satu\nbaris dua\n\nlain")
        assertEquals(listOf(MdBlock.Paragraph("baris satu baris dua"), MdBlock.Paragraph("lain")), blocks)
    }

    @Test
    fun bullets_keepIndentLevel() {
        val blocks = parseMarkdownLite("- atas\n  - dalam\n    - lebih dalam\n* bintang")
        assertEquals(
            listOf(
                MdBlock.Bullet("atas", 0),
                MdBlock.Bullet("dalam", 1),
                MdBlock.Bullet("lebih dalam", 2),
                MdBlock.Bullet("bintang", 0)
            ),
            blocks
        )
    }

    @Test
    fun numbered_andDivider() {
        val blocks = parseMarkdownLite("1. Pertama\n2. Kedua\n---")
        assertEquals(
            listOf(
                MdBlock.Numbered("1", "Pertama"),
                MdBlock.Numbered("2", "Kedua"),
                MdBlock.Divider
            ),
            blocks
        )
    }

    @Test
    fun inline_boldItalicCode() {
        val runs = parseInline("a **tebal** b *miring* c `kode`")
        assertEquals(
            listOf(
                MdRun("a ", MdStyle.PLAIN),
                MdRun("tebal", MdStyle.BOLD),
                MdRun(" b ", MdStyle.PLAIN),
                MdRun("miring", MdStyle.ITALIC),
                MdRun(" c ", MdStyle.PLAIN),
                MdRun("kode", MdStyle.CODE)
            ),
            runs
        )
    }

    @Test
    fun inline_unclosedMarkersStayAsText() {
        val runs = parseInline("harga **belum ditutup dan `juga")
        val text = runs.joinToString("") { it.text }
        assertEquals("harga **belum ditutup dan `juga", text)
        assertEquals(true, runs.all { it.style == MdStyle.PLAIN })
    }
}
