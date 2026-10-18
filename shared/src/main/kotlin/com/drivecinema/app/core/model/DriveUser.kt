package com.drivecinema.app.core.model

data class DriveUser(
    val displayName: String,
    val emailAddress: String,
    val photoLink: String? = null
)
