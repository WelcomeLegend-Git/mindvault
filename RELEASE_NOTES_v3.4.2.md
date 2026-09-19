# Release Notes — MindVault v3.4.2 (Build 13)

**Release Date:** September 19, 2026  
**Target Version:** `v3.4.2` (`versionCode = 13`)  
**Artifact:** [`docs/mindvault-v3.4.2.apk`](docs/mindvault-v3.4.2.apk) (7.81 MB / 7,814,360 bytes)

---

## 🌟 User-Facing Overview

MindVault v3.4.2 brings a major redesign and seamless automation to Cloud Backup & Sync:

1. **Instant Automatic Restore on Sign-In**: When you sign in with your Google account (whether on a newly installed phone or after an update), your cloud backup is restored instantly and automatically. Your streaks, focus configurations, achievements, and weekly goals (e.g. 1200 mins) appear immediately without requiring manual confirmation dialogs.
2. **Continuous Background Auto-Sync**: The app now automatically syncs your data in the background whenever changes happen:
   - Completing or stopping a focus session
   - Updating your profile or weekly study goals
   - Adjusting your focus schedule or blocked apps
   - Earning new achievements
3. **Redesigned Settings-Integrated UI**: The old unstyled recovery card in the center of the Profile screen has been removed. In its place is a sleek **"Cloud Backup & Sync"** option directly inside the Profile Settings card, featuring real-time status cues ("Auto-sync active", "Syncing changes to cloud…") and opening an elegant purple glassmorphic dialog.

---

## 🛠️ Technical Remediation & Change Log

### 1. Instant Cloud Restore on Sign-In
- Enhanced `AuthManager.handleSignInResult` with an `isExplicitSignIn` flag. When true, `recoverCloudData` runs with `autoRestore = true`.
- Decodes and commits the cloud snapshot to local SharedPreferences immediately if valid cloud data exists, bypassing manual dialog friction while strictly observing `requireIdleRestore` (preventing restores while an active focus session is locked).

### 2. Mutation-Hooked Continuous Auto-Backup
- Added `AuthManager.autoBackupOnChange()` running on `MindVaultApplication.applicationScope`.
- Seamlessly invoked across critical state mutation endpoints:
  - `StatisticsManager.finalizeSession()` & `saveAchievements()`
  - `UserManager.saveUser()`
  - `FocusDataStore.saveConfiguration()`
- If network connection is unavailable, WorkManager enqueues an expedited, network-constrained retry worker.

### 3. Glassmorphic UI Redesign
- Replaced the standalone `CloudRecoveryCard` in the main profile scroll view with `ProfileOptionItem` in `ProfileActivity.kt` under `SettingsSection`.
- Rebuilt `CloudRecoveryCard.kt` into `CloudBackupDialog(onDismiss: () -> Unit)` with MindVault's signature glassmorphic theme (background `#1E1B2E`, glowing purple border `#8B5CF6`, rounded corners `24.dp`).
- Preserved a no-op `@Composable internal fun CloudRecoveryCard()` compatibility stub to prevent breaking any legacy references.

### 4. Build, Distribution & Governance
- Bumped `versionCode` to `13` and `versionName` to `"3.4.2"` in `app/build.gradle.kts`.
- Updated official landing page download button and link in `docs/index.html` to `mindvault-v3.4.2.apk`.
- Updated README.md APK download badge to `v3.4.2`.
- Created living feature documentation at `docs/features/cloud_backup_and_sync.md`.

---

## 🧪 Verification Matrix

| Component / Test | Method | Status | Details |
|:---|:---|:---|:---|
| Kotlin Debug Compilation | Gradle | ✅ PASS | `compileDebugKotlin` passed in 1m 15s |
| Production Release Build | Gradle | ✅ PASS | `assembleRelease` R8 minification passed in 7m 41s |
| APK Artifact Generation | Filesystem | ✅ PASS | `mindvault-v3.4.2.apk` (7,814,360 bytes) |
| In-Code Rationale Rule | Manual Review | ✅ PASS | Rule 1 structured `INTENT & RATIONALE` comments present at all modified sites |
| Feature Documentation | Living Doc | ✅ PASS | Rule 2 `docs/features/cloud_backup_and_sync.md` created |
| Local Data Preservation | Logic Review | ✅ PASS | All local data mutation and restore paths verify session UID and idle lock state |
