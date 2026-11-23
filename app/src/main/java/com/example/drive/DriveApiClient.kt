package com.example.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class DriveAuthExpiredException(message: String) : Exception(message)

interface DriveRemote {
    suspend fun fetchUserInfo(accessToken: String): DriveUserInfo
    suspend fun listFolders(accessToken: String, parentId: String = "root"): List<DriveFolderItem>
    suspend fun createFolder(accessToken: String, parentId: String, name: String): DriveFolderItem
    suspend fun listChildren(accessToken: String, folderId: String): List<DriveChild>
    suspend fun listFilesRecursive(accessToken: String, rootFolderId: String): List<DriveFileItem>
    suspend fun downloadFile(accessToken: String, fileId: String, destFile: File)
    suspend fun exportGoogleWorkspaceFile(
        accessToken: String,
        fileId: String,
        exportMimeType: String,
        destFile: File
    )
    suspend fun uploadFile(
        accessToken: String,
        rootFolderId: String,
        relativePath: String,
        existingFileId: String?,
        sourceFile: File,
        nativeWorkspaceMimeType: String? = null
    ): DriveFileItem
    suspend fun trashFile(accessToken: String, fileId: String)
}

/** Satu entri isi folder Drive (folder atau file), untuk pohon di UI dan unduhan terpilih. */
data class DriveChild(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val size: Long?,
    val nativeMime: String?,
    val modifiedTimeMillis: Long
)

class DriveApiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) : DriveRemote {

    companion object {
        private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val ABOUT_URL = "https://www.googleapis.com/drive/v3/about"
        private const val LIST_FIELDS =
            "nextPageToken,files(id,name,mimeType,modifiedTime,size,md5Checksum,trashed,shortcutDetails)"
    }

