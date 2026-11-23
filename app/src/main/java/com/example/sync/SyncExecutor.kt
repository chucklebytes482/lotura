package com.example.sync

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
 * Sinkronisasi sederhana, dua langkah berurutan:
 *  1. ↓ Download: setiap berkas sumber menimpa berkas lokal dengan nama sama.
 *  2. ↑ Upload:   setiap berkas lokal menimpa berkas sumber dengan nama sama.
 * Tanpa pengecekan waktu, tanpa hash, tanpa berkas konflik, dan tanpa penghapusan.
 */
class SyncExecutor(
    private val repository: SyncRepository,
    private val driveRemote: DriveRemote,
    private val githubRemote: GithubRemote
) {
    private val pairLocks = ConcurrentHashMap<Long, Mutex>()

    private fun lockFor(pairId: Long): Mutex = pairLocks.computeIfAbsent(pairId) { Mutex() }

    suspend fun syncPairWithDrive(pairId: Long, accessToken: String): SyncRunResult {
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
                val ignoreRules = IgnoreRules(pair.ignorePatterns)
                val remoteFiles = driveRemote.listFilesRecursive(accessToken, pair.driveFolderId)
                    .filterNot { ignoreRules.isIgnored(it.relativePath, isDirectory = false) }

                var downloaded = 0
                var uploaded = 0

                // ↓ Download: timpa berkas lokal dengan nama sama
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

                // ↑ Upload: timpa berkas Drive dengan nama sama
                val remoteById = remoteFiles.associateBy { it.relativePath }
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

                val now = System.currentTimeMillis()
                val summary = if (uploaded + downloaded == 0) {
                    "Drive selaras • Tidak ada berkas"
                } else {
                    "Drive: ↑$uploaded ↓$downloaded"
                }
                repository.updateDriveStatus(pairId, now, summary, null)
                SyncRunResult(success = true, uploaded = uploaded, downloaded = downloaded, summary = summary)
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

    suspend fun syncPairWithGithub(pairId: Long): SyncRunResult {
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
                val ignoreRules = IgnoreRules(pair.ignorePatterns)
                val branch = pair.githubBranch.ifBlank { "main" }
                val (headCommitSha, rootTreeSha) = githubRemote.getBranchHeadCommit(
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
                var uploaded = 0

                // ↓ Download: timpa berkas lokal dengan nama sama
                for (blob in remoteBlobs) {
                    val dest = File(rootDir, blob.relativePath)
                    dest.parentFile?.mkdirs()
                    githubRemote.downloadBlobToFile(pat, pair.githubOwner, pair.githubRepo, blob.sha, dest)
                    downloaded++
                    repository.addLog(pairId, pair.name, "GITHUB", "DOWNLOAD", blob.relativePath, "OK", "Ditarik dari GitHub")
                }

                // ↑ Upload: timpa berkas GitHub dengan nama sama, dalam 1 commit
                val cleanSub = pair.githubSubfolder.trim().trim('/')
                fun toRepoPath(rel: String): String = if (cleanSub.isEmpty()) rel else "$cleanSub/$rel"

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

                val now = System.currentTimeMillis()
                val summary = if (uploaded + downloaded == 0) {
                    "GitHub selaras • Tidak ada berkas"
                } else {
                    "GitHub: ↑$uploaded ↓$downloaded"
                }
                repository.updateGithubStatus(pairId, now, summary, null)
                SyncRunResult(success = true, uploaded = uploaded, downloaded = downloaded, summary = summary)
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Gagal sinkronisasi GitHub"
                repository.updateGithubStatus(pairId, pair.lastGithubSyncAt, "Galat GitHub", msg)
                repository.addLog(pairId, pair.name, "GITHUB", "ERROR", "", "ERROR", msg)
                SyncRunResult(false, summary = msg, error = msg)
            }
        }
    }
}
