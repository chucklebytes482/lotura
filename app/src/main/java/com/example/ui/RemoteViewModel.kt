package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SyncBebasApp
import com.example.data.db.DriveAccountEntity
import com.example.data.db.GithubAccountEntity
import com.example.data.db.GithubRepoEntity
import com.example.data.db.MirrorJobEntity
import com.example.remote.Folder
import com.example.remote.Location
import com.example.remote.MirrorAutoSync
import com.example.remote.PickedFolder
import com.example.remote.RemoteEntry
import com.example.remote.RemoteFiles
import com.example.remote.Source
import com.example.remote.UploadMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class FolderUploadQueue(
    val folders: List<PickedFolder> = emptyList(),
    val picking: Boolean = false,
    val error: String? = null,
    val mirror: Boolean = false,
    /** 0 = tidak otomatis. Hanya dipakai saat mirror aktif. */
    val autoHours: Int = 0
)

internal data class BrowserState(
    val location: Location? = null,
    val entries: List<RemoteEntry> = emptyList(),
    val ready: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val isConnectingDrive: Boolean = false,
    val isAddingGithub: Boolean = false,
    val driveAuthError: String? = null,
    val githubAccountError: String? = null,
    val folderUpload: FolderUploadQueue? = null,
    val snackbarMessage: String? = null,
    val isOnline: Boolean = true
)

/** Daftar folder root Drive untuk satu akun. Disimpan di memori saja, dimuat ulang saat app dibuka lagi. */
internal data class DriveRootFolders(
    val loading: Boolean = false,
    val folders: List<RemoteEntry> = emptyList(),
    val error: String? = null,
    val loaded: Boolean = false
)

