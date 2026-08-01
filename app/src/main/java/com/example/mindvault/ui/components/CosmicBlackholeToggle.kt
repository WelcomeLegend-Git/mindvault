package com.example.mindvault.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CosmicBlackholeToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }

    val trackColor = if (checked) Color(0xFF1E0A3C) else Color(0xFF16213E)

    // Thumb position animation
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy), label = "thumb"
    )

    // Swirling animation
    val infiniteTransition = rememberInfiniteTransition(label = "vortex")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )

    // Intense pulse for black hole event horizon
    val eventHorizonPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )
    
    // Starfield offset for parallax
    val starOffsetX = thumbOffset * 100f
    
    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) { onCheckedChange(!checked) }
            .clip(RoundedCornerShape(100))
            .background(trackColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val trackRadius = height / 2f
            
            // Draw Starfield Background
            val starCount = 30
            for (i in 0 until starCount) {
                // Pseudo-random deterministic stars based on index
                val starX = ((i * 37 + starOffsetX) % width + width) % width
                val starY = (i * 23) % height
                val starSize = (i % 3) + 1f
                val starAlpha = if (checked) 0.8f else 0.2f
                
                drawCircle(
                    color = Color.White.copy(alpha = starAlpha),
                    radius = starSize,
                    center = Offset(starX, starY)
                )
            }
            
            // Draw Space Nebula Colors if checked
            if (checked) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF6C63FF).copy(alpha = 0.3f),
                            Color(0xFFFF00E5).copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    ),
                    size = Size(width, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackRadius)
                )
            }

            // Draw Thumb / Blackhole / Planet
            val thumbPadding = 12f
            val thumbSize = height - (thumbPadding * 2)
            val maxThumbX = width - thumbSize - (thumbPadding * 2)
            val currentX = thumbPadding + (maxThumbX * thumbOffset)
            val thumbCenter = Offset(currentX + (thumbSize / 2f), height / 2f)

            if (checked) {
                // Draw Blackhole Accretion Disk (Swirling vortex)
                val diskColors = listOf(
                    Color(0xFFFFB300),
                    Color(0xFFFF5252),
                    Color(0xFF6C63FF),
                    Color(0xFFFFB300)
                )
                
                // Outer glowing accretion disk
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = diskColors,
                        center = thumbCenter
                    ),
                    radius = (thumbSize / 2f) * eventHorizonPulse * 1.5f,
                    center = thumbCenter,
                    alpha = 0.6f
                )
                
                // Swirling particles around black hole
                val numParticles = 8
                for (i in 0 until numParticles) {
                    val angle = Math.toRadians((rotationAngle + (i * (360f / numParticles))).toDouble())
                    val distance = (thumbSize / 2f) * 1.2f
                    val px = (thumbCenter.x + distance * cos(angle)).toFloat()
                    val py = (thumbCenter.y + distance * sin(angle)).toFloat()
                    
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = 3f,
                        center = Offset(px, py)
                    )
                }

                // Inner Black Hole (Singularity)
                drawCircle(
                    color = Color.Black,
                    radius = thumbSize / 2f,
                    center = thumbCenter
                )
                
                // Event Horizon highlight rim
                drawCircle(
                    color = Color(0xFFFFB300).copy(alpha = 0.8f),
                    radius = thumbSize / 2f,
                    center = thumbCenter,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
                
            } else {
                // Inactive state - Looks like a lifeless gray moon/planet
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFE0E0E0), Color(0xFF9E9E9E)),
                        center = Offset(thumbCenter.x - 5f, thumbCenter.y - 5f),
                        radius = thumbSize
                    ),
                    radius = thumbSize / 2f,
                    center = thumbCenter
                )
                
                // Draw a few craters
                drawCircle(color = Color(0xFF757575), radius = thumbSize * 0.15f, center = Offset(thumbCenter.x - thumbSize * 0.2f, thumbCenter.y - thumbSize * 0.2f))
                drawCircle(color = Color(0xFF757575), radius = thumbSize * 0.1f, center = Offset(thumbCenter.x + thumbSize * 0.2f, thumbCenter.y + thumbSize * 0.1f))
            }
        }
    }
}
