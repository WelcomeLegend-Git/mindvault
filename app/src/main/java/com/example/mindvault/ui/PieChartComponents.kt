package com.example.mindvault.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.floor

/**
 * Reusable pie-chart based usage section matching the premium glassmorphism style.
 */
@Composable
fun AppUsagePieSection(
    title: String,
    usageList: List<Pair<String, Int>>, // list of app name to minutes
    totalMinutes: Int
) {
    Column {
        // Title Row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatMinutes(totalMinutes),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Always prepare Top 5 + Other slice (even if otherMinutes is 0)
        val sorted = usageList.sortedByDescending { it.second }
        val topFive = sorted.take(5)
        val otherMinutes = totalMinutes - topFive.sumOf { it.second }
        val displayList: List<Pair<String, Int>> = topFive + ("Other" to otherMinutes.coerceAtLeast(0))

        if (totalMinutes > 0) {
            // Pie chart + legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(220.dp)) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val radius = size.minDimension / 2
                    
                    // Draw shadow/glow effect
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            radius = radius + 20f
                        ),
                        radius = radius + 15f,
                        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                    )
                    
                    var currentAngle = 0f
                    displayList.forEachIndexed { idx, (_, minutes) ->
                        val sweepAngle = (minutes / totalMinutes.toFloat()) * 360f
                        val baseColor = sliceColor(idx)
                        
                        // Create gradient for each slice
                        val sliceGradient = Brush.sweepGradient(
                            colors = listOf(
                                baseColor,
                                baseColor.copy(alpha = 0.8f),
                                Color(
                                    red = (baseColor.red * 1.2f).coerceAtMost(1f),
                                    green = (baseColor.green * 1.2f).coerceAtMost(1f),
                                    blue = (baseColor.blue * 1.2f).coerceAtMost(1f),
                                    alpha = baseColor.alpha
                                )
                            ),
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                        )
                        
                        // Main slice with gradient
                        drawArc(
                            brush = sliceGradient,
                            startAngle = currentAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true
                        )
                        
                        // Subtle outline
                        drawArc(
                            color = Color.White.copy(alpha = 0.1f),
                            startAngle = currentAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        
                        currentAngle += sweepAngle
                    }
                    
                    // Inner circle for depth effect
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            radius = radius * 0.3f
                        ),
                        radius = radius * 0.25f,
                        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    displayList.filter { it.second > 0 }.forEachIndexed { idx, (app, minutes) ->
                        val c = sliceColor(idx)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(c, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${app.substringAfterLast('.')}  ${formatMinutes(minutes)}",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        } else {
            val message = if (title.contains("Focus Mode")) {
                "No focus sessions today"
            } else {
                "No usage data"
            }
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

private fun sliceColor(idx: Int): Color {
    val palette = listOf(
        Color(0xFFFF5252), // Red
        Color(0xFFFF9800), // Orange
        Color(0xFFFFEB3B), // Yellow
        Color(0xFF4CAF50), // Green
        Color(0xFF2196F3), // Blue
        Color(0xFF9C27B0)  // Purple (Other slice)
    )
    return palette[idx % palette.size].copy(alpha = 0.85f)
}

/* Deprecated mapping kept for reference but no longer used */
private fun usageColor(percent: Float): Color { // kept for compatibility if still referenced, maps to purple shades
    val alpha = 0.5f + 0.4f * percent
    return Color(0xFF6C63FF).copy(alpha = alpha)
}

@Composable
private fun PieChart(
    usageList: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    val totalMinutes = usageList.sumOf { it.second }

    Canvas(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2
        
        // Draw shadow/glow effect
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                radius = radius + 20f
            ),
            radius = radius + 15f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
        )
        
        var currentAngle = 0f
        usageList.forEachIndexed { idx, (_, minutes) ->
            val sweepAngle = (minutes / totalMinutes.toFloat()) * 360f
            val baseColor = sliceColor(idx)
            
            // Create gradient for each slice
            val sliceGradient = Brush.sweepGradient(
                colors = listOf(
                    baseColor,
                    baseColor.copy(alpha = 0.8f),
                    baseColor.copy(alpha = 1.2f).let {
                        Color(
                            red = (it.red * 1.2f).coerceAtMost(1f),
                            green = (it.green * 1.2f).coerceAtMost(1f),
                            blue = (it.blue * 1.2f).coerceAtMost(1f),
                            alpha = it.alpha
                        )
                    }
                ),
                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
            )
            
            // Main slice with gradient
            drawArc(
                brush = sliceGradient,
                startAngle = currentAngle,
                sweepAngle = sweepAngle,
                useCenter = true
            )
            
            // Subtle outline
            drawArc(
                color = Color.White.copy(alpha = 0.1f),
                startAngle = currentAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                style = Stroke(width = 2.dp.toPx())
            )
            
            currentAngle += sweepAngle
        }
        
        // Inner circle for depth effect
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                radius = radius * 0.3f
            ),
            radius = radius * 0.25f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
        )
    }
}

private fun formatMinutes(totalMinutes: Int): String {
    if (totalMinutes <= 0) return "0m"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Preview(showBackground = true)
@Composable
private fun PieChartPreview() {
    val sample = listOf(
        "AppA" to 60,
        "AppB" to 30,
        "AppC" to 15,
        "AppD" to 5
    )
    Box(modifier = Modifier.background(Color.Black)) {
        AppUsagePieSection("Preview", sample, 110)
    }
}
