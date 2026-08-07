package com.example.mindvault.ui

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.mindvault.ui.theme.MindVaultTheme
import com.example.mindvault.utils.AppManager
import com.example.mindvault.utils.AppCompatibilityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppCompatibilityActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatibilityManager.init(this)

        setContent {
            MindVaultTheme {
                AppCompatibilityScreen(
                    onBack = { finish() },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCompatibilityScreen(
    onBack: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(AppCompatibilityManager.isFeatureEnabled()) }
    var apps by remember { mutableStateOf(AppCompatibilityManager.getCompatibilityApps().toList()) }
    var showExplanationDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0A0E21),
            Color(0xFF1A1A2E)
        )
    )

    if (showExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showExplanationDialog = false },
            title = { Text("App Compatibility", color = Color.White) },
            text = {
                Text(
                    "When you open an app added to this list outside of Focus Mode, MindVault will temporarily disable its accessibility service so the app can function properly.\n\nWe need notification permission to show you a persistent notification while the service is paused, allowing you to easily resume it.",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExplanationDialog = false
                    AppCompatibilityManager.setFeatureEnabled(true)
                    isEnabled = true
                    onRequestNotificationPermission()
                }) {
                    Text("Enable & Allow", color = Color(0xFF6C63FF))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExplanationDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1A1A2E),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onAppSelected = { pkg ->
                AppCompatibilityManager.addApp(pkg)
                apps = AppCompatibilityManager.getCompatibilityApps().toList()
                showAppPicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Compatibility", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            if (isEnabled) {
                FloatingActionButton(
                    onClick = { showAppPicker = true },
                    containerColor = Color(0xFF6C63FF),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add App")
                }
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color(0xFF6C63FF),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Some apps (like banking apps) may not work when MindVault's Accessibility Service is active. Add those apps here — when you open them outside Focus Mode, MindVault will temporarily disable its service.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Master Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Enable App Compatibility",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showExplanationDialog = true
                            } else {
                                AppCompatibilityManager.setFeatureEnabled(false)
                                isEnabled = false
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF6C63FF),
                            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                AnimatedVisibility(
                    visible = isEnabled,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    if (apps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No compatibility apps added yet.\nTap + to add apps that don't work with Accessibility Service.",
                                color = Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(apps) { pkg ->
                                AppListItem(
                                    packageName = pkg,
                                    onDelete = {
                                        AppCompatibilityManager.removeApp(pkg)
                                        apps = AppCompatibilityManager.getCompatibilityApps().toList()
                                    }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(80.dp)) // FAB padding
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppListItem(
    packageName: String,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var appName by remember { mutableStateOf(packageName) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            appName = AppManager.getAppName(context, packageName)
            try {
                appIcon = context.packageManager.getApplicationIcon(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                // Ignore
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (appIcon != null) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        setImageDrawable(appIcon)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                },
                update = { it.setImageDrawable(appIcon) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.5f))
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = appName,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove App",
                tint = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            installedApps = packages.filter { 
                // Only show user-installed apps and exclude MindVault itself
                (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && 
                it.packageName != context.packageName
            }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            isLoading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A2E)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Select App",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF6C63FF))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(installedApps) { appInfo ->
                            AppPickerItem(
                                appInfo = appInfo,
                                onClick = { onAppSelected(appInfo.packageName) }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun AppPickerItem(
    appInfo: ApplicationInfo,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    val pm = context.packageManager
    
    LaunchedEffect(appInfo.packageName) {
        withContext(Dispatchers.IO) {
            try {
                appIcon = pm.getApplicationIcon(appInfo.packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                // Ignore
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (appIcon != null) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        setImageDrawable(appIcon)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                },
                update = { it.setImageDrawable(appIcon) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.5f))
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = pm.getApplicationLabel(appInfo).toString(),
            color = Color.White,
            fontSize = 16.sp,
            maxLines = 1
        )
    }
}
