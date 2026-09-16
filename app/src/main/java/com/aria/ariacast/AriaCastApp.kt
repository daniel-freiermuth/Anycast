package com.aria.ariacast

import android.app.Application

class AriaCastApp : Application() {

    /** Shared discovery manager — lives as long as the process.
     *  Used by MainActivity and AirPlayRouteProvider. */
    val discoveryManager: DiscoveryManager by lazy { DiscoveryManager(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
