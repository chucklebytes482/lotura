package com.example.remote

/** Penyaring file di penjelajah repositori GitHub: hanya md, json, gambar, dan dokumen. Folder tetap tampil. */
internal object GithubFileFilter {
    private val markdownAndData = setOf("md", "json")
    private val images = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif", "tif", "tiff", "ico", "avif", "raw", "psd"
    )
    private val documents = setOf(
        "pdf", "doc", "docx", "odt", "rtf", "txt", "pages",
        "xls", "xlsx", "ods", "csv", "numbers",
        "ppt", "pptx", "odp", "key",
        "epub"
    )
    private val custom = setOf("vxenc", "hc")
    private val allowed = markdownAndData + images + documents + custom

    fun allows(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return false
        return name.substring(dot + 1).lowercase() in allowed
    }
}
