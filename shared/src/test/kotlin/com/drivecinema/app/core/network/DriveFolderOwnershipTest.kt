package com.drivecinema.app.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The folder list tells the user's own folders apart from folders other people shared with them. */
class DriveFolderOwnershipTest {

    @Test
    fun listRootFoldersMarksOwnAndSharedFoldersWithOwnerName() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"files":[
                  {"id":"mine1","name":"Streaming","mimeType":"application/vnd.google-apps.folder","ownedByMe":true,
                   "owners":[{"displayName":"Bob","emailAddress":"bob@example.com"}]},
                  {"id":"shared1","name":"Streaming","mimeType":"application/vnd.google-apps.folder","ownedByMe":false,
                   "owners":[{"displayName":"Janos","emailAddress":"janos@example.com"}]},
                  {"id":"shared2","name":"Movies","mimeType":"application/vnd.google-apps.folder","ownedByMe":false,
                   "owners":[{"emailAddress":"anna@example.com"}]}
                ]}
                """.trimIndent()
            )
        )
        server.start()
        try {
            val gateway = GoogleDriveGateway(baseUrl = server.url("/drive/v3").toString().removeSuffix("/"))
            val folders = gateway.listRootFolders("token").getOrThrow().associateBy { it.id }

            assertTrue(folders.getValue("mine1").ownedByMe)
            assertNull("Own folders need no owner label", folders.getValue("mine1").ownerName)
            assertFalse(folders.getValue("shared1").ownedByMe)
            assertEquals("Janos", folders.getValue("shared1").ownerName)
            assertEquals("Falls back to the owner's email", "anna@example.com", folders.getValue("shared2").ownerName)

            val fields = server.takeRequest().requestUrl!!.queryParameter("fields")!!
            assertTrue(fields.contains("ownedByMe"))
            assertTrue(fields.contains("owners(displayName, emailAddress)"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun responsesWithoutOwnershipFieldsStillCountAsOwnFolders() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"files":[{"id":"f1","name":"Films","mimeType":"application/vnd.google-apps.folder"}]}"""
            )
        )
        server.start()
        try {
            val gateway = GoogleDriveGateway(baseUrl = server.url("/drive/v3").toString().removeSuffix("/"))
            val folder = gateway.listRootFolders("token").getOrThrow().single()
            assertTrue(folder.ownedByMe)
            assertNull(folder.ownerName)
        } finally {
            server.shutdown()
        }
    }
}
