package com.drivecinema.app.core.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * Task N.1: answers the crawler's folder listings from a test's own tree. One query may ask for many folders
 * (`('a' in parents or 'b' in parents) and trashed = false`); the answer lists every asked folder's files, each with
 * its `parents`, as Drive does. (A copy of the `:shared` test helper of the same name.)
 */
object FakeDriveListing {
    private val parentClause = Regex("""'([^']+)' in parents""")

    /** The folders a listing query asks for, in order; empty for any other request. */
    fun folderIds(q: String?): List<String> = parentClause.findAll(q.orEmpty()).map { it.groupValues[1] }.toList()

    /** `{"files": […]}` with the files (JSON objects as text) of every folder in [folderIds], each with its parent. */
    fun body(folderIds: List<String>, filesOf: (String) -> List<String>): String {
        val files = JSONArray()
        for (id in folderIds) {
            for (file in filesOf(id)) files.put(JSONObject(file).put("parents", JSONArray().put(id)))
        }
        return JSONObject().put("files", files).toString()
    }
}
