package com.example.geofencing.shared.tracking

import com.example.geofencing.shared.geo.GeoUtils
import com.example.geofencing.shared.model.GeoFence
import com.example.geofencing.shared.model.MemberStatus
import kotlin.math.max

internal data class StatusChange(val status: MemberStatus, val isExit: Boolean)

internal class FenceTracker {
    var status = MemberStatus.UNKNOWN
        private set
    private var version: Long? = null
    private var outsideFixes = 0

    fun reset() {
        status = MemberStatus.UNKNOWN
        version = null
        outsideFixes = 0
    }

    fun onLocation(
        fence: GeoFence,
        lat: Double,
        lng: Double,
        accuracyMeters: Double
    ): StatusChange? {
        if (fence.version != version) {
            reset()
            version = fence.version
        }
        if (accuracyMeters > MAX_ACCURACY_M) return null

        val distance = GeoUtils.distanceMeters(fence.lat, fence.lng, lat, lng)
        return when {
            distance <= fence.radiusMeters -> {
                outsideFixes = 0
                changeTo(MemberStatus.INSIDE, isExit = false)
            }

            distance > fence.radiusMeters + max(accuracyMeters, EDGE_BUFFER_M) ->
                if (++outsideFixes < OUTSIDE_FIXES) null
                else changeTo(MemberStatus.OUTSIDE, isExit = status == MemberStatus.INSIDE)

            else -> null
        }
    }

    private fun changeTo(newStatus: MemberStatus, isExit: Boolean): StatusChange? {
        if (status == newStatus) return null
        status = newStatus
        return StatusChange(newStatus, isExit)
    }

    private companion object {
        const val OUTSIDE_FIXES = 2
        const val EDGE_BUFFER_M = 10.0
        const val MAX_ACCURACY_M = 100.0
    }
}
