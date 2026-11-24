package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Keadaan unduhan APK, disimpan di ViewModel supaya tetap ada saat pindah tab. */
sealed interface ApkState {
    data object Idle : ApkState
    data object Downloading : ApkState
    /** APK sudah terunduh, tapi izin "pasang aplikasi tidak dikenal" belum ada. */
    data class NeedsInstallPermission(val file: File) : ApkState
    data class Failed(val message: String) : ApkState
}

object ApkUpdate {
    const val ASSET_NAME = "lotura-latest-arm64-v8a.apk"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/chucklebytes482/lotura/releases/latest"

    /** Ambil browser_download_url dari JSON rilis. Melempar IOException bila asset tidak ada. */
    fun assetUrlFrom(releaseJson: String): String {
        val assets = JSONObject(releaseJson).optJSONArray("assets")
            ?: throw IOException("Rilis terbaru tidak punya file APK.")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name") == ASSET_NAME) {
                return asset.getString("browser_download_url")
            }
        }
        throw IOException("File $ASSET_NAME tidak ada di rilis terbaru.")
    }

    /** Unduh APK rilis terbaru ke [target]. Mengembalikan file itu bila berhasil. */
    fun downloadLatest(client: OkHttpClient, target: File): File {
        val releaseRequest = Request.Builder()
            .url(LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .build()
        val releaseJson = client.newCall(releaseRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Gagal mengambil info rilis (HTTP ${response.code}).")
            response.body?.string() ?: throw IOException("Info rilis kosong.")
        }
        val url = assetUrlFrom(releaseJson)

        target.parentFile?.mkdirs()
        val partFile = File(target.parentFile, target.name + ".part")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Gagal mengunduh APK (HTTP ${response.code}).")
            val body = response.body ?: throw IOException("Unduhan APK kosong.")
            partFile.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
        }
        if (target.exists() && !target.delete()) throw IOException("Gagal mengganti APK lama.")
        if (!partFile.renameTo(target)) throw IOException("Gagal menyimpan APK.")
        return target
    }

    /** Halaman sistem untuk mengizinkan Lotura memasang aplikasi dari sumber ini. */
    fun unknownSourcesIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
