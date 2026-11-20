package com.example.sync

import com.example.data.db.DeletePolicy
import com.example.data.db.FileStateEntity
import com.example.drive.DriveFileItem
import com.example.drive.DriveMimeTypes
import com.example.github.GithubTreeBlob
import kotlin.math.abs

sealed interface DriveSyncOp {
    val relativePath: String

    data class UploadToDrive(
        override val relativePath: String,
        val existingDriveFileId: String?,
        val nativeWorkspaceMimeType: String?
    ) : DriveSyncOp

    data class DownloadFromDrive(
        override val relativePath: String,
        val remote: DriveFileItem
    ) : DriveSyncOp

    data class ExportGoogleDocFromDrive(
        override val relativePath: String,
        val remote: DriveFileItem,
        val exportMimeType: String
    ) : DriveSyncOp

    data class TrashOnDrive(
        override val relativePath: String,
        val driveFileId: String
    ) : DriveSyncOp

    data class DeleteLocal(
        override val relativePath: String
    ) : DriveSyncOp

    data class ConflictLocalWins(
        override val relativePath: String,
        val remote: DriveFileItem,
        val losingConflictPath: String
    ) : DriveSyncOp

    data class ConflictRemoteWins(
        override val relativePath: String,
        val remote: DriveFileItem,
        val losingConflictPath: String
    ) : DriveSyncOp

    data class SkipDeleteProtected(
        override val relativePath: String,
        val reason: String
    ) : DriveSyncOp
}

sealed interface GithubSyncOp {
    val relativePath: String

    data class StageUploadToGithub(override val relativePath: String) : GithubSyncOp
    data class DownloadFromGithub(override val relativePath: String, val blob: GithubTreeBlob) : GithubSyncOp
    data class StageDeleteFromGithub(override val relativePath: String) : GithubSyncOp
    data class DeleteLocalFromGithub(override val relativePath: String) : GithubSyncOp
    data class ConflictBackupLocalAndPull(
        override val relativePath: String,
        val blob: GithubTreeBlob,
        val losingConflictPath: String
    ) : GithubSyncOp
    data class SkipDeleteProtected(override val relativePath: String, val reason: String) : GithubSyncOp
}

/**
 * Pure deterministic 3-way sync planner for Google Drive and GitHub.
 * Zero Android runtime dependencies so every combination of create/edit/delete/conflict is unit-tested.
 */
object SyncPlanner {

    private const val TIME_TOLERANCE_MS = 2_000L

