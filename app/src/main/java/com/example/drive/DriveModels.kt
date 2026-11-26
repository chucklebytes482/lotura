package com.example.drive

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class DriveUserInfo(
    val email: String,
    val displayName: String
)

data class DriveFolderItem(
    val id: String,
    val name: String,
    val modifiedTimeMillis: Long = 0L
)

data class DriveFileItem(
    val id: String,
    val name: String,
    val relativePath: String,
    val mimeType: String,
    val modifiedTimeMillis: Long,
    val size: Long,
    val md5Checksum: String?,
    val trashed: Boolean = false,
    val nativeWorkspaceMimeType: String? = null
) {
    val isFolder: Boolean
        get() = mimeType == DriveMimeTypes.FOLDER

    val isNativeGoogleWorkspace: Boolean
        get() = nativeWorkspaceMimeType != null
}

object DriveMimeTypes {
    const val FOLDER = "application/vnd.google-apps.folder"
    const val SHORTCUT = "application/vnd.google-apps.shortcut"
    const val GOOGLE_DOC = "application/vnd.google-apps.document"
    const val GOOGLE_SHEET = "application/vnd.google-apps.spreadsheet"
    const val GOOGLE_SLIDES = "application/vnd.google-apps.presentation"

    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

    data class ExportSpec(
        val exportMimeType: String,
        val extension: String
    )

    fun exportSpecFor(googleMimeType: String): ExportSpec? = when (googleMimeType) {
        GOOGLE_DOC -> ExportSpec(DOCX, ".docx")
        GOOGLE_SHEET -> ExportSpec(XLSX, ".xlsx")
        GOOGLE_SLIDES -> ExportSpec(PPTX, ".pptx")
        else -> null
    }

    fun localFileNameForDriveItem(name: String, mimeType: String): String {
        val spec = exportSpecFor(mimeType) ?: return name
        return if (name.endsWith(spec.extension, ignoreCase = true)) {
            name
        } else {
            "$name${spec.extension}"
        }
    }

    fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "md", "markdown" -> "text/markdown"
            "txt", "log", "csv" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "pdf" -> "application/pdf"
            "docx" -> DOCX
            "xlsx" -> XLSX
            "pptx" -> PPTX
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "zip" -> "application/zip"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }
}

data class DriveFilePage(
    val files: List<RawDriveEntry>,
    val nextPageToken: String?
)

data class RawDriveEntry(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTimeMillis: Long,
    val size: Long,
    val md5Checksum: String?,
    val trashed: Boolean,
    val shortcutTargetId: String?,
    val shortcutTargetMimeType: String?
)

object DriveJsonParser {

    fun parseUserInfo(jsonStr: String): DriveUserInfo {
        val root = JSONObject(jsonStr)
        val user = root.optJSONObject("user") ?: JSONObject()
        val email = user.optString("emailAddress", "")
        val displayName = user.optString("displayName", "").ifBlank { email }
        return DriveUserInfo(email = email, displayName = displayName)
    }

    fun parseFilePage(jsonStr: String): DriveFilePage {
        val root = JSONObject(jsonStr)
        check(!root.optBoolean("incompleteSearch")) { "Daftar Drive tidak lengkap. Sinkron dibatalkan demi keamanan." }
        val nextToken = root.optString("nextPageToken", "").takeIf { it.isNotBlank() }
        val arr = root.optJSONArray("files")
        val list = mutableListOf<RawDriveEntry>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                val name = obj.optString("name", "")
                val mimeType = obj.optString("mimeType", "")
                if (id.isBlank() || name.isBlank()) continue
                val modifiedStr = obj.optString("modifiedTime", "")
                val sizeStr = obj.optString("size", "0")
                val md5 = obj.optString("md5Checksum", "").takeIf { it.isNotBlank() }
                val trashed = obj.optBoolean("trashed", false)
                val shortcut = obj.optJSONObject("shortcutDetails")
                val targetId = shortcut?.optString("targetId", "")?.takeIf { it.isNotBlank() }
                val targetMime = shortcut?.optString("targetMimeType", "")?.takeIf { it.isNotBlank() }

                list.add(
                    RawDriveEntry(
                        id = id,
                        name = name,
                        mimeType = mimeType,
                        modifiedTimeMillis = parseRfc3339Millis(modifiedStr),
                        size = sizeStr.toLongOrNull() ?: 0L,
                        md5Checksum = md5,
                        trashed = trashed,
                        shortcutTargetId = targetId,
                        shortcutTargetMimeType = targetMime
                    )
                )
            }
        }
        return DriveFilePage(files = list, nextPageToken = nextToken)
    }

    fun parseRfc3339Millis(isoString: String): Long {
        if (isoString.isBlank()) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                fmt.parse(isoString)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return 0L
    }
}
