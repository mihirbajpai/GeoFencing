# GeoFencing

Android app where everyone using it is part of one group. An admin draws a circular fence on the map, and as soon as any member walks out of it, every other member gets a push notification.

Built with Kotlin Multiplatform (Android target), Jetpack Compose, Google Maps, Firestore and FCM.

## Features

- First launch asks for a name, no login
- Admin mode behind a 4-digit PIN (`1234`, set in `AppConfig.kt`)
- Admin taps the map to place the fence, sets the radius with a slider (50 m to 2 km), or deletes it
- Admin can change how often locations are checked (default 5 s)
- Live member list showing who is inside or outside
- One FCM notification to everyone else when a member leaves; it re-arms once they're back inside

## How it works

1. A foreground service requests a location fix every N seconds.
2. `FenceTracker` compares it against the fence. A member only counts as outside after 2 fixes beyond the radius plus GPS accuracy, so jitter at the edge doesn't fire alerts.
3. The status is written to Firestore in a transaction that returns true only for the first exit per fence version. That's what keeps the alert to exactly one, even when the write is retried.
4. On that first exit, the device sends a data message to the FCM topic `all_users` using the FCM HTTP v1 API. Every other device shows the notification; the sender ignores its own.

There's no backend, so the app signs FCM requests itself with a bundled service account. That's fine for this assessment but not something to ship.

## Project structure

```
shared/                       KMP module
  commonMain/                 platform-independent code
    tracking/                 FenceMonitor (tracking loop), FenceTracker (inside/outside)
    data/                     FirestoreRepository
    viewmodel/                MapViewModel
    location/ notify/ storage/  interfaces + expect factories
  androidMain/                actual implementations: fused location, FCM sender,
                              notifications, SharedPreferences
app/                          Android app
  ui/                         Compose screens (map, dialogs)
  location/                   LocationTrackingService (foreground service)
  fcm/                        GeoFcmService (receives alerts)
```

Platform features (location, storage, alerts, notifications) are interfaces in `commonMain` with `expect` factories and Android `actual`s, so an iOS target only needs its own `iosMain` and UI.

## Setup

You need your own Firebase project and Maps key; none of the credentials are in the repo.

1. **Firebase project**: create one at [console.firebase.google.com](https://console.firebase.google.com) (the free Spark plan is enough) and add an Android app with package name `com.example.geofencing`.
2. **google-services.json**: download it and put it in `app/`.
3. **Firestore**: create a database, then paste `firestore.rules` into the Rules tab and publish.
4. **Service account**: Project settings → Service accounts → Generate new private key. Save it as `app/src/main/assets/service-account.json`. Also make sure *Firebase Cloud Messaging API (V1)* is enabled under Project settings → Cloud Messaging.
5. **Maps API key**: in Google Cloud Console enable *Maps SDK for Android*, create a key and add it to `local.properties`:
   ```
   MAPS_API_KEY=your_key
   ```
   Google Maps needs billing enabled on the project that owns the key (Android map loads are free).

## Build and run

Requires JDK 21 for Gradle (Android Studio's Gradle JDK setting) and Android SDK 37.

```
./gradlew :app:assembleDebug
```

Install on two or more devices, enter a name on each and allow location and notifications. On one device tap **Admin**, enter the PIN, create a fence around where you are, then take another device outside the circle. The other devices should get the alert within one or two location intervals.

## Known limitations

- Tracking stops if the app is force-stopped or the phone reboots (it keeps running when the app is in the background or the screen is off).
- On MIUI and similar ROMs, set the app's battery saver to *No restrictions* and enable autostart, otherwise tracking and notifications can be delayed.
- Firestore rules are open and the admin PIN is hardcoded, as the assessment didn't require auth.
