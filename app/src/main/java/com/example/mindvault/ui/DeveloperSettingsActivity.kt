package com.example.mindvault.ui

import android.content.Context
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindvault.engine.ScenarioVibe
import com.example.mindvault.ui.theme.MindVaultTheme

class DeveloperSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MindVaultTheme {
                DeveloperSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperSettingsScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

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
            TopAppBar(
                title = {
                    Text(
                        text = "Developer Testing",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Active Test Features ---
            Text(
                text = "Active Test Features",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD1B1FF),
                modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF6C63FF).copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 2.dp,
                            color = Color(0xFF6C63FF),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cosmic Blackhole UI",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Active universal toggle component and focus mode ambient animation set.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Notification & Quote Testing ---
            Text(
                text = "Notification & Quote Testing",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD1B1FF),
                modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
            )
            Text(
                text = "Tap a scenario to fire a live notification and preview the selected quote. Check deck stats below to verify no repeats.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 16.dp, start = 8.dp),
                lineHeight = 18.sp
            )

            // Last triggered result state
            var lastResult by remember { mutableStateOf<String?>(null) }
            var deckStatsRefresh by remember { mutableIntStateOf(0) }

            // Scenario buttons
            val scenarios = listOf(
                Triple("🌙", "Harsh Wakeup" to "Scrolling social media late at night", false to true),
                Triple("📚", "Encouraging" to "Studying late at night", true to false),
                Triple("☀️", "Mindful Refocus" to "Scrolling social media during the day", false to true),
                Triple("⚡", "High Energy" to "Active study session during the day", true to false),
                Triple("🌅", "Reflective Winddown" to "Idle at night, winding down", false to false),
                Triple("🕐", "Day Idle" to "Idle during the day, no session", false to false)
            )

            scenarios.forEach { (emoji, titleDesc, params) ->
                val (title, desc) = titleDesc
                val (isStudy, isScroll) = params

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            // Fire the actual notification and get the result
                            val result = com.example.mindvault.ui.notifications.CustomNotificationBuilder.showQuoteNotification(
                                context = context,
                                isStudySession = isStudy,
                                isScrolling = isScroll
                            )

                            // Display the same quote that was sent in the notification
                            lastResult = "\"${result.quote.q}\"\n— ${result.quote.a}\n\n" +
                                    "Vibe: ${result.vibe.name}\nFont: ${result.fontFileName}"
                            deckStatsRefresh++

                            Toast.makeText(context, "Notification sent!", Toast.LENGTH_SHORT).show()
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.06f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = Color(0xFF6C63FF).copy(alpha = 0.2f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = desc,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Send",
                            tint = Color(0xFF6C63FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Last result display
            if (lastResult != null) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Last Triggered Quote",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = lastResult!!,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // --- Quote Deck Stats ---
            Text(
                text = "Quote Deck Stats",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD1B1FF),
                modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)
            )
            Text(
                text = "Quotes never repeat until every quote in a deck has been shown once. When all are exhausted, the deck reshuffles.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 16.dp, start = 8.dp),
                lineHeight = 18.sp
            )

            // Force recomposition on refresh
            @Suppress("UNUSED_EXPRESSION")
            deckStatsRefresh

            val deckPrefs = context.getSharedPreferences("mindvault_quote_decks", Context.MODE_PRIVATE)

            ScenarioVibe.entries.forEach { vibe ->
                val deckId = "deck_${vibe.name}"
                val seenCount = deckPrefs.getStringSet(deckId, emptySet())?.size ?: 0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = vibe.name.replace("_", " "),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (seenCount > 0) Color(0xFF6C63FF).copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$seenCount shown",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (seenCount > 0) Color(0xFF6C63FF) else Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Reset decks button
            OutlinedButton(
                onClick = {
                    deckPrefs.edit().clear().apply()
                    deckStatsRefresh++
                    Toast.makeText(context, "All quote decks reset!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFF6B6B)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Reset All Quote Decks",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
