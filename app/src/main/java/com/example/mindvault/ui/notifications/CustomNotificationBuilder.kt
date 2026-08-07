package com.example.mindvault.ui.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.mindvault.R
import com.example.mindvault.engine.QuoteEngine
import com.example.mindvault.engine.QuoteResult
import com.example.mindvault.engine.ScenarioVibe

object CustomNotificationBuilder {

    private const val CHANNEL_ID = "mindvault_quotes_channel"

    fun showQuoteNotification(context: Context, isStudySession: Boolean, isScrolling: Boolean): QuoteResult {
        val quoteResult = QuoteEngine.getQuoteForContext(context, isStudySession, isScrolling)
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)

        // --- Collapsed view (compact, single-line-ish) ---
        val collapsedViews = RemoteViews(context.packageName, R.layout.notification_quote_card_collapsed)
        val collapsedBitmap = createCollapsedBitmap(context, quoteResult)
        collapsedViews.setImageViewBitmap(R.id.ivCustomTextBitmapCollapsed, collapsedBitmap)

        // --- Expanded view (full quote with author) ---
        val expandedViews = RemoteViews(context.packageName, R.layout.notification_quote_card)
        val expandedBitmap = createExpandedBitmap(context, quoteResult)
        expandedViews.setImageViewBitmap(R.id.ivCustomTextBitmap, expandedBitmap)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedViews)
            .setCustomBigContentView(expandedViews)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        return quoteResult
    }

    /**
     * Creates a compact bitmap for the collapsed notification view.
     * Shows the quote truncated to ~2 lines with the author inline.
     */
    private fun createCollapsedBitmap(context: Context, quoteResult: QuoteResult): Bitmap {
        val typeface = Typeface.createFromAsset(context.assets, "fonts/${quoteResult.fontFileName}")

        val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDarkMode) Color.WHITE else Color.BLACK
        val authorColor = if (isDarkMode) Color.parseColor("#94A3B8") else Color.parseColor("#475569")

        val quoteText = "\"${quoteResult.quote.q}\""
        val authorText = "— ${quoteResult.quote.a}"

        val width = 900
        val padding = 12

        val textPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 36f
            color = textColor
            this.typeface = typeface
        }

        val authorPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 26f
            color = authorColor
            this.typeface = Typeface.DEFAULT_BOLD
        }

        // Build a quote layout capped at 2 lines with ellipsis
        val quoteLayout = StaticLayout.Builder.obtain(quoteText, 0, quoteText.length, textPaint, width - (padding * 2))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(false)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val authorLayout = StaticLayout.Builder.obtain(authorText, 0, authorText.length, authorPaint, width - (padding * 2))
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .setMaxLines(1)
            .build()

        val height = quoteLayout.height + authorLayout.height + (padding * 3)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.translate(padding.toFloat(), padding.toFloat())
        quoteLayout.draw(canvas)

        canvas.translate(0f, quoteLayout.height.toFloat() + 8f)
        authorLayout.draw(canvas)

        return bitmap
    }

    /**
     * Creates the full-size bitmap for the expanded notification view.
     * Shows the complete quote and author with generous spacing.
     */
    private fun createExpandedBitmap(context: Context, quoteResult: QuoteResult): Bitmap {
        val typeface = Typeface.createFromAsset(context.assets, "fonts/${quoteResult.fontFileName}")
        
        val quoteText = "\"${quoteResult.quote.q}\""
        val authorText = "— ${quoteResult.quote.a}"

        val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDarkMode) Color.WHITE else Color.BLACK
        val authorColor = if (isDarkMode) Color.parseColor("#94A3B8") else Color.parseColor("#475569")

        val textPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 50f
            color = textColor
            this.typeface = typeface
        }

        val authorPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 34f
            color = authorColor
            this.typeface = Typeface.DEFAULT_BOLD
        }

        val width = 800
        val padding = 20

        val quoteLayout = StaticLayout.Builder.obtain(quoteText, 0, quoteText.length, textPaint, width - (padding * 2))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.2f)
            .setIncludePad(false)
            .build()

        val authorLayout = StaticLayout.Builder.obtain(authorText, 0, authorText.length, authorPaint, width - (padding * 2))
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .build()

        val height = quoteLayout.height + authorLayout.height + (padding * 4)
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        canvas.translate(padding.toFloat(), padding.toFloat())
        quoteLayout.draw(canvas)
        
        canvas.translate(0f, quoteLayout.height.toFloat() + 20f)
        authorLayout.draw(canvas)

        return bitmap
    }

    private fun createChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MindVault Smart Quotes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Custom typography quote notifications"
            }
            manager.createNotificationChannel(channel)
        }
    }

    // ========================== SYSTEM NOTIFICATIONS ==========================

    private const val SYSTEM_CHANNEL_ID = "mindvault_system_channel"
    private const val COMPATIBILITY_NOTIF_ID = 9001
    private const val REMINDER_NOTIF_ID = 9002

    private fun ensureSystemChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                SYSTEM_CHANNEL_ID,
                "MindVault System Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important system alerts and permission reminders"
            }
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Notification shown when Accessibility Service auto-disables for
     * a compatibility app (banking app etc).
     */
    fun showCompatibilityDisabledNotification(context: Context) {
        ensureSystemChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Tapping opens Accessibility Settings
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SYSTEM_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚠️ Accessibility Service Disabled")
            .setContentText("Turned off for app compatibility. Tap to re-enable for Focus Mode.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("MindVault's Accessibility Service was turned off because you opened an app that conflicts with it. Tap here to re-enable it before your next Focus session."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(COMPATIBILITY_NOTIF_ID, notification)
    }

    /**
     * Notification shown 5 minutes before a scheduled Focus session
     * when required permissions (especially Accessibility) are missing.
     */
    fun showPermissionReminderNotification(context: Context) {
        ensureSystemChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SYSTEM_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🔔 Focus Session Starting Soon")
            .setContentText("Accessibility Service is disabled. Tap to enable it now.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your scheduled Focus session starts in 5 minutes, but MindVault's Accessibility Service is currently off. Enable it now so your apps stay blocked during Focus Mode."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(REMINDER_NOTIF_ID, notification)
    }
}
