package com.example.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Satu operasi sync (download atau unggah) untuk satu folder Drive atau satu repo GitHub. */
data class SyncLogEntry(
    val atMillis: Long,
    /** Sumber: "Drive · email" atau "GitHub · owner/repo". */
    val source: String,
    /** Nama folder atau repo yang di-sync. */
    val label: String,
    /** true = unggah (perangkat ke sumber), false = download (sumber ke perangkat). */
    val upload: Boolean,
    val status: SyncStatus,
    val transferred: Int,
    val skipped: Int,
    val failed: Int,
    val error: String?
) {
    fun toJson(): JSONObject = JSONObject()
        .put("at", atMillis)
        .put("source", source)
        .put("label", label)
        .put("upload", upload)
        .put("status", status.name)
        .put("transferred", transferred)
        .put("skipped", skipped)
        .put("failed", failed)
        .put("error", error ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject): SyncLogEntry = SyncLogEntry(
            atMillis = o.getLong("at"),
            source = o.getString("source"),
            label = o.getString("label"),
            upload = o.getBoolean("upload"),
            status = SyncStatus.valueOf(o.getString("status")),
            transferred = o.getInt("transferred"),
            skipped = o.getInt("skipped"),
            failed = o.getInt("failed"),
            error = if (o.isNull("error")) null else o.getString("error")
        )
    }
}

/**
 * Log sync standar untuk semua sumber. Disimpan di SharedPreferences sebagai JSON,
 * urutan terbaru di depan, dibatasi [MAX_ENTRIES] entri.
 */
class SyncLogStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Semua entri, terbaru di depan. */
    fun loadAll(): List<SyncLogEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { SyncLogEntry.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    /** Menambah entri baru di depan, membuang yang melewati batas, lalu mengembalikan daftar terbaru. */
    fun append(entry: SyncLogEntry): List<SyncLogEntry> {
        val updated = (listOf(entry) + loadAll()).take(MAX_ENTRIES)
        val arr = JSONArray()
        updated.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
        return updated
    }

    companion object {
        const val MAX_ENTRIES = 200
        private const val PREFS = "sync_log"
        private const val KEY = "entries"
    }
}
