package com.example.mindvault.utils

import android.content.Context

/**
 * Helper object to query device Usage Stats and provide
 * easy-to-use aggregates for the Statistics UI.
 *
 * Screen-time based features have been removed from the app,
 * so these methods now return empty/default values and are
 * kept only to avoid breaking existing call sites.
 */
object UsageStatsHelper {

    fun getUsageStatsForRange(
        context: Context,
        startMillis: Long,
        endMillis: Long
    ): Map<String, Long> {
        return emptyMap()
    }

    fun getTodayUsageStats(context: Context): Map<String, Long> {
        return emptyMap()
    }

    fun getUsageDuringFocus(context: Context): Map<String, Long> {
        return emptyMap()
    }

    fun getTotalFocusMinutesToday(context: Context): Int {
        return 0
    }

    fun getTodayFocusWindowsPublic(context: Context): List<Pair<Long, Long>> {
        return emptyList()
    }
}
