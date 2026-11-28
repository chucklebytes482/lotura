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
import com.example.remote.GithubMoves
import com.example.remote.RemoteNames
import com.example.remote.deviceRoot
import com.example.remote.Location
import com.example.remote.MirrorAutoSync
import com.example.remote.PickedFolder
import com.example.remote.RemoteEntry
import com.example.remote.RemoteFiles
import com.example.remote.Source
import com.example.remote.UploadMode
import java.io.File
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

/** Penjelajah file perangkat. Dimulai dari /storage/emulated/0. */
/** Kartu yang dibuka. [driveEmail] hanya terisi untuk kartu Drive; tujuan auto sync. */
internal data class DeviceTarget(val title: String, val driveEmail: String?)

internal data class DeviceBrowse(
    val dir: File,
    val target: DeviceTarget,
    val entries: List<File> = emptyList(),
    val ready: Boolean = false,
    val error: String? = null
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
    val isOnline: Boolean = true,
    val device: DeviceBrowse? = null,
    /** Kunci [RemoteEntry.ref] item yang dipilih. Hanya dipakai di GitHub. */
    val selection: Set<String> = emptySet(),
    val editor: MarkdownEditor? = null,
    val movePicker: MovePicker? = null
)

/** Editor .md GitHub. [original] null selama memuat. [item].revision adalah sha blob dasar untuk simpan. */
internal data class MarkdownEditor(
    val location: Location,
    val item: RemoteEntry,
    val original: String? = null,
    val draft: String = "",
    val editing: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
) {
    val loaded get() = original != null
    val dirty get() = loaded && draft != original
}

