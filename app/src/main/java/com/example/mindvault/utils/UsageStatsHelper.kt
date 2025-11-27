package com.example.mindvault.utils

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Helper object to query device Usage Stats and provide
 * easy-to-use aggregates for the Statistics UI.
 */
object UsageStatsHelper {

    /**
     * Returns a map of packageName -> total foreground time (milliseconds)
     * for the supplied time range.
     */
    fun getUsageStatsForRange(
        context: Context,
        startMillis: Long,
        endMillis: Long
    ): Map<String, Long> {
        val usageManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val usageEvents = usageManager.queryEvents(startMillis, endMillis)

        val usageMap = mutableMapOf<String, Long>()
        val foregroundStartMap = mutableMapOf<String, Long>()

        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val packageName = event.packageName ?: continue

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val clampedStart = maxOf(startMillis, event.timeStamp)
                    foregroundStartMap[packageName] = clampedStart
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = foregroundStartMap.remove(packageName)
                    if (start != null) {
                        val clampedEnd = minOf(endMillis, event.timeStamp)
                        if (clampedEnd > start) {
                            val diff = clampedEnd - start
                            val current = usageMap[packageName] ?: 0L
                            usageMap[packageName] = current + diff
                        }
                    }
                }
            }
        }

        // If an app is still considered foreground at the end of the range,
        // count usage up to endMillis.
        foregroundStartMap.forEach { (packageName, start) ->
            val clampedEnd = endMillis
            if (clampedEnd > start) {
                val diff = clampedEnd - start
                val current = usageMap[packageName] ?: 0L
                usageMap[packageName] = current + diff
            }
        }

        return usageMap
    }

    /**
     * Convenience wrapper for the current day (00:00 → now).
     */
    fun getTodayUsageStats(context: Context): Map<String, Long> {
        val now = System.currentTimeMillis()
        val startOfDay = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
            
        val rawStats = getUsageStatsForRange(context, startOfDay, now)
        
        // Clamp total usage to elapsed time since midnight to prevent "future" usage
        val elapsedSinceMidnight = now - startOfDay
        val totalUsage = rawStats.values.sum()
        
        return if (totalUsage > elapsedSinceMidnight && elapsedSinceMidnight > 0) {
            // If total usage exceeds elapsed time, scale down all values
            val ratio = elapsedSinceMidnight.toDouble() / totalUsage.toDouble()
            rawStats.mapValues { (_, time) -> (time * ratio).toLong() }
        } else {
            rawStats
        }
    }

    /**
     * Returns usage that occurred while Focus Mode sessions were running today.
     * Relies on FocusSessionRecord persistence into SharedPreferences by StatisticsManager.
     * If no sessions were run today, returns an empty map.
     */
    fun getUsageDuringFocus(context: Context): Map<String, Long> {
        val sessions = getTodayFocusSessions(context)
        Log.d("UsageStatsHelper", "Found ${sessions.size} focus sessions today")
        
        if (sessions.isEmpty()) {
            Log.d("UsageStatsHelper", "No focus sessions found for today")
            return emptyMap()
        }

        val usageManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val aggregate = mutableMapOf<String, Long>()

        for ((start, end) in sessions) {
            Log.d("UsageStatsHelper", "Processing session: ${java.util.Date(start)} to ${java.util.Date(end)}")
            
            val slice: Map<String, UsageStats> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                usageManager.queryAndAggregateUsageStats(start, end)
            } else {
                // Fallback for pre-Q devices
                usageManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    start,
                    end
                ).associateBy { it.packageName }
            }
            
            Log.d("UsageStatsHelper", "Found ${slice.size} apps with usage in this session")
            
            slice.forEach { (pkg, stat) ->
                val current = aggregate[pkg] ?: 0L
                aggregate[pkg] = current + stat.totalTimeInForeground
                if (stat.totalTimeInForeground > 0) {
                    Log.d("UsageStatsHelper", "App $pkg used for ${stat.totalTimeInForeground}ms during focus")
                }
            }
        }
        
        Log.d("UsageStatsHelper", "Total apps with usage during focus: ${aggregate.filter { it.value > 0 }.size}")
        return aggregate
    }

    /**
     * Exposes today's focus session windows for other components.
     */
    fun getTodayFocusWindowsPublic(context: Context): List<Pair<Long, Long>> {
        return getTodayFocusSessions(context)
    }

    /**
     * Returns the total duration (in minutes) of all focus sessions today.
     * This is used to cap UI aggregates so totals cannot exceed the time actually spent in focus.
     */
    fun getTotalFocusMinutesToday(context: Context): Int {
        val sessions = getTodayFocusSessions(context)
        if (sessions.isEmpty()) return 0
        val totalMs = sessions.sumOf { (start, end) ->
            (end - start).coerceAtLeast(0L)
        }
        return (totalMs / 60000L).toInt()
    }

    /**
     * Reads today's focus session time windows from SharedPreferences.
     * Stored under key "sessions_yyyy-MM-dd" as JSON array
     * [{"start": epochMillis, "end": epochMillis}, ...].
     */
    private fun getTodayFocusSessions(context: Context): List<Pair<Long, Long>> {
        val prefs = context.getSharedPreferences("mindvault_stats", Context.MODE_PRIVATE)
        val dateKey = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val jsonString = prefs.getString("sessions_$dateKey", null)
        
        Log.d("UsageStatsHelper", "Looking for sessions on date: $dateKey")
        Log.d("UsageStatsHelper", "Sessions JSON: $jsonString")
        
        if (jsonString == null) {
            Log.d("UsageStatsHelper", "No session data found for today")
            return emptyList()
        }
        
        return try {
            val arr = JSONArray(jsonString)
            Log.d("UsageStatsHelper", "Found ${arr.length()} session records")
            
            (0 until arr.length()).mapNotNull { idx ->
                val obj: JSONObject = arr.optJSONObject(idx) ?: return@mapNotNull null
                val start = obj.optLong("start", -1L)
                val end = obj.optLong("end", -1L)
                
                if (start >= 0 && end >= 0) {
                    Log.d("UsageStatsHelper", "Valid session: ${java.util.Date(start)} to ${java.util.Date(end)}")
                    start to end
                } else {
                    Log.w("UsageStatsHelper", "Invalid session times: start=$start, end=$end")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("UsageStatsHelper", "Failed to parse sessions JSON: $jsonString", e)
            emptyList()
        }
    }
}
