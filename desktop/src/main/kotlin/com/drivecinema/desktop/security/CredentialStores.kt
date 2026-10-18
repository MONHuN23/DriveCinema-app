package com.drivecinema.desktop.security

import java.io.File

/** Picks the best credential store for this computer. */
object CredentialStores {
    const val FALLBACK_FILE = "credentials.json"

    fun forCurrentOs(
        dataDir: File,
        osName: String = System.getProperty("os.name").orEmpty(),
        secretServiceAvailable: () -> Boolean = SecretServiceCredentialStore::isAvailable
    ): CredentialStore {
        val os = osName.lowercase()
        // Windows Credential Manager comes in F.10; until then Windows uses the private file.
        if (!os.startsWith("windows") && !os.startsWith("mac") && secretServiceAvailable()) {
            return SecretServiceCredentialStore()
        }
        return FileCredentialStore(File(dataDir, FALLBACK_FILE))
    }
}
