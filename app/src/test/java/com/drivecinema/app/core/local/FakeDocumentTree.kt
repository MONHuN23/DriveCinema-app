package com.drivecinema.app.core.local

import android.net.Uri

/** In-memory SAF tree for tests: folders and files by path, e.g. "Films/Dune (2024).mkv". */
class FakeDocumentTree(private val rootId: String = "root") : DocumentTreeSource {
    private val children = mutableMapOf<String, MutableList<LocalDocument>>()
    val failingFolders = mutableSetOf<String>()

    fun folder(path: String) = apply { ensureFolder(path) }

    fun file(path: String, size: Long = 1_000L) = apply {
        val parent = path.substringBeforeLast('/', "")
        val parentId = if (parent.isEmpty()) rootId else ensureFolder(parent)
        val list = children.getOrPut(parentId) { mutableListOf() }
        list.removeAll { it.documentId == "$rootId:$path" }
        list += LocalDocument("$rootId:$path", path.substringAfterLast('/'), mimeFor(path), size)
    }

    fun remove(path: String) = apply {
        children.values.forEach { list -> list.removeAll { it.documentId == "$rootId:$path" } }
    }

    private fun ensureFolder(path: String): String {
        var parentId = rootId
        var current = ""
        for (part in path.split('/')) {
            current = if (current.isEmpty()) part else "$current/$part"
            val id = "$rootId:$current/"
            val list = children.getOrPut(parentId) { mutableListOf() }
            if (list.none { it.documentId == id }) {
                list += LocalDocument(id, part, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
            }
            parentId = id
        }
        return parentId
    }

    private fun mimeFor(path: String) = when (path.substringAfterLast('.').lowercase()) {
        "mkv" -> "video/x-matroska"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }

    override fun rootDocumentId(treeUri: String): String = rootId

    override fun listChildren(treeUri: String, parentDocumentId: String): List<LocalDocument> {
        if (parentDocumentId in failingFolders) throw IllegalStateException("listing failed")
        return children[parentDocumentId].orEmpty().toList()
    }

    override fun documentUri(treeUri: String, documentId: String): String = "$treeUri/document/" + Uri.encode(documentId)
}
