package com.example.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReleaseParsingTest {

    @Test
    fun assetUrl_picksExactApkName() {
        val json = """
            {"tag_name":"v1","assets":[
              {"name":"lotura-latest-x86.apk","browser_download_url":"https://e/x86.apk"},
              {"name":"${ApkUpdate.ASSET_NAME}","browser_download_url":"https://e/arm.apk"}
            ]}
        """.trimIndent()
        assertEquals("https://e/arm.apk", ApkUpdate.assetUrlFrom(json))
    }

    @Test
    fun assetUrl_missingAssetThrows() {
        val json = """{"assets":[{"name":"lain.zip","browser_download_url":"https://e/z"}]}"""
        assertThrows(IOException::class.java) { ApkUpdate.assetUrlFrom(json) }
    }

    @Test
    fun assetUrl_noAssetsArrayThrows() {
        assertThrows(IOException::class.java) { ApkUpdate.assetUrlFrom("{}") }
    }
}
