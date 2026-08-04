package com.example.mindvault.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mindvault.data.StatisticsManager
import com.example.mindvault.data.UserManager
import com.example.mindvault.ui.theme.MindVaultTheme
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class StatisticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize managers if not already done
        StatisticsManager.init(this)
        UserManager.init(this)
        
        setContent {
            MindVaultTheme {
                StatisticsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen() {
    val context = LocalContext.current
    val userStats by StatisticsManager.userStats.collectAsStateWithLifecycle()
    val dailyStats by StatisticsManager.dailyStats.collectAsStateWithLifecycle()
    val weeklyStats by StatisticsManager.weeklyStats.collectAsStateWithLifecycle()
    val currentUser by UserManager.currentUser.collectAsStateWithLifecycle()
    val isLoggedIn by UserManager.isLoggedIn.collectAsStateWithLifecycle()
    
    val tabs = listOf("Today", "Week", "Analytics")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    
    // Load real screen time data from UsageStatsManager
    val screenTimeSummary = remember { mutableStateOf(com.example.mindvault.data.ScreenTimeTracker.getTodayScreenTime(context)) }
    val weeklyScreenTime = remember { mutableStateOf(com.example.mindvault.data.ScreenTimeTracker.getWeeklyScreenTime(context)) }
    
    // Refresh on resume
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { }
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
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar
            TopAppBar(
                title = { Text("Statistics", fontWeight = FontWeight.Bold) },
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
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tab Selector
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = pagerState.currentPage == index
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    Color(0xFF6C63FF)
                                } else {
                                    Color.Transparent
                                }
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab,
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Content based on selected tab
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (page) {
                        0 -> {
                            // Today Tab
                            item { HeroStatsCard(dailyStats, userStats) }
                            item { ScreenTimeCard(screenTimeSummary.value) }
                            item { 
                                FocusVsPhoneCard(
                                    focusMinutes = dailyStats?.totalFocusTime ?: 0L,
                                    screenTimeMinutes = screenTimeSummary.value.totalScreenTimeMinutes,
                                    distractionsBlocked = dailyStats?.distractionCount ?: 0,
                                    productivityScore = StatisticsManager.getProductivityScore()
                                )
                            }
                            item { AppUsageBreakdownCard(screenTimeSummary.value.topApps) }
                            item { StreakCalendarCard(userStats) }
                            item { LevelProgressCard(userStats) }
                        }
                        1 -> {
                            // Week Tab
                            item { WeeklyHeroStatsCard(weeklyStats, userStats) }
                            item { WeeklyScreenTimeTrendCard(weeklyScreenTime.value) }
                            item { WeeklyBarChartSection(weeklyStats) }
                            item { StreakCalendarCard(userStats) }
                        }
                        2 -> {
                            // Analytics Tab
                            item { ProductivityTrendsCard(weeklyStats, userStats) }
                            item { FocusHeatmapCard(userStats) }
                            item { MonthlyProgressCard(userStats) }
                            item { PersonalBestsCard(userStats) }
                            item { DataInsightsCard(dailyStats, weeklyStats) }
                            item { AllTimeStatsCard(userStats) }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun TabSelector(
    selectedTab: Int,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tabs.size) { index ->
            val isSelected = selectedTab == index
            
            Card(
                modifier = Modifier
                    .clickable { onTabSelected(index) }
                    .animateContentSize(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        Color(0xFF6C63FF)
                    } else {
                        Color.White.copy(alpha = 0.1f)
                    }
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isSelected) 8.dp else 2.dp
                )
            ) {
                Text(
                    text = tabs[index],
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun HeroStatsCard(dailyStats: com.example.mindvault.data.DailyStats?, userStats: com.example.mindvault.data.UserStats?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = Color(0xFF6C63FF).copy(alpha = 0.3f),
                spotColor = Color(0xFF6C63FF).copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6C63FF),
                            Color(0xFF3F3D56),
                            Color(0xFF2F2E41)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Today's Focus",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${dailyStats?.totalFocusTime ?: 0}m",
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    
                    // Star Level Display replacing progress circle
                    val totalFocusMins = dailyStats?.totalFocusTime ?: 0L
                    val (dailyLevel, levelColor) = when {
                        totalFocusMins >= 600 -> 5 to Color(0xFFB9F2FF) // Diamond
                        totalFocusMins >= 480 -> 4 to Color(0xFFE5E4E2) // Platinum
                        totalFocusMins >= 360 -> 3 to Color(0xFFFFD700) // Gold
                        totalFocusMins >= 240 -> 2 to Color(0xFFC0C0C0) // Silver
                        totalFocusMins >= 120 -> 1 to Color(0xFFCD7F32) // Bronze
                        else -> 0 to Color.White.copy(alpha = 0.6f)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = levelColor,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "$dailyLevel",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickStatItem(
                        title = "Sessions",
                        value = "${dailyStats?.completedSessions ?: 0}",
                        icon = Icons.Default.PlayArrow
                    )
                    val totalFocusMins = dailyStats?.totalFocusTime ?: 0L
                    val (dailyLevel, levelColor) = remember(totalFocusMins) {
                        when {
                            totalFocusMins >= 600 -> 5 to Color(0xFFB9F2FF) // Diamond
                            totalFocusMins >= 480 -> 4 to Color(0xFFE5E4E2) // Platinum
                            totalFocusMins >= 360 -> 3 to Color(0xFFFFD700) // Gold
                            totalFocusMins >= 240 -> 2 to Color(0xFFC0C0C0) // Silver
                            totalFocusMins >= 120 -> 1 to Color(0xFFCD7F32) // Bronze
                            else -> 0 to Color.White.copy(alpha = 0.6f)
                        }
                    }
                    
                    QuickStatItem(
                        title = "Streak",
                        value = "${userStats?.currentStreak ?: 0}d",
                        icon = Icons.Default.LocalFireDepartment
                    )
                }
            }
        }
    }
}

@Composable
fun QuickStatItem(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color = Color.White.copy(alpha = 0.8f)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
    }
}

@Composable
fun ProgressItem(
    title: String,
    value: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}

@Composable
fun ProductivityInsightsCard(dailyStats: com.example.mindvault.data.DailyStats?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Insights,
                    contentDescription = "Insights",
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Productivity Insights",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val insights = generateInsights(dailyStats)
            insights.forEach { insight ->
                InsightItem(insight)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun InsightItem(insight: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    Color(0xFF6C63FF),
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = insight,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp
        )
    }
}

@Composable
fun QuickActionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Quick Actions",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ActionButton(
                    title = "Export Data",
                    icon = Icons.Default.FileDownload,
                    color = Color(0xFF6C63FF),
                    isEnabled = UserManager.hasPermission("EXPORT_DATA"),
                    onClick = { /* TODO: Implement export */ }
                )
                ActionButton(
                    title = "Set Goals",
                    icon = Icons.Default.Flag,
                    color = Color(0xFF00E676),
                    isEnabled = UserManager.hasPermission("PREMIUM_FEATURES"),
                    onClick = { /* TODO: Implement goal setting */ }
                )
                ActionButton(
                    title = "Share Stats",
                    icon = Icons.Default.Share,
                    color = Color(0xFFFFB74D),
                    isEnabled = true,
                    onClick = { /* TODO: Implement sharing */ }
                )
            }
        }
    }
}

