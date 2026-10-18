package com.drivecinema.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AuthCredentials(
    val refreshToken: String,
    val rootFolderId: String,
    val tmdbApiKey: String? = null,
    val userDisplayName: String? = null,
    val userEmail: String? = null,
    val userPhotoLink: String? = null
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("refreshToken", refreshToken)
            put("rootFolderId", rootFolderId)
            if (!tmdbApiKey.isNullOrEmpty()) {
                put("tmdbApiKey", tmdbApiKey)
            }
            if (!userDisplayName.isNullOrEmpty()) {
                put("userDisplayName", userDisplayName)
            }
            if (!userEmail.isNullOrEmpty()) {
                put("userEmail", userEmail)
            }
            if (!userPhotoLink.isNullOrEmpty()) {
                put("userPhotoLink", userPhotoLink)
            }
        }.toString()
    }

    companion object {
        fun fromJson(json: String): AuthCredentials? {
            return try {
                val obj = JSONObject(json)
                val token = obj.optString("refreshToken", "")
                val folderId = obj.optString("rootFolderId", "")
                val tmdbKey = obj.optString("tmdbApiKey", "").ifEmpty { null }
                val displayName = obj.optString("userDisplayName", "").ifEmpty { null }
                val email = obj.optString("userEmail", "").ifEmpty { null }
                val photoLink = obj.optString("userPhotoLink", "").ifEmpty { null }
                if (token.isNotEmpty()) {
                    AuthCredentials(
                        refreshToken = token,
                        rootFolderId = folderId,
                        tmdbApiKey = tmdbKey,
                        userDisplayName = displayName,
                        userEmail = email,
                        userPhotoLink = photoLink
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}

class AuthCredentialStore(
    private val context: Context,
    private val keyAlias: String = "DriveCinemaAuthKey"
) {
    private val keyStore: KeyStore? = try {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    } catch (_: Exception) {
        null
    }
    private val credentialsFile: File
        get() = File(context.filesDir, "auth_credentials.enc")

    private fun getOrCreateSecretKey(): SecretKey {
        val ks = keyStore
        if (ks == null) {
            // Test / JVM fallback when AndroidKeyStore provider is absent
            return javax.crypto.spec.SecretKeySpec("DriveCinemaTestEncryptionKey32B!".toByteArray(Charsets.UTF_8), "AES")
        }
        if (!ks.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
        return (ks.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    @Synchronized
    fun saveCredentials(credentials: AuthCredentials) {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv // 12 bytes
        val ciphertext = cipher.doFinal(credentials.toJson().toByteArray(Charsets.UTF_8))

        // Write [12-byte IV] + [Ciphertext + Tag] to a temp file, then rename it over the old file: a crash mid-write
        // must never leave a half-written credentials file, which would read as "signed out".
        // Files.move (not File.renameTo): renameTo can't replace an existing file on Windows JVMs (F-23).
        val target = credentialsFile
        val temp = File(target.parentFile, "${target.name}.tmp")
        try {
            java.io.FileOutputStream(temp).use { os ->
                os.write(iv)
                os.write(ciphertext)
                os.fd.sync()
            }
            java.nio.file.Files.move(
                temp.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    @Synchronized
    fun getCredentials(): AuthCredentials? {
        if (!credentialsFile.exists()) return null
        return try {
            val bytes = credentialsFile.readBytes()
            if (bytes.size < 12) return null
            val iv = bytes.copyOfRange(0, 12)
            val ciphertext = bytes.copyOfRange(12, bytes.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val decryptedBytes = cipher.doFinal(ciphertext)
            AuthCredentials.fromJson(String(decryptedBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun clear() {
        if (credentialsFile.exists()) {
            credentialsFile.delete()
        }
    }

    @Synchronized
    fun clearCredentials() {
        clear()
    }
}
