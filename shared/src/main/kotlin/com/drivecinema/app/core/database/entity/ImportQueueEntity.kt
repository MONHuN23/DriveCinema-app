package com.drivecinema.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where one desktop import (Task I.1) stands. Only the desktop uses this table; Android keeps it empty. */
enum class ImportState {
    QUEUED, UPLOADING, DONE, FAILED, NEEDS_REVIEW, CANCELLED,
    /** Already in the folder when it was chosen as an import folder: uploaded only if the user asks. */
    SKIPPED
}

/**
 * One local file found in an import folder, waiting for or done with its upload to Drive. Keyed by path + size +
 * modification time, so the same finished file is queued once, and a file replaced later counts as a new one.
 */
@Entity(
    tableName = "import_queue",
    indices = [
        Index("state"),
        Index("localPath")
    ]
)
data class ImportQueueEntity(
    @PrimaryKey val importKey: String,
    val localPath: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    /** Drive folder below the library root, e.g. "Series/Ted (2024)/Season 01"; null when it needs review. */
    val targetFolderPath: String?,
    /** The Drive library folder this file belongs to; files queued for another library are not uploaded. */
    val libraryRootId: String,
    val state: ImportState,
    /** Why it failed or needs review, or a note such as "Already on Drive". */
    val message: String? = null,
    /** Resumable upload session (valid for a week), so an upload goes on after a restart instead of starting again. */
    val sessionUri: String? = null,
    val bytesUploaded: Long = 0,
    val driveFileId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun keyOf(localPath: String, sizeBytes: Long, lastModifiedMs: Long) = "$localPath|$sizeBytes|$lastModifiedMs"
    }
}
