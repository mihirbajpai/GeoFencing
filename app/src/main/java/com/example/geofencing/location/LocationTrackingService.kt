package com.example.geofencing.location

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.geofencing.shared.location.createLocationProvider
import com.example.geofencing.shared.notify.createAlertSender
import com.example.geofencing.shared.notify.notificationBuilder
import com.example.geofencing.shared.storage.createLocalStore
import com.example.geofencing.shared.tracking.FenceMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Foreground service that keeps [FenceMonitor] running while the app is in the background. */
class LocationTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val locations = createLocationProvider(this)
        if (!locations.hasPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification =
            notificationBuilder("tracking", "Location tracking", NotificationManager.IMPORTANCE_LOW)
                .setContentTitle("Geofence tracking active")
                .setContentText("Checking your location against the group fence")
                .setOngoing(true)
                .build()
        ServiceCompat.startForeground(
            this,
            1,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        if (monitorJob == null) {
            val monitor = FenceMonitor(createLocalStore(this), locations, createAlertSender(this))
            monitorJob = scope.launch { monitor.run() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LocationTrackingService::class.java)
            )
        }
    }
}
