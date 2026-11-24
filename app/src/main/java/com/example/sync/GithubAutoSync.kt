package com.example.sync

import android.content.Context
import com.example.data.LocalFileOps
import com.example.data.SyncRepository
import com.example.data.db.GithubRepoEntity
import com.example.github.GithubApiClient
import com.example.github.GithubRemote
import com.example.github.GithubTreeChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/**
 * Sinkron otomatis dua arah dengan GitHub.
 *
 * Cara kerja:
 *  - Setiap repo punya "snapshot" sidik jari file (ukuran + waktu ubah) dari siklus terakhir.
 *  - Siklus dipicu oleh [notifyChanged] (debounce) dan polling berkala. Setiap siklus mengambil
 *    daftar file remote, lalu memproses kandidat: file yang berubah di perangkat, dan file yang
 *    remote-nya berbeda dengan base.
 *  - "Base" = sha blob yang diketahui sama di perangkat dan GitHub pada sinkron terakhir.
 *    Base disimpan per repo di SharedPreferences.
 *  - Hanya perubahan sepihak yang diterapkan: perangkat saja didorong, GitHub saja ditarik.
 *    Jika keduanya berubah, file dilaporkan sebagai konflik dan tidak ditimpa.
 *  - File yang sedang dibuka di editor tidak ditarik dulu; ditarik setelah editor ditutup.
 *  - Hanya berjalan selama proses aplikasi hidup.
 */
