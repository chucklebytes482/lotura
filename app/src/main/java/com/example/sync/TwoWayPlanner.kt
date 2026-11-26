package com.example.sync

/**
 * Perencana sinkron dua arah (murni Kotlin, tanpa Android).
 *
 * Tiga sumber kebenaran per path:
 *  - local: hash file di perangkat sekarang (git blob sha untuk GitHub, md5 untuk Drive).
 *  - remote: hash file di sumber sekarang (blob sha untuk GitHub, md5 untuk Drive).
 *  - base: keadaan kedua sisi setelah sync berhasil terakhir.
 *
 * Perubahan dihitung dari base: sisi yang hash-nya berbeda dari base dianggap berubah.
 * Hash lokal dan remote tidak pernah dibandingkan langsung, kecuali saat base belum ada.
 */

/** Berkas lokal. [hash] sesuai jenis sumber (lihat catatan di atas). */
data class LocalFile(val hash: String)

/**
 * Berkas di sisi remote.
 *  - ref: id file Drive, atau path file di repo GitHub.
 *  - hash: md5 (Drive) atau blob sha (GitHub). null untuk Google Workspace native (Docs/Sheets/Slides),
 *    yang isinya tidak bisa dibandingkan dengan file lokal.
 *  - nativeMime: tipe Google Workspace bila berkas native, null untuk file biasa.
 */
data class RemoteFile(val ref: String, val hash: String?, val nativeMime: String?)

/**
 * Keadaan satu path setelah sync berhasil.
 * remoteRef null berarti path itu tidak ada di remote pada saat sync terakhir.
 */
data class PathState(val localHash: String?, val remoteHash: String?, val remoteRef: String?)

/** Aksi yang diputuskan planner untuk satu path. */
sealed interface PlannedAction {
    val path: String

    /** Unduh dari remote (atau ekspor untuk native) ke perangkat. */
    data class Download(override val path: String, val remote: RemoteFile) : PlannedAction

    /** Unggah dari perangkat ke remote. remote diisi bila file sudah ada di sana (untuk memperbarui). */
    data class Upload(override val path: String, val local: LocalFile, val remote: RemoteFile?) : PlannedAction

    /** Remote sudah dihapus dan lokal tidak berubah: pindahkan lokal ke trash. */
    data class TrashLocal(override val path: String) : PlannedAction

    /** Lokal sudah dihapus dan remote tidak berubah: hapus remote (ke trash remote). */
    data class TrashRemote(override val path: String, val remote: RemoteFile) : PlannedAction

    /**
     * Kedua sisi berubah dan isinya berbeda: remote menang. Lokal dipindah ke trash (`.trash/<waktu>/<path>`),
     * lalu remote diunduh ke path asli. Tidak ada salinan konflik.
     */
    data class Conflict(override val path: String, val local: LocalFile, val remote: RemoteFile) : PlannedAction

    /** Kedua sisi sudah sama: cukup catat state. */
    data class Record(override val path: String, val local: LocalFile, val remote: RemoteFile) : PlannedAction

    /** Tidak ada di kedua sisi lagi: hapus state-nya. */
    data class Forget(override val path: String) : PlannedAction

    /** Tidak ada perubahan. */
    data class Unchanged(override val path: String) : PlannedAction
}

/** Ringkasan rencana untuk dry-run. */
data class SyncPlan(val actions: List<PlannedAction>) {
    val uploads: Int get() = actions.count { it is PlannedAction.Upload }
    val downloads: Int get() = actions.count { it is PlannedAction.Download }
    val conflicts: Int get() = actions.count { it is PlannedAction.Conflict }
    val trashLocal: Int get() = actions.count { it is PlannedAction.TrashLocal }
    val trashRemote: Int get() = actions.count { it is PlannedAction.TrashRemote }
    /** Jumlah file yang akan dipindah ke trash, di kedua sisi. */
    val trashTotal: Int get() = trashLocal + trashRemote
}

object TwoWayPlanner {

