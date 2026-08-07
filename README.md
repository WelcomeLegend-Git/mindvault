<p align="center">
  <h1 align="center">🧠 MindVault</h1>
  <p align="center">
    A native Android study companion with uninterruptible focus sessions, intelligent app blocking, and detailed productivity analytics.
  </p>
  <p align="center">
    <a href="https://welcomelegend-git.github.io/mindvault/"><img src="https://img.shields.io/badge/🌐_Web_Landing_Page-Download_Here-6C63FF?style=for-the-badge" alt="Landing Page"></a>
    <a href="https://github.com/WelcomeLegend-Git/mindvault/releases/latest"><img src="https://img.shields.io/badge/📱_Download_APK-v3.2.1-FFD700?style=for-the-badge" alt="Latest Release"></a>
  </p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Material_3-E8DEF8?logo=materialdesign&logoColor=black" alt="Material 3" />
  <img src="https://img.shields.io/badge/Firebase-DD2C00?logo=firebase&logoColor=white" alt="Firebase" />
  <img src="https://img.shields.io/badge/Room-003B57?logo=sqlite&logoColor=white" alt="Room" />
  <img src="https://img.shields.io/badge/Min_SDK-28-brightgreen" alt="Min SDK 28" />
  <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="License: MIT" />
</p>

---

## Overview

MindVault is an Android app designed for students who need enforced focus during study sessions. Once a focus session starts, it locks the device into a distraction-free state that cannot be interrupted until the timer completes. The app pairs this core mechanic with detailed study analytics, smart notifications, and cloud backup via Firebase.

### Key Features

| Feature | Description |
|---------|-------------|
| **Uninterruptible Focus Mode** | Full-screen lock with foreground service, wake lock, and overlay protection. Once started, a session cannot be bypassed. |
| **App Blocking** | Accessibility service monitors and blocks distracting apps during active sessions |
| **Smart Scheduling** | Configurable study/sleep windows with automatic session availability detection |
| **Productivity Analytics** | Pie charts, weekly screen-time breakdowns, streak tracking, and session history |
| **Achievement System** | Gamified milestones to reward consistent study habits |
| **Cloud Sync** | Firebase Auth + Firestore for cross-device backup and profile management |
| **Daily Motivation** | Scheduled motivational notifications via WorkManager |
| **Security** | App-level password protection and developer settings guard |

---

## Architecture

