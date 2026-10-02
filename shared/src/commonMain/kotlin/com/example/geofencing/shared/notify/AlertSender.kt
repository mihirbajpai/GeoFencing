package com.example.geofencing.shared.notify

import com.example.geofencing.shared.platform.PlatformContext

/** Broadcasts exit alerts to every device in the group. */
interface AlertSender {
    suspend fun sendExitAlert(senderId: String, senderName: String)
}

expect fun createAlertSender(context: PlatformContext): AlertSender

/** Starts receiving alerts sent through [AlertSender] on this device. */
expect fun subscribeToAlerts()

internal object AlertPayload {
    const val TYPE = "type"
    const val TYPE_EXIT = "EXIT"
    const val SENDER_ID = "senderId"
    const val SENDER_NAME = "senderName"

    fun exit(senderId: String, senderName: String) =
        mapOf(TYPE to TYPE_EXIT, SENDER_ID to senderId, SENDER_NAME to senderName)
}
