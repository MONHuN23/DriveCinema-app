package com.drivecinema.app.core.model

/** Folders grouped for display: the user's own Drive first, then folders other people shared with them. */
data class FolderPickerSections(
    val mine: List<DriveFolder>,
    val shared: List<DriveFolder>
) {
    val isEmpty: Boolean get() = mine.isEmpty() && shared.isEmpty()

    companion object {
        const val RECOMMENDED_NAME = "Streaming"

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
