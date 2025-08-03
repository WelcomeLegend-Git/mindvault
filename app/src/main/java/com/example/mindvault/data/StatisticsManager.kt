package com.example.mindvault.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.mindvault.utils.DataValidationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

data class FocusSessionRecord(
    val id: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime?,
    val type: String, // "STUDY_TIME" or "REST_TIME"
    val blockedApps: List<String> = emptyList(),
    val distractionCount: Int = 0,
    val isCompleted: Boolean = false
)

data class DailyStats(
    val date: LocalDate,
    val totalFocusTime: Long, // in minutes
    val studyTime: Long, // in minutes
    val restTime: Long, // in minutes
    val completedSessions: Int,
    val totalSessions: Int,
    val distractionCount: Int,
    val productivityScore: Float, // 0-100
    val topBlockedApps: List<String>
)

data class WeeklyStats(
    val weekStart: LocalDate,
    val dailyStats: List<DailyStats>,
    val totalFocusTime: Long,
    val averageDailyFocus: Long,
    val bestDay: LocalDate?,
    val longestStreak: Int,
    val currentStreak: Int,
    val weeklyGoalProgress: Float // 0-100
)

data class UserStats(
    val totalFocusHours: Long,
    val totalSessions: Int,
    val averageSessionLength: Long, // in minutes
    val currentStreak: Int,
    val longestStreak: Int,
    val level: Int,
    val experiencePoints: Int,
    val nextLevelXP: Int,
    val achievements: List<String>,
    val rank: String, // "Beginner", "Focused", "Master", "Zen Master"
    val weeklyGoal: Long, // in minutes
    val monthlyGoal: Long // in minutes
)

object StatisticsManager {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    
    private val _currentSession = MutableStateFlow<FocusSessionRecord?>(null)
    val currentSession = _currentSession.asStateFlow()
    
    private val _dailyStats = MutableStateFlow<DailyStats?>(null)
    val dailyStats = _dailyStats.asStateFlow()
    
    private val _weeklyStats = MutableStateFlow<WeeklyStats?>(null)
    val weeklyStats = _weeklyStats.asStateFlow()
    
    private val _userStats = MutableStateFlow<UserStats?>(null)
    val userStats = _userStats.asStateFlow()
    
    fun init(context: Context) {
        this.context = context.applicationContext
        this.prefs = context.getSharedPreferences("mindvault_stats", Context.MODE_PRIVATE)
        
        // Validate and clean data on initialization
        DataValidationHelper.validateAndCleanAllData(context)
        DataValidationHelper.logDataIntegrityReport(context)
        
        loadStats()
        Log.d("StatisticsManager", "Statistics Manager initialized with data validation")
    }
    
    fun isInitialized(): Boolean {
        return ::context.isInitialized
    }
    
    fun startFocusSession(type: String, blockedApps: List<String>) {
        val session = FocusSessionRecord(
            id = generateSessionId(),
            startTime = LocalDateTime.now(),
            endTime = null,
            type = type,
            blockedApps = blockedApps
        )
        _currentSession.value = session
        saveCurrentSession(session)
        Log.d("StatisticsManager", "Started focus session: $type")
    }
    
    fun endFocusSession(completed: Boolean = true) {
        val session = _currentSession.value ?: return
        val endedSession = session.copy(
            endTime = LocalDateTime.now(),
            isCompleted = completed
        )
        _currentSession.value = null
        saveSessionRecord(endedSession)
        updateDailyStats(endedSession)
        updateUserStats(endedSession)
        clearCurrentSession()
        Log.d("StatisticsManager", "Ended focus session: ${session.type}, completed: $completed")
    }
    
    fun recordDistraction(appPackage: String) {
        val session = _currentSession.value ?: return
        val updatedSession = session.copy(
            distractionCount = session.distractionCount + 1
        )
        _currentSession.value = updatedSession
        saveCurrentSession(updatedSession)
        Log.d("StatisticsManager", "Recorded distraction: $appPackage")
    }
    
    private fun loadStats() {
        loadDailyStats()
        loadWeeklyStats()
        loadUserStats()
        loadCurrentSession()
    }
    
