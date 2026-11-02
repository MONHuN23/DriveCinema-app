package com.drivecinema.app.core.remote

import android.content.Context
import android.os.Build
import android.provider.Settings
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
 * Milestone D: this device's link ID and its linked devices (with their secret link keys): TVs on the phone, phones on
 * the TV. AES-GCM file with a key that never leaves AndroidKeyStore; without AndroidKeyStore (unit tests, odd devices)
 * links live in memory only, deliberately with no fallback key (as `AndroidPairedHostStore`).
 */
class DeviceLinkStore(
    private val file: File,
    private val secretKey: () -> SecretKey? = ::androidKeyStoreKey
) {
    private data class State(val selfId: String, val links: List<DeviceLink>)

    private var memory: State? = null

    private fun state(): State = memory ?: (read() ?: State(DeviceLink.newId(), emptyList())).also { memory = it }

    /** This device's link ID, created on first use. */
    @Synchronized
    fun selfId(): String = state().selfId.also { if (!file.exists()) write(state()) }

    @Synchronized
    fun links(): List<DeviceLink> = state().links

    @Synchronized
    fun find(id: String): DeviceLink? = links().firstOrNull { it.id == id }

    /** Adds [link], replacing an older link with the same ID. */
    @Synchronized
    fun save(link: DeviceLink) = state().let { write(it.copy(links = it.links.filter { l -> l.id != link.id } + link)) }

    @Synchronized
    fun remove(id: String) = state().let { write(it.copy(links = it.links.filter { l -> l.id != id })) }

    private fun write(state: State) {
        memory = state
        val key = secretKey()
        if (key == null) {
            Log.w(TAG, "No AndroidKeyStore: Play on TV links are kept only until the app closes")
            return
        }
        val json = JSONObject().put("self", state.selfId).put("links", JSONArray(state.links.map { it.toJson() })).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val bytes = cipher.iv + cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(bytes)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun read(): State? {
        if (!file.exists()) return null
        val key = secretKey() ?: return null
        return try {
            val bytes = file.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }
            val o = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            val array = o.getJSONArray("links")
            State(
                selfId = o.getString("self").takeIf(PlayOnTvProtocol::isValidId) ?: DeviceLink.newId(),
                links = (0 until array.length()).mapNotNull { DeviceLink.fromJson(array.getJSONObject(it)) }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Play on TV links could not be read: ${e.javaClass.simpleName}")
            null
        }
    }

    companion object {
        private const val TAG = "DeviceLinkStore"
        private const val KEY_ALIAS = "DriveCinemaLinkKeys"

        @Volatile private var tvs: DeviceLinkStore? = null
        @Volatile private var phones: DeviceLinkStore? = null

        /** On the phone: the linked TVs (one instance per process, shared by the UI and the sender). */
        fun tvs(context: Context): DeviceLinkStore = tvs ?: synchronized(this) {
            tvs ?: DeviceLinkStore(File(context.applicationContext.filesDir, "linked_tvs.enc")).also { tvs = it }
        }

        /** On the TV: the linked phones (shared by the receiver and the Account dialog). */
        fun phones(context: Context): DeviceLinkStore = phones ?: synchronized(this) {
            phones ?: DeviceLinkStore(File(context.applicationContext.filesDir, "linked_phones.enc")).also { phones = it }
        }

        /** The name other devices see: the device name from Settings when set, else the model. */
        fun deviceName(context: Context): String =
            (runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull() ?: Build.MODEL)
                .orEmpty().trim().take(PlayOnTvProtocol.MAX_NAME).ifEmpty { "Android device" }

        private fun androidKeyStoreKey(): SecretKey? = try {
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
