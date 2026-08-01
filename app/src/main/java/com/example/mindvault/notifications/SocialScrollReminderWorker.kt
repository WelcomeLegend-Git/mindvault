package com.example.mindvault.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mindvault.utils.UsageAccessManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Battery-friendly periodic check for a prolonged, uninterrupted social-media
 * session. WorkManager decides the exact run time, so this is intentionally a
 * gentle reminder rather than real-time surveillance.
 */
class SocialScrollReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (!SocialScrollReminderSettings.isEnabled(applicationContext)) {
                return@withContext Result.success()
            }
            if (!UsageAccessManager.hasUsageAccess(applicationContext)) {
                return@withContext Result.success()
            }
            if (!NotificationPermissionUtils.hasPermission(applicationContext)) {
                return@withContext Result.success()
            }

            val activeSocialApp = SocialScrollUsageMonitor(applicationContext).getActiveSocialApp()
            if (activeSocialApp != null &&
                SocialScrollReminderSettings.isReminderDue(applicationContext)
            ) {
                NotificationHelper.showSocialScrollReminderNotification(
                    context = applicationContext,
                    appName = activeSocialApp.appName,
                    continuousUseMinutes = (activeSocialApp.continuousUseMillis / 60_000L).toInt()
                )
                SocialScrollReminderSettings.recordReminderShown(applicationContext)
            }

            Result.success()
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to check social app usage", exception)
            // This worker is periodic, so a later normal run is safer than a
            // retry loop that could unnecessarily wake the device.
            Result.success()
        }
    }

    private companion object {
        const val TAG = "SocialScrollReminder"
    }
}
