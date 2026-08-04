package com.example.mindvault.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import java.util.Calendar

/**
 * Queries UsageStatsManager for real device usage data.
 * Uses event-based calculation to match Digital Wellbeing accuracy.
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
        val focusVsScreenRatio: Float
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

    /**
     * Compute today's screen time by walking usage EVENTS (foreground/background
     * transitions). This matches Digital Wellbeing's approach and avoids the
     * duplicate-bucket problem with queryUsageStats(INTERVAL_DAILY).
     */
    fun getTodayScreenTime(context: Context): ScreenTimeSummary {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptySummary()

        val now = System.currentTimeMillis()
        val todayStart = todayMidnight()

        return computeScreenTime(context, usageStatsManager, todayStart, now)
    }

    /**
     * Get daily screen time for the past 7 days.
     */
    fun getWeeklyScreenTime(context: Context): List<Pair<String, Long>> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val result = mutableListOf<Pair<String, Long>>()
        val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
            val dayName = dayNames[dayOfWeek]

            // Set to midnight of that day
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis

            // End is midnight of next day, or now for today
            val calEnd = cal.clone() as Calendar
            calEnd.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = if (i == 0) System.currentTimeMillis() else calEnd.timeInMillis

            val summary = computeScreenTime(context, usageStatsManager, dayStart, dayEnd)
            result.add(dayName to summary.totalScreenTimeMinutes)
        }

        return result
    }

    /**
     * Core method: compute screen time from usage events between two timestamps.
     * Tracks foreground→background transitions per app to get accurate durations.
     */
    private fun computeScreenTime(
        context: Context,
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long
    ): ScreenTimeSummary {
        val pm = context.packageManager

        // Map: packageName → accumulated foreground millis
        val appTimeMap = mutableMapOf<String, Long>()
        // Map: packageName → timestamp when it last moved to foreground
        val foregroundStart = mutableMapOf<String, Long>()

        var unlockCount = 0

        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val timestamp = event.timeStamp

                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        // App came to foreground
                        foregroundStart[pkg] = timestamp
                    }

                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        // App went to background — compute duration
                        val start = foregroundStart.remove(pkg) ?: continue
                        val duration = (timestamp - start).coerceAtLeast(0L)
                        appTimeMap[pkg] = (appTimeMap[pkg] ?: 0L) + duration
                    }

                    UsageEvents.Event.KEYGUARD_HIDDEN -> {
                        unlockCount++
                    }
                }
            }

            // For apps still in foreground (currently open), count up to endTime
            for ((pkg, start) in foregroundStart) {
                val duration = (endTime - start).coerceAtLeast(0L)
                appTimeMap[pkg] = (appTimeMap[pkg] ?: 0L) + duration
            }

        } catch (e: Exception) {
            Log.e("ScreenTimeTracker", "Error computing screen time from events", e)
            return emptySummary()
        }

        // Build result, filtering system packages
        val appUsageList = mutableListOf<AppUsageInfo>()
        var totalScreenTime = 0L
        var socialMediaTime = 0L

        for ((packageName, timeMillis) in appTimeMap) {
            if (timeMillis < 60_000L) continue // Skip < 1 minute
            if (isSystemPackage(packageName, pm)) continue

            val appName = try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }

            val category = categorizeApp(packageName)

            appUsageList.add(
                AppUsageInfo(
                    packageName = packageName,
                    appName = appName,
                    usageTimeMillis = timeMillis,
                    category = category
                )
            )

            totalScreenTime += timeMillis
            if (category == AppCategory.SOCIAL) {
                socialMediaTime += timeMillis
            }
        }

        val topApps = appUsageList.sortedByDescending { it.usageTimeMillis }.take(8)

        val focusMinutes = StatisticsManager.getTodayFocusTime()
        val screenMinutes = totalScreenTime / 60_000L
        val ratio = if (screenMinutes > 0) {
            (focusMinutes.toFloat() / screenMinutes.toFloat()).coerceIn(0f, 1f)
        } else 0f

        return ScreenTimeSummary(
            totalScreenTimeMinutes = totalScreenTime / 60_000L,
            socialMediaMinutes = socialMediaTime / 60_000L,
            topApps = topApps,
            unlockCount = unlockCount,
            focusVsScreenRatio = ratio
        )
    }

    /**
     * Better system package detection — checks the ApplicationInfo flags
     * and filters known system package prefixes.
     */
    private fun isSystemPackage(packageName: String, pm: PackageManager): Boolean {
        // Known system prefixes to always exclude
        if (packageName.startsWith("com.android.") ||
            packageName.startsWith("android") ||
            packageName.contains("launcher") ||
            packageName.contains("systemui") ||
            packageName.contains("permissioncontroller") ||
            packageName.contains("inputmethod") ||
            packageName.contains("wellbeing") ||
            packageName == "com.google.android.packageinstaller" ||
            packageName == "com.google.android.gms" ||
            packageName == "com.google.android.gsf" ||
            packageName == "com.google.android.ext.services" ||
            packageName == "com.google.android.providers.media.module" ||
            packageName == "com.google.android.documentsui" ||
            packageName == "com.samsung.android.app.routines" ||
            packageName == "com.samsung.android.dialer" ||
            packageName == "com.sec.android.app.launcher"
        ) return true

        // Check the system flag — if it's a system app and not in our known lists,
        // it's probably not user-facing screen time
        try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            // Allow updated system apps (like YouTube, Chrome, Play Store etc.)
            // They are pre-installed but user-facing
            if (isSystem && !isUpdatedSystem) {
                return true
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return true // Can't resolve = skip
        }

        return false
    }

    private fun categorizeApp(packageName: String): AppCategory {
        return when {
            SOCIAL_APPS.contains(packageName) -> AppCategory.SOCIAL
            ENTERTAINMENT_APPS.contains(packageName) -> AppCategory.ENTERTAINMENT
            PRODUCTIVE_APPS.contains(packageName) -> AppCategory.PRODUCTIVE
            else -> AppCategory.NEUTRAL
        }
    }

    private fun todayMidnight(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
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
