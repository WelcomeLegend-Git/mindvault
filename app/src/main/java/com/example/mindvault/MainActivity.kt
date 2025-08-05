package com.example.mindvault

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.draw.alpha
import com.example.mindvault.data.FocusManager
import com.example.mindvault.model.FocusType
import com.example.mindvault.ui.FocusModeSetupActivity
import com.example.mindvault.ui.StatisticsActivity
import com.example.mindvault.data.UserManager
import com.example.mindvault.ui.ProfileActivity
import com.example.mindvault.ui.LoginActivity
import com.example.mindvault.ui.AchievementsActivity
import com.example.mindvault.ui.HelpCenterActivity
import com.example.mindvault.data.AppPasswordManager
import com.example.mindvault.ui.LockScreenActivity

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mindvault.ui.theme.MindVaultTheme
import com.example.mindvault.utils.AppManager
import com.example.mindvault.utils.PermissionManager
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.graphics.graphicsLayer

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppPasswordManager.init(this)

        if (AppPasswordManager.isPasswordSet()) {
            startActivity(Intent(this, LockScreenActivity::class.java))
        }
        
        // Initialize managers
        FocusManager.init(this)
        // StatisticsManager.init(this) // Removed this line
        UserManager.init(this)
        // PermissionManager.init(this) // Removed this line as PermissionManager doesn't have an init method
        
        // Check if this is the first time the user is opening the app
        val prefs = getSharedPreferences("mindvault_prefs", Context.MODE_PRIVATE)
        val isFirstTime = prefs.getBoolean("is_first_time", true)
        

        
        // Show FocusManager initialization status
        Toast.makeText(this, "FocusManager Initialized: ${FocusManager.isInitialized()}", Toast.LENGTH_SHORT).show()

        setContent {
        // The Accessibility Service is managed by the system and will run when enabled.
        // No need to manually start any service here.

        MindVaultTheme {
            val context = LocalContext.current
        val user by UserManager.currentUser.collectAsStateWithLifecycle()
            val isLoggedIn by UserManager.isLoggedIn.collectAsStateWithLifecycle()

            // Enforce mandatory login
            val loginLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == ComponentActivity.RESULT_CANCELED) {
                    // If user cancels login, launch it again (mandatory)
                    finish() // Close app if login is canceled
                }
            }
            
            // Only show login if user is not logged in
            LaunchedEffect(isLoggedIn) {
                if (!isLoggedIn) {
                    loginLauncher.launch(Intent(this@MainActivity, LoginActivity::class.java))
                }
            }

            HomeScreen(onLaunchLogin = {
                loginLauncher.launch(Intent(context, LoginActivity::class.java))
            })
        }
    }
    }

        override fun onResume() {
        super.onResume()
        // Don't show lock screen on resume - only on app start
        // The PermissionStatusCard will handle re-checking permissions on resume.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onLaunchLogin: () -> Unit) {
    val permissionCardExpanded = remember { mutableStateOf(true) }
    val context = LocalContext.current
    val user by UserManager.currentUser.collectAsStateWithLifecycle()
    val isLoggedIn by UserManager.isLoggedIn.collectAsStateWithLifecycle()
    val allPermissionsGranted = remember {
        derivedStateOf {
            PermissionManager.hasUsageStatsPermission(context) &&
            PermissionManager.hasOverlayPermission(context) &&
            AppManager.hasNotificationListenerPermission(context) &&
            PermissionManager.isAccessibilityServiceEnabled(context)
        }
    }
    // Listen to PermissionStatusCard expansion state
    val expandedState = remember { mutableStateOf(true) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1a1a2e), Color(0xFF16213e))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HomeHeader(onLaunchLogin = onLaunchLogin, expandedState = expandedState)
            StatusCard()
            Spacer(modifier = Modifier.height(24.dp))
            PermissionStatusCard(expandedState)
            Spacer(modifier = Modifier.height(24.dp))
            FeatureCardsGrid(onLaunchLogin = onLaunchLogin)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun HomeHeader(onLaunchLogin: () -> Unit, expandedState: MutableState<Boolean>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 50.dp)
    ) {
        // Profile icon stays at the top
        val context = LocalContext.current
        val user by UserManager.currentUser.collectAsStateWithLifecycle()
        IconButton(
            onClick = {
                if (UserManager.isLoggedIn.value) {
                    context.startActivity(Intent(context, ProfileActivity::class.java))
                } else {
                    onLaunchLogin()
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            ) {
                if (user != null && !user!!.profilePicture.isNullOrBlank()) {
                    AsyncImage(
                        model = user!!.profilePicture,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // MindVault content moved down
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 30.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MindVault",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your Study Companion",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- REPLACE ClockView WITH TOGGLE ---
            // Remove the label and double the size of the toggle
            Spacer(modifier = Modifier.height(24.dp))
            val activeSlot by FocusManager.activeSlotFlow.collectAsStateWithLifecycle()
            val focusModeEnabled by remember { mutableStateOf(FocusManager.getFocusModeEnabled()) }
            var switchState by remember { mutableStateOf(focusModeEnabled) }
            val isToggleEnabled = activeSlot == null

            // Sync state with FocusManager changes
            LaunchedEffect(activeSlot) {
                switchState = FocusManager.getFocusModeEnabled()
            }

            Box(
                modifier = Modifier
                    .size(width = 220.dp, height = 110.dp),
                contentAlignment = Alignment.Center
            ) {
                Switch(
                    checked = switchState,
                    onCheckedChange = {
                        if (isToggleEnabled || it) {
                            switchState = it
                            FocusManager.setFocusModeEnabled(it)
                        }
                    },
                    enabled = isToggleEnabled || switchState,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF8F5CFF),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier
                        .graphicsLayer(
                            scaleX = 2.2f,
                            scaleY = 2.2f,
                            alpha = if (isToggleEnabled) 1f else 0.4f
                        )
                )
                // Removed the yellow statement
            }
            // --- END REPLACEMENT ---
        }
    }
}

@Composable
fun StatusCard() {
    val context = LocalContext.current
    val activeSlot by FocusManager.activeSlotFlow.collectAsStateWithLifecycle()
    val configuration by FocusManager.configurationFlow.collectAsStateWithLifecycle()
    
    // Check all permissions
    val hasUsageStats = remember { mutableStateOf(PermissionManager.hasUsageStatsPermission(context)) }
    val hasOverlay = remember { mutableStateOf(PermissionManager.hasOverlayPermission(context)) }
    val hasNotification = remember { mutableStateOf(AppManager.hasNotificationListenerPermission(context)) }
    val hasAccessibility = remember { mutableStateOf(PermissionManager.isAccessibilityServiceEnabled(context)) }
    
    val allPermissionsGranted = hasUsageStats.value && hasOverlay.value && hasNotification.value && hasAccessibility.value
    
    // Re-check permissions on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageStats.value = PermissionManager.hasUsageStatsPermission(context)
                hasOverlay.value = PermissionManager.hasOverlayPermission(context)
                hasNotification.value = AppManager.hasNotificationListenerPermission(context)
                hasAccessibility.value = PermissionManager.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val nextSlotInfo = remember(configuration) { FocusManager.getNextSlotInfo() }
    
    Card(
        modifier = Modifier
            .fillMaxWidth(0.95f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.05f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = if (activeSlot != null && allPermissionsGranted) Color.Red else if (allPermissionsGranted) Color.Green else Color(0xFFFFA500),
                                shape = CircleShape
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Current Status",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (!allPermissionsGranted) {
                    Text(
                        text = "Please allow all permissions to activate focus mode",
                        fontSize = 18.sp,
                        color = Color(0xFFFFA500),
                        fontWeight = FontWeight.Bold
                    )
                } else if (activeSlot != null) {
                    Text(
                        text = "Focus Mode: ${activeSlot?.type?.name?.replace("_", " ")} until ${activeSlot?.endTime}",
                        fontSize = 18.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Focus mode ready - no active session",
                        fontSize = 18.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = nextSlotInfo,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun PermissionStatusCard(expandedState: MutableState<Boolean>) {
    val context = LocalContext.current
    var hasUsageStats by remember { mutableStateOf(PermissionManager.hasUsageStatsPermission(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionManager.hasOverlayPermission(context)) }
    var hasNotification by remember { mutableStateOf(AppManager.hasNotificationListenerPermission(context)) }
    var hasAccessibility by remember { mutableStateOf(PermissionManager.isAccessibilityServiceEnabled(context)) }
    
    // Dialog states
    var showUsageStatsDialog by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }

    // Re-check permissions on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageStats = PermissionManager.hasUsageStatsPermission(context)
                hasOverlay = PermissionManager.hasOverlayPermission(context)
                hasNotification = AppManager.hasNotificationListenerPermission(context)
                hasAccessibility = PermissionManager.isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allPermissionsGranted = hasUsageStats && hasOverlay && hasNotification && hasAccessibility
    
    // Auto-collapse when all permissions are granted
    LaunchedEffect(allPermissionsGranted) {
        if (allPermissionsGranted) {
            delay(2000) // Wait 2 seconds before collapsing
            expandedState.value = false
        } else {
            expandedState.value = true
        }
    }
    
    val transition = updateTransition(expandedState.value, label = "Permission Card Transition")
    
    val cardHeight by transition.animateDp(
        transitionSpec = { tween(durationMillis = 500, easing = FastOutSlowInEasing) },
        label = "Card Height"
    ) { expanded ->
        if (expanded) 300.dp else 60.dp
    }
    
    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300) },
        label = "Content Alpha"
    ) { expanded ->
        if (expanded) 1f else 0f
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .height(cardHeight)
            .clickable { 
                if (allPermissionsGranted) {
                    expandedState.value = !expandedState.value
                }
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.05f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Permissions Status",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    if (allPermissionsGranted) {
                        Icon(
                            imageVector = if (expandedState.value) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expandedState.value) "Collapse" else "Expand",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                if (alpha > 0.0f) {
                    Column(
                        modifier = Modifier.alpha(alpha)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        PermissionRow("Usage Stats", hasUsageStats) { showUsageStatsDialog = true }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        PermissionRow("Overlay", hasOverlay) { showOverlayDialog = true }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        PermissionRow("Notification Access", hasNotification) { showNotificationDialog = true }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        PermissionRow("Accessibility Service", hasAccessibility) { showAccessibilityDialog = true }
                    }
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
            PermissionManager.requestUsageStatsPermission(context)
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
            PermissionManager.requestAccessibilityPermission(context)
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
            PermissionManager.requestOverlayPermission(context)
        }
    )
    
    PermissionExplanationDialog(
        showDialog = showNotificationDialog,
        onDismiss = { showNotificationDialog = false },
        title = "Notification Access Permission",
        explanation = "MindVault needs Notification Access to enhance your focus experience by managing notifications during sessions.\n\n" +
                "🔒 Privacy Guarantee:\n" +
                "• We don't read notification content\n" +
                "• Only used to minimize distractions\n" +
                "• No data is stored or transmitted\n\n" +
                "This helps create a distraction-free environment during your focus sessions.",
        onGrantClick = {
            showNotificationDialog = false
            AppManager.openNotificationListenerSettings(context)
        }
    )
}

@Composable
fun PermissionRow(name: String, isEnabled: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                if (!isEnabled) {
                    onRequest()
                } else {
                    Toast.makeText(context, "$name permission is already granted", Toast.LENGTH_SHORT).show()
                }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isEnabled) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = name,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = if (isEnabled) 0.9f else 0.7f),
            fontWeight = if (isEnabled) FontWeight.Bold else FontWeight.Normal
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (!isEnabled) {
            Text(
                text = "Tap to enable",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun FeatureCardsGrid(onLaunchLogin: () -> Unit) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // First Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                title = "Focus Mode",
                subtitle = "Setup & Manage",
                icon = Icons.Default.Lock,
                iconColor = Color(0xFF6C63FF),
                backgroundBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF6C63FF),
                        Color(0xFF5A54D9)
                    )
                )
            ) {
                context.startActivity(Intent(context, FocusModeSetupActivity::class.java))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            FeatureCard(
                modifier = Modifier.weight(1f),
                title = "Statistics",
                subtitle = "View Insights",
                icon = Icons.Default.BarChart,
                iconColor = Color(0xFF4CAF50),
                backgroundBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF4CAF50),
                        Color(0xFF388E3C)
                    )
                )
            ) {
                context.startActivity(Intent(context, StatisticsActivity::class.java))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Second Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                title = "Achievements",
                subtitle = "View Rewards",
                icon = Icons.Default.EmojiEvents,
                iconColor = Color(0xFFFFD93D),
                backgroundBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFF8F00),
                        Color(0xFFE65100)
                    )
                )
            ) {
                context.startActivity(Intent(context, AchievementsActivity::class.java))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            FeatureCard(
                modifier = Modifier.weight(1f),
                title = "Help Center",
                subtitle = "Get Support",
                icon = Icons.Default.ContactSupport,
                iconColor = Color(0xFF00BCD4),
                backgroundBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF00BCD4),
                        Color(0xFF0097A7)
                    )
                )
            ) {
                context.startActivity(Intent(context, HelpCenterActivity::class.java))
            }
        }
    }
}

@Composable
fun FeatureCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    isActive: Boolean = true,
    backgroundBrush: Brush? = null,
    onClick: () -> Unit
) {
    val titleAlpha = if (isActive) 1f else 0.8f
    val subtitleAlpha = if (isActive) 0.9f else 0.6f

    Card(
        modifier = modifier
            .padding(12.dp) // Increased padding to make cards smaller
            .aspectRatio(1f)
            .clickable(enabled = isActive, onClick = onClick),
        shape = RoundedCornerShape(24.dp), // Increased corner radius
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent // We use a gradient background
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 12.dp else 4.dp,
            pressedElevation = if (isActive) 6.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = backgroundBrush ?: if (isActive) {
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            )
                        )
                    },
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = if (isActive) 1.5.dp else 0.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.5f
                            ), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 3D Icon with shadow effect
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.8f),
                                    Color.White.copy(alpha = 0.3f)
                                )
                            ),
                            shape = CircleShape
                        )
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier
                            .size(40.dp)
                            .align(Alignment.Center)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = titleAlpha),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = subtitleAlpha),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
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
