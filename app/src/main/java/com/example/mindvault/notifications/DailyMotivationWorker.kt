package com.example.mindvault.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mindvault.ui.getMotivationQuotes
import kotlin.random.Random

class DailyMotivationWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Show the fancy bitmap notification which internally fetches from QuoteEngine
        com.example.mindvault.ui.notifications.CustomNotificationBuilder.showQuoteNotification(
            context = appContext,
            isStudySession = false,
            isScrolling = false
        )
        
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "daily_motivation_work"
    }
}
