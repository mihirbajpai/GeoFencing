package com.example.geofencing.shared.storage

import com.example.geofencing.shared.platform.PlatformContext

/** Small per-device values that survive restarts. */
interface LocalStore {
    val deviceId: String
    var name: String?
    var isAdmin: Boolean
}

expect fun createLocalStore(context: PlatformContext): LocalStore
