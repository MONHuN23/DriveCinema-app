package com.drivecinema.desktop.security

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
        assertTrue(CredentialStores.forCurrentOs(dir, "Linux", secretServiceAvailable = { true }) is SecretServiceCredentialStore)
        val fallback = CredentialStores.forCurrentOs(dir, "Linux", secretServiceAvailable = { false })
        assertTrue(fallback is FileCredentialStore)
        assertFalse(fallback.isSystemKeyring)
        assertTrue(CredentialStores.forCurrentOs(dir, "Mac OS X", secretServiceAvailable = { true }) is FileCredentialStore)
    }

    @Test
    fun windowsUsesCredentialManagerWhenAvailableOtherwiseTheFile() {
        val manager = InMemoryCredentialStore()
        val chosen = CredentialStores.forCurrentOs(
            dir, "Windows 11", secretServiceAvailable = { true },
            windowsCredentialsAvailable = { true }, windowsStore = { manager }
        )
        assertSame(manager, chosen)
        val fallback = CredentialStores.forCurrentOs(dir, "Windows 11", secretServiceAvailable = { true }, windowsCredentialsAvailable = { false })
        assertTrue(fallback is FileCredentialStore)
    }

    @Test
    fun fileSignInMovesIntoTheSystemStoreOnce() {
        val file = File(dir, CredentialStores.FALLBACK_FILE)
        FileCredentialStore(file).save(credentials)
        val manager = InMemoryCredentialStore()

        val chosen = CredentialStores.forCurrentOs(dir, "Windows 11", windowsCredentialsAvailable = { true }, windowsStore = { manager })

        assertSame(manager, chosen)
        assertEquals(credentials, manager.load())
        assertFalse(file.exists())
    }

    @Test
    fun aSignInAlreadyInTheSystemStoreWinsOverTheFile() {
        val file = File(dir, CredentialStores.FALLBACK_FILE)
        FileCredentialStore(file).save(credentials.copy(refreshToken = "1//old-file"))
        val manager = InMemoryCredentialStore(credentials)

        assertSame(manager, CredentialStores.moveFromFile(FileCredentialStore(file), manager))
        assertEquals(credentials, manager.load())
        assertFalse(file.exists())
    }

    @Test
    fun failedMoveKeepsUsingTheFileSoNobodyIsSignedOut() {
        val file = File(dir, CredentialStores.FALLBACK_FILE)
        FileCredentialStore(file).save(credentials)
        val broken = object : CredentialStore {
            override val description = "broken"
            override val isSystemKeyring = true
            override fun load(): StoredCredentials? = null
            override fun save(credentials: StoredCredentials) = throw KeyringException("denied")
            override fun clear() = Unit
        }

        val chosen = CredentialStores.moveFromFile(FileCredentialStore(file), broken)

        assertTrue(chosen is FileCredentialStore)
        assertEquals(credentials, chosen.load())
    }

    @Test
    fun noFileMeansTheSystemStoreIsUsedAsIs() {
        val manager = InMemoryCredentialStore()
        assertSame(manager, CredentialStores.moveFromFile(FileCredentialStore(File(dir, CredentialStores.FALLBACK_FILE)), manager))
        assertNull(manager.load())
    }

    /**
     * Talks to the real Windows Credential Manager with a separate test entry and removes it again.
     * Opt-in: `$env:DRIVECINEMA_KEYRING_TEST='1'; .\gradlew.bat :desktop:test`.
     */
    @Test
    fun realWindowsCredentialManagerRoundTrip() {
        assumeTrue(System.getenv("DRIVECINEMA_KEYRING_TEST") == "1")
        assumeTrue(System.getProperty("os.name").orEmpty().startsWith("Windows"))
        assertTrue(WindowsCredentialStore.isAvailable())
        val store = WindowsCredentialStore(account = "unit-test-${System.nanoTime()}")
        try {
            assertNull(store.load())
            store.save(credentials)
            assertEquals(credentials, store.load())
            store.save(credentials.copy(refreshToken = "1//rotated"))
            assertEquals("1//rotated", store.load()!!.refreshToken)
        } finally {
            store.clear()
        }
        assertNull(store.load())
        store.clear() // deleting a missing entry is not an error
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
