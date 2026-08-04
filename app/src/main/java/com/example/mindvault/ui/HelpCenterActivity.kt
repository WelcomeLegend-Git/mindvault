package com.example.mindvault.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindvault.ui.theme.MindVaultTheme

class HelpCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MindVaultTheme {
                HelpCenterScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpCenterScreen() {
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help Center", fontWeight = FontWeight.Bold) },
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            item {
                // Contact Form
                ContactFormCard()
            }
            
            item {
                // FAQ Section Header
                Text(
                    text = "Frequently Asked Questions",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(getFAQItems()) { faq ->
                FAQCard(faq)
            }
        }
    }
    }
}

@Composable
fun ContactFormCard() {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ContactSupport,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Contact Support",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Email Field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { 
                    Text("Your Email", color = Color.White.copy(alpha = 0.7f))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF6C63FF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Description Field
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { 
                    Text("Describe your issue", color = Color.White.copy(alpha = 0.7f))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF6C63FF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp),
                maxLines = 5
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Submit Button
            Button(
                onClick = {
                    if (email.isNotBlank() && description.isNotBlank()) {
                        isSubmitting = true
                        sendEmailToSupport(context, email, description)
                        isSubmitting = false
                        email = ""
                        description = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting && email.isNotBlank() && description.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6C63FF)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Message")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "We'll get back to you within 24 hours",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun FAQCard(faq: FAQ) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = faq.question,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = faq.answer,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

data class FAQ(
    val question: String,
    val answer: String
)

fun getFAQItems(): List<FAQ> {
    return listOf(
        FAQ(
            "How do I start a focus session?",
            "Tap the Focus Mode button on the home screen, set your desired duration, and press start. The app will block distractions and track your focus time."
        ),
        FAQ(
            "Can I customize focus session durations?",
            "Yes! You can set custom durations from 5 minutes to 4 hours. The app remembers your preferred settings for quick access."
        ),
        FAQ(
            "What apps are blocked during focus mode?",
            "By default, social media and entertainment apps are blocked. You can customize the blocklist in Settings to add or remove specific apps."
        ),
        FAQ(
            "How does the streak system work?",
            "Your streak increases each day you complete at least one focus session. Missing a day will reset your streak, but your longest streak record is preserved."
        ),
        FAQ(
            "Can I use MindVault without an account?",
            "Yes, you can use Guest Mode for basic features. However, creating an account enables cloud sync, advanced analytics, and cross-device access."
        ),
        FAQ(
            "How is my focus score calculated?",
            "Your focus score is based on session duration, consistency, app usage during focus time, and completion rate. Higher scores unlock achievements and levels."
        ),
        FAQ(
            "Can I export my focus data?",
            "Premium users can export their data in CSV format from the Analytics section. This includes session history, statistics, and productivity metrics."
        ),
        FAQ(
            "What's the difference between Guest and Premium?",
            "Premium includes unlimited focus sessions, detailed analytics, custom themes, advanced blocking features, and priority support."
        ),
        FAQ(
            "Can I sync data across devices?",
            "Yes, with a registered account, your focus data automatically syncs across all your devices in real-time."
        ),
        FAQ(
            "How do I reset my statistics?",
            "Go to Settings > Data Management > Reset Statistics. Note: This action cannot be undone and will permanently delete all your focus history."
        ),
        FAQ(
            "Why aren't my focus sessions being tracked?",
            "Ensure the app has necessary permissions (Usage Access, Notifications). Also check that you're not force-closing the app during sessions."
        ),
        FAQ(
            "Can I pause a focus session?",
            "Focus sessions are designed to be continuous for maximum effectiveness. So you cannot end the session in the middle."
        )
    )
}

fun sendEmailToSupport(context: android.content.Context, userEmail: String, description: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf("roasting.art844@slmail.me"))
        putExtra(Intent.EXTRA_SUBJECT, "MindVault Support Request")
        putExtra(Intent.EXTRA_TEXT, """
            User Email: $userEmail
            
            Issue Description:
            $description
            
            ---
            Sent from MindVault Help Center
        """.trimIndent())
    }
    
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback: could show a toast or copy to clipboard
    }
}
