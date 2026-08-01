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
        // Fetch a quote from the unified engine, leveraging the "Deck of Cards" to avoid repeats
        val quoteResult = com.example.mindvault.engine.QuoteEngine.getQuoteForContext(
            context = appContext,
            isStudySessionActive = false,
            isScrollingSocialMedia = false
        )
        
        // Show the fancy bitmap notification
        com.example.mindvault.ui.notifications.CustomNotificationBuilder.showBitmapNotification(
            context = appContext,
            quoteText = quoteResult.quote.q,
            authorText = quoteResult.quote.a,
            fontFileName = quoteResult.fontFileName,
            vibe = quoteResult.vibe
        )
        
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "daily_motivation_work"
    }
}
