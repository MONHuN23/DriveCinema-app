package com.drivecinema.app.core.network

import com.drivecinema.app.core.metadata.VideoFiles
import com.drivecinema.app.core.model.DriveFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

sealed interface FolderValidationResult {
    data class Valid(
        val filmsFolderId: String? = null,
        val seriesFolderId: String? = null,
        val hasDirectVideos: Boolean = false
    ) : FolderValidationResult
    data class Invalid(val reason: String) : FolderValidationResult
}

class GoogleDriveGateway(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://www.googleapis.com/drive/v3"
) {

    companion object {
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

        fun evaluateFolderChildren(children: List<DriveFolder>): FolderValidationResult {
            val folders = children.filter { it.isFolder }
            val films = folders.find { it.name.equals("Films", ignoreCase = true) }
            val series = folders.find { it.name.equals("Series", ignoreCase = true) }
            val hasDirectVideos = children.any { !it.isFolder && (it.mimeType.startsWith("video/") || VideoFiles.isVideo(it.mimeType, it.name)) }

            return if (films != null || series != null || hasDirectVideos) {
                FolderValidationResult.Valid(
                    filmsFolderId = films?.id,
                    seriesFolderId = series?.id,
                    hasDirectVideos = hasDirectVideos
                )
            } else {
                FolderValidationResult.Invalid("Folder must contain 'Films', 'Series', or direct video files")
            }
        }
    }

    suspend fun listRootFolders(accessToken: String): Result<List<DriveFolder>> = withContext(Dispatchers.IO) {
        val query = "mimeType = '$FOLDER_MIME_TYPE' and trashed = false and ('root' in parents or sharedWithMe = true)"
        queryFolders(query, accessToken)
    }

    suspend fun listFolderChildren(folderId: String, accessToken: String): Result<List<DriveFolder>> = withContext(Dispatchers.IO) {
        val query = "'$folderId' in parents and trashed = false"
        queryFolders(query, accessToken)
    }

    suspend fun validateStreamingFolder(folderId: String, accessToken: String): Result<FolderValidationResult> = withContext(Dispatchers.IO) {
        val childrenResult = listFolderChildren(folderId, accessToken)
        childrenResult.map { children ->
            evaluateFolderChildren(children)
        }
    }

    suspend fun getAboutUser(accessToken: String): Result<com.drivecinema.app.core.model.DriveUser> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/about".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "user(displayName,emailAddress,photoLink)")
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    return@withContext Result.failure(IOException("Google Drive API request failed (${response.code}): $errorBody"))
                }

                val bodyString = response.body?.string() ?: ""
                val json = JSONObject(bodyString)
                val userObj = json.optJSONObject("user")
                    ?: return@withContext Result.failure(IOException("Missing user object in about response"))

                val displayName = userObj.optString("displayName", "")
                val emailAddress = userObj.optString("emailAddress", "")
                val photoLink = userObj.optString("photoLink", "").ifEmpty { null }

                Result.success(
                    com.drivecinema.app.core.model.DriveUser(
                        displayName = displayName,
                        emailAddress = emailAddress,
                        photoLink = photoLink
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findStreamingFolders(accessToken: String): Result<List<DriveFolder>> = withContext(Dispatchers.IO) {
        val query = "mimeType = '$FOLDER_MIME_TYPE' and trashed = false and name = 'Streaming'"
        queryFolders(query, accessToken)
    }

    suspend fun autoDiscoverStreamingFolder(accessToken: String): Result<DriveFolder?> = withContext(Dispatchers.IO) {
        val foldersResult = findStreamingFolders(accessToken)
        foldersResult.mapCatching { folders ->
            if (folders.size == 1) {
                val candidate = folders[0]
                val validation = validateStreamingFolder(candidate.id, accessToken).getOrNull()
                if (validation is FolderValidationResult.Valid) {
                    candidate
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    private fun queryFolders(query: String, accessToken: String): Result<List<DriveFolder>> {
        val list = mutableListOf<DriveFolder>()
        var pageToken: String? = null

        return try {
            do {
                val urlBuilder = "$baseUrl/files".toHttpUrl().newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("fields", "nextPageToken, files(id, name, mimeType, ownedByMe, owners(displayName, emailAddress))")
                    .addQueryParameter("supportsAllDrives", "true")
                    .addQueryParameter("includeItemsFromAllDrives", "true")
                    .addQueryParameter("pageSize", "100")

                if (!pageToken.isNullOrEmpty()) {
                    urlBuilder.addQueryParameter("pageToken", pageToken)
                }

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: ""
                        return Result.failure(IOException("Google Drive API request failed (${response.code}): $errorBody"))
                    }

                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    val filesArray = json.optJSONArray("files") ?: org.json.JSONArray()

                    for (i in 0 until filesArray.length()) {
                        val fileObj = filesArray.getJSONObject(i)
                        val id = fileObj.getString("id")
                        val name = fileObj.getString("name")
                        val mimeType = fileObj.optString("mimeType", "")
                        val isFolder = mimeType == FOLDER_MIME_TYPE
                        // Missing ownedByMe (e.g. shared drives) is treated as "not mine" only when an owner is listed.
                        val owner = fileObj.optJSONArray("owners")?.optJSONObject(0)
                        val ownerName = owner?.optString("displayName", "")?.ifEmpty { null }
                            ?: owner?.optString("emailAddress", "")?.ifEmpty { null }
                        val ownedByMe = if (fileObj.has("ownedByMe")) fileObj.optBoolean("ownedByMe", true) else owner == null
                        list.add(
                            DriveFolder(
                                id = id,
                                name = name,
                                isFolder = isFolder,
                                mimeType = mimeType,
                                ownedByMe = ownedByMe,
                                ownerName = if (ownedByMe) null else ownerName
                            )
                        )
                    }

                    pageToken = json.optString("nextPageToken", "").ifEmpty { null }
                }
            } while (pageToken != null)

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
