package com.example.sync

/** Kunci sumber untuk satu akun Drive atau satu repo GitHub. */
object SourceKeys {
    fun drive(email: String) = "drive:$email"
    fun github(fullName: String) = "github:$fullName"
}

/**
 * Mode satu operasi sync.
 *  - ADD_BOTH: hanya menambah dan menyalin. Tidak pernah memindah ke trash.
 *  - SYNC: dua arah penuh, termasuk edit dan hapus (pindah ke trash).
 */
enum class SyncMode { ADD_BOTH, SYNC }

/**
 * Satu baris di pohon file.
 *  - path: relatif terhadap root sumber, memakai '/'. Untuk file Google Docs sudah memakai nama ekspor (mis. `Laporan.docx`).
 *  - size: ukuran byte. null untuk folder dan Google Docs/Sheets/Slides (native).
 *  - remoteId: id folder/file Drive, atau sha blob untuk GitHub.
 */
/** Path yang punya segmen tersembunyi (diawali '.'), di level mana pun. Dilewati saat sinkron. */
fun isHiddenPath(path: String): Boolean = path.split('/').any { it.startsWith(".") }

/** Ekstensi berkas repo GitHub yang ikut ditampilkan dan disinkronkan (huruf kecil, tanpa titik). */
val TRACKED_REPO_EXTENSIONS = setOf("md", "vxenc", "hc")

/**
 * Berkas repo GitHub yang ikut ditampilkan dan disinkronkan: berkas dengan ekstensi di [TRACKED_REPO_EXTENSIONS]
 * di mana pun di repo. Selain itu, dan semua path tersembunyi, diabaikan saat tampil, download/unggah, maupun auto-sync.
 */
fun isTrackedRepoPath(path: String): Boolean {
    if (isHiddenPath(path)) return false
    val name = path.substringAfterLast('/')
    val dot = name.lastIndexOf('.')
    return dot > 0 && name.substring(dot + 1).lowercase() in TRACKED_REPO_EXTENSIONS
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

/** Alasan satu item dilewati. */
enum class SkipReason {
    /** Isi sama dengan sisi lain (hash cocok), jadi tidak perlu dikirim ulang. */
    SAME_CONTENT,
    /** Log lama: ukuran sama dianggap sudah sinkron. Tidak dihasilkan lagi, hanya dibaca dari log lama. */
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
