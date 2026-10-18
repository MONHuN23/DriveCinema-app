package com.drivecinema.app.core.model

data class DriveFolder(
    val id: String,
    val name: String,
    val isFolder: Boolean = true,
    val mimeType: String = "application/vnd.google-apps.folder",
    /** true when the folder is in the signed-in user's own Drive; false when someone shared it with them. */
    val ownedByMe: Boolean = true,
    /** Display name (or email) of the owner, for folders shared with the user. */
    val ownerName: String? = null
)
