package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SyncBebasApp
import com.example.data.db.GithubAccountEntity
import com.example.data.db.SyncLogEntity
import com.example.data.db.SyncPairEntity
import com.example.data.secure.AppPrefs
import com.example.drive.DriveFolderItem
import com.example.drive.GoogleAuthManager
import com.example.github.GithubRepoSummary
import com.example.service.SyncForegroundService
import com.example.service.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DriveBrowserUiState(
    val isLoading: Boolean = false,
    val currentParentId: String = "root",
    val breadcrumbs: List<Pair<String, String>> = listOf("root" to "Drive Saya"),
    val folders: List<DriveFolderItem> = emptyList(),
    val error: String? = null
)

data class GithubBrowserUiState(
    val isLoading: Boolean = false,
    val repos: List<GithubRepoSummary> = emptyList(),
    val error: String? = null
)

data class SyncUiState(
    val syncingPairIds: Set<Long> = emptySet(),
    val isGlobalSyncing: Boolean = false,
    val isConnectingGoogle: Boolean = false,
    val isAddingGithubAccount: Boolean = false,
    val googleAuthError: String? = null,
    val githubAccountError: String? = null,
    val snackbarMessage: String? = null,
    val isOnline: Boolean = true,
    val driveBrowser: DriveBrowserUiState = DriveBrowserUiState(),
    val githubBrowser: GithubBrowserUiState = GithubBrowserUiState()
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

    fun observePair(pairId: Long) = repository.observePair(pairId)
    fun observeFileStates(pairId: Long) = repository.observeFileStates(pairId)
    fun observeLogsForPair(pairId: Long) = repository.observeLogsForPair(pairId)

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

    fun setAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoSyncEnabled(enabled)
            if (enabled && appPrefs.value.realtimeWatcherEnabled && pairs.value.any { it.autoSyncDrive }) {
                runCatching { SyncForegroundService.start(app) }
            }
        }
    }

    fun setSyncIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            repository.setSyncIntervalMinutes(minutes)
            val allPairs = repository.getAllPairs()
            allPairs.forEach { pair ->
                SyncScheduler.scheduleForPair(app, pair, overrideIntervalMinutes = minutes)
            }
            val label = when (minutes) {
                5 -> "5 menit"
                240 -> "4 jam"
                1440 -> "24 jam"
                else -> "15 menit"
            }
            _uiState.update { it.copy(snackbarMessage = "Jadwal sinkronisasi diatur ke $label") }
        }
    }

    fun setRealtimeWatcherEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setRealtimeWatcherEnabled(enabled)
            if (enabled) {
                runCatching { SyncForegroundService.start(app) }
                _uiState.update { it.copy(snackbarMessage = "Pemantau FileObserver real-time diaktifkan") }
            } else {
                runCatching { SyncForegroundService.stop(app) }
                _uiState.update { it.copy(snackbarMessage = "Pemantau real-time dihentikan") }
            }
        }
    }

    fun addGithubAccount(label: String, pat: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val trimmedPat = pat.trim()
            if (trimmedPat.isEmpty()) {
                _uiState.update { it.copy(githubAccountError = "Fine-grained PAT wajib diisi.") }
                return@launch
            }
            _uiState.update { it.copy(isAddingGithubAccount = true, githubAccountError = null) }
            try {
                val user = githubApi.getAuthenticatedUser(trimmedPat)
                repository.addGithubAccount(
                    label = label.ifBlank { user.name },
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
            val savedId = repository.savePair(pair)
            val savedPair = pair.copy(id = savedId)
            SyncScheduler.scheduleForPair(app, savedPair)
            if (savedPair.autoSyncDrive && appPrefs.value.realtimeWatcherEnabled) {
                runCatching { SyncForegroundService.start(app) }
            }
            repository.addLog(
                pairId = savedId,
                pairName = savedPair.name,
                target = "SYSTEM",
                action = "CONFIG",
                status = "OK",
                message = "Pasangan folder disimpan (${savedPair.localPath})"
            )
            _uiState.update { it.copy(snackbarMessage = "Pasangan \"${savedPair.name}\" disimpan") }
            onSaved(savedId)
        }
    }

    fun deletePair(pair: SyncPairEntity, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            SyncScheduler.cancelForPair(app, pair.id)
            repository.deletePair(pair.id)
            _uiState.update { it.copy(snackbarMessage = "Pasangan \"${pair.name}\" dihapus") }
            onDeleted()
        }
    }

    fun syncPairNow(pairId: Long, includeGithub: Boolean = true) {
        viewModelScope.launch {
            refreshNetworkStatus()
            _uiState.update { it.copy(syncingPairIds = it.syncingPairIds + pairId) }
            try {
                val pair = repository.getPairById(pairId) ?: return@launch
                val results = mutableListOf<String>()

                if (pair.driveFolderId.isNotBlank()) {
                    val token = GoogleAuthManager.tryGetSilentAccessToken(app)
                        ?: repository.getDriveAccessToken()
                    if (!token.isNullOrBlank()) {
                        val driveRes = syncExecutor.syncPairWithDrive(pairId, token)
                        results.add(driveRes.summary)
                    } else {
                        results.add("Google Drive belum terhubung")
                    }
                }

                if (includeGithub && pair.githubEnabled && pair.githubAccountId != null) {
                    val ghRes = syncExecutor.syncPairWithGithub(pairId)
                    results.add(ghRes.summary)
                }

                _uiState.update {
                    it.copy(snackbarMessage = results.joinToString(" • ").ifBlank { "Sinkronisasi selesai" })
                }
            } finally {
                _uiState.update { it.copy(syncingPairIds = it.syncingPairIds - pairId) }
            }
        }
    }

    fun syncAllPairsNow() {
        viewModelScope.launch {
            refreshNetworkStatus()
            val all = repository.getAllPairs()
            if (all.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = "Belum ada pasangan folder untuk disinkronkan") }
                return@launch
            }
            _uiState.update { it.copy(isGlobalSyncing = true) }
            try {
                val token = GoogleAuthManager.tryGetSilentAccessToken(app)
                    ?: repository.getDriveAccessToken()
                var okCount = 0
                for (pair in all) {
                    _uiState.update { it.copy(syncingPairIds = it.syncingPairIds + pair.id) }
                    try {
                        if (!token.isNullOrBlank() && pair.driveFolderId.isNotBlank()) {
                            val r = syncExecutor.syncPairWithDrive(pair.id, token)
                            if (r.success) okCount++
                        }
                        if (pair.githubEnabled && pair.githubAccountId != null) {
                            syncExecutor.syncPairWithGithub(pair.id)
                        }
                    } finally {
                        _uiState.update { it.copy(syncingPairIds = it.syncingPairIds - pair.id) }
                    }
                }
                _uiState.update { it.copy(snackbarMessage = "Sinkronisasi selesai ($okCount/${all.size} pasangan Drive)") }
            } finally {
                _uiState.update { it.copy(isGlobalSyncing = false) }
            }
        }
    }

    fun openDriveFolder(parentId: String = "root", folderName: String? = null) {
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
                val crumbs = when {
                    parentId == "root" -> listOf("root" to "Drive Saya")
                    folderName != null -> {
                        val existingIdx = state.driveBrowser.breadcrumbs.indexOfFirst { it.first == parentId }
                        if (existingIdx >= 0) {
                            state.driveBrowser.breadcrumbs.take(existingIdx + 1)
                        } else {
                            state.driveBrowser.breadcrumbs + (parentId to folderName)
                        }
                    }
                    else -> state.driveBrowser.breadcrumbs
                }
                state.copy(
                    driveBrowser = state.driveBrowser.copy(
                        isLoading = true,
                        currentParentId = parentId,
                        breadcrumbs = crumbs,
                        error = null
                    )
                )
            }

            try {
                val folders = driveApi.listFolders(token, parentId)
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
            val parentId = _uiState.value.driveBrowser.currentParentId
            _uiState.update { it.copy(driveBrowser = it.driveBrowser.copy(isLoading = true, error = null)) }
            try {
                val created = driveApi.createFolder(token, parentId, clean)
                val updatedList = driveApi.listFolders(token, parentId)
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
