# Architecture audit remediation status

This changelist is build- and test-verified but uncommitted. This document is a
source-review checklist, not a release certification.

## Implemented

- Typed, versioned preference backup codec; legacy numeric/set conversion; validation of all sections before restore writes.
- Firebase/local sign-out and guest isolation; session revisions reject stale operations; profile exports exclude other users and local login-session records; account-tagged retries distinguish skipped work from network failures.
- Conservative cloud ownership for device-wide focus/statistics data with a durable provenance token replicated into the data stores; mixed/unknown provenance fails closed for uploads. Import reserves ownership before network submission. Restore/import/retry are exposed through an explicit, consent-gated cloud recovery card on the Profile screen.
- Crash-safe local restore: durable undo journal, rollback, repeatable startup recovery before managers load, and best-effort manager-readable validation before the journal retires. Persistence quarantine freezes writers without crashing the focus loop or sign-out flows; a durable pending-local-logout gate finishes interrupted cloud sign-out cleanup while guest sessions survive restart.
- Restore disk work and backup encoding run on IO; a coordinator holds the manager locks; main-thread callers can still wait behind restore commits and cache reload (documented residual).
- Single monitor job, serialized manager initialization/configuration/transitions, authoritative focus toggle preservation, and completion based on the previous slot's time window. Repeated statistics initialization does not recover the currently running in-process session as an orphan.
- One configuration-save path on an IO dispatcher; successful-save UI only after persistence returns; draft edits no longer enqueue backups.
- Reminder scheduling on initialization/configuration/toggle changes; tracking of prior alarm count; receiver ignores disabled or stale deliveries; wording acknowledges inexact timing.
- Overlay permission checks, guarded window operations, non-focusable window flags, destruction cleanup, target/label state with unchanged-bounds updates skipped, and schedule-boundary expiry reconciliation that rechecks adjacent slots without dropping a still-required block.
- Salted versioned PBKDF2-HMAC-SHA256 passwords with off-main hashing, constant-time comparison and automatic legacy upgrade. Existing passwords do not need resetting.
- Local session-record deletion includes records from earlier processes; preference string sets are copied before mutation.
- Android backup/device-transfer allowlists exclude local passwords, user-session records, Firebase identity and cloud-ownership metadata.
- Accessibility distraction accounting deduplicated from enforcement; exactly-once node cleanup; overlay operations serialized on the main thread; preserved configured service timeout; overlay label/bounds reconcile on target changes. Enforcement policy itself is unchanged. Notification quote/author bitmaps now carry content descriptions for TalkBack.
- Notification posting handles denied/revoked permission without crashing; lock-screen back handling uses the AndroidX dispatcher and still closes the task. Firebase App Distribution DSL import corrected.
- Broad installed-package visibility retained with a targeted, documented lint exception; this is not store-policy approval.
- Portable JVM setup, explicit optional release credentials (otherwise unsigned, never debug-signed), WorkManager deduplication, coroutine test dependency, CI with parallel jobs, dependency-catalog consolidation, build-only Firebase configuration, unit/lint/release/vulnerability-inventory tasks and report upload. Actions pinned to verified full commit SHAs, Gradle wrapper checksum added, OSV-based resolved-dependency scan with offline tests, and GitHub dependency review on PRs.
- Session accounting helper splits minutes across midnight and limits process-recovery credit to persisted observations.
- Architecture/setup/security documentation corrected.

## Explicitly retained at the user's request

- Existing selected-app/rest-time policy differences between accessibility and notification handling.
- Existing Settings/uninstall/revocation interception and call/audio behavior. Their original safety, false-positive and restoration concerns are NOT resolved by the other changes.
- No improvements to preventing OS permission revocation or uninstall were made.

## Validation completed

- 83/83 Gradle `:app:testDebugUnitTest` tests passed in 9m54s (12 suites: backup codec, cloud session/recovery, ownership, restore transaction, preflight, provenance, distraction-burst and overlay-boundary helpers, passwords, session accounting). XML reports verified, zero failures or skips.
- `:app:lintDebug` completed twice after integration: 0 errors, 170 warnings (down from 184 — dependency-catalog consolidation and notification content-description fixes cleared the rest). The package-visibility diagnostic carries a targeted documented exception.
- `:app:assembleRelease --offline` passed in 13m46s, including R8 minification, resource shrinking and APK packaging.
- Final integrated state re-verified: `:app:testDebugUnitTest` (83/83), `:app:lintDebug` (0 errors) and `:app:compileDebugKotlin` all pass with every change from all work streams included.
- `:app:compileDebugKotlin` and the full unit-test task re-verified after all parallel-agent integration edits; resource processing succeeded for the new string/layout resources.
- The Gradle test JVM launcher required a one-time workaround: the machine PATH contains a literal quote that breaks worker launching. A wrapper script removed quotes only from the child-process environment; no global setting changed.
- One Kotlin IDE diagnostic pair for the overlay string resource is stale (the resource exists and resource processing plus compilation passed); a Gradle sync in Android Studio clears it.
- No device run, emulator run, CI run, or signing-certificate verification was performed.

