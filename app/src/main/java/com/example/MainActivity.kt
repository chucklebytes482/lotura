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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.DriveSyncConnectedSheetContent
import com.example.ui.screens.DriveSyncUnauthenticatedSheetContent
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PairEditorScreen
import com.example.ui.screens.SyncLogScreen
import com.example.ui.theme.SyncBebasTheme

object Routes {
    const val HOME = "home"
    const val ACCOUNTS = "accounts"
    const val LOGS = "logs"
    const val PAIR_CREATE = "pair_editor"
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

    val pairs by viewModel.pairs.collectAsStateWithLifecycle()
    val githubAccounts by viewModel.githubAccounts.collectAsStateWithLifecycle()
    val recentLogs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val appPrefs by viewModel.appPrefs.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showDriveSheet by remember { mutableStateOf(false) }
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
            .onSuccess { token -> viewModel.onGoogleAccessTokenReceived(token) }
            .onFailure { err ->
                viewModel.onGoogleAuthError(
                    err.localizedMessage ?: "Otorisasi Google Drive dibatalkan atau gagal."
                )
            }
    }

    val startGoogleDriveAuth: () -> Unit = {
        val activity = context.findActivity()
        if (activity == null) {
            viewModel.onGoogleAuthError("Activity tidak tersedia untuk otorisasi Google.")
        } else {
            viewModel.onGoogleConnectingStarted()
            GoogleAuthManager.authorizeDriveInteractive(
                activity = activity,
                resolutionLauncher = driveAuthResolutionLauncher,
                onAccessToken = { token -> viewModel.onGoogleAccessTokenReceived(token) },
                onError = { err -> viewModel.onGoogleAuthError(err) }
            )
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSnackbar()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.HOME
    val isTopLevel = currentRoute in setOf(Routes.HOME, Routes.ACCOUNTS, Routes.LOGS)

    val topBarTitle = when (currentRoute) {
        Routes.HOME -> "Lotura"
        Routes.ACCOUNTS -> "Akun"
        Routes.LOGS -> "Log"
        Routes.PAIR_CREATE -> "Tambah Pasangan"
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
                    if (!isTopLevel) {
                        IconButton(
                            onClick = { navController.popBackStack() },
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
        bottomBar = {
            if (isTopLevel) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == Routes.HOME) Icons.Filled.FolderCopy else Icons.Outlined.FolderCopy,
                                contentDescription = "Beranda"
                            )
                        },
                        label = { Text("Beranda") },
                        modifier = Modifier.testTag("nav_home_tab")
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.ACCOUNTS,
                        onClick = {
                            navController.navigate(Routes.ACCOUNTS) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == Routes.ACCOUNTS) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                                contentDescription = "Akun"
                            )
                        },
                        label = { Text("Akun") },
                        modifier = Modifier.testTag("nav_accounts_tab")
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.LOGS,
                        onClick = {
                            navController.navigate(Routes.LOGS) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentRoute == Routes.LOGS) Icons.Filled.History else Icons.Outlined.History,
                                contentDescription = "Log"
                            )
                        },
                        label = { Text("Log") },
                        modifier = Modifier.testTag("nav_logs_tab")
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.HOME) {
                FloatingActionButton(
                    onClick = { navController.navigate(Routes.PAIR_CREATE) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_pair_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah pasangan folder")
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    pairs = pairs,
                    appPrefs = appPrefs,
                    syncingPairIds = uiState.syncingPairIds,
                    isOnline = uiState.isOnline,
                    hasStoragePermission = hasStoragePermission,
                    onRequestStoragePermission = {
                        runCatching {
                            context.startActivity(StoragePermissionHelper.buildAllFilesSettingsIntent(context))
                        }.onFailure {
                            viewModel.showMessage("Buka Pengaturan Android -> Akses Semua File untuk Lotura.")
                        }
                    },
                    onOpenDriveSheet = { showDriveSheet = true },
                    onOpenWebGuide = {
                        val guideUrl = context.getString(R.string.web_guide_url)
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guideUrl)))
                        }.onFailure {
                            viewModel.showMessage("Gagal membuka browser.")
                        }
                    },
                    onSyncPair = { id -> viewModel.syncPairNow(id) },
                    onDeleteSelectedPairs = { selected -> selected.forEach { viewModel.deletePair(it) } },
                    onAddPairClick = { navController.navigate(Routes.PAIR_CREATE) }
                )
            }

            composable(Routes.ACCOUNTS) {
                AccountsScreen(
                    appPrefs = appPrefs,
                    githubAccounts = githubAccounts,
                    isConnectingGoogle = uiState.isConnectingGoogle,
                    isAddingGithubAccount = uiState.isAddingGithubAccount,
                    googleAuthError = uiState.googleAuthError,
                    githubAccountError = uiState.githubAccountError,
                    onConnectGoogle = startGoogleDriveAuth,
                    onDisconnectGoogle = { viewModel.disconnectGoogleAccount() },
                    onAddGithubAccount = { pat, onOk -> viewModel.addGithubAccount(pat, onOk) },
                    onDeleteGithubAccount = { viewModel.deleteGithubAccount(it) }
                )
            }

            composable(Routes.LOGS) {
                SyncLogScreen(
                    logs = recentLogs,
                    onClearLogs = { viewModel.clearLogs() }
                )
            }

            composable(Routes.PAIR_CREATE) {
                PairEditorScreen(
                    existingPairs = pairs,
                    githubAccounts = githubAccounts,
                    driveBrowser = uiState.driveBrowser,
                    githubBrowser = uiState.githubBrowser,
                    githubFolderBrowser = uiState.githubFolderBrowser,
                    onLoadDriveRootFolders = { viewModel.loadDriveRootFolders() },
                    onCreateDriveFolder = { name -> viewModel.createDriveFolder(name) },
                    onLoadGithubRepos = { accId -> viewModel.loadGithubReposForAccount(accId) },
                    onLoadGithubFolders = { accId, owner, repo, branch ->
                        viewModel.loadGithubFolders(accId, owner, repo, branch)
                    },
                    onSavePair = { pair ->
                        viewModel.savePair(pair) {
                            navController.popBackStack()
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }

    if (showDriveSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDriveSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            if (!appPrefs.driveConnected) {
                DriveSyncUnauthenticatedSheetContent(
                    isLoading = uiState.isConnectingGoogle,
                    errorMessage = uiState.googleAuthError,
                    onConnectGoogleClick = startGoogleDriveAuth
                )
            } else {
                DriveSyncConnectedSheetContent(
                    userEmail = appPrefs.googleEmail,
                    onDisconnect = {
                        viewModel.disconnectGoogleAccount()
                        showDriveSheet = false
                    }
                )
            }
        }
    }
}
