package com.drivecinema.app.core.local

import android.content.Intent
import android.content.pm.PackageManager

interface TvStorageCapabilityChecker {
    fun isOpenDocumentTreeSupported(): Boolean
}

class SystemTvStorageCapabilityChecker(
    private val packageManager: PackageManager
) : TvStorageCapabilityChecker {

    companion object {
        /**
         * Android TV ships placeholder activities that "handle" document intents but only show a
         * not-supported screen and return no result. They must not count as a real folder picker.
         */
        val STUB_PICKER_PACKAGES = setOf("com.android.tv.frameworkpackagestubs")
    }

    override fun isOpenDocumentTreeSupported(): Boolean {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        val activities = packageManager.queryIntentActivities(intent, 0)
        return activities.any { it.activityInfo?.packageName !in STUB_PICKER_PACKAGES }
    }
}
