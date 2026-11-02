package com.drivecinema.app.core.devicesync

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Milestone Q: the only Drive calls device sync makes, all inside `appDataFolder` (design §6). */
class AppDataClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AppDataClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = AppDataClient(OkHttpClient(), baseUrl = server.url("/drive/v3").toString().trimEnd('/'),
            uploadUrl = server.url("/upload/drive/v3").toString().trimEnd('/'))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun listReadsEveryPageOfTheAppFolderOnly() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"nextPageToken":"p2","files":[{"id":"a","name":"progress-1.json","modifiedTime":"2026-10-29T10:00:00Z"}]}"""))
        server.enqueue(MockResponse().setBody("""{"files":[{"id":"b","name":"progress-2.json"},{"name":"no id"}]}"""))
        val files = client.listSyncFiles("tok")
        assertEquals(listOf(AppDataFile("a", "progress-1.json", "2026-10-29T10:00:00Z"), AppDataFile("b", "progress-2.json", null)), files)

        val first = server.takeRequest()
        assertEquals("GET", first.method)
        assertEquals("Bearer tok", first.getHeader("Authorization"))
        assertEquals("/drive/v3/files", first.requestUrl!!.encodedPath)
        assertEquals("appDataFolder", first.requestUrl!!.queryParameter("spaces"))
        assertEquals("p2", server.takeRequest().requestUrl!!.queryParameter("pageToken"))
    }

    @Test
    fun downloadCreateAndUpdateUseTheirOwnEndpoints() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"format\":1}"))
        server.enqueue(MockResponse().setBody("""{"id":"new1"}"""))
        server.enqueue(MockResponse().setBody("""{"id":"new1"}"""))

        assertEquals("{\"format\":1}", client.download("a", "tok"))
        assertEquals("new1", client.create("progress-1.json", "{\"x\":1}", "tok"))
        client.update("new1", "{\"x\":2}", "tok")

        val get = server.takeRequest()
        assertEquals("GET /drive/v3/files/a?alt=media", "${get.method} ${get.path}")
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/upload/drive/v3/files", create.requestUrl!!.encodedPath)
        assertEquals("multipart", create.requestUrl!!.queryParameter("uploadType"))
        val body = create.body.readUtf8()
        val metadata = JSONObject(body.substring(body.indexOf("{\"name\""), body.indexOf("}", body.indexOf("{\"name\"")) + 1))
        assertEquals("progress-1.json", metadata.getString("name"))
        assertEquals("the file is created in the app folder", "appDataFolder", metadata.getJSONArray("parents").getString(0))
        assertTrue(body.contains("{\"x\":1}"))
        val update = server.takeRequest()
        assertEquals("PATCH", update.method)
        assertEquals("/upload/drive/v3/files/new1", update.requestUrl!!.encodedPath)
        assertEquals("media", update.requestUrl!!.queryParameter("uploadType"))
        assertEquals("{\"x\":2}", update.body.readUtf8())
    }

    @Test
    fun aMissingScopeIsTellableFromOtherErrors() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"code":403,"message":"Request had insufficient authentication scopes."}}"""))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"code":403,"message":"User rate limit exceeded"}}"""))
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(failure { client.listSyncFiles("tok") }.needsPermission)
        assertFalse("a rate limit", failure { client.listSyncFiles("tok") }.needsPermission)
        assertEquals(500, failure { client.download("a", "tok") }.code)
    }

    private suspend fun failure(block: suspend () -> Unit): AppDataException {
        try { block() } catch (e: AppDataException) { return e }
        fail("expected an AppDataException")
        throw AssertionError()
    }
}
