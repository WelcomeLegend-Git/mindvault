package com.example.mindvault.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mindvault.data.FocusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime

/**
 * Worker that sends a focus reminder notification if focus mode is active.
 */
class FocusReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Check if focus mode is currently active
            val isFocusActive = FocusManager.isFocusModeActive()
            
            if (isFocusActive) {
                // Get current time for dynamic message
                val currentTime = LocalTime.now()
                val timeString = currentTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                
                val message = when {
                    currentTime.hour < 12 -> "Keep going! It's only $timeString. You're doing great!"
                    currentTime.hour < 18 -> "Stay focused! It's $timeString. You've got this!"
                    else -> "Almost there! It's $timeString. Keep pushing!"
                }
                
                NotificationHelper.showFocusReminderNotification(applicationContext, message)
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
