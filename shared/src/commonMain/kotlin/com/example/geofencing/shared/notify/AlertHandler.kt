package com.example.geofencing.shared.notify

import com.example.geofencing.shared.storage.LocalStore

/** Turns an incoming alert push into a notification, skipping alerts sent by this device. */
class AlertHandler(private val store: LocalStore, private val notifier: Notifier) {
    fun onMessage(data: Map<String, String>) {
        if (data[AlertPayload.TYPE] != AlertPayload.TYPE_EXIT) return
        if (data[AlertPayload.SENDER_ID] == store.deviceId) return
        notifier.showMemberLeft(data[AlertPayload.SENDER_NAME] ?: "A member")
    }
}
