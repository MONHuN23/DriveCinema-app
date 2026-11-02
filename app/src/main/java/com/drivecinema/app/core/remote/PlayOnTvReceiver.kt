package com.drivecinema.app.core.remote

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.drivecinema.app.MainActivity
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.getInstance
import com.drivecinema.app.core.host.HostAddressPolicy
import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.security.AuthCredentialStore
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.security.SecureRandom

/**
 * A request the TV receiver accepted, waiting for [MainActivity] to play it. The intent that brings MainActivity to the
 * front carries only a one-time token, so another app starting MainActivity (it is exported) can't make it play.
 */
object PendingPlay {
    data class Play(val mediaItemId: String, val title: String, val positionMs: Long)

    const val EXTRA_TOKEN = "com.drivecinema.app.extra.PLAY_ON_TV_TOKEN"

    private val random = SecureRandom()
    private var pending: Pair<String, Play>? = null

    @Synchronized
    fun post(play: Play): String {
        val token = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        pending = token to play
        return token
    }

    /** The waiting play for [token], once; null for a missing, stale or forged token. */
    @Synchronized
    fun take(token: String?): Play? {
        val (expected, play) = pending ?: return null
        if (token == null || token != expected) return null
        pending = null
        return play
    }
}

/**
 * Milestone D (design §5, R1): on the TV, listens for Play on TV while any DriveCinema screen is visible (the player
 * included), and only then. Started and stopped by counting started activities, so no service or notification.
 */
object PlayOnTvReceiver {
    private const val TAG = "PlayOnTv"

    private var installed = false
    private var startedActivities = 0
    private var listener: Listener? = null

    /** The port the TV listens on now, for the pairing answer. */
    val port: Int get() = listener?.port ?: PlayOnTvProtocol.PORT

    /** Call on the TV only, from the main thread; safe to call more than once. */
    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (++startedActivities == 1) listener = Listener(app).also { it.start() }
            }

            override fun onActivityStopped(activity: Activity) {
                if (startedActivities > 0 && --startedActivities == 0) {
                    listener?.stop()
                    listener = null
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private class Listener(private val context: Context) {
        @Volatile private var server: ServerSocket? = null
        @Volatile private var stopped = false
        private var nsdListener: NsdManager.RegistrationListener? = null
        private val throttle = AuthFailureThrottle()

        @Volatile var port: Int = PlayOnTvProtocol.PORT
            private set

        fun start() = Thread(::run, "PlayOnTvReceiver").apply { isDaemon = true }.start()

        fun stop() {
            stopped = true
            runCatching { server?.close() }
            unregisterNsd()
        }

        private fun run() {
            val socket = bind() ?: return
            server = socket
            if (stopped) return socket.close()
            port = socket.localPort
            registerNsd(port)
            val phones = DeviceLinkStore.phones(context)
            val session = PlayOnTvServerSession(phones.selfId(), phones::find)
            while (!stopped) {
                val client = try {
                    socket.accept()
                } catch (e: SocketException) {
                    break
                }
                client.use {
                    // Nothing from outside the home network, and nothing at all while paused after repeated failures.
                    if (!throttle.allowed() || !HostAddressPolicy.isAllowed(it.inetAddress, allowVpn = false)) return@use
                    it.soTimeout = 5_000
                    var decision: PlayDecision? = null
                    val result = session.handle(it.getInputStream(), it.getOutputStream()) { request, _ ->
                        decide(request).also { d -> decision = d }.status
                    }
                    when (result) {
                        is PlayOnTvServerSession.Result.Delivered -> decision?.mediaItemId?.let { id -> open(PendingPlay.Play(id, result.request.title, result.request.positionMs)) }
                        PlayOnTvServerSession.Result.Unauthorized -> throttle.recordFailure()
                        else -> Unit
                    }
                }
            }
            runCatching { socket.close() }
        }

        private fun bind(): ServerSocket? {
            for (port in listOf(PlayOnTvProtocol.PORT, 0)) {
                try {
                    return ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(port))
                    }
                } catch (e: Exception) {
                    AppLog.w(TAG, "Play on TV port $port unavailable: ${e.javaClass.simpleName}")
                }
            }
            return null
        }

        private fun decide(request: PlayRequest): PlayDecision {
            val creds = AuthCredentialStore(context).getCredentials()
            val signedIn = creds != null && creds.refreshToken.isNotEmpty() && creds.rootFolderId.isNotEmpty()
            val catalog = MediaDaoPlayCatalog(DriveCinemaDatabase.getInstance(context).mediaDao())
            return runBlocking { PlayOnTvRouting.decide(signedIn, catalog, request) }
        }

        /** Brings MainActivity to the front (`singleTask` clears a running player, which saves its progress). */
        private fun open(play: PendingPlay.Play) {
            val token = PendingPlay.post(play)
            Handler(Looper.getMainLooper()).post {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(PendingPlay.EXTRA_TOKEN, token)
                )
            }
        }

        private fun registerNsd(port: Int) {
            val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            val tvId = DeviceLinkStore.phones(context).selfId()
            val info = NsdServiceInfo().apply {
                serviceName = PlayOnTvProtocol.SERVICE_PREFIX + tvId
                serviceType = PlayOnTvProtocol.SERVICE_TYPE
                this.port = port
                setAttribute(PlayOnTvProtocol.TXT_ID, tvId)
            }
            val registration = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = AppLog.w(TAG, "NSD registration failed: $errorCode")
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            }
            // Under the lock, so a stop() racing with this either sees the registration or prevents it.
            synchronized(this) {
                if (stopped) return
                nsdListener = registration
                runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration) }
            }
        }

        private fun unregisterNsd() {
            val registration = synchronized(this) { nsdListener.also { nsdListener = null } } ?: return
            val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            runCatching { nsd.unregisterService(registration) }
        }
    }
}
