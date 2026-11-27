package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.DriveAccountEntity
import com.example.data.db.GithubAccountEntity
import com.example.data.db.GithubRepoEntity
import com.example.data.db.MirrorJobEntity
import com.example.remote.Location
import com.example.remote.MirrorAutoSync
import com.example.remote.RemoteEntry
import com.example.remote.RemoteNames
import com.example.remote.Source
import com.example.ui.BrowserState
import com.example.ui.RemoteViewModel
import com.example.ui.components.CompactSearchBar
import com.example.ui.components.BrandIcons
import com.example.ui.theme.providerBrandColors
import kotlinx.coroutines.launch

@Composable
internal fun HomeScreen(
    modifier: Modifier,
    state: BrowserState,
    driveAccounts: List<DriveAccountEntity>,
    githubAccounts: List<GithubAccountEntity>,
    repos: List<GithubRepoEntity>,
    viewModel: RemoteViewModel,
    onConnectDrive: () -> Unit,
    onUploadFiles: () -> Unit,
    onUploadFolders: () -> Unit,
    onDownload: (RemoteEntry) -> Unit
) {
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val location = state.location
    if (location != null) {
        BackHandler { viewModel.back() }
        BrowserScreen(modifier, state, location, viewModel, onUploadFiles, onUploadFolders, onDownload)
        return
    }

    var showPat by rememberSaveable { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf<Disconnect?>(null) }
    var repoQuery by rememberSaveable { mutableStateOf("") }
    var folderQuery by rememberSaveable { mutableStateOf("") }
    val driveRootFolders by viewModel.driveRootFolders.collectAsState()
    val collapsed by viewModel.collapsedAccounts.collectAsState()
    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = pager.currentPage) {
            listOf("Google Drive", "GitHub").forEachIndexed { index, title ->
                val brand = providerBrandColors(isGithub = index == 1)
                Tab(selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(title) },
                    icon = { Icon(if (index == 0) BrandIcons.GoogleDrive else BrandIcons.GitHub,
                        contentDescription = title, tint = brand.accent) })
            }
        }
        HorizontalPager(pager, modifier = Modifier.weight(1f)) { page ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!state.isOnline) item { Text("Tidak tersambung ke internet.") }
                if (page == 0) {
                    item { GuideWebCard() }
                    item {
                        Button(onClick = onConnectDrive, enabled = !state.isConnectingDrive,
                            modifier = Modifier.testTag("connect_drive")) {
                            Text(if (state.isConnectingDrive) "Menyambungkan…" else "Sambungkan Google Drive")
                        }
                    }
                    state.driveAuthError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                    val anyFolders = driveAccounts.any { driveRootFolders[it.email]?.folders?.isNotEmpty() == true }
                    if (anyFolders) item {
                        CompactSearchBar(folderQuery, "Cari folder", { folderQuery = it }, testTag = "search_drive_folders")
                    }
                    for (acct in driveAccounts) {
                        val key = "drive:${acct.email}"
                        val open = key !in collapsed
                        item(key = "drive_account_${acct.email}") {
                            LaunchedEffect(acct.email, open) { if (open) viewModel.loadDriveRootFolders(acct.email) }
                            AccountRow(acct.email, acct.displayName.takeIf { it.isNotBlank() && it != acct.email }, open,
                                onToggle = { viewModel.toggleAccountCollapsed(key) }) {
                                val loading = driveRootFolders[acct.email]?.loading == true
                                IconButton(onClick = { viewModel.loadDriveRootFolders(acct.email, force = true) }, enabled = !loading,
                                    modifier = Modifier.testTag("refresh_drive_folders")) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Muat ulang folder ${acct.email}")
                                }
                                IconButton(onClick = { disconnect = Disconnect(acct.email) { viewModel.removeDriveAccount(acct.email) } }) {
                                    Icon(Icons.Default.LinkOff, contentDescription = "Putuskan ${acct.email}")
                                }
                            }
                        }
                        val state = driveRootFolders[acct.email]
                        if (open) when {
                            state == null || (state.loading && state.folders.isEmpty()) -> item(key = "drive_loading_${acct.email}") {
                                Text("Memuat folder…", style = MaterialTheme.typography.bodySmall)
                            }
                            state.error != null && state.folders.isEmpty() -> item(key = "drive_error_${acct.email}") {
                                Text(state.error, color = MaterialTheme.colorScheme.error)
                            }
                            state.folders.isEmpty() -> item(key = "drive_empty_${acct.email}") {
                                Text("Tidak ada folder di My Drive.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (open) {
                            val matches = state?.folders.orEmpty().filter { it.name.contains(folderQuery, ignoreCase = true) }
                            items(matches, key = { "drive_folder_${acct.email}_${it.ref}" }) { folder ->
                                SourceRow(folder.name, null, onOpen = { viewModel.openDriveFolder(acct.email, folder) }) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Buka ${folder.name}",
                                        modifier = Modifier.padding(12.dp))
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Button(onClick = { showPat = true }, modifier = Modifier.testTag("connect_github")) {
                            Text("Tambahkan akun GitHub")
                        }
                    }
                    if (repos.isNotEmpty()) item {
                        CompactSearchBar(repoQuery, "Cari repositori", { repoQuery = it }, testTag = "search_repos")
                    }
                    for (acct in githubAccounts) {
                        val key = "github:${acct.id}"
                        val open = key !in collapsed
                        item(key = "account_${acct.id}") {
                            AccountRow(acct.username, acct.label.takeIf { it != acct.username }, open,
                                onToggle = { viewModel.toggleAccountCollapsed(key) }) {
                                IconButton(onClick = { viewModel.refreshGithubRepos(acct) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Muat ulang repositori ${acct.username}")
                                }
                                IconButton(onClick = { disconnect = Disconnect(acct.username) { viewModel.deleteGithubAccount(acct) } }) {
                                    Icon(Icons.Default.LinkOff, contentDescription = "Putuskan ${acct.username}")
                                }
                            }
                        }
                        if (open) {
                            val matches = repos.filter { it.accountId == acct.id && it.fullName.contains(repoQuery, ignoreCase = true) }
                            items(matches, key = { "repo_${it.fullName}" }) { repo ->
                                SourceRow(repo.fullName, if (repo.archived) "Diarsipkan · ${repo.defaultBranch}" else repo.defaultBranch,
                                    onOpen = { viewModel.openSource(Source.Github(repo)) }) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Buka ${repo.fullName}",
                                        modifier = Modifier.padding(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showPat) PatDialog(state, onDismiss = { showPat = false }) { pat ->
        viewModel.addGithubAccount(pat) { showPat = false }
    }
    disconnect?.let { target ->
        AlertDialog(onDismissRequest = { disconnect = null }, title = { Text("Putuskan akun?") },
            text = { Text("${target.label} dihapus dari Lotura. File remote dan hasil unduhan tidak berubah.") },
            confirmButton = { TextButton(onClick = { disconnect = null; target.action() }) { Text("Putuskan") } },
            dismissButton = { TextButton(onClick = { disconnect = null }) { Text("Batal") } })
    }
}

private data class Disconnect(val label: String, val action: () -> Unit)

/** Kartu akun yang bisa dilipat. Ketuk kartu untuk menampilkan atau menyembunyikan daftar folder/repo di bawahnya. */
@Composable
private fun AccountRow(title: String, subtitle: String?, open: Boolean, onToggle: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    SourceRow(title, subtitle, onOpen = onToggle) {
        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (open) "Lipat $title" else "Tampilkan $title",
            modifier = Modifier.padding(horizontal = 4.dp))
        actions()
    }
}

@Composable
private fun SourceRow(title: String, subtitle: String?, onOpen: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit) {
    OutlinedCard {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).heightIn(min = 48.dp).padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.Center) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            actions()
        }
    }
}

@Composable
private fun PatDialog(state: BrowserState, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var pat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!state.isAddingGithub) onDismiss() },
        title = { Text("Tambahkan akun GitHub") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gunakan PAT dengan akses Contents ke repositori pilihan. Akses tulis diperlukan untuk mengubahnya.")
                OutlinedTextField(pat, { pat = it }, label = { Text("Personal Access Token") },
                    enabled = !state.isAddingGithub, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, isError = state.githubAccountError != null, modifier = Modifier.testTag("github_pat_input"))
                state.githubAccountError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(pat) }, enabled = pat.isNotBlank() && !state.isAddingGithub) {
                Text(if (state.isAddingGithub) "Memeriksa…" else "Sambungkan")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isAddingGithub) { Text("Batal") } }
    )
}

