package com.example.mindvault.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindvault.data.DailyStats
import com.example.mindvault.data.WeeklyStats
import com.example.mindvault.data.UserStats
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

@Composable
fun ProductivityTrendsCard(weeklyStats: WeeklyStats?, userStats: UserStats?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Productivity Trends",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val weeklyFocus = weeklyStats?.totalFocusTime ?: 0L
            val avgDaily = weeklyStats?.averageDailyFocus ?: 0L
            val currentLevel = userStats?.level ?: 1
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCard("Weekly Focus", "${weeklyFocus}h ${weeklyFocus % 60}m", Color(0xFF6C63FF))
                MetricCard("Daily Average", "${avgDaily}h ${avgDaily % 60}m", Color(0xFFFF6B6B))
                MetricCard("Current Level", "$currentLevel", Color(0xFFFFD93D))
            }
        }
    }
}

@Composable
fun FocusHeatmapCard(userStats: UserStats?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "30-Day Focus Heatmap",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Generate real heatmap data from StatisticsManager
            val heatmapData = remember {
                (0..29).map { dayOffset ->
                    val date = LocalDate.now().minusDays(dayOffset.toLong())
                    val hasFocus = com.example.mindvault.data.StatisticsManager.hadFocusOn(date)
                    val intensity = if (hasFocus) {
                        // Get actual focus time for intensity calculation
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val prefs = context.getSharedPreferences("mindvault_stats", android.content.Context.MODE_PRIVATE)
                        val dateKey = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                        val focusMinutes = prefs.getLong("daily_focus_${dateKey}", 0L)
                        when {
                            focusMinutes >= 300 -> 4  // 5+ hours
                            focusMinutes >= 180 -> 3  // 3+ hours  
                            focusMinutes >= 120 -> 2  // 2+ hours
                            focusMinutes >= 30 -> 1   // 30+ minutes
                            else -> 0
                        }
                    } else 0
                    date to intensity
                }
            }
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(heatmapData.chunked(7)) { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { (date, intensity) ->
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        when (intensity) {
                                            0 -> Color.White.copy(alpha = 0.1f)
                                            1 -> Color(0xFF00E676).copy(alpha = 0.3f)
                                            2 -> Color(0xFF00E676).copy(alpha = 0.5f)
                                            3 -> Color(0xFF00E676).copy(alpha = 0.7f)
                                            else -> Color(0xFF00E676)
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyProgressCard(userStats: UserStats?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Monthly Progress",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val monthlyGoal = userStats?.monthlyGoal ?: 5000L
            val currentProgress = (userStats?.totalFocusHours ?: 0L) * 60 // Convert to minutes
            val progressPercent = (currentProgress.toFloat() / monthlyGoal * 100).coerceAtMost(100f)
            
            Column {
                Text(
                    text = "${currentProgress}m / ${monthlyGoal}m",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                LinearProgressIndicator(
                    progress = progressPercent / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF00E676),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${progressPercent.toInt()}% completed",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun PersonalBestsCard(userStats: UserStats?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFFFD93D),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Personal Bests",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val longestStreak = userStats?.longestStreak ?: 0
            val totalHours = userStats?.totalFocusHours ?: 0L
            val level = userStats?.level ?: 1
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AchievementItem("🔥", "Longest Streak", "${longestStreak}d")
                AchievementItem("⏰", "Total Hours", "${totalHours}h")
                AchievementItem("⭐", "Level Reached", "$level")
            }
        }
    }
}

@Composable
fun DataInsightsCard(dailyStats: DailyStats?, weeklyStats: WeeklyStats?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Insights,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Smart Insights",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val insights = generateSmartInsights(dailyStats, weeklyStats)
            insights.forEach { insight ->
                InsightRow(insight.icon, insight.title, insight.description)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun AllTimeStatsCard(userStats: UserStats?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    tint = Color(0xFFFFD93D),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "All-Time Statistics",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            val totalHours = userStats?.totalFocusHours ?: 0L
            val totalSessions = userStats?.totalSessions ?: 0
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${totalHours}h",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Total Focus Hours",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
                
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(60.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$totalSessions",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Total Sessions",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun AchievementItem(emoji: String, title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = emoji,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun InsightRow(icon: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = icon,
            fontSize = 20.sp,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

private data class SmartInsight(
    val icon: String,
    val title: String,
    val description: String
)

private fun generateSmartInsights(dailyStats: DailyStats?, weeklyStats: WeeklyStats?): List<SmartInsight> {
    val insights = mutableListOf<SmartInsight>()
    
    val dailyFocus = dailyStats?.totalFocusTime ?: 0L
    val weeklyFocus = weeklyStats?.totalFocusTime ?: 0L
    
    if (dailyFocus > 60) {
        insights.add(SmartInsight("🔥", "Great Focus Today!", "You've focused for over an hour today"))
    }
    
    if (weeklyFocus > 300) {
        insights.add(SmartInsight("💪", "Strong Week", "You're averaging ${weeklyFocus/7}min daily focus"))
    }
    
    val productivity = dailyStats?.productivityScore ?: 100f
    if (productivity > 85) {
        insights.add(SmartInsight("⭐", "High Productivity", "Your focus quality is excellent today"))
    }
    
    return insights.ifEmpty {
        listOf(SmartInsight("📊", "Getting Started", "Complete focus sessions to see insights"))
    }
}
