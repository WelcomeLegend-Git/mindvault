package com.example.mindvault.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mindvault.notifications.NotificationPermissionUtils
import com.example.mindvault.notifications.SocialScrollReminderScheduler
import com.example.mindvault.notifications.SocialScrollReminderSettings
import com.example.mindvault.receivers.MindVaultDeviceAdminReceiver
import com.example.mindvault.ui.theme.MindVaultTheme
import com.example.mindvault.utils.UsageAccessManager

class AdvancedProtectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MindVaultTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0F0F23),
                                    Color(0xFF1A1A2E),
                                    Color(0xFF16213E)
                                )
                            )
                        )
                ) {
                    AdvancedProtectionScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedProtectionScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("mindvault_prefs", Context.MODE_PRIVATE)

    // Device Admin state
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, MindVaultDeviceAdminReceiver::class.java)
    var deviceAdminEnabled by remember { mutableStateOf(dpm.isAdminActive(adminComponent)) }

    // Social Scroll Reminder state
    var socialScrollEnabled by remember {
        mutableStateOf(SocialScrollReminderSettings.isEnabled(context))
    }
    var usageAccessGranted by remember {
        mutableStateOf(UsageAccessManager.hasUsageAccess(context))
    }
    var pendingSocialScrollEnable by remember { mutableStateOf(false) }

    // Re-check states on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                deviceAdminEnabled = dpm.isAdminActive(adminComponent)
                socialScrollEnabled = SocialScrollReminderSettings.isEnabled(context)
                usageAccessGranted = UsageAccessManager.hasUsageAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Save advanced protection master state
    fun updateMasterState() {
        val isFullyActive = deviceAdminEnabled && socialScrollEnabled
        prefs.edit().putBoolean("advanced_protection_enabled", isFullyActive).apply()
    }

    // Launchers for permissions
    val usageAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        usageAccessGranted = UsageAccessManager.hasUsageAccess(context)
        if (pendingSocialScrollEnable && usageAccessGranted &&
            NotificationPermissionUtils.hasPermission(context)
        ) {
            socialScrollEnabled = true
            SocialScrollReminderSettings.setEnabled(context, true)
            SocialScrollReminderScheduler.schedule(context)
            updateMasterState()
        }
        pendingSocialScrollEnable = false
    }

    val socialScrollNotifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            usageAccessGranted = UsageAccessManager.hasUsageAccess(context)
            if (usageAccessGranted) {
                socialScrollEnabled = true
                SocialScrollReminderSettings.setEnabled(context, true)
                SocialScrollReminderScheduler.schedule(context)
                updateMasterState()
                pendingSocialScrollEnable = false
            } else {
                usageAccessLauncher.launch(UsageAccessManager.usageAccessSettingsIntent())
            }
        } else {
            pendingSocialScrollEnable = false
        }
    }

    fun handleDeviceAdminToggle(enabled: Boolean) {
        if (enabled) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Enable to protect MindVault from being uninstalled."
                )
            }
            context.startActivity(intent)
        } else {
            try {
                dpm.removeActiveAdmin(adminComponent)
                deviceAdminEnabled = false
                updateMasterState()
            } catch (_: Exception) {}
        }
    }

    fun handleSocialScrollToggle(enabled: Boolean) {
        if (!enabled) {
            pendingSocialScrollEnable = false
            socialScrollEnabled = false
            SocialScrollReminderSettings.setEnabled(context, false)
            SocialScrollReminderScheduler.cancel(context)
            updateMasterState()
            return
        }

        pendingSocialScrollEnable = true
        if (!NotificationPermissionUtils.hasPermission(context)) {
            socialScrollNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!UsageAccessManager.hasUsageAccess(context)) {
            usageAccessLauncher.launch(UsageAccessManager.usageAccessSettingsIntent())
        } else {
            usageAccessGranted = true
            socialScrollEnabled = true
            SocialScrollReminderSettings.setEnabled(context, true)
            SocialScrollReminderScheduler.schedule(context)
            updateMasterState()
            pendingSocialScrollEnable = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Protection", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            // Description
            Text(
                text = "Advanced Protection makes MindVault tamper-proof. " +
                        "When enabled, the app cannot be uninstalled and " +
                        "critical settings are guarded from being disabled.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(24.dp))

            // Master status indicator
            val allActive = deviceAdminEnabled && socialScrollEnabled
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (allActive) Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else Color(0xFFFFA500).copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (allActive) Icons.Default.Shield else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (allActive) Color(0xFF4CAF50) else Color(0xFFFFA500),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (allActive) "Fully Protected" else "Partially Protected",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (allActive) Color(0xFF4CAF50) else Color(0xFFFFA500)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Uninstall Protection Toggle
            ProtectionToggleCard(
                icon = Icons.Default.DeleteForever,
                title = "Uninstall Protection",
                subtitle = "Prevents MindVault from being uninstalled. Uses Device Administrator.",
                checked = deviceAdminEnabled,
                onCheckedChange = { handleDeviceAdminToggle(it) }
            )

            // Help guide for restricted settings (Android 13+)
            if (!deviceAdminEnabled) {
                Spacer(Modifier.height(8.dp))
                RestrictedSettingsHelpCard(context)
            }

            Spacer(Modifier.height(16.dp))

            // Scroll Interruptions Toggle
            ProtectionToggleCard(
                icon = Icons.Default.Timer,
                title = "Scroll Interruptions",
                subtitle = if (usageAccessGranted) {
                    "Shows a grounding reminder after ~15 min on social apps."
                } else {
                    "Requires Usage Access permission; only app name and time are checked."
                },
                checked = socialScrollEnabled,
                onCheckedChange = { handleSocialScrollToggle(it) }
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun RestrictedSettingsHelpCard(context: Context) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFA500).copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Clickable header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = Color(0xFFFFA500),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Having trouble enabling this?",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFA500),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Color(0xFFFFA500),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expandable guide
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(
                        text = "On Android 13 and above, apps installed from sources other than the Play Store have restricted permissions by default. You need to allow restricted settings first:",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        lineHeight = 18.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    // Steps
                    val steps = listOf(
                        "Open your device Settings app.",
                        "Go to Apps → See all apps → find MindVault.",
                        "Tap the ⋮ (three dots) in the top-right corner.",
                        "Tap \"Allow restricted settings\".",
                        "Verify with your PIN or fingerprint.",
                        "Come back here and turn on Uninstall Protection."
                    )

                    steps.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier.padding(bottom = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Step number bubble
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(
                                        color = Color(0xFF6C63FF).copy(alpha = 0.3f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB8B0FF)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = step,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                lineHeight = 17.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Quick action button
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF6C63FF)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6C63FF).copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Open MindVault App Info",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "Tap ⋮ (three dots) → Allow restricted settings",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProtectionToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.06f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = Color(0xFF6C63FF).copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color(0xFF6C63FF),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        lineHeight = 16.sp
                    )
                }

                Spacer(Modifier.width(12.dp))

                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF6C63FF),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )
            }
        }
    }
}
