package com.drivecinema.app.core.network

import com.drivecinema.app.core.model.DriveFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveGatewayTest {
    @Test
    fun testValidateStreamingFolderRequiresFilmsAndSeries() {
        val children = listOf(
            DriveFolder("f1", "Films", isFolder = true),
            DriveFolder("f2", "Series", isFolder = true)
        )
        val result = GoogleDriveGateway.evaluateFolderChildren(children)
        assertTrue(result is FolderValidationResult.Valid)
        val valid = result as FolderValidationResult.Valid
        assertEquals("f1", valid.filmsFolderId)
        assertEquals("f2", valid.seriesFolderId)
        assertEquals(false, valid.hasDirectVideos)
    }

    @Test
    fun testValidateStreamingFolderAllowsFilmsOnly() {
        val children = listOf(
            DriveFolder("f1", "Films", isFolder = true)
        )
        val result = GoogleDriveGateway.evaluateFolderChildren(children)
        assertTrue(result is FolderValidationResult.Valid)
        val valid = result as FolderValidationResult.Valid
        assertEquals("f1", valid.filmsFolderId)
        assertEquals(null, valid.seriesFolderId)
        assertEquals(false, valid.hasDirectVideos)
    }

    @Test
    fun testValidateStreamingFolderAllowsSeriesOnly() {
        val children = listOf(
            DriveFolder("f2", "Series", isFolder = true)
        )
        val result = GoogleDriveGateway.evaluateFolderChildren(children)
        assertTrue(result is FolderValidationResult.Valid)
        val valid = result as FolderValidationResult.Valid
        assertEquals(null, valid.filmsFolderId)
        assertEquals("f2", valid.seriesFolderId)
        assertEquals(false, valid.hasDirectVideos)
    }

    @Test
    fun testValidateStreamingFolderAllowsDirectVideos() {
        val children = listOf(
            DriveFolder("v1", "Movie.mkv", isFolder = false, mimeType = "video/x-matroska"),
            DriveFolder("d1", "Readme.txt", isFolder = false, mimeType = "text/plain")
        )
        val result = GoogleDriveGateway.evaluateFolderChildren(children)
        assertTrue(result is FolderValidationResult.Valid)
        val valid = result as FolderValidationResult.Valid
        assertEquals(null, valid.filmsFolderId)
        assertEquals(null, valid.seriesFolderId)
        assertEquals(true, valid.hasDirectVideos)
    }

    @Test
    fun testValidateStreamingFolderFailsWhenNoMediaOrSubfolders() {
        val children = listOf(
            DriveFolder("d1", "Readme.txt", isFolder = false, mimeType = "text/plain"),
            DriveFolder("f_other", "Documents", isFolder = true, mimeType = "application/vnd.google-apps.folder")
        )
        val result = GoogleDriveGateway.evaluateFolderChildren(children)
        assertTrue(result is FolderValidationResult.Invalid)
        assertEquals("Folder must contain 'Films', 'Series', or direct video files", (result as FolderValidationResult.Invalid).reason)
    }

    @Test
    fun testValidateStreamingFolderNetworkSuccess() = kotlinx.coroutines.runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()

        val jsonResponse = """
            {
                "files": [
                    {"id": "id_films", "name": "Films", "mimeType": "application/vnd.google-apps.folder"},
                    {"id": "id_series", "name": "Series", "mimeType": "application/vnd.google-apps.folder"},
                    {"id": "id_doc", "name": "Readme.txt", "mimeType": "text/plain"}
                ]
            }
        """.trimIndent()

        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(jsonResponse))

        val gateway = GoogleDriveGateway(baseUrl = server.url("/drive/v3").toString().removeSuffix("/"))
        val result = gateway.validateStreamingFolder("root_id", "test_access_token")

        val recordedRequest = server.takeRequest()
        assertEquals("Bearer test_access_token", recordedRequest.getHeader("Authorization"))
        assertTrue(recordedRequest.path!!.contains("files?q="))

        assertTrue(result.isSuccess)
        val validation = result.getOrNull()
        assertTrue(validation is FolderValidationResult.Valid)
        val valid = validation as FolderValidationResult.Valid
        assertEquals("id_films", valid.filmsFolderId)
        assertEquals("id_series", valid.seriesFolderId)

        server.shutdown()
    }

    @Test
    fun testFetchAboutUserSuccess() = kotlinx.coroutines.runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()

        val jsonResponse = """
            {
                "user": {
                    "displayName": "Alex Cinema",
                    "emailAddress": "alex@cinema.org",
                    "photoLink": "https://lh3.googleusercontent.com/photo.jpg"
                }
            }
        """.trimIndent()

        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(jsonResponse))

        val gateway = GoogleDriveGateway(baseUrl = server.url("/drive/v3").toString().removeSuffix("/"))
        val result = gateway.getAboutUser("test_token_123")

        val recordedRequest = server.takeRequest()
        assertEquals("Bearer test_token_123", recordedRequest.getHeader("Authorization"))
        assertTrue(recordedRequest.path!!.startsWith("/drive/v3/about?"))
        assertEquals("user(displayName,emailAddress,photoLink)", recordedRequest.requestUrl?.queryParameter("fields"))

        assertTrue(result.isSuccess)
        val user = result.getOrNull()
        org.junit.Assert.assertNotNull(user)
        assertEquals("Alex Cinema", user?.displayName)
        assertEquals("alex@cinema.org", user?.emailAddress)
        assertEquals("https://lh3.googleusercontent.com/photo.jpg", user?.photoLink)

        server.shutdown()
    }

    @Test
    fun testFetchAboutUserFailure() = kotlinx.coroutines.runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()

        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val gateway = GoogleDriveGateway(baseUrl = server.url("/drive/v3").toString().removeSuffix("/"))
        val result = gateway.getAboutUser("expired_token")

        assertTrue(result.isFailure)

        server.shutdown()
    }

    @Test
    fun testQueryFoldersPaginationFollowsNextPageToken() = kotlinx.coroutines.runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()

        val page1Files = (1..60).joinToString(",") { i ->
            """{"id": "folder_$i", "name": "Folder $i", "mimeType": "application/vnd.google-apps.folder"}"""
        }
        val page1Json = """
            {
                "nextPageToken": "token_page_2",
                "files": [$page1Files]
            }
        """.trimIndent()

        val page2Files = (61..110).joinToString(",") { i ->
            """{"id": "folder_$i", "name": "Folder $i", "mimeType": "application/vnd.google-apps.folder"}"""
        }
        val page2Json = """
            {
                "files": [$page2Files]
            }
        """.trimIndent()

        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(page1Json))
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(page2Json))

        val gateway = GoogleDriveGateway(baseUrl = server.url("/drive/v3").toString().removeSuffix("/"))
        val result = gateway.listRootFolders("test_token_paginated")

        assertTrue(result.isSuccess)
        val folders = result.getOrNull()
        org.junit.Assert.assertNotNull(folders)
        assertEquals(110, folders?.size)

        val req1 = server.takeRequest()
        assertEquals(null, req1.requestUrl?.queryParameter("pageToken"))

        val req2 = server.takeRequest()
        assertEquals("token_page_2", req2.requestUrl?.queryParameter("pageToken"))

        server.shutdown()
    }

    @Test
    fun testAutoDiscoverStreamingFolderWithZeroMatches() = kotlinx.coroutines.runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()

        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody("""{"files": []}"""))

        val gateway = GoogleDriveGateway(baseUrl = server.url("/drive/v3").toString().removeSuffix("/"))
        val result = gateway.autoDiscoverStreamingFolder("test_token")

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())

        server.shutdown()
    }

    @Test
    fun testAutoDiscoverStreamingFolderWithSingleValidMatch() = kotlinx.coroutines.runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()

        // 1. findStreamingFolders response
        val searchJson = """
            {
                "files": [
                    {"id": "streaming_123", "name": "Streaming", "mimeType": "application/vnd.google-apps.folder"}
                ]
            }
        """.trimIndent()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(searchJson))

        // 2. validateStreamingFolder children query
        val childrenJson = """
            {
                "files": [
                    {"id": "films_456", "name": "Films", "mimeType": "application/vnd.google-apps.folder"}
                ]
            }
        """.trimIndent()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(childrenJson))

        val gateway = GoogleDriveGateway(baseUrl = server.url("/drive/v3").toString().removeSuffix("/"))
        val result = gateway.autoDiscoverStreamingFolder("test_token")

        assertTrue(result.isSuccess)
        val discovered = result.getOrNull()
        org.junit.Assert.assertNotNull(discovered)
        assertEquals("streaming_123", discovered?.id)
        assertEquals("Streaming", discovered?.name)

        server.shutdown()
    }

    @Test
    fun testAutoDiscoverStreamingFolderWithMultipleMatchesReturnsNull() = kotlinx.coroutines.runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()

        val searchJson = """
            {
                "files": [
                    {"id": "streaming_1", "name": "Streaming", "mimeType": "application/vnd.google-apps.folder"},
                    {"id": "streaming_2", "name": "Streaming", "mimeType": "application/vnd.google-apps.folder"}
                ]
            }
        """.trimIndent()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(searchJson))

        val gateway = GoogleDriveGateway(baseUrl = server.url("/drive/v3").toString().removeSuffix("/"))
        val result = gateway.autoDiscoverStreamingFolder("test_token")

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())

        server.shutdown()
    }
}
