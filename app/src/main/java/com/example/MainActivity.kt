package com.example

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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drive.GoogleAuthManager
import com.example.drive.findActivity
import com.example.ui.SyncViewModel
import com.example.ui.update.ApkUpdateViewModel
import com.example.update.ApkUpdate
import com.example.ui.components.StoragePermissionHelper
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.SyncBebasTheme

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
    val snackbarHostState = remember { SnackbarHostState() }

    val driveAccounts by viewModel.driveAccounts.collectAsStateWithLifecycle()
    val githubAccounts by viewModel.githubAccounts.collectAsStateWithLifecycle()
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val apkViewModel: ApkUpdateViewModel = viewModel()
    val apkState by apkViewModel.state.collectAsStateWithLifecycle()

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Lotura",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
    ) { innerPadding ->
        HomeScreen(
            modifier = Modifier.padding(innerPadding),
            driveAccounts = driveAccounts,
            githubAccounts = githubAccounts,
            repos = repos,
            trees = uiState.trees,
            loading = uiState.loading,
            logs = logs,
            busySources = uiState.busySources,
            queuedSources = uiState.queuedSources,
            isConnectingDrive = uiState.isConnectingDrive,
            driveAuthError = uiState.driveAuthError,
            isAddingGithub = uiState.isAddingGithub,
            githubAccountError = uiState.githubAccountError,
            hasStoragePermission = hasStoragePermission,
            isOnline = uiState.isOnline,
            apkState = apkState,
            onDownloadApk = { apkViewModel.download() },
            onRetryApkInstall = { apkViewModel.retryInstall() },
            onOpenUnknownSourcesSettings = {
                runCatching {
                    context.startActivity(ApkUpdate.unknownSourcesIntent(context))
                }.onFailure {
                    viewModel.showMessage("Buka Pengaturan Android -> Aplikasi -> Akses khusus -> Pasang aplikasi tidak dikenal.")
                }
            },
            onDismissApkPrompt = { apkViewModel.dismissPermissionPrompt() },
            onDismissApkError = { apkViewModel.dismissError() },
            onRequestStoragePermission = {
                runCatching {
                    context.startActivity(StoragePermissionHelper.buildAllFilesSettingsIntent(context))
                }.onFailure {
                    viewModel.showMessage("Buka Pengaturan Android -> Akses Semua File untuk Lotura.")
                }
            },
            onConnectDrive = startDriveAuth,
            onRemoveDrive = { email -> viewModel.removeDriveAccount(email) },
            onReloadDrive = { email -> viewModel.reloadDrive(email) },
            onAddGithubAccount = { pat, onOk -> viewModel.addGithubAccount(pat, onOk) },
            onRefreshRepos = { account -> viewModel.refreshGithubRepos(account) },
            onDeleteGithubAccount = { account -> viewModel.deleteGithubAccount(account) },
            onSyncFolder = { item, upload -> viewModel.syncFolder(item, upload) },
            onSyncRepo = { repo, upload -> viewModel.syncRepo(repo, upload) },
            onCancelSync = { key -> viewModel.cancelSync(key) }
        )
    }
}
