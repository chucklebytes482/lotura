package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SyncBebasApp
import com.example.data.db.DeletePolicy
import com.example.data.db.GithubAccountEntity
import com.example.data.db.SyncLogEntity
import com.example.data.db.SyncPairEntity
import com.example.data.secure.AppPrefs
import com.example.drive.DriveFolderItem
import com.example.drive.GoogleAuthManager
import com.example.github.GithubRepoSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class DriveBrowserUiState(
    val isLoading: Boolean = false,
    val folders: List<DriveFolderItem> = emptyList(),
    val error: String? = null
)

data class GithubBrowserUiState(
    val isLoading: Boolean = false,
    val repos: List<GithubRepoSummary> = emptyList(),
    val error: String? = null
)

data class GithubFolderBrowserUiState(
    val isLoading: Boolean = false,
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val allFolders: List<String> = emptyList(),
    val error: String? = null
)

data class SyncUiState(
    val syncingPairIds: Set<Long> = emptySet(),
    val isConnectingGoogle: Boolean = false,
    val isAddingGithubAccount: Boolean = false,
    val googleAuthError: String? = null,
    val githubAccountError: String? = null,
    val snackbarMessage: String? = null,
    val isOnline: Boolean = true,
    val driveBrowser: DriveBrowserUiState = DriveBrowserUiState(),
    val githubBrowser: GithubBrowserUiState = GithubBrowserUiState(),
    val githubFolderBrowser: GithubFolderBrowserUiState = GithubFolderBrowserUiState()
)

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SyncBebasApp
    private val repository = app.repository
    private val driveApi = app.driveApiClient
    private val githubApi = app.githubApiClient
    private val syncExecutor = app.syncExecutor

    val pairs: StateFlow<List<SyncPairEntity>> = repository.syncPairs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val githubAccounts: StateFlow<List<GithubAccountEntity>> = repository.githubAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentLogs: StateFlow<List<SyncLogEntity>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appPrefs: StateFlow<AppPrefs> = repository.appPrefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPrefs())

    private val _uiState = MutableStateFlow(SyncUiState(isOnline = checkOnline(application)))
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    fun refreshNetworkStatus() {
        _uiState.update { it.copy(isOnline = checkOnline(app)) }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun onGoogleConnectingStarted() {
        _uiState.update { it.copy(isConnectingGoogle = true, googleAuthError = null) }
    }

    fun onGoogleAuthError(message: String) {
        _uiState.update { it.copy(isConnectingGoogle = false, googleAuthError = message) }
    }

    fun onGoogleAccessTokenReceived(accessToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectingGoogle = true, googleAuthError = null) }
            try {
                val userInfo = driveApi.fetchUserInfo(accessToken)
                repository.saveGoogleAccount(
                    email = userInfo.email,
                    displayName = userInfo.displayName,
                    accessToken = accessToken
                )
                repository.addLog(
                    target = "DRIVE",
                    action = "AUTH",
                    status = "OK",
                    message = "Terhubung ke Google Drive (${userInfo.email})"
                )
                _uiState.update {
                    it.copy(
                        isConnectingGoogle = false,
                        snackbarMessage = "Google Drive terhubung: ${userInfo.email}"
                    )
                }
            } catch (e: Exception) {
                repository.saveDriveAccessToken(accessToken)
                _uiState.update {
                    it.copy(
                        isConnectingGoogle = false,
                        snackbarMessage = "Token Google Drive tersimpan"
                    )
                }
            }
        }
    }

    fun disconnectGoogleAccount() {
        viewModelScope.launch {
            repository.clearGoogleAccount()
            repository.addLog(
                target = "DRIVE",
                action = "AUTH",
                status = "OK",
                message = "Akun Google Drive diputuskan"
            )
            _uiState.update { it.copy(snackbarMessage = "Akun Google Drive diputuskan") }
        }
    }

    fun addGithubAccount(pat: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val trimmedPat = pat.trim()
            if (trimmedPat.isEmpty()) {
                _uiState.update { it.copy(githubAccountError = "Fine-grained PAT wajib diisi.") }
                return@launch
            }
            _uiState.update { it.copy(isAddingGithubAccount = true, githubAccountError = null) }
            try {
                val user = githubApi.getAuthenticatedUser(trimmedPat)
                val autoLabel = user.name.trim().ifBlank { user.login }
                repository.addGithubAccount(
                    label = autoLabel,
                    username = user.login,
                    avatarUrl = user.avatarUrl,
                    pat = trimmedPat
                )
                repository.addLog(
                    target = "GITHUB",
                    action = "AUTH",
                    status = "OK",
                    message = "Akun GitHub @${user.login} ditambahkan (PAT terenkripsi Keystore)"
                )
                _uiState.update {
                    it.copy(
                        isAddingGithubAccount = false,
                        snackbarMessage = "Akun GitHub @${user.login} berhasil diverifikasi"
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAddingGithubAccount = false,
                        githubAccountError = e.localizedMessage ?: "Gagal memverifikasi PAT GitHub."
                    )
                }
            }
        }
    }

    fun deleteGithubAccount(account: GithubAccountEntity) {
        viewModelScope.launch {
            repository.deleteGithubAccount(account.id)
            _uiState.update { it.copy(snackbarMessage = "Akun GitHub @${account.username} dihapus") }
        }
    }

    fun savePair(pair: SyncPairEntity, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val normalized = if (pair.isGithubPair) {
                pair.copy(
                    driveFolderId = "",
                    driveFolderName = "",
                    deletePolicy = DeletePolicy.TWO_WAY.name,
                    autoSyncDrive = false,
                    githubEnabled = true,
                    githubIntervalMinutes = 0
                )
            } else {
                pair.copy(
                    deletePolicy = DeletePolicy.TWO_WAY.name,
                    autoSyncDrive = false,
                    githubEnabled = false,
                    githubAccountId = null,
                    githubOwner = "",
                    githubRepo = "",
                    githubBranch = "main",
                    githubSubfolder = "",
                    githubIntervalMinutes = 0
                )
            }

            // Ensure matching local device directory exists automatically
            runCatching {
                val dir = File(normalized.localPath)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }

            val savedId = repository.savePair(normalized)
            val savedPair = normalized.copy(id = savedId)
            repository.addLog(
                pairId = savedId,
                pairName = savedPair.name,
                target = if (savedPair.isGithubPair) "GITHUB" else "DRIVE",
                action = "CONFIG",
                status = "OK",
                message = "Pasangan folder disimpan"
            )
            _uiState.update { it.copy(snackbarMessage = "Pasangan \"${savedPair.name}\" disimpan") }
            onSaved(savedId)
        }
    }

    fun deletePair(pair: SyncPairEntity, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deletePair(pair.id)
            _uiState.update { it.copy(snackbarMessage = "Pasangan \"${pair.name}\" dihapus") }
            onDeleted()
        }
    }

    fun syncPairNow(pairId: Long) {
        viewModelScope.launch {
            refreshNetworkStatus()
            if (pairId in _uiState.value.syncingPairIds) return@launch
            _uiState.update {
                it.copy(
                    syncingPairIds = it.syncingPairIds + pairId,
                    snackbarMessage = "Tunggu hingga selesai agar sinkronisasi berhasil"
                )
            }
            try {
                val pair = repository.getPairById(pairId) ?: return@launch
                val message = if (pair.isGithubPair) {
                    syncExecutor.syncPairWithGithub(pairId).summary
                } else {
                    val token = GoogleAuthManager.tryGetSilentAccessToken(app)
                        ?: repository.getDriveAccessToken()
                    if (!token.isNullOrBlank()) {
                        syncExecutor.syncPairWithDrive(pairId, token).summary
                    } else {
                        "Google Drive belum terhubung"
                    }
                }
                _uiState.update { it.copy(snackbarMessage = message.ifBlank { "Sinkronisasi selesai" }) }
            } finally {
                _uiState.update { it.copy(syncingPairIds = it.syncingPairIds - pairId) }
            }
        }
    }

    fun loadDriveRootFolders() {
        viewModelScope.launch {
            val token = GoogleAuthManager.tryGetSilentAccessToken(app)
                ?: repository.getDriveAccessToken()
            if (token.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        driveBrowser = it.driveBrowser.copy(
                            isLoading = false,
                            error = "Hubungkan akun Google Drive terlebih dahulu di Beranda atau menu Akun."
                        )
                    )
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    driveBrowser = state.driveBrowser.copy(
                        isLoading = true,
                        error = null
                    )
                )
            }

            try {
                val folders = driveApi.listFolders(token, "root")
                _uiState.update {
                    it.copy(
                        driveBrowser = it.driveBrowser.copy(
                            isLoading = false,
                            folders = folders,
                            error = null
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        driveBrowser = it.driveBrowser.copy(
                            isLoading = false,
                            error = e.localizedMessage ?: "Gagal memuat folder Google Drive."
                        )
                    )
                }
            }
        }
    }

    fun createDriveFolder(name: String, onCreated: (DriveFolderItem) -> Unit = {}) {
        viewModelScope.launch {
            val clean = name.trim()
            if (clean.isEmpty()) return@launch
            val token = GoogleAuthManager.tryGetSilentAccessToken(app)
                ?: repository.getDriveAccessToken()
                ?: return@launch
            _uiState.update { it.copy(driveBrowser = it.driveBrowser.copy(isLoading = true, error = null)) }
            try {
                val created = driveApi.createFolder(token, "root", clean)
                val updatedList = driveApi.listFolders(token, "root")
                _uiState.update {
                    it.copy(
                        driveBrowser = it.driveBrowser.copy(isLoading = false, folders = updatedList),
                        snackbarMessage = "Folder Drive \"$clean\" dibuat"
                    )
                }
                onCreated(created)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        driveBrowser = it.driveBrowser.copy(
                            isLoading = false,
                            error = e.localizedMessage ?: "Gagal membuat folder Drive."
                        )
                    )
                }
            }
        }
    }

    fun loadGithubReposForAccount(accountId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(githubBrowser = GithubBrowserUiState(isLoading = true)) }
            val pat = repository.getGithubPat(accountId)
            if (pat.isNullOrBlank()) {
                _uiState.update {
                    it.copy(githubBrowser = GithubBrowserUiState(error = "PAT akun GitHub tidak ditemukan."))
                }
                return@launch
            }
            try {
                val repos = githubApi.listRepositories(pat)
                _uiState.update {
                    it.copy(githubBrowser = GithubBrowserUiState(isLoading = false, repos = repos))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        githubBrowser = GithubBrowserUiState(
                            isLoading = false,
                            error = e.localizedMessage ?: "Gagal memuat daftar repositori GitHub."
                        )
                    )
                }
            }
        }
    }

    fun loadGithubFolders(accountId: Long, owner: String, repo: String, branch: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    githubFolderBrowser = GithubFolderBrowserUiState(
                        isLoading = true,
                        owner = owner,
                        repo = repo,
                        branch = branch.ifBlank { "main" }
                    )
                )
            }
            val pat = repository.getGithubPat(accountId)
            if (pat.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        githubFolderBrowser = it.githubFolderBrowser.copy(
                            isLoading = false,
                            error = "PAT akun GitHub tidak ditemukan."
                        )
                    )
                }
                return@launch
            }
            try {
                val folders = githubApi.listFoldersInRepo(pat, owner, repo, branch.ifBlank { "main" })
                _uiState.update {
                    it.copy(
                        githubFolderBrowser = it.githubFolderBrowser.copy(
                            isLoading = false,
                            allFolders = folders,
                            error = null
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        githubFolderBrowser = it.githubFolderBrowser.copy(
                            isLoading = false,
                            error = e.localizedMessage ?: "Gagal memuat folder repositori GitHub."
                        )
                    )
                }
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            _uiState.update { it.copy(snackbarMessage = "Riwayat log dibersihkan") }
        }
    }

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
