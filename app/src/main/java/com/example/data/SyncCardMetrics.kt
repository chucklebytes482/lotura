package com.example.data

data class SyncCardMetrics(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val hasError: Boolean = false
) {
    companion object {
        private val uploadRegex = Regex("""↑(\d+)""")
        private val downloadRegex = Regex("""↓(\d+)""")
        private val exportRegex = Regex("""Ekspor:(\d+)""", RegexOption.IGNORE_CASE)

        fun parse(lastStatus: String, lastError: String?): SyncCardMetrics {
            val uploaded = uploadRegex.find(lastStatus)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val rawDownloaded = downloadRegex.find(lastStatus)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val exported = exportRegex.find(lastStatus)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

            val normalizedStatus = lastStatus.trim().lowercase()
            val statusSignalsError = normalizedStatus.startsWith("galat") ||
                normalizedStatus.startsWith("gagal") ||
                normalizedStatus.contains("otorisasi") ||
                normalizedStatus.contains("pat hilang")

            return SyncCardMetrics(
                uploaded = uploaded,
                downloaded = rawDownloaded + exported,
                hasError = !lastError.isNullOrBlank() || statusSignalsError
            )
        }
    }
}
