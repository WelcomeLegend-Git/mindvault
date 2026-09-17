package com.example.mindvault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mindvault.data.AuthManager
import com.example.mindvault.data.CloudBackupState
import com.example.mindvault.data.CloudBackupStatus
import com.example.mindvault.data.FocusManager
import com.example.mindvault.data.StatisticsManager
import com.example.mindvault.data.User
import com.example.mindvault.data.UserManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class CloudAction { RETRY, BACKUP, RESTORE, IMPORT }

private data class CloudRequest(
    val action: CloudAction,
    val revision: Long,
    val email: String,
    val userId: String,
    val status: CloudBackupStatus
) {
    fun matches(state: CloudBackupState, user: User?, loggedIn: Boolean): Boolean =
        loggedIn && user != null && user.id == userId && user.email == email &&
                state.accountEmail == email && state.revision == revision &&
                state.status == status && !state.busy
}

private fun restoreBlockedNow(): Boolean {
    val config = FocusManager.configurationFlow.value
    return StatisticsManager.currentSession.value != null ||
            FocusManager.activeSlotFlow.value != null ||
            (config.focusModeEnabled && config.timeSlots.isNotEmpty())
}

private const val RESTORE_BLOCKED =
    "Restore is unavailable while a focus session or enabled focus schedule is active, " +
            "including rest and call pauses. Wait until the session ends and the schedule is " +
            "off through the normal focus controls."