internal class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SyncBebasApp
    private val repository = app.repository
    private val driveApi = app.driveApiClient
    private val githubApi = app.githubApiClient
    private val files = RemoteFiles(app)
    private var pickerTarget: Location? = null

    val driveAccounts: StateFlow<List<DriveAccountEntity>> = repository.driveAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val githubAccounts: StateFlow<List<GithubAccountEntity>> = repository.githubAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val repos: StateFlow<List<GithubRepoEntity>> = repository.githubRepos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mirrorJobs: StateFlow<List<MirrorJobEntity>> = repository.mirrorJobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(BrowserState(isOnline = checkOnline(application)))
    val uiState = _uiState.asStateFlow()

    fun openSource(source: Source) = navigate(Location(source))

    private val _driveRootFolders = MutableStateFlow<Map<String, DriveRootFolders>>(emptyMap())
    val driveRootFolders: StateFlow<Map<String, DriveRootFolders>> = _driveRootFolders.asStateFlow()

    /** Memuat folder root sekali per akun. force = true untuk tombol muat ulang. */
    fun loadDriveRootFolders(email: String, force: Boolean = false) {
        val current = _driveRootFolders.value[email] ?: DriveRootFolders()
        if (current.loading || (current.loaded && !force)) return
        _driveRootFolders.update { it + (email to current.copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val folders = files.driveRootFolders(email)
                _driveRootFolders.update { it + (email to DriveRootFolders(folders = folders, loaded = true)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _driveRootFolders.update { map ->
                    val old = map[email] ?: DriveRootFolders()
                    map + (email to old.copy(loading = false, loaded = true, error = e.message ?: "Folder tidak bisa dimuat."))
                }
            }
        }
    }

    /** Akun yang daftarnya sedang dilipat. Kunci: "drive:<email>" atau "github:<id>". Disimpan di ViewModel agar bertahan saat masuk folder. */
    private val _collapsedAccounts = MutableStateFlow<Set<String>>(emptySet())
    val collapsedAccounts: StateFlow<Set<String>> = _collapsedAccounts.asStateFlow()

    fun toggleAccountCollapsed(key: String) = _collapsedAccounts.update { if (key in it) it - key else it + key }

    fun openDriveFolder(email: String, folder: RemoteEntry) {
        navigate(Location(Source.Drive(email), listOf(Folder(folder.name, folder.ref))))
    }
    fun openFolder(item: RemoteEntry) {
        val state = _uiState.value
        val current = state.location ?: return
        if (state.ready && item.isFolder && item in state.entries) navigate(current.open(item))
    }

    fun back() {
        val current = _uiState.value.location ?: return
        navigate(if (current.folders.isEmpty()) null else current.copy(folders = current.folders.dropLast(1)))
    }

    /** Kembali ke daftar akun (Drive dan GitHub), langsung dari folder mana pun. */
    fun goHome() = navigate(null)

    private fun navigate(location: Location?) {
        if (_uiState.value.busy || pickerTarget != null) return
        _uiState.update { it.copy(location = location, entries = emptyList(), ready = false, error = null) }
        if (location != null) runBusy(location, refresh = true) { null }
    }

    fun preparePicker(): Boolean {
        val state = _uiState.value
        val target = state.location ?: return false
        if (state.busy || !state.ready || !target.writable || pickerTarget != null) return false
        pickerTarget = target
        return true
    }

    fun uploadSelected(uris: List<Uri>) {
        if (_uiState.value.folderUpload != null) return
        val target = pickerTarget ?: return
        pickerTarget = null
        if (uris.isNotEmpty() && target == _uiState.value.location) {
            runBusy(target, refresh = true) { files.uploadFiles(target, uris) }
        }
    }

    fun beginFolderUpload(): Boolean {
        if (!preparePicker()) return false
        _uiState.update { it.copy(folderUpload = FolderUploadQueue()) }
        return true
    }

    fun beginAddingFolder(): Boolean {
        val queue = _uiState.value.folderUpload ?: return false
        if (queue.picking) return false
        _uiState.update { it.copy(folderUpload = queue.copy(picking = true, error = null)) }
        return true
    }

    fun onFolderPicked(uri: Uri?, error: String? = null) {
        val queue = _uiState.value.folderUpload?.takeIf { it.picking } ?: return
        if (uri == null) {
            _uiState.update { it.copy(folderUpload = queue.copy(picking = false, error = error)) }
            return
        }
        viewModelScope.launch {
            val loaded = try {
                val folder = files.pickFolder(uri)
                when {
                    queue.folders.any { it.tree == uri } -> queue.copy(error = "Folder ini sudah dipilih.")
                    queue.folders.any { it.name == folder.name } -> queue.copy(error = "Sudah ada folder bernama ${folder.name} dalam pilihan.")
                    else -> queue.copy(folders = queue.folders + folder, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                queue.copy(error = e.message ?: "Folder tidak dapat dibaca.")
            }
            _uiState.update { it.copy(folderUpload = loaded.copy(picking = false)) }
        }
    }

    fun removePickedFolder(folder: PickedFolder) {
        val queue = _uiState.value.folderUpload?.takeUnless { it.picking } ?: return
        _uiState.update { it.copy(folderUpload = queue.copy(folders = queue.folders - folder, error = null)) }
    }

    fun setFolderUploadMirror(enabled: Boolean) {
        val queue = _uiState.value.folderUpload?.takeUnless { it.picking } ?: return
        if (enabled && pickerTarget?.source !is Source.Drive) return
        _uiState.update { it.copy(folderUpload = queue.copy(mirror = enabled, error = null)) }
    }

    fun setFolderUploadInterval(hours: Int) {
        val queue = _uiState.value.folderUpload?.takeUnless { it.picking } ?: return
        _uiState.update { it.copy(folderUpload = queue.copy(autoHours = hours)) }
    }

    fun cancelFolderUpload() {
        if (_uiState.value.folderUpload?.picking != false) return
        pickerTarget = null
        _uiState.update { it.copy(folderUpload = null) }
    }

    fun uploadPickedFolders() {
        val queue = _uiState.value.folderUpload?.takeUnless { it.picking || it.folders.isEmpty() } ?: return
        val target = pickerTarget ?: return
        cancelFolderUpload()
        if (target == _uiState.value.location) {
            val mode = if (queue.mirror) UploadMode.MIRROR else UploadMode.ADD
            val hours = if (queue.mirror) queue.autoHours else 0
            runBusy(target, refresh = true) {
                val summary = files.uploadFolders(target, queue.folders, mode)
                if (hours > 0) {
                    files.enableMirrorAuto(target, queue.folders, hours)
                    "$summary Auto sync ${MirrorAutoSync.label(hours).lowercase()} aktif."
                } else summary
            }
        }
    }

    fun stopMirrorJob(job: MirrorJobEntity) {
        viewModelScope.launch {
            try {
                repository.stopMirrorJob(job.jobKey)
                MirrorAutoSync.cancel(app, job.jobKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(e.message ?: "Auto sync tidak bisa dihentikan.")
            }
        }
    }

    fun createFolder(name: String) = action(refresh = true) { location ->
        files.createFolder(location, name)
        "Folder $name dibuat di remote."
    }

    fun delete(item: RemoteEntry) = action(refresh = true, item = item) { location ->
        files.delete(location, item)
        if (location.source is Source.Drive) "Dipindahkan ke Sampah Drive." else "Dihapus dari cabang GitHub."
    }

    fun download(item: RemoteEntry) = action(refresh = false, item = item) { location ->
        val result = files.download(location, item)
        "Disimpan: ${result.file.absolutePath}" +
            if (result.skipped > 0) " • ${result.skipped} item tidak mendukung unduhan." else ""
    }

    private fun action(refresh: Boolean, item: RemoteEntry? = null, block: suspend (Location) -> String) {
        val state = _uiState.value
        val location = state.location ?: return
        if (!state.ready || pickerTarget != null || (item != null && item !in state.entries)) return
        runBusy(location, refresh) { block(location) }
    }

    private fun runBusy(location: Location, refresh: Boolean, work: suspend () -> String?) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                try {
                    work()?.let(::showMessage)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showMessage(e.message ?: "Operasi remote gagal.")
                }
                if (refresh) {
                    try {
                        val entries = files.list(location)
                        _uiState.update { it.copy(entries = entries, ready = true, error = null) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _uiState.update { it.copy(entries = emptyList(), ready = false,
                            error = e.message ?: "Folder tidak bisa dimuat.") }
                    }
                }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

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
            } catch (e: CancellationException) {
                throw e
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
            _driveRootFolders.update { it - email }
            _uiState.update { it.copy(snackbarMessage = "Akun $email diputus. Folder lokal tidak dihapus.") }
        }
    }

    fun addGithubAccount(pat: String, onSuccess: () -> Unit) {
        if (_uiState.value.isAddingGithub) return
        viewModelScope.launch {
            val token = pat.trim()
            if (token.isEmpty()) {
                _uiState.update { it.copy(githubAccountError = "Fine-grained PAT wajib diisi.") }
                return@launch
            }
            _uiState.update { it.copy(isAddingGithub = true, githubAccountError = null) }
            try {
                val user = githubApi.getAuthenticatedUser(token)
                val summaries = githubApi.listRepositories(token)
                val accountId = repository.addGithubAccount(
                    label = user.name.trim().ifBlank { user.login },
                    username = user.login,
                    avatarUrl = user.avatarUrl,
                    pat = token
                )
                repository.replaceGithubRepos(accountId, summaries.map { it.toEntity(accountId) })
                onSuccess()
            } catch (e: CancellationException) {
                throw e
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
                repository.replaceGithubRepos(account.id, summaries.map { it.toEntity(account.id) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Gagal memuat repo: ${e.message ?: "tidak diketahui"}") }
            }
        }
    }

    fun deleteGithubAccount(account: GithubAccountEntity) {
        viewModelScope.launch {
            repository.deleteGithubAccount(account.id)
        }
    }

    private fun com.example.github.GithubRepoSummary.toEntity(accountId: Long) =
        GithubRepoEntity(
            fullName = fullName,
            accountId = accountId,
            owner = owner,
            name = name,
            defaultBranch = defaultBranch,
            isPrivate = isPrivate,
            archived = archived,
            localPath = ""
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

}
