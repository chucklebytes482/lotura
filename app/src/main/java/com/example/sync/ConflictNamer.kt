package com.example.sync

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ConflictNamer {

    private val CONFLICT_PATTERN = Regex(""" \(konflik \d{8}-\d{6}\)(\.[^./]+)?$""")

    /**
     * Produces a conflict filename following the format:
     *   "dir/catatan (konflik 20261120-083015).md"
     *   "catatan (konflik 20261120-083015)" (when there is no extension)
     */
    fun buildConflictPath(
        relativePath: String,
        timestampMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val normalized = relativePath.trimStart('/')
        val dir = normalized.substringBeforeLast('/', "")
        val fileName = normalized.substringAfterLast('/')

        val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            this.timeZone = timeZone
        }
        val stamp = formatter.format(Date(timestampMillis))

        val lastDot = fileName.lastIndexOf('.')
        val conflictFileName = if (lastDot > 0) {
            val base = fileName.substring(0, lastDot)
            val ext = fileName.substring(lastDot)
            "$base (konflik $stamp)$ext"
        } else {
            "$fileName (konflik $stamp)"
        }

        return if (dir.isEmpty()) conflictFileName else "$dir/$conflictFileName"
    }

    fun isConflictCopy(relativePath: String): Boolean {
        val fileName = relativePath.substringAfterLast('/')
        return CONFLICT_PATTERN.containsMatchIn(fileName)
    }
}
