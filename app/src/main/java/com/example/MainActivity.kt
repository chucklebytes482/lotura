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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drive.GoogleAuthManager
import com.example.drive.findActivity
import com.example.remote.RemoteEntry
import com.example.ui.RemoteViewModel
import com.example.ui.components.StoragePermissionHelper
import com.example.ui.screens.FolderUploadDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.SyncBebasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: RemoteViewModel = viewModel()
            SyncBebasTheme {
                SyncBebasRootApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncBebasRootApp(viewModel: RemoteViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    val driveAccounts by viewModel.driveAccounts.collectAsStateWithLifecycle()
    val githubAccounts by viewModel.githubAccounts.collectAsStateWithLifecycle()
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasStoragePermission by remember { mutableStateOf(StoragePermissionHelper.hasAllFilesAccess(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasStoragePermission = StoragePermissionHelper.hasAllFilesAccess(context)
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

    var pendingDownload by remember { mutableStateOf<RemoteEntry?>(null) }
    fun finishStorageRequest() {
        hasStoragePermission = StoragePermissionHelper.hasAllFilesAccess(context)
        val entry = pendingDownload ?: return
        pendingDownload = null
        if (hasStoragePermission) viewModel.download(entry)
        else viewModel.showMessage("Izin penyimpanan diperlukan untuk mengunduh file.")
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        finishStorageRequest()
    }
    val storageSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        finishStorageRequest()
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        viewModel.uploadSelected(it)
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        viewModel.onFolderPicked(it)
    }
    val addFolder: () -> Unit = {
        if (viewModel.beginAddingFolder()) {
            runCatching { folderPicker.launch(null) }.onFailure {
                viewModel.onFolderPicked(null, "Pemilih folder Android tidak tersedia.")
            }
        }
    }
    uiState.folderUpload?.let { queue ->
        FolderUploadDialog(queue, viewModel::cancelFolderUpload, addFolder, viewModel::removePickedFolder,
            viewModel::uploadPickedFolders)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        HomeScreen(
            modifier = Modifier.padding(innerPadding),
            state = uiState,
            driveAccounts = driveAccounts,
            githubAccounts = githubAccounts,
            repos = repos,
            viewModel = viewModel,
            onConnectDrive = startDriveAuth,
            onUploadFiles = {
                if (viewModel.preparePicker()) {
                    runCatching { filePicker.launch(arrayOf("*/*")) }.onFailure {
                        viewModel.uploadSelected(emptyList())
                        viewModel.showMessage("Pemilih file Android tidak tersedia.")
                    }
                }
            },
            onUploadFolders = { if (viewModel.beginFolderUpload()) addFolder() },
            onDownload = { entry ->
                if (hasStoragePermission) viewModel.download(entry)
                else {
                    pendingDownload = entry
                    if (android.os.Build.VERSION.SDK_INT < 30) {
                        permissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        runCatching {
                            storageSettings.launch(StoragePermissionHelper.buildAllFilesSettingsIntent(context))
                        }.onFailure {
                            pendingDownload = null
                            viewModel.showMessage("Buka Pengaturan Android → Akses Semua File untuk Lotura.")
                        }
                    }
                }
            }
        )
    }
}
