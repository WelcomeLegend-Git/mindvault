package com.example.mindvault.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

    @Volatile
    private var initialized = false
    private var lastCheckpointElapsed = 0L

    private val _currentSession = MutableStateFlow<FocusSessionRecord?>(null)
    val currentSession = _currentSession.asStateFlow()

    private val _dailyStats = MutableStateFlow<DailyStats?>(null)
    val dailyStats = _dailyStats.asStateFlow()

    private val _weeklyStats = MutableStateFlow<WeeklyStats?>(null)
    val weeklyStats = _weeklyStats.asStateFlow()

    private val _userStats = MutableStateFlow<UserStats?>(null)
    val userStats = _userStats.asStateFlow()

    fun init(context: Context) {
        if (initialized) return
        initialize(context)
    }

    @Synchronized
    private fun initialize(context: Context) {
        if (initialized) return
        LocalRestore.checkWritable()
        this.context = context.applicationContext
        this.prefs = context.getSharedPreferences("mindvault_stats", Context.MODE_PRIVATE)
        migrateHoursToMinutes()
        loadStats()
        initialized = true
        Log.d("StatisticsManager", "Statistics Manager initialized")
    }

    @Synchronized
    internal fun reloadAfterRestore() {
        LocalRestore.checkWritable()
        check(_currentSession.value == null) { "Cannot reload statistics during focus" }
        lastCheckpointElapsed = 0L
        migrateHoursToMinutes()
        loadStats()
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
            Log.d(
                "StatisticsManager",
                "Migrated total_focus_hours ($oldHours h) → total_focus_minutes ($migratedMinutes min)"
            )
        }
    }

    fun isInitialized(): Boolean {
        return initialized
    }

    @Synchronized
    fun startFocusSession(type: String, blockedApps: List<String>) {
        if (!ManagerPersistence.writable()) return
        check(initialized) { "StatisticsManager must be initialized before starting a session" }
        // A duplicate start must not discard an active session or reset its checkpoint.
        if (_currentSession.value != null) return
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

    @Synchronized
    fun endFocusSession(completed: Boolean = true) {
        if (!ManagerPersistence.writable()) return
        val session = _currentSession.value ?: return
        val endedSession = session.copy(
            endTime = LocalDateTime.now().coerceAtLeast(session.startTime),
            isCompleted = completed
        )
        finalizeSession(endedSession)
        _currentSession.value = null
        refreshStats()
        Log.d("StatisticsManager", "Ended focus session: ${session.type}, completed: $completed")
        MindVaultApplication.instance.applicationScope.launch {
            AuthManager.syncUserDataToCloud()
        }
    }

    /** Account switches during a long-running session must taint its provenance even before
     * the next accounting checkpoint. This does not change session timing or focus behavior. */
    @Synchronized
    internal fun observeActivePrincipal() {
        if (!ManagerPersistence.writable() || !initialized || _currentSession.value == null) return
        try {
            val token = prefs.getString(DeviceDataOwnership.KEY, null)
            if (token == DeviceDataOwnership.UNKNOWN || token == DeviceDataOwnership.UNOWNED) return
            ManagerPersistence.observe(context, prefs)
        } catch (failure: Exception) {
            // A provenance/storage failure must fail cloud persistence closed, not terminate
            // the focus monitor that enforces the already-published schedule.
            LocalRestore.blockUntilRestart()
            Log.e("StatisticsManager", "Provenance unavailable; persistence quarantined", failure)
        }
    }

    /** Writes are throttled to 30 seconds using a monotonic clock. Process recovery credits
     * only through the last persisted observation; this method does not reconcile slots. */
    @Synchronized
    fun checkpointActiveSession() {
        if (!ManagerPersistence.writable()) return
        val session = _currentSession.value ?: return
        if (android.os.SystemClock.elapsedRealtime() - lastCheckpointElapsed < 30_000L) return
        saveCurrentSession(session)
    }

    @Synchronized
    fun recordDistraction(appPackage: String) {
        if (!ManagerPersistence.writable()) return
        val session = _currentSession.value ?: return
        val updatedSession = session.copy(
            distractionCount = session.distractionCount + 1
        )
        _currentSession.value = updatedSession
        saveCurrentSession(updatedSession)
        Log.d("StatisticsManager", "Recorded distraction: $appPackage")
    }

    private fun loadStats() {
        loadCurrentSession()
        refreshStats()
    }

    private fun refreshStats() {
        val today = LocalDate.now()
        loadDailyStats(today)
        loadWeeklyStats(today)
        loadUserStats(today)
    }

    private fun loadDailyStats(today: LocalDate) {
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

    private fun loadWeeklyStats(today: LocalDate) {
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

        val currentStreak = calculateCurrentStreak(today)
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

    private fun loadUserStats(today: LocalDate) {
        val totalMinutes = prefs.getLong("total_focus_minutes", 0L)
        val totalHours = totalMinutes / 60
        val totalSessions = prefs.getInt("total_sessions", 0)
        val avgSessionLength = if (totalSessions > 0) totalMinutes / totalSessions else 0L
        val currentStreak = calculateCurrentStreak(today)
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
        if (_currentSession.value != null) return
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
                val lastObserved = runCatching {
                    LocalDateTime.parse(obj.optString("lastObservedTime", ""))
                }.getOrNull()
                // Restart time is not evidence that focus continued while the process was dead.
                val recovered = session.copy(
                    endTime = SessionAccounting.recoveryEnd(
                        session.startTime, lastObserved, LocalDateTime.now()
                    ),
                    isCompleted = false
                )
                Log.d("StatisticsManager", "Recovering orphaned session ${recovered.id} from ${recovered.startTime}")
                finalizeSession(recovered, attributeNewActivity = false)
                MindVaultApplication.instance.applicationScope.launch {
                    AuthManager.syncUserDataToCloud()
                }
            } catch (e: Exception) {
                Log.e("StatisticsManager", "Failed to restore session, clearing", e)
                clearCurrentSession()
            }
        }
    }

    private fun updateDailyStats(session: FocusSessionRecord, editor: SharedPreferences.Editor) {
        val allocations = SessionAccounting.split(
            session.startTime, session.endTime, session.isCompleted, session.distractionCount
        )
        for (day in allocations) {
            val dateKey = day.date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val currentFocus = prefs.getLong("daily_focus_${dateKey}", 0L)
            // Do not cap one side of the daily/lifetime ledger independently.
            val newFocusTotal = currentFocus.coerceAtLeast(0L) + day.minutes
            editor.putLong("daily_focus_${dateKey}", newFocusTotal)

            when (session.type) {
                "STUDY_TIME" -> {
                    val currentStudy = prefs.getLong("daily_study_${dateKey}", 0L)
                    editor.putLong("daily_study_${dateKey}", (currentStudy + day.minutes).coerceIn(0L, newFocusTotal))
                }

                "REST_TIME" -> {
                    val currentRest = prefs.getLong("daily_rest_${dateKey}", 0L)
                    editor.putLong("daily_rest_${dateKey}", (currentRest + day.minutes).coerceIn(0L, newFocusTotal))
                }
            }

            if (day.totalSessions > 0) {
                val totalSessions = prefs.getInt("daily_total_${dateKey}", 0)
                editor.putInt("daily_total_${dateKey}", totalSessions + day.totalSessions)
                val completedSessions = prefs.getInt("daily_completed_${dateKey}", 0)
                editor.putInt("daily_completed_${dateKey}", completedSessions + day.completedSessions)
                val distractions = prefs.getInt("daily_distractions_${dateKey}", 0)
                editor.putInt("daily_distractions_${dateKey}", distractions + day.distractions)
            }
        }
    }

    private fun updateUserStats(session: FocusSessionRecord, editor: SharedPreferences.Editor) {
        val sessionDuration = SessionAccounting.totalMinutes(session.startTime, session.endTime)

        // Use the same whole-session rounding as the sum of daily allocations.
        val currentMinutes = prefs.getLong("total_focus_minutes", 0L).coerceAtLeast(0L)
        val newTotalMinutes = currentMinutes + sessionDuration
        editor.putLong("total_focus_minutes", newTotalMinutes)

        // Update total sessions with validation
        val totalSessions = prefs.getInt("total_sessions", 0).coerceAtLeast(0)
        val newTotalSessions = totalSessions + 1
        editor.putInt("total_sessions", newTotalSessions)

        // Update XP with improved calculation
        val xpGained = calculateXPGained(session, sessionDuration)
        val currentXP = prefs.getInt("experience_points", 0).coerceAtLeast(0)
        val newXP = (currentXP + xpGained).coerceAtMost(1000000) // Reasonable max
        editor.putInt("experience_points", newXP)


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

    private fun calculateCurrentStreak(today: LocalDate = LocalDate.now()): Int {
        var streak = 0
        var date = today

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
    @Synchronized
    private fun saveAchievements(achievements: List<String>) {
        if (!ManagerPersistence.writable()) return
        ManagerPersistence.attribute(context, prefs, prefs.edit())
            .putString("achievements", achievements.joinToString(",")).apply()
        // Trigger background sync but also enqueue a WorkManager retry in case we are offline
        MindVaultApplication.instance.applicationScope.launch {
            val outcome = AuthManager.backupToCloud()
            if (outcome == CloudBackupResult.RETRY) {
                try {
                    AuthManager.enqueueBackupRetry()
                } catch (_: Exception) {
                }
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
            // Event checkpoint only; do not infer activity beyond the last persisted observation.
            obj.put("lastObservedTime", LocalDateTime.now().toString())
            obj.put("type", session.type)
            obj.put("distractionCount", session.distractionCount)
            val blockedAppsArray = org.json.JSONArray()
            session.blockedApps.forEach { blockedAppsArray.put(it) }
            obj.put("blockedApps", blockedAppsArray)
            ManagerPersistence.attribute(context, prefs, prefs.edit())
                .putString("current_session", obj.toString()).apply()
            lastCheckpointElapsed = android.os.SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            Log.e("StatisticsManager", "Failed to save current session", e)
        }
    }

    private fun saveSessionRecord(session: FocusSessionRecord, editor: SharedPreferences.Editor) {
        // Records, counts and untimestamped distractions are indexed by start date.
        // getTopBlockedApps intentionally follows that convention, not each time slice.
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
            editor.putString(prefsKey, jsonArray.toString())
        }
        Log.d("StatisticsManager", "Saved session record with ${session.blockedApps.size} blocked apps: ${session.id}")
    }

    private fun finalizeSession(session: FocusSessionRecord, attributeNewActivity: Boolean = true) {
        // Keep the ledger and removal of its recovery checkpoint in one atomic prefs
        // update: a process restart sees either the old checkpoint or the final ledger.
        val editor = prefs.edit()
        if (attributeNewActivity) ManagerPersistence.attribute(context, prefs, editor)
        saveSessionRecord(session, editor)
        updateDailyStats(session, editor)
        updateUserStats(session, editor)
        editor.remove("current_session").apply()
        if (session.isCompleted) updateStreaks(true)
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
        val daily = _dailyStats.value ?: return 0f
        val user = _userStats.value

        // Focus time component (0-40 points): 240 min (4h) = max
        val focusScore = (daily.totalFocusTime.toFloat() / 240f * 40f).coerceIn(0f, 40f)

        // Session completion component (0-25 points)
        val completionScore = if (daily.totalSessions > 0) {
            (daily.completedSessions.toFloat() / daily.totalSessions.toFloat() * 25f)
        } else 0f

        // Distraction penalty component (25-0 points): 0 distractions = 25, 5+ = 0
        val distractionScore = (25f - (daily.distractionCount * 5f)).coerceIn(0f, 25f)

        // Streak bonus component (0-10 points)
        val streakScore = ((user?.currentStreak ?: 0).toFloat() / 7f * 10f).coerceIn(0f, 10f)

        return (focusScore + completionScore + distractionScore + streakScore).coerceIn(0f, 100f)
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
