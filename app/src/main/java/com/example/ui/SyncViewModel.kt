package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SyncBebasApp
import com.example.data.LocalPathHelper
import com.example.data.db.DriveAccountEntity
import com.example.data.db.GithubAccountEntity
import com.example.data.db.GithubRepoEntity
import com.example.data.db.SyncLogEntity
import com.example.github.GithubTreeBlob
import com.example.sync.IgnoreRules
import com.example.sync.OpResult
import com.example.sync.SourceKeys
import com.example.sync.TreeItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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
    val expanded: Set<String> = emptySet(),
    /** TreeItem.key yang dicentang. */
    val selected: Set<String> = emptySet(),
    /** sourceKey yang sedang diunduh/diunggah. */
    val busySources: Set<String> = emptySet(),
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
    private val ignoreRules = IgnoreRules(IgnoreRules.DEFAULT_PATTERNS)

    val driveAccounts: StateFlow<List<DriveAccountEntity>> = repository.driveAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val githubAccounts: StateFlow<List<GithubAccountEntity>> = repository.githubAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val repos: StateFlow<List<GithubRepoEntity>> = repository.githubRepos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentLogs: StateFlow<List<SyncLogEntity>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(SyncUiState(isOnline = checkOnline(application)))
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        // Setiap akun Drive yang belum dimuat (baru atau setelah restart) langsung dimuat 3 level.
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

    fun onDriveAuthError(message: String) {
        _uiState.update { it.copy(isConnectingDrive = false, driveAuthError = message) }
    }

    fun onDriveAccessTokenReceived(accessToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectingDrive = true, driveAuthError = null) }
            try {
                val info = driveApi.fetchUserInfo(accessToken)
                repository.addOrUpdateDriveAccount(info.email, info.displayName, accessToken)
                repository.addLog(
                    sourceLabel = "Drive ${info.email}",
                    target = "DRIVE",
                    action = "AUTH",
                    status = "OK",
                    message = "Akun Google Drive terhubung"
                )
                _uiState.update { it.copy(snackbarMessage = "Google Drive terhubung: ${info.email}") }
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
            repository.addLog(
                sourceLabel = "Drive $email",
                target = "DRIVE",
                action = "AUTH",
                status = "OK",
                message = "Akun diputus. Folder lokal tidak dihapus."
            )
            dropSource(SourceKeys.drive(email))
            _uiState.update { it.copy(snackbarMessage = "Akun $email diputus. Folder lokal tidak dihapus.") }
        }
    }

    /** Memuat ulang pohon Drive untuk satu akun. */
    fun reloadDrive(email: String) {
        val key = SourceKeys.drive(email)
        _uiState.update { st ->
            st.copy(loaded = st.loaded.filterNot { it == key || it.startsWith("$key|") }.toSet())
        }
        viewModelScope.launch { fetchDriveLevel(email, parent = null, remaining = DRIVE_PRELOAD_LEVELS) }
    }

    /** Membuka/menutup folder atau akun Drive. Folder yang belum dimuat diambil dari Drive. */
    fun toggleDriveExpand(email: String, folder: TreeItem?) {
        val key = folder?.key ?: SourceKeys.drive(email)
        val wasExpanded = key in _uiState.value.expanded
        _uiState.update { st ->
            st.copy(expanded = if (wasExpanded) st.expanded - key else st.expanded + key)
        }
        if (!wasExpanded && key !in _uiState.value.loaded && folder != null) {
            viewModelScope.launch { fetchDriveLevel(email, parent = folder, remaining = 1) }
        }
    }

    /** Buka/tutup folder di mana pun: Drive memuat anak dari Drive, GitHub cukup ganti status lokal. */
    fun toggleFolder(item: TreeItem) {
        if (item.sourceKey.startsWith("drive:")) {
            toggleDriveExpand(item.sourceKey.removePrefix("drive:"), item)
        } else {
            _uiState.update { st ->
                st.copy(expanded = if (item.key in st.expanded) st.expanded - item.key else st.expanded + item.key)
            }
        }
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
                repository.addLog(
                    sourceLabel = "GitHub @${user.login}",
                    target = "GITHUB",
                    action = "AUTH",
                    status = "OK",
                    message = "Akun ditambahkan, ${summaries.size} repo terhubung"
                )
                _uiState.update {
                    it.copy(snackbarMessage = "Akun GitHub @${user.login} berhasil, ${summaries.size} repo")
                }
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
                _uiState.update { it.copy(snackbarMessage = "Daftar repo @${account.username} diperbarui") }
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
            _uiState.update { it.copy(snackbarMessage = "Akun GitHub @${account.username} dihapus") }
        }
    }

    /** Membuka/menutup repo. Pohon repo dimuat saat pertama dibuka. */
    fun toggleRepoExpand(repo: GithubRepoEntity) {
        val key = SourceKeys.github(repo.fullName)
        val wasExpanded = key in _uiState.value.expanded
        _uiState.update { st ->
            st.copy(expanded = if (wasExpanded) st.expanded - key else st.expanded + key)
        }
        if (!wasExpanded && key !in _uiState.value.loaded) {
            viewModelScope.launch { loadRepoTree(repo) }
        }
    }

    fun reloadRepo(repo: GithubRepoEntity) {
        val key = SourceKeys.github(repo.fullName)
        _uiState.update { st -> st.copy(loaded = st.loaded - key) }
        viewModelScope.launch { loadRepoTree(repo) }
    }

    // ---------- Pilihan & transfer ----------

    fun toggleSelect(item: TreeItem) {
        _uiState.update { st ->
            st.copy(selected = if (item.key in st.selected) st.selected - item.key else st.selected + item.key)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selected = emptySet()) }
    }

    fun downloadSelected() = runSelected(upload = false)

    fun uploadSelected() = runSelected(upload = true)

    private fun runSelected(upload: Boolean) {
        viewModelScope.launch {
            val st = _uiState.value
            val all = st.trees.values.flatten()
            val picked = all.filter { it.key in st.selected }
            // Folder yang sudah dipilih sudah mencakup anak-anaknya; jangan dikerjakan dua kali.
            val tops = picked.filter { item ->
                picked.none { other -> other !== item && other.isFolder && other.sourceKey == item.sourceKey && other.covers(item.path) }
            }
            if (tops.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = "Centang file atau folder dulu") }
                return@launch
            }

            var total = OpResult()
            for ((sourceKey, items) in tops.groupBy { it.sourceKey }) {
                _uiState.update { it.copy(busySources = it.busySources + sourceKey) }
                total += try {
                    runOperation(sourceKey, items, upload)
                } catch (e: Exception) {
                    repository.addLog(
                        sourceLabel = sourceLabel(sourceKey),
                        target = "SYSTEM",
                        action = if (upload) "UPLOAD" else "DOWNLOAD",
                        status = "ERROR",
                        message = e.message ?: "Operasi gagal"
                    )
                    OpResult(failed = items.size)
                } finally {
                    _uiState.update { it.copy(busySources = it.busySources - sourceKey) }
                }
            }

            val verb = if (upload) "Upload" else "Download"
            _uiState.update {
                it.copy(
                    selected = emptySet(),
                    snackbarMessage = "$verb selesai: ${total.transferred} ditransfer, ${total.skipped} dilewati, ${total.failed} gagal"
                )
            }
        }
    }

    private suspend fun runOperation(sourceKey: String, items: List<TreeItem>, upload: Boolean): OpResult {
        if (sourceKey.startsWith("drive:")) {
            val email = sourceKey.removePrefix("drive:")
            val root = LocalPathHelper.driveRoot(email)
            return if (upload) {
                engine.uploadDrive(email, root, ignoreRules, items)
            } else {
                engine.downloadDrive(email, root, items)
            }
        }
        val fullName = sourceKey.removePrefix("github:")
        val repo = repos.value.firstOrNull { it.fullName == fullName }
            ?: error("Repo $fullName tidak ditemukan")
        val pat = repository.getGithubPat(repo.accountId)
            ?: error("PAT untuk @${repo.owner} tidak ada. Tambahkan ulang akun.")
        val root = File(repo.localPath)
        return if (upload) {
            engine.uploadGithub(pat, repo.owner, repo.name, repo.defaultBranch, root, ignoreRules, items)
        } else {
            engine.downloadGithub(pat, repo.owner, repo.name, repo.defaultBranch, root, items)
        }
    }

    // ---------- Log ----------

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            _uiState.update { it.copy(snackbarMessage = "Riwayat log dibersihkan") }
        }
    }

    // ---------- Internal ----------

    private suspend fun fetchDriveLevel(email: String, parent: TreeItem?, remaining: Int) {
        val sourceKey = SourceKeys.drive(email)
        val parentKey = parent?.key ?: sourceKey
        val parentPath = parent?.path ?: ""
        _uiState.update { it.copy(loading = it.loading + parentKey) }
        try {
            val children = engine.driveChildren(email, parent?.remoteId ?: "root")
            val items = children.map { child ->
                TreeItem(
                    sourceKey = sourceKey,
                    path = if (parentPath.isEmpty()) child.name else "$parentPath/${child.name}",
                    name = child.name,
                    isFolder = child.isFolder,
                    size = child.size,
                    remoteId = child.id,
                    nativeMime = child.nativeMime
                )
            }
            _uiState.update { st ->
                val prefix = if (parentPath.isEmpty()) "$sourceKey|" else "$sourceKey|$parentPath/"
                val existing = st.trees[sourceKey].orEmpty()
                // Ganti isi folder ini; keturunan lama ikut dibuang.
                val kept = existing.filterNot { it.key.startsWith(prefix) || (parentPath.isEmpty() && it.sourceKey == sourceKey) }
                val staleLoaded = st.loaded.filter { it.startsWith(prefix) }.toSet()
                st.copy(
                    trees = st.trees + (sourceKey to kept + items),
                    loaded = (st.loaded - staleLoaded) + parentKey
                )
            }
            if (remaining > 1) {
                for (folder in items.filter { it.isFolder }) {
                    fetchDriveLevel(email, folder, remaining - 1)
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(snackbarMessage = "Gagal memuat Drive $email: ${e.message ?: "tidak diketahui"}") }
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
            val folders = linkedSetOf<String>()
            blobs.forEach { blob ->
                val parts = blob.repoPath.split('/')
                for (i in 0 until parts.size - 1) folders += parts.take(i + 1).joinToString("/")
            }
            val items = folders.map { path ->
                TreeItem(sourceKey, path, path.substringAfterLast('/'), true, null, null, null, repo.archived)
            } + blobs.map { blob ->
                TreeItem(sourceKey, blob.repoPath, blob.repoPath.substringAfterLast('/'), false, blob.size, blob.sha, null, repo.archived)
            }
            _uiState.update { st ->
                st.copy(trees = st.trees + (sourceKey to items), loaded = st.loaded + sourceKey)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(snackbarMessage = "Gagal memuat repo ${repo.fullName}: ${e.message ?: "tidak diketahui"}") }
        } finally {
            _uiState.update { it.copy(loading = it.loading - sourceKey) }
        }
    }

    /** Membuang semua state (pohon, pilihan, expand) untuk satu sumber. */
    private fun dropSource(sourceKey: String) {
        _uiState.update { st ->
            fun owned(key: String) = key == sourceKey || key.startsWith("$sourceKey|")
            st.copy(
                trees = st.trees - sourceKey,
                loaded = st.loaded.filterNot(::owned).toSet(),
                expanded = st.expanded.filterNot(::owned).toSet(),
                selected = st.selected.filterNot(::owned).toSet()
            )
        }
    }

    private fun sourceLabel(sourceKey: String): String =
        if (sourceKey.startsWith("drive:")) "Drive ${sourceKey.removePrefix("drive:")}"
        else "GitHub ${sourceKey.removePrefix("github:")}"

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
        private const val DRIVE_PRELOAD_LEVELS = 3
    }
}
