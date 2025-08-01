package com.example.mindvault.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindvault.data.WeeklyStats
import com.example.mindvault.data.UserStats
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeeklyHeroStatsCard(weeklyStats: WeeklyStats?, userStats: UserStats?) {
    val totalFocusMins = weeklyStats?.totalFocusTime ?: 0L
    val averageFocus = weeklyStats?.averageDailyFocus ?: 0L
    val completedSessions = weeklyStats?.dailyStats?.sumOf { it.completedSessions } ?: 0

    // Determine star level from average focus (minutes)
    val (level, levelColor) = when {
        averageFocus >= 600 -> 5 to Color(0xFFB9F2FF) // Diamond
        averageFocus >= 480 -> 4 to Color(0xFFE5E4E2) // Platinum
        averageFocus >= 360 -> 3 to Color(0xFFFFD700) // Gold
        averageFocus >= 240 -> 2 to Color(0xFFC0C0C0) // Silver
        averageFocus >= 120 -> 1 to Color(0xFFCD7F32) // Bronze
        else -> 0 to Color.White.copy(alpha = 0.6f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = Color(0xFF6C63FF).copy(alpha = 0.3f),
                spotColor = Color(0xFF6C63FF).copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6C63FF),
                            Color(0xFF3F3D56),
                            Color(0xFF2F2E41)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "This Week's Focus",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatMinutes(totalFocusMins),
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Star level display
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = levelColor,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "$level",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            // Quick stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickStatItem(
                    title = "Sessions",
                    value = "$completedSessions",
                    icon = Icons.Default.Star // reuse star icon
                )
                QuickStatItem(
                    title = "Streak",
                    value = "${userStats?.currentStreak ?: 0}d",
                    icon = Icons.Default.LocalFireDepartment
                )
            }
            }
        }
    }
}

@Composable
fun WeeklyBarChartSection(weeklyStats: WeeklyStats?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Weekly Focus Time",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            val daily = weeklyStats?.dailyStats ?: emptyList()
            if (daily.isEmpty()) {
                Text(
                    text = "No focus data this week",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                return@Column
            }
            val maxMinutes = daily.maxOf { it.totalFocusTime }.coerceAtLeast(1L)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                daily.forEach { dayStats ->
                    val barHeightRatio = dayStats.totalFocusTime / maxMinutes.toFloat()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .fillMaxHeight(barHeightRatio)
                                .shadow(
                                    elevation = 6.dp,
                                    shape = RoundedCornerShape(6.dp),
                                    ambientColor = Color(0xFF6C63FF).copy(alpha = 0.4f)
                                )
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF8B7CF6),
                                            Color(0xFF6C63FF),
                                            Color(0xFF5B52D6)
                                        )
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = dayStats.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatMinutes(totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
