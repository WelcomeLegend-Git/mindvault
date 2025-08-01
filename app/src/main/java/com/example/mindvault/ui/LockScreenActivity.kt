package com.example.mindvault.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.mindvault.R
import com.example.mindvault.data.AppPasswordManager
import com.example.mindvault.ui.theme.MindVaultTheme

class LockScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MindVaultTheme {
                LockScreen(onUnlock = {
                    finish()
                })
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent user from backing out of the lock screen
        // You can optionally show a toast or exit the app
        finishAffinity() // Closes the app
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

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
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .shadow(elevation = 15.dp, shape = CircleShape, spotColor = Color(0xFF2E7D32))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF2E7D32).copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.mindvault_logo),
                    contentDescription = "MindVault Logo",
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "MindVault Locked",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter your password to continue",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            TextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.1f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        )
                    ),
                label = { Text("Password", color = Color.White.copy(alpha = 0.6f)) },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    val description = if (isPasswordVisible) "Hide password" else "Show password"
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(imageVector = image, description, tint = Color(0xFFFFD700))
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFFFD700)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (AppPasswordManager.verifyPassword(password)) {
                        onUnlock()
                    } else {
                        Toast.makeText(context, "Incorrect Password", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(18.dp), spotColor = Color(0xFF6C63FF))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFFFFD700), Color(0xFF6C63FF))
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Text("Unlock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}
