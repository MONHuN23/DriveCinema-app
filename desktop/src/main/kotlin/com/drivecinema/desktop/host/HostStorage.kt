package com.drivecinema.desktop.host

import com.drivecinema.app.core.host.HostIdentity
import com.drivecinema.app.core.host.HostPairing
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Files only the user may read (as for the file credential store); Windows: %APPDATA% is already private. */
internal object PrivateFiles {
    fun write(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(bytes)
        restrictToOwner(temp)
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun restrictToOwner(file: File) {
        runCatching {
            Files.setPosixFilePermissions(file.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }
    }
}

/**
 * The PC's host identity in the data directory (Task I.2): a random host ID, the secret behind file IDs, and the TLS
 * key + certificate (`host-identity.p12`). Created once; keeping them means paired devices keep trusting this PC.
 */
class HostIdentityStore(private val dataDir: File, private val random: SecureRandom = SecureRandom()) {
    private val infoFile get() = File(dataDir, "host.json")
    private val keyFile get() = File(dataDir, "host-identity.p12")

    /** Protects nothing by itself (the file is user-private); PKCS#12 just needs one. */
    private val password = "drivecinema-host".toCharArray()

    data class Info(val hostId: String, val fileIdSecret: ByteArray)

    @Synchronized
    fun info(): Info {
        runCatching {
            val json = JSONObject(infoFile.readText())
            return Info(json.getString("hostId"), Base64.getDecoder().decode(json.getString("fileIdSecret")))
        }
        val info = Info(
            hostId = ByteArray(9).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) },
            fileIdSecret = ByteArray(32).also(random::nextBytes)
        )
        PrivateFiles.write(
            infoFile,
            JSONObject().put("hostId", info.hostId).put("fileIdSecret", Base64.getEncoder().encodeToString(info.fileIdSecret)).toString().toByteArray()
        )
        return info
    }

    @Synchronized
    fun identity(): HostIdentity {
        runCatching {
            val keyStore = KeyStore.getInstance("PKCS12").apply { keyFile.inputStream().use { load(it, password) } }
            HostIdentity.fromKeyStore(keyStore, password)?.let { return it }
        }
        val identity = HostIdentity.generate(random = random)
        val bytes = java.io.ByteArrayOutputStream().also { identity.toKeyStore(password).store(it, password) }.toByteArray()
        PrivateFiles.write(keyFile, bytes)
        return identity
    }
}

/** A device paired with this PC, as shown in "Paired devices". */
data class PairedDevice(val deviceId: String, val name: String, val pairedAt: Long, val lastSeenAt: Long?)

/**
 * Paired devices (`host-devices.json`). Only a SHA-256 of each device key is stored, so the file can't be used to
 * pose as a device. Removing a device makes its key worthless at once.
 */
class HostDeviceRegistry(private val file: File, private val clock: () -> Long = System::currentTimeMillis) {
    private data class Entry(val device: PairedDevice, val keyHash: ByteArray)

    private var entries: List<Entry> = load()

    @Synchronized
    fun devices(): List<PairedDevice> = entries.map { it.device }

    @Synchronized
    fun add(pairing: HostPairing, name: String) {
        entries = entries.filter { it.device.deviceId != pairing.deviceId } +
            Entry(PairedDevice(pairing.deviceId, name, clock(), null), hash(pairing.deviceKey))
        save()
    }

    @Synchronized
    fun remove(deviceId: String) {
        entries = entries.filter { it.device.deviceId != deviceId }
        save()
    }

    /** The device for an `Authorization: Bearer <deviceId>.<key>` header, or null. Compares hashes in constant time. */
    @Synchronized
    fun authorize(header: String?): PairedDevice? {
        val token = header?.removePrefix("Bearer ")?.takeIf { it != header } ?: return null
        val deviceId = token.substringBefore('.', "")
        val key = token.substringAfter('.', "")
        if (deviceId.isEmpty() || key.isEmpty()) return null
        val entry = entries.firstOrNull { it.device.deviceId == deviceId } ?: return null
        if (!MessageDigest.isEqual(entry.keyHash, hash(key))) return null
        // Remember "last seen" without rewriting the file on every request.
        val now = clock()
        if ((entry.device.lastSeenAt ?: 0L) < now - 60_000) {
            entries = entries.map { if (it === entry) it.copy(device = it.device.copy(lastSeenAt = now)) else it }
            save()
        }
        return entry.device
    }

    private fun hash(key: String) = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))

    private fun load(): List<Entry> = runCatching {
        val array = JSONObject(file.readText()).getJSONArray("devices")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Entry(
                PairedDevice(o.getString("deviceId"), o.getString("name"), o.getLong("pairedAt"), o.optLong("lastSeenAt").takeIf { it > 0 }),
                Base64.getDecoder().decode(o.getString("keyHash"))
            )
        }
    }.getOrDefault(emptyList())

    private fun save() {
        val array = JSONArray(entries.map {
            JSONObject().put("deviceId", it.device.deviceId).put("name", it.device.name).put("pairedAt", it.device.pairedAt)
                .put("lastSeenAt", it.device.lastSeenAt ?: 0L).put("keyHash", Base64.getEncoder().encodeToString(it.keyHash))
        })
        PrivateFiles.write(file, JSONObject().put("devices", array).toString(2).toByteArray())
    }
}