## Required before release

- Minified-release device smoke test and signing verification; the CI workflow has not yet run. Local debug tests, lint and release packaging have passed; lint warnings remain.
- Device/emulator tests for sign-out/guest/account switches (including guest survival across restart), delayed Firebase Tasks, background retries, restore failure, quarantine/restart recovery, process death, session interruption, reboot reminders, overlay permission revocation and multi-window cleanup. First-round on-device checks completed 2026-09-17 (see "On-device verification" below); interactive flows (password set/unlock, focus slots, Google sign-in, cloud restore) still need manual testing with real accounts.
- Direct preference readers outside the cooperating manager locks see new data non-atomically; guarantee covers single-process cooperating writers only.
- Recovery/import actions are UI-accessible now; exact attribution for account intervals shorter than a monitor poll is approximate (auth-transition hook still possible as follow-up).
- On failed/offline recovery, cloud uploads remain disabled; recovery requires retry or restart while quarantined. Corrupt journals or persistent storage failure intentionally block writes until restart, with no repair UI.
- Session helper tests do not prove end-to-end Firebase cancellation behavior. Already submitted Firestore writes can finish with their original snapshot after sign-out.
- Existing global statistics are not a fully per-account database. Provenance stamps prevent silent cross-account uploads, but no destructive clearing or account migration was introduced.
- Event-only accessibility evaluation, full overlay expiry reconciliation in every quiet-period case, comprehensive UI/accessibility review (TalkBack on notification quotes and overlay flows remain device checks), and device validation of midnight/checkpoint statistics still require work.
- Deployed Firestore authorization rules match `backups/{userId}`. Collection restored to `backups` in v3.4.1 so queries succeed with existing deployed security rules.
- Supply release credentials and verify certificate continuity with installed versions before distribution. Debug-signing continuity with the installed device build was verified via apksigner (2026-09-17); production distribution still requires the four `MINDVAULT_RELEASE_*` credentials.
- Backups write and read from authorized `backups` collection; type-safe JSON schema seamlessly decodes both legacy and tagged payloads.
- Wrapper JAR checksum caveat: the current wrapper JAR matches official older-wrapper checksums, not the 8.13 JAR; verify against the distribution checksum and consider re-bootstrapping the wrapper.
- Branch protection may need the new CI check names listed in docs/CI_SUPPLY_CHAIN.md.

## On-device verification (2026-09-17, Vivo V2437, Android 16 / API 36)

Setup: installed app (v3.3.2 build 10, debug-signed, Aug 7) was verified via apksigner to share the debug
keystore with the new build, then upgraded in place with `adb install -r` — user data preserved.

| Check | Result |
|---|---|
| In-place upgrade with existing data (163 statistics keys, login, session history) | PASS — data intact |
| Cold start after update | PASS — no crash, home screen renders |
| Restart survival with `cloud_signed_out=true` (regression for the pending-logout fix) | PASS — `current_user_id` survived restart; old code would have cleared it |
| Provenance stamping of legacy data | PASS — both stores stamped `unknown` (fail-closed; uploads stay blocked until explicit claim) |
| Accessibility service binds after update | PASS — preserved `notificationTimeout=100` confirmed in `dumpsys accessibility` |
| Notification listener binds after update | PASS |
| Window-event churn (app switches, screen off/on, Settings launches) | PASS — 0 crashes, no spurious statistics writes |
| Crash scan (`logcat -b crash` + FATAL grep) across all runs | PASS — zero fatal exceptions |

Not exercised on device (deliberately, to avoid altering user settings/accounts): setting an app
password, configuring focus time slots, Google sign-in / live cloud backup flows, overlay blocking
of a selected app during an active session.

Focus services were re-enabled via adb after the update reset them (equivalent to re-tapping
enable in the app). Pre-test APK backup: `../mindvault-test-backup/` (outside the repo).

Nothing has been committed or pushed. Any push remains withheld until the user explicitly authorizes it.
