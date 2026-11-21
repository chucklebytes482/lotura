package com.example.github

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

interface GithubRemote {
    suspend fun getAuthenticatedUser(pat: String): GithubUserInfo
    suspend fun listRepositories(pat: String): List<GithubRepoSummary>
    suspend fun listFoldersInRepo(pat: String, owner: String, repo: String, branch: String): List<String>
    suspend fun getBranchHeadCommit(pat: String, owner: String, repo: String, branch: String): Pair<String, String>
    suspend fun listBlobsInSubfolder(
        pat: String,
        owner: String,
        repo: String,
        treeSha: String,
        subfolder: String
    ): List<GithubTreeBlob>
    suspend fun downloadBlobToFile(pat: String, owner: String, repo: String, blobSha: String, destFile: File)
    suspend fun createBlobFromFile(pat: String, owner: String, repo: String, file: File): String
    suspend fun createAtomicCommit(
        pat: String,
        owner: String,
        repo: String,
        branch: String,
        parentCommitSha: String,
        baseTreeSha: String,
        changes: List<GithubTreeChange>,
        message: String
    ): String
}

class GithubApiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) : GithubRemote {

    companion object {
        private const val BASE_URL = "https://api.github.com"
        private const val API_VERSION = "2022-11-28"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val MAX_GITHUB_FILE_BYTES = 45L * 1024L * 1024L // Safe limit below 50 MB warning / 100 MB hard cap
    }

    override suspend fun getAuthenticatedUser(pat: String): GithubUserInfo =
        withContext(Dispatchers.IO) {
            val req = baseRequest(pat, "$BASE_URL/user").get().build()
            val obj = JSONObject(executeString(req))
            val login = obj.getString("login")
            GithubUserInfo(
                login = login,
                name = obj.optString("name", "").ifBlank { login },
                avatarUrl = obj.optString("avatar_url", "")
            )
        }

