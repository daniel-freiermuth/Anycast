package com.aria.ariacast

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaRoute2Info
import android.media.MediaRoute2ProviderService
import android.media.RouteDiscoveryPreference
import android.media.RoutingSessionInfo
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Publishes discovered AirPlay / AriaCast receivers as system media routes so
 * they appear in the system output picker (the speaker icon on the volume
 * panel) alongside Bluetooth and Cast devices.
 *
 * Volume changes from the system UI arrive via [onSetRouteVolume] /
 * [onSetSessionVolume] and are forwarded to [AudioCastService.sendVolumeDb].
 */
class AirPlayRouteProvider : MediaRoute2ProviderService() {

    companion object {
        private const val TAG = "AirPlayRouteProvider"
        private const val SESSION_ID_PREFIX = "ariacast-session-"
        private const val VOLUME_MAX = 30
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var audioCastService: AudioCastService? = null
    private var isBound = false
    private var discoveryManager: DiscoveryManager? = null
    private var discoveryJob: Job? = null
    private var activeSessionId: String? = null
    private var currentVolume: Int = VOLUME_MAX

    // Cache the last published server list so we can build route IDs consistently
    private var lastServers: List<Server> = emptyList()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AudioCastService.AudioCastBinder
            audioCastService = binder.getService()
            isBound = true
            Log.i(TAG, "Bound to AudioCastService")
        }

        override fun onServiceDisconnected(className: ComponentName) {
            audioCastService = null
            isBound = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AirPlayRouteProvider created")
        // Use the process-wide DiscoveryManager so already-found servers are
        // available immediately — no fresh mDNS scan needed on every service restart.
        discoveryManager = (application as AriaCastApp).discoveryManager
        discoveryManager?.startDiscovery()
        startObservingRoutes()
        Intent(this, AudioCastService::class.java).also {
            bindService(it, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "AirPlayRouteProvider destroyed")
        discoveryJob?.cancel()
        // Don't stop discovery — the shared DiscoveryManager keeps running
        // so routes are instantly available on the next service start.
        if (isBound) unbindService(connection)
        super.onDestroy()
    }

    // ── Discovery & route publishing ────────────────────────────────────

    override fun onDiscoveryPreferenceChanged(preference: RouteDiscoveryPreference) {
        Log.d(TAG, "Discovery preference changed: features=${preference.preferredFeatures}")
        // We always keep discovery running; just republish what we have.
        publishRoutes(lastServers)
    }

    private fun startObservingRoutes() {
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            val dm = discoveryManager ?: return@launch
            dm.servers.collectLatest { servers ->
                lastServers = servers
                publishRoutes(servers)
            }
        }
    }

    private fun publishRoutes(servers: List<Server>) {
        val routes = servers.map { server -> buildRouteInfo(server) }
        Log.d(TAG, "Publishing ${routes.size} routes: ${routes.map { it.name }}")
        notifyRoutes(routes)
    }

    private fun buildRouteInfo(server: Server): MediaRoute2Info {
        val builder = MediaRoute2Info.Builder(routeIdFor(server), server.name)
            .addFeature(MediaRoute2Info.FEATURE_LIVE_AUDIO)
            .addFeature(MediaRoute2Info.FEATURE_REMOTE_PLAYBACK)
            .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
            .setVolumeMax(VOLUME_MAX)
            .setVolume(currentVolume)
            .setDescription(server.platform ?: "AirPlay")

        builder.setType(MediaRoute2Info.TYPE_REMOTE_SPEAKER)
        return builder.build()
    }

    private fun routeIdFor(server: Server): String =
        "${server.host}:${server.port}:${server.platform ?: "unknown"}"

    // ── Volume ──────────────────────────────────────────────────────────

    override fun onSetRouteVolume(requestId: Long, routeId: String, volume: Int) {
        Log.d(TAG, "onSetRouteVolume: routeId=$routeId volume=$volume")
        applyVolume(volume)
    }

    override fun onSetSessionVolume(requestId: Long, sessionId: String, volume: Int) {
        Log.d(TAG, "onSetSessionVolume: sessionId=$sessionId volume=$volume")
        applyVolume(volume)
    }

    private fun applyVolume(volume: Int) {
        currentVolume = volume.coerceIn(0, VOLUME_MAX)
        val dB = (currentVolume - VOLUME_MAX).toDouble()
        audioCastService?.sendVolumeDb(dB)
        updateSessionVolume()
        // Also republish routes with updated volume
        publishRoutes(lastServers)
    }

    private fun updateSessionVolume() {
        val sessionId = activeSessionId ?: return
        val svc = audioCastService ?: return
        val dest = svc.activeDestinations.value.firstOrNull() ?: return

        val updated = RoutingSessionInfo.Builder(sessionId, packageName)
            .setName(dest.name)
            .addSelectedRoute(routeIdFor(dest))
            .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
            .setVolumeMax(VOLUME_MAX)
            .setVolume(currentVolume)
            .build()
        notifySessionUpdated(updated)
    }

    private fun routeIdFor(dest: CastDestination): String =
        "${dest.host}:${dest.port}:${dest.platform ?: "unknown"}"

    // ── Session lifecycle (system-initiated via output picker) ───────────

    override fun onCreateSession(requestId: Long, packageName: String, routeId: String,
                                  sessionHints: android.os.Bundle?) {
        Log.i(TAG, "onCreateSession: pkg=$packageName routeId=$routeId")

        // The user selected our route from the output picker.
        // If we're already casting, create a session for volume control.
        // If not casting, we can't start (needs MediaProjection) — reject.
        val svc = audioCastService
        if (svc == null || svc.state.value != CastState.CASTING) {
            Log.w(TAG, "Not casting — rejecting session request")
            notifyRequestFailed(requestId, REASON_REJECTED)
            return
        }

        val dest = svc.activeDestinations.value.firstOrNull()
        if (dest == null) {
            notifyRequestFailed(requestId, REASON_REJECTED)
            return
        }

        val sessionId = SESSION_ID_PREFIX + dest.host
        val sessionInfo = RoutingSessionInfo.Builder(sessionId, packageName)
            .setName(dest.name)
            .addSelectedRoute(routeId)
            .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
            .setVolumeMax(VOLUME_MAX)
            .setVolume(currentVolume)
            .build()

        activeSessionId = sessionId
        notifySessionCreated(requestId, sessionInfo)
        Log.i(TAG, "Session created: $sessionId for ${dest.name}")
    }

    override fun onSelectRoute(requestId: Long, sessionId: String, routeId: String) {
        Log.d(TAG, "onSelectRoute: sessionId=$sessionId routeId=$routeId")
        // Single-device routing only.
        notifyRequestFailed(requestId, REASON_REJECTED)
    }

    override fun onDeselectRoute(requestId: Long, sessionId: String, routeId: String) {
        Log.d(TAG, "onDeselectRoute: sessionId=$sessionId routeId=$routeId")
    }

    override fun onTransferToRoute(requestId: Long, sessionId: String, routeId: String) {
        Log.d(TAG, "onTransferToRoute: sessionId=$sessionId routeId=$routeId")
        notifyRequestFailed(requestId, REASON_REJECTED)
    }

    override fun onReleaseSession(requestId: Long, sessionId: String) {
        Log.i(TAG, "Session released: $sessionId")
        activeSessionId = null
        notifySessionReleased(sessionId)
    }
}