@Composable
internal fun CloudRecoveryCard() {
    val state by AuthManager.cloudState.collectAsStateWithLifecycle()
    val user by UserManager.currentUser.collectAsStateWithLifecycle()
    val loggedIn by UserManager.isLoggedIn.collectAsStateWithLifecycle()
    val config by FocusManager.configurationFlow.collectAsStateWithLifecycle()
    val activeSlot by FocusManager.activeSlotFlow.collectAsStateWithLifecycle()
    val session by StatisticsManager.currentSession.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val restoreBlocked = session != null || activeSlot != null ||
            (config.focusModeEnabled && config.timeSlots.isNotEmpty())
    val accountMatches = loggedIn && user != null && state.accountEmail != null &&
            state.accountEmail == user?.email && state.revision != null

    // Never carry consent across accounts, sign-in revisions, or Activity recreation.
    var confirmation by remember(state.revision, state.accountEmail, user?.id, loggedIn) {
        mutableStateOf<CloudRequest?>(null)
    }
    var feedback by remember(state.revision, state.accountEmail) { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    val busy = state.busy || running

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) confirmation = null
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.status, busy, restoreBlocked) {
        val request = confirmation
        if (request != null && (state.status != request.status || busy ||
                    (request.action == CloudAction.RESTORE && restoreBlocked))
        ) {
            confirmation = null
        }
    }

    fun capture(action: CloudAction): CloudRequest? {
        val current = AuthManager.cloudState.value
        val currentUser = UserManager.currentUser.value ?: return null
        val revision = current.revision ?: return null
        val email = current.accountEmail ?: return null
        val request = CloudRequest(action, revision, email, currentUser.id, state.status)
        return request.takeIf {
            !running && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                    it.matches(current, currentUser, UserManager.isLoggedIn.value) &&
                    current.revision == state.revision && current.accountEmail == state.accountEmail &&
                    currentUser.id == user?.id
        }
    }

    fun execute(request: CloudRequest) {
        confirmation = null
        if (running) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
            !request.matches(AuthManager.cloudState.value, UserManager.currentUser.value, UserManager.isLoggedIn.value)
        ) {
            feedback = "Cloud status or account changed. Review the current status and try again."
            return
        }
        if (request.action == CloudAction.RESTORE && restoreBlockedNow()) {
            feedback = RESTORE_BLOCKED
            return
        }
        // Set synchronously, before launch, to reject rapid repeated taps.
        running = true
        feedback = null
        scope.launch {
            try {
                // Recheck live flows, not the lifecycle collector's last rendered snapshot.
                if (!request.matches(
                        AuthManager.cloudState.value,
                        UserManager.currentUser.value,
                        UserManager.isLoggedIn.value
                    )
                ) {
                    return@launch
                }
                val completed = when (request.action) {
                    CloudAction.RETRY -> AuthManager.retryCloudRecovery()
                    CloudAction.BACKUP -> AuthManager.syncUserDataToCloud()
                    CloudAction.RESTORE -> {
                        if (restoreBlockedNow()) {
                            feedback = RESTORE_BLOCKED
                            return@launch
                        }
                        AuthManager.restoreCloudBackup(request.revision)
                    }

                    CloudAction.IMPORT -> AuthManager.importDeviceData(request.revision)
                }
                val latest = AuthManager.cloudState.value
                // Recovery can return false to offer a choice; backup true can mean skipped.
                // Treat backend status as authoritative and never invent a success notification.
                if (!completed && request.matches(
                        latest,
                        UserManager.currentUser.value,
                        UserManager.isLoggedIn.value
                    )
                ) {
                    feedback = "The request did not complete. Review the cloud status and retry when online."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val latest = AuthManager.cloudState.value
                if (latest.revision == request.revision && latest.accountEmail == request.email) {
                    feedback = "The cloud request failed. Check your connection and retry the server check."
                }
            } finally {
                running = false
            }
        }
    }

    fun ask(action: CloudAction) {
        val request = capture(action)
        if (request == null) {
            feedback = "Cloud status or account changed. Review the current status and try again."
        } else if (action == CloudAction.RESTORE && restoreBlockedNow()) {
            feedback = RESTORE_BLOCKED
        } else {
            feedback = null
            confirmation = request
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Cloud backup & recovery", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            state.accountEmail?.let { Text("Google account: $it", style = MaterialTheme.typography.bodyMedium) }
            Text(
                text = when (state.status) {
                    CloudBackupStatus.SIGNED_OUT -> "Cloud backup is off"
                    CloudBackupStatus.CHECKING -> "Checking cloud backup"
                    CloudBackupStatus.READY -> "Cloud backup is ready"
                    CloudBackupStatus.RESTORE_AVAILABLE -> "Cloud backup found"
                    CloudBackupStatus.IMPORT_AVAILABLE -> "Device import needs your permission"
                    CloudBackupStatus.BLOCKED -> "Cloud recovery is blocked"
                    CloudBackupStatus.ERROR -> "Cloud request needs attention"
                    CloudBackupStatus.UPLOADING -> "Uploading cloud backup"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
            if (state.status == CloudBackupStatus.SIGNED_OUT) {
                Text("Guest and local-only use do not back up or recover data. Sign in with Google using the existing account controls to enable cloud recovery.")
            } else if (!accountMatches && !state.busy) {
                Text("No matching cloud account is connected. Sign in with Google using the existing account controls, then review this card.")
            }
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Please wait. Cloud actions are unavailable while a request is in progress.")
            }
            feedback?.let {
                Text(
                    it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (state.status == CloudBackupStatus.READY) {
                Text("Back up now uploads this device’s current profile, focus settings and statistics to this account, replacing its cloud backup.")
                Button(
                    onClick = { capture(CloudAction.BACKUP)?.let { execute(it) } },
                    enabled = accountMatches && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Back up now") }
            }
            if (state.status == CloudBackupStatus.RESTORE_AVAILABLE || state.status == CloudBackupStatus.READY) {
                if (restoreBlocked) Text(RESTORE_BLOCKED)
                OutlinedButton(
                    onClick = { ask(CloudAction.RESTORE) },
                    enabled = accountMatches && !busy && !restoreBlocked,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Restore cloud backup…") }
            }
            if (state.status == CloudBackupStatus.IMPORT_AVAILABLE) {
                OutlinedButton(
                    onClick = { ask(CloudAction.IMPORT) },
                    enabled = accountMatches && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Import this device’s data…") }
            }
            if (state.status != CloudBackupStatus.SIGNED_OUT) {
                OutlinedButton(
                    onClick = { capture(CloudAction.RETRY)?.let { execute(it) } },
                    enabled = accountMatches && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(
                        if (state.status == CloudBackupStatus.ERROR || state.status == CloudBackupStatus.BLOCKED)
                            "Retry server check" else "Check server again"
                    )
                }
            }
        }
    }

    confirmation?.let { request ->
        val restoring = request.action == CloudAction.RESTORE
        val valid = request.matches(state, user, loggedIn) && !busy && (!restoring || !restoreBlocked)
        if (valid) {
            AlertDialog(
                onDismissRequest = { confirmation = null },
                title = { Text(if (restoring) "Replace device data?" else "Assign and upload device data?") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Google account: ${request.email}")
                        Text(
                            if (restoring)
                                "This replaces this device’s profile, focus settings and statistics with this account’s cloud backup. It does not merge data. Local changes not in that backup will be lost, and restored focus settings may take effect. This cannot be undone here."
                            else
                                "This assigns this device’s existing profile, focus settings and statistics to this Google account and uploads them to the cloud. Only continue if this device’s data is yours and belongs in this account. Future backups will use this account. This cannot be undone here."
                        )
                        Text(
                            if (restoring)
                                "Restore is blocked during an active focus session or enabled schedule. This action does not stop or bypass focus protection."
                            else
                                "The server will be checked again. If a backup now exists, import will not overwrite it; review the new recovery status instead."
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { execute(request) }, enabled = valid,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(if (restoring) "Replace and restore" else "Assign and upload")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmation = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
