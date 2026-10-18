package com.drivecinema.app.ui.player

import com.drivecinema.app.core.player.DriveStreamUrls
import android.content.ContentResolver
import android.net.Uri

/** What the player plays, and the key its watch progress is stored under. */
sealed interface PlaybackSource {
    val uri: Uri
    val progressKey: String

    /** Google Drive file streamed over HTTPS; progress keyed by the immutable Drive file ID. */
    data class Drive(val fileId: String) : PlaybackSource {
        override val uri: Uri get() = PlayerUriBuilder.buildStreamUri(fileId)
        override val progressKey: String get() = fileId
    }

    /** Local document from the Storage Access Framework; progress keyed by its content URI. */
    data class Local(val contentUri: String) : PlaybackSource {
        override val uri: Uri get() = Uri.parse(contentUri)
        override val progressKey: String get() = contentUri
    }
}

object PlayerUriBuilder {
    fun buildStreamUri(fileId: String): Uri {
        return Uri.parse(DriveStreamUrls.media(fileId))
    }

    /**
     * Resolves the player intent extras. A local URI is only accepted with the `content://` scheme
     * (SAF documents); anything else is ignored so the player never opens arbitrary files or URLs.
     */
    fun resolveSource(driveFileId: String?, localContentUri: String?): PlaybackSource? {
        if (!localContentUri.isNullOrEmpty() &&
            Uri.parse(localContentUri).scheme == ContentResolver.SCHEME_CONTENT
        ) {
            return PlaybackSource.Local(localContentUri)
        }
        if (!driveFileId.isNullOrEmpty()) {
            return PlaybackSource.Drive(driveFileId)
        }
        return null
    }
}
