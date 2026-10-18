package com.drivecinema.app.core.local

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalFolderPreferencesTest {

    private lateinit var context: Context
    private lateinit var fakePermissionManager: FakeUriPermissionManager
    private lateinit var preferences: LocalFolderPreferences

    class FakeUriPermissionManager : UriPermissionManager {
        val grantedUris = mutableSetOf<Uri>()
        val releasedUris = mutableSetOf<Uri>()
        var shouldThrowSecurityException = false
        var permissionsUnreadable = false

        override fun takePersistableReadPermission(uri: Uri): Boolean {
            if (shouldThrowSecurityException) {
                return false
            }
            grantedUris.add(uri)
            return true
        }

        override fun releasePersistableReadPermission(uri: Uri): Boolean {
            releasedUris.add(uri)
            grantedUris.remove(uri)
            return true
        }

        override fun getPersistedReadPermissions(): Set<Uri>? {
            if (permissionsUnreadable) return null
            return grantedUris.toSet()
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        fakePermissionManager = FakeUriPermissionManager()
        preferences = LocalFolderPreferences(
            context = context,
            prefsName = "test_local_folders_${System.currentTimeMillis()}",
            permissionManager = fakePermissionManager
        )
    }

    @Test
    fun addFolderUriSavesUriWhenPermissionSucceeds() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies")

        val result = preferences.addFolderUri(uri)

        assertEquals(AddFolderResult.Success, result)
        assertTrue(fakePermissionManager.grantedUris.contains(uri))
        val allUris = preferences.getAllFolderUris()
        assertEquals(1, allUris.size)
        assertTrue(allUris.contains(uri))
    }

    @Test
    fun addFolderUriFailsAndDoesNotSaveWhenPermissionFails() {
        fakePermissionManager.shouldThrowSecurityException = true
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies")

        val result = preferences.addFolderUri(uri)

        assertEquals(AddFolderResult.PermissionDenied, result)
        assertFalse(fakePermissionManager.grantedUris.contains(uri))
        val allUris = preferences.getAllFolderUris()
        assertTrue(allUris.isEmpty())
    }

    @Test
    fun addFolderUriRejectsNonExternalStorageAuthority() {
        val driveUri = Uri.parse("content://com.google.android.apps.docs.storage/tree/root%3A12345")

        val result = preferences.addFolderUri(driveUri)

        assertEquals(AddFolderResult.NotLocalStorage, result)
        assertFalse(fakePermissionManager.grantedUris.contains(driveUri))
        assertTrue(preferences.getAllFolderUris().isEmpty())
    }

    @Test
    fun reconciliationPrunesStaleUrisFromPreferences() {
        val uri1 = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies")
        val uri2 = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ASeries")

        // Both added successfully
        preferences.addFolderUri(uri1)
        preferences.addFolderUri(uri2)
        assertEquals(2, preferences.getAllFolderUris().size)

        // Simulate OS revoking permission for uri1 externally (e.g. app data cleared or permission revoked)
        fakePermissionManager.grantedUris.remove(uri1)

        // getAllFolderUris should reconcile with Android and prune uri1
        val reconciled = preferences.getAllFolderUris()
        assertEquals(1, reconciled.size)
        assertTrue(reconciled.contains(uri2))
        assertFalse(reconciled.contains(uri1))

        // Subsequent check verifies prefs were actually pruned
        val rawPrefsUris = context.getSharedPreferences(preferences.prefsName, Context.MODE_PRIVATE)
            .getStringSet("local_folder_uris", emptySet()) ?: emptySet()
        assertEquals(1, rawPrefsUris.size)
        assertTrue(rawPrefsUris.contains(uri2.toString()))
    }

    @Test
    fun unreadablePermissionStateNeverWipesSavedFolders() {
        val uri1 = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies")
        val uri2 = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ASeries")
        preferences.addFolderUri(uri1)
        preferences.addFolderUri(uri2)

        // Transient failure reading persisted grants (e.g. ContentResolver error at startup)
        fakePermissionManager.permissionsUnreadable = true
        assertEquals(setOf(uri1, uri2), preferences.getAllFolderUris())

        val rawPrefsUris = context.getSharedPreferences(preferences.prefsName, Context.MODE_PRIVATE)
            .getStringSet("local_folder_uris", emptySet()) ?: emptySet()
        assertEquals("Saved folders must survive an unreadable permission state", 2, rawPrefsUris.size)

        // Once readable again, the list is intact
        fakePermissionManager.permissionsUnreadable = false
        assertEquals(setOf(uri1, uri2), preferences.getAllFolderUris())
    }

    @Test
    fun removeFolderUriReleasesPermissionAndRemovesFromStorage() {
        val uri1 = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies")
        val uri2 = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ASeries")

        preferences.addFolderUri(uri1)
        preferences.addFolderUri(uri2)
        assertEquals(2, preferences.getAllFolderUris().size)

        val removed = preferences.removeFolderUri(uri1)

        assertTrue(removed)
        assertTrue(fakePermissionManager.releasedUris.contains(uri1))
        val remaining = preferences.getAllFolderUris()
        assertEquals(1, remaining.size)
        assertTrue(remaining.contains(uri2))
        assertFalse(remaining.contains(uri1))

        // Second removal returns false because it was not present
        val removedAgain = preferences.removeFolderUri(uri1)
        assertFalse(removedAgain)
    }

    @Test
    fun clearReleasesAllPermissionsAndEmptiesStorage() {
        val uri1 = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies")
        val uri2 = Uri.parse("content://com.android.externalstorage.documents/tree/1234-ABCD%3AVideos")

        preferences.addFolderUri(uri1)
        preferences.addFolderUri(uri2)

        preferences.clear()

        assertTrue(fakePermissionManager.releasedUris.contains(uri1))
        assertTrue(fakePermissionManager.releasedUris.contains(uri2))
        assertTrue(preferences.getAllFolderUris().isEmpty())
    }

    private class SecurityExceptionContentResolver(context: Context) : android.content.ContentResolver(context) {
        override fun takePersistableUriPermission(uri: Uri, modeFlags: Int) {
            throw SecurityException("No permission grant for $uri")
        }

        override fun releasePersistableUriPermission(uri: Uri, modeFlags: Int) {
            throw SecurityException("No permission grant for $uri")
        }
    }

    @Test
    fun systemUriPermissionManagerHandlesSecurityExceptionSafely() {
        val throwingResolver = SecurityExceptionContentResolver(context)
        val systemManager = SystemUriPermissionManager(throwingResolver)
        val testUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies")

        val takeResult = systemManager.takePersistableReadPermission(testUri)
        assertFalse(takeResult)

        val releaseResult = systemManager.releasePersistableReadPermission(testUri)
        assertFalse(releaseResult)

        val persisted = systemManager.getPersistedReadPermissions()
        // Either no grants, or "unreadable" (null) — never a crash.
        assertTrue(persisted.isNullOrEmpty())
    }

    @Test
    fun parseTreeDocumentIdFallbackHandlesVariousTreeIds() {
        assertEquals("Films", preferences.extractDisplayNameFromTreeId("primary:Movies/Films"))
        assertEquals("Movies", preferences.extractDisplayNameFromTreeId("primary:Movies"))
        assertEquals("Videos", preferences.extractDisplayNameFromTreeId("1234-ABCD:Videos"))
        assertEquals("Summer", preferences.extractDisplayNameFromTreeId("1234-ABCD:Videos/Family/Summer"))
        assertEquals("Internal Storage", preferences.extractDisplayNameFromTreeId("primary:"))
        assertEquals("Storage (1234-ABCD)", preferences.extractDisplayNameFromTreeId("1234-ABCD:"))
        assertEquals("DirectFolder", preferences.extractDisplayNameFromTreeId("DirectFolder"))
        assertEquals("Local Folder", preferences.extractDisplayNameFromTreeId(null))
        assertEquals("Local Folder", preferences.extractDisplayNameFromTreeId(""))
    }
}