@Composable
private fun BrowserScreen(
    modifier: Modifier, state: BrowserState, location: Location, viewModel: RemoteViewModel,
    onUploadFiles: () -> Unit,
    onUploadFolders: () -> Unit,
    onDownload: (RemoteEntry) -> Unit
) {
    var uploadMenu by remember(location) { mutableStateOf(false) }
    var search by remember(location) { mutableStateOf("") }
    var create by remember(location) { mutableStateOf(false) }
    var folderName by remember(location) { mutableStateOf("") }
    var delete by remember(location) { mutableStateOf<RemoteEntry?>(null) }
    var autoSyncOpen by remember(location) { mutableStateOf(false) }
    val mirrorJobs by viewModel.mirrorJobs.collectAsState()
    val canEdit = location.writable && state.ready && !state.busy
    val listed = state.entries.filter { it.name.contains(search, ignoreCase = true) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::back, enabled = !state.busy) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
            }
            Text(location.title, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = viewModel::goHome, enabled = !state.busy, modifier = Modifier.testTag("home_button")) {
                Icon(Icons.Default.Home, "Kembali ke daftar akun")
            }
            if (location.writable) {
                IconButton(onClick = { folderName = ""; create = true }, enabled = canEdit, modifier = Modifier.testTag("new_folder")) {
                    Icon(Icons.Default.CreateNewFolder, "Buat folder baru")
                }
                Box {
                    IconButton(onClick = { uploadMenu = true }, enabled = canEdit, modifier = Modifier.testTag("upload_menu")) {
                        Icon(Icons.Default.Upload, "Unggah")
                    }
                    DropdownMenu(expanded = uploadMenu, onDismissRequest = { uploadMenu = false }) {
                        DropdownMenuItem(text = { Text("Unggah file") }, onClick = {
                            uploadMenu = false
                            onUploadFiles()
                        }, modifier = Modifier.testTag("upload_files"))
                        DropdownMenuItem(text = { Text("Unggah folder") }, onClick = {
                            uploadMenu = false
                            onUploadFolders()
                        }, modifier = Modifier.testTag("upload_folders"))
                    }
                }
            }
        }
        val path = (listOf(location.source.title) + location.folders.map { it.name }).joinToString(" / ")
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(path, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp))
            if (location.source is Source.Drive) {
                IconButton(onClick = { autoSyncOpen = true }, modifier = Modifier.testTag("auto_sync")) {
                    Icon(Icons.Default.Schedule, contentDescription = "Auto sync")
                }
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("remote_progress"))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.entries.isNotEmpty()) item { CompactSearchBar(search, "Cari di folder ini", { search = it }) }
            if (state.error != null) item { Text(state.error, color = MaterialTheme.colorScheme.error) }
            if (listed.isEmpty() && state.ready && !state.busy) item {
                Text(if (search.isBlank()) "Folder ini kosong." else "Tidak ada hasil.")
            }
            items(listed, key = { it.ref }) { entry ->
                OutlinedCard {
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).heightIn(min = 48.dp)
                            .then(if (entry.isFolder) Modifier.clickable(enabled = !state.busy) { viewModel.openFolder(entry) } else Modifier)
                            .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val (icon, label) = when (entry.kind) {
                                RemoteEntry.Kind.FOLDER -> Icons.Default.Folder to "Folder"
                                RemoteEntry.Kind.FILE -> Icons.Default.Description to "File"
                                RemoteEntry.Kind.LINK -> Icons.Default.Link to "Pintasan"
                                RemoteEntry.Kind.UNSUPPORTED -> Icons.Default.Description to "File tanpa format unduhan"
                            }
                            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
                            Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (entry.downloadable) IconButton(onClick = { onDownload(entry) }, enabled = !state.busy) {
                            Icon(Icons.Default.Download, "Unduh ${entry.name}")
                        }
                        if (location.writable) IconButton(onClick = { delete = entry }, enabled = canEdit) {
                            Icon(Icons.Default.DeleteOutline, "Hapus ${entry.name}", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (create) {
        val valid = RemoteNames.validNewName(folderName.trim())
        AlertDialog(onDismissRequest = { create = false }, title = { Text("Buat folder baru") },
            text = {
                OutlinedTextField(folderName, { folderName = it }, label = { Text("Nama folder") }, singleLine = true,
                    isError = folderName.isNotEmpty() && !valid, modifier = Modifier.testTag("new_folder_name"))
            },
            confirmButton = { TextButton(enabled = valid, onClick = { create = false; viewModel.createFolder(folderName.trim()) }) { Text("Buat") } },
            dismissButton = { TextButton(onClick = { create = false }) { Text("Batal") } })
    }
    if (autoSyncOpen && location.source is Source.Drive) {
        val email = (location.source as Source.Drive).email
        val jobs = mirrorJobs.filter { it.email == email }
        AlertDialog(onDismissRequest = { autoSyncOpen = false }, title = { Text("Auto sync") },
            text = {
                if (jobs.isEmpty()) Text("Belum ada folder dengan auto sync untuk akun ini.")
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(jobs, key = { it.jobKey }) { job -> AutoSyncRow(job, onStop = { viewModel.stopMirrorJob(job) }) }
                }
            },
            confirmButton = { TextButton(onClick = { autoSyncOpen = false }) { Text("Tutup") } })
    }
    delete?.let { entry ->
        val place = if (location.source is Source.Drive) "dipindahkan ke Sampah Google Drive" else "dihapus dari cabang GitHub ini lewat commit"
        val contents = if (entry.isFolder) " Seluruh isi folder ikut terhapus, termasuk item tersembunyi." else ""
        AlertDialog(onDismissRequest = { delete = null }, title = { Text("Hapus ${if (entry.isFolder) "folder" else "file"}?") },
            text = { Text("${entry.name} akan $place.$contents") },
            confirmButton = { TextButton(onClick = { delete = null; viewModel.delete(entry) }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Batal") } })
    }
}

@Composable
private fun AutoSyncRow(job: MirrorJobEntity, onStop: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(job.folderName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Tujuan: ${job.parentTitle} • ${MirrorAutoSync.label(job.intervalHours)}",
            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(job.lastResult,
            style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (job.enabled) {
            TextButton(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp).testTag("stop_auto")) {
                Text("Hentikan")
            }
        }
    }
}
