package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.drive.GoogleAuthManager
import com.example.drive.findActivity
import com.example.ui.SyncViewModel
import com.example.ui.components.StoragePermissionHelper
import com.example.ui.screens.FileBrowserScreen
import com.example.ui.screens.FolderDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.SyncBebasTheme

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail"
    const val FILES = "files"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SyncViewModel = viewModel()
            SyncBebasTheme {
                SyncBebasRootApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncBebasRootApp(viewModel: SyncViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val driveAccounts by viewModel.driveAccounts.collectAsStateWithLifecycle()
    val githubAccounts by viewModel.githubAccounts.collectAsStateWithLifecycle()
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val browse by viewModel.browse.collectAsStateWithLifecycle()
    val detailStats by viewModel.detailStats.collectAsStateWithLifecycle()

    var hasStoragePermission by remember { mutableStateOf(StoragePermissionHelper.hasAllFilesAccess()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasStoragePermission = StoragePermissionHelper.hasAllFilesAccess()
                viewModel.refreshNetworkStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val driveAuthResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        GoogleAuthManager.extractAccessTokenFromResult(context, activityResult.data)
            .onSuccess { token -> viewModel.onDriveAccessTokenReceived(token) }
            .onFailure { err ->
                viewModel.onDriveAuthError(
                    err.localizedMessage ?: "Otorisasi Google Drive dibatalkan atau gagal."
                )
            }
    }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val email = GoogleAuthManager.emailFromPickerResult(activityResult.data)
        val activity = context.findActivity()
        when {
            email == null -> viewModel.onDriveConnectingCancelled()
            activity == null -> viewModel.onDriveAuthError("Activity tidak tersedia untuk otorisasi Google.")
            else -> GoogleAuthManager.authorizeDriveInteractive(
                activity = activity,
                resolutionLauncher = driveAuthResolutionLauncher,
                email = email,
                onAccessToken = { token -> viewModel.onDriveAccessTokenReceived(token) },
                onError = { err -> viewModel.onDriveAuthError(err) }
            )
        }
    }

    val startDriveAuth: () -> Unit = {
        if (context.findActivity() == null) {
            viewModel.onDriveAuthError("Activity tidak tersedia untuk otorisasi Google.")
        } else {
            viewModel.onDriveConnectingStarted()
            accountPickerLauncher.launch(GoogleAuthManager.buildAccountPickerIntent())
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message = msg)
        viewModel.consumeSnackbar()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME
    val isDetail = currentRoute == Routes.DETAIL
    val isFiles = currentRoute == Routes.FILES

    fun goBackFromDetail() {
        viewModel.closeDetail()
        navController.popBackStack()
    }
    fun goBackFromFiles() {
        // Di dalam editor atau subfolder: naik satu level dulu. Di akar: tutup penjelajah.
        if (!viewModel.handleBrowseBack()) {
            viewModel.closeBrowse()
            navController.popBackStack()
        }
    }
    BackHandler(enabled = isDetail) { goBackFromDetail() }
    BackHandler(enabled = isFiles) { goBackFromFiles() }

    val topBarTitle = when {
        isDetail -> detail?.title ?: "Lotura"
        isFiles -> browse?.title ?: "Lotura"
        else -> "Lotura"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = topBarTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (isDetail || isFiles) {
                        IconButton(
                            onClick = { if (isDetail) goBackFromDetail() else goBackFromFiles() },
                            modifier = Modifier.testTag("navigate_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    driveAccounts = driveAccounts,
                    githubAccounts = githubAccounts,
                    repos = repos,
                    trees = uiState.trees,
                    loading = uiState.loading,
                    summaries = summaries,
                    busySources = uiState.busySources,
                    isConnectingDrive = uiState.isConnectingDrive,
                    driveAuthError = uiState.driveAuthError,
                    isAddingGithub = uiState.isAddingGithub,
                    githubAccountError = uiState.githubAccountError,
                    hasStoragePermission = hasStoragePermission,
                    isOnline = uiState.isOnline,
                    onRequestStoragePermission = {
                        runCatching {
                            context.startActivity(StoragePermissionHelper.buildAllFilesSettingsIntent(context))
                        }.onFailure {
                            viewModel.showMessage("Buka Pengaturan Android -> Akses Semua File untuk Lotura.")
                        }
                    },
                    onOpenWebGuide = {
                        val guideUrl = context.getString(R.string.web_guide_url)
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guideUrl)))
                        }.onFailure {
                            viewModel.showMessage("Gagal membuka browser.")
                        }
                    },
                    onConnectDrive = startDriveAuth,
                    onRemoveDrive = { email -> viewModel.removeDriveAccount(email) },
                    onReloadDrive = { email -> viewModel.reloadDrive(email) },
                    onAddGithubAccount = { pat, onOk -> viewModel.addGithubAccount(pat, onOk) },
                    onRefreshRepos = { account -> viewModel.refreshGithubRepos(account) },
                    onDeleteGithubAccount = { account -> viewModel.deleteGithubAccount(account) },
                    onOpenDriveFolder = { item -> viewModel.openDriveFolder(item); navController.navigate(Routes.DETAIL) },
                    onOpenRepo = { repo -> viewModel.openRepo(repo); navController.navigate(Routes.DETAIL) },
                    onSyncFolder = { item, upload -> viewModel.syncFolder(item, upload) },
                    onSyncRepo = { repo, upload -> viewModel.syncRepo(repo, upload) }
                )
            }

            composable(Routes.DETAIL) {
                val current = detail
                if (current == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    FolderDetailScreen(
                        detail = current,
                        summary = summaries[current.summaryKey],
                        busy = uiState.busySources.contains(current.sourceKey),
                        onDownload = { viewModel.syncDetail(upload = false) },
                        onUpload = { viewModel.syncDetail(upload = true) },
                        stats = detailStats,
                        onOpenFiles = current.repo?.let { repo ->
                            val open: () -> Unit = {
                                viewModel.openFiles(repo)
                                navController.navigate(Routes.FILES)
                            }
                            open
                        },
                        onRetry = {
                            current.repo?.let { viewModel.openRepo(it) }
                            current.driveItem?.let { viewModel.openDriveFolder(it) }
                        }
                    )
                }
            }

            composable(Routes.FILES) {
                val current = browse
                if (current == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    FileBrowserScreen(
                        browse = current,
                        onEntryClick = { entry ->
                            if (entry.isDir) viewModel.browseTo(entry) else viewModel.openEditor(entry)
                        },
                        onSave = { file, text -> viewModel.saveEditedFile(file, text) },
                        onCloseEditor = { viewModel.closeEditor() },
                        onCreate = { name, isFolder -> viewModel.createEntry(name, isFolder) },
                        onRename = { entry, newName -> viewModel.renameEntry(entry, newName) },
                        onDelete = { entry -> viewModel.deleteEntry(entry) },
                        onOpenHistory = { viewModel.openHistory() },
                        onPreviewVersion = { commit -> viewModel.previewVersion(commit) },
                        onCloseVersion = { viewModel.closeVersion() },
                        onCloseHistory = { viewModel.closeHistory() },
                        onRestoreVersion = { viewModel.restoreVersion() }
                    )
                }
            }
        }
    }
}
