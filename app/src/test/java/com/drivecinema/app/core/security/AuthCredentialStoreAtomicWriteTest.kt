package com.drivecinema.app.core.security

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Report F-09: credentials are written to a temp file and renamed over the old one, so an interrupted save can
 * never leave a half-written `auth_credentials.enc` (which reads as "signed out").
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AuthCredentialStoreAtomicWriteTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val credentialsFile get() = File(context.filesDir, "auth_credentials.enc")
    private val tempFile get() = File(context.filesDir, "auth_credentials.enc.tmp")

    @Before
    @After
    fun clean() {
        credentialsFile.delete()
        tempFile.delete()
    }

    @Test
    fun savedCredentialsReadBack() {
        val store = AuthCredentialStore(context)
        store.saveCredentials(AuthCredentials(refreshToken = "r1", rootFolderId = "f1"))
        store.saveCredentials(AuthCredentials(refreshToken = "r2", rootFolderId = "f2"))
        assertEquals("r2", store.getCredentials()?.refreshToken)
        assertFalse("no temp file is left behind after a save", tempFile.exists())
    }

    @Test
    fun saveReplacesAnExistingCredentialsFileOnEveryHost() {
        // F-23: File.renameTo can't replace an existing file on Windows, so this save used to throw there.
        AuthCredentialStore(context).saveCredentials(AuthCredentials(refreshToken = "old", rootFolderId = "f1"))
        AuthCredentialStore(context).saveCredentials(AuthCredentials(refreshToken = "new", rootFolderId = "f2"))
        val read = AuthCredentialStore(context).getCredentials()
        assertEquals("new", read?.refreshToken)
        assertEquals("f2", read?.rootFolderId)
        assertFalse(tempFile.exists())
    }

    @Test
    fun leftoverTempFromInterruptedSaveDoesNotReplaceCredentials() {
        val store = AuthCredentialStore(context)
        store.saveCredentials(AuthCredentials(refreshToken = "kept", rootFolderId = "f"))
        tempFile.writeBytes(ByteArray(7) { 1 }) // what a save killed half-way leaves now
        assertEquals("kept", AuthCredentialStore(context).getCredentials()?.refreshToken)
    }

    @Test
    fun nextSaveReplacesALeftoverTempFile() {
        tempFile.writeBytes(ByteArray(7) { 1 })
        val store = AuthCredentialStore(context)
        store.saveCredentials(AuthCredentials(refreshToken = "fresh", rootFolderId = "f"))
        assertEquals("fresh", store.getCredentials()?.refreshToken)
        assertFalse(tempFile.exists())
    }

    @Test
    fun truncatedCredentialsFileReadsAsSignedOutWithoutCrashing() {
        // What the old direct write could leave behind after a crash between writing the IV and the ciphertext.
        val store = AuthCredentialStore(context)
        store.saveCredentials(AuthCredentials(refreshToken = "r", rootFolderId = "f"))
        credentialsFile.writeBytes(credentialsFile.readBytes().copyOf(12))
        assertNull(store.getCredentials())
    }
}