    fun planDriveSync(
        localFiles: Map<String, LocalFileSnapshot>,
        remoteFiles: Map<String, DriveFileItem>,
        baseline: Map<String, FileStateEntity>,
        deletePolicy: DeletePolicy,
        nowMillis: Long = System.currentTimeMillis()
    ): List<DriveSyncOp> {
        val allPaths = (localFiles.keys + remoteFiles.keys + baseline.keys).sorted()
        val ops = mutableListOf<DriveSyncOp>()

        for (path in allPaths) {
            val local = localFiles[path]
            val remote = remoteFiles[path]
            val base = baseline[path]

            when {
                // 1. Only local exists
                local != null && remote == null -> {
                    val wasOnDrive = base?.driveFileId != null
                    if (wasOnDrive) {
                        val localChanged = isLocalModifiedSinceBase(local, base)
                        if (localChanged) {
                            // Local edited after remote deletion -> preserve user edit by re-uploading
                            ops.add(DriveSyncOp.UploadToDrive(path, null, null))
                        } else if (deletePolicy == DeletePolicy.TWO_WAY) {
                            ops.add(DriveSyncOp.DeleteLocal(path))
                        } else {
                            ops.add(
                                DriveSyncOp.SkipDeleteProtected(
                                    path,
                                    "Mode aman: file lokal dipertahankan meski dihapus di Drive"
                                )
                            )
                        }
                    } else {
                        ops.add(DriveSyncOp.UploadToDrive(path, null, null))
                    }
                }

                // 2. Only remote exists
                local == null && remote != null -> {
                    val wasLocal = base != null && base.localModifiedAt > 0L
                    if (wasLocal) {
                        val remoteChanged = isRemoteModifiedSinceBase(remote, base)
                        if (remoteChanged) {
                            // Remote updated after local deletion -> download newer remote
                            ops.add(downloadOrExportOp(path, remote))
                        } else if (deletePolicy == DeletePolicy.TWO_WAY) {
                            ops.add(DriveSyncOp.TrashOnDrive(path, remote.id))
                        } else {
                            ops.add(
                                DriveSyncOp.SkipDeleteProtected(
                                    path,
                                    "Mode aman: file Drive dipertahankan meski dihapus lokal"
                                )
                            )
                        }
                    } else {
                        ops.add(downloadOrExportOp(path, remote))
                    }
                }

                // 3. Both local and remote exist
                local != null && remote != null -> {
                    // Identical content via MD5 checksum -> already in sync
                    if (!remote.md5Checksum.isNullOrBlank() &&
                        remote.md5Checksum.equals(local.contentHash, ignoreCase = true)
                    ) {
                        continue
                    }

                    if (base == null) {
                        // First sync with both sides present and different content -> newest wins, loser saved as conflict
                        ops.add(resolveConflict(path, local, remote, nowMillis))
                    } else {
                        val localChanged = isLocalModifiedSinceBase(local, base)
                        val remoteChanged = isRemoteModifiedSinceBase(remote, base)

                        when {
                            !localChanged && !remoteChanged -> Unit
                            localChanged && !remoteChanged -> {
                                ops.add(
                                    DriveSyncOp.UploadToDrive(
                                        relativePath = path,
                                        existingDriveFileId = remote.id,
                                        nativeWorkspaceMimeType = remote.nativeWorkspaceMimeType
                                            ?: base.driveNativeMimeType
                                    )
                                )
                            }
                            !localChanged && remoteChanged -> {
                                ops.add(downloadOrExportOp(path, remote))
                            }
                            else -> {
                                // Both changed -> conflict resolution (newest wins)
                                ops.add(resolveConflict(path, local, remote, nowMillis))
                            }
                        }
                    }
                }

                // 4. Neither local nor remote exists -> stale baseline, handled during cleanup
                else -> Unit
            }
        }
        return ops
    }

