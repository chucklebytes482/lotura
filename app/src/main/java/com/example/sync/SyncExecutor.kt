package com.example.sync

import com.example.data.SyncRepository
import com.example.data.db.FileStateEntity
import com.example.drive.DriveAuthExpiredException
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
    val exportedDocs: Int = 0,
    val deleted: Int = 0,
    val conflicts: Int = 0,
    val summary: String,
    val error: String? = null
)

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
                val localFiles = LocalScanner.scanDirectory(rootDir, ignoreRules)
                val remoteList = driveRemote.listFilesRecursive(accessToken, pair.driveFolderId)
                    .filterNot { ignoreRules.isIgnored(it.relativePath, isDirectory = false) }
                val remoteMap = remoteList.associateBy { it.relativePath }
                val baselineMap = repository.getFileStates(pairId).associateBy { it.relativePath }

                val ops = SyncPlanner.planDriveSync(
                    localFiles = localFiles,
                    remoteFiles = remoteMap,
                    baseline = baselineMap,
                    deletePolicy = com.example.data.db.DeletePolicy.TWO_WAY
                )

                var uploaded = 0
                var downloaded = 0
                var exportedDocs = 0
                var deleted = 0
                var conflicts = 0

                for (op in ops) {
                    val localFile = File(rootDir, op.relativePath)
                    when (op) {
                        is DriveSyncOp.UploadToDrive -> {
                            val remoteItem = driveRemote.uploadFile(
                                accessToken = accessToken,
                                rootFolderId = pair.driveFolderId,
                                relativePath = op.relativePath,
                                existingFileId = op.existingDriveFileId,
                                sourceFile = localFile,
                                nativeWorkspaceMimeType = op.nativeWorkspaceMimeType
                            )
                            val hashes = LocalScanner.computeHashes(localFile)
                            val prev = baselineMap[op.relativePath]
                            repository.upsertFileState(
                                FileStateEntity(
                                    pairId = pairId,
                                    relativePath = op.relativePath,
                                    localModifiedAt = localFile.lastModified(),
                                    localSize = localFile.length(),
                                    localHash = hashes.first,
                                    driveFileId = remoteItem.id,
                                    driveModifiedAt = remoteItem.modifiedTimeMillis,
                                    driveNativeMimeType = remoteItem.nativeWorkspaceMimeType,
                                    githubBlobSha = prev?.githubBlobSha,
                                    lastSyncedAt = System.currentTimeMillis()
                                )
                            )
                            uploaded++
                            repository.addLog(pairId, pair.name, "DRIVE", "UPLOAD", op.relativePath, "OK", "Diunggah ke Google Drive")
                        }

                        is DriveSyncOp.DownloadFromDrive -> {
                            driveRemote.downloadFile(accessToken, op.remote.id, localFile)
                            if (op.remote.modifiedTimeMillis > 0L) {
                                localFile.setLastModified(op.remote.modifiedTimeMillis)
                            }
                            val hashes = LocalScanner.computeHashes(localFile)
                            val prev = baselineMap[op.relativePath]
                            repository.upsertFileState(
                                FileStateEntity(
                                    pairId = pairId,
                                    relativePath = op.relativePath,
                                    localModifiedAt = localFile.lastModified(),
                                    localSize = localFile.length(),
                                    localHash = hashes.first,
                                    driveFileId = op.remote.id,
                                    driveModifiedAt = op.remote.modifiedTimeMillis,
                                    driveNativeMimeType = null,
                                    githubBlobSha = prev?.githubBlobSha,
                                    lastSyncedAt = System.currentTimeMillis()
                                )
                            )
                            downloaded++
                            repository.addLog(pairId, pair.name, "DRIVE", "DOWNLOAD", op.relativePath, "OK", "Diunduh dari Google Drive")
                        }

                        is DriveSyncOp.ExportGoogleDocFromDrive -> {
                            driveRemote.exportGoogleWorkspaceFile(
                                accessToken = accessToken,
                                fileId = op.remote.id,
                                exportMimeType = op.exportMimeType,
                                destFile = localFile
                            )
                            if (op.remote.modifiedTimeMillis > 0L) {
                                localFile.setLastModified(op.remote.modifiedTimeMillis)
                            }
                            val hashes = LocalScanner.computeHashes(localFile)
                            val prev = baselineMap[op.relativePath]
                            repository.upsertFileState(
                                FileStateEntity(
                                    pairId = pairId,
                                    relativePath = op.relativePath,
                                    localModifiedAt = localFile.lastModified(),
                                    localSize = localFile.length(),
                                    localHash = hashes.first,
                                    driveFileId = op.remote.id,
                                    driveModifiedAt = op.remote.modifiedTimeMillis,
                                    driveNativeMimeType = op.remote.nativeWorkspaceMimeType,
                                    githubBlobSha = prev?.githubBlobSha,
                                    lastSyncedAt = System.currentTimeMillis()
                                )
                            )
                            exportedDocs++
                            repository.addLog(
                                pairId,
                                pair.name,
                                "DRIVE",
                                "EXPORT_DOCS",
                                op.relativePath,
                                "OK",
                                "Diekspor otomatis dari Google Docs/Sheets/Slides"
                            )
                        }

                        is DriveSyncOp.TrashOnDrive -> {
                            driveRemote.trashFile(accessToken, op.driveFileId)
                            repository.deleteFileState(pairId, op.relativePath)
                            deleted++
                            repository.addLog(pairId, pair.name, "DRIVE", "DELETE", op.relativePath, "OK", "Dipindahkan ke Sampah Drive")
                        }

                        is DriveSyncOp.DeleteLocal -> {
                            if (localFile.exists()) localFile.delete()
                            repository.deleteFileState(pairId, op.relativePath)
                            deleted++
                            repository.addLog(pairId, pair.name, "DRIVE", "DELETE", op.relativePath, "OK", "Dihapus lokal mengikuti Drive")
                        }

                        is DriveSyncOp.ConflictLocalWins -> {
                            // Save losing remote version as conflict file
                            val conflictFile = File(rootDir, op.losingConflictPath)
                            val nativeMime = op.remote.nativeWorkspaceMimeType
                            if (nativeMime != null) {
                                driveRemote.exportGoogleWorkspaceFile(accessToken, op.remote.id, op.remote.mimeType, conflictFile)
                            } else {
                                driveRemote.downloadFile(accessToken, op.remote.id, conflictFile)
                            }
                            // Upload winning local version
                            val uploadedRemote = driveRemote.uploadFile(
                                accessToken = accessToken,
                                rootFolderId = pair.driveFolderId,
                                relativePath = op.relativePath,
                                existingFileId = op.remote.id,
                                sourceFile = localFile,
                                nativeWorkspaceMimeType = op.remote.nativeWorkspaceMimeType
                            )
                            val hashes = LocalScanner.computeHashes(localFile)
                            repository.upsertFileState(
                                FileStateEntity(
                                    pairId = pairId,
                                    relativePath = op.relativePath,
                                    localModifiedAt = localFile.lastModified(),
                                    localSize = localFile.length(),
                                    localHash = hashes.first,
                                    driveFileId = uploadedRemote.id,
                                    driveModifiedAt = uploadedRemote.modifiedTimeMillis,
                                    driveNativeMimeType = uploadedRemote.nativeWorkspaceMimeType,
                                    githubBlobSha = baselineMap[op.relativePath]?.githubBlobSha,
                                    lastSyncedAt = System.currentTimeMillis()
                                )
                            )
                            conflicts++
                            uploaded++
                            repository.addLog(
                                pairId,
                                pair.name,
                                "DRIVE",
                                "CONFLICT",
                                op.relativePath,
                                "CONFLICT",
                                "Lokal lebih baru menang; versi Drive disimpan sebagai ${op.losingConflictPath}"
                            )
                        }

                        is DriveSyncOp.ConflictRemoteWins -> {
                            // Move losing local file to conflict copy
                            val conflictFile = File(rootDir, op.losingConflictPath)
                            conflictFile.parentFile?.mkdirs()
                            if (localFile.exists()) {
                                localFile.copyTo(conflictFile, overwrite = true)
                            }
                            // Download/export winning remote version
                            val nativeMime = op.remote.nativeWorkspaceMimeType
                            if (nativeMime != null) {
                                driveRemote.exportGoogleWorkspaceFile(accessToken, op.remote.id, op.remote.mimeType, localFile)
                            } else {
                                driveRemote.downloadFile(accessToken, op.remote.id, localFile)
                            }
                            if (op.remote.modifiedTimeMillis > 0L) {
                                localFile.setLastModified(op.remote.modifiedTimeMillis)
                            }
                            val hashes = LocalScanner.computeHashes(localFile)
                            repository.upsertFileState(
                                FileStateEntity(
                                    pairId = pairId,
                                    relativePath = op.relativePath,
                                    localModifiedAt = localFile.lastModified(),
                                    localSize = localFile.length(),
                                    localHash = hashes.first,
                                    driveFileId = op.remote.id,
                                    driveModifiedAt = op.remote.modifiedTimeMillis,
                                    driveNativeMimeType = op.remote.nativeWorkspaceMimeType,
                                    githubBlobSha = baselineMap[op.relativePath]?.githubBlobSha,
                                    lastSyncedAt = System.currentTimeMillis()
                                )
                            )
                            conflicts++
                            downloaded++
                            repository.addLog(
                                pairId,
                                pair.name,
                                "DRIVE",
                                "CONFLICT",
                                op.relativePath,
                                "CONFLICT",
                                "Drive lebih baru menang; versi lokal disimpan sebagai ${op.losingConflictPath}"
                            )
                        }

                        is DriveSyncOp.SkipDeleteProtected -> {
                            repository.addLog(pairId, pair.name, "DRIVE", "SKIP", op.relativePath, "SKIP", op.reason)
                        }
                    }
                }

                // Refresh baseline for already-matching files that had no baseline yet
                for ((relPath, localSnap) in localFiles) {
                    val remoteSnap = remoteMap[relPath]
                    if (remoteSnap != null && !baselineMap.containsKey(relPath) &&
                        !remoteSnap.md5Checksum.isNullOrBlank() &&
                        remoteSnap.md5Checksum.equals(localSnap.contentHash, ignoreCase = true)
                    ) {
                        repository.upsertFileState(
                            FileStateEntity(
                                pairId = pairId,
                                relativePath = relPath,
                                localModifiedAt = localSnap.modifiedAtMillis,
                                localSize = localSnap.size,
                                localHash = localSnap.contentHash,
                                driveFileId = remoteSnap.id,
                                driveModifiedAt = remoteSnap.modifiedTimeMillis,
                                driveNativeMimeType = remoteSnap.nativeWorkspaceMimeType,
                                lastSyncedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                val now = System.currentTimeMillis()
                val summary = if (uploaded + downloaded + exportedDocs + deleted + conflicts == 0) {
                    "Drive selaras • Tidak ada perubahan"
                } else {
                    "Drive: ↑$uploaded ↓$downloaded Ekspor:$exportedDocs Hapus:$deleted Konflik:$conflicts"
                }
                repository.updateDriveStatus(pairId, now, summary, null)
                SyncRunResult(
                    success = true,
                    uploaded = uploaded,
                    downloaded = downloaded,
                    exportedDocs = exportedDocs,
                    deleted = deleted,
                    conflicts = conflicts,
                    summary = summary
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
                val localFiles = LocalScanner.scanDirectory(rootDir, ignoreRules)
                val (headCommitSha, rootTreeSha) = githubRemote.getBranchHeadCommit(
                    pat = pat,
                    owner = pair.githubOwner,
                    repo = pair.githubRepo,
                    branch = pair.githubBranch.ifBlank { "main" }
                )
                val remoteBlobs = githubRemote.listBlobsInSubfolder(
                    pat = pat,
                    owner = pair.githubOwner,
                    repo = pair.githubRepo,
                    treeSha = rootTreeSha,
                    subfolder = pair.githubSubfolder
                ).filterNot { ignoreRules.isIgnored(it.relativePath, isDirectory = false) }
                val remoteMap = remoteBlobs.associateBy { it.relativePath }
                val baselineMap = repository.getFileStates(pairId).associateBy { it.relativePath }

                val ops = SyncPlanner.planGithubSync(
                    localFiles = localFiles,
                    remoteBlobs = remoteMap,
                    baseline = baselineMap,
                    deletePolicy = com.example.data.db.DeletePolicy.TWO_WAY
                )

                val cleanSub = pair.githubSubfolder.trim().trim('/')
                fun toRepoPath(rel: String): String = if (cleanSub.isEmpty()) rel else "$cleanSub/$rel"

                val stagedChanges = mutableListOf<GithubTreeChange>()
                val uploadedShaMap = mutableMapOf<String, String>()
                val deletedPaths = mutableListOf<String>()

                var uploaded = 0
                var downloaded = 0
                var deleted = 0
                var conflicts = 0

                for (op in ops) {
                    val localFile = File(rootDir, op.relativePath)
                    when (op) {
                        is GithubSyncOp.StageUploadToGithub -> {
                            if (localFile.length() > GithubApiClient.MAX_GITHUB_FILE_BYTES) {
                                repository.addLog(
                                    pairId, pair.name, "GITHUB", "SKIP", op.relativePath, "SKIP",
                                    "Melewati file > 45 MB (batas aman GitHub)"
                                )
                                continue
                            }
                            val blobSha = githubRemote.createBlobFromFile(pat, pair.githubOwner, pair.githubRepo, localFile)
                            stagedChanges.add(GithubTreeChange(toRepoPath(op.relativePath), blobSha))
                            uploadedShaMap[op.relativePath] = blobSha
                            uploaded++
                        }

                        is GithubSyncOp.StageDeleteFromGithub -> {
                            stagedChanges.add(GithubTreeChange(toRepoPath(op.relativePath), null))
                            deletedPaths.add(op.relativePath)
                            deleted++
                        }

                        is GithubSyncOp.DownloadFromGithub -> {
                            githubRemote.downloadBlobToFile(pat, pair.githubOwner, pair.githubRepo, op.blob.sha, localFile)
                            val hashes = LocalScanner.computeHashes(localFile)
                            val prev = baselineMap[op.relativePath]
                            repository.upsertFileState(
                                FileStateEntity(
                                    pairId = pairId,
                                    relativePath = op.relativePath,
                                    localModifiedAt = localFile.lastModified(),
                                    localSize = localFile.length(),
                                    localHash = hashes.first,
                                    driveFileId = prev?.driveFileId,
                                    driveModifiedAt = prev?.driveModifiedAt ?: 0L,
                                    driveNativeMimeType = prev?.driveNativeMimeType,
                                    githubBlobSha = op.blob.sha,
                                    lastSyncedAt = System.currentTimeMillis()
                                )
                            )
                            downloaded++
                            repository.addLog(pairId, pair.name, "GITHUB", "DOWNLOAD", op.relativePath, "OK", "Ditarik dari GitHub")
                        }

                        is GithubSyncOp.DeleteLocalFromGithub -> {
                            if (localFile.exists()) localFile.delete()
                            repository.deleteFileState(pairId, op.relativePath)
                            deleted++
                            repository.addLog(pairId, pair.name, "GITHUB", "DELETE", op.relativePath, "OK", "Dihapus lokal mengikuti GitHub")
                        }

                        is GithubSyncOp.ConflictBackupLocalAndPull -> {
                            val conflictFile = File(rootDir, op.losingConflictPath)
                            conflictFile.parentFile?.mkdirs()
                            if (localFile.exists()) {
                                localFile.copyTo(conflictFile, overwrite = true)
                            }
                            githubRemote.downloadBlobToFile(pat, pair.githubOwner, pair.githubRepo, op.blob.sha, localFile)
                            val hashes = LocalScanner.computeHashes(localFile)
                            val prev = baselineMap[op.relativePath]
                            repository.upsertFileState(
                                FileStateEntity(
                                    pairId = pairId,
                                    relativePath = op.relativePath,
                                    localModifiedAt = localFile.lastModified(),
                                    localSize = localFile.length(),
                                    localHash = hashes.first,
                                    driveFileId = prev?.driveFileId,
                                    driveModifiedAt = prev?.driveModifiedAt ?: 0L,
                                    driveNativeMimeType = prev?.driveNativeMimeType,
                                    githubBlobSha = op.blob.sha,
                                    lastSyncedAt = System.currentTimeMillis()
                                )
                            )
                            conflicts++
                            downloaded++
                            repository.addLog(
                                pairId, pair.name, "GITHUB", "CONFLICT", op.relativePath, "CONFLICT",
                                "Konflik GitHub: versi lokal diamankan ke ${op.losingConflictPath}"
                            )
                        }

                        is GithubSyncOp.SkipDeleteProtected -> {
                            repository.addLog(pairId, pair.name, "GITHUB", "SKIP", op.relativePath, "SKIP", op.reason)
                        }
                    }
                }

                // Commit all staged uploads & deletions atomically in 1 single GitHub commit
                if (stagedChanges.isNotEmpty()) {
                    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                    val commitMessage = "Lotura: ${pair.name} ($uploaded ubah, ${deletedPaths.size} hapus) [$stamp]"
                    val newCommitSha = githubRemote.createAtomicCommit(
                        pat = pat,
                        owner = pair.githubOwner,
                        repo = pair.githubRepo,
                        branch = pair.githubBranch.ifBlank { "main" },
                        parentCommitSha = headCommitSha,
                        baseTreeSha = rootTreeSha,
                        changes = stagedChanges,
                        message = commitMessage
                    )
                    for ((relPath, blobSha) in uploadedShaMap) {
                        val file = File(rootDir, relPath)
                        val hashes = if (file.exists()) LocalScanner.computeHashes(file) else "" to blobSha
                        val prev = baselineMap[relPath]
                        repository.upsertFileState(
                            FileStateEntity(
                                pairId = pairId,
                                relativePath = relPath,
                                localModifiedAt = file.lastModified(),
                                localSize = file.length(),
                                localHash = hashes.first,
                                driveFileId = prev?.driveFileId,
                                driveModifiedAt = prev?.driveModifiedAt ?: 0L,
                                driveNativeMimeType = prev?.driveNativeMimeType,
                                githubBlobSha = blobSha,
                                lastSyncedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    for (relPath in deletedPaths) {
                        repository.deleteFileState(pairId, relPath)
                    }
                    repository.addLog(
                        pairId, pair.name, "GITHUB", "COMMIT", "", "OK",
                        "Commit ${newCommitSha.take(7)} berhasil ($uploaded file, ${deletedPaths.size} hapus)"
                    )
                }

                // Update baseline githubBlobSha for already identical files
                for ((relPath, localSnap) in localFiles) {
                    val remoteBlob = remoteMap[relPath]
                    if (remoteBlob != null && remoteBlob.sha == localSnap.gitBlobSha) {
                        val prev = baselineMap[relPath]
                        if (prev == null || prev.githubBlobSha != remoteBlob.sha) {
                            repository.upsertFileState(
                                FileStateEntity(
                                    pairId = pairId,
                                    relativePath = relPath,
                                    localModifiedAt = localSnap.modifiedAtMillis,
                                    localSize = localSnap.size,
                                    localHash = localSnap.contentHash,
                                    driveFileId = prev?.driveFileId,
                                    driveModifiedAt = prev?.driveModifiedAt ?: 0L,
                                    driveNativeMimeType = prev?.driveNativeMimeType,
                                    githubBlobSha = remoteBlob.sha,
                                    lastSyncedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }

                val now = System.currentTimeMillis()
                val summary = if (uploaded + downloaded + deleted + conflicts == 0) {
                    "GitHub selaras • Tidak ada perubahan"
                } else {
                    "GitHub: ↑$uploaded ↓$downloaded Hapus:$deleted Konflik:$conflicts"
                }
                repository.updateGithubStatus(pairId, now, summary, null)
                SyncRunResult(
                    success = true,
                    uploaded = uploaded,
                    downloaded = downloaded,
                    deleted = deleted,
                    conflicts = conflicts,
                    summary = summary
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
