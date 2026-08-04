package com.example.mindvault.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.util.Calendar

/**
 * Queries UsageStatsManager for real device usage data.
 * Requires Usage Access permission.
 */
object ScreenTimeTracker {

    data class AppUsageInfo(
        val packageName: String,
        val appName: String,
        val usageTimeMillis: Long,
        val category: AppCategory
    )

    enum class AppCategory { SOCIAL, ENTERTAINMENT, PRODUCTIVE, NEUTRAL }

    data class ScreenTimeSummary(
        val totalScreenTimeMinutes: Long,
        val socialMediaMinutes: Long,
        val topApps: List<AppUsageInfo>,
        val unlockCount: Int,
        val focusVsScreenRatio: Float // focusTime / screenTime (0-1)
    )

    private val SOCIAL_APPS = setOf(
        "com.instagram.android",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.facebook.katana",
        "com.facebook.orca",
        "com.twitter.android",
        "com.snapchat.android",
        "org.telegram.messenger",
        "com.reddit.frontpage",
        "com.instagram.barcelona",
        "com.tiktok.android",
        "com.zhiliaoapp.musically"
    )

    private val ENTERTAINMENT_APPS = setOf(
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "com.amazon.avod.thirdpartyclient",
        "com.spotify.music",
        "in.startv.hotstar",
        "com.jio.jioplay.tv",
        "com.graymatrix.did",
        "com.mxtech.videoplayer.ad"
    )

    private val PRODUCTIVE_APPS = setOf(
        "com.google.android.apps.docs",
        "com.google.android.apps.docs.editors.docs",
        "com.google.android.apps.docs.editors.sheets",
        "com.google.android.apps.docs.editors.slides",
        "com.microsoft.office.word",
        "com.microsoft.office.excel",
        "com.microsoft.office.powerpoint",
        "com.microsoft.teams",
        "com.google.android.apps.classroom",
        "com.google.android.calendar",
        "com.notion.android",
        "com.todoist",
        "com.google.android.keep"
    )

    fun getTodayScreenTime(context: Context): ScreenTimeSummary {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptySummary()

        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        // Get usage stats for today
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        if (usageStatsList.isNullOrEmpty()) return emptySummary()

        val pm = context.packageManager
        val appUsageList = mutableListOf<AppUsageInfo>()
        var totalScreenTime = 0L
        var socialMediaTime = 0L

        // Filter and aggregate
        for (stats in usageStatsList) {
            val timeInForeground = stats.totalTimeInForeground
            if (timeInForeground <= 0) continue

            val packageName = stats.packageName

            // Skip system/launcher packages
            if (isSystemPackage(packageName)) continue

            val appName = try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName.substringAfterLast('.')
                    .replaceFirstChar { it.uppercase() }
            }

            val category = categorizeApp(packageName)

            appUsageList.add(
                AppUsageInfo(
                    packageName = packageName,
                    appName = appName,
                    usageTimeMillis = timeInForeground,
                    category = category
                )
            )

            totalScreenTime += timeInForeground
            if (category == AppCategory.SOCIAL) {
                socialMediaTime += timeInForeground
            }
        }

        // Sort by usage time, take top 8
        val topApps = appUsageList
            .sortedByDescending { it.usageTimeMillis }
            .take(8)

        // Count unlocks
        val unlockCount = countUnlocks(usageStatsManager, startTime, endTime)

        // Calculate focus ratio
        val focusMinutes = StatisticsManager.getTodayFocusTime()
        val screenMinutes = totalScreenTime / 60000L
        val ratio = if (screenMinutes > 0) {
            (focusMinutes.toFloat() / screenMinutes.toFloat()).coerceIn(0f, 1f)
        } else 0f

        return ScreenTimeSummary(
            totalScreenTimeMinutes = totalScreenTime / 60000L,
            socialMediaMinutes = socialMediaTime / 60000L,
            topApps = topApps,
            unlockCount = unlockCount,
            focusVsScreenRatio = ratio
        )
    }

    /**
     * Get daily screen time for the past 7 days for weekly trends.
     */
    fun getWeeklyScreenTime(context: Context): List<Pair<String, Long>> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val result = mutableListOf<Pair<String, Long>>()
        val calendar = Calendar.getInstance()
        val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dayEnd = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd
            )

            var totalMinutes = 0L
            stats?.forEach { stat ->
                if (!isSystemPackage(stat.packageName) && stat.totalTimeInForeground > 0) {
                    totalMinutes += stat.totalTimeInForeground / 60000L
                }
            }

            val dayName = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
            result.add(dayName to totalMinutes)
        }

        return result
    }

    private fun countUnlocks(
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long
    ): Int {
        var count = 0
        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) {
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenTimeTracker", "Error counting unlocks", e)
        }
        return count
    }

    private fun categorizeApp(packageName: String): AppCategory {
        return when {
            SOCIAL_APPS.contains(packageName) -> AppCategory.SOCIAL
            ENTERTAINMENT_APPS.contains(packageName) -> AppCategory.ENTERTAINMENT
            PRODUCTIVE_APPS.contains(packageName) -> AppCategory.PRODUCTIVE
            else -> AppCategory.NEUTRAL
        }
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android.") ||
                packageName.startsWith("android") ||
                packageName == "com.google.android.inputmethod.latin" ||
                packageName == "com.samsung.android.inputmethod" ||
                packageName.contains("launcher") ||
                packageName.contains("systemui") ||
                packageName.contains("permissioncontroller") ||
                packageName == "com.google.android.permissioncontroller" ||
                packageName == "com.google.android.packageinstaller"
    }

    private fun emptySummary(): ScreenTimeSummary {
        return ScreenTimeSummary(
            totalScreenTimeMinutes = 0L,
            socialMediaMinutes = 0L,
            topApps = emptyList(),
            unlockCount = 0,
            focusVsScreenRatio = 0f
        )
    }
}
