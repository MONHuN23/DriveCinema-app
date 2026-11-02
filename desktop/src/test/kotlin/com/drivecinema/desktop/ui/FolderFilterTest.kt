package com.drivecinema.desktop.ui

import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.core.model.FolderPickerSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderFilterTest {

    private val sections = FolderPickerSections(
        mine = listOf(DriveFolder("s", "Streaming"), DriveFolder("c", "Classroom")),
        shared = listOf(
            DriveFolder("t", "1. Tanulási szokásrendszer", ownedByMe = false, ownerName = "Dorner"),
            DriveFolder("o", "2025 tavasz Operációs rendszerek I. gy.", ownedByMe = false, ownerName = "Kitti")
        )
    )

    @Test
    fun aBlankQueryKeepsEveryFolder() {
        assertSame(sections, FolderFilter.apply(sections, "  "))
    }

    @Test
    fun matchesNamesIgnoringCaseAndAccents() {
        val found = FolderFilter.apply(sections, "TANULASI")
        assertTrue(found.mine.isEmpty())
        assertEquals(listOf("t"), found.shared.map { it.id })
        assertEquals(listOf("o"), FolderFilter.apply(sections, "operacios").shared.map { it.id })
        assertEquals(listOf("s"), FolderFilter.apply(sections, " stream ").mine.map { it.id })
    }

    @Test
    fun matchesWhoSharedTheFolder() {
        assertEquals(listOf("o"), FolderFilter.apply(sections, "kitti").shared.map { it.id })
    }

    @Test
    fun noMatchLeavesBothSectionsEmpty() {
        assertTrue(FolderFilter.apply(sections, "zzz").isEmpty)
    }
}
