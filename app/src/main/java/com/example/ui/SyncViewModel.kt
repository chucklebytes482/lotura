package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SyncBebasApp
import com.example.data.LocalPathHelper
import com.example.data.SyncLogEntry
import com.example.data.SyncLogStore
import com.example.data.SyncStatus
import com.example.data.SyncSummary
import com.example.data.db.DriveAccountEntity
import com.example.data.db.GithubAccountEntity
import com.example.data.db.GithubRepoEntity
import com.example.github.GithubTreeBlob
import com.example.sync.IgnoreRules
import com.example.sync.OpResult
import com.example.sync.SourceKeys
import com.example.sync.SourceTreeRules
import com.example.sync.SyncMode
import com.example.sync.SyncScan
import com.example.sync.TreeItem
import com.example.sync.isSyncPath
import com.example.sync.trackedDirPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Dry-run yang menunggu konfirmasi user sebelum tombol "Sinkron" dijalankan. */
data class PendingSync(
    val sourceKey: String,
    val label: String,
    val scan: SyncScan
)

/**
 * Satu akun = satu koneksi (Drive) atau satu repo (GitHub).
 * Pohon disimpan flat per sumber; induk-anak dihitung dari path.
 */
data class SyncUiState(
    /** sourceKey -> semua item yang sudah dimuat untuk sumber itu. */
    val trees: Map<String, List<TreeItem>> = emptyMap(),
    /** Kunci yang isi anaknya sudah dimuat: sourceKey (akar) atau TreeItem.key (folder). */
    val loaded: Set<String> = emptySet(),
    val loading: Set<String> = emptySet(),
    /** sourceKey yang sedang diunduh/diunggah/dipindai. */
    val busySources: Set<String> = emptySet(),
    /** sourceKey yang menunggu giliran sync (sync lain sedang berjalan). */
    val queuedSources: Set<String> = emptySet(),
    /**
     * sourceKey yang sudah lolos "Tambah dari kedua sisi" dan boleh menjalankan "Sinkron".
     * Disimpan di memori: hilang saat app ditutup, jadi setiap sesi harus mulai dari "Tambah".
     * Dihapus lagi setiap kali "Sinkron" dijalankan (berhasil, sebagian, atau gagal).
     */
    val armedSources: Set<String> = emptySet(),
    /** Dry-run sinkron yang menunggu konfirmasi. Null bila tidak ada. */
    val pendingPlan: PendingSync? = null,
    val isConnectingDrive: Boolean = false,
    val isAddingGithub: Boolean = false,
    val driveAuthError: String? = null,
    val githubAccountError: String? = null,
    val snackbarMessage: String? = null,
    val isOnline: Boolean = true
)

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SyncBebasApp
    private val repository = app.repository
    private val driveApi = app.driveApiClient
    private val githubApi = app.githubApiClient
    private val engine = app.syncEngine
    private val logStore = SyncLogStore(application)
    private val ignoreRules = IgnoreRules(IgnoreRules.DEFAULT_PATTERNS)

    /** Semua operasi sync berjalan satu per satu, supaya dua sumber tidak saling berebut jaringan dan file. */
    private val syncMutex = Mutex()
    /** Job per sumber (yang sedang antre atau berjalan), untuk tombol batal. */
    private val syncJobs = mutableMapOf<String, Job>()

    val driveAccounts: StateFlow<List<DriveAccountEntity>> = repository.driveAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val githubAccounts: StateFlow<List<GithubAccountEntity>> = repository.githubAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val repos: StateFlow<List<GithubRepoEntity>> = repository.githubRepos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(SyncUiState(isOnline = checkOnline(application)))
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private val _logs = MutableStateFlow(logStore.loadAll())
    /** Log sync standar untuk semua sumber, terbaru di depan. */
    val logs: StateFlow<List<SyncLogEntry>> = _logs.asStateFlow()

    init {
        // Setiap akun Drive yang belum dimuat (baru atau setelah restart) langsung dimuat 1 level.
        viewModelScope.launch {
            repository.driveAccounts.collect { accounts ->
                accounts.forEach { account ->
                    val key = SourceKeys.drive(account.email)
                    val state = _uiState.value
                    if (key !in state.loaded && key !in state.loading) {
                        fetchDriveLevel(account.email, parent = null, remaining = DRIVE_PRELOAD_LEVELS)
                    }
                }
            }
        }
    }

    // ---------- Umum ----------

    fun refreshNetworkStatus() {
        _uiState.update { it.copy(isOnline = checkOnline(app)) }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    // ---------- Google Drive ----------

    fun onDriveConnectingStarted() {
        _uiState.update { it.copy(isConnectingDrive = true, driveAuthError = null) }
    }

    /** Dipanggil saat user menutup pemilih akun tanpa memilih. Tombol kembali aktif. */
    fun onDriveConnectingCancelled() {
        _uiState.update { it.copy(isConnectingDrive = false) }
    }

    fun onDriveAuthError(message: String) {
        _uiState.update { it.copy(isConnectingDrive = false, driveAuthError = message) }
    }

    fun onDriveAccessTokenReceived(accessToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectingDrive = true, driveAuthError = null) }
            try {
                val info = driveApi.fetchUserInfo(accessToken)
                repository.addOrUpdateDriveAccount(info.email, info.displayName, accessToken)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(driveAuthError = e.localizedMessage ?: "Gagal membaca akun Google Drive.")
                }
            } finally {
                _uiState.update { it.copy(isConnectingDrive = false) }
            }
        }
    }

    fun removeDriveAccount(email: String) {
        viewModelScope.launch {
            repository.removeDriveAccount(email)
            dropSource(SourceKeys.drive(email))
            _uiState.update { it.copy(snackbarMessage = "Akun $email diputus. Folder lokal tidak dihapus.") }
        }
    }

    /** Memuat ulang pohon Drive untuk satu akun. */
    fun reloadDrive(email: String) {
        val key = SourceKeys.drive(email)
        _uiState.update { st ->
            st.copy(loaded = SourceTreeRules.loadedWithoutSource(st.loaded, key))
        }
        viewModelScope.launch { fetchDriveLevel(email, parent = null, remaining = DRIVE_PRELOAD_LEVELS) }
    }

    // ---------- GitHub ----------

    fun addGithubAccount(pat: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val token = pat.trim()
            if (token.isEmpty()) {
                _uiState.update { it.copy(githubAccountError = "Fine-grained PAT wajib diisi.") }
                return@launch
            }
            _uiState.update { it.copy(isAddingGithub = true, githubAccountError = null) }
            try {
                val user = githubApi.getAuthenticatedUser(token)
                val accountId = repository.addGithubAccount(
                    label = user.name.trim().ifBlank { user.login },
                    username = user.login,
                    avatarUrl = user.avatarUrl,
                    pat = token
                )
                val summaries = githubApi.listRepositories(token)
                repository.replaceGithubRepos(accountId, summaries.map { it.toEntity(accountId, user.login) })
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(githubAccountError = e.localizedMessage ?: "Gagal memverifikasi PAT GitHub.")
                }
            } finally {
                _uiState.update { it.copy(isAddingGithub = false) }
            }
        }
    }

    fun refreshGithubRepos(account: GithubAccountEntity) {
        viewModelScope.launch {
            try {
                val pat = repository.getGithubPat(account.id) ?: error("PAT tidak ada, tambahkan ulang akun.")
                val summaries = githubApi.listRepositories(pat)
                repository.replaceGithubRepos(account.id, summaries.map { it.toEntity(account.id, account.username) })
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Gagal memuat repo: ${e.message ?: "tidak diketahui"}") }
            }
        }
    }

    fun deleteGithubAccount(account: GithubAccountEntity) {
        viewModelScope.launch {
            val accountRepos = repos.value.filter { it.accountId == account.id }
            repository.replaceGithubRepos(account.id, emptyList())
            repository.deleteGithubAccount(account.id)
            accountRepos.forEach { dropSource(SourceKeys.github(it.fullName)) }
        }
    }

    // ---------- Sync dua arah: dua tombol per sumber ----------

    /**
     * Tombol 1 "Tambah dari kedua sisi": untuk seluruh akun Drive atau seluruh repo.
     * Hanya menambah dan menyalin, tidak pernah memindah ke trash. Sukses tanpa error membuka kunci tombol 2.
     */
    fun addBothSides(sourceKey: String, label: String) {
        if (pendingPlanFor(sourceKey)) return
        enqueue(sourceKey, label) {
            val ok = runAndLog(sourceKey, label, SyncMode.ADD_BOTH)
            if (ok) _uiState.update { it.copy(armedSources = it.armedSources + sourceKey) }
        }
    }

    /**
     * Tombol 2 "Sinkron": hanya jalan kalau sumber sudah dibuka kuncinya lewat [addBothSides].
     * Membuat dry-run dulu. Eksekusi baru terjadi di [confirmSync].
     */
    fun previewSync(sourceKey: String, label: String) {
        if (sourceKey !in _uiState.value.armedSources) {
            showMessage("Klik \"Tambah dari kedua sisi\" dulu sebelum sinkron.")
            return
        }
        if (pendingPlanFor(sourceKey)) return
        enqueue(sourceKey, label) {
            val scan = scanOrLog(sourceKey, label, SyncMode.SYNC) ?: return@enqueue
            _uiState.update { it.copy(pendingPlan = PendingSync(sourceKey, label, scan)) }
        }
    }

    /**
     * Menjalankan rencana dry-run yang sedang ditampilkan. Kunci dibuka lagi setelah ini, apa pun hasilnya,
     * supaya setiap sinkron selalu didahului "Tambah dari kedua sisi".
     */
    fun confirmSync() {
        val pending = _uiState.value.pendingPlan ?: return
        _uiState.update { it.copy(pendingPlan = null) }
        enqueue(pending.sourceKey, pending.label) {
            try {
                executeAndLog(pending.sourceKey, pending.label, SyncMode.SYNC, pending.scan)
            } finally {
                _uiState.update { it.copy(armedSources = it.armedSources - pending.sourceKey) }
            }
        }
    }

    /** Membatalkan dry-run. Kunci tetap terbuka, user bisa langsung klik "Sinkron" lagi. */
    fun cancelPendingSync() {
        _uiState.update { it.copy(pendingPlan = null) }
    }

    /** Menghentikan sync sumber [sourceKey], baik yang sedang berjalan maupun yang masih antre. */
    fun cancelSync(sourceKey: String) {
        syncJobs[sourceKey]?.cancel()
    }

    private fun pendingPlanFor(sourceKey: String): Boolean {
        if (_uiState.value.pendingPlan?.sourceKey != sourceKey) return false
        showMessage("Selesaikan dulu dry-run sinkron yang sedang ditampilkan.")
        return true
    }

    /**
     * Memasukkan operasi ke antrean. Satu sumber hanya boleh sekali dalam antrean atau berjalan.
     * Operasi dijalankan lewat [syncMutex], jadi satu per satu.
     */
    private fun enqueue(sourceKey: String, label: String, block: suspend () -> Unit) {
        val state = _uiState.value
        if (sourceKey in state.busySources || sourceKey in state.queuedSources) {
            _uiState.update { it.copy(snackbarMessage = "$label sudah dalam antrean atau sedang berjalan") }
            return
        }
        _uiState.update { it.copy(queuedSources = it.queuedSources + sourceKey) }
        // LAZY: job disimpan dulu sebelum mulai, supaya blok finally tidak jalan sebelum job tercatat.
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                syncMutex.withLock {
                    _uiState.update {
                        it.copy(queuedSources = it.queuedSources - sourceKey, busySources = it.busySources + sourceKey)
                    }
                    block()
                }
            } finally {
                _uiState.update {
                    it.copy(queuedSources = it.queuedSources - sourceKey, busySources = it.busySources - sourceKey)
                }
                syncJobs.remove(sourceKey)
            }
        }
        syncJobs[sourceKey] = job
        job.start()
    }

    /** Scan lalu eksekusi langsung (mode tambah). Mengembalikan true kalau sukses tanpa error dan tidak dibatalkan. */
    private suspend fun runAndLog(sourceKey: String, label: String, mode: SyncMode): Boolean {
        val scan = scanOrLog(sourceKey, label, mode) ?: return false
        return executeAndLog(sourceKey, label, mode, scan)
    }

    /** Scan saja. Gagal atau dibatalkan dicatat di log, lalu mengembalikan null. */
    private suspend fun scanOrLog(sourceKey: String, label: String, mode: SyncMode): SyncScan? {
        return try {
            scanFor(sourceKey, mode)
        } catch (e: CancellationException) {
            appendLog(sourceKey, label, mode, SyncStatus.DIBATALKAN, OpResult(), "Dibatalkan oleh pengguna")
            throw e
        } catch (e: Exception) {
            val message = e.message ?: "Scan gagal"
            _uiState.update { it.copy(snackbarMessage = message) }
            appendLog(sourceKey, label, mode, SyncStatus.GAGAL, OpResult(failed = 1), message)
            null
        }
    }

    /** Menjalankan rencana [scan] dan mencatat hasilnya. Mengembalikan true kalau sukses tanpa error. */
    private suspend fun executeAndLog(sourceKey: String, label: String, mode: SyncMode, scan: SyncScan): Boolean {
        var cancelled = false
        var errorMessage: String? = null
        val result = try {
            engine.execute(scan, app.syncStateStore)
        } catch (e: CancellationException) {
            cancelled = true
            OpResult()
        } catch (e: Exception) {
            errorMessage = e.message ?: "Operasi gagal"
            OpResult(failed = 1)
        }
        if (!currentCoroutineContext().isActive) cancelled = true
        val status = if (cancelled) SyncStatus.DIBATALKAN else SyncSummary.fromResult(result, System.currentTimeMillis()).status
        appendLog(
            sourceKey = sourceKey,
            label = label,
            mode = mode,
            status = status,
            result = result,
            error = if (cancelled) "Dibatalkan oleh pengguna" else errorMessage
        )
        if (!cancelled && result.transferred > 0) {
            if (SourceTreeRules.isDriveSource(sourceKey)) {
                fetchDriveLevel(SourceTreeRules.driveEmailOf(sourceKey), parent = null, remaining = DRIVE_PRELOAD_LEVELS)
            } else {
                repos.value.firstOrNull { it.fullName == SourceTreeRules.githubFullNameOf(sourceKey) }?.let { loadRepoTree(it) }
            }
        }
        return !cancelled && errorMessage == null && result.failed == 0
    }

    private suspend fun scanFor(sourceKey: String, mode: SyncMode): SyncScan {
        if (SourceTreeRules.isDriveSource(sourceKey)) {
            val email = SourceTreeRules.driveEmailOf(sourceKey)
            val root = LocalPathHelper.driveRoot(email)
            return engine.scanDrive(email, root, ignoreRules, app.syncStateStore, sourceKey, mode)
        }
        val fullName = SourceTreeRules.githubFullNameOf(sourceKey)
        val repo = repos.value.firstOrNull { it.fullName == fullName }
            ?: error("Repo $fullName tidak ditemukan")
        val pat = repository.getGithubPat(repo.accountId)
            ?: error("PAT untuk @${repo.owner} tidak ada. Tambahkan ulang akun.")
        return engine.scanGithub(
            pat = pat,
            owner = repo.owner,
            repo = repo.name,
            branch = repo.defaultBranch,
            rootDir = File(repo.localPath),
            ignoreRules = ignoreRules,
            stateStore = app.syncStateStore,
            sourceKey = sourceKey,
            mode = mode
        )
    }

    /** Gagal memuat pohon: beri tahu lewat Snackbar dan catat di log sebagai GAGAL. */
    private fun logLoadFailure(sourceKey: String, label: String, message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
        appendLog(
            sourceKey = sourceKey,
            label = label,
            mode = SyncMode.SYNC,
            status = SyncStatus.GAGAL,
            result = OpResult(),
            error = message
        )
    }

    /** Menulis satu entri log. Dipakai untuk sync dan untuk kegagalan memuat pohon. */
    private fun appendLog(
        sourceKey: String,
        label: String,
        mode: SyncMode,
        status: SyncStatus,
        result: OpResult,
        error: String?
    ) {
        val entry = SyncLogEntry(
            atMillis = System.currentTimeMillis(),
            sourceKey = sourceKey,
            source = SourceTreeRules.sourceLabel(sourceKey),
            label = label,
            status = status,
            transferred = result.transferred,
            skipped = result.skipped,
            failed = result.failed,
            error = error,
            hidden = result.hidden,
            skipDetails = result.skipDetails.take(SyncLogStore.MAX_SKIP_DETAILS),
            skipCounts = result.skipDetails.groupingBy { it.reason }.eachCount(),
            mode = mode.name
        )
        _logs.value = logStore.append(entry)
    }

    // ---------- Internal ----------

    private suspend fun fetchDriveLevel(email: String, parent: TreeItem?, remaining: Int) {
        val sourceKey = SourceKeys.drive(email)
        val parentKey = parent?.key ?: sourceKey
        val parentPath = parent?.path ?: ""
        _uiState.update { it.copy(loading = it.loading + parentKey) }
        try {
            val children = engine.driveChildren(email, parent?.remoteId ?: "root")
            val items = children
                .filterNot { it.name.startsWith(".") }
                .map { child ->
                    TreeItem(
                        sourceKey = sourceKey,
                        path = SourceTreeRules.childPath(parentPath, child.name),
                        name = child.name,
                        isFolder = child.isFolder,
                        size = child.size,
                        remoteId = child.id,
                        nativeMime = child.nativeMime
                    )
                }
            _uiState.update { st ->
                // Ganti isi folder ini; keturunan lama ikut dibuang.
                val prefix = SourceTreeRules.childPrefix(sourceKey, parentPath)
                st.copy(
                    trees = st.trees + (sourceKey to SourceTreeRules.replaceDriveLevel(st.trees[sourceKey].orEmpty(), sourceKey, parentPath, items)),
                    loaded = SourceTreeRules.loadedWithoutPrefix(st.loaded, prefix) + parentKey
                )
            }
            if (remaining > 1) {
                for (folder in items.filter { it.isFolder }) {
                    fetchDriveLevel(email, folder, remaining - 1)
                }
            }
        } catch (e: Exception) {
            logLoadFailure(sourceKey, parent?.name ?: email, "Gagal memuat Drive $email: ${e.message ?: "tidak diketahui"}")
        } finally {
            _uiState.update { it.copy(loading = it.loading - parentKey) }
        }
    }

    private suspend fun loadRepoTree(repo: GithubRepoEntity) {
        val sourceKey = SourceKeys.github(repo.fullName)
        _uiState.update { it.copy(loading = it.loading + sourceKey) }
        try {
            val pat = repository.getGithubPat(repo.accountId) ?: error("PAT tidak ada, tambahkan ulang akun.")
            val (_, treeSha) = githubApi.getBranchHeadCommit(pat, repo.owner, repo.name, repo.defaultBranch)
            val blobs: List<GithubTreeBlob> = githubApi.listBlobsInSubfolder(pat, repo.owner, repo.name, treeSha, "")
                .filter { it.isRegularFile && isSyncPath(it.repoPath) }
            val items = trackedDirPaths(blobs.map { it.repoPath }).map { path ->
                TreeItem(sourceKey, path, path.substringAfterLast('/'), true, null, null, null, repo.archived)
            } + blobs.map { blob ->
                TreeItem(sourceKey, blob.repoPath, blob.repoPath.substringAfterLast('/'), false, blob.size, blob.sha, null, repo.archived)
            }
            _uiState.update { st ->
                st.copy(trees = st.trees + (sourceKey to items), loaded = st.loaded + sourceKey)
            }
        } catch (e: Exception) {
            logLoadFailure(sourceKey, repo.name, "Gagal memuat repo ${repo.fullName}: ${e.message ?: "tidak diketahui"}")
        } finally {
            _uiState.update { it.copy(loading = it.loading - sourceKey) }
        }
    }

    /** Membuang state pohon, kunci, dan base sync untuk satu sumber. Log sync tetap disimpan sebagai riwayat. */
    private suspend fun dropSource(sourceKey: String) {
        app.syncStateStore.deleteSource(sourceKey)
        _uiState.update { st ->
            st.copy(
                trees = st.trees - sourceKey,
                loaded = SourceTreeRules.loadedWithoutSource(st.loaded, sourceKey),
                armedSources = st.armedSources - sourceKey,
                pendingPlan = st.pendingPlan?.takeIf { it.sourceKey != sourceKey }
            )
        }
    }

    private fun com.example.github.GithubRepoSummary.toEntity(accountId: Long, username: String) =
        GithubRepoEntity(
            fullName = fullName,
            accountId = accountId,
            owner = owner,
            name = name,
            defaultBranch = defaultBranch,
            isPrivate = isPrivate,
            archived = archived,
            localPath = LocalPathHelper.githubRoot(username, name).absolutePath
        )

    private fun checkOnline(context: Context): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    companion object {
        private const val DRIVE_PRELOAD_LEVELS = 1
    }
}
