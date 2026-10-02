package com.example.geofencing.shared

internal object AppConfig {
    const val ADMIN_PIN = "1234"
    const val FCM_TOPIC = "all_users"

    const val DEFAULT_INTERVAL_SEC = 5
    const val DEFAULT_RADIUS_M = 200.0
    const val MIN_RADIUS_M = 50.0
    const val MAX_RADIUS_M = 2000.0
}
