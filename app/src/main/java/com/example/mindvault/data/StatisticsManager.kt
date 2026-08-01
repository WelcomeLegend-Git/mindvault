package com.example.mindvault.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import com.example.mindvault.MindVaultApplication
import kotlinx.coroutines.launch
import com.example.mindvault.data.AuthManager

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
    val totalFocusMinutes: Long, // precise total in minutes (source of truth)
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
        migrateHoursToMinutes()
        loadStats()
        Log.d("StatisticsManager", "Statistics Manager initialized")
    }

    /**
     * One-time migration: convert old "total_focus_hours" (Long hours) to
     * "total_focus_minutes" (Long minutes) so sub-hour sessions are no longer lost.
     */
    private fun migrateHoursToMinutes() {
        if (prefs.contains("total_focus_hours") && !prefs.contains("total_focus_minutes")) {
            val oldHours = prefs.getLong("total_focus_hours", 0L)
            val migratedMinutes = oldHours * 60
            prefs.edit()
                .putLong("total_focus_minutes", migratedMinutes)
                .remove("total_focus_hours")
                .apply()
            Log.d("StatisticsManager", "Migrated total_focus_hours ($oldHours h) → total_focus_minutes ($migratedMinutes min)")
        }
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
        MindVaultApplication.instance.applicationScope.launch {
            AuthManager.syncUserDataToCloud()
        }
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
        
        val totalFocusTime = prefs.getLong("daily_focus_${dateKey}", 0L).coerceAtLeast(0L)
        val studyTime = prefs.getLong("daily_study_${dateKey}", 0L).coerceAtLeast(0L)
        val restTime = prefs.getLong("daily_rest_${dateKey}", 0L).coerceAtLeast(0L)
        val completedSessions = prefs.getInt("daily_completed_${dateKey}", 0).coerceAtLeast(0)
        val totalSessions = prefs.getInt("daily_total_${dateKey}", 0).coerceAtLeast(0)
        val distractionCount = prefs.getInt("daily_distractions_${dateKey}", 0).coerceAtLeast(0)
        
        // Ensure data consistency
        val validatedStudyTime = studyTime.coerceAtMost(totalFocusTime)
        val validatedRestTime = restTime.coerceAtMost(totalFocusTime - validatedStudyTime)
        val validatedCompletedSessions = completedSessions.coerceAtMost(totalSessions)
        
        _dailyStats.value = DailyStats(
            date = today,
            totalFocusTime = totalFocusTime,
            studyTime = validatedStudyTime,
            restTime = validatedRestTime,
            completedSessions = validatedCompletedSessions,
            totalSessions = totalSessions,
            distractionCount = distractionCount,
            productivityScore = 0f,
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
                productivityScore = 0f,
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
            averageDailyFocus = totalWeeklyFocus / (today.dayOfWeek.value).coerceAtLeast(1),
            bestDay = findBestDay(dailyStatsList),
            longestStreak = longestStreak,
            currentStreak = currentStreak,
            weeklyGoalProgress = (totalWeeklyFocus.toFloat() / weeklyGoal * 100).coerceAtMost(100f)
        )
    }
    
    private fun loadUserStats() {
        val totalMinutes = prefs.getLong("total_focus_minutes", 0L)
        val totalHours = totalMinutes / 60
        val totalSessions = prefs.getInt("total_sessions", 0)
        val avgSessionLength = if (totalSessions > 0) totalMinutes / totalSessions else 0L
        val currentStreak = calculateCurrentStreak()
        val longestStreak = prefs.getInt("longest_streak", 0)
        val totalXp = prefs.getInt("experience_points", 0)
        val (level, xpInLevel, xpForNextLevel) = calculateLevelAndProgress(totalXp)

        _userStats.value = UserStats(
            totalFocusHours = totalHours,
            totalFocusMinutes = totalMinutes,
            totalSessions = totalSessions,
            averageSessionLength = avgSessionLength,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            level = level,
            experiencePoints = xpInLevel,
            nextLevelXP = xpForNextLevel,
            achievements = loadAchievements(),
            rank = calculateRank(level),
            weeklyGoal = prefs.getLong("weekly_goal", 1200L),
            monthlyGoal = prefs.getLong("monthly_goal", 5000L)
        )
    }
    
    private fun loadCurrentSession() {
        val sessionJson = prefs.getString("current_session", null)
        if (sessionJson != null) {
            try {
                val obj = org.json.JSONObject(sessionJson)
                val session = FocusSessionRecord(
                    id = obj.getString("id"),
                    startTime = LocalDateTime.parse(obj.getString("startTime")),
                    endTime = null,
                    type = obj.getString("type"),
                    blockedApps = mutableListOf<String>().apply {
                        val arr = obj.optJSONArray("blockedApps")
                        if (arr != null) {
                            for (i in 0 until arr.length()) add(arr.getString(i))
                        }
                    },
                    distractionCount = obj.optInt("distractionCount", 0),
                    isCompleted = false
                )
                // Recover the orphaned session — end it now so the time isn't lost
                val recovered = session.copy(
                    endTime = LocalDateTime.now(),
                    isCompleted = true
                )
                Log.d("StatisticsManager", "Recovering orphaned session ${recovered.id} from ${recovered.startTime}")
                saveSessionRecord(recovered)
                updateDailyStats(recovered)
                updateUserStats(recovered)
                clearCurrentSession()
            } catch (e: Exception) {
                Log.e("StatisticsManager", "Failed to restore session, clearing", e)
                clearCurrentSession()
            }
        }
    }
    
    private fun updateDailyStats(session: FocusSessionRecord) {
        val today = LocalDate.now()
        val dateKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        val sessionDuration = if (session.endTime != null) {
            ChronoUnit.MINUTES.between(session.startTime, session.endTime).coerceAtLeast(0L)
        } else 0L
        
        val editor = prefs.edit()
        
        // Update totals with validation
        val currentFocus = prefs.getLong("daily_focus_${dateKey}", 0L)
        val newFocusTotal = (currentFocus + sessionDuration).coerceAtMost(1440L) // Max 24 hours per day
        editor.putLong("daily_focus_${dateKey}", newFocusTotal)
        
        when (session.type) {
            "STUDY_TIME" -> {
                val currentStudy = prefs.getLong("daily_study_${dateKey}", 0L)
                val newStudyTotal = (currentStudy + sessionDuration).coerceAtMost(newFocusTotal)
                editor.putLong("daily_study_${dateKey}", newStudyTotal)
            }
            "REST_TIME" -> {
                val currentRest = prefs.getLong("daily_rest_${dateKey}", 0L)
                val newRestTotal = (currentRest + sessionDuration).coerceAtMost(newFocusTotal)
                editor.putLong("daily_rest_${dateKey}", newRestTotal)
            }
        }
        
        // Update session counts
        val totalSessions = prefs.getInt("daily_total_${dateKey}", 0)
        editor.putInt("daily_total_${dateKey}", totalSessions + 1)
        
        if (session.isCompleted) {
            val completedSessions = prefs.getInt("daily_completed_${dateKey}", 0)
            editor.putInt("daily_completed_${dateKey}", completedSessions + 1)
        }
        
        // Update distractions with validation
        val currentDistractions = prefs.getInt("daily_distractions_${dateKey}", 0)
        val validatedDistractions = session.distractionCount.coerceAtLeast(0)
        val newDistractionTotal = currentDistractions + validatedDistractions
        editor.putInt("daily_distractions_${dateKey}", newDistractionTotal)
        
        editor.apply()
        loadDailyStats()
        MindVaultApplication.instance.applicationScope.launch {
            AuthManager.syncUserDataToCloud()
        }
    }
    
    private fun updateUserStats(session: FocusSessionRecord) {
        val sessionDuration = if (session.endTime != null) {
            ChronoUnit.MINUTES.between(session.startTime, session.endTime).coerceAtLeast(0L)
        } else 0L
        
        val editor = prefs.edit()
        
        // Update total focus minutes (precise, no rounding loss)
        val currentMinutes = prefs.getLong("total_focus_minutes", 0L)
        val newTotalMinutes = (currentMinutes + sessionDuration).coerceAtMost(6_000_000L) // Reasonable max
        editor.putLong("total_focus_minutes", newTotalMinutes)
        
        // Update total sessions with validation
        val totalSessions = prefs.getInt("total_sessions", 0).coerceAtLeast(0)
        val newTotalSessions = (totalSessions + 1).coerceAtMost(50000) // Reasonable max
        editor.putInt("total_sessions", newTotalSessions)
        
        // Update XP with improved calculation
        val xpGained = calculateXPGained(session, sessionDuration)
        val currentXP = prefs.getInt("experience_points", 0).coerceAtLeast(0)
        val newXP = (currentXP + xpGained).coerceAtMost(1000000) // Reasonable max
        editor.putInt("experience_points", newXP)
        
        // Update streaks only for completed sessions
        if (session.isCompleted) {
            updateStreaks(true)
        }
        
        editor.apply()
        loadUserStats()
        MindVaultApplication.instance.applicationScope.launch {
            AuthManager.syncUserDataToCloud()
        }
    }
    
    private fun calculateXPGained(session: FocusSessionRecord, duration: Long): Int {
        if (duration <= 0) return 0
        
        // Base XP: 1 XP per minute, capped at reasonable amount
        var xp = duration.toInt().coerceAtMost(480) // Max 8 hours worth of base XP
        
        // Completion bonus: significant reward for finishing sessions
        if (session.isCompleted) {
            xp = (xp * 1.5).toInt()
        }
        
        // Focus quality bonus: reward for maintaining focus (low distractions)
        when (session.distractionCount) {
            0 -> xp = (xp * 1.3).toInt() // 30% bonus for perfect focus
            1 -> xp = (xp * 1.15).toInt() // 15% bonus for excellent focus
            2 -> xp = (xp * 1.05).toInt() // 5% bonus for good focus
            // No bonus for 3+ distractions
        }
        
        // Duration milestone bonuses
        when {
            duration >= 240 -> xp += 100 // 4+ hour milestone
            duration >= 120 -> xp += 50  // 2+ hour milestone
            duration >= 60 -> xp += 25   // 1+ hour milestone
            duration >= 30 -> xp += 10   // 30+ minute milestone
        }
        
        // Session type modifier
        if (session.type == "STUDY_TIME") {
            xp = (xp * 1.1).toInt() // Slight bonus for study sessions
        }
        
        return xp.coerceIn(1, 1000) // Ensure reasonable XP range
    }

    private fun calculateLevelAndProgress(totalXp: Int): Triple<Int, Int, Int> {
        var level = 1
        var xpRemaining = totalXp.coerceAtLeast(0)
        var xpForNext = 1000

        while (xpRemaining >= xpForNext && level < 100) {
            xpRemaining -= xpForNext
            level++
            xpForNext = (xpForNext * 1.2f).roundToInt().coerceAtMost(50000)
        }

        return Triple(level, xpRemaining, xpForNext)
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
        
        // If today has no focus yet, allow the streak to continue from yesterday
        // (the day isn't over yet). But if yesterday also has no focus, streak is 0.
        if (!hadFocusOn(date)) {
            date = date.minusDays(1)
            if (!hadFocusOn(date)) {
                return 0
            }
        }
        
        // Count consecutive days backward from `date`
        while (hadFocusOn(date)) {
            streak++
            date = date.minusDays(1)
        }
        
        return streak
    }
    
    private fun updateStreaks(sessionCompleted: Boolean) {
        if (!sessionCompleted) return
        
        val currentStreak = calculateCurrentStreak()
        val longestStreak = prefs.getInt("longest_streak", 0)
        
        if (currentStreak > longestStreak) {
            prefs.edit().putInt("longest_streak", currentStreak).apply()
            MindVaultApplication.instance.applicationScope.launch {
                AuthManager.syncUserDataToCloud()
            }
        }
    }
    
    private fun getTopBlockedApps(date: LocalDate): List<String> {
        // Get blocked apps from actual sessions for this date
        val dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val sessionsJson = prefs.getString("sessions_${dateKey}", null)
        
        if (sessionsJson.isNullOrEmpty()) {
            return emptyList()
        }
        
        try {
            val jsonArray = org.json.JSONArray(sessionsJson)
            val blockedApps = mutableMapOf<String, Int>()
            
            for (i in 0 until jsonArray.length()) {
                val session = jsonArray.optJSONObject(i)
                session?.optJSONArray("blockedApps")?.let { apps ->
                    for (j in 0 until apps.length()) {
                        val app = apps.optString(j)
                        if (app.isNotEmpty()) {
                            blockedApps[app] = blockedApps.getOrDefault(app, 0) + 1
                        }
                    }
                }
            }
            
            return blockedApps.toList()
                .sortedByDescending { it.second }
                .take(5)
                .map { it.first }
        } catch (e: Exception) {
            Log.e("StatisticsManager", "Error parsing blocked apps data", e)
            return emptyList()
        }
    }
    
    private fun findBestDay(dailyStats: List<DailyStats>): LocalDate? {
        return dailyStats.maxByOrNull { it.totalFocusTime }?.date
    }
    
    private fun loadAchievements(): List<String> {
        val achievementsString = prefs.getString("achievements", "")
        return if (achievementsString.isNullOrEmpty()) {
            emptyList()
        } else {
            achievementsString.split(",")
        }
    }

    // After achievements are updated, sync as well
    private fun saveAchievements(achievements: List<String>) {
        prefs.edit().putString("achievements", achievements.joinToString(",")).apply()
        // Trigger background sync but also enqueue a WorkManager retry in case we are offline
        MindVaultApplication.instance.applicationScope.launch {
            val success = AuthManager.syncUserDataToCloud()
            if (!success) {
                try {
                    val wm = androidx.work.WorkManager.getInstance(MindVaultApplication.instance)
                    val req = androidx.work.OneTimeWorkRequestBuilder<com.example.mindvault.data.BackupSyncWorker>()
                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                    wm.enqueue(req)
                } catch (_: Exception) { }
            }
        }
    }
    
    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}"
    }
    
    private fun saveCurrentSession(session: FocusSessionRecord) {
        try {
            val obj = org.json.JSONObject()
            obj.put("id", session.id)
            obj.put("startTime", session.startTime.toString())
            obj.put("type", session.type)
            obj.put("distractionCount", session.distractionCount)
            val blockedAppsArray = org.json.JSONArray()
            session.blockedApps.forEach { blockedAppsArray.put(it) }
            obj.put("blockedApps", blockedAppsArray)
            prefs.edit().putString("current_session", obj.toString()).apply()
        } catch (e: Exception) {
            Log.e("StatisticsManager", "Failed to save current session", e)
        }
    }
    
    private fun saveSessionRecord(session: FocusSessionRecord) {
        // Persist session info including blocked apps for analytics
        val dateKey = session.startTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val prefsKey = "sessions_${dateKey}"
        val existing = prefs.getString(prefsKey, null)
        val jsonArray = if (existing.isNullOrEmpty()) org.json.JSONArray() else org.json.JSONArray(existing)

        // Only store if session has valid end time
        if (session.endTime != null) {
            val obj = org.json.JSONObject()
            obj.put("id", session.id)
            obj.put("start", session.startTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
            obj.put("end", session.endTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
            obj.put("type", session.type)
            obj.put("isCompleted", session.isCompleted)
            obj.put("distractionCount", session.distractionCount)
            
            // Store blocked apps array
            val blockedAppsArray = org.json.JSONArray()
            session.blockedApps.forEach { app ->
                blockedAppsArray.put(app)
            }
            obj.put("blockedApps", blockedAppsArray)
            
            jsonArray.put(obj)
            prefs.edit().putString(prefsKey, jsonArray.toString()).apply()
        }
        Log.d("StatisticsManager", "Saved session record with ${session.blockedApps.size} blocked apps: ${session.id}")
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
        return 0f
    }

    /**
     * Returns true if the user accumulated at least 30 minutes of focus time on the given date.
     * This is used by the streak calendar UI.
     */
    fun hadFocusOn(date: java.time.LocalDate): Boolean {
        val dateKey = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        // Correct preference key interpolation
        val minutes = prefs.getLong("daily_focus_${dateKey}", 0L)
        return minutes >= 30L
    }
}
