package com.drivecinema.app.core.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.drivecinema.app.core.database.DriveCinemaDatabase

/** Android: reads a Storage Access Framework document tree. */
class SystemDocumentTreeSource(private val contentResolver: ContentResolver) : DocumentTreeSource {
    override fun rootDocumentId(treeUri: String): String = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))

    override fun listChildren(treeUri: String, parentDocumentId: String): List<LocalDocument> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(Uri.parse(treeUri), parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val cursor = contentResolver.query(childrenUri, projection, null, null, null)
            ?: throw IllegalStateException("Folder listing unavailable (permission lost or storage removed)")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        LocalDocument(
                            documentId = it.getString(0),
                            name = it.getString(1) ?: "",
                            mimeType = it.getString(2) ?: "",
                            sizeBytes = if (it.isNull(3)) null else it.getLong(3)
                        )
                    )
                }
            }
        }
    }

    override fun documentUri(treeUri: String, documentId: String): String =
        DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), documentId).toString()
}

// Android entry points for the shared crawler/indexer (they work on String tree IDs; Android passes SAF Uris).

fun LocalLibraryCrawler.crawl(treeUri: Uri): LocalLibrary = crawl(treeUri.toString())

suspend fun LocalLibraryIndexer.indexFolder(treeUri: Uri): LocalIndexResult = indexFolder(treeUri.toString())

suspend fun LocalLibraryIndexer.retireUnsavedFolders(savedTreeUris: Collection<Uri>) =
    retireUnsavedFolders(savedTreeUris.map { it.toString() })

/** Android construction: catalog writes run in Room's Android transaction. */
fun LocalLibraryIndexer(
    db: DriveCinemaDatabase,
    crawler: LocalLibraryCrawler,
    clock: () -> Long = System::currentTimeMillis
): LocalLibraryIndexer =
    LocalLibraryIndexer(db, crawler, runInTransaction = { block -> db.withTransaction { block() } }, clock = clock)
