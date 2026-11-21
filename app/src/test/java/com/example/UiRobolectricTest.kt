package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.db.DeletePolicy
import com.example.data.db.GithubAccountEntity
import com.example.data.db.SyncPairEntity
import com.example.data.secure.AppPrefs
import com.example.ui.DriveBrowserUiState
import com.example.ui.GithubBrowserUiState
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.DriveSyncConnectedSheetContent
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PairEditorScreen
import com.example.ui.theme.SyncBebasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun homeScreen_displaysPrioritySyncAndWebGuideAndPairs() {
        var openedSheet = false
        var openedWebGuide = false
        var openedPairId = -1L
        val samplePair = SyncPairEntity(
            id = 7L,
            name = "Catatan Markdown",
            localPath = "/storage/emulated/0/Documents/Catatan",
            driveFolderId = "drive-123",
            driveFolderName = "Catatan Drive",
            deletePolicy = DeletePolicy.SAFE_NO_DELETE.name,
            githubEnabled = true,
            githubOwner = "pengguna",
            githubRepo = "catatan-md"
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
                    onSyncSelectedPairs = {},
                    onDeleteSelectedPairs = {},
                    onAddPairClick = {}
                )
            }
        }

        composeRule.waitForIdle()
        // Prioritas #1: Sinkronisasi
        composeRule.onNodeWithTag("home_drive_status_card").assertIsDisplayed().performClick()
        assertTrue(openedSheet)

        // Prioritas #2: Web Panduan (GitHub Pages)
        composeRule.onNodeWithTag("home_web_guide_card").assertIsDisplayed().performClick()
        assertTrue(openedWebGuide)

        composeRule.onNodeWithTag("pair_card_7").assertIsDisplayed().performClick()
        assertEquals(7L, openedPairId)
    }

    @Test
    fun accountsScreen_hasNoThemeSelector() {
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
                    onAddGithubAccount = { _, _, _ -> },
                    onDeleteGithubAccount = {},
                    onToggleRealtimeWatcher = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("accounts_connect_google_button").assertIsDisplayed()
        composeRule.onNodeWithText("Tema Precision Slate").assertDoesNotExist()
        composeRule.onNodeWithText("Layanan & Tema").assertDoesNotExist()
    }

    @Test
    fun pairEditorScreen_validatesAndSavesNewSyncPair() {
        var savedPair: SyncPairEntity? = null

        composeRule.setContent {
            SyncBebasTheme {
                PairEditorScreen(
                    existingPair = null,
                    githubAccounts = listOf(GithubAccountEntity(id = 1L, label = "Utama", username = "dev")),
                    driveBrowser = DriveBrowserUiState(),
                    githubBrowser = GithubBrowserUiState(),
                    onOpenDriveFolder = { _, _ -> },
                    onCreateDriveFolder = {},
                    onLoadGithubRepos = {},
                    onSavePair = { savedPair = it },
                    onNavigateBack = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pair_name_input").performTextInput("Proyek Kerja")
        composeRule.onNodeWithTag("policy_safe_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_pair_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedPair)
        assertEquals("Proyek Kerja", savedPair?.name)
        assertEquals(DeletePolicy.SAFE_NO_DELETE, savedPair?.resolvedDeletePolicy)
    }

    @Test
    fun driveSyncSheet_rendersStatusAndTriggersSyncNow() {
        var syncClicked = false
        var chosenInterval = 15

        composeRule.setContent {
            SyncBebasTheme {
                DriveSyncConnectedSheetContent(
                    userEmail = "pribadi@gmail.com",
                    selectedIntervalMinutes = chosenInterval,
                    lastSyncedAtMillis = System.currentTimeMillis(),
                    isSyncing = false,
                    onSelectInterval = { chosenInterval = it },
                    onDisconnect = {},
                    onSyncNow = { syncClicked = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("drive_sync_status_card").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_interval_5").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_interval_15").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_interval_240").assertIsDisplayed().performClick()
        assertEquals(240, chosenInterval)
        composeRule.onNodeWithTag("sync_interval_1440").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_sync_now_button").performClick()
        assertTrue(syncClicked)
    }
}
