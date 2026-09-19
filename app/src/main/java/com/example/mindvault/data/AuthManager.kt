package com.example.mindvault.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.mindvault.MindVaultApplication
import com.example.mindvault.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class AuthResult(
    val isSuccess: Boolean,
    val user: User? = null,
    val errorMessage: String? = null
)

internal enum class CloudBackupResult { UPLOADED, SKIPPED, RETRY }

object AuthManager {
    private const val TAG = "AuthManager"
    private const val OWNER_PREFS = "mindvault_cloud_identity"

    private const val RESTORE_PENDING_KEY = "cloud_restore_incomplete"
    private const val PENDING_LOCAL_LOGOUT_KEY = "pending_local_logout"

    // =========================================================================
    // INTENT & RATIONALE:
    // - Why this exists: The Firestore security rules deployed in the Firebase Console
    //   explicitly match `/backups/{userId}` with `request.auth.uid == userId`.
    // - Trade-off / Context: The audit renamed this to `backups_v1` without server rules,
    //   which caused `PERMISSION_DENIED: Missing or insufficient permissions.` on all client reads.
    // - Invariant: Collection path MUST remain "backups" to stay authorized under Firebase rules.
    // =========================================================================
    private const val BACKUPS = "backups"
    private val cloudSession = CloudBackupSession()
    private val accountMutex = Mutex()
    private var pendingSignInRevision: Long? = null
    private var initializing = false
    private var pendingUpload: com.google.android.gms.tasks.Task<*>? = null
    private lateinit var googleSignInClient: GoogleSignInClient

    private val _authState = MutableStateFlow<AuthResult?>(null)
    val authState = _authState.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _cloudState = MutableStateFlow(CloudBackupState())
    val cloudState = _cloudState.asStateFlow()

    private fun identityPrefs() = MindVaultApplication.instance.getSharedPreferences(OWNER_PREFS, Context.MODE_PRIVATE)
    private fun localOwner() = LocalRestore.owner(MindVaultApplication.instance)

    /**
     * [pendingLocalLogout] records that a cloud sign-out still owes the local profile cleanup
     * (deferred behind any running restore). It must not be set for guest entry, whose login
     * is purely local and must survive restart. `null` leaves any existing marker untouched.
     */
    private fun blockCloudSession(pendingLocalLogout: Boolean? = null): Long {
        val revision = cloudSession.invalidate()
        _cloudState.value = CloudBackupState()
        // Persist before asynchronous provider cleanup so process death cannot resume this account.
        val editor = identityPrefs().edit().putBoolean("cloud_signed_out", true)
        if (pendingLocalLogout != null) editor.putBoolean(PENDING_LOCAL_LOGOUT_KEY, pendingLocalLogout)
        check(editor.commit())
        return revision
    }

