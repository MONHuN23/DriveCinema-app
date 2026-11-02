package com.drivecinema.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** UI polish: the phone picker's search and its folded "Shared with you" section. */
class FolderPickerSectionsTest {

    private val sections = FolderPickerSections.from(
        listOf(
            DriveFolder(id = "m1", name = "Streaming"),
            DriveFolder(id = "m2", name = "Classroom"),
            DriveFolder(id = "s1", name = "2025 tavasz Operációs rendszerek", ownedByMe = false),
            DriveFolder(id = "s2", name = "Family Streaming", ownedByMe = false)
        )
    )

    @Test
    fun searchKeepsMatchesInBothSectionsIgnoringCase() {
        val found = sections.filter("  streaming ")

        assertEquals(listOf("m1"), found.mine.map { it.id })
        assertEquals(listOf("s2"), found.shared.map { it.id })
        assertEquals(2, found.total)
    }

    @Test
    fun aBlankSearchKeepsEverything() {
        assertSame(sections, sections.filter("   "))
    }

    @Test
    fun searchMatchesAccentedNames() {
        assertEquals(listOf("s1"), sections.filter("operációs").shared.map { it.id })
        assertTrue(sections.filter("nothing like this").isEmpty)
    }

    @Test
    fun sharedStaysFoldedWhenYouHaveFoldersOfYourOwn() {
        assertFalse(sections.sharedStartsExpanded(currentFolderId = null))
        assertFalse(sections.sharedStartsExpanded(currentFolderId = "m1"))
    }

    @Test
    fun sharedOpensWhenTheCurrentLibraryIsSharedOrYouHaveNothingElse() {
        assertTrue(sections.sharedStartsExpanded(currentFolderId = "s2"))
        assertTrue(FolderPickerSections(mine = emptyList(), shared = sections.shared).sharedStartsExpanded(null))
    }
}
