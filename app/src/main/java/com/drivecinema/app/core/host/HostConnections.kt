package com.drivecinema.app.core.host

import android.content.Context
import androidx.room.withTransaction
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.getInstance
import okhttp3.OkHttpClient

/**
 * Task I.2: one [HostLibrarySync] per app process, shared by the library screen (pairing, sync) and the player
 * (which streams PC copies through that PC's pinned, connected client).
 */
object HostConnections {
    @Volatile private var instance: HostLibrarySync? = null

    fun get(context: Context): HostLibrarySync = instance ?: synchronized(this) {
        instance ?: run {
            val db = DriveCinemaDatabase.getInstance(context.applicationContext)
            HostLibrarySync(
                db,
                AndroidPairedHostStore(context.applicationContext),
                runInTransaction = { block -> db.withTransaction { block() } }
            )
        }.also { instance = it }
    }

    /** The pinned client for an `https://<address>:<port>` the player opens, if it belongs to a connected, paired PC. */
    fun clientFor(host: String?, port: Int): OkHttpClient? {
        val sync = instance ?: return null
        return sync.hosts().firstNotNullOfOrNull { paired ->
            sync.connectedApi(paired.pairing.hostId)
                ?.takeIf { it.activeAddress == host && paired.pairing.httpsPort == port }
                ?.client
        }
    }
}
