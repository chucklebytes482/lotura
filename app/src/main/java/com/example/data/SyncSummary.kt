package com.example.data

import com.example.sync.OpResult

/** Hasil sync terakhir untuk satu folder root Drive atau satu repo GitHub. */
enum class SyncStatus { OK, SEBAGIAN, GAGAL }

data class SyncSummary(
    val lastSyncAtMillis: Long,
    val status: SyncStatus,
    /** Ditransfer + sudah sama (dilewati). */
    val okCount: Int,
    val failCount: Int,
    val hiddenSkipped: Int
) {
    companion object {
        /** OK jika tidak ada gagal; GAGAL jika tidak ada yang berhasil; sisanya SEBAGIAN. */
        fun fromResult(result: OpResult, atMillis: Long): SyncSummary {
            val ok = result.transferred + result.skipped
            val status = when {
                result.failed == 0 -> SyncStatus.OK
                ok == 0 -> SyncStatus.GAGAL
                else -> SyncStatus.SEBAGIAN
            }
            return SyncSummary(atMillis, status, ok, result.failed, result.hidden)
        }

        private const val SEP = "|"

        fun encode(s: SyncSummary): String =
            listOf(s.lastSyncAtMillis, s.status.name, s.okCount, s.failCount, s.hiddenSkipped).joinToString(SEP)

        fun decode(raw: String): SyncSummary? {
            val parts = raw.split(SEP)
            if (parts.size != 5) return null
            return runCatching {
                SyncSummary(
                    lastSyncAtMillis = parts[0].toLong(),
                    status = SyncStatus.valueOf(parts[1]),
                    okCount = parts[2].toInt(),
                    failCount = parts[3].toInt(),
                    hiddenSkipped = parts[4].toInt()
                )
            }.getOrNull()
        }
    }
}
