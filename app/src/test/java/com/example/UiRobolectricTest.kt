package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
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
import java.io.File
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_displaysAccountCardWebGuideDistinctProviderBadgesAndSyncButton() {
        var openedSheet = false
        var openedWebGuide = false
        var openedPairId = -1L
        var syncedPairId = -1L
        val githubPair = SyncPairEntity(
            id = 7L,
            name = "lotura/docs",
            localPath = "/storage/emulated/0/Documents/lotura/docs",
            driveFolderId = "",
            driveFolderName = "",
            githubEnabled = true,
            githubAccountId = 1L,
            githubOwner = "pengguna",
            githubRepo = "lotura",
            githubSubfolder = "docs"
        )
        val drivePair = SyncPairEntity(
            id = 8L,
            name = "Catatan",
            localPath = "/storage/emulated/0/Documents/Catatan",
            driveFolderId = "drv-8",
            driveFolderName = "Catatan"
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

        // Distinct provider badges for GitHub vs Google Drive cards
        composeRule.onNodeWithTag("home_pair_list").performScrollToKey(7L)
        composeRule.onNodeWithTag("pair_provider_badge_7", useUnmergedTree = true).assertIsDisplayed()

        // Per-folder sync button directly on pair card
        composeRule.onNodeWithTag("pair_sync_button_7").assertIsDisplayed().performClick()
        assertEquals(7L, syncedPairId)
        composeRule.onNodeWithTag("pair_syncing_notice_7", useUnmergedTree = true).assertDoesNotExist()

        // Local default path and delete policy pills must not appear on Home card
        composeRule.onNodeWithText("/storage/emulated/0/Documents/lotura/docs", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Mode Aman").assertDoesNotExist()
        composeRule.onNodeWithText("Aman").assertDoesNotExist()

        composeRule.onNodeWithTag("pair_card_7").assertIsDisplayed().performClick()
        assertEquals(7L, openedPairId)

        // Scroll to Google Drive pair card and verify its distinct provider badge
        composeRule.onNodeWithTag("home_pair_list").performScrollToKey(8L)
        composeRule.onNodeWithTag("pair_provider_badge_8", useUnmergedTree = true).assertIsDisplayed()
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
    fun pairDetailScreen_embedsFileExplorerDirectlyWithoutEditOrRecentLogs() {
        val tempDir = createTempDirectory("lotura_detail_test").toFile()
        try {
            val sampleNote = File(tempDir, "catatan.md").apply { writeText("# Halo Lotura\n") }
            val samplePair = SyncPairEntity(
                id = 3L,
                name = "Petapikir/panduan",
                localPath = tempDir.absolutePath,
                driveFolderId = "",
                driveFolderName = "",
                githubEnabled = true,
                githubAccountId = 1L,
                githubOwner = "chucklebytes482",
                githubRepo = "Petapikir",
                githubBranch = "main",
                githubSubfolder = "panduan"
            )

            composeRule.setContent {
                SyncBebasTheme {
                    PairDetailScreen(
                        pair = samplePair,
                        trackedFiles = emptyList(),
                        onDeletePair = {},
                        onShowMessage = {},
                        onNavigateBack = {}
                    )
                }
            }

            composeRule.waitForIdle()
            // Top summary shows device path and Delete button
            composeRule.onNodeWithText(tempDir.absolutePath).assertIsDisplayed()
            composeRule.onNodeWithTag("detail_delete_pair_button").assertIsDisplayed()

            // Inline File Explorer controls & file are displayed directly
            composeRule.onNodeWithTag("detail_content_list").performScrollToKey("explorer_header_bar")
            composeRule.onNodeWithTag("explorer_new_file_button").assertIsDisplayed()
            composeRule.onNodeWithTag("explorer_new_folder_button").assertIsDisplayed()
            composeRule.onNodeWithTag("detail_content_list").performScrollToKey("explorer_search")
            composeRule.onNodeWithTag("explorer_search_input").assertIsDisplayed()
            composeRule.onNodeWithTag("detail_content_list").performScrollToKey(sampleNote.absolutePath)
            composeRule.onNodeWithText("catatan.md", useUnmergedTree = true).assertIsDisplayed()

            // Removed controls & sections must not exist
            composeRule.onNodeWithTag("detail_open_explorer_button").assertDoesNotExist()
            composeRule.onNodeWithTag("detail_edit_pair_button").assertDoesNotExist()
            composeRule.onNodeWithTag("detail_sync_now_button").assertDoesNotExist()
            composeRule.onNodeWithTag("detail_commit_github_button").assertDoesNotExist()
            composeRule.onNodeWithText("Aktivitas Sinkronisasi Terbaru").assertDoesNotExist()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun pairEditorScreen_sourceFirstAutoNamesDrivePairAndMarksAlreadyPairedFolders() {
        var savedPair: SyncPairEntity? = null
        val alreadyPairedDrive = SyncPairEntity(
            id = 10L,
            name = "SudahAda",
            localPath = "/storage/emulated/0/Documents/SudahAda",
            driveFolderId = "drv-paired",
            driveFolderName = "SudahAda"
        )

        composeRule.setContent {
            SyncBebasTheme {
                PairEditorScreen(
                    existingPairs = listOf(alreadyPairedDrive),
                    githubAccounts = emptyList(),
                    driveBrowser = DriveBrowserUiState(
                        currentParentId = "drv-paired",
                        breadcrumbs = listOf("root" to "Drive Saya", "drv-paired" to "SudahAda"),
                        folders = listOf(
                            DriveFolderItem(id = "drv-paired", name = "SudahAda"),
                            DriveFolderItem(id = "drv-99", name = "Catatan Kerja")
                        )
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
        // Legacy controls must not exist
        composeRule.onNodeWithTag("pair_name_input").assertDoesNotExist()
        composeRule.onNodeWithTag("pair_browse_local_button").assertDoesNotExist()
        composeRule.onNodeWithTag("pair_resolved_local_path").assertDoesNotExist()
        composeRule.onNodeWithTag("auto_pair_name_preview").assertDoesNotExist()

        // Open Drive folder picker: paired folder shows Terpasang badge, no Pilih button, and confirm button disabled
        composeRule.onNodeWithTag("pair_pick_drive_folder_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drive_folder_paired_drv-paired", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("pick_drive_folder_drv-paired").assertDoesNotExist()
        composeRule.onNodeWithTag("confirm_drive_folder_button").assertIsNotEnabled()

        // Pick unpaired Google Drive folder
        composeRule.onNodeWithTag("pick_drive_folder_drv-99").performClick()
        composeRule.waitForIdle()

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
    fun pairEditorScreen_githubNestsRepoInLocalPathAndPreventsDuplicateFolders() {
        var savedPair: SyncPairEntity? = null
        val alreadyPairedGithub = SyncPairEntity(
            id = 20L,
            name = "Petapikir/docs",
            localPath = "/storage/emulated/0/Documents/Petapikir/docs",
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
                        allFolders = listOf("docs", "docs/guides", "panduan"),
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
        composeRule.onNodeWithTag("pick_gh_repo_Petapikir").performClick()
        composeRule.waitForIdle()

        // Already-paired "docs" folder shows Terpasang badge and cannot be selected
        composeRule.onNodeWithTag("gh_folder_paired_docs", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("pick_gh_folder_docs").assertDoesNotExist()

        // Root cannot be confirmed directly; pick unpaired "panduan" folder
        composeRule.onNodeWithTag("confirm_gh_folder_button").assertDoesNotExist()
        composeRule.onNodeWithTag("pick_gh_folder_panduan").performClick()
        composeRule.waitForIdle()

        // Save pair
        composeRule.onNodeWithTag("save_pair_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedPair)
        assertEquals("Petapikir/panduan", savedPair?.name)
        assertEquals("panduan", savedPair?.githubSubfolder)
        assertEquals("/storage/emulated/0/Documents/Petapikir/panduan", savedPair?.localPath)
        assertTrue(savedPair?.githubEnabled == true)
        assertEquals("", savedPair?.driveFolderId)
    }
}
