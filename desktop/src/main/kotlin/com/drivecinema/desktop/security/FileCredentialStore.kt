package com.drivecinema.desktop.security

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

/**
 * Fallback when no system keyring is available: a JSON file in the app data directory that only the
 * current user can read (POSIX 600). Not encrypted — encrypting with a key stored next to it would add
 * nothing — so the UI says that the system keyring is not used.
 */
class FileCredentialStore(private val file: File) : CredentialStore {

    override val description = "file ${file.absolutePath} (readable only by you)"
    override val isSystemKeyring = false

    override fun load(): StoredCredentials? =
        if (file.exists()) StoredCredentials.fromJson(file.readText()) else null

    override fun save(credentials: StoredCredentials) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.delete()
        temp.createNewFile()
        restrictToOwner(temp)
        temp.writeText(credentials.toJson())
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    override fun clear() {
        file.delete()
    }

    private fun restrictToOwner(target: File) {
        try {
            Files.setPosixFilePermissions(
                target.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            )
        } catch (_: UnsupportedOperationException) {
            // Windows: files under %APPDATA% are already private to the user.
            target.setReadable(false, false); target.setReadable(true, true)
            target.setWritable(false, false); target.setWritable(true, true)
        }
    }
}
