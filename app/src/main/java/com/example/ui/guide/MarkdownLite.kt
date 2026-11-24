package com.example.ui.guide

/**
 * Parser Markdown kecil untuk panduan di dalam aplikasi. Murni Kotlin tanpa Android,
 * jadi bisa dites di JVM. Yang didukung: heading #, paragraf, bullet (- atau *) dengan
 * indentasi, daftar angka, garis pemisah ---, dan inline **tebal**, *miring*, `kode`.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    /** [indent] = tingkat indentasi (2 spasi = 1 tingkat). */
    data class Bullet(val text: String, val indent: Int) : MdBlock
    data class Numbered(val number: String, val text: String) : MdBlock
    data object Divider : MdBlock
}

enum class MdStyle { PLAIN, BOLD, ITALIC, CODE }

data class MdRun(val text: String, val style: MdStyle)

private val numberedRegex = Regex("""^(\d+)\.\s+(.*)$""")

fun parseMarkdownLite(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val para = StringBuilder()

    fun flushParagraph() {
        val value = para.toString().trim()
        if (value.isNotEmpty()) blocks += MdBlock.Paragraph(value)
        para.clear()
    }

    for (raw in text.lines()) {
        val trimmed = raw.trim()
        val leading = raw.length - raw.trimStart().length
        when {
            trimmed.isEmpty() -> flushParagraph()
            trimmed == "---" || trimmed == "***" -> {
                flushParagraph()
                blocks += MdBlock.Divider
            }
            trimmed.startsWith("#") && headingLevel(trimmed) > 0 -> {
                flushParagraph()
                val level = headingLevel(trimmed)
                blocks += MdBlock.Heading(level, trimmed.drop(level).trim())
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks += MdBlock.Bullet(trimmed.drop(2).trim(), indent = leading / 2)
            }
            numberedRegex.matches(trimmed) -> {
                flushParagraph()
                val m = numberedRegex.find(trimmed)!!
                blocks += MdBlock.Numbered(m.groupValues[1], m.groupValues[2])
            }
            else -> {
                if (para.isNotEmpty()) para.append(' ')
                para.append(trimmed)
            }
        }
    }
    flushParagraph()
    return blocks
}

/** Jumlah # di awal jika diikuti spasi atau akhir baris (maks 6); 0 jika bukan heading. */
private fun headingLevel(line: String): Int {
    val level = line.takeWhile { it == '#' }.length
    if (level !in 1..6) return 0
    return if (line.length == level || line[level] == ' ') level else 0
}

/** Pecah teks inline menjadi potongan dengan gaya. Penanda yang tidak ditutup dibiarkan apa adanya. */
fun parseInline(s: String): List<MdRun> {
    val out = mutableListOf<MdRun>()
    val buf = StringBuilder()

    fun flush() {
        if (buf.isNotEmpty()) {
            out += MdRun(buf.toString(), MdStyle.PLAIN)
            buf.clear()
        }
    }

    var i = 0
    while (i < s.length) {
        when {
            s.startsWith("**", i) -> {
                val end = s.indexOf("**", i + 2)
                if (end > i + 2) {
                    flush()
                    out += MdRun(s.substring(i + 2, end), MdStyle.BOLD)
                    i = end + 2
                } else {
                    buf.append("**")
                    i += 2
                }
            }
            s[i] == '`' -> {
                val end = s.indexOf('`', i + 1)
                if (end > i) {
                    flush()
                    out += MdRun(s.substring(i + 1, end), MdStyle.CODE)
                    i = end + 1
                } else {
                    buf.append('`')
                    i++
                }
            }
            s[i] == '*' -> {
                val end = s.indexOf('*', i + 1)
                if (end > i + 1) {
                    flush()
                    out += MdRun(s.substring(i + 1, end), MdStyle.ITALIC)
                    i = end + 1
                } else {
                    buf.append('*')
                    i++
                }
            }
            else -> {
                buf.append(s[i])
                i++
            }
        }
    }
    flush()
    return out
}
