package com.example

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun homeScreen_displaysIconOnlyMetricsSupportsLongPressDeleteAndNoRedundantLabels() {
        var openedSheet = false
        var openedWebGuide = false
        var syncedPairId = -1L
        var deletedPairs: List<SyncPairEntity> = emptyList()

        val githubPair = SyncPairEntity(
            id = 7L,
            name = "Petapikir/panduan",
            localPath = "/storage/emulated/0/Documents/GitHub/Petapikir/panduan",
            driveFolderId = "",
            driveFolderName = "",
            githubEnabled = true,
            githubAccountId = 1L,
            githubOwner = "pengguna",
            githubRepo = "Petapikir",
            githubSubfolder = "panduan",
            lastStatus = "GitHub: ↑3 ↓2 Hapus:1 Konflik:4"
        )
        val drivePair = SyncPairEntity(
            id = 8L,
            name = "Catatan",
            localPath = "/storage/emulated/0/Documents/Google Drive/Catatan",
            driveFolderId = "drv-8",
            driveFolderName = "Catatan",
            lastStatus = "Galat Drive",
            lastError = "Token kedaluwarsa"
        )

        composeRule.setContent {
            SyncBebasTheme {
                HomeScreen(
                    pairs = listOf(githubPair, drivePair),
                    appPrefs = AppPrefs(driveConnected = true, googleEmail = "pengguna@gmail.com"),
                    syncingPairIds = emptySet(),
                    isOnline = true,
                    hasStoragePermission = true,
                    onRequestStoragePermission = {},
                    onOpenDriveSheet = { openedSheet = true },
                    onOpenWebGuide = { openedWebGuide = true },
                    onSyncPair = { syncedPairId = it },
                    onDeleteSelectedPairs = { deletedPairs = it },
                    onAddPairClick = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_drive_status_card").assertIsDisplayed().performClick()
        assertTrue(openedSheet)

        composeRule.onNodeWithTag("home_web_guide_card").assertIsDisplayed().performClick()
        assertTrue(openedWebGuide)
        composeRule.onNodeWithText("Panduan & Arti Ikon", useUnmergedTree = true).assertIsDisplayed()

        // Scroll to GitHub pair card and verify icon-only layout with 4 always-visible metrics
        composeRule.onNodeWithTag("home_pair_list").performScrollToKey(7L)
        composeRule.onNodeWithTag("pair_provider_icon_7", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("pair_metric_upload_7", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("pair_metric_download_7", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("pair_metric_delete_7", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("pair_metric_conflict_7", useUnmergedTree = true).assertIsDisplayed()

        // Redundant provider text badges & verbose status words must not exist on pair cards
        composeRule.onNodeWithTag("pair_provider_badge_7", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Hapus:1", substring = true, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Konflik:4", substring = true, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("/storage/emulated/0/Documents/GitHub/Petapikir/panduan", useUnmergedTree = true).assertDoesNotExist()

        // Per-folder sync button works directly
        composeRule.onNodeWithTag("pair_sync_button_7").assertIsDisplayed().performClick()
        assertEquals(7L, syncedPairId)
        composeRule.onNodeWithTag("pair_syncing_notice_7", useUnmergedTree = true).assertDoesNotExist()

        // Scroll to Drive pair card with error: shows error icon, not verbose error text
        composeRule.onNodeWithTag("home_pair_list").performScrollToKey(8L)
        composeRule.onNodeWithTag("pair_provider_icon_8", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("pair_metric_error_8", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Token kedaluwarsa", useUnmergedTree = true).assertDoesNotExist()

        // Long-press enters selection mode and deletes selected pair
        composeRule.onNodeWithTag("pair_card_8").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_pair_list").performScrollToKey("selection_bar")
        composeRule.onNodeWithTag("selection_action_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_selected_button").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(8L), deletedPairs.map { it.id })
    }

    @Test
    fun homeScreen_keepsFixedCardLayoutWithoutDuplicateWaitNoticeWhileSyncing() {
        val samplePair = SyncPairEntity(
            id = 9L,
            name = "Catatan",
            localPath = "/storage/emulated/0/Documents/Google Drive/Catatan",
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
                    onSyncPair = {},
                    onDeleteSelectedPairs = {},
                    onAddPairClick = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_pair_list").performScrollToKey(9L)
        composeRule.onNodeWithTag("pair_sync_button_9").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithTag("pair_syncing_notice_9", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Tunggu hingga selesai agar sinkronisasi berhasil", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("pair_metric_upload_9", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("pair_metric_download_9", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("pair_metric_delete_9", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("pair_metric_conflict_9", useUnmergedTree = true).assertIsDisplayed()
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
    fun pairEditorScreen_driveUsesRootOnlyPickerAndGoogleDriveFolderHierarchy() {
        var savedPair: SyncPairEntity? = null
        val alreadyPairedDrive = SyncPairEntity(
            id = 10L,
            name = "SudahAda",
            localPath = "/storage/emulated/0/Documents/Google Drive/SudahAda",
            driveFolderId = "drv-paired",
            driveFolderName = "SudahAda"
        )

        composeRule.setContent {
            SyncBebasTheme {
                PairEditorScreen(
                    existingPairs = listOf(alreadyPairedDrive),
                    githubAccounts = emptyList(),
                    driveBrowser = DriveBrowserUiState(
                        folders = listOf(
                            DriveFolderItem(id = "drv-paired", name = "SudahAda"),
                            DriveFolderItem(id = "drv-99", name = "Catatan Kerja")
                        )
                    ),
                    githubBrowser = GithubBrowserUiState(),
                    githubFolderBrowser = GithubFolderBrowserUiState(),
                    onLoadDriveRootFolders = {},
                    onCreateDriveFolder = {},
                    onLoadGithubRepos = {},
                    onLoadGithubFolders = { _, _, _, _ -> },
                    onSavePair = { savedPair = it },
                    onNavigateBack = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pair_name_input").assertDoesNotExist()
        composeRule.onNodeWithTag("pair_browse_local_button").assertDoesNotExist()
        composeRule.onNodeWithTag("pair_resolved_local_path").assertDoesNotExist()
        composeRule.onNodeWithTag("auto_pair_name_preview").assertDoesNotExist()

        // Open Drive root-only folder picker: paired folder shows Terpasang badge and no subfolder confirm button
        composeRule.onNodeWithTag("pair_pick_drive_folder_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drive_folder_paired_drv-paired", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("pick_drive_folder_drv-paired").assertDoesNotExist()
        composeRule.onNodeWithTag("confirm_drive_folder_button").assertDoesNotExist()

        // Single-tap unpaired root Google Drive folder
        composeRule.onNodeWithTag("pick_drive_folder_drv-99").performClick()
        composeRule.waitForIdle()

        // Save pair
        composeRule.onNodeWithTag("save_pair_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedPair)
        assertEquals("Catatan Kerja", savedPair?.name)
        assertEquals("/storage/emulated/0/Documents/Google Drive/Catatan Kerja", savedPair?.localPath)
        assertFalse(savedPair?.githubEnabled == true)
        assertEquals(DeletePolicy.TWO_WAY, savedPair?.resolvedDeletePolicy)
    }

    @Test
    fun pairEditorScreen_githubUsesRootOnlyPickerAndGithubRepoFolderHierarchy() {
        var savedPair: SyncPairEntity? = null
        val alreadyPairedGithub = SyncPairEntity(
            id = 20L,
            name = "Petapikir/docs",
            localPath = "/storage/emulated/0/Documents/GitHub/Petapikir/docs",
            driveFolderId = "",
            driveFolderName = "",
            githubEnabled = true,
            githubAccountId = 1L,
            githubOwner = "chucklebytes482",
            githubRepo = "Petapikir",
            githubBranch = "main",
            githubSubfolder = "docs"
        )

        composeRule.setContent {
            SyncBebasTheme {
                PairEditorScreen(
                    existingPairs = listOf(alreadyPairedGithub),
                    githubAccounts = listOf(GithubAccountEntity(id = 1L, label = "Dev", username = "chucklebytes482")),
                    driveBrowser = DriveBrowserUiState(),
                    githubBrowser = GithubBrowserUiState(
                        repos = listOf(
                            GithubRepoSummary(
                                owner = "chucklebytes482",
                                name = "Petapikir",
                                fullName = "chucklebytes482/Petapikir",
                                defaultBranch = "main",
                                isPrivate = false
                            )
                        )
                    ),
                    githubFolderBrowser = GithubFolderBrowserUiState(
                        owner = "chucklebytes482",
                        repo = "Petapikir",
                        branch = "main",
                        allFolders = listOf("docs", "docs/guides", "panduan", "panduan/sub")
                    ),
                    onLoadDriveRootFolders = {},
                    onCreateDriveFolder = {},
                    onLoadGithubRepos = {},
                    onLoadGithubFolders = { _, _, _, _ -> },
                    onSavePair = { savedPair = it },
                    onNavigateBack = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("source_type_github_card").performClick()
        composeRule.waitForIdle()

        // Saving without selecting repo/folder is blocked
        composeRule.onNodeWithTag("save_pair_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertNull(savedPair)
        composeRule.onNodeWithTag("pair_validation_error").assertIsDisplayed()

        // Pick repo -> opens root-only folder picker automatically
        composeRule.onNodeWithTag("pair_pick_github_repo_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pick_gh_repo_Petapikir").performClick()
        composeRule.waitForIdle()

        // Subfolder navigation controls must not exist; nested subfolder "guides" is not listed
        composeRule.onNodeWithTag("gh_folder_up_button").assertDoesNotExist()
        composeRule.onNodeWithTag("confirm_gh_folder_button").assertDoesNotExist()
        composeRule.onNodeWithText("guides").assertDoesNotExist()

        // Already-paired root folder "docs" shows Terpasang badge and cannot be picked
        composeRule.onNodeWithTag("gh_folder_paired_docs", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("pick_gh_folder_docs").assertDoesNotExist()

        // Pick unpaired root folder "panduan"
        composeRule.onNodeWithTag("pick_gh_folder_panduan").performClick()
        composeRule.waitForIdle()

        // Save pair
        composeRule.onNodeWithTag("save_pair_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedPair)
        assertEquals("Petapikir/panduan", savedPair?.name)
        assertEquals("panduan", savedPair?.githubSubfolder)
        assertEquals("/storage/emulated/0/Documents/GitHub/Petapikir/panduan", savedPair?.localPath)
        assertTrue(savedPair?.githubEnabled == true)
        assertEquals("", savedPair?.driveFolderId)
    }
}
