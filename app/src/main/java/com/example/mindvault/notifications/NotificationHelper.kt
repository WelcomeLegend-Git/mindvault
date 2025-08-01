package com.example.mindvault.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.mindvault.R

/**
 * Central helper for building and showing notifications.
 */
object NotificationHelper {

    const val CHANNEL_MOTIVATION_ID = "daily_motivation"
    private const val CHANNEL_MOTIVATION_NAME = "Daily Motivation"
    
    // Channel for achievement unlocked notifications
    const val CHANNEL_ACHIEVEMENT_ID = "achievement_unlocked"
    private const val CHANNEL_ACHIEVEMENT_NAME = "Achievements"

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
        }
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

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
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

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
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

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}
