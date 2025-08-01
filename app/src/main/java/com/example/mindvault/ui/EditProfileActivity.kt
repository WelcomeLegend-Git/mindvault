package com.example.mindvault.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mindvault.data.UserManager
import com.example.mindvault.ui.theme.MindVaultTheme

class EditProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MindVaultTheme {
                EditProfileScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen() {
    val context = LocalContext.current
    val currentUser = UserManager.currentUser.collectAsState(initial = null).value

    var name by remember { mutableStateOf(currentUser?.name ?: "") }
    var weeklyGoalText by remember { mutableStateOf(currentUser?.preferences?.weeklyGoal?.toString() ?: "") }
    var imageUri by remember { mutableStateOf<Uri?>(currentUser?.profilePicture?.let { Uri.parse(it) }) }

    val imagePickerLauncher = rememberLauncherForActivityResult(contract = PickVisualMedia()) { uri ->
        if (uri != null) {
            imageUri = uri
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0F23),
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            "✨ Edit Profile", 
                            color = Color.White, 
                            fontSize = 22.sp, 
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color(0xFFFFD700).copy(alpha = 0.3f),
                                    offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                    blurRadius = 4f
                                )
                            )
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Spacer(Modifier.height(32.dp))
                    
                    // Premium Profile Picture Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF1A1A2E).copy(alpha = 0.9f),
                                            Color(0xFF16213E).copy(alpha = 0.8f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFFFD700).copy(alpha = 0.3f),
                                            Color(0xFF6C63FF).copy(alpha = 0.2f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Profile Picture",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD700),
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                
                                Box(
                                    modifier = Modifier.size(140.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Profile Picture Container
                                    Box(
                                        modifier = Modifier
                                            .size(130.dp)
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        Color(0xFFFFD700).copy(alpha = 0.3f),
                                                        Color(0xFF6C63FF).copy(alpha = 0.2f),
                                                        Color.Transparent
                                                    ),
                                                    radius = 200f
                                                ),
                                                shape = CircleShape
                                            )
                                            .border(
                                                width = 2.dp,
                                                brush = Brush.linearGradient(
                                                    colors = listOf(
                                                        Color(0xFFFFD700).copy(alpha = 0.6f),
                                                        Color(0xFF6C63FF).copy(alpha = 0.4f)
                                                    )
                                                ),
                                                shape = CircleShape
                                            )
                                            .clip(CircleShape)
                                            .clickable {
                                                imagePickerLauncher.launch(
                                                    PickVisualMediaRequest(PickVisualMedia.ImageOnly)
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (imageUri != null) {
                                            AsyncImage(
                                                model = imageUri,
                                                contentDescription = "Profile Picture",
                                                modifier = Modifier
                                                    .size(126.dp)
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    Icons.Default.CameraAlt,
                                                    contentDescription = "Add Photo",
                                                    tint = Color(0xFFFFD700),
                                                    modifier = Modifier.size(40.dp)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "Add Photo",
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                    
                                    // Remove Picture Button (only show if image exists)
                                    if (imageUri != null) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(36.dp)
                                                .background(
                                                    brush = Brush.radialGradient(
                                                        colors = listOf(
                                                            Color(0xFFFF5252),
                                                            Color(0xFFD32F2F)
                                                        )
                                                    ),
                                                    shape = CircleShape
                                                )
                                                .border(
                                                    width = 2.dp,
                                                    color = Color.White,
                                                    shape = CircleShape
                                                )
                                                .clip(CircleShape)
                                                .clickable { 
                                                    imageUri = null 
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Remove Photo",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Name TextField Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF1A1A2E).copy(alpha = 0.8f),
                                            Color(0xFF16213E).copy(alpha = 0.7f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF6C63FF).copy(alpha = 0.3f),
                                            Color(0xFFFFD700).copy(alpha = 0.2f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(24.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Display Name",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6C63FF),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                
                                // Show current name as placeholder
                                if (currentUser?.name?.isNotEmpty() == true && name.isEmpty()) {
                                    Text(
                                        text = "Current: ${currentUser.name}",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                
                                PremiumTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = "",
                                    placeholder = currentUser?.name ?: "Enter your name"
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Weekly Goal TextField Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF1A1A2E).copy(alpha = 0.8f),
                                            Color(0xFF16213E).copy(alpha = 0.7f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFFFD700).copy(alpha = 0.3f),
                                            Color(0xFF6C63FF).copy(alpha = 0.2f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(24.dp)
                        ) {
                            Column {
                                Text(
                                    text = "⏱️ Weekly Goal",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD700),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                
                                // Show current goal as placeholder
                                if (currentUser?.preferences?.weeklyGoal != null && weeklyGoalText.isEmpty()) {
                                    Text(
                                        text = "Current: ${currentUser.preferences.weeklyGoal} minutes",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                
                                PremiumTextField(
                                    value = weeklyGoalText,
                                    onValueChange = { weeklyGoalText = it.filter { ch -> ch.isDigit() } },
                                    label = "",
                                    placeholder = currentUser?.preferences?.weeklyGoal?.toString() ?: "1200",
                                    keyboardType = KeyboardType.Number
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(40.dp))
                }

                // Save Button
                item {
                    Button(
                        onClick = {
                            val success = UserManager.updateCurrentUser(
                                newName = name.takeIf { it.isNotBlank() },
                                newProfilePicture = imageUri?.toString(),
                                newWeeklyGoal = weeklyGoalText.toLongOrNull()
                            )
                            Toast.makeText(
                                context,
                                if (success) "✨ Profile updated successfully!" else "❌ Failed to update profile",
                                Toast.LENGTH_SHORT
                            ).show()
                            if (success) {
                                (context as? ComponentActivity)?.finish()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFFFD700),
                                            Color(0xFFB88746),
                                            Color(0xFF6C63FF)
                                        )
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✨ Save Changes",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = Color.Black,
                                style = TextStyle(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.White.copy(alpha = 0.3f),
                                        offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                                        blurRadius = 2f
                                    )
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(Color(0xFFFFD700)),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFD700).copy(alpha = 0.3f),
                                Color(0xFF6C63FF).copy(alpha = 0.2f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                innerTextField()
            }
        }
    )
}                                              