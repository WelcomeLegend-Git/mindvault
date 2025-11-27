package com.example.mindvault.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val completedSessions = weeklyStats?.dailyStats?.sumOf { it.completedSessions } ?: 0

    // Determine star level from weekly goal progress instead of daily average
    val weeklyProgress = weeklyStats?.weeklyGoalProgress ?: 0f
    val (level, levelColor) = when {
        weeklyProgress >= 90f -> 5 to Color(0xFFB9F2FF) // Diamond
        weeklyProgress >= 75f -> 4 to Color(0xFFE5E4E2) // Platinum
        weeklyProgress >= 50f -> 3 to Color(0xFFFFD700) // Gold
        weeklyProgress >= 25f -> 2 to Color(0xFFC0C0C0) // Silver
        weeklyProgress >= 10f -> 1 to Color(0xFFCD7F32) // Bronze
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
            val daily = weeklyStats?.dailyStats ?: emptyList()
            val selectedDayInfo = remember(daily) { mutableStateOf<String?>(null) }

            selectedDayInfo.value?.let { info ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = info,
                    color = Color(0xFFFFB74D),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

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
                    .height(160.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                daily.forEach { dayStats ->
                    val barHeightRatio = dayStats.totalFocusTime / maxMinutes.toFloat()
                    val dayName = dayStats.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                selectedDayInfo.value = "$dayName: ${formatMinutes(dayStats.totalFocusTime)}"
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                // Reserve bottom space for label: bars scale within 120dp
                                .height((120.dp * barHeightRatio).coerceAtLeast(4.dp))
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
                        Spacer(modifier = Modifier.height(8.dp))
                        // Use two-letter labels to avoid both Tuesday/Thursday mapping to the same "T"
                        Text(
                            text = dayStats.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2),
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