/** Pemilih folder tujuan pindah di dalam repo yang sama. [path].ref kosong berarti root repo. */
internal data class MovePicker(
    val path: Location,
    val folders: List<RemoteEntry> = emptyList(),
    val ready: Boolean = false,
    val error: String? = null
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

    /** Akun yang daftarnya sedang dilipat. Kunci: "drive:<email>" atau "github:<id>". Disimpan di ViewModel agar bertahan saat masuk folder. */
    private val _collapsedAccounts = MutableStateFlow<Set<String>>(emptySet())
    val collapsedAccounts: StateFlow<Set<String>> = _collapsedAccounts.asStateFlow()

    fun toggleAccountCollapsed(key: String) = _collapsedAccounts.update { if (key in it) it - key else it + key }

    fun openDevice(target: DeviceTarget) = loadDevice(deviceRoot(), target)

    fun closeDevice() = _uiState.update { it.copy(device = null) }

    private fun loadDevice(dir: File, target: DeviceTarget) {
        _uiState.update { it.copy(device = DeviceBrowse(dir, target)) }
        viewModelScope.launch {
            val loaded = try {
                DeviceBrowse(dir, target, files.listDevice(dir), ready = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DeviceBrowse(dir, target, error = e.message ?: "Folder tidak bisa dibaca.")
            }
            _uiState.update { state -> if (state.device?.dir == dir) state.copy(device = loaded) else state }
        }
    }

    /** Auto sync dari satu folder di /autosync ke folder autosync di root Google Drive akun [email]. */
    fun scheduleDeviceFolder(folder: File, email: String, hours: Int) {
        viewModelScope.launch {
            val message = try {
                files.enableMirrorAuto(Location(Source.Drive(email)), listOf(PickedFolder(Uri.fromFile(folder), folder.name)), hours)
                "Auto sync ${MirrorAutoSync.label(hours).lowercase()} aktif untuk ${folder.name} ke Google Drive/autosync."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Auto sync tidak bisa diaktifkan."
            }
            showMessage(message)
        }
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
        _uiState.update { it.copy(location = location, entries = emptyList(), ready = false, error = null, selection = emptySet(), editor = null, movePicker = null) }
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

    /** Mengaktifkan atau menonaktifkan jadwal satu folder. Saat diaktifkan, putaran pertama langsung jalan. */
    fun setMirrorJobEnabled(job: MirrorJobEntity, enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setMirrorJobEnabled(job.jobKey, enabled)
                if (enabled) MirrorAutoSync.schedule(app, job.copy(enabled = true))
                else MirrorAutoSync.cancel(app, job.jobKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(e.message ?: "Jadwal auto sync tidak bisa diubah.")
            }
        }
    }

    fun createFolder(name: String) = action(refresh = true) { location ->
        files.createFolder(location, name)
        "Folder $name dibuat di remote."
    }

    /** Hapus satu item (Drive atau GitHub). */
    fun delete(item: RemoteEntry) = deleteItems(listOf(item))

    fun deleteSelected() = deleteItems(selectedItems())

    private fun deleteItems(items: List<RemoteEntry>) = runOn(items, refresh = true) { location ->
        files.delete(location, items)
        clearSelection()
        if (location.source is Source.Drive) "Dipindahkan ke Sampah Drive." else "Dihapus dari cabang GitHub."
    }

    fun download(item: RemoteEntry) = action(refresh = false, item = item) { location ->
        val result = files.download(location, item)
        "Disimpan: ${result.file.absolutePath}" +
            if (result.skipped > 0) " • ${result.skipped} item tidak mendukung unduhan." else ""
    }

    fun downloadSelected() {
        val items = selectedItems()
        runOn(items, refresh = false) { location ->
            val batch = files.downloadMany(location, items)
            clearSelection()
            val dir = batch.files.firstOrNull()?.parentFile?.absolutePath.orEmpty()
            if (batch.files.isEmpty()) "Tidak ada item yang bisa diunduh."
            else "Disimpan ${batch.files.size} item ke $dir" +
                if (batch.skipped > 0) " • ${batch.skipped} item tidak mendukung unduhan." else "."
        }
    }

    fun renameSelected(newName: String) {
        val item = selectedItems().singleOrNull() ?: return
        val name = newName.trim()
        runOn(listOf(item), refresh = true) { location ->
            files.rename(location, item, name)
            clearSelection()
            "Diganti menjadi $name."
        }
    }

    // ---------- Pilihan banyak (hanya GitHub) ----------

    fun toggleSelection(item: RemoteEntry) {
        val state = _uiState.value
        if (state.location?.source !is Source.Github || !state.ready || state.busy || item !in state.entries) return
        _uiState.update { s -> s.copy(selection = if (item.ref in s.selection) s.selection - item.ref else s.selection + item.ref) }
    }

    fun clearSelection() = _uiState.update { it.copy(selection = emptySet()) }

    private fun selectedItems(): List<RemoteEntry> = _uiState.value.let { s -> s.entries.filter { it.ref in s.selection } }

    // ---------- Pindah ke folder lain di repo yang sama ----------

    fun openMovePicker() {
        val state = _uiState.value
        val location = state.location ?: return
        if (location.source !is Source.Github || !location.writable || !state.ready || state.busy || state.selection.isEmpty()) return
        val root = Location(location.source)
        _uiState.update { it.copy(movePicker = MovePicker(root)) }
        loadPicker(root)
    }

    fun movePickerEnter(folder: RemoteEntry) {
        val picker = _uiState.value.movePicker?.takeIf { it.ready && folder in it.folders } ?: return
        val next = picker.path.open(folder)
        _uiState.update { it.copy(movePicker = MovePicker(next)) }
        loadPicker(next)
    }

    fun movePickerUp() {
        val picker = _uiState.value.movePicker?.takeIf { it.ready && it.path.folders.isNotEmpty() } ?: return
        val next = picker.path.copy(folders = picker.path.folders.dropLast(1))
        _uiState.update { it.copy(movePicker = MovePicker(next)) }
        loadPicker(next)
    }

    fun movePickerConfirm() {
        val state = _uiState.value
        val location = state.location ?: return
        val picker = state.movePicker?.takeIf { it.ready } ?: return
        val items = selectedItems()
        GithubMoves.invalidTarget(items.map { it.ref }, location.ref, picker.path.ref)?.let { message ->
            _uiState.update { it.copy(movePicker = picker.copy(error = message)) }
            return
        }
        val started = runOn(items, refresh = true) { loc ->
            files.move(loc, items, picker.path.ref)
            clearSelection()
            "Dipindah ${items.size} item ke ${picker.path.title}."
        }
        if (started) _uiState.update { it.copy(movePicker = null) }
    }

    fun closeMovePicker() = _uiState.update { it.copy(movePicker = null) }

    private fun loadPicker(path: Location) {
        viewModelScope.launch {
            try {
                val folders = files.listFolderNames(path)
                _uiState.update { s ->
                    s.movePicker?.takeIf { it.path == path }?.let { s.copy(movePicker = it.copy(folders = folders, ready = true, error = null)) } ?: s
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { s ->
                    s.movePicker?.takeIf { it.path == path }?.let {
                        s.copy(movePicker = it.copy(ready = true, error = e.message ?: "Folder tidak bisa dimuat."))
                    } ?: s
                }
            }
        }
    }

    // ---------- Editor Markdown (hanya GitHub) ----------

    fun openMarkdown(item: RemoteEntry) {
        val state = _uiState.value
        val location = state.location ?: return
        if (location.source !is Source.Github || !state.ready || state.busy || pickerTarget != null || state.editor != null) return
        if (item.kind != RemoteEntry.Kind.FILE || !RemoteNames.markdown(item.name) || item !in state.entries) return
        _uiState.update { it.copy(editor = MarkdownEditor(location, item)) }
        viewModelScope.launch {
            try {
                val text = files.readMarkdown(location, item)
                _uiState.update { s ->
                    s.editor?.takeIf { it.item.ref == item.ref }?.let { s.copy(editor = it.copy(original = text, draft = text)) } ?: s
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { s ->
                    s.editor?.takeIf { it.item.ref == item.ref }?.let {
                        s.copy(editor = it.copy(error = e.message ?: "Berkas tidak bisa dibaca."))
                    } ?: s
                }
            }
        }
    }

    fun startMarkdownEdit() = _uiState.update { s ->
        s.editor?.takeIf { it.loaded && !it.saving }?.let { s.copy(editor = it.copy(editing = true, error = null)) } ?: s
    }

    fun updateMarkdownDraft(text: String) = _uiState.update { s ->
        s.editor?.takeIf { it.editing && !it.saving }?.let { s.copy(editor = it.copy(draft = text)) } ?: s
    }

    /** Tombol "Selesai": bila ada perubahan, satu commit dibuat. Tanpa perubahan hanya kembali ke mode baca. */
    fun finishMarkdownEdit() {
        val editor = _uiState.value.editor ?: return
        if (!editor.editing || editor.saving || !editor.loaded || _uiState.value.busy) return
        if (!editor.dirty) {
            _uiState.update { it.copy(editor = editor.copy(editing = false, error = null)) }
            return
        }
        val text = editor.draft
        _uiState.update { it.copy(editor = editor.copy(saving = true, error = null)) }
        viewModelScope.launch {
            try {
                val sha = files.saveMarkdown(editor.location, editor.item, editor.item.revision, text)
                val saved = editor.item.copy(revision = sha)
                _uiState.update { s ->
                    s.copy(
                        entries = s.entries.map { if (it.ref == saved.ref) saved else it },
                        editor = s.editor?.takeIf { it.item.ref == saved.ref }
                            ?.copy(item = saved, original = text, editing = false, saving = false) ?: s.editor,
                        snackbarMessage = "Tersimpan ke GitHub: ${saved.name}"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { s ->
                    s.copy(editor = s.editor?.takeIf { it.item.ref == editor.item.ref }
                        ?.copy(saving = false, error = e.message ?: "Gagal menyimpan ke GitHub.") ?: s.editor)
                }
            }
        }
    }

    /** Tombol "Batal" atau kembali saat mode edit: draft dikembalikan ke isi terakhir yang tersimpan. */
    fun discardMarkdownEdit() = _uiState.update { s ->
        s.editor?.takeIf { !it.saving }?.let { s.copy(editor = it.copy(draft = it.original.orEmpty(), editing = false, error = null)) } ?: s
    }

    fun closeMarkdown() {
        if (_uiState.value.editor?.saving == true) return
        _uiState.update { it.copy(editor = null) }
    }

    private fun runOn(items: List<RemoteEntry>, refresh: Boolean, block: suspend (Location) -> String?): Boolean {
        val state = _uiState.value
        val location = state.location ?: return false
        if (!state.ready || state.busy || pickerTarget != null || items.isEmpty() || items.any { it !in state.entries }) return false
        runBusy(location, refresh) { block(location) }
        return true
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
            closeDevice()
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
