package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SyncBebasApp
import com.example.data.LocalFileOps
import com.example.data.LocalPathHelper
import com.example.data.SyncSummary
import com.example.data.SyncSummaryStore
import com.example.data.db.DriveAccountEntity
import com.example.data.db.GithubAccountEntity
import com.example.data.db.GithubRepoEntity
import com.example.github.GithubCommitEntry
import com.example.github.GithubTreeBlob
import com.example.sync.IgnoreRules
import com.example.sync.OpResult
import com.example.sync.SourceKeys
import com.example.sync.TreeItem
import com.example.sync.isHiddenPath
import com.example.sync.isTrackedRepoPath
import com.example.sync.trackedDirPaths
import com.example.sync.LocalScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /** sourceKey yang sedang diunduh/diunggah. */
    val busySources: Set<String> = emptySet(),
    val isConnectingDrive: Boolean = false,
    val isAddingGithub: Boolean = false,
    val driveAuthError: String? = null,
    val githubAccountError: String? = null,
    val snackbarMessage: String? = null,
    val isOnline: Boolean = true
)

/** Layar detail: satu folder root Drive atau satu repo GitHub. */
data class FolderDetail(
    val title: String,
    val sourceKey: String,
    /** Path relatif terhadap root sumber. Kosong untuk root repo. */
    val relPath: String,
    val localPath: String,
    /** Kunci ringkasan sync: TreeItem.key untuk folder Drive, sourceKey untuk repo. */
    val summaryKey: String,
    val driveItem: TreeItem? = null,
    val repo: GithubRepoEntity? = null
)

enum class SaveStatus { IDLE, SAVING, SAVED, ERROR }

/** Editor teks untuk satu berkas. [initialText] dibaca sekali saat dibuka. */
data class EditorState(
    val file: File,
    val initialText: String,
    val status: SaveStatus = SaveStatus.IDLE,
    val error: String? = null,
    /** Naik setiap kali isi diganti dari luar (pulihkan versi), supaya editor dibangun ulang. */
    val revision: Int = 0
)

/** Riwayat commit satu berkas di GitHub, beserta pratinjau versi yang dipilih. [path] relatif terhadap akar repo. */
data class HistoryState(
    val path: String,
    val loading: Boolean = false,
    val commits: List<GithubCommitEntry> = emptyList(),
    val error: String? = null,
    val selected: GithubCommitEntry? = null,
    val previewLoading: Boolean = false,
    val previewText: String? = null,
    val previewError: String? = null
)

