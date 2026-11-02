package com.drivecinema.app.core.model

/** Folders grouped for display: the user's own Drive first, then folders other people shared with them. */
data class FolderPickerSections(
    val mine: List<DriveFolder>,
    val shared: List<DriveFolder>
) {
    val isEmpty: Boolean get() = mine.isEmpty() && shared.isEmpty()

    val total: Int get() = mine.size + shared.size

    /** The phone's search box: only folders whose name contains [query] (any case); a blank query keeps them all. */
    fun filter(query: String): FolderPickerSections {
        val q = query.trim()
        if (q.isEmpty()) return this
        fun List<DriveFolder>.matching() = filter { it.name.contains(q, ignoreCase = true) }
        return FolderPickerSections(mine.matching(), shared.matching())
    }

    /**
     * Whether the phone opens "Shared with you" unfolded: when there is nothing of the user's own to pick, or when
     * the current library is a shared folder (it must stay in sight).
     */
    fun sharedStartsExpanded(currentFolderId: String?): Boolean =
        mine.isEmpty() || (currentFolderId != null && shared.any { it.id == currentFolderId })

    companion object {
        const val RECOMMENDED_NAME = "Streaming"

        /** The phone shows a search box above this many folders. */
        const val SEARCH_THRESHOLD = 8

        fun isRecommended(folder: DriveFolder): Boolean =
            folder.name.trim().equals(RECOMMENDED_NAME, ignoreCase = true)

        /** "Streaming" folders first, then alphabetical; files (non-folders) are left out. */
        fun from(folders: List<DriveFolder>): FolderPickerSections {
            val order = compareBy<DriveFolder>({ !isRecommended(it) }, { it.name.lowercase() })
            val onlyFolders = folders.filter { it.isFolder }.distinctBy { it.id }
            return FolderPickerSections(
                mine = onlyFolders.filter { it.ownedByMe }.sortedWith(order),
                shared = onlyFolders.filter { !it.ownedByMe }.sortedWith(order)
            )
        }
    }
}
