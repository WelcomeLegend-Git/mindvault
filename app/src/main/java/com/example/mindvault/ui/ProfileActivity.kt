package com.example.mindvault.ui

import android.content.Intent
import com.example.mindvault.ui.LoginActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mindvault.data.AuthManager
import com.example.mindvault.data.UserManager
import com.example.mindvault.ui.theme.MindVaultTheme
import kotlinx.coroutines.launch
import com.example.mindvault.data.StatisticsManager
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import android.net.Uri
import com.example.mindvault.ui.HelpCenterActivity
import com.example.mindvault.ui.AboutActivity
import com.example.mindvault.ui.EditProfileActivity
import com.example.mindvault.ui.SecurityActivity
import com.example.mindvault.ui.StatisticsActivity
import com.example.mindvault.ui.AchievementsActivity
import com.example.mindvault.ui.DeveloperSettingsActivity

class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MindVaultTheme {
                ProfileScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by UserManager.currentUser.collectAsStateWithLifecycle()
    val isLoggedIn by UserManager.isLoggedIn.collectAsStateWithLifecycle()
    
    val scrollState = rememberScrollState()
    
    // Observe real streak data from StatisticsManager
    val userStats by StatisticsManager.userStats.collectAsStateWithLifecycle()
    val currentStreak = userStats?.currentStreak ?: 0
    val longestStreak = userStats?.longestStreak ?: 0
    
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
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Top App Bar
            TopAppBar(
                title = {
                    Text(
                        text = "Profile",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { (context as ProfileActivity).finish() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    // The sync button was here. It has been removed because sync is now automatic.
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // User Profile Card
            if (isLoggedIn && currentUser != null) {
                UserProfileCard(user = currentUser!!, currentStreak = currentStreak, longestStreak = longestStreak)
            } else {
                GuestProfileCard()
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Settings Card
            SettingsSection()
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // App Shortcuts
            AppShortcutsSection()

            Spacer(modifier = Modifier.height(24.dp))
            
            // Help & Support
            HelpSupportSection()

            Spacer(modifier = Modifier.height(24.dp))

            // Sign Out Button (centered below About)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.widthIn(max = 340.dp)) {
                    SignOutSection()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun UserProfileCard(user: com.example.mindvault.data.User, currentStreak: Int = 0, longestStreak: Int = 0) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A2E).copy(alpha = 0.95f),
                            Color(0xFF16213E).copy(alpha = 0.85f),
                            Color(0xFF0F0F23).copy(alpha = 0.90f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFD700).copy(alpha = 0.3f),
                            Color(0xFF6C63FF).copy(alpha = 0.2f),
                            Color(0xFFFFD700).copy(alpha = 0.1f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            // Corner Stats positioned absolutely
            Box(modifier = Modifier.fillMaxWidth()) {
                // Current Streak - Top Left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF6C63FF).copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$currentStreak",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700)
                        )
                        Text(
                            text = "Current",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Longest Streak - Top Right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFD700).copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$longestStreak",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6C63FF)
                        )
                        Text(
                            text = "Longest",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(40.dp)) // Space for corner stats
                
                // Profile Picture with Enhanced Glow
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFD700).copy(alpha = 0.8f),
                                    Color(0xFFB88746).copy(alpha = 0.6f),
                                    Color(0xFF6C63FF).copy(alpha = 0.4f)
                                ),
                                radius = 120f
                            ),
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFFD700).copy(alpha = 0.6f),
                                    Color(0xFF6C63FF).copy(alpha = 0.4f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!user.profilePicture.isNullOrBlank()) {
                        AsyncImage(
                            model = user.profilePicture,
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(82.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile Picture",
                            tint = Color.White,
                            modifier = Modifier.size(45.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // User Name with Glow Effect
                Text(
                    text = user.name,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color(0xFFFFD700).copy(alpha = 0.3f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                            blurRadius = 4f
                        )
                    )
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // User Email
                Text(
                    text = user.email,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Premium Role Badge
                Box(
                    modifier = Modifier
                        .background(
                            brush = if (user.role == com.example.mindvault.data.UserRole.PREMIUM) {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFFD700),
                                        Color(0xFFB88746)
                                    )
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF6C63FF),
                                        Color(0xFF5A54D9)
                                    )
                                )
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (user.role == com.example.mindvault.data.UserRole.PREMIUM) {
                                Color(0xFFFFD700).copy(alpha = 0.5f)
                            } else {
                                Color(0xFF6C63FF).copy(alpha = 0.5f)
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (user.role == com.example.mindvault.data.UserRole.PREMIUM) "✨ ${user.role.name}" else user.role.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (user.role == com.example.mindvault.data.UserRole.PREMIUM) Color.Black else Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Weekly Goal Card - Center Highlight
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.8f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFFFD700).copy(alpha = 0.2f),
                                        Color(0xFF6C63FF).copy(alpha = 0.2f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFFD700).copy(alpha = 0.6f),
                                        Color(0xFF6C63FF).copy(alpha = 0.4f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "⏱️ WEEKLY GOAL",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700),
                                letterSpacing = 1.sp
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "${user.preferences.weeklyGoal}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color(0xFF6C63FF).copy(alpha = 0.5f),
                                        offset = androidx.compose.ui.geometry.Offset(0f, 3f),
                                        blurRadius = 6f
                                    )
                                )
                            )
                            
