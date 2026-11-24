package com.example.data

import android.content.Context
import com.example.sync.SkipDetail
import com.example.sync.SkipReason
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
    val error: String?,
    /** Jumlah item tersembunyi yang dilewati. */
    val hidden: Int = 0,
    /** Alasan per item yang dilewati. Dibatasi [SyncLogStore.MAX_SKIP_DETAILS] saat disimpan. */
    val skipDetails: List<SkipDetail> = emptyList(),
    /** Jumlah item yang dilewati per alasan. Dipakai untuk ringkasan di log. */
    val skipCounts: Map<SkipReason, Int> = emptyMap()
) {
    fun toJson(): JSONObject {
        val details = JSONArray()
        skipDetails.take(SyncLogStore.MAX_SKIP_DETAILS).forEach {
            details.put(JSONObject().put("path", it.path).put("reason", it.reason.name))
        }
        return JSONObject()
            .put("at", atMillis)
            .put("source", source)
            .put("label", label)
            .put("upload", upload)
            .put("status", status.name)
            .put("transferred", transferred)
            .put("skipped", skipped)
            .put("failed", failed)
            .put("error", error ?: JSONObject.NULL)
            .put("hidden", hidden)
            .put("skipDetails", details)
            .put("skipCounts", JSONObject().apply {
                skipCounts.forEach { (reason, n) -> put(reason.name, n) }
            })
    }

    companion object {
        fun fromJson(o: JSONObject): SyncLogEntry {
            val details = o.optJSONArray("skipDetails") ?: JSONArray()
            return SyncLogEntry(
                atMillis = o.getLong("at"),
                source = o.getString("source"),
                label = o.getString("label"),
                upload = o.getBoolean("upload"),
                status = SyncStatus.valueOf(o.getString("status")),
                transferred = o.getInt("transferred"),
                skipped = o.getInt("skipped"),
                failed = o.getInt("failed"),
                error = if (o.isNull("error")) null else o.getString("error"),
                hidden = o.optInt("hidden", 0),
                skipDetails = (0 until details.length()).mapNotNull { i ->
                    val d = details.getJSONObject(i)
                    val reason = runCatching { SkipReason.valueOf(d.getString("reason")) }.getOrNull()
                    reason?.let { SkipDetail(d.getString("path"), it) }
                },
                skipCounts = o.optJSONObject("skipCounts")?.let { c ->
                    c.keys().asSequence().mapNotNull { k ->
                        val reason = runCatching { SkipReason.valueOf(k) }.getOrNull()
                        reason?.let { it to c.optInt(k, 0) }
                    }.toMap()
                } ?: emptyMap()
            )
        }
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
        /** Batas path yang disimpan per entri. Jumlah total tetap ada di [SyncLogEntry.skipped] dan [SyncLogEntry.hidden]. */
        const val MAX_SKIP_DETAILS = 100
        private const val PREFS = "sync_log"
        private const val KEY = "entries"
    }
}
