package com.example.geofencing.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoFence(val lat: Double, val lng: Double, val radiusMeters: Double, val version: Long)

@Serializable
internal data class Settings(val intervalSec: Int)

@Serializable
enum class MemberStatus { UNKNOWN, INSIDE, OUTSIDE }

@Serializable
internal data class Member(
    val id: String,
    val name: String,
    val status: MemberStatus = MemberStatus.UNKNOWN,
    val fenceVersion: Long = 0,
    val updatedAt: Long = 0,
) {
    /** Status for [fence], or UNKNOWN if it was recorded against an older fence. */
    fun statusFor(fence: GeoFence?) =
        if (fence?.version == fenceVersion) status else MemberStatus.UNKNOWN
}
