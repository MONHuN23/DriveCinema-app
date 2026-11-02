package com.drivecinema.app.core.host

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Task I.2: paired PCs (with their secret device keys) in `paired_hosts.enc`, AES-GCM with a key that never leaves
 * AndroidKeyStore. Without AndroidKeyStore (unit tests, odd devices) pairings live in memory only: there is
 * deliberately no fallback key (unlike the sign-in store, report F-10).
 */
class AndroidPairedHostStore(
    private val file: File,
    private val secretKey: () -> SecretKey? = ::androidKeyStoreKey
) : PairedHostStore {
    constructor(context: Context) : this(File(context.filesDir, "paired_hosts.enc"))

    private var memory: List<PairedHost>? = null

    @Synchronized
    override fun hosts(): List<PairedHost> = memory ?: read().also { memory = it }

    @Synchronized
    override fun save(host: PairedHost) = write(hosts().filter { it.pairing.hostId != host.pairing.hostId } + host)

    @Synchronized
    override fun remove(hostId: String) = write(hosts().filter { it.pairing.hostId != hostId })

    private fun write(hosts: List<PairedHost>) {
        memory = hosts
        val key = secretKey()
        if (key == null) {
            Log.w(TAG, "No AndroidKeyStore: PC pairings are kept only until the app closes")
            return
        }
        val json = JSONArray(hosts.map {
            JSONObject().put("pairing", JSONObject(it.pairing.toJson())).put("home", it.homeAddress ?: "").put("vpn", it.vpnAddress ?: "")
        }).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val bytes = cipher.iv + cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        // Temp file + atomic replace, like the sign-in store (F-09, F-23).
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(bytes)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun read(): List<PairedHost> {
        if (!file.exists()) return emptyList()
        val key = secretKey() ?: return emptyList()
        return try {
            val bytes = file.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }
            val array = JSONArray(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                HostPairing.fromJson(o.getJSONObject("pairing").toString())?.let {
                    PairedHost(it, o.optString("home").ifEmpty { null }, o.optString("vpn").ifEmpty { null })
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Paired PCs could not be read: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    private companion object {
        const val TAG = "PairedHostStore"
        const val KEY_ALIAS = "DriveCinemaHostKeys"

        fun androidKeyStoreKey(): SecretKey? = try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build()
                    )
                }
                .generateKey()
        } catch (e: Exception) {
            null
        }
    }
}
