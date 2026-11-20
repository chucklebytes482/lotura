package com.example

import com.example.data.db.DeletePolicy
import com.example.data.db.FileStateEntity
import com.example.drive.DriveFileItem
import com.example.drive.DriveMimeTypes
import com.example.github.GithubTreeBlob
import com.example.sync.DriveSyncOp
import com.example.sync.GithubSyncOp
import com.example.sync.LocalFileSnapshot
import com.example.sync.SyncPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlannerTest {

    private fun local(path: String, mtime: Long, hash: String, gitSha: String = hash) =
        LocalFileSnapshot(path, mtime, 100L, hash, gitSha)

    private fun remote(
        path: String,
        id: String,
        mtime: Long,
        md5: String? = null,
        nativeMime: String? = null
    ) = DriveFileItem(
        id = id,
        name = path.substringAfterLast('/'),
        relativePath = path,
        mimeType = nativeMime?.let { DriveMimeTypes.exportSpecFor(it)?.exportMimeType } ?: "text/markdown",
        modifiedTimeMillis = mtime,
        size = 100L,
        md5Checksum = md5,
        nativeWorkspaceMimeType = nativeMime
    )

    @Test
    fun drivePlan_uploadsNewLocalAndExportsNewGoogleDoc() {
        val ops = SyncPlanner.planDriveSync(
            localFiles = mapOf("baru.md" to local("baru.md", 10_000L, "h1")),
            remoteFiles = mapOf(
                "Catatan.docx" to remote(
                    path = "Catatan.docx",
                    id = "gdoc-1",
                    mtime = 20_000L,
                    nativeMime = DriveMimeTypes.GOOGLE_DOC
                )
            ),
            baseline = emptyMap(),
            deletePolicy = DeletePolicy.TWO_WAY
        )

        assertEquals(2, ops.size)
        assertTrue(ops.any { it is DriveSyncOp.UploadToDrive && it.relativePath == "baru.md" })
        assertTrue(
            ops.any {
                it is DriveSyncOp.ExportGoogleDocFromDrive &&
                    it.relativePath == "Catatan.docx" &&
                    it.exportMimeType == DriveMimeTypes.DOCX
            }
        )
    }

    @Test
    fun drivePlan_twoWayDeletesVsSafeNoDelete() {
        val base = mapOf(
            "hapus_di_drive.md" to FileStateEntity(
                pairId = 1,
                relativePath = "hapus_di_drive.md",
                localModifiedAt = 5_000L,
                localSize = 100L,
                localHash = "h1",
                driveFileId = "d-1",
                driveModifiedAt = 5_000L
            ),
            "hapus_di_lokal.md" to FileStateEntity(
                pairId = 1,
                relativePath = "hapus_di_lokal.md",
                localModifiedAt = 5_000L,
                localSize = 100L,
                localHash = "h2",
                driveFileId = "d-2",
                driveModifiedAt = 5_000L
            )
        )
        val locals = mapOf("hapus_di_drive.md" to local("hapus_di_drive.md", 5_000L, "h1"))
        val remotes = mapOf("hapus_di_lokal.md" to remote("hapus_di_lokal.md", "d-2", 5_000L, "h2"))

        val twoWayOps = SyncPlanner.planDriveSync(locals, remotes, base, DeletePolicy.TWO_WAY)
        assertTrue(twoWayOps.any { it is DriveSyncOp.DeleteLocal && it.relativePath == "hapus_di_drive.md" })
        assertTrue(twoWayOps.any { it is DriveSyncOp.TrashOnDrive && it.relativePath == "hapus_di_lokal.md" })

        val safeOps = SyncPlanner.planDriveSync(locals, remotes, base, DeletePolicy.SAFE_NO_DELETE)
        assertEquals(2, safeOps.count { it is DriveSyncOp.SkipDeleteProtected })
    }

    @Test
    fun drivePlan_conflictNewestTimestampWinsAndPreservesLoser() {
        val base = mapOf(
            "konflik_lokal.md" to FileStateEntity(1, "konflik_lokal.md", 1_000L, 100L, "base1", "d1", 1_000L),
            "konflik_drive.md" to FileStateEntity(1, "konflik_drive.md", 1_000L, 100L, "base2", "d2", 1_000L)
        )
        val locals = mapOf(
            "konflik_lokal.md" to local("konflik_lokal.md", 9_000L, "localNew"),
            "konflik_drive.md" to local("konflik_drive.md", 4_000L, "localOlder")
        )
        val remotes = mapOf(
            "konflik_lokal.md" to remote("konflik_lokal.md", "d1", 5_000L, "remoteOlder"),
            "konflik_drive.md" to remote("konflik_drive.md", "d2", 9_500L, "remoteNew")
        )

        val ops = SyncPlanner.planDriveSync(locals, remotes, base, DeletePolicy.TWO_WAY, nowMillis = 1_700_000_000_000L)
        val localWin = ops.filterIsInstance<DriveSyncOp.ConflictLocalWins>().single()
        assertEquals("konflik_lokal.md", localWin.relativePath)
        assertTrue(localWin.losingConflictPath.contains("(konflik "))

        val remoteWin = ops.filterIsInstance<DriveSyncOp.ConflictRemoteWins>().single()
        assertEquals("konflik_drive.md", remoteWin.relativePath)
        assertTrue(remoteWin.losingConflictPath.contains("(konflik "))
    }

    @Test
    fun githubPlan_stagesUploadsDownloadsDeletesAndSkipsIdenticalSha() {
        val base = mapOf(
            "sama.md" to FileStateEntity(1, "sama.md", 1000L, 10L, "m1", githubBlobSha = "sha-same"),
            "edit_lokal.md" to FileStateEntity(1, "edit_lokal.md", 1000L, 10L, "m2", githubBlobSha = "sha-old"),
            "hapus_lokal.md" to FileStateEntity(1, "hapus_lokal.md", 1000L, 10L, "m3", githubBlobSha = "sha-del")
        )
        val locals = mapOf(
            "sama.md" to local("sama.md", 2000L, "m1", gitSha = "sha-same"),
            "edit_lokal.md" to local("edit_lokal.md", 3000L, "m2-new", gitSha = "sha-new")
        )
        val remoteBlobs = mapOf(
            "sama.md" to GithubTreeBlob("sama.md", "sama.md", "sha-same", 10L),
            "edit_lokal.md" to GithubTreeBlob("edit_lokal.md", "edit_lokal.md", "sha-old", 10L),
            "hapus_lokal.md" to GithubTreeBlob("hapus_lokal.md", "hapus_lokal.md", "sha-del", 10L),
            "baru_gh.md" to GithubTreeBlob("baru_gh.md", "baru_gh.md", "sha-remote-new", 25L)
        )

        val ops = SyncPlanner.planGithubSync(locals, remoteBlobs, base, DeletePolicy.TWO_WAY)
        assertTrue(ops.none { it.relativePath == "sama.md" })
        assertTrue(ops.any { it is GithubSyncOp.StageUploadToGithub && it.relativePath == "edit_lokal.md" })
        assertTrue(ops.any { it is GithubSyncOp.StageDeleteFromGithub && it.relativePath == "hapus_lokal.md" })
        assertTrue(ops.any { it is GithubSyncOp.DownloadFromGithub && it.relativePath == "baru_gh.md" })
    }
}