    fun planGithubSync(
        localFiles: Map<String, LocalFileSnapshot>,
        remoteBlobs: Map<String, GithubTreeBlob>,
        baseline: Map<String, FileStateEntity>,
        deletePolicy: DeletePolicy,
        nowMillis: Long = System.currentTimeMillis()
    ): List<GithubSyncOp> {
        val allPaths = (localFiles.keys + remoteBlobs.keys + baseline.keys).sorted()
        val ops = mutableListOf<GithubSyncOp>()

        for (path in allPaths) {
            val local = localFiles[path]
            val remote = remoteBlobs[path]
            val base = baseline[path]
            val baseGhSha = base?.githubBlobSha

            when {
                // Only local exists
                local != null && remote == null -> {
                    if (baseGhSha != null) {
                        val localChanged = local.gitBlobSha != baseGhSha && isLocalModifiedSinceBase(local, base)
                        if (localChanged) {
                            ops.add(GithubSyncOp.StageUploadToGithub(path))
                        } else if (deletePolicy == DeletePolicy.TWO_WAY) {
                            ops.add(GithubSyncOp.DeleteLocalFromGithub(path))
                        } else {
                            ops.add(
                                GithubSyncOp.SkipDeleteProtected(
                                    path,
                                    "Mode aman: file lokal dipertahankan meski dihapus di GitHub"
                                )
                            )
                        }
                    } else {
                        ops.add(GithubSyncOp.StageUploadToGithub(path))
                    }
                }

                // Only GitHub remote exists
                local == null && remote != null -> {
                    if (baseGhSha != null) {
                        val remoteChanged = remote.sha != baseGhSha
                        if (remoteChanged) {
                            ops.add(GithubSyncOp.DownloadFromGithub(path, remote))
                        } else if (deletePolicy == DeletePolicy.TWO_WAY) {
                            ops.add(GithubSyncOp.StageDeleteFromGithub(path))
                        } else {
                            ops.add(
                                GithubSyncOp.SkipDeleteProtected(
                                    path,
                                    "Mode aman: file GitHub dipertahankan meski dihapus lokal"
                                )
                            )
                        }
                    } else {
                        ops.add(GithubSyncOp.DownloadFromGithub(path, remote))
                    }
                }

                // Both exist
                local != null && remote != null -> {
                    if (local.gitBlobSha == remote.sha) {
                        continue // Exact same content!
                    }
                    if (baseGhSha == null) {
                        // First time linking existing local + existing GitHub with different bytes
                        val conflictPath = ConflictNamer.buildConflictPath(path, nowMillis)
                        ops.add(GithubSyncOp.ConflictBackupLocalAndPull(path, remote, conflictPath))
                    } else {
                        val localChanged = local.gitBlobSha != baseGhSha
                        val remoteChanged = remote.sha != baseGhSha
                        when {
                            localChanged && !remoteChanged -> ops.add(GithubSyncOp.StageUploadToGithub(path))
                            !localChanged && remoteChanged -> ops.add(GithubSyncOp.DownloadFromGithub(path, remote))
                            localChanged && remoteChanged -> {
                                val conflictPath = ConflictNamer.buildConflictPath(path, nowMillis)
                                ops.add(GithubSyncOp.ConflictBackupLocalAndPull(path, remote, conflictPath))
                            }
                            else -> Unit
                        }
                    }
                }

                else -> Unit
            }
        }
        return ops
    }

    private fun downloadOrExportOp(path: String, remote: DriveFileItem): DriveSyncOp {
        val nativeMime = remote.nativeWorkspaceMimeType
        if (nativeMime != null) {
            val spec = DriveMimeTypes.exportSpecFor(nativeMime)
            if (spec != null) {
                return DriveSyncOp.ExportGoogleDocFromDrive(
                    relativePath = path,
                    remote = remote,
                    exportMimeType = spec.exportMimeType
                )
            }
        }
        return DriveSyncOp.DownloadFromDrive(path, remote)
    }

    private fun resolveConflict(
        path: String,
        local: LocalFileSnapshot,
        remote: DriveFileItem,
        nowMillis: Long
    ): DriveSyncOp {
        val conflictPath = ConflictNamer.buildConflictPath(path, nowMillis)
        return if (local.modifiedAtMillis >= remote.modifiedTimeMillis) {
            DriveSyncOp.ConflictLocalWins(path, remote, conflictPath)
        } else {
            DriveSyncOp.ConflictRemoteWins(path, remote, conflictPath)
        }
    }

    private fun isLocalModifiedSinceBase(local: LocalFileSnapshot, base: FileStateEntity): Boolean {
        if (base.localHash.isNotBlank() && local.contentHash.isNotBlank()) {
            return !base.localHash.equals(local.contentHash, ignoreCase = true)
        }
        return abs(local.modifiedAtMillis - base.localModifiedAt) > TIME_TOLERANCE_MS ||
            local.size != base.localSize
    }

    private fun isRemoteModifiedSinceBase(remote: DriveFileItem, base: FileStateEntity): Boolean {
        if (base.driveFileId != null && base.driveFileId != remote.id) return true
        if (!remote.md5Checksum.isNullOrBlank() && base.localHash.isNotBlank()) {
            if (!remote.md5Checksum.equals(base.localHash, ignoreCase = true)) return true
        }
        return abs(remote.modifiedTimeMillis - base.driveModifiedAt) > TIME_TOLERANCE_MS
    }
}
