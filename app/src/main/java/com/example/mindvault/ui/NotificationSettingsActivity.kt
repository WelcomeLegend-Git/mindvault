package com.example.mindvault.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindvault.ui.theme.MindVaultTheme
import java.time.LocalDate
import kotlin.random.Random
// New imports
import android.content.Context
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.mindvault.notifications.NotificationPermissionUtils
import com.example.mindvault.notifications.MotivationScheduler
import com.example.mindvault.notifications.FocusReminderScheduler
import com.example.mindvault.notifications.SocialScrollReminderScheduler
import com.example.mindvault.notifications.SocialScrollReminderSettings
import com.example.mindvault.utils.UsageAccessManager

class NotificationSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MindVaultTheme {
                // Main container with the gradient background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF100F1C), Color(0xFF2C2A4A), Color(0xFF4A4678))
                            )
                        )
                ) {
                    NotificationSettingsScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("mindvault_notifications", Context.MODE_PRIVATE)

    var achievementEnabled by remember { mutableStateOf(prefs.getBoolean("achievement", true)) }
    var focusEnabled by remember { mutableStateOf(prefs.getBoolean("focus", true)) }
    var motivationEnabled by remember { mutableStateOf(prefs.getBoolean("motivation", true)) }
    var dailyMotivationEnabled by remember { mutableStateOf(prefs.getBoolean("daily_motivation", false)) }
    var socialScrollRemindersEnabled by remember {
        mutableStateOf(SocialScrollReminderSettings.isEnabled(context))
    }
    var usageAccessGranted by remember {
        mutableStateOf(UsageAccessManager.hasUsageAccess(context))
    }
    var pendingSocialScrollEnable by remember { mutableStateOf(false) }

    fun savePref(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
    fun enableSocialScrollReminders() {
        socialScrollRemindersEnabled = true
        SocialScrollReminderSettings.setEnabled(context, true)
        SocialScrollReminderScheduler.schedule(context)
    }



    var pendingToggle by remember { mutableStateOf<String?>(null) }
    var pendingDailyMotivation by remember { mutableStateOf(false) }

    val genericNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when (pendingToggle) {
                "achievement" -> {
                    achievementEnabled = true
                    savePref("achievement", true)
                }
                "focus" -> {
                    focusEnabled = true
                    savePref("focus", true)
                    FocusReminderScheduler.scheduleFocusReminders(context)
                }
                "motivation" -> {
                    motivationEnabled = true
                    savePref("motivation", true)
                }
            }
        }
        pendingToggle = null
    }

    val usageAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        usageAccessGranted = UsageAccessManager.hasUsageAccess(context)
        if (pendingSocialScrollEnable && usageAccessGranted &&
            NotificationPermissionUtils.hasPermission(context)
        ) {
            enableSocialScrollReminders()
        }
        pendingSocialScrollEnable = false
    }

    val socialScrollNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            usageAccessGranted = UsageAccessManager.hasUsageAccess(context)
            if (usageAccessGranted) {
                enableSocialScrollReminders()
                pendingSocialScrollEnable = false
            } else {
                usageAccessLauncher.launch(UsageAccessManager.usageAccessSettingsIntent())
            }
        } else {
            pendingSocialScrollEnable = false
        }
    }

    val dailyMotivationUsageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        usageAccessGranted = UsageAccessManager.hasUsageAccess(context)
        if (pendingDailyMotivation && usageAccessGranted &&
            NotificationPermissionUtils.hasPermission(context)
        ) {
            dailyMotivationEnabled = true
            savePref("daily_motivation", true)
            MotivationScheduler.scheduleDailyMotivation(context)
        }
        pendingDailyMotivation = false
    }

    val dailyMotivationNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            usageAccessGranted = UsageAccessManager.hasUsageAccess(context)
            if (usageAccessGranted) {
                dailyMotivationEnabled = true
                savePref("daily_motivation", true)
                MotivationScheduler.scheduleDailyMotivation(context)
                pendingDailyMotivation = false
            } else {
                dailyMotivationUsageLauncher.launch(UsageAccessManager.usageAccessSettingsIntent())
            }
        } else {
            pendingDailyMotivation = false
        }
    }

    val motivationQuotes = remember { getMotivationQuotes() }
    val today = remember { LocalDate.now().toEpochDay() }
    val userSeed = remember { Random.nextInt(0, 100000) }
    val quoteIndex = ((today + userSeed) % motivationQuotes.size).toInt()
    val todayQuote = motivationQuotes[quoteIndex]

    // region toggle handlers
    fun handleAchievementChange(enabled: Boolean) {
        if (!enabled) {
            achievementEnabled = false
            savePref("achievement", false)
            return
        }
        if (NotificationPermissionUtils.hasPermission(context)) {
            achievementEnabled = true
            savePref("achievement", true)
        } else {
            pendingToggle = "achievement"
            genericNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun handleFocusChange(enabled: Boolean) {
        if (!enabled) {
            focusEnabled = false
            savePref("focus", false)
            FocusReminderScheduler.cancelFocusReminders(context)
            return
        }
        if (NotificationPermissionUtils.hasPermission(context)) {
            focusEnabled = true
            savePref("focus", true)
            FocusReminderScheduler.scheduleFocusReminders(context)
        } else {
            pendingToggle = "focus"
            genericNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun handleMotivationChange(enabled: Boolean) {
        if (!enabled) {
            motivationEnabled = false
            savePref("motivation", false)
            return
        }
        if (NotificationPermissionUtils.hasPermission(context)) {
            motivationEnabled = true
            savePref("motivation", true)
        } else {
            pendingToggle = "motivation"
            genericNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun handleSocialScrollReminderChange(enabled: Boolean) {
        if (!enabled) {
            pendingSocialScrollEnable = false
            socialScrollRemindersEnabled = false
            SocialScrollReminderSettings.setEnabled(context, false)
            SocialScrollReminderScheduler.cancel(context)
            return
        }

        pendingSocialScrollEnable = true
        if (!NotificationPermissionUtils.hasPermission(context)) {
            socialScrollNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!UsageAccessManager.hasUsageAccess(context)) {
            usageAccessLauncher.launch(UsageAccessManager.usageAccessSettingsIntent())
        } else {
            usageAccessGranted = true
            enableSocialScrollReminders()
            pendingSocialScrollEnable = false
        }
    }

    fun handleDailyMotivationChange(enabled: Boolean) {
        if (!enabled) {
            pendingDailyMotivation = false
            dailyMotivationEnabled = false
            savePref("daily_motivation", false)
            MotivationScheduler.cancelDailyMotivation(context)
            return
        }

        pendingDailyMotivation = true
        if (!NotificationPermissionUtils.hasPermission(context)) {
            dailyMotivationNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!UsageAccessManager.hasUsageAccess(context)) {
            dailyMotivationUsageLauncher.launch(UsageAccessManager.usageAccessSettingsIntent())
        } else {
            usageAccessGranted = true
            dailyMotivationEnabled = true
            savePref("daily_motivation", true)
            MotivationScheduler.scheduleDailyMotivation(context)
            pendingDailyMotivation = false
        }
    }
    // endregion

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = "Notifications Icon",
                    tint = Color(0xFFD1B1FF),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Notification Hub",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Customize your app alerts.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(32.dp))
            }

            item {
                PremiumNotificationToggle(
                    icon = Icons.Filled.Star,
                    title = "Achievements",
                    subtitle = "When you unlock new milestones.",
                    checked = achievementEnabled,
                    onCheckedChange = { handleAchievementChange(it) }
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                PremiumNotificationToggle(
                    icon = Icons.Filled.Timer,
                    title = "Focus Reminders",
                    subtitle = "To help you stay on track.",
                    checked = focusEnabled,
                    onCheckedChange = { handleFocusChange(it) }
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                PremiumNotificationToggle(
                    icon = Icons.Filled.Lightbulb,
                    title = "General Motivation",
                    subtitle = "Occasional motivational boosts.",
                    checked = motivationEnabled,
                    onCheckedChange = { handleMotivationChange(it) }
                )
                Spacer(Modifier.height(16.dp))
            }


            item {
                PremiumNotificationToggle(
                    icon = Icons.Filled.WorkspacePremium,
                    title = "Daily Motivation",
                    subtitle = "A unique quote every single day.",
                    checked = dailyMotivationEnabled,
                    onCheckedChange = { handleDailyMotivationChange(it) },
                    isPremium = true
                )
                Spacer(Modifier.height(24.dp))
            }

            if (dailyMotivationEnabled) {
                item {
                    PremiumMotivationCard(quote = todayQuote)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun PremiumNotificationToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isPremium: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color(0xFFD1B1FF),
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (isPremium) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFFF9D423), Color(0xFFFF4E50))
                                )
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "PREMIUM",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF8F5CFF),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            ),
            thumbContent = {
                if (checked) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                        tint = Color(0xFF8F5CFF)
                    )
                }
            }
        )
    }
}

@Composable
fun PremiumMotivationCard(quote: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF8F5CFF).copy(alpha = 0.2f),
                        Color.Transparent
                    )
                )
            )
            .padding(2.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "✨ Today's Motivation ✨",
            color = Color(0xFFD1B1FF),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "\"$quote\"",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

fun getMotivationQuotes(): List<String> {
    return listOf(
        "Push yourself, because no one else is going to do it for you.",
        "Success doesn’t just find you. You have to go out and get it.",
        "Great things never come from comfort zones.",
        "Dream it. Wish it. Do it.",
        "Stay focused and never give up.",
        "Don’t stop when you’re tired. Stop when you’re done.",
        "Wake up with determination. Go to bed with satisfaction.",
        "Little things make big days.",
        "Don’t wait for opportunity. Create it.",
        "Sometimes we’re tested not to show our weaknesses, but to discover our strengths."
    )
}