package com.example.geofencing.fcm

import com.example.geofencing.shared.notify.AlertHandler
import com.example.geofencing.shared.notify.createNotifier
import com.example.geofencing.shared.storage.createLocalStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives exit alerts from FCM. */
class GeoFcmService : FirebaseMessagingService() {
    private val handler by lazy { AlertHandler(createLocalStore(this), createNotifier(this)) }

    override fun onMessageReceived(message: RemoteMessage) = handler.onMessage(message.data)
}
