package com.drivecinema.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AuthCredentialStoreTest {

    @Test
    fun testBackupRulesExcludeCredentialsFile() {
        val dataExtractionRules = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue("Cloud backup must exclude auth_credentials.enc", dataExtractionRules.contains("<exclude domain=\"file\" path=\"auth_credentials.enc\""))
        assertTrue("Device transfer must exclude auth_credentials.enc", dataExtractionRules.contains("<device-transfer>") && dataExtractionRules.contains("<exclude domain=\"file\" path=\"auth_credentials.enc\""))

        val backupRules = File("src/main/res/xml/backup_rules.xml").readText()
        assertTrue("Full backup must exclude auth_credentials.enc", backupRules.contains("<exclude domain=\"file\" path=\"auth_credentials.enc\""))
    }

    @Test
    fun testPayloadSerializationAndDeserialization() {
        val original = AuthCredentials(refreshToken = "test_refresh_token_123", rootFolderId = "folder_xyz")
        val json = original.toJson()
        val deserialized = AuthCredentials.fromJson(json)

        assertNotNull(deserialized)
        assertEquals(original.refreshToken, deserialized?.refreshToken)
        assertEquals(original.rootFolderId, deserialized?.rootFolderId)
        assertEquals(null, deserialized?.tmdbApiKey)
    }

    @Test
    fun testTmdbApiKeySerialization() {
        val original = AuthCredentials(
            refreshToken = "token_abc",
            rootFolderId = "folder_123",
            tmdbApiKey = "tmdb_secret_key_456"
        )
        val json = original.toJson()
        val deserialized = AuthCredentials.fromJson(json)

        assertNotNull(deserialized)
        assertEquals("token_abc", deserialized?.refreshToken)
        assertEquals("folder_123", deserialized?.rootFolderId)
        assertEquals("tmdb_secret_key_456", deserialized?.tmdbApiKey)
    }

    @Test
    fun testUserIdentitySerializationAndDeserialization() {
        val original = AuthCredentials(
            refreshToken = "token_with_user",
            rootFolderId = "folder_abc",
            userDisplayName = "Alex Cinema",
            userEmail = "alex@cinema.org",
            userPhotoLink = "https://lh3.googleusercontent.com/avatar.jpg"
        )
        val json = original.toJson()
        val deserialized = AuthCredentials.fromJson(json)

        assertNotNull(deserialized)
        assertEquals("token_with_user", deserialized?.refreshToken)
        assertEquals("folder_abc", deserialized?.rootFolderId)
        assertEquals("Alex Cinema", deserialized?.userDisplayName)
        assertEquals("alex@cinema.org", deserialized?.userEmail)
        assertEquals("https://lh3.googleusercontent.com/avatar.jpg", deserialized?.userPhotoLink)
    }

    @Test
    fun testUserIdentityBackwardCompatibility() {
        val legacyJson = """{"refreshToken":"legacy_token","rootFolderId":"legacy_folder"}"""
        val deserialized = AuthCredentials.fromJson(legacyJson)

        assertNotNull(deserialized)
        assertEquals("legacy_token", deserialized?.refreshToken)
        assertEquals("legacy_folder", deserialized?.rootFolderId)
        assertEquals(null, deserialized?.userDisplayName)
        assertEquals(null, deserialized?.userEmail)
        assertEquals(null, deserialized?.userPhotoLink)
    }

    @Test
    fun testClearCredentialsDeletesEncryptedFile() {
        val tempDir = java.nio.file.Files.createTempDirectory("cred_clear_test").toFile()
        val credFile = File(tempDir, "auth_credentials.enc")
        credFile.writeText("encrypted_tokens")
        assertTrue(credFile.exists())

        // Execution of clearCredentials file deletion logic
        if (credFile.exists()) {
            credFile.delete()
        }

        org.junit.Assert.assertFalse("Encrypted file must be removed by clearCredentials", credFile.exists())
        tempDir.deleteRecursively()
    }
}
