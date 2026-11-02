package com.drivecinema.app.core.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * Task N.1: answers the crawler's folder listings from a test's own tree. One query may ask for many folders
 * (`('a' in parents or 'b' in parents) and trashed = false`); the answer lists every asked folder's files, each with
 * its `parents`, as Drive does. (Copies of this helper live in the app and desktop tests.)
 */
object FakeDriveListing {
    private val parentClause = Regex("""'([^']+)' in parents""")

    /** The folders a listing query asks for, in order; empty for any other request. */
    fun folderIds(q: String?): List<String> = parentClause.findAll(q.orEmpty()).map { it.groupValues[1] }.toList()

    /** `{"files": […]}` with the files of every folder in [folderIds], each tagged with that folder as its parent. */
    fun body(folderIds: List<String>, filesOf: (String) -> List<JSONObject>): String {
        val files = JSONArray()
        for (id in folderIds) {
            for (file in filesOf(id)) files.put(JSONObject(file.toString()).put("parents", JSONArray().put(id)))
        }
        return JSONObject().put("files", files).toString()
    }

    /** Same, for trees written as JSON text (`[{"id":…}, …]` per folder). */
    fun bodyFromJson(folderIds: List<String>, filesOf: (String) -> String?): String =
        body(folderIds) { id -> filesOf(id)?.let { json -> JSONArray(json).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } }.orEmpty() }
}
