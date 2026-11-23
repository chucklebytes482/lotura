
package com.example.sync

import com.example.data.db.SyncPairEntity
import com.example.data.SyncRepository
import com.example.drive.DriveAuthExpiredException
import com.example.drive.DriveMimeTypes
import com.example.drive.DriveRemote
import com.example.github.GithubApiClient
import com.example.github.GithubRemote
import com.example.github.GithubTreeChange
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class SyncRunResult(
    val success: Boolean,
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val summary: String,
    val error: String? = null
)

/**
 * Sinkronisasi sederhana dengan dua aksi terpisah per pasangan:
 *  - Download: setiap berkas sumber menimpa berkas lokal dengan nama sama.
 *  - Upload:   setiap berkas lokal menimpa berkas sumber dengan nama sama.
 * Tanpa pengecekan waktu, tanpa hash, tanpa berkas konflik, dan tanpa penghapusan.
 */
class SyncExecutor(
    private val repository: SyncRepository,
    private val driveRemote: DriveRemote,
    private val githubRemote: GithubRemote
) {
    private val pairLocks = ConcurrentHashMap<Long, Mutex>()

    private fun lockFor(pairId: Long): Mutex = pairLocks.computeIfAbsent(pairId) { Mutex() }

    // ---------------------------------------------------------------- Google Drive

    suspend fun downloadFromDrive(pairId: Long, accessToken: String): SyncRunResult =
        runDriveAction(pairId, accessToken) { pair, rootDir, ignoreRules ->
            val remoteFiles = driveRemote.listFilesRecursive(accessToken, pair.driveFolderId)
                .filterNot { ignoreRules.isIgnored(it.relativePath, isDirectory = false) }

            var downloaded = 0
            for (remote in remoteFiles) {
                val dest = File(rootDir, remote.relativePath)
                dest.parentFile?.mkdirs()
                val exportSpec = remote.nativeWorkspaceMimeType?.let { DriveMimeTypes.exportSpecFor(it) }
                if (exportSpec != null) {
                    driveRemote.exportGoogleWorkspaceFile(
                        accessToken = accessToken,
                        fileId = remote.id,
                        exportMimeType = exportSpec.exportMimeType,
                        destFile = dest
                    )
                } else {
                    driveRemote.downloadFile(accessToken, remote.id, dest)
                }
                downloaded++
                repository.addLog(pairId, pair.name, "DRIVE", "DOWNLOAD", remote.relativePath, "OK", "Diunduh dari Google Drive")
            }

            val summary = if (downloaded == 0) "Drive • Tidak ada berkas untuk diunduh" else "Drive: ↓$downloaded"
            Outcome(uploaded = 0, downloaded = downloaded, summary = summary)
        }

    suspend fun uploadToDrive(pairId: Long, accessToken: String): SyncRunResult =
        runDriveAction(pairId, accessToken) { pair, rootDir, ignoreRules ->
            // Daftar ulang remote agar existingFileId terbaru dipakai untuk menimpa.
            val remoteById = driveRemote.listFilesRecursive(accessToken, pair.driveFolderId)
                .filterNot { ignoreRules.isIgnored(it.relativePath, isDirectory = false) }
                .associateBy { it.relativePath }

            var uploaded = 0
            val localFiles = LocalScanner.scanDirectory(rootDir, ignoreRules)
            for (path in localFiles.keys) {
                val remote = remoteById[path]
                driveRemote.uploadFile(
                    accessToken = accessToken,
                    rootFolderId = pair.driveFolderId,
                    relativePath = path,
                    existingFileId = remote?.id,
                    sourceFile = File(rootDir, path),
                    nativeWorkspaceMimeType = remote?.nativeWorkspaceMimeType
                )
                uploaded++
                repository.addLog(pairId, pair.name, "DRIVE", "UPLOAD", path, "OK", "Diunggah ke Google Drive")
            }

            val summary = if (uploaded == 0) "Drive • Tidak ada berkas untuk diunggah" else "Drive: ↑$uploaded"
            Outcome(uploaded = uploaded, downloaded = 0, summary = summary)
        }

    private data class Outcome(val uploaded: Int, val downloaded: Int, val summary: String)

    private suspend fun runDriveAction(
        pairId: Long,
        accessToken: String,
        block: suspend (SyncPairEntity, File, IgnoreRules) -> Outcome
    ): SyncRunResult {
        return lockFor(pairId).withLock {
            val pair = repository.getPairById(pairId)
                ?: return@withLock SyncRunResult(false, summary = "Pasangan folder tidak ditemukan", error = "Pasangan tidak ditemukan")

            val rootDir = File(pair.localPath)
            if (!rootDir.exists() && !rootDir.mkdirs()) {
                val msg = "Folder lokal tidak dapat diakses: ${pair.localPath}"
                repository.updateDriveStatus(pairId, pair.lastDriveSyncAt, "Gagal akses folder", msg)
                repository.addLog(pairId, pair.name, "DRIVE", "ERROR", "", "ERROR", msg)
                return@withLock SyncRunResult(false, summary = msg, error = msg)
            }

            if (pair.driveFolderId.isBlank()) {
                val msg = "Folder Google Drive tujuan belum dipilih."
                repository.updateDriveStatus(pairId, pair.lastDriveSyncAt, msg, msg)
                return@withLock SyncRunResult(false, summary = msg, error = msg)
            }

            try {
                val outcome = block(pair, rootDir, IgnoreRules(pair.ignorePatterns))
                val now = System.currentTimeMillis()
                repository.updateDriveStatus(pairId, now, outcome.summary, null)
                SyncRunResult(
                    success = true,
                    uploaded = outcome.uploaded,
                    downloaded = outcome.downloaded,
                    summary = outcome.summary
                )
            } catch (authEx: DriveAuthExpiredException) {
                val msg = authEx.message ?: "Otorisasi Google Drive kedaluwarsa"
                repository.updateDriveStatus(pairId, pair.lastDriveSyncAt, "Perlu otorisasi Google", msg)
                repository.addLog(pairId, pair.name, "DRIVE", "ERROR", "", "ERROR", msg)
                SyncRunResult(false, summary = msg, error = msg)
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Gagal sinkronisasi Google Drive"
                repository.updateDriveStatus(pairId, pair.lastDriveSyncAt, "Galat Drive", msg)
                repository.addLog(pairId, pair.name, "DRIVE", "ERROR", "", "ERROR", msg)
                SyncRunResult(false, summary = msg, error = msg)
            }
        }
    }

    // ---------------------------------------------------------------- GitHub

    suspend fun downloadFromGithub(pairId: Long): SyncRunResult =
        runGithubAction(pairId) { pair, rootDir, pat, ignoreRules ->
            val branch = pair.githubBranch.ifBlank { "main" }
            val (_, rootTreeSha) = githubRemote.getBranchHeadCommit(
                pat = pat,
                owner = pair.githubOwner,
                repo = pair.githubRepo,
                branch = branch
            )
            val remoteBlobs = githubRemote.listBlobsInSubfolder(
                pat = pat,
                owner = pair.githubOwner,
                repo = pair.githubRepo,
                treeSha = rootTreeSha,
                subfolder = pair.githubSubfolder
            ).filterNot { ignoreRules.isIgnored(it.relativePath, isDirectory = false) }

            var downloaded = 0
            for (blob in remoteBlobs) {
                val dest = File(rootDir, blob.relativePath)
                dest.parentFile?.mkdirs()
                githubRemote.downloadBlobToFile(pat, pair.githubOwner, pair.githubRepo, blob.sha, dest)
                downloaded++
                repository.addLog(pairId, pair.name, "GITHUB", "DOWNLOAD", blob.relativePath, "OK", "Ditarik dari GitHub")
            }

            val summary = if (downloaded == 0) "GitHub • Tidak ada berkas untuk diunduh" else "GitHub: ↓$downloaded"
            Outcome(uploaded = 0, downloaded = downloaded, summary = summary)
        }

    suspend fun uploadToGithub(pairId: Long): SyncRunResult =
        runGithubAction(pairId) { pair, rootDir, pat, ignoreRules ->
            val branch = pair.githubBranch.ifBlank { "main" }
            val (headCommitSha, rootTreeSha) = githubRemote.getBranchHeadCommit(
                pat = pat,
                owner = pair.githubOwner,
                repo = pair.githubRepo,
                branch = branch
            )

            val cleanSub = pair.githubSubfolder.trim().trim('/')
            fun toRepoPath(rel: String): String = if (cleanSub.isEmpty()) rel else "$cleanSub/$rel"

            var uploaded = 0
            val localFiles = LocalScanner.scanDirectory(rootDir, ignoreRules)
            val changes = mutableListOf<GithubTreeChange>()
            for (path in localFiles.keys) {
                val file = File(rootDir, path)
                if (file.length() > GithubApiClient.MAX_GITHUB_FILE_BYTES) {
                    repository.addLog(
                        pairId, pair.name, "GITHUB", "SKIP", path, "SKIP",
                        "Melewati file > 45 MB (batas aman GitHub)"
                    )
                    continue
                }
                val blobSha = githubRemote.createBlobFromFile(pat, pair.githubOwner, pair.githubRepo, file)
                changes.add(GithubTreeChange(toRepoPath(path), blobSha))
                uploaded++
                repository.addLog(pairId, pair.name, "GITHUB", "UPLOAD", path, "OK", "Diunggah ke GitHub")
            }

            if (changes.isNotEmpty()) {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                val commitSha = githubRemote.createAtomicCommit(
                    pat = pat,
                    owner = pair.githubOwner,
                    repo = pair.githubRepo,
                    branch = branch,
                    parentCommitSha = headCommitSha,
                    baseTreeSha = rootTreeSha,
                    changes = changes,
                    message = "Lotura: ${pair.name} ($uploaded berkas) [$stamp]"
                )
                repository.addLog(pairId, pair.name, "GITHUB", "COMMIT", "", "OK", "Commit ${commitSha.take(7)} berhasil ($uploaded file)")
            }

            val summary = if (uploaded == 0) "GitHub • Tidak ada berkas untuk diunggah" else "GitHub: ↑$uploaded"
            Outcome(uploaded = uploaded, downloaded = 0, summary = summary)
        }

    private suspend fun runGithubAction(
        pairId: Long,
        block: suspend (SyncPairEntity, File, String, IgnoreRules) -> Outcome
    ): SyncRunResult {
        return lockFor(pairId).withLock {
            val pair = repository.getPairById(pairId)
                ?: return@withLock SyncRunResult(false, summary = "Pasangan tidak ditemukan", error = "Pasangan tidak ditemukan")

            if (!pair.githubEnabled || pair.githubAccountId == null ||
                pair.githubOwner.isBlank() || pair.githubRepo.isBlank()
            ) {
                return@withLock SyncRunResult(true, summary = "GitHub tidak aktif untuk pasangan ini")
            }

            if (pair.githubSubfolder.trim().trim('/').isBlank()) {
                val msg = "Pilih 1 folder spesifik di repositori GitHub terlebih dahulu."
                repository.updateGithubStatus(pairId, pair.lastGithubSyncAt, msg, msg)
                repository.addLog(pairId, pair.name, "GITHUB", "ERROR", "", "ERROR", msg)
                return@withLock SyncRunResult(false, summary = msg, error = msg)
            }

            val pat = repository.getGithubPat(pair.githubAccountId)
            if (pat.isNullOrBlank()) {
                val msg = "Token PAT GitHub untuk akun pasangan ini tidak ditemukan."
                repository.updateGithubStatus(pairId, pair.lastGithubSyncAt, "Token PAT hilang", msg)
                repository.addLog(pairId, pair.name, "GITHUB", "ERROR", "", "ERROR", msg)
                return@withLock SyncRunResult(false, summary = msg, error = msg)
            }

            val rootDir = File(pair.localPath)
            if (!rootDir.exists() && !rootDir.mkdirs()) {
                val msg = "Folder lokal tidak dapat diakses: ${pair.localPath}"
                repository.updateGithubStatus(pairId, pair.lastGithubSyncAt, "Gagal akses folder", msg)
                return@withLock SyncRunResult(false, summary = msg, error = msg)
            }

            try {
                val outcome = block(pair, rootDir, pat, IgnoreRules(pair.ignorePatterns))
                val now = System.currentTimeMillis()
                repository.updateGithubStatus(pairId, now, outcome.summary, null)
                SyncRunResult(
                    success = true,
                    uploaded = outcome.uploaded,
                    downloaded = outcome.downloaded,
                    summary = outcome.summary
                )
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Gagal sinkronisasi GitHub"
                repository.updateGithubStatus(pairId, pair.lastGithubSyncAt, "Galat GitHub", msg)
                repository.addLog(pairId, pair.name, "GITHUB", "ERROR", "", "ERROR", msg)
                SyncRunResult(false, summary = msg, error = msg)
            }
        }
    }
}
