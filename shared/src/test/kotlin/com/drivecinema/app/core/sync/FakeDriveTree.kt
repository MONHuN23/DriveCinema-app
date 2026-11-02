package com.drivecinema.app.core.sync

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Task N.1: a Drive folder tree served like `files.list`: one or many `'id' in parents` per query, each file with its
 * `parents`, pages of at most [pageLimit] files, and an optional limit on the folders one query may name (more →
 * HTTP 400, as a query Drive finds too long). [latencyMs] delays every answer, to model a slow connection.
 */
class FakeDriveTree(
    private val pageLimit: Int = 1000,
    private val maxParentsAccepted: Int = Int.MAX_VALUE,
    var latencyMs: Long = 0
) {
    private val children = LinkedHashMap<String, MutableList<JSONObject>>()
    val requests = AtomicInteger()
    val rejected = AtomicInteger()
    /** The folders each accepted request asked for, in order. */
    val asked: MutableList<List<String>> = Collections.synchronizedList(mutableListOf())
    /** Called with the folders of each request before it is answered (tests block or count here). */
    @Volatile var onRequest: (List<String>) -> Unit = {}

    val folderCount: Int get() = children.keys.size

    fun folder(parent: String, id: String, name: String) = add(parent, JSONObject().put("id", id).put("name", name).put("mimeType", FolderQueueCrawler.FOLDER_MIME_TYPE)).also { children.getOrPut(id) { mutableListOf() } }

    fun video(parent: String, id: String, name: String, size: Long? = null) =
        add(parent, JSONObject().put("id", id).put("name", name).put("mimeType", "video/x-matroska").apply { size?.let { put("size", it.toString()) } })

    fun file(parent: String, id: String, name: String, mimeType: String) = add(parent, JSONObject().put("id", id).put("name", name).put("mimeType", mimeType))

    /** The same tree, e.g. to serve it with other limits. */
    fun copyFrom(other: FakeDriveTree) {
        other.children.forEach { (id, files) -> children[id] = files.map { JSONObject(it.toString()) }.toMutableList() }
    }

    private fun add(parent: String, file: JSONObject) {
        children.getOrPut(parent) { mutableListOf() }.add(file)
    }

    val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
            val ids = FakeDriveListing.folderIds(url.queryParameter("q"))
            if (ids.isEmpty()) return MockResponse().setResponseCode(404)
            requests.incrementAndGet()
            if (ids.size > maxParentsAccepted) {
                rejected.incrementAndGet()
                return MockResponse().setResponseCode(400).setBody("""{"error":{"code":400,"message":"The query is too complex."}}""")
            }
            onRequest(ids)
            asked += ids
            val all = ids.flatMap { id -> children[id].orEmpty().map { JSONObject(it.toString()).put("parents", JSONArray().put(id)) } }
            val offset = url.queryParameter("pageToken")?.removePrefix("p")?.toInt() ?: 0
            val size = minOf(pageLimit, url.queryParameter("pageSize")?.toInt() ?: 100)
            val page = all.drop(offset).take(size)
            val body = JSONObject().put("files", JSONArray(page))
            if (offset + size < all.size) body.put("nextPageToken", "p${offset + size}")
            return MockResponse().setBody(body.toString()).apply { if (latencyMs > 0) setHeadersDelay(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS) }
        }
    }
}
