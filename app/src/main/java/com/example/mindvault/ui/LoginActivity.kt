package com.example.mindvault.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mindvault.R
import com.example.mindvault.data.AuthManager
import com.example.mindvault.data.UserManager
import com.example.mindvault.data.UserRole
import com.example.mindvault.ui.theme.MindVaultTheme
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MindVaultTheme {
                LoginScreen(
                    onLoginSuccess = { 
                        setResult(Activity.RESULT_OK)
                        finish()
                    },
                    onLoginCancel = { 
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {},
    onLoginCancel: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val authState by AuthManager.authState.collectAsStateWithLifecycle()
    val isLoading by AuthManager.isLoading.collectAsStateWithLifecycle()
    
    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                val authResult = AuthManager.handleGoogleSignInResult(result.data)
                if (authResult.isSuccess) {
                    onLoginSuccess()
                } else {
                    Toast.makeText(context, authResult.errorMessage ?: "Sign in failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // Handle auth state changes
    LaunchedEffect(authState) {
        if (authState?.isSuccess == true) {
            onLoginSuccess()
        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF2E7D32).copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.mindvault_logo),
                    contentDescription = "MindVault Logo",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // App Title
            Text(
                text = "MindVault",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle
            Text(
                text = "Your Study Companion",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Welcome Message
            Text(
                text = "Welcome!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Sign in to unlock all features",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Google Sign In Button
            LoginButton(
                icon = Icons.Default.Grade,
                text = "Continue with Google",
                backgroundColor = Color(0xFF4285F4),
                onClick = {
                    scope.launch {
                        try {
                            val signInIntent = AuthManager.getGoogleSignInIntent()
                            googleSignInLauncher.launch(signInIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to start Google Sign-In: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )

            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Guest Sign In Button
            LoginButton(
                icon = Icons.Default.AccountCircle,
                text = "Continue as Guest",
                backgroundColor = Color(0xFF6C63FF),
                onClick = {
                    scope.launch {
                        val authResult = AuthManager.signInAsGuest()
                        if (!authResult.isSuccess) {
                            Toast.makeText(context, authResult.errorMessage ?: "Guest Sign-In failed", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Terms and Privacy
            Text(
                text = "By continuing, you agree to our Terms of Service and Privacy Policy",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            
            // Loading indicator
            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
fun LoginButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
