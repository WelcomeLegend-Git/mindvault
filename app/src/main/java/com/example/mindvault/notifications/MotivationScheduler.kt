package com.example.mindvault.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object MotivationScheduler {

    fun scheduleDailyMotivation(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyMotivationWorker>(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyMotivationWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelDailyMotivation(context: Context) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(DailyMotivationWorker.UNIQUE_WORK_NAME)
    }
}
