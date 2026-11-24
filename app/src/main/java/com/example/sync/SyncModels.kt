package com.example.sync

/** Kunci sumber untuk satu akun Drive atau satu repo GitHub. */
object SourceKeys {
    fun drive(email: String) = "drive:$email"
    fun github(fullName: String) = "github:$fullName"
}

/**
 * Satu baris di pohon file.
 *  - path: relatif terhadap root sumber, memakai '/'. Untuk file Google Docs sudah memakai nama ekspor (mis. `Laporan.docx`).
 *  - size: ukuran byte. null untuk folder dan Google Docs/Sheets/Slides (native).
 *  - remoteId: id folder/file Drive, atau sha blob untuk GitHub.
 */
/** Path yang punya segmen tersembunyi (diawali '.'), di level mana pun. Dilewati saat sinkron. */
fun isHiddenPath(path: String): Boolean = path.split('/').any { it.startsWith(".") }

/** Folder di root repo yang seluruh isinya ikut disinkronkan. */
val TRACKED_ROOT_FOLDERS: Set<String> = setOf("panduan", "doc", "docs")

/**
 * Berkas repo GitHub yang ikut ditampilkan dan disinkronkan:
 *  - semua berkas .md di mana pun (termasuk README.md),
 *  - berkas bernama README* di mana pun,
 *  - seluruh isi folder root `panduan`, `doc`, `docs`.
 * Selain itu diabaikan, baik saat tampil, download/unggah, maupun auto-sync.
 */
fun isTrackedRepoPath(path: String): Boolean {
    if (isHiddenPath(path)) return false
    val parts = path.split('/')
    val name = parts.last().lowercase()
    if (parts.size > 1 && parts[0].lowercase() in TRACKED_ROOT_FOLDERS) return true
    return name.endsWith(".md") || name.startsWith("readme")
}

/** Semua folder (relatif, memakai '/') yang berisi setidaknya satu path yang dilacak. */
fun trackedDirPaths(trackedFilePaths: Collection<String>): Set<String> {
    val dirs = linkedSetOf<String>()
    trackedFilePaths.forEach { path ->
        val parts = path.split('/')
        for (i in 1 until parts.size) dirs += parts.take(i).joinToString("/")
    }
    return dirs
}

data class TreeItem(
    val sourceKey: String,
    val path: String,
    val name: String,
    val isFolder: Boolean,
    val size: Long?,
    val remoteId: String?,
    val nativeMime: String?,
    val archived: Boolean = false
) {
    val key: String get() = "$sourceKey|$path"
    val depth: Int get() = path.count { it == '/' }

    fun covers(otherPath: String): Boolean =
        otherPath == path || (isFolder && otherPath.startsWith("$path/"))
}

/** Aturan perbandingan: sama jika ukuran sumber dan target sama. Ukuran null selalu ditransfer. */
object SyncDecisions {
    /**
     * @param sourceSize ukuran file yang akan dikirim (null jika tidak diketahui / native).
     * @param targetSize ukuran file di sisi tujuan (null jika belum ada atau native).
     */
    fun shouldTransfer(sourceSize: Long?, targetSize: Long?): Boolean =
        sourceSize == null || targetSize == null || sourceSize != targetSize
}

/** Alasan satu item dilewati. */
enum class SkipReason {
    /** Ukuran di sumber dan tujuan sama, jadi dianggap sudah sinkron. */
    SAME_SIZE,
    /** Bukan file biasa di perangkat (mis. folder kosong atau tidak ada), tidak bisa diunggah. */
    NOT_A_LOCAL_FILE,
    /** Lebih dari batas 45 MB untuk GitHub. */
    TOO_LARGE_FOR_GITHUB,
    /** Path tersembunyi (diawali '.'), dilewati sebelum sync. */
    HIDDEN
}

data class SkipDetail(val path: String, val reason: SkipReason)

/**
 * Hasil satu operasi sync.
 * Invariant: [skipDetails].size == skipped + hidden. Angka [hidden] dihitung terpisah dari [skipped]
 * supaya ringkasan status tetap sama seperti sebelumnya.
 */
data class OpResult(
    val transferred: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    /** Item tersembunyi (mis. file sistem) yang dilewati sebelum sync. */
    val hidden: Int = 0,
    /** Alasan per item yang dilewati, termasuk yang [hidden]. */
    val skipDetails: List<SkipDetail> = emptyList()
) {
    operator fun plus(other: OpResult) = OpResult(
        transferred + other.transferred,
        skipped + other.skipped,
        failed + other.failed,
        hidden + other.hidden,
        skipDetails + other.skipDetails
    )

    companion object {
        /** Satu item dilewati dengan alasan [reason]. HIDDEN masuk ke hitungan [hidden]. */
        fun skip(reason: SkipReason, path: String): OpResult =
            if (reason == SkipReason.HIDDEN) OpResult(hidden = 1, skipDetails = listOf(SkipDetail(path, reason)))
            else OpResult(skipped = 1, skipDetails = listOf(SkipDetail(path, reason)))
    }
}