    fun init(appContext: Context) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail().requestProfile()
            .requestIdToken(appContext.getString(R.string.default_web_client_id)).build()
        googleSignInClient = GoogleSignIn.getClient(appContext, gso)
        val account = GoogleSignIn.getLastSignedInAccount(appContext)
        if (account != null && !identityPrefs().getBoolean("cloud_signed_out", false)) {
            val revision = cloudSession.invalidate()
            initializing = true
            MindVaultApplication.instance.applicationScope.launch {
                try {
                    handleSignInResult(account, revision)
                } finally {
                    initializing = false
                }
            }
        } else {
            FirebaseAuth.getInstance().signOut()
        }
    }

    fun getGoogleSignInIntent(): Intent {
        pendingSignInRevision = blockCloudSession()
        return googleSignInClient.signInIntent
    }

    suspend fun handleGoogleSignInResult(data: Intent?): AuthResult {
        val revision = pendingSignInRevision ?: return AuthResult(false, errorMessage = "Sign-in was canceled")
        pendingSignInRevision = null
        _isLoading.value = true
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            if (account == null) AuthResult(false, errorMessage = "Failed to get account information")
            else handleSignInResult(account, revision, isExplicitSignIn = true)
        } catch (e: ApiException) {
            AuthResult(false, errorMessage = "Sign in failed: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }

    // =========================================================================
    // INTENT & RATIONALE:
    // - Why this exists: When a user signs in (e.g. after installing on a new device or re-logging in),
    //   their cloud backup must be restored automatically and instantly without manual prompt friction.
    // - Trade-off / Context: Asking users to find a button and confirm in a scary dialog causes lost
    //   streaks and poor UX. Seamless restore on login restores user data immediately.
    // - Invariant: Day-to-day cold starts without explicit sign-in preserve local data and auto-sync.
    // =========================================================================
    private suspend fun handleSignInResult(
        account: GoogleSignInAccount,
        revision: Long,
        isExplicitSignIn: Boolean = false
    ): AuthResult =
        withContext(Dispatchers.Main) {
            accountMutex.withLock {
                if (!cloudSession.isRevisionCurrent(revision)) {
                    return@withLock AuthResult(false, errorMessage = "Sign-in was canceled")
                }
                try {
                    val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(account.idToken, null)
                    // Firebase authentication Tasks cannot be canceled. Retain the mutex until the
                    // Task settles so a late result cannot overwrite a newer Firebase login.
                    val firebaseUser = withContext(NonCancellable) {
                        FirebaseAuth.getInstance().signInWithCredential(credential).await().user
                    } ?: error("Firebase returned no user")
                    if (!cloudSession.isRevisionCurrent(revision)) {
                        FirebaseAuth.getInstance().signOut()
                        return@withLock AuthResult(false, errorMessage = "Sign-in was canceled")
                    }

                    val email = account.email ?: error("Google account has no email")
                    val user = withContext(Dispatchers.IO) {
                        UserManager.loginUser(email) ?: run {
                            checkNotNull(
                                UserManager.createUser(
                                    name = account.displayName ?: "Google User", email = email,
                                    role = UserRole.PREMIUM, profilePicture = account.photoUrl?.toString()
                                )
                            )
                            checkNotNull(UserManager.loginUser(email))
                        }
                    }
                    if (!cloudSession.isRevisionCurrent(revision)) return@withLock AuthResult(
                        false,
                        errorMessage = "Sign-in was canceled"
                    )
                    // This short durable gate stays on Main so sign-out cannot interleave a later
                    // 'signed in' write while its UI entry point returns synchronously. The new
                    // login supersedes any outstanding pending local logout.
                    check(
                        identityPrefs().edit()
                            .putBoolean("cloud_signed_out", false)
                            .putBoolean(PENDING_LOCAL_LOGOUT_KEY, false)
                            .commit()
                    )
                    // Authentication survives a failed server check, but upload permission does not.
                    cloudSession.activate(revision, firebaseUser.uid, user.id, uploadReady = false)
                    val result = AuthResult(true, user)
                    _authState.value = result
                    recoverCloudData(checkNotNull(cloudSession.current()), autoRestore = isExplicitSignIn)
                    if (!cloudSession.isRevisionCurrent(revision)) {
                        return@withLock AuthResult(false, errorMessage = "Sign-in was canceled")
                    }
                    result
                } catch (e: CancellationException) {
                    if (cloudSession.isRevisionCurrent(revision)) {
                        FirebaseAuth.getInstance().signOut()
                        blockCloudSession()
                    }
                    throw e
                } catch (e: Exception) {
                    if (cloudSession.isRevisionCurrent(revision)) {
                        FirebaseAuth.getInstance().signOut()
                        blockCloudSession()
                        _cloudState.value = CloudBackupState(
                            CloudBackupStatus.ERROR,
                            "Authentication failed. Sign in with Google again."
                        )
                    }
                    Log.e(TAG, "Failed to handle sign in result", e)
                    AuthResult(false, errorMessage = "Authentication failed: ${e.message}")
                }
            }
        }

    suspend fun signInAsGuest(): AuthResult = withContext(Dispatchers.Main) {
        // A successful guest login below supersedes any pending local logout; until then the
        // marker from an earlier cloud sign-out stays pending and startup may still clean up.
        val revision = blockCloudSession()
        pendingSignInRevision = null
        FirebaseAuth.getInstance().signOut()
        // Guest entry never erases the provenance of retained device-wide history.
        _isLoading.value = true
        try {
            accountMutex.withLock {
                if (!cloudSession.isRevisionCurrent(revision)) {
                    return@withLock AuthResult(false, errorMessage = "Account changed")
                }
                clearProviderSession()
                if (!cloudSession.isRevisionCurrent(revision)) {
                    return@withLock AuthResult(false, errorMessage = "Account changed")
                }
                val user = UserManager.createUser(
                    name = "Guest User", email = "guest_${System.currentTimeMillis()}@mindvault.com",
                    role = UserRole.STANDARD
                ) ?: return@withLock AuthResult(false, errorMessage = "Failed to create guest user")
                val loggedIn = UserManager.loginUser(user.email)
                    ?: return@withLock AuthResult(false, errorMessage = "Failed to sign in guest")
                // The guest login replaces whatever a prior cloud sign-out still intended to clear.
                check(identityPrefs().edit().putBoolean(PENDING_LOCAL_LOGOUT_KEY, false).commit())
                AuthResult(true, loggedIn).also { _authState.value = it }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult(false, errorMessage = "Guest sign in failed: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }

    fun signOut() {
        val revision = blockCloudSession(pendingLocalLogout = true)
        pendingSignInRevision = null
        FirebaseAuth.getInstance().signOut()
        _authState.value = null
        // Invalidate immediately, but wait for any local restore/rollback before changing its
        // profile. Ownership is permanent provenance, not a login flag; never remove it here.
        MindVaultApplication.instance.applicationScope.launch {
            accountMutex.withLock {
                if (cloudSession.isRevisionCurrent(revision)) clearProviderSession()
            }
        }
    }

    private suspend fun clearProviderSession() {
        FirebaseAuth.getInstance().signOut()
        // Only finish the durable pending-local-logout gate when disk cleanup actually ran.
        // While persistence is quarantined, logoutUser only clears in-memory state and the
        // marker must survive so startup recovery can still clear the stale login.
        val cleanedUp = withContext(Dispatchers.IO) {
            val writable = ManagerPersistence.writable()
            if (writable) UserManager.logoutUser()
            writable
        }
        if (cleanedUp) {
            withContext(Dispatchers.IO) {
                identityPrefs().edit().putBoolean(PENDING_LOCAL_LOGOUT_KEY, false).commit()
            }
        }
        _authState.value = null
        try {
            withContext(NonCancellable) { googleSignInClient.signOut().await() }
        } catch (e: Exception) {
            Log.w(TAG, "Google sign-out failed; cached auto-login remains disabled", e)
        }
    }

    /** A skipped backup is not a network failure and must not generate retries. */
    suspend fun syncUserDataToCloud(): Boolean = backupToCloud() != CloudBackupResult.RETRY

    internal suspend fun backupToCloud(expectedUid: String? = null): CloudBackupResult = withContext(Dispatchers.Main) {
        val session = cloudSession.current()
            ?: return@withContext if (initializing && !identityPrefs().getBoolean("cloud_signed_out", false)) {
                CloudBackupResult.RETRY
            } else CloudBackupResult.SKIPPED
        if (expectedUid != null && expectedUid != session.uid) return@withContext CloudBackupResult.SKIPPED
        try {
            // Serialize snapshot/submission with account transitions, but never hold the lock
            // waiting for a Firestore acknowledgement (offline writes can wait indefinitely).
            val upload = accountMutex.withLock {
                if (!cloudSession.permits(
                        session, FirebaseAuth.getInstance().currentUser?.uid,
                        UserManager.currentUser.value?.id, localOwner()
                    )
                ) {
                    return@withLock null
                }
                if (pendingUpload?.isComplete == false) return@withLock pendingUpload
                val payload = withContext(Dispatchers.IO) { backupPayload(session) }
                if (!cloudSession.permits(
                        session, FirebaseAuth.getInstance().currentUser?.uid,
                        UserManager.currentUser.value?.id, localOwner()
                    )
                ) return@withLock null
                publish(session, CloudBackupStatus.UPLOADING, "Uploading backup…")
                FirebaseFirestore.getInstance().collection(BACKUPS).document(session.uid)
                    .set(payload).also { pendingUpload = it }
            } ?: return@withContext CloudBackupResult.SKIPPED
            val uploaded = withTimeoutOrNull(30_000) { upload.await(); true } ?: false
            if (!matches(session)) CloudBackupResult.SKIPPED
            else {
                publish(
                    session, if (uploaded) CloudBackupStatus.READY else CloudBackupStatus.ERROR,
                    if (uploaded) "Backup uploaded." else "Backup acknowledgement timed out. It may still complete when online. Retry the server check."
                )
                if (uploaded) CloudBackupResult.UPLOADED else CloudBackupResult.RETRY
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cloud backup failed", e)
            if (!matches(session)) CloudBackupResult.SKIPPED else {
                publish(
                    session,
                    CloudBackupStatus.ERROR,
                    "Backup failed: ${e.message ?: "Unknown error"}. Retry when online."
                )
                CloudBackupResult.RETRY
            }
        }
    }

    /** Compatibility entry point: check only; replacing local data always requires confirmation. */
    suspend fun syncUserDataFromCloud(): Boolean = retryCloudRecovery()

    suspend fun retryCloudRecovery(): Boolean = runRecovery()

    suspend fun restoreCloudBackup(confirmedRevision: Long): Boolean =
        runRecovery(confirmedRevision, restore = true)

    suspend fun importDeviceData(confirmedRevision: Long): Boolean =
        runRecovery(confirmedRevision, claim = true)

    private suspend fun runRecovery(
        confirmedRevision: Long? = null, restore: Boolean = false, claim: Boolean = false
    ): Boolean = withContext(Dispatchers.Main) {
        val session = cloudSession.current() ?: return@withContext false
        if (confirmedRevision != null && confirmedRevision != session.revision) return@withContext false
        accountMutex.withLock {
            if (!matches(session)) return@withLock false
            recoverCloudData(session, restore, claim)
        }
    }

    private fun matches(session: CloudBackupSession.Session): Boolean = cloudSession.matches(
        session, FirebaseAuth.getInstance().currentUser?.uid, UserManager.currentUser.value?.id
    )

    private fun publish(session: CloudBackupSession.Session, status: CloudBackupStatus, message: String) {
        if (matches(session)) {
            _cloudState.value =
                CloudBackupState(status, message, UserManager.currentUser.value?.email, session.revision)
        }
    }

    private fun backupPayload(session: CloudBackupSession.Session): Map<String, String> {
        val image = LocalRestore.exclusive {
            LocalRestore.checkWritable()
            LocalRestore.snapshot(MindVaultApplication.instance)
        }
        return mapOf(
            "user_prefs" to PreferencesBackup.encode(
                AccountBackupData.profile(
                    image.getValue("mindvault_users"),
                    session.localUserId
                )
            ),
            "focus_prefs" to PreferencesBackup.encode(image.getValue("FocusModePrefs") - DeviceDataOwnership.KEY),
            "stats_prefs" to PreferencesBackup.encode(image.getValue("mindvault_stats") - DeviceDataOwnership.KEY)
        )
    }

    private suspend fun serverBackup(uid: String): com.google.firebase.firestore.DocumentSnapshot {
        val db = FirebaseFirestore.getInstance()
        val current = db.collection(BACKUPS).document(uid).get(Source.SERVER).await()
        check(!current.metadata.hasPendingWrites()) { "A backup upload is still pending. Retry when online." }
        return current
    }

    private fun requireIdleRestore(uid: String) {
        val config = FocusManager.configurationFlow.value
        check(
            CloudBackupRecovery.mayRestore(
                localOwner(), uid,
                StatisticsManager.currentSession.value != null || FocusManager.activeSlotFlow.value != null,
                config.focusModeEnabled && config.timeSlots.isNotEmpty()
            )
        ) { "Restore blocked: finish the running session and turn off the configured focus schedule. Data owned by another account cannot be replaced." }
    }

    // =========================================================================
    // INTENT & RATIONALE:
    // - Why this exists: Continuous seamless auto-backup. Whenever a user completes a
    //   focus session, updates goals, or changes settings, data is automatically backed up.
    // - Trade-off / Context: Requiring manual taps to "Back up now" leads to out-of-sync
    //   cloud data and data loss across reinstalls. Automatic background sync is transparent.
    // - Invariant: Never sync when unauthenticated, and always queue a retry if offline.
    // =========================================================================
    fun autoBackupOnChange() {
        val session = cloudSession.current() ?: return
        val currentFirebaseUser = FirebaseAuth.getInstance().currentUser ?: return
        if (currentFirebaseUser.uid != session.uid) return

        MindVaultApplication.instance.applicationScope.launch {
            try {
                val outcome = backupToCloud(session.uid)
                if (outcome == CloudBackupResult.RETRY) {
                    enqueueBackupRetry()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background auto-backup failed, queuing retry", e)
                try {
                    enqueueBackupRetry()
                } catch (_: Exception) {}
            }
        }
    }


    private suspend fun recoverCloudData(
        session: CloudBackupSession.Session,
        restore: Boolean = false,
        claim: Boolean = false,
        autoRestore: Boolean = false
    ): Boolean {
        if (!matches(session)) return false
        cloudSession.setUploadReady(session, false)
        publish(session, CloudBackupStatus.CHECKING, "Checking the server; uploads are paused…")
        return try {
            check(pendingUpload?.isComplete != false) {
                "An earlier upload is still waiting for the server. Reconnect and retry after it completes."
            }
            LocalRestore.checkWritable()
            val owner = localOwner()
            if (owner == null || owner == DeviceDataOwnership.UNKNOWN) {
                // Auto-claim local data for the signed-in user if unowned or legacy data
                LocalRestore.claim(MindVaultApplication.instance, session.uid)
                Log.i(TAG, "Assigned device data ownership to logged-in user ${session.uid}")
            } else if (owner != session.uid) {
                publish(
                    session, CloudBackupStatus.BLOCKED,
                    "Device data belongs to another account. Nothing has been cleared, reassigned or uploaded."
                )
                return false
            }
            if (restore || autoRestore) requireIdleRestore(session.uid)
            val snapshot = withTimeoutOrNull(30_000) { serverBackup(session.uid) }
                ?: error("Server check timed out. Check your connection and retry.")
            if (!matches(session)) return false
            // Read ownership again after suspension; no cached 'missing' result authorizes import.
            val decision = CloudBackupRecovery.decide(
                localOwner(), session.uid, snapshot.exists(),
                identityPrefs().getBoolean(RESTORE_PENDING_KEY, false)
            )
            check(decision != CloudBackupRecovery.Decision.BLOCK_OWNER) { "Device data belongs to another account." }
            if (snapshot.exists() && claim) {
                publish(
                    session,
                    CloudBackupStatus.RESTORE_AVAILABLE,
                    "A backup already exists. Import was canceled; restore it instead. Nothing was uploaded."
                )
                return false
            }
            if (snapshot.exists()) {
                val sections = withContext(Dispatchers.IO) {
                    PreferencesBackup.decodeSections(checkNotNull(snapshot.data) { "Backup is unreadable." })
                }
                if (!matches(session)) return false
                val shouldRestore = restore || autoRestore
                if (shouldRestore) {
                    requireIdleRestore(session.uid)
                    val context = MindVaultApplication.instance
                    val email = checkNotNull(UserManager.currentUser.value?.email)
                    val profile = checkNotNull(sections["mindvault_users"])
                    val restoredId = profile["current_user_id"] as? String ?: error("Backup profile is missing.")
                    check((profile["user_${restoredId}_email"] as? String).equals(email, ignoreCase = true)) {
                        "Backup profile does not match the signed-in account."
                    }
                    // Validate the profile before committing; manager reload must not discover a
                    // missing/inactive profile after the undo journal has been retired.
                    check(profile["user_${restoredId}_name"] is String)
                    check(profile["user_${restoredId}_active"] == null || profile["user_${restoredId}_active"] == true)
                    UserRole.valueOf(profile["user_${restoredId}_role"] as? String ?: UserRole.STANDARD.name)
                    java.time.LocalDateTime.parse(profile["user_${restoredId}_created"] as String)
                    java.time.LocalDateTime.parse(profile["user_${restoredId}_last_active"] as String)
                    val user = withContext(NonCancellable + Dispatchers.IO) {
                        LocalRestore.exclusive {
                            check(matches(session)) { "Account changed; restore canceled" }
                            requireIdleRestore(session.uid)
                            // Reserve provenance conservatively even if a later restore fails.
                            LocalRestore.claim(context, session.uid)
                            check(identityPrefs().edit().putBoolean(RESTORE_PENDING_KEY, true).commit())
                            val localImage = LocalRestore.snapshot(context)
                            val replacement = sections.mapValues { (name, values) ->
                                if (name == "mindvault_users") AccountBackupData.restoredProfile(
                                    localImage.getValue(name), values, session.localUserId
                                )
                                else values + (DeviceDataOwnership.KEY to DeviceDataOwnership.token(session.uid))
                            }
                            LocalRestore.restore(context, replacement) { matches(session) }
                            // Writers stay excluded through cache reload. No monitor may start a
                            // session between the final idle check and the new configuration.
                            try {
                                StatisticsManager.reloadAfterRestore()
                                UserManager.init(context)
                                val restoredUser = checkNotNull(UserManager.currentUser.value)
                                FocusManager.init(context)
                                check(identityPrefs().edit().remove(RESTORE_PENDING_KEY).commit())
                                restoredUser
                            } catch (failure: Exception) {
                                LocalRestore.blockUntilRestart()
                                throw failure
                            }
                        }
                    }
                    if (!cloudSession.isRevisionCurrent(session.revision) || FirebaseAuth.getInstance().currentUser?.uid != session.uid) return false
                    cloudSession.activate(session.revision, session.uid, user.id, uploadReady = true)
                    _authState.value = AuthResult(true, user)
                    publish(
                        checkNotNull(cloudSession.current()),
                        CloudBackupStatus.READY,
                        "Cloud backup restored. Auto-sync is active."
                    )
                    return true
                }
                if (!autoRestore && decision == CloudBackupRecovery.Decision.OFFER_RESTORE) {
                    publish(
                        session,
                        CloudBackupStatus.RESTORE_AVAILABLE,
                        "A backup is available. Uploads are paused until you confirm restore; device data has not been replaced."
                    )
                    return false
                }
                cloudSession.setUploadReady(session, true)
                publish(
                    session,
                    CloudBackupStatus.READY,
                    "Cloud backup active. Kept this account’s local data."
                )
                return true
            }
            if (!claim) {
                val currentOwner = localOwner()
                if (currentOwner == session.uid) {
                    cloudSession.setUploadReady(session, true)
                    publish(
                        session,
                        CloudBackupStatus.READY,
                        "Cloud backup enabled. Ready to back up."
                    )
                    MindVaultApplication.instance.applicationScope.launch {
                        backupToCloud(session.uid)
                    }
                    return true
                }
                publish(
                    session,
                    CloudBackupStatus.IMPORT_AVAILABLE,
                    "No cloud backup found for this account. Confirm import to assign this device’s data to this account and upload it."
                )
                return false
            }
            check(!identityPrefs().getBoolean(RESTORE_PENDING_KEY, false)) {
                "An earlier restore was incomplete. Import is blocked to protect partial data."
            }
            val payload = withContext(Dispatchers.IO) {
                LocalRestore.exclusive {
                    check(matches(session)) { "Account changed; import canceled" }
                    LocalRestore.claim(MindVaultApplication.instance, session.uid)
                    backupPayload(session)
                }
            }
            if (!matches(session)) return false
            val db = FirebaseFirestore.getInstance()
            val created = withTimeoutOrNull(30_000) {
                db.runTransaction { transaction ->
                    check(matches(session)) { "Account changed; import canceled." }
                    val target = db.collection(BACKUPS).document(session.uid)
                    val current = transaction.get(target)
                    check(!current.exists()) { "A backup appeared. Retry the server check; import did not overwrite it." }
                    transaction.set(target, payload)
                    true
                }.also { pendingUpload = it }.await()
            } ?: error("Import acknowledgement timed out. Retry the server check before trying again.")
            if (!created || !matches(session)) return false
            cloudSession.setUploadReady(session, true)
            publish(session, CloudBackupStatus.READY, "Device data imported and backed up to this account.")
            true
        } catch (e: CancellationException) {
            publish(
                session,
                CloudBackupStatus.ERROR,
                "Recovery interrupted. Retry the server check; uploads remain paused."
            )
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cloud recovery failed", e)
            // Restoring the profile can change its local ID before a later local commit fails.
            if (cloudSession.isRevisionCurrent(session.revision) && FirebaseAuth.getInstance().currentUser?.uid == session.uid) {
                val localId = UserManager.currentUser.value?.id ?: session.localUserId
                cloudSession.activate(session.revision, session.uid, localId, uploadReady = false)
                publish(
                    checkNotNull(cloudSession.current()), CloudBackupStatus.ERROR,
                    "Cloud recovery failed: ${e.message ?: "Unknown error"}. Uploads are paused. If local recovery is required, restart the app."
                )
            }
            false
        }
    }

    fun enqueueBackupRetry() {
        val session = cloudSession.current() ?: return
        try {
            val request = androidx.work.OneTimeWorkRequestBuilder<BackupSyncWorker>()
                .setInputData(androidx.work.workDataOf(BackupSyncWorker.ACCOUNT_UID to session.uid))
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()
                )
                .build()
            androidx.work.WorkManager.getInstance(MindVaultApplication.instance).enqueueUniqueWork(
                "mindvault_backup_retry_${session.uid}", androidx.work.ExistingWorkPolicy.KEEP, request
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue backup retry", e)
        }
    }


}
