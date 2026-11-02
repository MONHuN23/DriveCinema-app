package com.drivecinema.desktop.importer

import com.drivecinema.app.core.metadata.MediaFilenameParser
import java.io.IOException

/** The file can't be placed without a person deciding (e.g. two possible season folders); it goes to "needs review". */
class NeedsReviewException(message: String) : IOException(message)

/**
 * Task I.1: finds the Drive folder for an [ImportTarget.Upload] path below the library root, creating only the
 * missing show and season folders. `Films` and `Series` must already exist (they are the library's own layout);
 * existing folders are reused (see [DriveFolderMatcher]) and never renamed or moved. Created folders are cached, so
 * episodes uploaded one after another never create a second "Season 01".
 */
class DriveFolderPathResolver(private val drive: DriveWriteClient, private val libraryRootId: String) {
    private val known = HashMap<List<String>, String>()

    @Synchronized
    fun resolve(folderPath: List<String>): String {
        requireLibraryLayout(folderPath)
        var parentId = libraryRootId
        for (depth in folderPath.indices) {
            val prefix = folderPath.subList(0, depth + 1).toList()
            parentId = known[prefix] ?: findOrCreate(folderPath, depth, parentId).also { known[prefix] = it }
        }
        return parentId
    }

    private fun findOrCreate(path: List<String>, depth: Int, parentId: String): String {
        val name = path[depth]
        val folders = drive.listChildren(parentId).filter { it.isFolder }.map { it.asFolder() }
        val match = when (depth) {
            0 -> {
                val top = folders.filter { it.name.trim().equals(name, ignoreCase = true) }
                return top.singleOrNull()?.id
                    ?: throw NeedsReviewException(
                        if (top.isEmpty()) "The Drive library has no \"$name\" folder. Create it in Google Drive first."
                        else "The Drive library has ${top.size} folders named \"$name\"."
                    )
            }
            1 -> DriveFolderMatcher.matchShow(name, folders)
            else -> DriveFolderMatcher.matchSeason(MediaFilenameParser.parseSeasonFolderOrNull(name)!!, folders)
        }
        return when (match) {
            is FolderMatch.Existing -> match.folder.id
            FolderMatch.Missing -> drive.createFolder(name, parentId).id
            is FolderMatch.Ambiguous -> throw NeedsReviewException(
                "More than one Drive folder could be \"$name\": ${match.candidates.joinToString { "\"${it.name}\"" }}."
            )
        }
    }

    /** Only the two shapes the resolver produces: `Films` or `Series/<Show>/Season NN`. Anything else is a bug. */
    private fun requireLibraryLayout(path: List<String>) {
        val valid = path == listOf(ImportTargetResolver.FILMS_FOLDER) ||
            (path.size == 3 && path[0] == ImportTargetResolver.SERIES_FOLDER && path[1].isNotBlank() &&
                MediaFilenameParser.parseSeasonFolderOrNull(path[2]) != null)
        require(valid) { "Not a library folder path: $path" }
    }
}
