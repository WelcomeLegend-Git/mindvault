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
        // Pick deterministic quote of the day
        val quotes = getMotivationQuotes()
        val index = Random.nextInt(quotes.size)
        val quote = quotes[index]
        NotificationHelper.showMotivationNotification(appContext, quote)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "daily_motivation_work"
    }
}
