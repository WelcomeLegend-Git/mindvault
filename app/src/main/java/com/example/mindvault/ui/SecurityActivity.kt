package com.example.mindvault.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindvault.data.AppPasswordManager
import com.example.mindvault.ui.theme.MindVaultTheme

class SecurityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MindVaultTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF100F1C), Color(0xFF2C2A4A), Color(0xFF4A4678))
                            )
                        )
                ) {
                    SecurityScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen() {
    val context = LocalContext.current
    // Corrected: isPasswordSet() does not take any arguments.
    val isPasswordSet = remember { mutableStateOf(AppPasswordManager.isPasswordSet()) }

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isCurrentPasswordVisible by remember { mutableStateOf(false) }
    var isNewPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security", fontWeight = FontWeight.Bold) },
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
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Security Shield",
                tint = Color(0xFFD1B1FF),
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "App Lock",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Secure your vault with a device password.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(32.dp))

            if (isPasswordSet.value) {
                PremiumPasswordTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = "Current Password",
                    isVisible = isCurrentPasswordVisible,
                    onVisibilityChange = { isCurrentPasswordVisible = !isCurrentPasswordVisible }
                )
                Spacer(Modifier.height(16.dp))
            }

            PremiumPasswordTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = if (isPasswordSet.value) "New Password" else "Set Password",
                isVisible = isNewPasswordVisible,
                onVisibilityChange = { isNewPasswordVisible = !isNewPasswordVisible }
            )
            Spacer(Modifier.height(16.dp))

            PremiumPasswordTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = "Confirm Password",
                isVisible = isConfirmPasswordVisible,
                onVisibilityChange = { isConfirmPasswordVisible = !isConfirmPasswordVisible }
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    if (newPassword.length < 4) {
                        Toast.makeText(context, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show(); return@Button
                    }
                    if (newPassword != confirmPassword) {
                        Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show(); return@Button
                    }
                    // Corrected: verifyPassword() only takes the password string.
                    if (isPasswordSet.value && !AppPasswordManager.verifyPassword(currentPassword)) {
                        Toast.makeText(context, "Current password incorrect", Toast.LENGTH_SHORT).show(); return@Button
                    }
                    // Corrected: setPassword() only takes the password string.
                    AppPasswordManager.setPassword(newPassword)
                    isPasswordSet.value = true
                    Toast.makeText(context, "Password Updated Successfully", Toast.LENGTH_SHORT).show()
                    (context as? ComponentActivity)?.finish()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF8F5CFF), Color(0xFFB585FF))
                        )
                    ),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Text(if (isPasswordSet.value) "Change Password" else "Set Password", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            // Show Remove Password button only when password is set
            if (isPasswordSet.value) {
                Spacer(Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = {
                        if (!AppPasswordManager.verifyPassword(currentPassword)) {
                            Toast.makeText(context, "Current password incorrect", Toast.LENGTH_SHORT).show(); return@OutlinedButton
                        }
                        AppPasswordManager.clearPassword()
                        isPasswordSet.value = false
                        Toast.makeText(context, "Password Removed Successfully", Toast.LENGTH_SHORT).show()
                        (context as? ComponentActivity)?.finish()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    border = BorderStroke(2.dp, Color(0xFFFF6B6B)),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFF6B6B)
                    )
                ) {
                    Text("Remove Password", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun PremiumPasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isVisible: Boolean,
    onVisibilityChange: () -> Unit
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        label = { Text(label, color = Color.White.copy(alpha = 0.6f)) },
        singleLine = true,
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            val image = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
            val description = if (isVisible) "Hide password" else "Show password"
            IconButton(onClick = onVisibilityChange) {
                Icon(imageVector = image, description, tint = Color(0xFFD1B1FF))
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.1f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color(0xFFD1B1FF)
        )
    )
}