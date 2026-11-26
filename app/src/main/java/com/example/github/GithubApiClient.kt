package com.example.github

import android.util.Base64
import android.util.Base64OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

internal class GithubEmptyRepositoryException : Exception()

class GithubApiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val BASE_URL = "https://api.github.com"
        private const val API_VERSION = "2022-11-28"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val MAX_REPO_PAGES = 20
        const val MAX_GITHUB_FILE_BYTES = 45L * 1024L * 1024L // Safe limit below 50 MB warning / 100 MB hard cap
    }

    suspend fun getAuthenticatedUser(pat: String): GithubUserInfo =
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

    suspend fun listRepositories(pat: String): List<GithubRepoSummary> =
        withContext(Dispatchers.IO) {
            val repos = mutableListOf<GithubRepoSummary>()
            var page = 1
            while (page <= MAX_REPO_PAGES) {
                val req = baseRequest(pat, "$BASE_URL/user/repos?per_page=100&sort=updated&page=$page")
                    .get()
                    .build()
                val arr = JSONArray(executeString(req))
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val ownerObj = obj.optJSONObject("owner") ?: continue
                    repos.add(
                        GithubRepoSummary(
                            owner = ownerObj.optString("login", ""),
                            name = obj.optString("name", ""),
                            fullName = obj.optString("full_name", ""),
                            defaultBranch = obj.optString("default_branch", "main"),
                            isPrivate = obj.optBoolean("private", false),
                            archived = obj.optBoolean("archived", false)
                        )
                    )
                }
                if (arr.length() < 100) break
                page++
            }
            repos.distinctBy { it.fullName }
        }

    /**
     * Returns Pair(headCommitSha, rootTreeSha) for the target branch.
     */
    suspend fun getBranchHeadCommit(
        pat: String,
        owner: String,
        repo: String,
        branch: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val refReq = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/ref/heads/${branch.split('/').joinToString("/") { urlEnc(it) }}")
            .get()
            .build()
        val refObj = JSONObject(executeString(refReq, allowEmptyRepo = true))
        val commitSha = refObj.getJSONObject("object").getString("sha")

        val commitReq = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/commits/$commitSha")
            .get()
            .build()
        val commitObj = JSONObject(executeString(commitReq))
        val treeSha = commitObj.getJSONObject("tree").getString("sha")
        commitSha to treeSha
    }

    suspend fun listTree(pat: String, owner: String, repo: String, treeSha: String): List<GithubTreeEntry> =
        withContext(Dispatchers.IO) {
            val req = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/trees/$treeSha").build()
            val root = JSONObject(executeString(req))
            check(!root.optBoolean("truncated")) { "Daftar folder GitHub tidak lengkap." }
            val tree = root.getJSONArray("tree")
            List(tree.length()) { i ->
                val item = tree.getJSONObject(i)
                GithubTreeEntry(item.getString("path"), item.getString("sha"),
                    item.getString("mode"), item.getString("type"))
            }
        }

    suspend fun downloadBlobToFile(pat: String, owner: String, repo: String, blobSha: String, destFile: File) =
        withContext(Dispatchers.IO) {
            val req = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/blobs/$blobSha")
                .header("Accept", "application/vnd.github.raw+json")
                .build()
            httpClient.newCall(req).execute().use { response ->
                check(response.isSuccessful) { "Gagal unduh GitHub (${response.code})." }
                val body = checkNotNull(response.body) { "Respons unduhan GitHub kosong." }
                body.byteStream().use { input -> destFile.outputStream().use { input.copyTo(it) } }
            }
            Unit
        }

    suspend fun createEmptyBlob(pat: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        val req = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/blobs")
            .post(JSONObject().put("content", "").put("encoding", "utf-8").toString().toRequestBody(JSON_MEDIA))
            .build()
        JSONObject(executeString(req)).getString("sha")
    }

    suspend fun createBlobFromFile(
        pat: String,
        owner: String,
        repo: String,
        file: File
    ): String = withContext(Dispatchers.IO) {
        if (file.length() > MAX_GITHUB_FILE_BYTES) {
            throw IllegalArgumentException("Ukuran berkas ${file.name} melebihi batas aman GitHub (45 MB).")
        }
        val body = base64Body(file, prefix = "{\"content\":\"", suffix = "\",\"encoding\":\"base64\"}")
        val req = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/blobs").post(body).build()
        JSONObject(executeString(req)).getString("sha")
    }

    // The Contents API can create the first commit; the Git Data API needs an existing branch.
    suspend fun createInitialFile(pat: String, owner: String, repo: String, path: String, message: String, file: File?) =
        withContext(Dispatchers.IO) {
            val encoded = path.split('/').joinToString("/") { urlEnc(it) }
            val prefix = "{\"message\":${JSONObject.quote(message)},\"content\":\""
            val body = base64Body(file, prefix, "\"}")
            val req = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/contents/$encoded").put(body).build()
            JSONObject(executeString(req)).getJSONObject("commit").getString("sha")
        }

    private fun base64Body(file: File?, prefix: String, suffix: String) = object : RequestBody() {
        override fun contentType() = JSON_MEDIA
        override fun contentLength(): Long = prefix.toByteArray(Charsets.UTF_8).size +
            4L * (((file?.length() ?: 0L) + 2L) / 3L) + suffix.toByteArray(Charsets.UTF_8).size

        override fun writeTo(sink: BufferedSink) {
            sink.writeUtf8(prefix)
            if (file != null) {
                Base64OutputStream(sink.outputStream(), Base64.NO_WRAP or Base64.NO_CLOSE).use { encoded ->
                    file.inputStream().use { it.copyTo(encoded) }
                }
            }
            sink.writeUtf8(suffix)
        }
    }

    suspend fun createAtomicCommit(
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
                put("mode", change.mode)
                put("type", change.type)
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
        val refReq = baseRequest(pat, "$BASE_URL/repos/$owner/$repo/git/refs/heads/${branch.split('/').joinToString("/") { urlEnc(it) }}")
            .patch(refPayload.toString().toRequestBody(JSON_MEDIA))
            .build()
        executeString(refReq)
        newCommitSha
    }

    private fun urlEnc(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun baseRequest(pat: String, url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${pat.trim()}")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", API_VERSION)
            .addHeader("User-Agent", "Lotura-Android")
    }

    private fun executeString(request: Request, allowEmptyRepo: Boolean = false): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (allowEmptyRepo && response.code == 409) throw GithubEmptyRepositoryException()
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
