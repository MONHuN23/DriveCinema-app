package com.drivecinema.desktop.importer

import com.drivecinema.app.core.network.GoogleDriveGateway
import com.drivecinema.app.core.player.TokenProvider
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * A small in-memory Google Drive on MockWebServer for the import tests: folder listings, folder creation and
 * resumable uploads (308 + Range, Content-Range status queries), with injectable failures.
 */
class FakeDrive : AutoCloseable {
    data class Item(val id: String, val name: String, val mimeType: String, val parent: String, var bytes: ByteArray? = null)
    private class Session(val name: String, val parent: String, val total: Long, val received: ByteArrayOutputStream = ByteArrayOutputStream())

    val server = MockWebServer()
    val items = CopyOnWriteArrayList<Item>()
    /** "METHOD /path" of every request, for checking that nothing but list/create/upload is ever sent. */
    val calls = CopyOnWriteArrayList<String>()
    val authorizations = CopyOnWriteArrayList<String?>()
    /** Answers to use instead of the normal ones, for the next requests whose "METHOD /path" starts with the key. */
    val failures = ConcurrentLinkedQueue<Pair<String, MockResponse>>()
    /** When set, a chunk PUT stores only this many bytes and answers 308 (Drive may keep less than was sent). */
    @Volatile var partialChunkLimit: Int? = null
    private val sessions = HashMap<String, Session>()
    private val ids = AtomicInteger()

    val tokens = object : TokenProvider {
        override fun getAccessToken() = "ya29.test"
        override fun forceRefresh() = "ya29.test"
    }

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val call = "${request.method} ${request.requestUrl!!.encodedPath}"
                calls += call
                authorizations += request.getHeader("Authorization")
                failures.firstOrNull { call.startsWith(it.first) }?.let { failures.remove(it); return it.second }
                return synchronized(this@FakeDrive) { handle(request) }
            }
        }
        server.start()
    }

    fun client(baseClient: OkHttpClient = OkHttpClient()) = DriveWriteClient(
        tokens,
        baseClient,
        apiBase = server.url("/drive/v3").toString().trimEnd('/'),
        uploadBase = server.url("/upload/drive/v3").toString().trimEnd('/')
    )

    fun folder(name: String, parent: String = "root", id: String = "f${ids.incrementAndGet()}"): String {
        items += Item(id, name, GoogleDriveGateway.FOLDER_MIME_TYPE, parent)
        return id
    }

    fun file(name: String, parent: String, bytes: ByteArray): String {
        val id = "file${ids.incrementAndGet()}"
        items += Item(id, name, "video/x-matroska", parent, bytes)
        return id
    }

    fun childrenOf(parent: String) = items.filter { it.parent == parent }

    fun sessionUrl(n: Int) = server.url("/upload/session/s$n").toString()

    private fun handle(request: RecordedRequest): MockResponse {
        val url = request.requestUrl!!
        val path = url.encodedPath
        return when {
            request.method == "GET" && path == "/drive/v3/files" -> list(url.queryParameter("q")!!)
            request.method == "POST" && path == "/drive/v3/files" -> {
                val body = JSONObject(request.body.readUtf8())
                val id = folder(body.getString("name"), body.getJSONArray("parents").getString(0))
                MockResponse().setBody(JSONObject().put("id", id).put("name", body.getString("name")).toString())
            }
            request.method == "POST" && path == "/upload/drive/v3/files" -> {
                require(url.queryParameter("uploadType") == "resumable")
                val body = JSONObject(request.body.readUtf8())
                val key = "s${sessions.size + 1}"
                sessions[key] = Session(body.getString("name"), body.getJSONArray("parents").getString(0), request.getHeader("X-Upload-Content-Length")!!.toLong())
                MockResponse().setHeader("Location", server.url("/upload/session/$key").toString())
            }
            request.method == "PUT" && path.startsWith("/upload/session/") -> chunk(path.substringAfterLast('/'), request)
            else -> MockResponse().setResponseCode(404)
        }
    }

    private fun list(q: String): MockResponse {
        val parent = Regex("""^'([^']+)' in parents""").find(q)!!.groupValues[1]
        val name = Regex("""name = '((?:[^'\\]|\\.)*)'""").find(q)?.groupValues?.get(1)?.replace("\\'", "'")?.replace("\\\\", "\\")
        val files = childrenOf(parent).filter { name == null || it.name == name }.map {
            JSONObject().put("id", it.id).put("name", it.name).put("mimeType", it.mimeType)
                .apply { it.bytes?.let { b -> put("size", b.size.toString()) } }
        }
        return MockResponse().setBody(JSONObject().put("files", JSONArray(files)).toString())
    }

    private fun chunk(key: String, request: RecordedRequest): MockResponse {
        val session = sessions[key] ?: return MockResponse().setResponseCode(404)
        val range = request.getHeader("Content-Range")!!
        val body = request.body.readByteArray()
        if (!range.startsWith("bytes */")) {
            val start = range.removePrefix("bytes ").substringBefore('-').toLong()
            require(start == session.received.size().toLong()) { "chunk starts at $start, Drive has ${session.received.size()}" }
            val kept = partialChunkLimit?.let { body.copyOf(minOf(it, body.size)) } ?: body
            session.received.write(kept)
        }
        val have = session.received.size().toLong()
        if (have < session.total) {
            return MockResponse().setResponseCode(308).apply { if (have > 0) setHeader("Range", "bytes=0-${have - 1}") }
        }
        // A status query after completion answers 200 again without creating a second file.
        val id = "upload-$key"
        if (items.none { it.id == id }) items += Item(id, session.name, "video/x-matroska", session.parent, session.received.toByteArray())
        return MockResponse().setResponseCode(200).setBody(JSONObject().put("id", id).put("name", session.name).toString())
    }

    override fun close() = server.shutdown()
}
