package com.drivecinema.desktop.security

import com.drivecinema.app.core.logging.AppLog
import java.io.File

/** Picks the best credential store for this computer. */
object CredentialStores {
    const val FALLBACK_FILE = "credentials.json"

    fun forCurrentOs(
        dataDir: File,
        osName: String = System.getProperty("os.name").orEmpty(),
        secretServiceAvailable: () -> Boolean = SecretServiceCredentialStore::isAvailable,
        windowsCredentialsAvailable: () -> Boolean = WindowsCredentialStore::isAvailable,
        windowsStore: () -> CredentialStore = { WindowsCredentialStore() },
        secretServiceStore: () -> CredentialStore = { SecretServiceCredentialStore() }
    ): CredentialStore {
        val os = osName.lowercase()
        val fallback = FileCredentialStore(File(dataDir, FALLBACK_FILE))
        val system = when {
            os.startsWith("windows") -> if (windowsCredentialsAvailable()) windowsStore() else null
            os.startsWith("mac") -> null
            else -> if (secretServiceAvailable()) secretServiceStore() else null
        } ?: return fallback
        return moveFromFile(fallback, system)
    }

    /**
     * A sign-in kept in the fallback file (before F.10 on Windows, or while the Linux keyring was missing) moves
     * into the system store once, then the file is deleted. If the move fails, the file keeps being used, so
     * nobody is signed out by it.
     */
    internal fun moveFromFile(file: FileCredentialStore, system: CredentialStore): CredentialStore {
        val saved = file.load() ?: run {
            file.clear() // an unreadable leftover file is useless
            return system
        }
        return try {
            if (system.load() == null) system.save(saved)
            file.clear()
            system
        } catch (e: Exception) {
            AppLog.w("CredentialStores", "Keeping the sign-in file, moving it to ${system.description} failed: ${e.message}")
            file
        }
    }
}
