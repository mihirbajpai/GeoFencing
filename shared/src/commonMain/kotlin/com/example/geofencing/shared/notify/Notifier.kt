package com.example.geofencing.shared.notify

import com.example.geofencing.shared.platform.PlatformContext

/** Shows local notifications on this device. */
interface Notifier {
    fun showMemberLeft(memberName: String)
}

expect fun createNotifier(context: PlatformContext): Notifier
