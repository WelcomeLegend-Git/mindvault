# Release Notes — MindVault v3.4.1 (Build 12)

**Release Date:** September 19, 2026  
**Target Version:** `v3.4.1` (`versionCode = 12`)  
**Artifact:** [`docs/mindvault-v3.4.1.apk`](docs/mindvault-v3.4.1.apk) (7.81 MB)

---

## 🌟 User-Facing Overview

This hotfix release directly resolves the cloud backup error encountered after updating:

1. **Fixed "Missing or Insufficient Permissions" Error**: Cloud backup now connects seamlessly to Firebase without reporting `PERMISSION_DENIED`.
2. **User Data & Statistics Fully Preserved**: Your active streaks, weekly goals, focus session history, and application preferences remain completely intact.
3. **Seamless Cloud Sync**: Once logged in with your Google account, the app automatically recognizes your data ownership, enables cloud backup, and provides one-tap manual sync and automatic background synchronization.

---

## 🛠️ Technical Remediation & Change Log

### 1. Cloud Firestore Authorization Restoration
- **Root Cause**: The external security audit renamed the Firestore collection from `backups` to `backups_v1`. However, deployed Firebase Firestore security rules enforce `match /backups/{userId}` with `request.auth.uid == userId`. Because no server-side rule existed for `backups_v1`, every client read threw `PERMISSION_DENIED: Missing or insufficient permissions.`.
- **Fix**: Restored `BACKUPS` constant in `AuthManager.kt` to `"backups"`. Both uploads and reads now use the authorized path `/backups/{userId}`.
- **Rationale Comment**: Embedded Rule 1 mandatory in-code rationale documenting the Firestore server security rule constraint.

### 2. Auto-Claim of Legacy and Unowned Data
- In `AuthManager.recoverCloudData()`, if the local owner is `null` or `UNKNOWN` (from pre-v3.4.0 installs), the app automatically claims ownership for the currently authenticated Firebase user `session.uid` instead of displaying blocking error states.
- If the server has no prior backup, `recoverCloudData` automatically marks cloud backup as `READY` and triggers the initial background backup rather than forcing a manual import modal.

### 3. Symbol Decoupling for Clean Compilation
- Extracted `PreferenceImage` typealias into `PreferenceImage.kt` to ensure unambiguous symbol resolution during incremental and clean Gradle builds.
- Removed duplicate typealias from `RestoreTransaction.kt`.

### 4. Version Bump & Distribution
- Bumped `versionCode` to `12` and `versionName` to `"3.4.1"` in `app/build.gradle.kts`.
- Updated download link and button on the official web landing page (`docs/index.html`).
- Updated README.md release badge to `v3.4.1`.

---

## 🧪 Verification Matrix

| Component / Test | Method | Status | Details |
|:---|:---|:---|:---|
| Kotlin Debug Compilation | Gradle | ✅ PASS | `compileDebugKotlin` passed in 2m 36s |
| Production Release Build | Gradle | ✅ PASS | `assembleRelease` R8-minified in 7m 48s |
| APK Artifact Generation | Filesystem | ✅ PASS | `mindvault-v3.4.1.apk` (7,814,360 bytes) |
| Firestore Authorization | Static & Rule Check | ✅ PASS | Aligned with `/backups/{userId}` |
| Local Statistics Retention | Code Review | ✅ PASS | Read paths preserve all local SharedPreferences |