                            Text(
                                text = "minutes",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GuestProfileCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Guest Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF607D8B),
                                    Color(0xFF455A64)
                                )
                            ),
                            shape = CircleShape
                        )
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Guest",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Guest User",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Limited features available",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { /* TODO: Open login options */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6C63FF)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Sign In",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection() {
    val context = LocalContext.current
    var showDeveloperTestingDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFD1B1FF),
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                
                ProfileOptionItem(
                    icon = Icons.Default.Edit,
                    title = "Edit Profile",
                    subtitle = "Update your personal information"
                ) {
                    context.startActivity(Intent(context, EditProfileActivity::class.java))
                }
                
                ProfileOptionItem(
                    icon = Icons.Default.Lock,
                    title = "Security",
                    subtitle = "Set or change app password"
                ) {
                    context.startActivity(Intent(context, SecurityActivity::class.java))
                }
                
                ProfileOptionItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Customize your alerts",
                    onClick = { context.startActivity(Intent(context, NotificationSettingsActivity::class.java)) }
                )
                
                // Developer Section
                ProfileOptionItem(
                    icon = Icons.Default.DeveloperMode,
                    title = "Developer Testing",
                    subtitle = "Debug options and experimental features"
                ) {
                    showDeveloperTestingDialog = true
                }
            }
        }
    }

    if (showDeveloperTestingDialog) {
        AlertDialog(
            onDismissRequest = { 
                showDeveloperTestingDialog = false 
                passwordInput = "" 
            },
            title = { Text("Developer Access") },
            text = {
                Column {
                    Text("Please enter the developer password:")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            },
            containerColor = Color(0xFF16213E),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            confirmButton = {
                TextButton(
                    onClick = {
                        if (passwordInput == "878955") {
                            showDeveloperTestingDialog = false
                            passwordInput = ""
                            context.startActivity(Intent(context, DeveloperSettingsActivity::class.java))
                        } else {
                            Toast.makeText(context, "Incorrect password", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Verify", color = Color(0xFF6C63FF))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeveloperTestingDialog = false 
                        passwordInput = ""
                    }
                ) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}



@Composable
fun AppShortcutsSection() {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "App Shortcuts",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFD1B1FF),
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            
                ProfileOptionItem(
                    icon = Icons.Default.Schedule,
                    title = "Focus Sessions",
                    subtitle = "Quick access to focus timer"
                ) {
                    context.startActivity(Intent(context, com.example.mindvault.MainActivity::class.java))
                }
                
                ProfileOptionItem(
                    icon = Icons.Default.BarChart,
                    title = "Statistics",
                    subtitle = "View your progress and insights"
                ) {
                    context.startActivity(Intent(context, StatisticsActivity::class.java))
                }
                
                ProfileOptionItem(
                    icon = Icons.Default.Settings,
                    title = "Focus Setup",
                    subtitle = "Configure your focus settings"
                ) {
                    context.startActivity(Intent(context, FocusModeSetupActivity::class.java))
                }
            }
        }
    }
}

@Composable
fun HelpSupportSection() {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Help & Support",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFD1B1FF),
                modifier = Modifier.padding(bottom = 20.dp)
            )
            
            ProfileOptionItem(
                icon = Icons.Default.Help,
                title = "Help Center",
                subtitle = "Find answers to common questions"
            ) {
                context.startActivity(Intent(context, HelpCenterActivity::class.java))
            }
            
            ProfileOptionItem(
                icon = Icons.Default.Feedback,
                title = "Send Feedback",
                subtitle = "Share your thoughts with us"
            ) {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("roasting.art844@slmail.me"))
                    putExtra(Intent.EXTRA_SUBJECT, "MindVault Feedback")
                }
                context.startActivity(intent)
            }
            
            ProfileOptionItem(
                icon = Icons.Default.Share,
                title = "Share App",
                subtitle = "Spread the word about MindVault"
            ) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "MindVault – Focus & Productivity")
                    putExtra(Intent.EXTRA_TEXT, "Unlock deep focus with MindVault – the ultra-premium productivity app. Download now: https://play.google.com/store/apps/details?id=${context.packageName}")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share MindVault"))
            }
            
            ProfileOptionItem(
                icon = Icons.Default.LocalFireDepartment,
                title = "View Streak",
                subtitle = "See your focus streak history"
            ) {
                // Open Statistics screen – user can switch to streak calendar there
                context.startActivity(Intent(context, StatisticsActivity::class.java))
            }
            
            ProfileOptionItem(
                icon = Icons.Default.Info,
                title = "About",
                subtitle = "Learn more about MindVault"
            ) {
                context.startActivity(Intent(context, AboutActivity::class.java))
            }
        }
    }
}

@Composable
fun ProfileOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isDangerous: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (isDangerous) Color(0xFFFF5252).copy(alpha = 0.2f) else Color(0xFF6C63FF).copy(alpha = 0.2f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isDangerous) Color(0xFFFF5252) else Color(0xFF6C63FF),
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        
        Icon(
            imageVector = Icons.Default.ArrowForwardIos,
            contentDescription = "Navigate",
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

