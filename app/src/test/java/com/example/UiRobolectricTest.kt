package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.db.DeletePolicy
import com.example.data.db.GithubAccountEntity
import com.example.data.db.SyncPairEntity
import com.example.data.secure.AppPrefs
import com.example.drive.DriveFolderItem
import com.example.github.GithubRepoSummary
import com.example.ui.DriveBrowserUiState
import com.example.ui.GithubBrowserUiState
import com.example.ui.GithubFolderBrowserUiState
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.DriveSyncConnectedSheetContent
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PairDetailScreen
import com.example.ui.screens.PairEditorScreen
import com.example.ui.theme.SyncBebasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_displaysAccountCardWebGuideAndPerFolderSyncButton() {
        var openedSheet = false
        var openedWebGuide = false
        var openedPairId = -1L
        var syncedPairId = -1L
        val samplePair = SyncPairEntity(
            id = 7L,
            name = "lotura/docs",
            localPath = "/storage/emulated/0/Documents/docs",
            driveFolderId = "",
            driveFolderName = "",
            githubEnabled = true,
            githubAccountId = 1L,
            githubOwner = "pengguna",
            githubRepo = "lotura",
            githubSubfolder = "docs"
        )

        composeRule.setContent {
            SyncBebasTheme {
                HomeScreen(
                    pairs = listOf(samplePair),
                    appPrefs = AppPrefs(driveConnected = true, googleEmail = "pengguna@gmail.com"),
                    syncingPairIds = emptySet(),
                    isOnline = true,
                    hasStoragePermission = true,
                    onRequestStoragePermission = {},
                    onOpenDriveSheet = { openedSheet = true },
                    onOpenWebGuide = { openedWebGuide = true },
                    onOpenPairDetail = { openedPairId = it },
                    onSyncPair = { syncedPairId = it },
                    onDeleteSelectedPairs = {},
                    onAddPairClick = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_drive_status_card").assertIsDisplayed().performClick()
        assertTrue(openedSheet)

        composeRule.onNodeWithTag("home_web_guide_card").assertIsDisplayed().performClick()
        assertTrue(openedWebGuide)

        // Per-folder sync button directly on pair card
        composeRule.onNodeWithTag("pair_sync_button_7").assertIsDisplayed().performClick()
        assertEquals(7L, syncedPairId)
        composeRule.onNodeWithTag("pair_syncing_notice_7", useUnmergedTree = true).assertDoesNotExist()

        // Local default path and delete policy pills must not appear on Home card
        composeRule.onNodeWithText("/storage/emulated/0/Documents/docs", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Mode Aman").assertDoesNotExist()
        composeRule.onNodeWithText("Aman").assertDoesNotExist()

        composeRule.onNodeWithTag("pair_card_7").assertIsDisplayed().performClick()
        assertEquals(7L, openedPairId)
    }

    @Test
    fun homeScreen_showsWaitNoticeWhilePairIsSyncing() {
        val samplePair = SyncPairEntity(
            id = 9L,
            name = "Catatan",
            localPath = "/storage/emulated/0/Documents/Catatan",
            driveFolderId = "drv-1",
            driveFolderName = "Catatan"
        )

        composeRule.setContent {
            SyncBebasTheme {
                HomeScreen(
                    pairs = listOf(samplePair),
                    appPrefs = AppPrefs(driveConnected = true, googleEmail = "pengguna@gmail.com"),
                    syncingPairIds = setOf(9L),
                    isOnline = true,
                    hasStoragePermission = true,
                    onRequestStoragePermission = {},
                    onOpenDriveSheet = {},
                    onOpenWebGuide = {},
                    onOpenPairDetail = {},
                    onSyncPair = {},
                    onDeleteSelectedPairs = {},
                    onAddPairClick = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pair_syncing_notice_9", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Tunggu hingga selesai agar sinkronisasi berhasil", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun accountsScreen_hasNoLayananCardAndAutoLabelsGithubAccount() {
        composeRule.setContent {
            SyncBebasTheme {
                AccountsScreen(
                    appPrefs = AppPrefs(driveConnected = false),
                    githubAccounts = emptyList(),
                    isConnectingGoogle = false,
                    isAddingGithubAccount = false,
                    googleAuthError = null,
                    githubAccountError = null,
                    onConnectGoogle = {},
                    onDisconnectGoogle = {},
                    onAddGithubAccount = { _, _ -> },
                    onDeleteGithubAccount = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("accounts_connect_google_button").assertIsDisplayed()
        composeRule.onNodeWithText("Layanan").assertDoesNotExist()
        composeRule.onNodeWithText("Pemantau Latar Belakang").assertDoesNotExist()
        composeRule.onNodeWithTag("accounts_realtime_switch").assertDoesNotExist()
        composeRule.onNodeWithText("Tema Precision Slate").assertDoesNotExist()

        // Open Add GitHub dialog: only PAT field, no manual Label Akun field
        composeRule.onNodeWithTag("add_github_account_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("github_pat_input").assertIsDisplayed()
        composeRule.onNodeWithTag("github_label_input").assertDoesNotExist()
    }

    @Test
    fun driveSyncSheet_showsAccountStatusOnlyWithoutScheduleOrSyncAll() {
        var disconnected = false

        composeRule.setContent {
            SyncBebasTheme {
                DriveSyncConnectedSheetContent(
                    userEmail = "pribadi@gmail.com",
                    onDisconnect = { disconnected = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drive_sync_status_card").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_interval_5").assertDoesNotExist()
        composeRule.onNodeWithTag("sync_interval_15").assertDoesNotExist()
        composeRule.onNodeWithTag("drive_sync_now_button").assertDoesNotExist()
        composeRule.onNodeWithTag("drive_disconnect_button").assertIsDisplayed().performClick()
        assertTrue(disconnected)
    }

    @Test
    fun pairDetailScreen_hasNoSyncNowOrCommitGithubButtons() {
        val samplePair = SyncPairEntity(
            id = 3L,
            name = "Catatan",
            localPath = "/storage/emulated/0/Documents/Catatan",
            driveFolderId = "drive-1",
            driveFolderName = "Catatan"
        )

        composeRule.setContent {
            SyncBebasTheme {
                PairDetailScreen(
                    pair = samplePair,
                    trackedFiles = emptyList(),
                    logs = emptyList(),
                    onOpenExplorer = {},
                    onEditPair = {},
                    onDeletePair = {},
                    onNavigateBack = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("/storage/emulated/0/Documents/Catatan").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_open_explorer_button").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_edit_pair_button").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_sync_now_button").assertDoesNotExist()
        composeRule.onNodeWithTag("detail_commit_github_button").assertDoesNotExist()
        composeRule.onNodeWithText("Mode Aman").assertDoesNotExist()
    }

    @Test
    fun pairEditorScreen_sourceFirstAutoNamesDrivePairAndRemovesLegacyOptions() {
        var savedPair: SyncPairEntity? = null

        composeRule.setContent {
            SyncBebasTheme {
                PairEditorScreen(
                    existingPair = null,
                    githubAccounts = emptyList(),
                    driveBrowser = DriveBrowserUiState(
                        folders = listOf(DriveFolderItem(id = "drv-99", name = "Catatan Kerja"))
                    ),
                    githubBrowser = GithubBrowserUiState(),
                    githubFolderBrowser = GithubFolderBrowserUiState(),
                    onOpenDriveFolder = { _, _ -> },
                    onCreateDriveFolder = {},
                    onLoadGithubRepos = {},
                    onLoadGithubFolders = { _, _, _, _ -> },
                    onOpenGithubFolderPath = {},
                    onSavePair = { savedPair = it },
                    onNavigateBack = {}
                )
            }
        }

        composeRule.waitForIdle()
        // Legacy manual/policy/watcher/local-picker/default-path/name-preview controls must not exist
        composeRule.onNodeWithTag("pair_name_input").assertDoesNotExist()
        composeRule.onNodeWithTag("policy_safe_card").assertDoesNotExist()
        composeRule.onNodeWithTag("policy_two_way_card").assertDoesNotExist()
        composeRule.onNodeWithTag("pair_auto_drive_switch").assertDoesNotExist()
        composeRule.onNodeWithTag("pair_browse_local_button").assertDoesNotExist()
        composeRule.onNodeWithTag("pair_resolved_local_path").assertDoesNotExist()
        composeRule.onNodeWithTag("auto_pair_name_preview").assertDoesNotExist()

        // Pick Google Drive folder
        composeRule.onNodeWithTag("pair_pick_drive_folder_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pick_drive_folder_drv-99").performClick()
        composeRule.waitForIdle()

        // Still no default path card or auto pair name preview after picking source
        composeRule.onNodeWithTag("pair_resolved_local_path").assertDoesNotExist()
        composeRule.onNodeWithTag("auto_pair_name_preview").assertDoesNotExist()

        // Save pair
        composeRule.onNodeWithTag("save_pair_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedPair)
        assertEquals("Catatan Kerja", savedPair?.name)
        assertEquals("/storage/emulated/0/Documents/Catatan Kerja", savedPair?.localPath)
        assertFalse(savedPair?.githubEnabled == true)
        assertEquals(DeletePolicy.TWO_WAY, savedPair?.resolvedDeletePolicy)
    }

    @Test
    fun pairEditorScreen_githubRequiresSingleFolderAndAutoNamesPair() {
        var savedPair: SyncPairEntity? = null

        composeRule.setContent {
            SyncBebasTheme {
                PairEditorScreen(
                    existingPair = null,
                    githubAccounts = listOf(GithubAccountEntity(id = 1L, label = "Dev", username = "devuser")),
                    driveBrowser = DriveBrowserUiState(),
                    githubBrowser = GithubBrowserUiState(
                        repos = listOf(
                            GithubRepoSummary(
                                owner = "devuser",
                                name = "lotura",
                                fullName = "devuser/lotura",
                                defaultBranch = "main",
                                isPrivate = false
                            )
                        )
                    ),
                    githubFolderBrowser = GithubFolderBrowserUiState(
                        owner = "devuser",
                        repo = "lotura",
                        branch = "main",
                        allFolders = listOf("app", "docs", "docs/guides"),
                        currentPath = ""
                    ),
                    onOpenDriveFolder = { _, _ -> },
                    onCreateDriveFolder = {},
                    onLoadGithubRepos = {},
                    onLoadGithubFolders = { _, _, _, _ -> },
                    onOpenGithubFolderPath = {},
                    onSavePair = { savedPair = it },
                    onNavigateBack = {}
                )
            }
        }

        composeRule.waitForIdle()
        // Switch source to GitHub
        composeRule.onNodeWithTag("source_type_github_card").performClick()
        composeRule.waitForIdle()

        // Attempting to save without selecting repo/folder must be blocked
        composeRule.onNodeWithTag("save_pair_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertNull(savedPair)
        composeRule.onNodeWithTag("pair_validation_error").assertIsDisplayed()

        // Pick repo -> opens folder picker automatically
        composeRule.onNodeWithTag("pair_pick_github_repo_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pick_gh_repo_lotura").performClick()
        composeRule.waitForIdle()

        // Root cannot be confirmed directly; pick "docs" folder
        composeRule.onNodeWithTag("confirm_gh_folder_button").assertDoesNotExist()
        composeRule.onNodeWithTag("pick_gh_folder_docs").performClick()
        composeRule.waitForIdle()

        // Save pair
        composeRule.onNodeWithTag("save_pair_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedPair)
        assertEquals("lotura/docs", savedPair?.name)
        assertEquals("docs", savedPair?.githubSubfolder)
        assertEquals("/storage/emulated/0/Documents/docs", savedPair?.localPath)
        assertTrue(savedPair?.githubEnabled == true)
        assertEquals("", savedPair?.driveFolderId)
    }
}
