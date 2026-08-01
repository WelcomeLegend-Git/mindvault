package com.example.mindvault.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mindvault.data.StatisticsManager
import com.example.mindvault.data.UserManager
import com.example.mindvault.ui.theme.MindVaultTheme
import androidx.compose.ui.platform.LocalContext
import com.example.mindvault.notifications.NotificationHelper
import com.example.mindvault.notifications.NotificationPermissionUtils

class AchievementsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MindVaultTheme {
                AchievementsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen() {
    val userStats by StatisticsManager.userStats.collectAsStateWithLifecycle()
    val achievements = generateAchievements(userStats)

    // Notification logic for newly unlocked achievements
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mindvault_notifications", android.content.Context.MODE_PRIVATE) }
    val achievementNotificationsEnabled = remember { prefs.getBoolean("achievement", true) }

    LaunchedEffect(achievements) {
        if (achievementNotificationsEnabled && NotificationPermissionUtils.hasPermission(context)) {
            val notified = prefs.getStringSet("notified_achievements", HashSet<String>())?.toMutableSet() ?: mutableSetOf()
            val newlyUnlocked = achievements.filter { it.isUnlocked && !notified.contains(it.id) }
            newlyUnlocked.forEach { achievement ->
                NotificationHelper.showAchievementNotification(
                    context,
                    achievement.title,
                    achievement.description
                )
                notified.add(achievement.id)
            }
            if (newlyUnlocked.isNotEmpty()) {
                prefs.edit().putStringSet("notified_achievements", notified).apply()
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { /* Handle back */ }
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Achievements",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Progress Overview
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    val unlockedCount = achievements.count { it.isUnlocked }
                    val totalCount = achievements.size
                    val progressPercent = (unlockedCount.toFloat() / totalCount * 100).toInt()
                    
                    Text(
                        text = "Overall Progress",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$unlockedCount / $totalCount achievements unlocked",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LinearProgressIndicator(
                        progress = unlockedCount.toFloat() / totalCount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFFFFD93D),
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$progressPercent% Complete",
                        color = Color(0xFFFFD93D),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Achievements List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(achievements) { achievement ->
                    AchievementCard(achievement)
                }
            }
        }
    }
}

@Composable
fun AchievementCard(achievement: Achievement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isUnlocked) {
                Color.White.copy(alpha = 0.08f)
            } else {
                Color.White.copy(alpha = 0.03f)
            }
        ),
        border = if (achievement.isUnlocked) {
            BorderStroke(1.dp, achievement.color.copy(alpha = 0.3f))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Achievement Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (achievement.isUnlocked) {
                            Brush.radialGradient(
                                colors = listOf(
                                    achievement.color.copy(alpha = 0.3f),
                                    achievement.color.copy(alpha = 0.1f)
                                )
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.1f),
                                    Color.White.copy(alpha = 0.05f)
                                )
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = achievement.icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (achievement.isUnlocked) {
                        achievement.color
                    } else {
                        Color.White.copy(alpha = 0.3f)
                    }
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Achievement Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = achievement.title,
                        color = if (achievement.isUnlocked) Color.White else Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (achievement.isUnlocked) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Unlocked",
                            modifier = Modifier.size(16.dp),
                            tint = achievement.color
                        )
                    }
                }
                
                Text(
                    text = achievement.description,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
                
                if (!achievement.isUnlocked && achievement.progress != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = achievement.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = achievement.color.copy(alpha = 0.7f),
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    Text(
                        text = achievement.progressText ?: "",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }
            
            // XP Reward
            if (achievement.isUnlocked) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "+${achievement.xpReward}",
                        color = Color(0xFFFFD93D),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "XP",
                        color = Color(0xFFFFD93D).copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val xpReward: Int,
    val isUnlocked: Boolean,
    val progress: Float? = null,
    val progressText: String? = null
)

fun generateAchievements(userStats: com.example.mindvault.data.UserStats?): List<Achievement> {
    val totalMinutes = userStats?.totalFocusMinutes ?: 0L
    val totalHours = userStats?.totalFocusHours ?: 0L
    val totalSessions = userStats?.totalSessions ?: 0
    val currentStreak = userStats?.currentStreak ?: 0
    val longestStreak = userStats?.longestStreak ?: 0
    val level = userStats?.level ?: 1
    
    // Additional variables for premium achievements
    val totalXP = (totalSessions * 10) + (totalHours * 50).toInt() + (level * 100) // Calculated XP for now
    // averageSessionLength is already in minutes from StatisticsManager
    val avgSessionMinutes = userStats?.averageSessionLength ?: 0L
    
    return listOf(
    // 🌟 FIRST STEPS CATEGORY (10 achievements)
    Achievement(
        "first_session",
        "First Steps",
        "Complete your first focus session",
        Icons.Default.PlayArrow,
        Color(0xFF00E676),
        50,
        totalSessions >= 1
    ),
    Achievement(
        "first_minute",
        "The Journey Begins",
        "Focus for your first minute",
        Icons.Default.Start,
        Color(0xFF4CAF50),
        25,
        totalMinutes >= 1  // 1 minute of focus
    ),
    Achievement(
        "first_five",
        "Building Momentum",
        "Complete 5 focus sessions",
        Icons.Default.Directions,
        Color(0xFF8BC34A),
        75,
        totalSessions >= 5,
        if (totalSessions < 5) totalSessions / 5f else null,
        if (totalSessions < 5) "$totalSessions / 5 sessions" else null
    ),
    Achievement(
        "first_hour",
        "Hour Power",
        "Focus for 1 hour total",
        Icons.Default.AccessTime,
        Color(0xFFFF9800),
        75,
        totalMinutes >= 60,
    ),
    Achievement(
        "newcomer",
        "Mind Newcomer",
        "Complete your first 3 sessions",
        Icons.Default.Person,
        Color(0xFF03DAC6),
        100,
        totalSessions >= 3
    ),
    Achievement(
        "first_level",
        "Level Up!",
        "Reach level 2",
        Icons.Default.TrendingUp,
        Color(0xFF2196F3),
        100,
        level >= 2
    ),
    Achievement(
        "explorer",
        "Mind Explorer",
        "Try different session lengths",
        Icons.Default.Explore,
        Color(0xFF9C27B0),
        125,
        totalSessions >= 3
    ),
    Achievement(
        "commitment",
        "First Commitment",
        "Use MindVault for 2 consecutive days",
        Icons.Default.Handshake,
        Color(0xFFE91E63),
        150,
        currentStreak >= 2
    ),
    Achievement(
        "foundation",
        "Building Foundation",
        "Complete 2 hours of focus time",
        Icons.Default.Architecture,
        Color(0xFF795548),
        175,
        totalMinutes >= 120,
    ),
    Achievement(
        "dedication",
        "Early Dedication",
        "Complete 7 focus sessions",
        Icons.Default.Favorite,
        Color(0xFFFF5722),
        125,
        totalSessions >= 7,
        if (totalSessions < 7) totalSessions / 7f else null,
        if (totalSessions < 7) "$totalSessions / 7 sessions" else null
    ),

    // 📊 SESSION MILESTONES (10 achievements)
    Achievement(
        "10_sessions",
        "Getting Started",
        "Complete 10 focus sessions",
        Icons.Default.Speed,
        Color(0xFF2196F3),
        200,
        totalSessions >= 10,
        if (totalSessions < 10) totalSessions / 10f else null,
        if (totalSessions < 10) "$totalSessions / 10 sessions" else null
    ),
    Achievement(
        "25_sessions",
        "Quarter Century",
        "Complete 25 focus sessions",
        Icons.Default.FastForward,
        Color(0xFF3F51B5),
        250,
        totalSessions >= 25,
        if (totalSessions < 25) totalSessions / 25f else null,
        if (totalSessions < 25) "$totalSessions / 25 sessions" else null
    ),
    Achievement(
        "50_sessions",
        "Half Century",
        "Complete 50 focus sessions",
        Icons.Default.Flag,
        Color(0xFF673AB7),
        350,
        totalSessions >= 50,
        if (totalSessions < 50) totalSessions / 50f else null,
        if (totalSessions < 50) "$totalSessions / 50 sessions" else null
    ),
    Achievement(
        "75_sessions",
        "Three Quarters",
        "Complete 75 focus sessions",
        Icons.Default.TrendingUp,
        Color(0xFF9C27B0),
        450,
        totalSessions >= 75,
        if (totalSessions < 75) totalSessions / 75f else null,
        if (totalSessions < 75) "$totalSessions / 75 sessions" else null
    ),
    Achievement(
        "centurion",
        "Centurion",
        "Complete 100 focus sessions",
        Icons.Default.EmojiEvents,
        Color(0xFF607D8B),
        500,
        totalSessions >= 100,
        if (totalSessions < 100) totalSessions / 100f else null,
        if (totalSessions < 100) "$totalSessions / 100 sessions" else null
    ),
    Achievement(
        "150_sessions",
        "Achiever",
        "Complete 150 focus sessions",
        Icons.Default.Star,
        Color(0xFFE91E63),
        650,
        totalSessions >= 150,
        if (totalSessions < 150) totalSessions / 150f else null,
        if (totalSessions < 150) "$totalSessions / 150 sessions" else null
    ),
    Achievement(
        "200_sessions",
        "Dedicated Mind",
        "Complete 200 focus sessions",
        Icons.Default.Favorite,
        Color(0xFF4CAF50),
        800,
        totalSessions >= 200,
        if (totalSessions < 200) totalSessions / 200f else null,
        if (totalSessions < 200) "$totalSessions / 200 sessions" else null
    ),
    Achievement(
        "300_sessions",
        "Elite Practitioner",
        "Complete 300 focus sessions",
        Icons.Default.Psychology,
        Color(0xFFFF9800),
        1000,
        totalSessions >= 300,
        if (totalSessions < 300) totalSessions / 300f else null,
        if (totalSessions < 300) "$totalSessions / 300 sessions" else null
    ),
    Achievement(
        "500_sessions",
        "Meditation Sage",
        "Complete 500 focus sessions",
        Icons.Default.AutoAwesome,
        Color(0xFF9C27B0),
        1500,
        totalSessions >= 500,
        if (totalSessions < 500) totalSessions / 500f else null,
        if (totalSessions < 500) "$totalSessions / 500 sessions" else null
    ),
    Achievement(
        "1000_sessions",
        "Mindfulness Legend",
        "Complete 1000 focus sessions",
        Icons.Default.Diamond,
        Color(0xFFFFD700),
        2500,
        totalSessions >= 1000,
        if (totalSessions < 1000) totalSessions / 1000f else null,
        if (totalSessions < 1000) "$totalSessions / 1000 sessions" else null
    ),

    // ⏰ TIME MASTERY (10 achievements)
    Achievement(
        "5_hours",
        "Time Apprentice",
        "Focus for 5 hours total",
        Icons.Default.Schedule,
        Color(0xFF00BCD4),
        300,
        totalMinutes >= 300,
        if (totalMinutes < 300) totalMinutes / 300f else null,
        if (totalMinutes < 300) "${totalMinutes / 60} / 5 hours" else null
    ),
    Achievement(
        "10_hours",
        "Dedication",
        "Focus for 10 hours total",
        Icons.Default.Schedule,
        Color(0xFF9C27B0),
        400,
        totalMinutes >= 600,
        if (totalMinutes < 600) totalMinutes / 600f else null,
        if (totalMinutes < 600) "${totalMinutes / 60} / 10 hours" else null
    ),
    Achievement(
        "25_hours",
        "Time Warrior",
        "Focus for 25 hours total",
        Icons.Default.AccessTime,
        Color(0xFF607D8B),
        600,
        totalMinutes >= 1500,
        if (totalMinutes < 1500) totalMinutes / 1500f else null,
        if (totalMinutes < 1500) "${totalMinutes / 60} / 25 hours" else null
    ),
    Achievement(
        "50_hours",
        "Time Master",
        "Focus for 50 hours total",
        Icons.Default.Timer,
        Color(0xFF795548),
        800,
        totalMinutes >= 3000,
        if (totalMinutes < 3000) totalMinutes / 3000f else null,
        if (totalMinutes < 3000) "${totalMinutes / 60} / 50 hours" else null
    ),
    Achievement(
        "master",
        "Focus Master",
        "Accumulate 100 hours of focus time",
        Icons.Default.Psychology,
        Color(0xFFB71C1C),
        1000,
        totalMinutes >= 6000,
        if (totalMinutes < 6000) totalMinutes / 6000f else null,
        if (totalMinutes < 6000) "${totalMinutes / 60} / 100 hours" else null
    ),
    Achievement(
        "200_hours",
        "Time Sage",
        "Focus for 200 hours total",
        Icons.Default.HourglassEmpty,
        Color(0xFF4CAF50),
        1500,
        totalMinutes >= 12000,
        if (totalMinutes < 12000) totalMinutes / 12000f else null,
        if (totalMinutes < 12000) "${totalMinutes / 60} / 200 hours" else null
    ),
    Achievement(
        "365_hours",
        "Year of Focus",
        "Focus for 365 hours total",
        Icons.Default.CalendarToday,
        Color(0xFFFF9800),
        2000,
        totalMinutes >= 21900,
        if (totalMinutes < 21900) totalMinutes / 21900f else null,
        if (totalMinutes < 21900) "${totalMinutes / 60} / 365 hours" else null
    ),
    Achievement(
        "500_hours",
        "Time Virtuoso",
        "Focus for 500 hours total",
        Icons.Default.AutoAwesome,
        Color(0xFF9C27B0),
        2500,
        totalMinutes >= 30000,
        if (totalMinutes < 30000) totalMinutes / 30000f else null,
        if (totalMinutes < 30000) "${totalMinutes / 60} / 500 hours" else null
    ),
    Achievement(
        "750_hours",
        "Temporal Master",
        "Focus for 750 hours total",
        Icons.Default.Diamond,
        Color(0xFF673AB7),
        3000,
        totalMinutes >= 45000,
        if (totalMinutes < 45000) totalMinutes / 45000f else null,
        if (totalMinutes < 45000) "${totalMinutes / 60} / 750 hours" else null
    ),
    Achievement(
        "1000_hours",
        "Millennium Mind",
        "Focus for 1000 hours total",
        Icons.Default.Stars,
        Color(0xFFFFD700),
        5000,
        totalMinutes >= 60000,
        if (totalMinutes < 60000) totalMinutes / 60000f else null,
        if (totalMinutes < 60000) "${totalMinutes / 60} / 1000 hours" else null
    ),

    // 🔥 STREAK LEGENDS (10 achievements)
    Achievement(
        "first_streak",
        "Consistency",
        "Maintain a 3-day focus streak",
        Icons.Default.LocalFireDepartment,
        Color(0xFFFF5722),
        150,
        longestStreak >= 3
    ),
    Achievement(
        "5_day_streak",
        "Steady Progress",
        "Maintain a 5-day focus streak",
        Icons.Default.Whatshot,
        Color(0xFFFF7043),
        200,
        longestStreak >= 5
    ),
    Achievement(
        "week_warrior",
        "Week Warrior",
        "Maintain a 7-day focus streak",
        Icons.Default.EmojiEvents,
        Color(0xFFFFD93D),
        300,
        longestStreak >= 7
    ),
    Achievement(
        "10_day_streak",
        "Streak Champion",
        "Maintain a 10-day focus streak",
        Icons.Default.LocalFireDepartment,
        Color(0xFFFF6F00),
        400,
        longestStreak >= 10
    ),
    Achievement(
        "2_week_streak",
        "Fortnight Focus",
        "Maintain a 14-day focus streak",
        Icons.Default.Fireplace,
        Color(0xFFE65100),
        500,
        longestStreak >= 14
    ),
    Achievement(
        "21_day_streak",
        "Habit Former",
        "Maintain a 21-day focus streak",
        Icons.Default.AutoAwesome,
        Color(0xFFBF360C),
        750,
        longestStreak >= 21
    ),
    Achievement(
        "month_master",
        "Month Master",
        "Maintain a 30-day focus streak",
        Icons.Default.CalendarMonth,
        Color(0xFF8BC34A),
        1000,
        longestStreak >= 30
    ),
    Achievement(
        "45_day_streak",
        "Streak Virtuoso",
        "Maintain a 45-day focus streak",
        Icons.Default.Stars,
        Color(0xFF4CAF50),
        1250,
        longestStreak >= 45
    ),
    Achievement(
        "60_day_streak",
        "Two Month Legend",
        "Maintain a 60-day focus streak",
        Icons.Default.Diamond,
        Color(0xFF2E7D32),
        1500,
        longestStreak >= 60
    ),
    Achievement(
        "100_day_streak",
        "Centurion Streak",
        "Maintain a 100-day focus streak",
        Icons.Default.EmojiEvents,
        Color(0xFFFFD700),
        2500,
        longestStreak >= 100
    ),

    // 🎯 LEVEL CHAMPIONS (10 achievements)
    Achievement(
        "level_3",
        "Rising Star",
        "Reach level 3",
        Icons.Default.Star,
        Color(0xFF2196F3),
        200,
        level >= 3
    ),
    Achievement(
        "level_5",
        "Skilled Practitioner",
        "Reach level 5",
        Icons.Default.Star,
        Color(0xFFE91E63),
        300,
        level >= 5
    ),
    Achievement(
        "level_10",
        "Double Digits",
        "Reach level 10",
        Icons.Default.TrendingUp,
        Color(0xFF9C27B0),
        500,
        level >= 10
    ),
    Achievement(
        "level_15",
        "Advanced Mind",
        "Reach level 15",
        Icons.Default.Psychology,
        Color(0xFF673AB7),
        750,
        level >= 15
    ),
    Achievement(
        "level_20",
        "Expert Level",
        "Reach level 20",
        Icons.Default.School,
        Color(0xFF3F51B5),
        1000,
        level >= 20
    ),
    Achievement(
        "level_25",
        "Quarter Century",
        "Reach level 25",
        Icons.Default.AutoAwesome,
        Color(0xFF2196F3),
        1250,
        level >= 25
    ),
    Achievement(
        "level_30",
        "Mindfulness Master",
        "Reach level 30",
        Icons.Default.EmojiEvents,
        Color(0xFF00BCD4),
        1500,
        level >= 30
    ),
    Achievement(
        "level_40",
        "Elite Mentor",
        "Reach level 40",
        Icons.Default.School, // Fixed: Added missing icon
        Color(0xFF4CAF50),
        2000,
        level >= 40
    ),
    Achievement(
        "level_50",
        "Half Century Hero",
        "Reach level 50",
        Icons.Default.Diamond,
        Color(0xFF8BC34A),
        2500,
        level >= 50
    ),
    Achievement(
        "level_100",
        "Centurion Master",
        "Reach level 100",
        Icons.Default.Stars,
        Color(0xFFFFD700),
        5000,
        level >= 100
    ),

    // ⏰ TIME WARRIORS (10 achievements)
    Achievement(
        "early_bird",
        "Early Bird",
        "Start a focus session before 8 AM",
        Icons.Default.WbSunny,
        Color(0xFFFFEB3B),
        100,
        false // This would need session time tracking
    ),
    Achievement(
        "dawn_warrior",
        "Dawn Warrior",
        "Start 5 sessions before 6 AM",
        Icons.Default.WbTwilight,
        Color(0xFFFF9800),
        200,
        false
    ),
    Achievement(
        "morning_master",
        "Morning Master",
        "Complete 10 morning sessions (6-10 AM)",
        Icons.Default.LightMode,
        Color(0xFFFFD54F),
        300,
        false
    ),
    Achievement(
        "midday_monk",
        "Midday Monk",
        "Focus during lunch hours (12-2 PM)",
        Icons.Default.WbSunny,
        Color(0xFFFF8F00),
        150,
        false
    ),
    Achievement(
        "afternoon_ace",
        "Afternoon Ace",
        "Complete 10 afternoon sessions (2-6 PM)",
        Icons.Default.Brightness6,
        Color(0xFFFF6F00),
        200,
        false
    ),
    Achievement(
        "evening_expert",
        "Evening Expert",
        "Complete 10 evening sessions (6-9 PM)",
        Icons.Default.Brightness4,
        Color(0xFFFF5722),
        200,
        false
    ),
    Achievement(
        "night_owl",
        "Night Owl",
        "Focus after 10 PM",
        Icons.Default.Bedtime,
        Color(0xFF3F51B5),
        100,
        false
    ),
    Achievement(
        "midnight_mystic",
        "Midnight Mystic",
        "Complete 5 sessions after midnight",
        Icons.Default.DarkMode,
        Color(0xFF1A237E),
        300,
        false
    ),
    Achievement(
        "weekend_warrior",
        "Weekend Warrior",
        "Focus on both Saturday and Sunday",
        Icons.Default.Weekend,
        Color(0xFF8BC34A),
        250,
        false
    ),
    Achievement(
        "all_day_master",
        "All Day Master",
        "Focus in morning, afternoon, and evening in one day",
        Icons.Default.AllInclusive,
        Color(0xFF9C27B0),
        400,
        false
    ),

    // 🎯 FOCUS MASTERS (10 achievements)
    Achievement(
        "marathon",
        "Marathon Runner",
        "Complete a 2-hour focus session",
        Icons.Default.Timer,
        Color(0xFF795548),
        400,
        false
    ),
    Achievement(
        "ultra_marathon",
        "Ultra Marathon",
        "Complete a 3-hour focus session",
        Icons.Default.TimerOff,
        Color(0xFF5D4037),
        600,
        false
    ),
    Achievement(
        "endurance_master",
        "Endurance Master",
        "Complete a 4-hour focus session",
        Icons.Default.FitnessCenter,
        Color(0xFF3E2723),
        800,
        false
    ),
    Achievement(
        "deep_focus",
        "Deep Focus",
        "Complete 10 sessions of 1+ hours",
        Icons.Default.Psychology,
        Color(0xFF1976D2),
        500,
        false
    ),
    Achievement(
        "concentration_king",
        "Concentration King",
        "Average 45+ minutes per session (20+ sessions)",
        Icons.Default.EmojiEvents,
        Color(0xFFD32F2F),
        600,
        avgSessionMinutes >= 45 && totalSessions >= 20
    ),
    Achievement(
        "mindful_minutes",
        "Mindful Minutes",
        "Complete 50 sessions of 30+ minutes",
        Icons.Default.AccessTime,
        Color(0xFF388E3C),
        400,
        false
    ),
    Achievement(
        "quality_over_quantity",
        "Quality Over Quantity",
        "Maintain 60+ min average (10+ sessions)",
        Icons.Default.Grade,
        Color(0xFFF57C00),
        750,
        avgSessionMinutes >= 60 && totalSessions >= 10
    ),
    Achievement(
        "laser_focus",
        "Laser Focus",
        "Complete 100 sessions of 30+ minutes",
        Icons.Default.CenterFocusStrong,
        Color(0xFF7B1FA2),
        1000,
        false
    ),
    Achievement(
        "zen_master",
        "Zen Master",
        "Complete 25 sessions of 90+ minutes",
        Icons.Default.SelfImprovement,
        Color(0xFF303F9F),
        1200,
        false
    ),
    Achievement(
        "flow_state",
        "Flow State Legend",
        "Complete 10 sessions of 2+ hours",
        Icons.Default.WaterDrop,
        Color(0xFF0277BD),
        1500,
        false
    ),

    // 📅 WEEKLY HEROES (10 achievements)
    Achievement(
        "weekly_starter",
        "Weekly Starter",
        "Focus every day for one week",
        Icons.Default.CalendarViewWeek,
        Color(0xFF4CAF50),
        300,
        currentStreak >= 7
    ),
    Achievement(
        "consistent_week",
        "Consistent Week",
        "Complete 2+ sessions every day for a week",
        Icons.Default.Repeat,
        Color(0xFF8BC34A),
        400,
        false
    ),
    Achievement(
        "power_week",
        "Power Week",
        "Complete 15+ sessions in one week",
        Icons.Default.Bolt,
        Color(0xFFFFEB3B),
        500,
        false
    ),
    Achievement(
        "dedication_week",
        "Dedication Week",
        "Focus 10+ hours in one week",
        Icons.Default.Schedule,
        Color(0xFFFF9800),
        600,
        false
    ),
    Achievement(
        "balance_master",
        "Balance Master",
        "Focus on all 7 days with 30+ min each",
        Icons.Default.Balance,
        Color(0xFF9C27B0),
        700,
        false
    ),
    Achievement(
        "weekly_warrior",
        "Weekly Warrior",
        "Complete 4 perfect weeks (7 days each)",
        Icons.Default.Shield,
        Color(0xFF673AB7),
        800,
        false
    ),
    Achievement(
        "sunday_sage",
        "Sunday Sage",
        "Focus every Sunday for 4 weeks",
        Icons.Default.Church,
        Color(0xFF3F51B5),
        400,
        false
    ),
    Achievement(
        "weekday_champion",
        "Weekday Champion",
        "Focus Mon-Fri for 2 consecutive weeks",
        Icons.Default.BusinessCenter,
        Color(0xFF2196F3),
        500,
        false
    ),
    Achievement(
        "weekend_master",
        "Weekend Master",
        "Focus every weekend for 8 weeks",
        Icons.Default.Weekend,
        Color(0xFF00BCD4),
        600,
        false
    ),
    Achievement(
        "perfect_month",
        "Perfect Month",
        "Complete 4 consecutive perfect weeks",
        Icons.Default.CalendarMonth,
        Color(0xFFFFD700),
        1200,
        false
    ),

    // 🏆 MONTHLY TITANS (10 achievements)
    Achievement(
        "monthly_starter",
        "Monthly Starter",
        "Focus for 20 days in a month",
        Icons.Default.CalendarToday,
        Color(0xFF4CAF50),
        500,
        false
    ),
    Achievement(
        "monthly_champion",
        "Monthly Champion",
        "Focus every day for 30 days",
        Icons.Default.EmojiEvents,
        Color(0xFF8BC34A),
        800,
        currentStreak >= 30
    ),
    Achievement(
        "hour_collector",
        "Hour Collector",
        "Focus 25+ hours in one month",
        Icons.Default.CollectionsBookmark,
        Color(0xFFFF9800),
        700,
        false
    ),
    Achievement(
        "session_machine",
        "Session Machine",
        "Complete 60+ sessions in one month",
        Icons.Default.Autorenew,
        Color(0xFF9C27B0),
        900,
        false
    ),
    Achievement(
        "consistency_king",
        "Consistency King",
        "Never miss more than 1 day in a month",
        Icons.Default.EmojiEvents,
        Color(0xFFFFD93D),
        1000,
        false
    ),
    Achievement(
        "monthly_marathon",
        "Monthly Marathon",
        "Focus 50+ hours in one month",
        Icons.Default.DirectionsRun,
        Color(0xFF795548),
        1200,
        false
    ),
    Achievement(
        "triple_digit_month",
        "Triple Digit Month",
        "Complete 100+ sessions in one month",
        Icons.Default.Looks3,
        Color(0xFF607D8B),
        1500,
        false
    ),
    Achievement(
        "seasonal_sage",
        "Seasonal Sage",
        "Focus every day for 3 consecutive months",
        Icons.Default.Nature,
        Color(0xFF4CAF50),
        2000,
        currentStreak >= 90
    ),
    Achievement(
        "monthly_legend",
        "Monthly Legend",
        "Achieve 6 perfect months in a year",
        Icons.Default.Stars,
        Color(0xFFFFD700),
        2500,
        false
    ),

    // 💎 ELITE LEGENDS (10 achievements)
    Achievement(
        "xp_millionaire",
        "XP Millionaire",
        "Earn 1,000,000 total XP",
        Icons.Default.AttachMoney,
        Color(0xFFFFD700),
        5000,
        totalXP >= 1000000,
        if (totalXP < 1000000) totalXP / 1000000f else null,
        if (totalXP < 1000000) "${totalXP / 1000} / 1000K XP" else null
    ),
    Achievement(
        "grand_master",
        "Grand Master",
        "Reach level 75",
        Icons.Default.Castle,
        Color(0xFF4A148C),
        3000,
        level >= 75
    ),
    Achievement(
        "legendary_streak",
        "Legendary Streak",
        "Maintain a 365-day focus streak",
        Icons.Default.LocalFireDepartment,
        Color(0xFFD32F2F),
        10000,
        longestStreak >= 365
    ),
    Achievement(
        "time_lord",
        "Time Lord",
        "Accumulate 2000+ hours of focus",
        Icons.Default.AccessTime,
        Color(0xFF1A237E),
        7500,
        totalMinutes >= 120000,
        if (totalMinutes < 120000) totalMinutes / 120000f else null,
        if (totalMinutes < 120000) "${totalMinutes / 60} / 2000 hours" else null
    ),
    Achievement(
        "session_deity",
        "Session Deity",
        "Complete 2000+ focus sessions",
        Icons.Default.AllInclusive,
        Color(0xFF6A1B9A),
        6000,
        totalSessions >= 2000,
        if (totalSessions < 2000) totalSessions / 2000f else null,
        if (totalSessions < 2000) "$totalSessions / 2000 sessions" else null
    ),
    Achievement(
        "mindfulness_avatar",
        "Mindfulness Avatar",
        "Achieve perfect stats: 1000+ sessions, 500+ hours, 100+ streak",
        Icons.Default.Psychology,
        Color(0xFF00695C),
        8000,
        totalSessions >= 1000 && totalMinutes >= 30000 && longestStreak >= 100
    ),
    Achievement(
        "zen_emperor",
        "Zen Emperor",
        "Master all aspects: Level 50+, 365+ streak, 1000+ hours",
        Icons.Default.SelfImprovement,
        Color(0xFF1B5E20),
        10000,
        level >= 50 && longestStreak >= 365 && totalMinutes >= 60000
    ),
    Achievement(
        "transcendent_mind",
        "Transcendent Mind",
        "Complete 50 ultra-marathon sessions (3+ hours each)",
        Icons.Default.AutoAwesome,
        Color(0xFF880E4F),
        12000,
        false
    ),
    Achievement(
        "eternal_focus",
        "Eternal Focus",
        "Maintain focus practice for 2+ years (730+ day streak)",
        Icons.Default.AllInclusive,
        Color(0xFF0D47A1),
        15000,
        longestStreak >= 730
    ),
    Achievement(
        "mindvault_legend",
        "MindVault Legend",
        "Ultimate achievement: 5000+ sessions, 2500+ hours, Level 100",
        Icons.Default.Diamond,
        Color(0xFFFFD700),
        25000,
        totalSessions >= 5000 && totalMinutes >= 150000 && level >= 100
    )
)
}