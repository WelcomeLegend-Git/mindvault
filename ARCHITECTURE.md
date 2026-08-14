# MindVault Architecture & Design

MindVault is built as a reactive, local-first Android application using Jetpack Compose, Kotlin Coroutines/Flow, AndroidX Room, and Firebase Firestore.

## Architectural Layers

```
┌─────────────────────────────────────────────────────────────┐
│                       UI Layer                              │
│       Jetpack Compose • Material 3 • ViewModels             │
└──────────────────────────────┬──────────────────────────────┘
                               │ StateFlow / Events
┌──────────────────────────────▼──────────────────────────────┐
│                      Domain / Core                          │
│   FocusManager • ScreenTimeTracker • StatisticsManager      │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    Background Services                      │
│   FocusAccessibilityService • NotificationListenerService   │
│   WorkManager Tasks • DeviceAdminReceiver                   │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                       Data Layer                            │
│   Room Database • SharedPreferences • Firebase Firestore    │
└─────────────────────────────────────────────────────────────┘
```

## Security & Privacy Model

* **Local-First Execution**: Core focus enforcement, app blocking, and screen time metrics execute entirely on-device without telemetry or cloud dependency.
* **Accessibility Isolation**: The accessibility service only reads package names and UI texts in volatile memory during active focus sessions. No user input or personal messages are recorded or transmitted.
* **Encrypted Cloud Backups**: Authenticated cloud backup uses Google Firebase with per-user document isolation (`backups/{userId}`).
