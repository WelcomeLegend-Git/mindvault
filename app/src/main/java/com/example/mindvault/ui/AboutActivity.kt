package com.example.mindvault.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.mindvault.ui.theme.MindVaultTheme

/**
 * Ultra-premium About page describing MindVault.
 * Shows app name, version, mission statement, privacy promise and key features.
 */
class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MindVaultTheme {
                AboutScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0F23),
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with back arrow
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.Bold) },
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

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "MindVault",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF6C63FF),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Version $versionName",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Our Mission",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "MindVault empowers high-performers to achieve deep focus and unparalleled productivity. Built with ultra-premium design and cutting-edge analytics, we help you understand, optimise and master your time.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Privacy First",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "We do NOT collect personal data. All your focus sessions and statistics are stored securely on-device and (optionally) in your private cloud backup. Your data are always in your control.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Key Features",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                val features = listOf(
                    "Distraction-blocking Focus Mode with app-lock",
                    "Realtime streak & productivity analytics",
                    "Smart Context-Aware Motivation Quotes",
                    "Hyper-premium glassmorphism UI",
                    "Achievements and gamified motivation",
                    "Fully offline – works even in airplane mode"
                )
                features.forEach {
                    Text(
                        text = "• $it",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Open-Source Components",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "MindVault is built with Jetpack Compose, Kotlin Coroutines, Coil and Firebase – all brilliant open-source projects maintained by their respective communities.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = "© 2026 MindVault Labs. All rights reserved.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
