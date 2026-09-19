package com.example.mindvault.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mindvault.data.*
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
    "Restore is unavailable while a focus session or enabled focus schedule is active. " +
            "Wait until the session ends or turn off the schedule in focus controls."

// =========================================================================
// INTENT & RATIONALE:
// - Why this exists: CloudBackupDialog presents cloud sync status in a sleek,
//   non-intrusive modal styled with MindVault's signature purple dark theme.
// - Trade-off / Context: Dumping an unstyled, bulky card on the main Profile screen
//   broke app UX and intimidated users. Backup operates seamlessly in the background;
//   this modal serves as an accessible setting for manual sync and recovery.
// - Invariant: Confirmation must guard destructive restore; uploads stay paused if account diverges.
// =========================================================================
@Composable
internal fun CloudBackupDialog(onDismiss: () -> Unit) {
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
            feedback = "Cloud status or account changed. Try again."
            return
        }
        if (request.action == CloudAction.RESTORE && restoreBlockedNow()) {
            feedback = RESTORE_BLOCKED
            return
        }
        running = true
        feedback = null
        scope.launch {
            try {
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
                if (!completed && request.matches(latest, UserManager.currentUser.value, UserManager.isLoggedIn.value)) {
                    feedback = "Request did not complete. Check connection and retry."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                feedback = "Cloud request failed. Check internet connection."
            } finally {
                running = false
            }
        }
    }

    fun ask(action: CloudAction) {
        val request = capture(action)
        if (request == null) {
            feedback = "Cloud status or account changed. Try again."
        } else if (action == CloudAction.RESTORE && restoreBlockedNow()) {
            feedback = RESTORE_BLOCKED
        } else {
            feedback = null
            confirmation = request
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B2E)),
            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = "Cloud Sync",
                                tint = Color(0xFFD1B1FF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Cloud Backup & Sync",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            state.accountEmail?.let {
                                Text(
                                    text = it,
                                    fontSize = 12.sp,
                                    color = Color(0xFFD1B1FF)
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF12101F)),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Status",
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (state.status) {
                                    CloudBackupStatus.SIGNED_OUT -> "Cloud Backup is Off"
                                    CloudBackupStatus.CHECKING -> "Checking Cloud Sync…"
                                    CloudBackupStatus.READY -> "Automatic Cloud Sync Active"
                                    CloudBackupStatus.RESTORE_AVAILABLE -> "Cloud Backup Available"
                                    CloudBackupStatus.IMPORT_AVAILABLE -> "Ready to Back Up"
                                    CloudBackupStatus.BLOCKED -> "Sync Requires Attention"
                                    CloudBackupStatus.ERROR -> "Sync Attention Needed"
                                    CloudBackupStatus.UPLOADING -> "Syncing to Cloud…"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = if (state.status == CloudBackupStatus.SIGNED_OUT) {
                                "Sign in with Google to protect your streaks, goals, and focus statistics."
                            } else {
                                "Your focus sessions, streaks, weekly goals, and settings are automatically backed up whenever changes are made."
                            },
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            lineHeight = 18.sp
                        )

                        if (busy) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF8B5CF6),
                                trackColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
                            )
                        }

                        feedback?.let {
                            Text(
                                text = it,
                                fontSize = 12.sp,
                                color = Color(0xFFFF6B6B),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Action Buttons
                if (state.status == CloudBackupStatus.READY || state.status == CloudBackupStatus.IMPORT_AVAILABLE) {
                    Button(
                        onClick = {
                            if (state.status == CloudBackupStatus.IMPORT_AVAILABLE) {
                                ask(CloudAction.IMPORT)
                            } else {
                                capture(CloudAction.BACKUP)?.let { execute(it) }
                            }
                        },
                        enabled = accountMatches && !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync to Cloud Now", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (state.status == CloudBackupStatus.RESTORE_AVAILABLE || state.status == CloudBackupStatus.READY) {
                    OutlinedButton(
                        onClick = { ask(CloudAction.RESTORE) },
                        enabled = accountMatches && !busy && !restoreBlocked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD1B1FF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore from Cloud…", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (state.status == CloudBackupStatus.ERROR || state.status == CloudBackupStatus.BLOCKED) {
                    OutlinedButton(
                        onClick = { capture(CloudAction.RETRY)?.let { execute(it) } },
                        enabled = accountMatches && !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f))
                    ) {
                        Text("Retry Server Sync")
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }

    // Confirmation Alert Dialog
    confirmation?.let { request ->
        val restoring = request.action == CloudAction.RESTORE
        val valid = request.matches(state, user, loggedIn) && !busy && (!restoring || !restoreBlocked)
        if (valid) {
            AlertDialog(
                onDismissRequest = { confirmation = null },
                title = { Text(if (restoring) "Restore Cloud Backup?" else "Sync Device Data?") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Google Account: ${request.email}", fontWeight = FontWeight.Medium)
                        Text(
                            if (restoring)
                                "This will replace this device's profile, focus settings, and statistics with your account's cloud backup."
                            else
                                "This uploads your current statistics and settings to your Google account."
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { execute(request) },
                        enabled = valid
                    ) {
                        Text(if (restoring) "Restore" else "Sync", color = Color(0xFF8B5CF6))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmation = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/** Compatibility stub for any legacy references */
@Composable
internal fun CloudRecoveryCard() {
    // Deliberately empty: functionality moved to CloudBackupDialog within SettingsSection.
}
