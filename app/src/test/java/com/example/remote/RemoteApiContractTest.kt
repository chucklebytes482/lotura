package com.example.remote

import android.app.Application
import com.example.drive.DriveApiClient
import com.example.github.GithubApiClient
import com.example.github.GithubTreeChange
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class RemoteApiContractTest {
    @Test fun githubListsOneLevelAndDoesNotRequestRecursiveTree() = runBlocking {
        val api = GithubApiClient(mock { request ->
            assertEquals("/repos/a/b/git/trees/sha", request.url.encodedPath)
            assertNull(request.url.query)
            """{"truncated":false,"tree":[{"path":"docs","sha":"next","mode":"040000","type":"tree"},{"path":"file","sha":"blob","mode":"100644","type":"blob"}]}"""
        })
        val entries = api.listTree("test-token", "a", "b", "sha")
        assertEquals(listOf("tree", "blob"), entries.map { it.type })
        assertEquals("next", entries.first().sha)
    }

    @Test fun githubFolderDeleteUsesTreeEntryAndDoesNotForceUpdate() = runBlocking {
        val api = GithubApiClient(mock { request ->
            val body = JSONObject(body(request))
            when (request.url.pathSegments.takeLast(2).joinToString("/")) {
                "git/trees" -> {
                    assertEquals("old-tree", body.getString("base_tree"))
                    val entry = body.getJSONArray("tree").getJSONObject(0)
                    assertEquals("docs/archive", entry.getString("path"))
                    assertEquals("tree", entry.getString("type"))
                    assertEquals("040000", entry.getString("mode"))
                    assertTrue(entry.isNull("sha"))
                    """{"sha":"new-tree"}"""
                }
                "git/commits" -> {
                    assertEquals("new-tree", body.getString("tree"))
                    assertEquals("observed-head", body.getJSONArray("parents").getString(0))
                    """{"sha":"new-commit"}"""
                }
                "heads/main" -> {
                    assertEquals("PATCH", request.method)
                    assertFalse(body.getBoolean("force"))
                    assertEquals("new-commit", body.getString("sha"))
                    "{}"
                }
                else -> error("Unexpected request")
            }
        })
        assertEquals("new-commit", api.createAtomicCommit("test-token", "a", "b", "main", "observed-head",
            "old-tree", listOf(GithubTreeChange("docs/archive", null, "040000", "tree")), "test"))
    }

    @Test fun githubStreamsBase64WithoutLosingPaddingOrSuffix() = runBlocking {
        val api = GithubApiClient(mock { request ->
            val text = body(request)
            assertEquals(text.toByteArray().size.toLong(), request.body!!.contentLength())
            assertEquals("AAH/Ag==", JSONObject(text).getString("content"))
            """{"sha":"blob"}"""
        })
        val file = File.createTempFile("lotura-test-", ".bin")
        try {
            file.writeBytes(byteArrayOf(0, 1, -1, 2))
            assertEquals("blob", api.createBlobFromFile("test-token", "a", "b", file))
        } finally { file.delete() }
    }

    @Test fun githubInitialUploadReturnsCommitForNestedPath() = runBlocking {
        val api = GithubApiClient(mock { request ->
            assertEquals("PUT", request.method)
            assertEquals("/repos/a/b/contents/Folder%20baru/empty/.gitkeep", request.url.encodedPath)
            assertEquals("", JSONObject(body(request)).getString("content"))
            """{"commit":{"sha":"initial-head"}}"""
        })
        assertEquals("initial-head", api.createInitialFile("test-token", "a", "b", "Folder baru/empty/.gitkeep", "test", null))
    }

    @Test fun driveCreatesNestedFoldersWithTheRequestedParent() = runBlocking {
        val api = DriveApiClient(mock { request ->
            assertEquals("POST", request.method)
            assertEquals("/drive/v3/files", request.url.encodedPath)
            val body = JSONObject(body(request))
            assertEquals("Remote folder ID", body.getJSONArray("parents").getString(0))
            assertEquals("child", body.getString("name"))
            assertEquals("application/vnd.google-apps.folder", body.getString("mimeType"))
            """{"id":"child-folder-id","name":"child"}"""
        })
        assertEquals("child-folder-id", api.createFolder("test-token", "Remote folder ID", "child").id)
    }

    @Test fun driveListsPagesAndTrashesActualId() = runBlocking {
        val api = DriveApiClient(mock { request ->
            if (request.method == "PATCH") {
                assertEquals("/drive/v3/files/shortcut-id", request.url.encodedPath)
                assertTrue(JSONObject(body(request)).getBoolean("trashed"))
                "{}"
            } else {
                assertEquals("'parent' in parents and trashed = false", request.url.queryParameter("q"))
                if (request.url.queryParameter("pageToken") == null) {
                    """{"nextPageToken":"next","files":[{"id":"folder-id","name":"Folder","mimeType":"application/vnd.google-apps.folder"}]}"""
                } else {
                    assertEquals("next", request.url.queryParameter("pageToken"))
                    """{"files":[{"id":"shortcut-id","name":"Shortcut","mimeType":"application/vnd.google-apps.shortcut","shortcutDetails":{"targetId":"real-id"}}]}"""
                }
            }
        })
        val children = api.listChildren("test-token", "parent")
        assertEquals(listOf("folder-id", "shortcut-id"), children.map { it.id })
        api.trashFile("test-token", children.last().id)
    }

    private fun body(request: Request): String = Buffer().use { buffer ->
        request.body!!.writeTo(buffer)
        buffer.readUtf8()
    }

    private fun mock(reply: (Request) -> String): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(reply(chain.request()).toResponseBody()).build()
    }.build()
}
