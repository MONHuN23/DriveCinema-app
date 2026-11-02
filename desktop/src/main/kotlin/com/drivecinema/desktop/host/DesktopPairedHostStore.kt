package com.drivecinema.desktop.host

import com.drivecinema.app.core.host.HostPairing
import com.drivecinema.app.core.host.PairedHost
import com.drivecinema.app.core.host.PairedHostStore
import com.drivecinema.app.core.logging.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Task I.2, desktop as a client: other PCs this computer streams from (`paired-hosts.json`, readable by this user
 * only, like this PC's own TLS key and device list). Not in the system keyring: the keyring stores hold exactly one
 * sign-in entry each, and a device key only lets this computer read another PC's videos on the home network.
 */
class DesktopPairedHostStore(private val file: File) : PairedHostStore {
    private var cache: List<PairedHost>? = null

    @Synchronized
    override fun hosts(): List<PairedHost> = cache ?: read().also { cache = it }

    @Synchronized
    override fun save(host: PairedHost) = write(hosts().filter { it.pairing.hostId != host.pairing.hostId } + host)

    @Synchronized
    override fun remove(hostId: String) = write(hosts().filter { it.pairing.hostId != hostId })

    private fun write(hosts: List<PairedHost>) {
        cache = hosts
        val json = JSONArray(hosts.map {
            JSONObject().put("pairing", JSONObject(it.pairing.toJson())).put("home", it.homeAddress ?: "").put("vpn", it.vpnAddress ?: "")
        })
        PrivateFiles.write(file, json.toString(2).toByteArray(Charsets.UTF_8))
    }

    private fun read(): List<PairedHost> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                HostPairing.fromJson(o.getJSONObject("pairing").toString())?.let {
                    PairedHost(it, o.optString("home").ifEmpty { null }, o.optString("vpn").ifEmpty { null })
                }
            }
        } catch (e: Exception) {
            AppLog.w("DesktopPairedHostStore", "Paired PCs could not be read: ${e.javaClass.simpleName}")
            emptyList()
        }
    }
}
