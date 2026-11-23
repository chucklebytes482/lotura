package com.example.data

data class SyncCardMetrics(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val deleted: Int = 0,
    val conflicts: Int = 0,
    val hasError: Boolean = false
) {
    companion object {
        private val uploadRegex = Regex("""↑(\d+)""")
        private val downloadRegex = Regex("""↓(\d+)""")
        private val exportRegex = Regex("""Ekspor:(\d+)""", RegexOption.IGNORE_CASE)
        private val deleteRegex = Regex("""Hapus:(\d+)""", RegexOption.IGNORE_CASE)
        private val conflictRegex = Regex("""Konflik:(\d+)""", RegexOption.IGNORE_CASE)

        fun parse(lastStatus: String, lastError: String?): SyncCardMetrics {
            val uploaded = uploadRegex.find(lastStatus)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val rawDownloaded = downloadRegex.find(lastStatus)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val exported = exportRegex.find(lastStatus)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val deleted = deleteRegex.find(lastStatus)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val conflicts = conflictRegex.find(lastStatus)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

            val normalizedStatus = lastStatus.trim().lowercase()
            val statusSignalsError = normalizedStatus.startsWith("galat") ||
                normalizedStatus.startsWith("gagal") ||
                normalizedStatus.contains("otorisasi") ||
                normalizedStatus.contains("pat hilang")

            return SyncCardMetrics(
                uploaded = uploaded,
                downloaded = rawDownloaded + exported,
                deleted = deleted,
                conflicts = conflicts,
                hasError = !lastError.isNullOrBlank() || statusSignalsError
            )
        }
    }
}
