package com.drivecinema.app.core.player

import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BestSourceSelectorTest {

    private fun source(key: String, kind: SourceKind, available: Boolean = true, seenAt: Long = 0L) =
        MediaSourceEntity(sourceKey = key, mediaItemId = "item", kind = kind, isAvailable = available, lastSeenAt = seenAt)

    @Test
    fun prefersAvailableLocalCopyOverDrive() {
        val chosen = BestSourceSelector.choose(
            listOf(source("drive1", SourceKind.DRIVE, seenAt = 9), source("content://a", SourceKind.LOCAL, seenAt = 1))
        )
        assertEquals("content://a", chosen?.sourceKey)
    }

    @Test
    fun fallsBackToDriveWhenLocalCopyIsUnavailable() {
        val chosen = BestSourceSelector.choose(
            listOf(source("content://a", SourceKind.LOCAL, available = false), source("drive1", SourceKind.DRIVE))
        )
        assertEquals("drive1", chosen?.sourceKey)
    }

    @Test
    fun picksMostRecentlySeenAmongSameKind() {
        val chosen = BestSourceSelector.choose(
            listOf(source("content://old", SourceKind.LOCAL, seenAt = 1), source("content://new", SourceKind.LOCAL, seenAt = 5))
        )
        assertEquals("content://new", chosen?.sourceKey)
    }

    @Test
    fun returnsNothingWhenNoSourceIsAvailable() {
        assertNull(BestSourceSelector.choose(listOf(source("drive1", SourceKind.DRIVE, available = false))))
        assertNull(BestSourceSelector.choose(emptyList()))
    }
}
