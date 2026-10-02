package com.example.geofencing

import android.app.Application
import com.example.geofencing.shared.notify.subscribeToAlerts

class GeoFencingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        subscribeToAlerts()
    }
}
