package com.example.mindvault.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindvault.utils.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeeklyScreenTimeChart() {
    val context = LocalContext.current
    val dailyUsageMinutes = remember { mutableStateOf<List<Pair<DayOfWeek, Long>>>(emptyList()) }
    val selectedDayUsage = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        dailyUsageMinutes.value = withContext(Dispatchers.IO) {
            val today = LocalDate.now()
            val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong()) // Monday start
            (0..6).map { offset ->
                val date = weekStart.plusDays(offset.toLong())
                val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val end = if (offset == 6) System.currentTimeMillis() else date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val totalMs = UsageStatsHelper.getUsageStatsForRange(context, start, end).values.sum()
                date.dayOfWeek to (totalMs / 60000L)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            val avg = if (dailyUsageMinutes.value.isNotEmpty()) {
                dailyUsageMinutes.value.map { it.second }.filter { it > 0 }.average().toLong()
            } else 0L
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weekly All App Usage  ${formatMinutes(avg)} avg",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (selectedDayUsage.value != null) {
                    Text(
                        text = selectedDayUsage.value!!,
                        color = Color(0xFFFFA726),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (dailyUsageMinutes.value.isEmpty()) {
                Text(
                    text = "No usage data",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            } else {
                val max = dailyUsageMinutes.value.maxOf { it.second }.coerceAtLeast(1L)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        dailyUsageMinutes.value.forEach { (day, minutes) ->
                            val ratio = if (minutes > 0) (minutes / max.toFloat()).coerceAtLeast(0.05f) else 0f
                            val dayName = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1)
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        selectedDayUsage.value = if (selectedDayUsage.value == "$dayName: ${formatMinutes(minutes)}") {
                                            null
                                        } else {
                                            "$dayName: ${formatMinutes(minutes)}"
                                        }
                                    }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height((120 * ratio).dp.coerceAtLeast(4.dp))
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                                        ambientColor = if (selectedDayUsage.value?.startsWith(dayName) == true) {
                                            Color(0xFFFFD54F).copy(alpha = 0.5f)
                                        } else {
                                            Color(0xFFFFA726).copy(alpha = 0.5f)
                                        }
                                    )
                                    .background(
                                        brush = if (selectedDayUsage.value?.startsWith(dayName) == true) {
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0xFFFFF176),
                                                    Color(0xFFFFD54F),
                                                    Color(0xFFFFB300)
                                                )
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0xFFFFCC02),
                                                    Color(0xFFFFA726),
                                                    Color(0xFFFF8F00)
                                                )
                                            )
                                        },
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                    )
                            )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = dayName,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMinutes(total: Long): String {
    val hours = total / 60
    val mins = total % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}
