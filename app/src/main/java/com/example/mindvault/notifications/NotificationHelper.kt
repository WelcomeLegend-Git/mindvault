package com.example.mindvault.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mindvault.R

/**
 * Central helper for building and showing notifications.
 */
object NotificationHelper {

    private fun postNotification(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (error: SecurityException) {
            // Permission can be revoked between the check and the notification request.
            Log.w("NotificationHelper", "Notification permission unavailable", error)
        }
    }

    const val CHANNEL_MOTIVATION_ID = "daily_motivation"
    private const val CHANNEL_MOTIVATION_NAME = "Daily Motivation"

    // Channel for achievement unlocked notifications
    const val CHANNEL_ACHIEVEMENT_ID = "achievement_unlocked"
    private const val CHANNEL_ACHIEVEMENT_NAME = "Achievements"

    const val CHANNEL_SCROLL_REMINDERS_ID = "social_scroll_reminders"
    private const val CHANNEL_SCROLL_REMINDERS_NAME = "Scroll Interruptions"
    private const val SOCIAL_SCROLL_NOTIFICATION_ID = 3_001

    /**
     * Must be called once on application start to make sure channels exist.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val motivationChannel = NotificationChannel(
                CHANNEL_MOTIVATION_ID,
                CHANNEL_MOTIVATION_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily motivational quote notifications"
                enableLights(true)
                lightColor = Color.parseColor("#8F5CFF")
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(motivationChannel)

            // Achievement channel
            val achievementChannel = NotificationChannel(
                CHANNEL_ACHIEVEMENT_ID,
                CHANNEL_ACHIEVEMENT_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for unlocked achievements"
                enableLights(true)
                lightColor = Color.parseColor("#FFD700")
            }
            manager.createNotificationChannel(achievementChannel)

            val scrollReminderChannel = NotificationChannel(
                CHANNEL_SCROLL_REMINDERS_ID,
                CHANNEL_SCROLL_REMINDERS_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Gentle reminders after prolonged social-media use"
                enableLights(true)
                lightColor = Color.parseColor("#8F5CFF")
            }
            manager.createNotificationChannel(scrollReminderChannel)
        }
    }

    fun showSocialScrollReminderNotification(
        context: Context,
        appName: String,
        continuousUseMinutes: Int
    ) {
        val message = "You have been on $appName for about $continuousUseMinutes minutes. " +
                "Pause, take one breath, and choose what deserves the next few minutes."
        val notification = NotificationCompat.Builder(context, CHANNEL_SCROLL_REMINDERS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("A quick pause")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(Color.parseColor("#8F5CFF"))
            .setAutoCancel(true)
            .build()

        postNotification(context, SOCIAL_SCROLL_NOTIFICATION_ID, notification)
    }

    fun showFocusReminderNotification(context: Context, message: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_MOTIVATION_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🎯 Focus Reminder")
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message)
            )
            .setColor(Color.parseColor("#8F5CFF"))
            .setAutoCancel(true)

        postNotification(context, System.currentTimeMillis().toInt(), builder.build())
    }

    fun showMotivationNotification(context: Context, quote: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_MOTIVATION_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("✨ Daily Motivation ✨")
            .setContentText(quote)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(quote)
            )
            .setColor(Color.parseColor("#8F5CFF"))
            .setAutoCancel(true)

        postNotification(context, System.currentTimeMillis().toInt(), builder.build())
    }

    fun showAchievementNotification(context: Context, title: String, description: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ACHIEVEMENT_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🏆 Achievement Unlocked!")
            .setContentText(title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("🏆 $title")
                    .bigText(description)
            )
            .setColor(Color.parseColor("#FFD700"))
            .setAutoCancel(true)

        postNotification(context, System.currentTimeMillis().toInt(), builder.build())
    }
}
