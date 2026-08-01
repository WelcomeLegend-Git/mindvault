package com.example.mindvault

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindvault.data.FocusManager
import com.example.mindvault.model.FocusType
import com.example.mindvault.ui.theme.MindVaultTheme
import com.example.mindvault.utils.AppManager
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AppBlockedActivity : ComponentActivity() {
    
    private lateinit var handler: Handler
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Make this activity appear on top of everything and prevent bypass
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        
        val blockedApp = intent.getStringExtra("blocked_app") ?: ""
        val slotTypeName = intent.getStringExtra("slot_type") ?: ""
        val slotType = try {
            FocusType.valueOf(slotTypeName)
        } catch (e: Exception) {
            FocusType.STUDY_TIME
        }
        
        // Prevent recent apps button and other system buttons
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        handler = Handler()
        
        // Handle back press - prevent going back
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - prevent going back to blocked app
                // Re-check if app should still be blocked
                if (FocusManager.isAppBlocked(blockedApp)) {
                    // Stay on this screen
                } else {
                    finish()
                }
            }
        })
        
        setContent {
            MindVaultTheme {
                AppBlockedScreen(
                    blockedApp = blockedApp,
                    slotType = slotType,
                    onClose = { 
                        // Go to home screen
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                        finish()
                    }
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
    }
}

@Composable
fun AppBlockedScreen(
    blockedApp: String,
    slotType: FocusType,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    val activeSlot = FocusManager.getCurrentActiveSlot()
    
    // Update time every second
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalDateTime.now()
            
            // Check if time slot is still active
            if (FocusManager.getCurrentActiveSlot() == null) {
                onClose()
                break
            }
            
            delay(1000)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = when (slotType) {
                    FocusType.STUDY_TIME -> Color(0xFF1E40AF) // Blue for study time
                    FocusType.REST_TIME -> Color(0xFF059669)  // Green for rest time
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            // Block Icon
            Text(
                text = "🚫",
                fontSize = 80.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // App Blocked Title
            Text(
                text = "App Blocked",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // App Name
            Text(
                text = AppManager.getAppName(context, blockedApp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // Reason Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (slotType) {
                            FocusType.STUDY_TIME -> "📚 Study Time Active"
                            FocusType.REST_TIME -> "🎮 Rest Time Active"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = when (slotType) {
                            FocusType.STUDY_TIME -> "This app is blocked during study time to help you focus."
                            FocusType.REST_TIME -> "Only selected apps are allowed during rest time."
                        },
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
            
            // Time Info
            if (activeSlot != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Time Remaining",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        Text(
                            text = "Until ${activeSlot.endTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        
                        Text(
                            text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Motivational Message
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (slotType) {
                            FocusType.STUDY_TIME -> "Stay Focused! 💪"
                            FocusType.REST_TIME -> "Enjoy Your Break! 😊"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = when (slotType) {
                            FocusType.STUDY_TIME -> "Use this time productively. Your future self will thank you!"
                            FocusType.REST_TIME -> "Take a proper break with your selected apps only."
                        },
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Return to Home Button
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Return to Home",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}