package com.example.geofencing.shared.location

import com.example.geofencing.shared.platform.PlatformContext
import kotlinx.coroutines.flow.Flow

data class LocationFix(val lat: Double, val lng: Double, val accuracyMeters: Double)

/** Location fixes from the platform. Check [hasPermission] before calling the others. */
interface LocationProvider {
    fun hasPermission(): Boolean
    fun updates(intervalMs: Long): Flow<LocationFix>
    suspend fun current(): LocationFix?
}

expect fun createLocationProvider(context: PlatformContext): LocationProvider
