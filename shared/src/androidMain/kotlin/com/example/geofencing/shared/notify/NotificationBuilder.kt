package com.example.geofencing.shared.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.geofencing.shared.R

/** Notification builder with the channel, icon and tap-to-open already set up. */
fun Context.notificationBuilder(
    channelId: String,
    channelName: String,
    importance: Int
): NotificationCompat.Builder {
    getSystemService(NotificationManager::class.java)
        .createNotificationChannel(NotificationChannel(channelId, channelName, importance))
    val openApp = packageManager.getLaunchIntentForPackage(packageName)
        ?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }
    return NotificationCompat.Builder(this, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentIntent(openApp)
}
