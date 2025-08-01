package com.example.mindvault.ui

import com.example.mindvault.MainActivity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindvault.ui.theme.MindVaultTheme
import com.example.mindvault.model.FocusType
import com.example.mindvault.utils.AppManager

class PermissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MindVaultTheme {
                PermissionScreen(
                    onGrantUsageStatsClick = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        startActivity(intent)
                    },
                    onGrantOverlayClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"))
                        startActivity(intent)
                    },
                    onGrantAccessibilityClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                    },
                    onAllPermissionsGranted = {
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Check if all permissions are granted and navigate back if they are
        if (AppManager.hasAllRequiredPermissions(this)) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}

@Composable
fun PermissionScreen(
    onGrantUsageStatsClick: () -> Unit,
    onGrantOverlayClick: () -> Unit,
    onGrantAccessibilityClick: () -> Unit,
    onAllPermissionsGranted: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasUsageStats by remember { mutableStateOf(AppManager.hasUsageStatsPermission(context)) }
    var hasOverlay by remember { mutableStateOf(AppManager.hasSystemAlertWindowPermission(context)) }
    var hasAccessibility by remember { mutableStateOf(AppManager.hasAccessibilityServicePermission(context)) }
    
    // Dialog states
    var showUsageStatsDialog by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            val newUsageStats = AppManager.hasUsageStatsPermission(context)
            val newOverlay = AppManager.hasSystemAlertWindowPermission(context)
            val newAccessibility = AppManager.hasAccessibilityServicePermission(context)
            
            if (newUsageStats != hasUsageStats || newOverlay != hasOverlay || newAccessibility != hasAccessibility) {
                hasUsageStats = newUsageStats
                hasOverlay = newOverlay
                hasAccessibility = newAccessibility
                
                if (hasUsageStats && hasOverlay && hasAccessibility) {
                    onAllPermissionsGranted()
                    break
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Permission Icon",
                    modifier = Modifier.size(64.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Permissions Required",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "MindVault needs three permissions to block apps during focus sessions:",
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                // Usage Stats Permission
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasUsageStats) "✓" else "○",
                        color = if (hasUsageStats) Color.Green else Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier.width(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Usage Access",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Monitor app usage",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { showUsageStatsDialog = true },
                        enabled = !hasUsageStats,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Grant")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Accessibility Service Permission
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasAccessibility) "✓" else "○",
                        color = if (hasAccessibility) Color.Green else Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier.width(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Accessibility Service",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Real-time app blocking",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { showAccessibilityDialog = true },
                        enabled = !hasAccessibility,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Grant")
                    }
                }
                
                // Debug info for accessibility service
                if (!hasAccessibility) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Debug: ${AppManager.getAccessibilityServiceStatus(context)}",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Overlay Permission
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasOverlay) "✓" else "○",
                        color = if (hasOverlay) Color.Green else Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier.width(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Display Over Apps",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Show blocking overlay",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { showOverlayDialog = true },
                        enabled = !hasOverlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Grant")
                    }
                }
                
                if (hasUsageStats && hasOverlay && hasAccessibility) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "All permissions granted! ✓",
                        color = Color.Green,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    
    // Permission Explanation Dialogs
    PermissionExplanationDialog(
        showDialog = showUsageStatsDialog,
        onDismiss = { showUsageStatsDialog = false },
        title = "Usage Access Permission",
        explanation = "MindVault needs Usage Access permission to monitor which apps you're using during focus sessions.\n\n" +
                "🔒 Privacy Guarantee:\n" +
                "• We only check if blocked apps are being used\n" +
                "• No personal data is collected or stored\n" +
                "• All data stays on your device\n\n" +
                "This permission helps make focus mode unbreakable by detecting when you try to open distracting apps.",
        onGrantClick = {
            showUsageStatsDialog = false
            onGrantUsageStatsClick()
        }
    )
    
    PermissionExplanationDialog(
        showDialog = showAccessibilityDialog,
        onDismiss = { showAccessibilityDialog = false },
        title = "Accessibility Service Permission",
        explanation = "MindVault uses Accessibility Service to provide real-time app blocking during focus sessions.\n\n" +
                "🔒 Privacy Guarantee:\n" +
                "• We only monitor app launches, not content\n" +
                "• No personal information is accessed or stored\n" +
                "• Service only runs during active focus sessions\n\n" +
                "This makes our focus mode truly unbreakable - when you're in a session, distracting apps are immediately blocked.",
        onGrantClick = {
            showAccessibilityDialog = false
            onGrantAccessibilityClick()
        }
    )
    
    PermissionExplanationDialog(
        showDialog = showOverlayDialog,
        onDismiss = { showOverlayDialog = false },
        title = "Display Over Apps Permission",
        explanation = "MindVault needs Display Over Apps permission to show blocking overlays when you try to open restricted apps.\n\n" +
                "🔒 Privacy Guarantee:\n" +
                "• Only shows blocking messages, no data collection\n" +
                "• Overlay only appears during focus sessions\n" +
                "• No access to other app content\n\n" +
                "This creates a visual barrier that prevents you from using distracting apps during focus time.",
        onGrantClick = {
            showOverlayDialog = false
            onGrantOverlayClick()
        }
    )
}

@Composable
fun PermissionExplanationDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    title: String,
    explanation: String,
    onGrantClick: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = explanation,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8F5CFF)
                    )
                ) {
                    Text("Grant Permission", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF2A2A3E),
            shape = RoundedCornerShape(16.dp)
        )
    }
}
