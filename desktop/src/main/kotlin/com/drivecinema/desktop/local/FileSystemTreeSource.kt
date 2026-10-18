package com.drivecinema.desktop.local

import com.drivecinema.app.core.local.DocumentTreeSource
import com.drivecinema.app.core.local.LocalDocument
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * Desktop: reads a library folder from the file system for the shared crawler. Folder IDs are absolute paths,
 * the tree and every video are `file:` URIs (VLC plays them directly; they are also the progress keys).
 */
class FileSystemTreeSource : DocumentTreeSource {

    override fun rootDocumentId(treeUri: String): String = pathOf(treeUri).toAbsolutePath().normalize().toString()

    override fun listChildren(treeUri: String, parentDocumentId: String): List<LocalDocument> {
        val parent = Paths.get(parentDocumentId)
        if (!Files.isDirectory(parent)) throw IOException("Folder not available: $parentDocumentId")
        // Any listing failure throws, so a partial crawl is never taken for the whole library.
        return Files.list(parent).use { stream ->
            stream.filter { !it.name.startsWith(".") }
                .map { path ->
                    val isFolder = path.isDirectory()
                    LocalDocument(
                        documentId = path.toAbsolutePath().normalize().toString(),
                        name = path.name,
                        mimeType = if (isFolder) LocalDocument.FOLDER_MIME_TYPE else "",
                        sizeBytes = if (isFolder) null else runCatching { Files.size(path) }.getOrNull()
                    )
                }
                .toList()
        }
    }

    override fun documentUri(treeUri: String, documentId: String): String = Paths.get(documentId).toUri().toString()

    companion object {
        /** The tree ID stored for a folder: its `file:` URI. */
        fun treeUriOf(folder: Path): String = folder.toAbsolutePath().normalize().toUri().toString()

        fun pathOf(treeUri: String): Path = Paths.get(URI(treeUri))
    }
}