@Composable
fun ActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(enabled = isEnabled) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color.copy(alpha = if (isEnabled) 0.2f else 0.1f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color.copy(alpha = if (isEnabled) 1f else 0.5f),
                modifier = Modifier.size(24.dp)
            )
            
            if (!isEnabled) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(16.dp)
                        .offset(x = 12.dp, y = (-12).dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = Color.White.copy(alpha = if (isEnabled) 0.8f else 0.5f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

// Additional cards for Week and All Time tabs would go here...
@Composable
fun WeeklyOverviewCard(weeklyStats: com.example.mindvault.data.WeeklyStats?) {
    // Implementation for weekly overview
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Weekly Overview - Coming Soon",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun WeeklyChartCard(weeklyStats: com.example.mindvault.data.WeeklyStats?) {
    // Implementation for weekly chart
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Weekly Chart - Coming Soon",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun WeeklyGoalsCard(weeklyStats: com.example.mindvault.data.WeeklyStats?) {
    // Implementation for weekly goals
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Weekly Goals - Coming Soon",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun StreakCalendarCard(userStats: com.example.mindvault.data.UserStats?) {
    val currentStreak = userStats?.currentStreak ?: 0
    val longestStreak = userStats?.longestStreak ?: 0
    val today = remember { java.time.LocalDate.now() }
    val dates = remember { (0..27).map { today.minusDays(it.toLong()) }.reversed() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Streak",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Current: ${currentStreak}d",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Longest: ${longestStreak}d",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Weekday labels
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("S","M","T","W","T","F","S").forEach { lbl ->
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lbl,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Calendar grid for current month
            // Build calendar weeks for the current month
            val calendarWeeks = remember(today) {
                val ym = java.time.YearMonth.from(today)
                val first = ym.atDay(1)
                val total = ym.lengthOfMonth()
                val datesInMonth = (1..total).map { first.plusDays((it - 1).toLong()) }
                val prefix = List(first.dayOfWeek.value % 7) { null }
                val temp: List<java.time.LocalDate?> = prefix + datesInMonth
                val suffix = List((7 - temp.size % 7) % 7) { null }
                (temp + suffix).chunked(7)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                calendarWeeks.forEach { weekDates ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        weekDates.forEach { date ->
                            if (date == null) {
                                Spacer(modifier = Modifier.size(32.dp))
                            } else {
                                val metGoal = remember(date) { com.example.mindvault.data.StatisticsManager.hadFocusOn(date) }
                                val backgroundModifier = if (metGoal) {
                                    Modifier
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF00E676).copy(alpha = 0.35f),
                                                    Color(0xFF00C853).copy(alpha = 0.35f)
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), CircleShape)
                                } else {
                                    Modifier
                                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), CircleShape)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .then(backgroundModifier),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        color = if (metGoal) Color.White else Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun LifetimeStatsCard(userStats: com.example.mindvault.data.UserStats?) {
    // Implementation for lifetime stats
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Lifetime Stats - Coming Soon",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun LevelProgressCard(userStats: com.example.mindvault.data.UserStats?) {
    val currentLevel = userStats?.level ?: 1
    val currentXP = userStats?.experiencePoints ?: 0
    val nextLevelXP = userStats?.nextLevelXP ?: 1000
    val rank = userStats?.rank ?: "Beginner"
    
    // Calculate progress safely
    val progress = if (nextLevelXP > 0) {
        (currentXP.toFloat() / nextLevelXP.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Header with Rank and Level
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current Rank",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    Text(
                        text = rank,
                        color = Color(0xFFFFD700), // Gold color for rank
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF6C63FF), Color(0xFF5A54D9))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Level $currentLevel",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // XP Progress Bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "XP Progress",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$currentXP / $nextLevelXP XP",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF00E676),
                                        Color(0xFF00C853)
                                    )
                                )
                            )
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "${nextLevelXP - currentXP} XP to next level",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun ProductivityScoreCard(dailyStats: com.example.mindvault.data.DailyStats?) {
    val score = dailyStats?.productivityScore ?: 0f
    val completedSessions = dailyStats?.completedSessions ?: 0
    val totalSessions = dailyStats?.totalSessions ?: 0
    val distractions = dailyStats?.distractionCount ?: 0
    val focusTime = dailyStats?.totalFocusTime ?: 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Productivity Score",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Circular Score Indicator
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(100.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { score / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = when {
                            score >= 80 -> Color(0xFF00E676)
                            score >= 50 -> Color(0xFFFFD700)
                            else -> Color(0xFFFF5252)
                        },
                        trackColor = Color.White.copy(alpha = 0.1f),
                        strokeWidth = 8.dp,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${score.toInt()}",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "/100",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Breakdown
                Column(modifier = Modifier.weight(1f)) {
                    ProductivityFactor(
                        label = "Completion",
                        value = if (totalSessions > 0) "${(completedSessions * 100 / totalSessions)}%" else "0%",
                        isPositive = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ProductivityFactor(
                        label = "Focus Time",
                        value = "${focusTime}m",
                        isPositive = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ProductivityFactor(
                        label = "Distractions",
                        value = "$distractions",
                        isPositive = distractions == 0
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductivityFactor(label: String, value: String, isPositive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = if (isPositive) Color(0xFF00E676) else Color(0xFFFF5252),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

// RankingCard merged into LevelProgressCard

private fun generateInsights(dailyStats: com.example.mindvault.data.DailyStats?): List<String> {
    val insights = mutableListOf<String>()
    
    dailyStats?.let { stats ->
        if (stats.distractionCount == 0) {
            insights.add("Perfect focus today! Zero distractions recorded.")
        } else if (stats.distractionCount < 3) {
            insights.add("Great self-control! Only ${stats.distractionCount} distractions today.")
        }
        
        if (stats.completedSessions == stats.totalSessions && stats.totalSessions > 0) {
            insights.add("100% session completion rate - you're on fire!")
        }
        
        if (stats.totalFocusTime > 120) {
            insights.add("Excellent focus time! You've exceeded 2 hours today.")
        }
    }
    
    if (insights.isEmpty()) {
        insights.add("Start a focus session to see personalized insights!")
    }
    
    return insights
}

// Premium and Access Control Cards
@Composable
fun LoginPromptCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E88E5).copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, Color(0xFF1E88E5).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Login",
                tint = Color(0xFF1E88E5),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Sign in to unlock full statistics",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Track your progress, set goals, and access detailed analytics",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { /* TODO: Implement login */ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E88E5)
                ),
                shape = RoundedCornerShape(12.dp)
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

@Composable
fun UpgradePromptCard() {
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
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFD700),
                            Color(0xFFFFA000),
                            Color(0xFFFF8F00)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Premium",
                    tint = Color.Black,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Upgrade to Premium",
                    color = Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Unlock advanced analytics, detailed charts, and premium insights",
                    color = Color.Black.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { /* TODO: Implement upgrade */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Upgrade Now",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AdvancedAnalyticsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = "Analytics",
                    tint = Color(0xFF6C63FF),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Advanced Analytics",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFD700)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "PRO",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sample analytics content
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnalyticsMetric(
                    title = "Peak Hours",
                    value = "2-4 PM",
                    trend = "+12%"
                )
                AnalyticsMetric(
                    title = "Efficiency",
                    value = "87%",
                    trend = "+5%"
                )
                AnalyticsMetric(
                    title = "Focus Score",
                    value = "9.2/10",
                    trend = "+0.3"
                )
            }
        }
    }
}

@Composable
fun AnalyticsMetric(
    title: String,
    value: String,
    trend: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
        Text(
            text = trend,
            color = Color(0xFF00E676),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ComparisonChartsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = "Charts",
                    tint = Color(0xFF6C63FF),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Comparison Charts",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Compare your performance across different time periods",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PredictiveInsightsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "AI Insights",
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Insights",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val aiInsights = listOf(
                "Your productivity peaks at 2 PM - schedule important tasks then",
                "You're 23% more focused on Tuesdays and Wednesdays",
                "Taking 5-minute breaks every 25 minutes could boost your efficiency by 15%",
                "Your current streak suggests you'll reach your monthly goal 3 days early"
            )
            
            aiInsights.forEach { insight ->
                InsightItem(insight)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ExportDataCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Export Your Data",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ExportOption(
                    title = "CSV",
                    description = "Spreadsheet format",
                    icon = Icons.Default.TableChart,
                    onClick = { /* TODO: Export CSV */ }
                )
                ExportOption(
                    title = "PDF",
                    description = "Report format",
                    icon = Icons.Default.PictureAsPdf,
                    onClick = { /* TODO: Export PDF */ }
                )
                ExportOption(
                    title = "JSON",
                    description = "Raw data",
                    icon = Icons.Default.Code,
                    onClick = { /* TODO: Export JSON */ }
                )
            }
        }
    }
}

@Composable
fun ExportOption(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    Color(0xFF6C63FF).copy(alpha = 0.2f),
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF6C63FF),
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = description,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}
