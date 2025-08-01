package com.example.mindvault.notifications

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Scheduler for focus reminder notifications.
 * Handles scheduling and cancellation of periodic focus reminders.
 */
object FocusReminderScheduler {
    private const val WORK_NAME = "focus_reminder"
    
    /**
     * Schedules periodic focus reminders every 30 minutes.
     * Will not schedule if already scheduled.
     */
    fun scheduleFocusReminders(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<FocusReminderWorker>(30, TimeUnit.MINUTES)
            .setInitialDelay(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,  // Don't reschedule if already running
            workRequest
        )
    }
    
    /**
     * Cancels any scheduled focus reminders.
     */
    fun cancelFocusReminders(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
