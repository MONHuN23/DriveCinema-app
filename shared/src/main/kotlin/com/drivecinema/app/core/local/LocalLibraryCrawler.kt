package com.drivecinema.app.core.local

import com.drivecinema.app.core.metadata.MediaFilenameParser
import com.drivecinema.app.core.sync.FolderQueueCrawler

/** One entry of a local folder listing (Android: Storage Access Framework; desktop: the file system). */
data class LocalDocument(
    val documentId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long? = null
) {
    val isFolder: Boolean get() = mimeType == FOLDER_MIME_TYPE

    companion object {
        /** Android's `DocumentsContract.Document.MIME_TYPE_DIR`; the desktop source uses it for folders too. */
        const val FOLDER_MIME_TYPE = "vnd.android.document/directory"
    }
}

/**
 * Reads a local folder tree. [treeUri] identifies the library folder (Android: the SAF tree URI; desktop: the
 * folder's `file:` URI). Abstracted so the crawler can be tested without a device.
 */
interface DocumentTreeSource {
    fun rootDocumentId(treeUri: String): String
    fun listChildren(treeUri: String, parentDocumentId: String): List<LocalDocument>
    /** The playable URI for a document inside the tree (`content://` or `file:`; also the watch-progress key). */
    fun documentUri(treeUri: String, documentId: String): String
}

data class LocalVideo(
    /** Playable URI (`content://` on Android, `file:` on desktop) — the source key and progress key for this copy. */
    val sourceKey: String,
    val fileName: String,
    val sizeBytes: Long?
)

data class LocalEpisode(val video: LocalVideo, val season: Int, val episode: Int, val title: String?)

data class LocalShow(val folderName: String, val title: String, val year: Int?, val episodes: List<LocalEpisode>)

data class LocalLibrary(val films: List<LocalVideo>, val shows: List<LocalShow>) {
    val isEmpty: Boolean get() = films.isEmpty() && shows.all { it.episodes.isEmpty() }
}

/**
 * Crawls a local library folder with exactly the same layout rules as Google Drive (see the in-app Setup
 * Guide): videos directly in the root and anywhere under "Films" are films; "Series/<Show>/<Season n>/" holds
 * episodes, season from the folder name, episode from SxxEyy. A video named like an episode ("Show.S01E04...")
 * outside Series/ (e.g. when a show's own folder was added) becomes an episode of that show, never a film.
 * Any listing failure throws, so a partial crawl is never treated as the full library.
 */
class LocalLibraryCrawler(private val source: DocumentTreeSource) {

    fun crawl(treeUri: String): LocalLibrary {
        val rootId = source.rootDocumentId(treeUri)
        val rootChildren = source.listChildren(treeUri, rootId)
        val films = mutableListOf<LocalVideo>()
        val shows = mutableListOf<LocalShow>()
        // Episodes found outside Series/, grouped by the show name in their file names.
        val looseEpisodes = linkedMapOf<String, MutableList<LocalEpisode>>()

        fun addFilmOrEpisode(doc: LocalDocument) {
            val marked = MediaFilenameParser.parseMarkedEpisode(doc.name)
            if (marked == null) {
                films += toVideo(treeUri, doc)
            } else {
                looseEpisodes.getOrPut(marked.cleanShowName) { mutableListOf() } +=
                    LocalEpisode(toVideo(treeUri, doc), marked.season, marked.episode, marked.episodeTitle)
            }
        }

        rootChildren.filter { isVideo(it) }.forEach { addFilmOrEpisode(it) }

        rootChildren.firstOrNull { it.isFolder && it.name.trim().equals("Films", ignoreCase = true) }?.let { filmsFolder ->
            val queue = ArrayDeque<String>().apply { add(filmsFolder.documentId) }
            while (queue.isNotEmpty()) {
                for (child in source.listChildren(treeUri, queue.removeFirst())) {
                    when {
                        child.isFolder -> queue.add(child.documentId)
                        isVideo(child) -> addFilmOrEpisode(child)
                    }
                }
            }
        }

        rootChildren.firstOrNull { it.isFolder && it.name.trim().equals("Series", ignoreCase = true) }?.let { seriesFolder ->
            for (showFolder in source.listChildren(treeUri, seriesFolder.documentId).filter { it.isFolder }) {
                val parsedShow = MediaFilenameParser.parseMovie(showFolder.name)
                val episodes = mutableListOf<LocalEpisode>()
                for (child in source.listChildren(treeUri, showFolder.documentId)) {
                    if (child.isFolder) {
                        val season = parseSeasonNumber(child.name)
                        for (file in source.listChildren(treeUri, child.documentId).filter { isVideo(it) }) {
                            val parsed = MediaFilenameParser.parseEpisode(file.name)
                            episodes += LocalEpisode(toVideo(treeUri, file), season, parsed.episode, parsed.episodeTitle)
                        }
                    } else if (isVideo(child)) {
                        val parsed = MediaFilenameParser.parseEpisode(child.name)
                        val season = if (parsed.cleanShowName.isNotEmpty() && parsed.season > 0) parsed.season else 1
                        episodes += LocalEpisode(toVideo(treeUri, child), season, parsed.episode, parsed.episodeTitle)
                    }
                }
                shows += LocalShow(showFolder.name, parsedShow.cleanTitle, parsedShow.year, episodes)
            }
        }

        for ((showName, episodes) in looseEpisodes) {
            val parsedShow = MediaFilenameParser.parseMovie(showName)
            shows += LocalShow(showName, parsedShow.cleanTitle, parsedShow.year, episodes)
        }

        return LocalLibrary(films, shows)
    }

    private fun isVideo(doc: LocalDocument): Boolean =
        !doc.isFolder && FolderQueueCrawler.isVideo(doc.mimeType, doc.name)

    private fun toVideo(treeUri: String, doc: LocalDocument) =
        LocalVideo(source.documentUri(treeUri, doc.documentId), doc.name, doc.sizeBytes)

    private fun parseSeasonNumber(folderName: String): Int = MediaFilenameParser.parseSeasonFolder(folderName)
}
