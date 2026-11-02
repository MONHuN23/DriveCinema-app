package com.drivecinema.app.core.devicesync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** A file in the app folder, as listed. */
data class AppDataFile(val id: String, val name: String, val modifiedTime: String?)

/** A failed app folder call. [needsPermission]: the sign-in has no `drive.appdata` scope (insufficient scopes). */
class AppDataException(message: String, val code: Int, val needsPermission: Boolean) : IOException(message)

/**
 * Milestone Q: the four calls device sync makes, all inside the Google account's hidden app folder
 * (`spaces=appDataFolder`, scope `drive.appdata`): list, download, create, update. Nothing else is ever sent, and only
 * to [baseUrl] / [uploadUrl] (`www.googleapis.com` over HTTPS in the app).
 */
class AppDataClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://www.googleapis.com/drive/v3",
    private val uploadUrl: String = "https://www.googleapis.com/upload/drive/v3"
) : AppFolder {
    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val APP_FOLDER = "appDataFolder"
        private val JSON = "application/json; charset=UTF-8".toMediaType()
    }

    /** Every sync file in the app folder (all pages). */
    override suspend fun listSyncFiles(accessToken: String): List<AppDataFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<AppDataFile>()
        var pageToken: String? = null
        do {
            val url = "$baseUrl/files".toHttpUrl().newBuilder()
                .addQueryParameter("spaces", APP_FOLDER)
                .addQueryParameter("q", "name contains '${SyncFile.FILE_PREFIX}' and trashed = false")
                .addQueryParameter("fields", "nextPageToken,files(id,name,modifiedTime)")
                .addQueryParameter("pageSize", "100")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val body = JSONObject(execute(Request.Builder().url(url).get(), accessToken))
            val array = body.optJSONArray("files") ?: JSONArray()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.isNotEmpty() } ?: continue
                files += AppDataFile(id, o.optString("name"), o.optString("modifiedTime").takeIf { it.isNotEmpty() })
            }
            pageToken = body.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        files
    }

    override suspend fun download(fileId: String, accessToken: String): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl/files/$fileId".toHttpUrl().newBuilder().addQueryParameter("alt", "media").build()
        execute(Request.Builder().url(url).get(), accessToken)
    }

    /** Creates [name] in the app folder; returns its file ID. */
    override suspend fun create(name: String, content: String, accessToken: String): String = withContext(Dispatchers.IO) {
        val metadata = JSONObject().put("name", name).put("parents", JSONArray().put(APP_FOLDER)).toString()
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody(JSON))
            .addPart(content.toRequestBody(JSON))
            .build()
        val url = "$uploadUrl/files".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id")
            .build()
        val id = JSONObject(execute(Request.Builder().url(url).post(body), accessToken)).optString("id")
        if (id.isEmpty()) throw AppDataException("Drive created the sync file without an ID", 0, needsPermission = false)
        id
    }

    /** Replaces the content of the app folder file [fileId]. */
    override suspend fun update(fileId: String, content: String, accessToken: String) = withContext(Dispatchers.IO) {
        val url = "$uploadUrl/files/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "media")
            .addQueryParameter("fields", "id")
            .build()
        execute(Request.Builder().url(url).patch(content.toRequestBody(JSON)), accessToken)
        Unit
    }

    private fun execute(builder: Request.Builder, accessToken: String): String {
        val request = builder.header("Authorization", "Bearer $accessToken").build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.isSuccessful) return text
            throw AppDataException(
                "Drive app folder: HTTP ${response.code}",
                response.code,
                // "Request had insufficient authentication scopes" (ACCESS_TOKEN_SCOPE_INSUFFICIENT); a 403 rate limit isn't one.
                needsPermission = (response.code == 403 || response.code == 401) && text.contains("insufficient", ignoreCase = true)
            )
        }
    }
}
