package com.creolecast.app

import android.app.Application

class CreoleCastApp : Application() {

    /** Shared discovery manager — lives as long as the process.
     *  Used by MainActivity and AirPlayRouteProvider. */
    val discoveryManager: DiscoveryManager by lazy { DiscoveryManager(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