    private fun loadDailyStats() {
        val today = LocalDate.now()
        val dateKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        val totalFocusTime = prefs.getLong("daily_focus_${dateKey}", 0L)
        val studyTime = prefs.getLong("daily_study_${dateKey}", 0L)
        val restTime = prefs.getLong("daily_rest_${dateKey}", 0L)
        val completedSessions = prefs.getInt("daily_completed_${dateKey}", 0)
        val totalSessions = prefs.getInt("daily_total_${dateKey}", 0)
        val distractionCount = prefs.getInt("daily_distractions_${dateKey}", 0)
        val productivityScore = prefs.getFloat("daily_productivity_${dateKey}", 100f)
        
        _dailyStats.value = DailyStats(
            date = today,
            totalFocusTime = totalFocusTime,
            studyTime = studyTime,
            restTime = restTime,
            completedSessions = completedSessions,
            totalSessions = totalSessions,
            distractionCount = distractionCount,
            productivityScore = productivityScore,
            topBlockedApps = getTopBlockedApps(today)
        )
    }
    
    private fun loadWeeklyStats() {
        val today = LocalDate.now()
        val weekStart = today.minusDays(today.dayOfWeek.value - 1L)
        
        val dailyStatsList = mutableListOf<DailyStats>()
        var totalWeeklyFocus = 0L
        
        for (i in 0..6) {
            val date = weekStart.plusDays(i.toLong())
            val dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            
            val dayStats = DailyStats(
                date = date,
                totalFocusTime = prefs.getLong("daily_focus_${dateKey}", 0L),
                studyTime = prefs.getLong("daily_study_${dateKey}", 0L),
                restTime = prefs.getLong("daily_rest_${dateKey}", 0L),
                completedSessions = prefs.getInt("daily_completed_${dateKey}", 0),
                totalSessions = prefs.getInt("daily_total_${dateKey}", 0),
                distractionCount = prefs.getInt("daily_distractions_${dateKey}", 0),
                productivityScore = prefs.getFloat("daily_productivity_${dateKey}", 100f),
                topBlockedApps = getTopBlockedApps(date)
            )
            dailyStatsList.add(dayStats)
            totalWeeklyFocus += dayStats.totalFocusTime
        }
        
        val currentStreak = calculateCurrentStreak()
        val longestStreak = prefs.getInt("longest_streak", 0)
        val weeklyGoal = prefs.getLong("weekly_goal", 1200L) // 20 hours default
        
        _weeklyStats.value = WeeklyStats(
            weekStart = weekStart,
            dailyStats = dailyStatsList,
            totalFocusTime = totalWeeklyFocus,
            averageDailyFocus = if (dailyStatsList.isNotEmpty()) totalWeeklyFocus / dailyStatsList.size else 0L,
            bestDay = findBestDay(dailyStatsList),
            longestStreak = longestStreak,
            currentStreak = currentStreak,
            weeklyGoalProgress = if (weeklyGoal > 0) (totalWeeklyFocus.toFloat() / weeklyGoal * 100).coerceAtMost(100f) else 0f
        )
    }
    
    private fun loadUserStats() {
        val totalHours = prefs.getLong("total_focus_hours", 0L)
        val totalSessions = prefs.getInt("total_sessions", 0)
        val avgSessionLength = if (totalSessions > 0) totalHours * 60 / totalSessions else 0L
        val currentStreak = calculateCurrentStreak()
        val longestStreak = prefs.getInt("longest_streak", 0)
        val xp = prefs.getInt("experience_points", 0)
        val level = calculateLevel(xp)
        
        _userStats.value = UserStats(
            totalFocusHours = totalHours,
            totalSessions = totalSessions,
            averageSessionLength = avgSessionLength,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            level = level,
            experiencePoints = xp,
            nextLevelXP = calculateNextLevelXP(level),
            achievements = loadAchievements(),
            rank = calculateRank(level),
            weeklyGoal = prefs.getLong("weekly_goal", 1200L),
            monthlyGoal = prefs.getLong("monthly_goal", 5000L)
        )
    }
    
    private fun loadCurrentSession() {
        val sessionJson = prefs.getString("current_session", null)
        if (sessionJson != null) {
            // Parse JSON and restore session (simplified for this example)
            // In a real app, you'd use proper JSON parsing
        }
    }
    
