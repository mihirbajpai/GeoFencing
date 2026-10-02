package com.example.geofencing.shared.storage

import android.content.Context
import androidx.core.content.edit
import com.example.geofencing.shared.platform.PlatformContext
import java.util.UUID

actual fun createLocalStore(context: PlatformContext): LocalStore = SharedPrefsLocalStore(context)

private class SharedPrefsLocalStore(context: Context) : LocalStore {
    private val prefs =
        context.applicationContext.getSharedPreferences("geofencing", Context.MODE_PRIVATE)

    override val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { prefs.edit { putString(KEY_DEVICE_ID, it) } }

    override var name: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit { putString(KEY_NAME, value) }

    override var isAdmin: Boolean
        get() = prefs.getBoolean(KEY_IS_ADMIN, false)
        set(value) = prefs.edit { putBoolean(KEY_IS_ADMIN, value) }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_NAME = "name"
        const val KEY_IS_ADMIN = "is_admin"
    }
}
