package com.example.mindvault.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindvault.MainActivity
import com.example.mindvault.R
import com.example.mindvault.ui.theme.MindVaultTheme
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MindVaultTheme {
                SplashScreen {
                    // Navigate to main activity after animation
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onAnimationComplete: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    
    // Logo animations
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )
    
    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "logoAlpha"
    )
    
    // Lock animation - starts open, then closes
    val lockProgress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1200,
            delayMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "lockProgress"
    )
    
    // Text animations
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 1200),
        label = "textAlpha"
    )
    
    LaunchedEffect(Unit) {
        startAnimation = true
        delay(3000) // Show splash for 3 seconds
        onAnimationComplete()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B5E20)), // Solid green background like logo
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Main logo - clean Flipkart style
            Image(
                painter = painterResource(id = R.drawable.mindvault_logo_transparent),
                contentDescription = "MindVault Logo",
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // App name
            Text(
                text = "MindVault",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.alpha(textAlpha)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tagline
            Text(
                text = "Secure Your Focus",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.alpha(textAlpha)
            )
        }
    }
}