    private fun updateDailyStats(session: FocusSessionRecord) {
        val today = LocalDate.now()
        val dateKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        val sessionDuration = if (session.endTime != null) {
            ChronoUnit.MINUTES.between(session.startTime, session.endTime)
        } else 0L
        
        // Validate session data before updating
        val isValid = DataValidationHelper.validateSessionRecord(
            session.startTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            session.endTime?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
            sessionDuration,
            session.distractionCount
        )
        
        if (!isValid) {
            Log.w("StatisticsManager", "Invalid session data, skipping update")
            return
        }
        
        // Validate session duration
        if (sessionDuration <= 0) {
            Log.w("StatisticsManager", "Invalid session duration: $sessionDuration minutes")
            return
        }
        
        val editor = prefs.edit()
        
        // Update totals
        val currentFocus = prefs.getLong("daily_focus_${dateKey}", 0L)
        editor.putLong("daily_focus_${dateKey}", currentFocus + sessionDuration)
        
        when (session.type) {
            "STUDY_TIME" -> {
                val currentStudy = prefs.getLong("daily_study_${dateKey}", 0L)
                editor.putLong("daily_study_${dateKey}", currentStudy + sessionDuration)
            }
            "REST_TIME" -> {
                val currentRest = prefs.getLong("daily_rest_${dateKey}", 0L)
                editor.putLong("daily_rest_${dateKey}", currentRest + sessionDuration)
            }
        }
        
        // Update session counts
        val totalSessions = prefs.getInt("daily_total_${dateKey}", 0)
        editor.putInt("daily_total_${dateKey}", totalSessions + 1)
        
        if (session.isCompleted) {
            val completedSessions = prefs.getInt("daily_completed_${dateKey}", 0)
            editor.putInt("daily_completed_${dateKey}", completedSessions + 1)
        }
        
        // Update distractions
        val currentDistractions = prefs.getInt("daily_distractions_${dateKey}", 0)
        editor.putInt("daily_distractions_${dateKey}", currentDistractions + session.distractionCount)
        
        // Calculate productivity score
        val completionRate = if (totalSessions + 1 > 0) {
            (prefs.getInt("daily_completed_${dateKey}", 0) + if (session.isCompleted) 1 else 0).toFloat() / (totalSessions + 1)
        } else 1f
        
        val distractionPenalty = (session.distractionCount * 5).coerceAtMost(30)
        val productivityScore = ((completionRate * 100) - distractionPenalty).coerceAtLeast(0f)
        editor.putFloat("daily_productivity_${dateKey}", productivityScore)
        
        editor.apply()
        loadDailyStats()
    }
    
    private fun updateUserStats(session: FocusSessionRecord) {
        val sessionDuration = if (session.endTime != null) {
            ChronoUnit.MINUTES.between(session.startTime, session.endTime)
        } else 0L
        
        // Validate session duration
        if (sessionDuration <= 0) {
            Log.w("StatisticsManager", "Invalid session duration for user stats: $sessionDuration minutes")
            return
        }
        
        val editor = prefs.edit()
        
        // Update total hours
        val currentHours = prefs.getLong("total_focus_hours", 0L)
        editor.putLong("total_focus_hours", currentHours + (sessionDuration / 60))
        
        // Update total sessions
        val totalSessions = prefs.getInt("total_sessions", 0)
        editor.putInt("total_sessions", totalSessions + 1)
        
        // Update XP
        val xpGained = calculateXPGained(session, sessionDuration)
        val currentXP = prefs.getInt("experience_points", 0)
        editor.putInt("experience_points", currentXP + xpGained)
        
        // Update streaks
        updateStreaks(session.isCompleted)
        
        editor.apply()
        loadUserStats()
    }
    
    private fun calculateXPGained(session: FocusSessionRecord, duration: Long): Int {
        var xp = (duration / 5).toInt() // 1 XP per 5 minutes
        
        if (session.isCompleted) xp = (xp * 1.5).toInt()
        if (session.distractionCount == 0) xp = (xp * 1.2).toInt()
        if (duration >= 60) xp += 50 // Bonus for hour+ sessions
        
        return xp
    }
    
    private fun calculateLevel(xp: Int): Int {
        return (xp / 1000) + 1 // 1000 XP per level
    }
    
    private fun calculateNextLevelXP(level: Int): Int {
        return level * 1000
    }
    
    private fun calculateRank(level: Int): String {
        return when {
            level < 5 -> "Beginner"
            level < 15 -> "Focused"
            level < 30 -> "Master"
            else -> "Zen Master"
        }
    }
    
