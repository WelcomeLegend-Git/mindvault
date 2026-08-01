package com.example.mindvault.notifications

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.example.mindvault.utils.UsageAccessManager

/**
 * Reconstructs the currently active social-app session from Android usage
 * events. It keeps only the active package and its start time locally; no
 * browsing content, messages, or app data is collected.
 */
class SocialScrollUsageMonitor(private val context: Context) {

    data class ActiveSocialApp(
        val packageName: String,
        val appName: String,
        val continuousUseMillis: Long
    )

    fun getActiveSocialApp(
        nowMillis: Long = System.currentTimeMillis()
    ): ActiveSocialApp? {
        if (!UsageAccessManager.hasUsageAccess(context)) return null

        var activePackage = preferences.getString(KEY_ACTIVE_PACKAGE, null)
        var activeSince = preferences.getLong(KEY_ACTIVE_SINCE, 0L)
        var lastProcessedEventAt = preferences.getLong(KEY_LAST_EVENT_AT, 0L)

        if (lastProcessedEventAt > nowMillis) {
            activePackage = null
            activeSince = 0L
            lastProcessedEventAt = 0L
        }

        // The first check needs enough history to recover a session that began
        // before WorkManager's first execution. Later checks continue from the
        // last observed event, which keeps each query small.
        val queryStart = if (lastProcessedEventAt > 0L) {
            lastProcessedEventAt
        } else {
            nowMillis - INITIAL_LOOKBACK_MILLIS
        }

        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
            ?: return null
        val events = usageStatsManager.queryEvents(queryStart, nowMillis)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            lastProcessedEventAt = maxOf(lastProcessedEventAt, event.timeStamp)

            if (isSessionEndingEvent(event.eventType)) {
                activePackage = null
                activeSince = 0L
                continue
            }

            val packageName = event.packageName ?: continue
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                continue
            }

            when {
                isForegroundEvent(event.eventType) -> {
                    if (MONITORED_SOCIAL_APPS.containsKey(packageName)) {
                        if (activePackage != packageName) {
                            activePackage = packageName
                            activeSince = event.timeStamp
                        }
                    } else {
                        activePackage = null
                        activeSince = 0L
                    }
                }

                isBackgroundEvent(event.eventType) && activePackage == packageName -> {
                    activePackage = null
                    activeSince = 0L
                }
            }
        }

        saveState(activePackage, activeSince, lastProcessedEventAt)

        val packageName = activePackage ?: return null
        if (activeSince <= 0L) return null

        if (nowMillis < activeSince) return null

        val continuousUseMillis = nowMillis - activeSince
        if (continuousUseMillis < SocialScrollReminderSettings.CONTINUOUS_USE_THRESHOLD_MILLIS) {
            return null
        }

        return ActiveSocialApp(
            packageName = packageName,
            appName = MONITORED_SOCIAL_APPS.getValue(packageName),
            continuousUseMillis = continuousUseMillis
        )
    }

    fun clearState() {
        preferences.edit().clear().apply()
    }

    private fun saveState(activePackage: String?, activeSince: Long, lastEventAt: Long) {
        preferences.edit()
            .putString(KEY_ACTIVE_PACKAGE, activePackage)
            .putLong(KEY_ACTIVE_SINCE, activeSince)
            .putLong(KEY_LAST_EVENT_AT, lastEventAt)
            .apply()
    }

    private fun isSessionEndingEvent(eventType: Int): Boolean =
        eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
            eventType == UsageEvents.Event.KEYGUARD_SHOWN

    private fun isForegroundEvent(eventType: Int): Boolean =
        eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                eventType == UsageEvents.Event.ACTIVITY_RESUMED)

    private fun isBackgroundEvent(eventType: Int): Boolean =
        eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                eventType == UsageEvents.Event.ACTIVITY_PAUSED)

    private companion object {
        const val PREFERENCES_NAME = "mindvault_social_scroll_monitor"
        const val KEY_ACTIVE_PACKAGE = "active_package"
        const val KEY_ACTIVE_SINCE = "active_since"
        const val KEY_LAST_EVENT_AT = "last_event_at"
        const val INITIAL_LOOKBACK_MILLIS = 6L * 60L * 60L * 1000L

        val MONITORED_SOCIAL_APPS = mapOf(
            "com.instagram.android" to "Instagram",
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.facebook.katana" to "Facebook",
            "com.facebook.orca" to "Messenger",
            "com.twitter.android" to "X",
            "com.google.android.youtube" to "YouTube",
            "com.snapchat.android" to "Snapchat",
            "org.telegram.messenger" to "Telegram",
            "com.reddit.frontpage" to "Reddit",
            "com.instagram.barcelona" to "Threads"
        )
    }

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
