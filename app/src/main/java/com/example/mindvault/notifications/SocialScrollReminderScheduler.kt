package com.example.mindvault.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules the low-battery social-scroll check at Android's supported minimum interval. */
object SocialScrollReminderScheduler {

    private const val UNIQUE_WORK_NAME = "mindvault_social_scroll_reminder"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SocialScrollReminderWorker>(
            15,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        SocialScrollUsageMonitor(context).clearState()
    }
}