```mermaid
graph TB
    subgraph Presentation["UI Layer — Jetpack Compose"]
        MA[MainActivity]
        FM[FocusModeSetupActivity]
        SA[StatisticsActivity]
        PA[ProfileActivity]
        LA[LoginActivity]
    end

    subgraph Domain["ViewModel + Logic"]
        VM[FocusModeSetupViewModel]
        SM[StatisticsManager]
        PM[PermissionManager]
        AM[AppManager]
    end

    subgraph Data["Data Layer"]
        DS[FocusDataStore — Preferences]
        RM[Room Database — Sessions]
        FS[Firebase Auth + Firestore]
        APM[AppPasswordManager]
    end

    subgraph Services["Background Services"]
        FG[FocusAccessibilityService]
        NL[NotificationListenerService]
        WM[WorkManager — Reminders & Backup]
        BR[BootReceiver]
    end

    MA --> VM --> DS
    MA --> SM --> RM
    SA --> SM
    PA --> FS
    LA --> FS
    FM --> VM
    VM --> AM
    FG --> AM
    WM --> FS
    BR --> WM
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin |
| **UI** | Jetpack Compose, Material Design 3 |
| **Architecture** | MVVM with ViewModel + StateFlow |
| **Local Storage** | Room (sessions), DataStore (preferences) |
| **Cloud** | Firebase Auth, Firestore (sync & backup) |
| **Background** | Foreground Service, AccessibilityService, WorkManager |
| **Auth** | Google Sign-In via Credential Manager |
| **Image Loading** | Coil |
| **Serialization** | Gson |
| **Build** | Gradle KTS with version catalogs |

---

## Project Structure

```
mindvault/
├── app/src/main/java/com/example/mindvault/
│   ├── MainActivity.kt                # Main entry, home screen, navigation
│   ├── MindVaultApplication.kt        # Application class, dependency setup
│   ├── AppBlockedActivity.kt          # Overlay shown when blocked app is opened
│   ├── BootReceiver.kt                # Re-schedule workers on device restart
│   │
│   ├── data/
│   │   ├── FocusDataStore.kt          # DataStore preferences wrapper
│   │   ├── FocusSession.kt            # Room entity + DAO
│   │   ├── StatisticsManager.kt       # Aggregation logic for analytics
│   │   ├── AuthManager.kt             # Firebase Auth wrapper
│   │   ├── UserManager.kt             # Firestore user profile CRUD
│   │   ├── AppPasswordManager.kt      # Encrypted app lock
│   │   └── BackupSyncWorker.kt        # Periodic Firestore sync via WorkManager
│   │
│   ├── model/
│   │   └── FocusModels.kt             # Domain models and enums
│   │
│   ├── notifications/
│   │   ├── NotificationHelper.kt      # Channel setup + notification builder
│   │   ├── FocusReminderScheduler.kt  # Schedule daily study reminders
│   │   ├── FocusReminderWorker.kt     # WorkManager worker for reminders
│   │   ├── MotivationScheduler.kt     # Schedule motivational quotes
│   │   └── DailyMotivationWorker.kt   # WorkManager worker for motivation
│   │
│   ├── services/
│   │   ├── FocusAccessibilityService.kt      # Monitors and blocks apps
│   │   └── FocusNotificationListenerService.kt # Manages notification suppression
│   │
│   ├── ui/
│   │   ├── FocusModeSetupActivity.kt  # Session configuration screen
│   │   ├── FocusModeSetupViewModel.kt # ViewModel for focus setup
│   │   ├── LockScreenActivity.kt      # Full-screen lock during sessions
│   │   ├── StatisticsActivity.kt      # Analytics dashboard
│   │   ├── ProfileActivity.kt         # User profile management
│   │   ├── LoginActivity.kt           # Firebase auth flow
│   │   ├── AchievementsActivity.kt    # Gamified milestones
│   │   ├── SecurityActivity.kt        # App lock settings
│   │   ├── PieChartComponents.kt      # Custom Compose chart components
│   │   ├── PremiumAnalyticsComponents.kt # Analytics card composables
│   │   ├── WeeklyComponents.kt        # Weekly breakdown UI
│   │   ├── WeeklyScreenTimeChart.kt   # Screen time visualization
│   │   └── theme/                     # Material 3 color, type, theme
│   │
│   └── utils/
│       ├── AppManager.kt             # App list and blocking logic
│       ├── OverlayBlocker.kt         # System overlay protection
│       ├── PermissionManager.kt      # Runtime permission handling
│       └── UsageStatsHelper.kt       # Android UsageStats API wrapper
│
├── build.gradle.kts                   # App-level build config
├── settings.gradle.kts                # Project settings
└── gradle/                            # Gradle wrapper + version catalog
```

---

## Getting Started

### Prerequisites

- **Android Studio** Ladybug (2024.2) or newer
- **JDK** 11+
- **Android SDK** 36 (compile) / 28 (min)
- A **Firebase** project with Auth and Firestore enabled

### 1. Clone

```bash
git clone https://github.com/WelcomeLegend-Git/mindvault.git
cd mindvault
```

### 2. Firebase Setup

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Add an Android app with package name `com.example.mindvault`
3. Download `google-services.json` and place it in `app/`
4. Enable **Authentication** (Google Sign-In) and **Firestore Database**

### 3. Build & Run

```bash
./gradlew assembleDebug
```

Or open the project in Android Studio and click **Run**.

---

## Permissions

| Permission | Purpose |
|------------|---------|
| `SYSTEM_ALERT_WINDOW` | Overlay to block apps during focus sessions |
| `FOREGROUND_SERVICE` | Persistent focus session timer |
| `WAKE_LOCK` | Prevent device sleep during sessions |
| `ACCESSIBILITY_SERVICE` | Monitor and intercept app launches |
| `NOTIFICATION_LISTENER` | Suppress distracting notifications |
| `USAGE_STATS` | Screen time analytics |
| `RECEIVE_BOOT_COMPLETED` | Reschedule workers after restart |

---

## License

This project is licensed under the [MIT License](LICENSE).
