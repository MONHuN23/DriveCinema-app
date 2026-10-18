package com.drivecinema.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestValidationTest {
    @Test
    fun testManifestDeclaresLeanbackAndTouchscreenOptional() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("Must declare leanback software optional", manifest.contains("android.software.leanback\" android:required=\"false\""))
        assertTrue("Must declare touchscreen optional for TV", manifest.contains("android.hardware.touchscreen\" android:required=\"false\""))
        assertTrue("Must declare drive.readonly redirect scheme", manifest.contains("com.drivecinema.app"))
    }

    @Test
    fun testManifestDeclaresOpenDocumentTreeQueryForPackageVisibility() {
        // Without this, queryIntentActivities(ACTION_OPEN_DOCUMENT_TREE) is filtered on Android 11+ and the
        // TV storage check wrongly reports "not supported" (Robolectric does not enforce visibility).
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val queries = manifest.substringAfter("<queries>", "").substringBefore("</queries>")
        assertTrue(
            "Must declare <queries> for android.intent.action.OPEN_DOCUMENT_TREE",
            queries.contains("android.intent.action.OPEN_DOCUMENT_TREE")
        )
    }
}
