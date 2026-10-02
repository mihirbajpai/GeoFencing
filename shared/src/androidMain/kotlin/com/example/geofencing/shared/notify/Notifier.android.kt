package com.example.geofencing.shared.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.geofencing.shared.platform.PlatformContext

actual fun createNotifier(context: PlatformContext): Notifier =
    AndroidNotifier(context.applicationContext)

private class AndroidNotifier(private val context: Context) : Notifier {
    override fun showMemberLeft(memberName: String) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        if (granted != PackageManager.PERMISSION_GRANTED) return

        val notification = context.notificationBuilder(
            "alerts",
            "Geofence alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
            .setContentTitle("$memberName left the geofence")
            .setContentText("$memberName has moved outside the group's area")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}