/** Penjelajah file untuk satu repo GitHub. [root] adalah akar repo lokal, [dir] folder yang sedang dibuka. */
data class BrowseState(
    val title: String,
    val root: File,
    val dir: File,
    val entries: List<LocalFileOps.Entry>,
    val editor: EditorState? = null,
    val repo: GithubRepoEntity? = null,
    val history: HistoryState? = null,
    /** true untuk repo GitHub: hanya berkas yang dilacak (lihat isTrackedRepoPath) yang tampil. */
    val trackedOnly: Boolean = false
)

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SyncBebasApp
    private val repository = app.repository
    private val driveApi = app.driveApiClient
    private val githubApi = app.githubApiClient
    private val engine = app.syncEngine
    private val summaryStore = SyncSummaryStore(application)
    private val ignoreRules = IgnoreRules(IgnoreRules.DEFAULT_PATTERNS)
    private val autoSync = app.autoSync

    val driveAccounts: StateFlow<List<DriveAccountEntity>> = repository.driveAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val githubAccounts: StateFlow<List<GithubAccountEntity>> = repository.githubAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val repos: StateFlow<List<GithubRepoEntity>> = repository.githubRepos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(SyncUiState(isOnline = checkOnline(application)))
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private val _summaries = MutableStateFlow(summaryStore.loadAll())
    /** Ringkasan sync terakhir per kunci (lihat [FolderDetail.summaryKey]). */
    val summaries: StateFlow<Map<String, SyncSummary>> = _summaries.asStateFlow()

    private val _detail = MutableStateFlow<FolderDetail?>(null)
    val detail: StateFlow<FolderDetail?> = _detail.asStateFlow()

    private val _browse = MutableStateFlow<BrowseState?>(null)
    /** Penjelajah file yang sedang dibuka, null jika tidak ada. */
    val browse: StateFlow<BrowseState?> = _browse.asStateFlow()

    init {
        // Pesan dari auto-sync (konflik, gagal push, sukses push) tampil sebagai snackbar.
        viewModelScope.launch {
            autoSync.messages.collect { msg -> _uiState.update { it.copy(snackbarMessage = msg) } }
        }
        // Berkas yang sedang dibuka di editor tidak ditimpa oleh pull; pull-nya ditunda ke siklus berikutnya.
        autoSync.isEditing = { file -> _browse.value?.editor?.file?.absolutePath == file.absolutePath }
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

    // ---------- Detail folder / repo ----------

    /** Membuka detail folder root Drive. */
    fun openDriveFolder(item: TreeItem) {
        val email = item.sourceKey.removePrefix("drive:")
        val root = LocalPathHelper.driveRoot(email)
        _detail.value = FolderDetail(
            title = item.name,
            sourceKey = item.sourceKey,
            relPath = item.path,
            localPath = File(root, item.path).absolutePath,
            summaryKey = item.key,
            driveItem = item
        )
    }

    /** Membuka detail repo. Pohon dimuat di latar belakang bila belum ada (dibutuhkan untuk download/unggah). */
    fun openRepo(repo: GithubRepoEntity) {
        val sourceKey = SourceKeys.github(repo.fullName)
        _detail.value = FolderDetail(
            title = repo.fullName,
            sourceKey = sourceKey,
            relPath = "",
            localPath = repo.localPath,
            summaryKey = sourceKey,
            repo = repo
        )
        if (sourceKey !in _uiState.value.loaded) {
            viewModelScope.launch { loadRepoTree(repo) }
        }
    }

    fun closeDetail() {
        _detail.value = null
    }

    /** Download/unggah dari layar detail, memakai alur yang sama dengan beranda. */
    fun syncDetail(upload: Boolean) {
        val current = _detail.value ?: return
        current.driveItem?.let { syncFolder(it, upload) }
        current.repo?.let { syncRepo(it, upload) }
    }

    // ---------- Transfer (lewat ikon per folder dasar atau per repo) ----------

    /** Download/unggah satu folder dasar, termasuk seluruh isinya. */
    fun syncFolder(item: TreeItem, upload: Boolean) {
        viewModelScope.launch { runItems(listOf(item), upload, label = item.name, summaryKey = item.key) }
    }

    /**
     * Download/unggah seluruh repo: semua item dasar (folder dan file dasar).
     * File dasar tetap ikut walau tidak ditampilkan. Pohon dimuat dulu bila belum ada.
     */
    fun syncRepo(repo: GithubRepoEntity, upload: Boolean) {
        viewModelScope.launch {
            val sourceKey = SourceKeys.github(repo.fullName)
            if (sourceKey !in _uiState.value.loaded) loadRepoTree(repo)
            val rootItems = _uiState.value.trees[sourceKey].orEmpty().filter { !it.path.contains('/') }
            if (rootItems.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = "Repo ${repo.name} kosong atau gagal dimuat") }
                return@launch
            }
            runItems(rootItems, upload, label = repo.name, summaryKey = sourceKey)
        }
    }

    private suspend fun runItems(items: List<TreeItem>, upload: Boolean, label: String, summaryKey: String) {
        if (items.isEmpty()) return
        val sourceKey = items.first().sourceKey
        _uiState.update { it.copy(busySources = it.busySources + sourceKey) }
        var errorMessage: String? = null
        val result = try {
            runOperation(sourceKey, items, upload)
        } catch (e: Exception) {
            errorMessage = e.message ?: "Operasi gagal"
            OpResult(failed = items.size)
        } finally {
            _uiState.update { it.copy(busySources = it.busySources - sourceKey) }
        }
        val summary = SyncSummary.fromResult(result, System.currentTimeMillis())
        summaryStore.save(summaryKey, summary)
        _summaries.update { it + (summaryKey to summary) }

        val verb = if (upload) "Unggah" else "Download"
        val text = if (errorMessage != null) {
            "$verb $label gagal: $errorMessage"
        } else {
            "$verb $label selesai: ${result.transferred} ditransfer, ${result.skipped} dilewati, ${result.failed} gagal"
        }
        _uiState.update { it.copy(snackbarMessage = text) }
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
                .filterNot { isHiddenPath(it.repoPath) }
                .filter { isTrackedRepoPath(it.repoPath) }
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
        // Sha yang sama di perangkat dan GitHub dicatat sebagai base, supaya auto-sync tahu titik awalnya.
        val shas = linkedMapOf<String, String>()
        val result = autoSync.withRepoLock(repo.fullName) {
            if (upload) {
                engine.uploadGithub(
                    pat, repo.owner, repo.name, repo.defaultBranch, root, ignoreRules, items,
                    onSynced = { path, sha -> shas[path] = sha }
                )
            } else {
                engine.downloadGithub(
                    pat, repo.owner, repo.name, repo.defaultBranch, root, items,
                    onSynced = { path, sha -> shas[path] = sha }
                )
            }
        }
        autoSync.recordBases(repo.fullName, shas)
        return result
    }

    // ---------- Penjelajah file & editor (repo GitHub) ----------

    /** Membuka penjelajah di salinan lokal sebuah repo atau folder Drive. [trackedOnly] untuk repo GitHub. */
    fun openLocalFolder(title: String, localPath: String, trackedOnly: Boolean = false) {
        val root = File(localPath)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { root.mkdirs() }
            _browse.value = BrowseState(
                title = title,
                root = root,
                dir = root,
                entries = withContext(Dispatchers.IO) { listBrowse(root, root, trackedOnly) },
                trackedOnly = trackedOnly
            )
        }
    }

    /** Isi satu folder untuk penjelajah. Pada mode [trackedOnly], hanya berkas yang dilacak dan folder yang memuatnya. */
    private fun listBrowse(root: File, dir: File, trackedOnly: Boolean): List<LocalFileOps.Entry> {
        val raw = LocalFileOps.list(dir)
        if (!trackedOnly) return raw
        val tracked = LocalScanner.scanFiles(root, ignoreRules).keys.filter { isTrackedRepoPath(it) }.toSet()
        val dirs = trackedDirPaths(tracked)
        return raw.filter { e ->
            val rel = e.file.relativeTo(root).invariantSeparatorsPath
            if (e.isDir) rel in dirs else rel in tracked
        }
    }

    /** Menutup penjelajah sepenuhnya. Editor yang masih terbuka ikut ditutup; auto-save sudah dijalankan oleh layar. */
    fun closeBrowse() {
        _browse.value = null
    }

    fun browseTo(entry: LocalFileOps.Entry) {
        if (!entry.isDir) return
        _browse.update { it?.copy(dir = entry.file, editor = null) }
        viewModelScope.launch { refreshBrowse() }
    }

    /** Tombol kembali: tutup editor dulu, lalu naik satu folder. Mengembalikan false jika sudah di akar. */
    fun handleBrowseBack(): Boolean {
        val current = _browse.value ?: return false
        if (current.editor != null) {
            closeEditor()
            return true
        }
        if (current.dir.absolutePath == current.root.absolutePath) return false
        val parent = current.dir.parentFile ?: return false
        _browse.update { it?.copy(dir = parent) }
        viewModelScope.launch { refreshBrowse() }
        return true
    }

    fun openEditor(entry: LocalFileOps.Entry) {
        if (!entry.file.isFile) return
        viewModelScope.launch {
            val editable = withContext(Dispatchers.IO) { LocalFileOps.isEditable(entry.file) }
            if (!editable) {
                showMessage("${entry.name} tidak bisa diedit: berkas biner atau lebih dari 2 MB.")
                return@launch
            }
            val text = try {
                withContext(Dispatchers.IO) { LocalFileOps.readText(entry.file) }
            } catch (e: Exception) {
                showMessage("Gagal membuka ${entry.name}: ${e.message ?: "tidak diketahui"}")
                return@launch
            }
            _browse.update { it?.copy(editor = EditorState(entry.file, text)) }
        }
    }

    fun closeEditor() {
        _browse.update { it?.copy(editor = null, history = null) }
    }

    private fun relativePath(root: File, file: File): String =
        file.absolutePath.removePrefix(root.absolutePath).trimStart('/')

    /** Memuat riwayat commit berkas yang sedang diedit dari GitHub. */
    fun openHistory() {
        val b = _browse.value ?: return
        val editor = b.editor ?: return
        val repo = b.repo ?: return
        val path = relativePath(b.root, editor.file)
        _browse.update { it?.copy(history = HistoryState(path = path, loading = true)) }
        viewModelScope.launch {
            val result = runCatching {
                val pat = repository.getGithubPat(repo.accountId) ?: error("PAT tidak ada, tambahkan ulang akun.")
                githubApi.listCommitsForPath(pat, repo.owner, repo.name, repo.defaultBranch, path)
            }
            _browse.update { cur ->
                val h = cur?.history
                if (cur == null || h == null || h.path != path) cur
                else cur.copy(
                    history = h.copy(
                        loading = false,
                        commits = result.getOrDefault(emptyList()),
                        error = result.exceptionOrNull()?.let { e -> e.message ?: "Gagal memuat riwayat" }
                    )
                )
            }
        }
    }

    /** Mengambil isi berkas pada commit tertentu untuk dipratinjau. */
    fun previewVersion(commit: GithubCommitEntry) {
        val b = _browse.value ?: return
        val repo = b.repo ?: return
        val h = b.history ?: return
        _browse.update {
            it?.copy(history = h.copy(selected = commit, previewLoading = true, previewText = null, previewError = null))
        }
        viewModelScope.launch {
            val result = runCatching {
                val pat = repository.getGithubPat(repo.accountId) ?: error("PAT tidak ada, tambahkan ulang akun.")
                githubApi.readFileAtCommit(pat, repo.owner, repo.name, commit.sha, h.path)
            }
            _browse.update { cur ->
                val ch = cur?.history
                if (cur == null || ch == null || ch.selected?.sha != commit.sha) cur
                else cur.copy(
                    history = ch.copy(
                        previewLoading = false,
                        previewText = result.getOrNull(),
                        previewError = result.exceptionOrNull()?.let { e -> e.message ?: "Gagal memuat versi" }
                    )
                )
            }
        }
    }

    /** Kembali dari pratinjau ke daftar commit. */
    fun closeVersion() {
        _browse.update {
            it?.copy(history = it.history?.copy(selected = null, previewLoading = false, previewText = null, previewError = null))
        }
    }

    fun closeHistory() {
        _browse.update { it?.copy(history = null) }
    }

    /** Memulihkan isi editor dari versi yang dipratinjau. Ditulis ke disk, lalu auto-sync mendorongnya. */
    fun restoreVersion() {
        val b = _browse.value ?: return
        val editor = b.editor ?: return
        val text = b.history?.previewText ?: return
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { LocalFileOps.writeText(editor.file, text) }.exceptionOrNull()
            }
            if (error != null) {
                showMessage("Gagal memulihkan: ${error.message ?: "tidak diketahui"}")
                return@launch
            }
            _browse.update { cur ->
                val current = cur?.editor
                if (cur == null || current == null) cur
                else cur.copy(
                    editor = EditorState(
                        file = current.file,
                        initialText = text,
                        status = SaveStatus.SAVED,
                        revision = current.revision + 1
                    ),
                    history = null
                )
            }
            autoSync.notifyChanged()
            showMessage("Versi dipulihkan.")
        }
    }

    /**
     * Simpan isi editor ke [file]. Dipanggil dengan debounce dari layar, dan saat editor ditutup.
     * Tidak bergantung pada editor yang masih terbuka, supaya simpanan terakhir tidak hilang.
     */
    fun saveEditedFile(file: File, text: String) {
        setEditorStatus(file, SaveStatus.SAVING, null)
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { LocalFileOps.writeText(file, text) }.exceptionOrNull()
            }
            if (error == null) {
                setEditorStatus(file, SaveStatus.SAVED, null)
                autoSync.notifyChanged()
            } else {
                setEditorStatus(file, SaveStatus.ERROR, error.message ?: "Gagal menyimpan")
            }
        }
    }

    fun createEntry(name: String, isFolder: Boolean) {
        val dir = _browse.value?.dir ?: return
        runFileOp(success = null) {
            if (isFolder) LocalFileOps.createFolder(dir, name) else LocalFileOps.createFile(dir, name)
        }
    }

    fun renameEntry(entry: LocalFileOps.Entry, newName: String) {
        closeEditorIfInside(entry.file)
        runFileOp(success = null) { LocalFileOps.rename(entry.file, newName) }
    }

    fun deleteEntry(entry: LocalFileOps.Entry) {
        closeEditorIfInside(entry.file)
        runFileOp(success = "${entry.name} dihapus.") { LocalFileOps.delete(entry.file) }
    }

    private fun closeEditorIfInside(file: File) {
        val editor = _browse.value?.editor ?: return
        val target = file.absolutePath
        if (editor.file.absolutePath == target || editor.file.absolutePath.startsWith("$target/")) closeEditor()
    }

    private fun runFileOp(success: String?, block: () -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { block() } }
            result.onFailure { showMessage(it.message ?: "Operasi gagal") }
            result.onSuccess { success?.let { showMessage(it) } }
            refreshBrowse()
            autoSync.notifyChanged()
        }
    }

    private suspend fun refreshBrowse() {
        val current = _browse.value ?: return
        val dir = current.dir
        val result = withContext(Dispatchers.IO) { runCatching { listBrowse(current.root, dir, current.trackedOnly) } }
        result.onFailure { showMessage("Gagal membaca folder: ${it.message ?: "tidak diketahui"}") }
        val entries = result.getOrDefault(emptyList())
        _browse.update { b -> if (b != null && b.dir.absolutePath == dir.absolutePath) b.copy(entries = entries) else b }
    }

    private fun setEditorStatus(file: File, status: SaveStatus, error: String?) {
        _browse.update { b ->
            val editor = b?.editor
            if (b == null || editor == null || editor.file.absolutePath != file.absolutePath) b
            else b.copy(editor = editor.copy(status = status, error = error))
        }
    }


    /** Membuang semua state (pohon, ringkasan, detail) untuk satu sumber. */
    private fun dropSource(sourceKey: String) {
        fun owned(key: String) = key == sourceKey || key.startsWith("$sourceKey|")
        summaryStore.removeOwned(sourceKey)
        _summaries.update { m -> m.filterKeys { !owned(it) } }
        _uiState.update { st ->
            st.copy(
                trees = st.trees - sourceKey,
                loaded = st.loaded.filterNot(::owned).toSet()
            )
        }
        if (_detail.value?.sourceKey == sourceKey) closeDetail()
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
        /** Batas panggilan API untuk menghitung isi folder Drive. */
    }
}
