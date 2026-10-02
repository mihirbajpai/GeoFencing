package com.example.geofencing.shared.tracking

import com.example.geofencing.shared.data.FirestoreRepository
import com.example.geofencing.shared.location.LocationProvider
import com.example.geofencing.shared.model.GeoFence
import com.example.geofencing.shared.notify.AlertSender
import com.example.geofencing.shared.storage.LocalStore
import com.example.geofencing.shared.util.runCatchingCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate

/** Tracking loop: follows the shared fence, checks each location fix and reports exits once. */
class FenceMonitor(
    private val store: LocalStore,
    private val locations: LocationProvider,
    private val alertSender: AlertSender,
) {
    private val tracker = FenceTracker()
    private var unreported: Pair<Long, StatusChange>? = null
    private var alertPending = false

    suspend fun run() {
        combine(
            FirestoreRepository.fence,
            FirestoreRepository.intervalSec,
            ::Pair
        ).collectLatest { (fence, sec) ->
            if (fence == null) {
                tracker.reset()
                unreported = null
                alertPending = false
                return@collectLatest
            }
            locations.updates(sec * 1000L).conflate().collect { fix ->
                if (alertPending) sendAlert()
                unreported?.let { (version, change) ->
                    if (version == fence.version) report(fence, change) else unreported = null
                }
                tracker.onLocation(fence, fix.lat, fix.lng, fix.accuracyMeters)
                    ?.let { report(fence, it) }
            }
        }
    }

    private suspend fun report(fence: GeoFence, change: StatusChange) {
        val name = store.name ?: return
        runCatchingCancellable {
            FirestoreRepository.reportStatus(
                store.deviceId,
                name,
                change.status,
                fence.version
            )
        }
            .onSuccess { firstExit ->
                unreported = null
                if (change.isExit && firstExit) sendAlert()
            }
            .onFailure {
                println("FenceMonitor: report failed, will retry: ${it.message}")
                unreported = fence.version to change
            }
    }

    // exit is already in Firestore, so only the FCM call is retried
    private suspend fun sendAlert() {
        val name = store.name ?: return
        alertPending = runCatchingCancellable { alertSender.sendExitAlert(store.deviceId, name) }
            .onFailure { println("FenceMonitor: alert failed, will retry: ${it.message}") }
            .isFailure
    }
}
