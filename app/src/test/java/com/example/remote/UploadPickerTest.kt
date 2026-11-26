package com.example.remote

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract as Documents
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class UploadPickerTest {
    private val authority = "lotura.test.upload"
    private val tree = Documents.buildTreeDocumentUri(authority, "root:Photos")
    private val dir = Documents.Document.MIME_TYPE_DIR

    @Test fun nestedDocumentsStayWithinTheSelectedTreeGrant() = runBlocking {
        val picker = picker { uri, cursor ->
            assertEquals("root:Photos", Documents.getTreeDocumentId(uri))
            when (uri.pathSegments.last()) {
                "root:Photos" -> cursor.addRow(arrayOf("root:Photos", "Photos", dir))
                "children" -> when (Documents.getDocumentId(uri)) {
                    "root:Photos" -> cursor.addRow(arrayOf("sub:Trip", "Trip", dir))
                    "sub:Trip" -> cursor.addRow(arrayOf("file:1", "picture.jpg", "image/jpeg"))
                    else -> error("Unexpected document $uri")
                }
                else -> error("Unexpected URI $uri")
            }
        }
        assertEquals(PickedFolder(tree, "Photos"), picker.folder(tree))
        val result = scanUploadTrees(listOf(UploadDocument("Photos", picker.rootUri(tree), true)), picker::children)
        val leaf = result.items.gitUploadFiles().single()
        assertEquals("Photos/Trip/picture.jpg", leaf.path)
        assertEquals(Documents.buildDocumentUriUsingTree(tree, "file:1"), leaf.content)
    }

    @Test fun loadingFolderIsNotMistakenForAnEmptyFolder() {
        val picker = picker { _, cursor -> cursor.extras = Bundle().apply { putBoolean(Documents.EXTRA_LOADING, true) } }
        assertThrows(IllegalStateException::class.java) { picker.children(picker.rootUri(tree)) }
    }

    private fun picker(rows: (Uri, MatrixCursor) -> Unit): UploadPicker {
        ShadowContentResolver.registerProviderInternal(authority, object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
                MatrixCursor(checkNotNull(projection).map { it }.toTypedArray()).also { rows(uri, it) }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Unexpected insert")
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Unexpected delete")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Unexpected update")
        })
        return UploadPicker(RuntimeEnvironment.getApplication().contentResolver)
    }
}
