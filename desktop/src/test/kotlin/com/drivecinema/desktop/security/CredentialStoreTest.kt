package com.drivecinema.desktop.security

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class CredentialStoreTest {

    private lateinit var dir: File
    private val credentials = StoredCredentials("1//refresh", "Ana Example", "ana@example.com", "https://photo")

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("drivecinema-cred").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun jsonRoundTripAndNoTokenInToString() {
        assertEquals(credentials, StoredCredentials.fromJson(credentials.toJson()))
        assertEquals(credentials.copy(photoLink = null), StoredCredentials.fromJson(credentials.copy(photoLink = null).toJson()))
        assertFalse(credentials.toString().contains("1//refresh"))
    }

    @Test
    fun unreadableOrEmptyTokenIsIgnored() {
        assertNull(StoredCredentials.fromJson("not json"))
        assertNull(StoredCredentials.fromJson("""{"refreshToken":"","email":"a"}"""))
    }

    @Test
    fun fileStoreSavesLoadsAndClears() {
        val file = File(dir, "credentials.json")
        val store = FileCredentialStore(file)
        assertNull(store.load())

        store.save(credentials)
        assertEquals(credentials, store.load())

        store.save(credentials.copy(refreshToken = "1//rotated"))
        assertEquals("1//rotated", store.load()!!.refreshToken)

        store.clear()
        assertNull(store.load())
        assertFalse(file.exists())
    }

    @Test
    fun fileStoreIsReadableOnlyByTheOwner() {
        assumeTrue(File("/").toPath().fileSystem.supportedFileAttributeViews().contains("posix"))
        val file = File(dir, "credentials.json")
        FileCredentialStore(file).save(credentials)
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(file.toPath())
        )
    }

    @Test
    fun linuxUsesTheKeyringWhenAvailableOtherwiseTheFile() {
        assertTrue(CredentialStores.forCurrentOs(dir, "Linux") { true } is SecretServiceCredentialStore)
        val fallback = CredentialStores.forCurrentOs(dir, "Linux") { false }
        assertTrue(fallback is FileCredentialStore)
        assertFalse(fallback.isSystemKeyring)
        assertTrue(CredentialStores.forCurrentOs(dir, "Windows 11") { true } is FileCredentialStore)
    }

    /**
     * Talks to the real keyring with a separate test entry (never the app's own one) and removes it again.
     * Opt-in: `DRIVECINEMA_KEYRING_TEST=1 ./gradlew :desktop:test`.
     */
    @Test
    fun realKeyringRoundTrip() {
        assumeTrue(System.getenv("DRIVECINEMA_KEYRING_TEST") == "1")
        assumeTrue(SecretServiceCredentialStore.isAvailable())
        val store = SecretServiceCredentialStore(account = "unit-test-${System.nanoTime()}")
        try {
            assertNull(store.load())
            store.save(credentials)
            assertEquals(credentials, store.load())
        } finally {
            store.clear()
        }
        assertNull(store.load())
    }
}
