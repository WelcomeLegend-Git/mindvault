package com.example.mindvault.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindvault.data.ScreenTimeTracker
import com.example.mindvault.data.StatisticsManager
import kotlin.math.roundToInt

// ─── Screen Time Hero Card ───────────────────────────────────────────────────
@Composable
fun ScreenTimeCard(summary: ScreenTimeTracker.ScreenTimeSummary) {
    val totalMinutes = summary.totalScreenTimeMinutes
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF1A1A2E))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.05f))
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = Color(0xFF66BB6A),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Screen Time Today",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // Unlock count
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${summary.unlockCount} unlocks",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Big screen time number
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (hours > 0) {
                        Text(
                            "$hours",
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "h ",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Text(
                        "$mins",
                        color = Color.White,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "m",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Social vs Other split bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ScreenTimeStatChip(
                        label = "Social Media",
                        value = formatMinutes(summary.socialMediaMinutes),
                        color = Color(0xFFFF5252)
                    )
                    ScreenTimeStatChip(
                        label = "Other Apps",
                        value = formatMinutes(totalMinutes - summary.socialMediaMinutes),
                        color = Color(0xFF66BB6A)
                    )
                    ScreenTimeStatChip(
                        label = "Focus Ratio",
                        value = "${(summary.focusVsScreenRatio * 100).roundToInt()}%",
                        color = Color(0xFF42A5F5)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenTimeStatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

// ─── App Usage Breakdown Card ────────────────────────────────────────────────
@Composable
fun AppUsageBreakdownCard(topApps: List<ScreenTimeTracker.AppUsageInfo>) {
    if (topApps.isEmpty()) return

    val maxUsage = topApps.maxOfOrNull { it.usageTimeMillis } ?: 1L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Apps,
                    contentDescription = null,
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "App Usage Today",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            topApps.take(6).forEach { app ->
                AppUsageRow(app, maxUsage)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun AppUsageRow(app: ScreenTimeTracker.AppUsageInfo, maxUsage: Long) {
    val minutes = app.usageTimeMillis / 60000L
    val progress = (app.usageTimeMillis.toFloat() / maxUsage.toFloat()).coerceIn(0f, 1f)
    val barColor = when (app.category) {
        ScreenTimeTracker.AppCategory.SOCIAL -> Color(0xFFFF5252)
        ScreenTimeTracker.AppCategory.ENTERTAINMENT -> Color(0xFFFFB74D)
        ScreenTimeTracker.AppCategory.PRODUCTIVE -> Color(0xFF66BB6A)
        ScreenTimeTracker.AppCategory.NEUTRAL -> Color(0xFF90A4AE)
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(barColor, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    app.appName,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                formatMinutes(minutes),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(barColor, barColor.copy(alpha = 0.6f))
                        ),
                        shape = RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

// ─── Focus vs Phone Usage Card ───────────────────────────────────────────────
@Composable
fun FocusVsPhoneCard(
    focusMinutes: Long,
    screenTimeMinutes: Long,
    distractionsBlocked: Int,
    productivityScore: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = Color(0xFF42A5F5),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Productivity Score",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Circular score
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(90.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { productivityScore / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = when {
                            productivityScore >= 80 -> Color(0xFF66BB6A)
                            productivityScore >= 50 -> Color(0xFFFFD54F)
                            else -> Color(0xFFFF5252)
                        },
                        trackColor = Color.White.copy(alpha = 0.1f),
                        strokeWidth = 8.dp,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${productivityScore.roundToInt()}",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "/100",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Breakdown metrics
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricRow(
                        label = "Focus Time",
                        value = formatMinutes(focusMinutes),
                        icon = Icons.Default.CenterFocusStrong,
                        color = Color(0xFF66BB6A)
                    )
                    MetricRow(
                        label = "Screen Time",
                        value = formatMinutes(screenTimeMinutes),
                        icon = Icons.Default.PhoneAndroid,
                        color = Color(0xFF42A5F5)
                    )
                    MetricRow(
                        label = "Distractions",
                        value = "$distractionsBlocked blocked",
                        icon = Icons.Default.Block,
                        color = if (distractionsBlocked == 0) Color(0xFF66BB6A) else Color(0xFFFF5252)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Weekly Screen Time Trend Card ───────────────────────────────────────────
@Composable
fun WeeklyScreenTimeTrendCard(weeklyData: List<Pair<String, Long>>) {
    if (weeklyData.isEmpty()) return

    val maxMinutes = weeklyData.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Timeline,
                    contentDescription = null,
                    tint = Color(0xFF7C4DFF),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Screen Time This Week",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(20.dp))

            // Bar chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyData.forEach { (day, minutes) ->
                    val heightFraction = (minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0.05f, 1f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            formatMinutes(minutes),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 9.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .fillMaxHeight(heightFraction)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xFF7C4DFF), Color(0xFF448AFF))
                                    )
                                )
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            day,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ─── Helper ──────────────────────────────────────────────────────────────────
private fun formatMinutes(minutes: Long): String {
    return when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}
