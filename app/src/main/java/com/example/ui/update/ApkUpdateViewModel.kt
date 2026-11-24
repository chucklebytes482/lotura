package com.example.ui.update

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.update.ApkState
import com.example.update.ApkUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/** Mengunduh APK rilis terbaru lalu membuka installer Android. */
class ApkUpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val client = OkHttpClient()
    private val target = File(application.cacheDir, "updates/lotura.apk")
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<ApkState>(ApkState.Idle)
    val state: StateFlow<ApkState> = _state.asStateFlow()

    fun download() {
        if (downloadJob?.isActive == true) return
        _state.value = ApkState.Downloading
        downloadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ApkUpdate.downloadLatest(client, target) }
            }
            result
                .onSuccess { file -> requestInstall(file) }
                .onFailure { err ->
                    _state.value = ApkState.Failed(err.message ?: "Unduhan APK gagal.")
                }
        }
    }

    /** Dipanggil setelah pengguna kembali dari pengaturan izin. */
    fun retryInstall() {
        val current = _state.value as? ApkState.NeedsInstallPermission ?: return
        requestInstall(current.file)
    }

    fun dismissError() {
        if (_state.value is ApkState.Failed) _state.value = ApkState.Idle
    }

    fun dismissPermissionPrompt() {
        if (_state.value is ApkState.NeedsInstallPermission) _state.value = ApkState.Idle
    }

    private fun requestInstall(file: File) {
        val app = getApplication<Application>()
        if (!app.packageManager.canRequestPackageInstalls()) {
            _state.value = ApkState.NeedsInstallPermission(file)
            return
        }
        _state.value = ApkState.Idle
        launchInstaller(file)
    }

    private fun launchInstaller(file: File) {
        val app = getApplication<Application>()
        runCatching {
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        }.onFailure { err ->
            _state.value = ApkState.Failed("Gagal membuka installer: ${err.message ?: "tidak diketahui"}")
        }
    }
}