class GithubAutoSync(
    context: Context,
    private val repository: SyncRepository,
    private val github: GithubRemote,
    private val scope: CoroutineScope
) {
    enum class Action { NONE, PUSH, DELETE, PULL, PULL_DELETE, CONFLICT }

    /** Dijawab oleh ViewModel: apakah berkas ini sedang dibuka di editor. Berkas seperti itu tidak ditarik. */
    @Volatile
    var isEditing: (File) -> Boolean = { false }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ignoreRules = IgnoreRules(IgnoreRules.DEFAULT_PATTERNS)

    private val locks = HashMap<String, Mutex>()
    /** repoKey -> (path -> sidik jari) pada siklus terakhir yang sudah diproses. */
    private val snapshots = HashMap<String, Map<String, String>>()
    /** repoKey -> konflik yang sudah dilaporkan, supaya tidak berulang setiap siklus. */
    private val reportedConflicts = HashMap<String, Set<String>>()
    private val lastError = HashMap<String, String>()

    private var loopJob: Job? = null
    private var debounceJob: Job? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 32)
    /** Pesan untuk ditampilkan ke user (konflik, gagal push, dsb). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                runAllRepos()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Dipanggil setelah file berubah lewat aplikasi. Siklus berjalan setelah jeda singkat. */
    fun notifyChanged() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            runAllRepos()
        }
    }

    /** Menjalankan [block] dengan kunci per repo, sehingga auto-sync dan sync manual tidak bertabrakan. */
    suspend fun <T> withRepoLock(repoKey: String, block: suspend () -> T): T =
        lockFor(repoKey).withLock { block() }

    /** Mencatat sha yang sudah sama di perangkat dan GitHub setelah sync manual. */
    fun recordBases(repoKey: String, shas: Map<String, String>) {
        if (shas.isEmpty()) return
        val merged = loadBases(repoKey).toMutableMap()
        merged.putAll(shas)
        saveBases(repoKey, merged)
    }

    private suspend fun runAllRepos() {
        val repos = repository.githubRepos.first().filterNot { it.archived }
        for (repo in repos) {
            try {
                syncRepo(repo)
            } catch (e: Exception) {
                val msg = e.message ?: "tidak diketahui"
                if (lastError[repo.fullName] != msg) {
                    lastError[repo.fullName] = msg
                    _messages.tryEmit("Auto-sync ${repo.name} gagal: $msg")
                }
            }
        }
    }

    private suspend fun syncRepo(repo: GithubRepoEntity) = withRepoLock(repo.fullName) {
        val key = repo.fullName
        val root = File(repo.localPath)
        val local = LocalScanner.scanFiles(root, ignoreRules).filterKeys { isTrackedRepoPath(it) }
        val current = local.mapValues { (_, file) -> fingerprint(file) }

        // Siklus pertama sejak proses hidup: perubahan lokal hanya dicatat, tidak didorong.
        val previous = snapshots[key]
        val changed = LinkedHashSet<String>()
        if (previous != null) {
            current.forEach { (path, fp) -> if (previous[path] != fp) changed += path }
            previous.keys.forEach { path -> if (path !in current) changed += path }
        }

        val pat = repository.getGithubPat(repo.accountId)
            ?: throw IllegalStateException("PAT tidak ada, tambahkan ulang akun.")
        val (headCommit, treeSha) = github.getBranchHeadCommit(pat, repo.owner, repo.name, repo.defaultBranch)
        val remote = github.listBlobsInSubfolder(pat, repo.owner, repo.name, treeSha, "")
            .filter { isTrackedRepoPath(it.repoPath) }
            .associate { it.repoPath to it.sha }

        val bases = loadBases(key).toMutableMap()
        // Kandidat: berubah di perangkat, atau remote-nya beda dengan base (berubah di GitHub).
        val candidates = LinkedHashSet<String>(changed.filter { isTrackedRepoPath(it) })
        (remote.keys + bases.keys).forEach { path ->
            if (isTrackedRepoPath(path) && remote[path] != bases[path]) candidates += path
        }
        if (candidates.isEmpty()) {
            snapshots[key] = current
            lastError.remove(key)
            return@withRepoLock
        }

        val snap = (previous ?: current).toMutableMap()
        val changes = mutableListOf<GithubTreeChange>()
        // Path yang hasil keputusannya pasti dan sudah bisa dianggap selesai.
        val settled = mutableListOf<String>()
        val pushes = mutableListOf<Pair<String, String>>() // path -> sha blob baru
        val pulled = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val adoptedShas = mutableMapOf<String, String>()

        for (path in candidates) {
            val file = local[path]
            if (file != null && file.length() > GithubApiClient.MAX_GITHUB_FILE_BYTES) {
                skipped += path
                settled += path
                continue
            }
            val localSha = file?.let { LocalFileOps.gitBlobSha(it) }
            val action = decide(localSha, remote[path], bases[path])
            val target = File(root, path)
            if ((action == Action.PULL || action == Action.PULL_DELETE) && isEditing(target)) {
                continue // sedang diedit; ditarik setelah editor ditutup
            }
            when (action) {
                Action.NONE -> {
                    if (localSha != null && localSha == remote[path]) adoptedShas[path] = localSha
                    if (localSha == null && remote[path] == null) bases.remove(path)
                    settled += path
                }
                Action.PUSH -> {
                    val blobSha = if (localSha != null) {
                        github.createBlobFromFile(pat, repo.owner, repo.name, file!!)
                    } else {
                        null
                    }
                    changes += GithubTreeChange(repoPath = path, blobSha = blobSha)
                    pushes += path to (blobSha ?: "")
                }
                Action.DELETE -> {
                    // Tidak ada penghapusan otomatis di GitHub (standar sinkronisasi bagian 3).
                    // Base dibiarkan supaya file tidak ditarik balik.
                    settled += path
                }
                Action.PULL -> {
                    val remoteSha = remote.getValue(path)
                    github.downloadBlobToFile(pat, repo.owner, repo.name, remoteSha, target)
                    adoptedShas[path] = remoteSha
                    pulled += path
                    settled += path
                }
                Action.PULL_DELETE -> {
                    // Tidak ada penghapusan otomatis di perangkat. Base dibiarkan.
                    settled += path
                }
                Action.CONFLICT -> {
                    conflicts += path
                    settled += path
                }
            }
        }

        if (changes.isNotEmpty()) {
            val names = pushes.take(3).joinToString { it.first.substringAfterLast('/') }
            val more = if (pushes.size > 3) " dan ${pushes.size - 3} lainnya" else ""
            github.createAtomicCommit(
                pat = pat,
                owner = repo.owner,
                repo = repo.name,
                branch = repo.defaultBranch,
                parentCommitSha = headCommit,
                baseTreeSha = treeSha,
                changes = changes,
                message = "Auto-sync: $names$more dari Lotura"
            )
            for ((path, sha) in pushes) {
                if (sha.isEmpty()) {
                    bases.remove(path)
                } else {
                    bases[path] = sha
                }
                settled += path
            }
        }

        // Berhasil: catat base dan majukan snapshot untuk path yang selesai.
        adoptedShas.forEach { (path, sha) -> bases[path] = sha }
        val pulledSet = pulled.toSet()
        for (path in settled.distinct()) {
            if (path in pulledSet) {
                val f = File(root, path)
                if (f.isFile) snap[path] = fingerprint(f) else snap.remove(path)
            } else {
                val fp = current[path]
                if (fp != null) snap[path] = fp else snap.remove(path)
            }
        }
        snapshots[key] = snap
        saveBases(key, bases)
        lastError.remove(key)

        if (pushes.isNotEmpty()) {
            _messages.tryEmit("Tersimpan ke GitHub ${repo.name}: ${pushes.size} perubahan")
        }
        if (pulled.isNotEmpty()) {
            _messages.tryEmit("Diambil dari GitHub ${repo.name}: ${pulled.size} perubahan")
        }
        if (skipped.isNotEmpty()) {
            _messages.tryEmit("Tidak di-push (lebih dari 45 MB): ${skipped.take(3).joinToString()}")
        }
        val previousConflicts = reportedConflicts[key].orEmpty()
        val newConflicts = conflicts.filterNot { it in previousConflicts }
        reportedConflicts[key] = conflicts.toSet()
        if (newConflicts.isNotEmpty()) {
            _messages.tryEmit(
                "Konflik di ${repo.name}: ${newConflicts.take(3).joinToString()}. " +
                    "Berubah di GitHub dan di perangkat, tidak ditimpa."
            )
        }
        Unit
    }

    private fun lockFor(key: String): Mutex = synchronized(locks) { locks.getOrPut(key) { Mutex() } }

    private fun fingerprint(file: File): String = "${file.length()}:${file.lastModified()}"

    private fun loadBases(repoKey: String): Map<String, String> {
        val raw = prefs.getString(repoKey, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        }.getOrDefault(emptyMap())
    }

    private fun saveBases(repoKey: String, bases: Map<String, String>) {
        val obj = JSONObject()
        bases.forEach { (path, sha) -> obj.put(path, sha) }
        prefs.edit().putString(repoKey, obj.toString()).apply()
    }

    companion object {
        const val PREFS_NAME = "autosync_github"
        const val DEBOUNCE_MS = 3_000L
        const val POLL_INTERVAL_MS = 60_000L

        /**
         * Aturan keputusan untuk satu path. Hanya perubahan sepihak yang diterapkan.
         * @param local sha blob lokal, null jika berkas sudah tidak ada
         * @param remote sha blob di GitHub, null jika tidak ada
         * @param base sha yang diketahui sama pada sinkron terakhir, null jika belum pernah
         */
        fun decide(local: String?, remote: String?, base: String?): Action {
            if (local == remote) return Action.NONE
            if (local == null) {
                // Remote ada, lokal tidak ada.
                return when (base) {
                    remote -> Action.DELETE // dihapus di perangkat, GitHub tidak berubah
                    null -> Action.PULL // file baru di GitHub
                    else -> Action.CONFLICT // dihapus di perangkat, diubah di GitHub
                }
            }
            if (remote == null) {
                // Lokal ada, remote tidak ada.
                return when (base) {
                    null -> Action.PUSH // file baru di perangkat
                    local -> Action.PULL_DELETE // dihapus di GitHub, perangkat tidak berubah
                    else -> Action.CONFLICT // diubah di perangkat, dihapus di GitHub
                }
            }
            return when {
                base == remote -> Action.PUSH // hanya perangkat yang berubah
                base == local -> Action.PULL // hanya GitHub yang berubah
                else -> Action.CONFLICT
            }
        }
    }
}