    /**
     * Rencana untuk semua path. [local], [remote], dan [base] sudah dibatasi ke ruang lingkup sync.
     * Hasil diurutkan menurut path.
     */
    fun planAll(
        local: Map<String, LocalFile>,
        remote: Map<String, RemoteFile>,
        base: Map<String, PathState>
    ): List<PlannedAction> {
        val paths = (local.keys + remote.keys + base.keys).toSortedSet()
        return paths.map { plan(it, local[it], remote[it], base[it]) }
    }

    /**
     * Rencana mode "Tambah dari kedua sisi": hanya menambah dan menyalin.
     * Tidak pernah menghasilkan TrashLocal, TrashRemote, atau Forget.
     * Path yang sudah ada di base tidak ditransfer, kecuali kedua sisi berubah dan isinya beda (konflik).
     */
    fun planAddAll(
        local: Map<String, LocalFile>,
        remote: Map<String, RemoteFile>,
        base: Map<String, PathState>
    ): List<PlannedAction> {
        val paths = (local.keys + remote.keys + base.keys).toSortedSet()
        return paths.map { planAdd(it, local[it], remote[it], base[it]) }
    }

    /** Aturan mode tambah untuk satu path. Lihat [planAddAll]. */
    fun planAdd(path: String, l: LocalFile?, r: RemoteFile?, b: PathState?): PlannedAction {
        // Belum pernah sync: Upload, Download, Record, Conflict, atau Unchanged. Tidak ada trash.
        if (b == null) return plan(path, l, r, null)
        // Sudah pernah sync tapi satu sisi hilang: jangan transfer (edit/hapus diurus mode Sinkron).
        if (l == null || r == null) return PlannedAction.Unchanged(path)
        return when (val a = plan(path, l, r, b)) {
            is PlannedAction.Conflict, is PlannedAction.Record -> a
            else -> PlannedAction.Unchanged(path)
        }
    }

    /** Aturan untuk satu path. Lihat komentar di [PlannedAction] untuk arti tiap aksi. */
    fun plan(path: String, l: LocalFile?, r: RemoteFile?, b: PathState?): PlannedAction {
        val native = r?.nativeMime != null

        if (b == null) {
            // Belum pernah sync untuk path ini.
            return when {
                l == null && r == null -> PlannedAction.Unchanged(path)
                l != null && r == null -> PlannedAction.Upload(path, l, null)
                l == null && r != null -> PlannedAction.Download(path, r)
                l != null && r != null && (native || l.hash == r.hash) -> PlannedAction.Record(path, l, r)
                l != null && r != null -> PlannedAction.Conflict(path, l, r)
                else -> PlannedAction.Unchanged(path)
            }
        }

        val lChanged = l?.hash != b.localHash
        val remotePresentBefore = b.remoteRef != null
        val rChanged = when {
            (r != null) != remotePresentBefore -> true
            r == null -> false
            native -> false // Native: isinya tidak bisa dibandingkan, hanya keberadaannya.
            else -> r.hash != b.remoteHash
        }

        return when {
            l != null && r != null -> when {
                lChanged && rChanged ->
                    if (native || l.hash == r.hash) PlannedAction.Record(path, l, r)
                    else PlannedAction.Conflict(path, l, r)
                lChanged -> PlannedAction.Upload(path, l, r)
                rChanged -> PlannedAction.Download(path, r)
                else -> PlannedAction.Unchanged(path)
            }
            l != null && r == null -> when {
                // Remote dihapus sementara lokal diedit: edit lokal menang, file dibuat ulang di remote.
                remotePresentBefore && !lChanged -> PlannedAction.TrashLocal(path)
                else -> PlannedAction.Upload(path, l, null)
            }
            l == null && r != null -> when {
                // Lokal dihapus dan remote tidak berubah. Native tidak pernah dihapus dari remote
                // lewat sisi lokal, karena yang ada di perangkat hanya salinan ekspornya.
                b.localHash != null && !rChanged && !native -> PlannedAction.TrashRemote(path, r)
                else -> PlannedAction.Download(path, r)
            }
            else -> if (b.localHash != null || remotePresentBefore) PlannedAction.Forget(path)
            else PlannedAction.Unchanged(path)
        }
    }
}