    private fun calculateCurrentStreak(): Int {
        var streak = 0
        var date = LocalDate.now()
        
        while (true) {
            val dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val dailyFocus = prefs.getLong("daily_focus_${dateKey}", 0L)
            
            if (dailyFocus >= 30) { // At least 30 minutes to count as a streak day
                streak++
                date = date.minusDays(1)
            } else {
                break
            }
        }
        
        return streak
    }
    
    private fun updateStreaks(sessionCompleted: Boolean) {
        if (!sessionCompleted) return
        
        val currentStreak = calculateCurrentStreak()
        val longestStreak = prefs.getInt("longest_streak", 0)
        
        if (currentStreak > longestStreak) {
            prefs.edit().putInt("longest_streak", currentStreak).apply()
        }
    }
    
    private fun getTopBlockedApps(date: LocalDate): List<String> {
        // This would typically query a database of blocked app interactions
        // For now, return empty list to avoid dummy data
        return emptyList()
    }
    
    private fun findBestDay(dailyStats: List<DailyStats>): LocalDate? {
        return dailyStats.maxByOrNull { it.totalFocusTime }?.date
    }
    
    private fun loadAchievements(): List<String> {
        val achievementsString = prefs.getString("achievements", "")
        return if (achievementsString.isNullOrEmpty()) {
            emptyList()
        } else {
            achievementsString.split(",").filter { it.isNotEmpty() }
        }
    }
    
    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}"
    }
    
    private fun saveCurrentSession(session: FocusSessionRecord) {
        // Save current session to preferences (simplified)
        prefs.edit().putString("current_session", session.id).apply()
    }
    
    private fun saveSessionRecord(session: FocusSessionRecord) {
        // Persist basic info so UsageStatsHelper can compute screen time during focus sessions
        val dateKey = session.startTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val prefsKey = "sessions_${dateKey}"
        val existing = prefs.getString(prefsKey, null)
        val jsonArray = if (existing.isNullOrEmpty()) org.json.JSONArray() else org.json.JSONArray(existing)

        // Only store if session has valid end time
        if (session.endTime != null) {
            val obj = org.json.JSONObject()
            obj.put("start", session.startTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
            obj.put("end", session.endTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
            jsonArray.put(obj)
            prefs.edit().putString(prefsKey, jsonArray.toString()).apply()
        }
        Log.d("StatisticsManager", "Saved session record: ${session.id}")
    }
    
    private fun clearCurrentSession() {
        prefs.edit().remove("current_session").apply()
    }
    
    // Public methods for UI
    fun getTodayFocusTime(): Long {
        return _dailyStats.value?.totalFocusTime ?: 0L
    }
    
    fun getWeeklyProgress(): Float {
        return _weeklyStats.value?.weeklyGoalProgress ?: 0f
    }
    
    fun getCurrentLevel(): Int {
        return _userStats.value?.level ?: 1
    }
    
    fun getProductivityScore(): Float {
        return _dailyStats.value?.productivityScore ?: 100f
    }

    /**
     * Returns true if the user accumulated at least 30 minutes of focus time on the given date.
     * This is used by the streak calendar UI.
     */
    fun hadFocusOn(date: java.time.LocalDate): Boolean {
        val dateKey = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        val minutes = prefs.getLong("daily_focus_${dateKey}", 0L)
        return minutes >= 30L
    }
    
    /**
     * Clear all statistics data for testing/reset purposes
     */
    fun clearAllStats() {
        prefs.edit().clear().apply()
        loadStats()
        Log.d("StatisticsManager", "All statistics cleared")
    }
    
    /**
     * Validate and clean up any corrupted data
     */
    fun validateAndCleanData() {
        val today = LocalDate.now()
        val dateKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        // Check for negative values and reset them
        val focusTime = prefs.getLong("daily_focus_${dateKey}", 0L)
        if (focusTime < 0) {
            prefs.edit().putLong("daily_focus_${dateKey}", 0L).apply()
            Log.w("StatisticsManager", "Fixed negative focus time")
        }
        
        // Check for unrealistic values and cap them
        if (focusTime > 1440) { // More than 24 hours in a day
            prefs.edit().putLong("daily_focus_${dateKey}", 1440L).apply()
            Log.w("StatisticsManager", "Capped unrealistic focus time")
        }
        
        loadStats()
    }
}
