package com.drivecinema.app.core.local

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

class FakeTvStorageCapabilityChecker(var supported: Boolean = false) : TvStorageCapabilityChecker {
    override fun isOpenDocumentTreeSupported(): Boolean = supported
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TvStorageCapabilityCheckerTest {

    private lateinit var context: Context
    private lateinit var checker: SystemTvStorageCapabilityChecker

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        checker = SystemTvStorageCapabilityChecker(context.packageManager)
    }

    @Test
    fun returnsFalseWhenNoActivityHandlesOpenDocumentTree() {
        // By default in standard test environment, no activity handles OPEN_DOCUMENT_TREE unless added
        val isSupported = checker.isOpenDocumentTreeSupported()
        assertFalse(isSupported)
    }

    @Test
    fun returnsTrueWhenActivityHandlesOpenDocumentTree() {
        val shadowPackageManager = Shadows.shadowOf(context.packageManager)
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.documentsui"
                name = "com.android.documentsui.picker.PickActivity"
            }
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        shadowPackageManager.addResolveInfoForIntent(intent, resolveInfo)

        val isSupported = checker.isOpenDocumentTreeSupported()
        assertTrue(isSupported)
    }

    private fun registerHandler(packageName: String, activityName: String) {
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = activityName
            }
        }
        Shadows.shadowOf(context.packageManager)
            .addResolveInfoForIntent(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), resolveInfo)
    }

    @Test
    fun androidTvFrameworkStubAloneIsNotSupported() {
        // Regression: the Android TV emulator (and some TVs) resolve OPEN_DOCUMENT_TREE only to a
        // placeholder that shows "not supported" and returns no folder.
        registerHandler("com.android.tv.frameworkpackagestubs", "com.android.tv.frameworkpackagestubs.Stubs\$DocumentsStub")

        assertFalse(checker.isOpenDocumentTreeSupported())
    }

    @Test
    fun realPickerAlongsideStubIsSupported() {
        registerHandler("com.android.tv.frameworkpackagestubs", "com.android.tv.frameworkpackagestubs.Stubs\$DocumentsStub")
        registerHandler("com.google.android.documentsui", "com.android.documentsui.picker.PickActivity")

        assertTrue(checker.isOpenDocumentTreeSupported())
    }

    @Test
    fun fakeTvStorageCapabilityCheckerAllowsDirectTestControl() {
        val fakeChecker = FakeTvStorageCapabilityChecker(supported = false)
        assertFalse(fakeChecker.isOpenDocumentTreeSupported())

        fakeChecker.supported = true
        assertTrue(fakeChecker.isOpenDocumentTreeSupported())
    }
}