    override suspend fun fetchUserInfo(accessToken: String): DriveUserInfo =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$ABOUT_URL?fields=user(emailAddress,displayName)")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            DriveJsonParser.parseUserInfo(executeString(req))
        }

    override suspend fun listFolders(
        accessToken: String,
        parentId: String
    ): List<DriveFolderItem> = withContext(Dispatchers.IO) {
        val query = "'$parentId' in parents and trashed = false and " +
            "(mimeType = '${DriveMimeTypes.FOLDER}' or mimeType = '${DriveMimeTypes.SHORTCUT}')"
        val result = mutableListOf<DriveFolderItem>()
        var pageToken: String? = null
        do {
            val encodedQ = URLEncoder.encode(query, "UTF-8")
            val tokenParam = if (pageToken != null) "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}" else ""
            val req = Request.Builder()
                .url("$FILES_URL?q=$encodedQ&orderBy=name&pageSize=100&fields=${URLEncoder.encode(LIST_FIELDS, "UTF-8")}$tokenParam&supportsAllDrives=true&includeItemsFromAllDrives=true")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            val page = DriveJsonParser.parseFilePage(executeString(req))
            page.files.forEach { entry ->
                val resolvedId = if (entry.mimeType == DriveMimeTypes.SHORTCUT && entry.shortcutTargetMimeType == DriveMimeTypes.FOLDER) {
                    entry.shortcutTargetId ?: entry.id
                } else if (entry.mimeType == DriveMimeTypes.FOLDER) {
                    entry.id
                } else {
                    return@forEach
                }
                result.add(
                    DriveFolderItem(
                        id = resolvedId,
                        name = entry.name,
                        modifiedTimeMillis = entry.modifiedTimeMillis
                    )
                )
            }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        result
    }

    override suspend fun createFolder(
        accessToken: String,
        parentId: String,
        name: String
    ): DriveFolderItem = withContext(Dispatchers.IO) {
        val metadata = JSONObject().apply {
            put("name", name.trim())
            put("mimeType", DriveMimeTypes.FOLDER)
            put("parents", JSONArray().apply { put(parentId) })
        }
        val req = Request.Builder()
            .url("$FILES_URL?fields=id,name,modifiedTime&supportsAllDrives=true")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val obj = JSONObject(executeString(req))
        DriveFolderItem(
            id = obj.getString("id"),
            name = obj.optString("name", name),
            modifiedTimeMillis = DriveJsonParser.parseRfc3339Millis(obj.optString("modifiedTime", ""))
        )
    }

    /**
     * Isi satu folder (tidak rekursif). Dipakai untuk pohon di UI.
     * Nama file memakai nama lokal (Google Docs diberi ekstensi ekspor), sama dengan [listFilesRecursive].
     * Native Google Docs/Sheets/Slides tidak punya ukuran, jadi [DriveChild.size] = null.
     */
    override suspend fun listChildren(
        accessToken: String,
        folderId: String
    ): List<DriveChild> = withContext(Dispatchers.IO) {
        val query = "'$folderId' in parents and trashed = false"
        val result = mutableListOf<DriveChild>()
        var pageToken: String? = null
        do {
            val encodedQ = URLEncoder.encode(query, "UTF-8")
            val tokenParam = if (pageToken != null) "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}" else ""
            val req = Request.Builder()
                .url("$FILES_URL?q=$encodedQ&orderBy=folder,name&pageSize=200&fields=${URLEncoder.encode(LIST_FIELDS, "UTF-8")}$tokenParam&supportsAllDrives=true&includeItemsFromAllDrives=true")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            val page = DriveJsonParser.parseFilePage(executeString(req))
            for (entry in page.files) {
                val effectiveMime = if (entry.mimeType == DriveMimeTypes.SHORTCUT) {
                    entry.shortcutTargetMimeType ?: entry.mimeType
                } else {
                    entry.mimeType
                }
                val effectiveId = if (entry.mimeType == DriveMimeTypes.SHORTCUT) {
                    entry.shortcutTargetId ?: entry.id
                } else {
                    entry.id
                }
                if (effectiveMime == DriveMimeTypes.FOLDER) {
                    result.add(
                        DriveChild(
                            id = effectiveId,
                            name = entry.name,
                            isFolder = true,
                            size = null,
                            nativeMime = null,
                            modifiedTimeMillis = entry.modifiedTimeMillis
                        )
                    )
                    continue
                }
                val exportSpec = DriveMimeTypes.exportSpecFor(effectiveMime)
                if (effectiveMime.startsWith("application/vnd.google-apps.") && exportSpec == null) continue
                val isNative = exportSpec != null
                result.add(
                    DriveChild(
                        id = effectiveId,
                        name = DriveMimeTypes.localFileNameForDriveItem(entry.name, effectiveMime),
                        isFolder = false,
                        size = if (isNative) null else entry.size,
                        nativeMime = if (isNative) effectiveMime else null,
                        modifiedTimeMillis = entry.modifiedTimeMillis
                    )
                )
            }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        result.sortedWith(compareByDescending<DriveChild> { it.isFolder }.thenBy { it.name.lowercase() })
    }

    override suspend fun listFilesRecursive(
        accessToken: String,
        rootFolderId: String
    ): List<DriveFileItem> = withContext(Dispatchers.IO) {
        val collected = mutableListOf<DriveFileItem>()
        val visitedFolders = mutableSetOf<String>()
        scanFolderRecursive(
            accessToken = accessToken,
            folderId = rootFolderId,
            prefixPath = "",
            visitedFolders = visitedFolders,
            output = collected
        )
        collected
    }

    private fun scanFolderRecursive(
        accessToken: String,
        folderId: String,
        prefixPath: String,
        visitedFolders: MutableSet<String>,
        output: MutableList<DriveFileItem>
    ) {
        if (!visitedFolders.add(folderId)) return
        val query = "'$folderId' in parents and trashed = false"
        var pageToken: String? = null
        do {
            val encodedQ = URLEncoder.encode(query, "UTF-8")
            val tokenParam = if (pageToken != null) "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}" else ""
            val req = Request.Builder()
                .url("$FILES_URL?q=$encodedQ&pageSize=200&fields=${URLEncoder.encode(LIST_FIELDS, "UTF-8")}$tokenParam&supportsAllDrives=true&includeItemsFromAllDrives=true")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            val page = DriveJsonParser.parseFilePage(executeString(req))
            for (entry in page.files) {
                val effectiveMime = if (entry.mimeType == DriveMimeTypes.SHORTCUT) {
                    entry.shortcutTargetMimeType ?: entry.mimeType
                } else {
                    entry.mimeType
                }
                val effectiveId = if (entry.mimeType == DriveMimeTypes.SHORTCUT) {
                    entry.shortcutTargetId ?: entry.id
                } else {
                    entry.id
                }

                if (effectiveMime == DriveMimeTypes.FOLDER) {
                    val subPrefix = if (prefixPath.isEmpty()) entry.name else "$prefixPath/${entry.name}"
                    scanFolderRecursive(accessToken, effectiveId, subPrefix, visitedFolders, output)
                } else {
                    val exportSpec = DriveMimeTypes.exportSpecFor(effectiveMime)
                    // Skip unsupported native Google Workspace types (e.g., Forms, Jamboard)
                    if (effectiveMime.startsWith("application/vnd.google-apps.") && exportSpec == null) {
                        continue
                    }
                    val localName = DriveMimeTypes.localFileNameForDriveItem(entry.name, effectiveMime)
                    val relPath = if (prefixPath.isEmpty()) localName else "$prefixPath/$localName"
                    output.add(
                        DriveFileItem(
                            id = effectiveId,
                            name = localName,
                            relativePath = relPath,
                            mimeType = exportSpec?.exportMimeType ?: effectiveMime,
                            modifiedTimeMillis = entry.modifiedTimeMillis,
                            size = entry.size,
                            md5Checksum = entry.md5Checksum,
                            trashed = entry.trashed,
                            nativeWorkspaceMimeType = if (exportSpec != null) effectiveMime else null
                        )
                    )
                }
            }
            pageToken = page.nextPageToken
        } while (pageToken != null)
    }

    override suspend fun downloadFile(accessToken: String, fileId: String, destFile: File) =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$FILES_URL/$fileId?alt=media&supportsAllDrives=true")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            downloadToFile(req, destFile)
        }

    override suspend fun exportGoogleWorkspaceFile(
        accessToken: String,
        fileId: String,
        exportMimeType: String,
        destFile: File
    ) = withContext(Dispatchers.IO) {
        val encodedMime = URLEncoder.encode(exportMimeType, "UTF-8")
        val req = Request.Builder()
            .url("$FILES_URL/$fileId/export?mimeType=$encodedMime")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()
        downloadToFile(req, destFile)
    }

    override suspend fun uploadFile(
        accessToken: String,
        rootFolderId: String,
        relativePath: String,
        existingFileId: String?,
        sourceFile: File,
        nativeWorkspaceMimeType: String?
    ): DriveFileItem = withContext(Dispatchers.IO) {
        val normalized = relativePath.trimStart('/')
        val parentRelDir = normalized.substringBeforeLast('/', "")
        val fileName = normalized.substringAfterLast('/')
        val parentFolderId = if (existingFileId == null) {
            ensureSubfolderPath(accessToken, rootFolderId, parentRelDir)
        } else {
            rootFolderId
        }

        val uploadContentMime = DriveMimeTypes.guessMimeType(fileName)
        val metadata = JSONObject().apply {
            if (existingFileId == null) {
                put("name", fileName)
                put("parents", JSONArray().apply { put(parentFolderId) })
            }
            if (!nativeWorkspaceMimeType.isNullOrBlank()) {
                put("mimeType", nativeWorkspaceMimeType)
            }
        }

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(
                metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .addPart(
                sourceFile.asRequestBody(uploadContentMime.toMediaType())
            )
            .build()

        val fields = URLEncoder.encode("id,name,mimeType,modifiedTime,size,md5Checksum", "UTF-8")
        val req = Request.Builder()
            .addHeader("Authorization", "Bearer $accessToken")
            .let { builder ->
                if (existingFileId != null) {
                    builder.url("$UPLOAD_URL/$existingFileId?uploadType=multipart&fields=$fields&supportsAllDrives=true")
                        .patch(multipartBody)
                } else {
                    builder.url("$UPLOAD_URL?uploadType=multipart&fields=$fields&supportsAllDrives=true")
                        .post(multipartBody)
                }
            }
            .build()

        val obj = JSONObject(executeString(req))
        val returnedMime = obj.optString("mimeType", uploadContentMime)
        val exportSpec = DriveMimeTypes.exportSpecFor(returnedMime)
        DriveFileItem(
            id = obj.getString("id"),
            name = fileName,
            relativePath = normalized,
            mimeType = exportSpec?.exportMimeType ?: returnedMime,
            modifiedTimeMillis = DriveJsonParser.parseRfc3339Millis(obj.optString("modifiedTime", "")),
            size = obj.optString("size", sourceFile.length().toString()).toLongOrNull() ?: sourceFile.length(),
            md5Checksum = obj.optString("md5Checksum", "").takeIf { it.isNotBlank() },
            nativeWorkspaceMimeType = if (exportSpec != null) returnedMime else nativeWorkspaceMimeType
        )
    }

    override suspend fun trashFile(accessToken: String, fileId: String) =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply { put("trashed", true) }
            val req = Request.Builder()
                .url("$FILES_URL/$fileId?supportsAllDrives=true")
                .addHeader("Authorization", "Bearer $accessToken")
                .patch(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            executeString(req)
            Unit
        }

    private suspend fun ensureSubfolderPath(
        accessToken: String,
        rootFolderId: String,
        relativeDir: String
    ): String {
        if (relativeDir.isBlank()) return rootFolderId
        var currentParent = rootFolderId
        val segments = relativeDir.split('/').filter { it.isNotBlank() }
        for (segment in segments) {
            val escaped = segment.replace("'", "\\'")
            val query = "'$currentParent' in parents and name = '$escaped' and mimeType = '${DriveMimeTypes.FOLDER}' and trashed = false"
            val encodedQ = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("$FILES_URL?q=$encodedQ&pageSize=1&fields=files(id)&supportsAllDrives=true&includeItemsFromAllDrives=true")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            val arr = JSONObject(executeString(req)).optJSONArray("files")
            currentParent = if (arr != null && arr.length() > 0) {
                arr.getJSONObject(0).getString("id")
            } else {
                createFolder(accessToken, currentParent, segment).id
            }
        }
        return currentParent
    }

    private fun executeString(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (response.code == 401) {
                throw DriveAuthExpiredException("Sesi Google Drive kedaluwarsa (401). Silakan otorisasi ulang.")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("Google Drive API (${response.code}): ${bodyStr.take(240)}")
            }
            return bodyStr
        }
    }

    private fun downloadToFile(request: Request, destFile: File) {
        destFile.parentFile?.mkdirs()
        val tempFile = File(destFile.parentFile, ".syncbebas_dl_${System.nanoTime()}.tmp")
        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    throw DriveAuthExpiredException("Sesi Google Drive kedaluwarsa (401).")
                }
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    throw IllegalStateException("Gagal unduh Drive (${response.code}): ${err.take(200)}")
                }
                val stream = response.body?.byteStream()
                    ?: throw IllegalStateException("Respons unduhan Drive kosong.")
                tempFile.outputStream().use { out ->
                    stream.copyTo(out)
                }
            }
            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }
}
