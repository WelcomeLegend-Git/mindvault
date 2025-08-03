package com.example.mindvault.utils

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
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
        val stats: Map<String, UsageStats> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            usageManager.queryAndAggregateUsageStats(startMillis, endMillis)
        } else {
            // Fallback for pre-Q devices – still aggregates explicitly
            usageManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startMillis,
                endMillis
            ).associateBy { it.packageName }
        }
        return stats.mapValues { it.value.totalTimeInForeground }
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
        return getUsageStatsForRange(context, startOfDay, now)
    }

    /**
     * Returns usage that occurred while Focus Mode sessions were running today.
     * Relies on FocusSessionRecord persistence into SharedPreferences by StatisticsManager.
     * If no sessions were run today, returns an empty map.
     */
    fun getUsageDuringFocus(context: Context): Map<String, Long> {
        val sessions = getTodayFocusSessions(context)
        if (sessions.isEmpty()) {
            Log.d("UsageStatsHelper", "No focus sessions found for today")
            return emptyMap()
        }

        val usageManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val aggregate = mutableMapOf<String, Long>()

        for ((start, end) in sessions) {
            Log.d("UsageStatsHelper", "Processing focus session: ${end - start}ms")
            val slice = usageManager.queryAndAggregateUsageStats(start, end)
            slice.forEach { (pkg, stat) ->
                val current = aggregate[pkg] ?: 0L
                aggregate[pkg] = current + stat.totalTimeInForeground
            }
        }
        
        Log.d("UsageStatsHelper", "Found usage data for ${aggregate.size} apps during focus sessions")
        return aggregate
    }

    /**
     * Reads today's focus session time windows from SharedPreferences.
     * Stored under key "sessions_yyyy-MM-dd" as JSON array
     * [{"start": epochMillis, "end": epochMillis}, ...].
     */
    private fun getTodayFocusSessions(context: Context): List<Pair<Long, Long>> {
        val prefs = context.getSharedPreferences("mindvault_stats", Context.MODE_PRIVATE)
        val dateKey = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val jsonString = prefs.getString("sessions_$dateKey", null) ?: return emptyList()
        
        return try {
            val arr = JSONArray(jsonString)
            val sessions = mutableListOf<Pair<Long, Long>>()
            
            for (i in 0 until arr.length()) {
                val obj: JSONObject = arr.optJSONObject(i) ?: continue
                val start = obj.optLong("start", -1L)
                val end = obj.optLong("end", -1L)
                
                if (start >= 0 && end >= 0 && end > start) {
                    sessions.add(start to end)
                }
            }
            
            Log.d("UsageStatsHelper", "Found ${sessions.size} valid focus sessions for today")
            sessions
        } catch (e: Exception) {
            Log.e("UsageStatsHelper", "Failed to parse sessions JSON", e)
            emptyList()
        }
    }
    
    /**
     * Validates usage stats data for accuracy
     */
    fun validateUsageStats(usageStats: Map<String, Long>): Map<String, Long> {
        return usageStats.filter { (_, timeInMs) ->
            // Filter out unrealistic values (more than 24 hours in a day)
            timeInMs <= 24 * 60 * 60 * 1000L
        }.filter { (packageName, _) ->
            // Filter out system packages that shouldn't be counted
            !packageName.startsWith("com.android.") &&
            !packageName.startsWith("android.") &&
            !packageName.startsWith("com.google.android.") &&
            packageName != "android"
        }
    }
    
    /**
     * Get usage stats with validation applied
     */
    fun getValidatedTodayUsageStats(context: Context): Map<String, Long> {
        val rawStats = getTodayUsageStats(context)
        return validateUsageStats(rawStats)
    }
    
    /**
     * Get focus session usage with validation applied
     */
    fun getValidatedUsageDuringFocus(context: Context): Map<String, Long> {
        val rawStats = getUsageDuringFocus(context)
        return validateUsageStats(rawStats)
    }
    
    /**
     * Get validated usage stats for a specific range
     */
    fun getValidatedUsageStatsForRange(context: Context, startMillis: Long, endMillis: Long): Map<String, Long> {
        val rawStats = getUsageStatsForRange(context, startMillis, endMillis)
        return validateUsageStats(rawStats)
    }
}