    override suspend fun listRepositories(pat: String): List<GithubRepoSummary> =
        withContext(Dispatchers.IO) {
            val req = baseRequest(pat, "$BASE_URL/user/repos?per_page=100&sort=updated")
                .get()
                .build()
            val arr = JSONArray(executeString(req))
            val repos = mutableListOf<GithubRepoSummary>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val ownerObj = obj.optJSONObject("owner") ?: continue
                repos.add(
                    GithubRepoSummary(
                        owner = ownerObj.optString("login", ""),
                        name = obj.optString("name", ""),
                        fullName = obj.optString("full_name", ""),
                        defaultBranch = obj.optString("default_branch", "main"),
                        isPrivate = obj.optBoolean("private", false)
                    )
                )
            }
            repos
        }

    /**
     * Returns Pair(headCommitSha, rootTreeSha) for the target branch.
     */
    override suspend fun getBranchHeadCommit(
        pat: String,
        owner: String,
        repo: String,
        branch: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val refReq = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/ref/heads/$branch")
            .get()
            .build()
        val refObj = JSONObject(executeString(refReq))
        val commitSha = refObj.getJSONObject("object").getString("sha")

        val commitReq = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/commits/$commitSha")
            .get()
            .build()
        val commitObj = JSONObject(executeString(commitReq))
        val treeSha = commitObj.getJSONObject("tree").getString("sha")
        commitSha to treeSha
    }

    override suspend fun listFoldersInRepo(
        pat: String,
        owner: String,
        repo: String,
        branch: String
    ): List<String> = withContext(Dispatchers.IO) {
        val (_, rootTreeSha) = getBranchHeadCommit(pat, owner, repo, branch.ifBlank { "main" })
        val req = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/trees/$rootTreeSha?recursive=1")
            .get()
            .build()
        val root = JSONObject(executeString(req))
        val treeArr = root.optJSONArray("tree") ?: return@withContext emptyList()
        val folders = mutableListOf<String>()
        for (i in 0 until treeArr.length()) {
            val item = treeArr.optJSONObject(i) ?: continue
            if (item.optString("type") != "tree") continue
            val path = item.optString("path", "").trim().trim('/')
            if (path.isNotEmpty() && !path.startsWith(".")) {
                folders.add(path)
            }
        }
        folders.distinct().sortedBy { it.lowercase() }
    }

    override suspend fun listBlobsInSubfolder(
        pat: String,
        owner: String,
        repo: String,
        treeSha: String,
        subfolder: String
    ): List<GithubTreeBlob> = withContext(Dispatchers.IO) {
        val cleanSub = subfolder.trim().trim('/')
        val prefix = if (cleanSub.isEmpty()) "" else "$cleanSub/"

        val req = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/trees/$treeSha?recursive=1")
            .get()
            .build()
        val root = JSONObject(executeString(req))
        val treeArr = root.optJSONArray("tree") ?: return@withContext emptyList()
        val blobs = mutableListOf<GithubTreeBlob>()
        for (i in 0 until treeArr.length()) {
            val item = treeArr.optJSONObject(i) ?: continue
            if (item.optString("type") != "blob") continue
            val repoPath = item.optString("path", "")
            if (repoPath.isBlank()) continue
            if (prefix.isNotEmpty() && !repoPath.startsWith(prefix)) continue
            val relative = if (prefix.isEmpty()) repoPath else repoPath.removePrefix(prefix)
            if (relative.isBlank()) continue
            blobs.add(
                GithubTreeBlob(
                    repoPath = repoPath,
                    relativePath = relative,
                    sha = item.getString("sha"),
                    size = item.optLong("size", 0L)
                )
            )
        }
        blobs
    }

    override suspend fun downloadBlobToFile(
        pat: String,
        owner: String,
        repo: String,
        blobSha: String,
        destFile: File
    ) = withContext(Dispatchers.IO) {
        val req = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/blobs/$blobSha")
            .get()
            .build()
        val obj = JSONObject(executeString(req))
        val encoding = obj.optString("encoding", "base64")
        val content = obj.optString("content", "")
        val bytes = if (encoding == "base64") {
            Base64.decode(content.replace("\n", "").replace("\r", ""), Base64.DEFAULT)
        } else {
            content.toByteArray(Charsets.UTF_8)
        }
        destFile.parentFile?.mkdirs()
        val tmp = File(destFile.parentFile, ".syncbebas_gh_${System.nanoTime()}.tmp")
        try {
            tmp.writeBytes(bytes)
            if (destFile.exists()) destFile.delete()
            if (!tmp.renameTo(destFile)) {
                tmp.copyTo(destFile, overwrite = true)
                tmp.delete()
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    override suspend fun createBlobFromFile(
        pat: String,
        owner: String,
        repo: String,
        file: File
    ): String = withContext(Dispatchers.IO) {
        if (file.length() > MAX_GITHUB_FILE_BYTES) {
            throw IllegalArgumentException("Ukuran berkas ${file.name} melebihi batas aman GitHub (45 MB).")
        }
        val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("content", b64)
            put("encoding", "base64")
        }
        val req = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/blobs")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        JSONObject(executeString(req)).getString("sha")
    }

    override suspend fun createAtomicCommit(
        pat: String,
        owner: String,
        repo: String,
        branch: String,
        parentCommitSha: String,
        baseTreeSha: String,
        changes: List<GithubTreeChange>,
        message: String
    ): String = withContext(Dispatchers.IO) {
        val treeEntries = JSONArray()
        changes.forEach { change ->
            val entry = JSONObject().apply {
                put("path", change.repoPath)
                put("mode", "100644")
                put("type", "blob")
                if (change.blobSha != null) {
                    put("sha", change.blobSha)
                } else {
                    put("sha", JSONObject.NULL)
                }
            }
            treeEntries.put(entry)
        }

        val treePayload = JSONObject().apply {
            put("base_tree", baseTreeSha)
            put("tree", treeEntries)
        }
        val treeReq = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/trees")
            .post(treePayload.toString().toRequestBody(JSON_MEDIA))
            .build()
        val newTreeSha = JSONObject(executeString(treeReq)).getString("sha")

        val commitPayload = JSONObject().apply {
            put("message", message)
            put("tree", newTreeSha)
            put("parents", JSONArray().apply { put(parentCommitSha) })
        }
        val commitReq = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/commits")
            .post(commitPayload.toString().toRequestBody(JSON_MEDIA))
            .build()
        val newCommitSha = JSONObject(executeString(commitReq)).getString("sha")

        val refPayload = JSONObject().apply {
            put("sha", newCommitSha)
            put("force", false)
        }
        val refReq = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/refs/heads/$branch")
            .patch(refPayload.toString().toRequestBody(JSON_MEDIA))
            .build()
        executeString(refReq)
        newCommitSha
    }

    private fun baseRequest(pat: String, url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${pat.trim()}")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", API_VERSION)
            .addHeader("User-Agent", "Lotura-Android")
    }

    private fun executeString(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) {
                throw IllegalStateException("Token GitHub ditolak atau izin repo kurang (${response.code}).")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub API (${response.code}): ${bodyStr.take(240)}")
            }
            return bodyStr
        }
    }
}
