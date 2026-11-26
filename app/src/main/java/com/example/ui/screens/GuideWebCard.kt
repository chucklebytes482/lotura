package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Alamat web panduan Lotura di GitHub Pages. */
internal const val GUIDE_URL = "https://chucklebytes482.github.io/lotura/"

/** Kartu berisi web panduan (GitHub Pages) yang dimuat di dalam WebView. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun GuideWebCard(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(12.dp)),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                // Tautan tetap dibuka di dalam WebView, tidak lewat browser luar.
                webViewClient = WebViewClient()
                loadUrl(GUIDE_URL)
            }
        }
    )
}
