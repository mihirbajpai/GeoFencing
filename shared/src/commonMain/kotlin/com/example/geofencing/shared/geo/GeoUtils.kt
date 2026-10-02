package com.example.geofencing.shared.geo

import com.example.geofencing.shared.model.GeoFence
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double)

/** Box around the fence, used to fit the map camera. */
fun GeoFence.bounds(): GeoBounds = GeoUtils.boundsAround(lat, lng, radiusMeters)

internal object GeoUtils {
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val METERS_PER_DEG = 111_320.0

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = rad(lat2 - lat1)
        val dLng = rad(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(rad(lat1)) * cos(rad(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    fun boundsAround(lat: Double, lng: Double, radiusMeters: Double): GeoBounds {
        val dLat = radiusMeters / METERS_PER_DEG
        val dLng = radiusMeters / (METERS_PER_DEG * cos(rad(lat)))
        return GeoBounds(lat - dLat, lng - dLng, lat + dLat, lng + dLng)
    }

    private fun rad(deg: Double) = deg * PI / 180
}
