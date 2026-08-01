package com.example.mindvault.notifications

import android.content.Context

/** Persistent settings and frequency limits for social-scroll reminders. */
object SocialScrollReminderSettings {

    private const val PREFERENCES_NAME = "mindvault_social_scroll_reminders"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_REMINDER_AT = "last_reminder_at"

    /** A reminder may appear after this much uninterrupted social-app use. */
    const val CONTINUOUS_USE_THRESHOLD_MILLIS = 15L * 60L * 1000L

    /** Avoid turning a helpful interruption into another source of noise. */
    private const val REMINDER_COOLDOWN_MILLIS = 60L * 60L * 1000L

    fun isEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isReminderDue(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val lastReminderAt = preferences(context).getLong(KEY_LAST_REMINDER_AT, 0L)
        return nowMillis - lastReminderAt >= REMINDER_COOLDOWN_MILLIS
    }

    fun recordReminderShown(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        preferences(context).edit().putLong(KEY_LAST_REMINDER_AT, nowMillis).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
