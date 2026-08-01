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
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.mindvault.R
import com.example.mindvault.engine.QuoteEngine
import com.example.mindvault.engine.QuoteResult
import com.example.mindvault.engine.ScenarioVibe

object CustomNotificationBuilder {

    private const val CHANNEL_ID = "mindvault_quotes_channel"

    fun showQuoteNotification(context: Context, isStudySession: Boolean, isScrolling: Boolean) {
        val quoteResult = QuoteEngine.getQuoteForContext(context, isStudySession, isScrolling)
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)

        val remoteViews = RemoteViews(context.packageName, R.layout.notification_quote_card)



        // Create Text Bitmap using custom font
        val textBitmap = createCustomFontBitmap(context, quoteResult)
        remoteViews.setImageViewBitmap(R.id.ivCustomTextBitmap, textBitmap)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createCustomFontBitmap(context: Context, quoteResult: QuoteResult): Bitmap {
        val typeface = Typeface.createFromAsset(context.assets, "fonts/${quoteResult.fontFileName}")
        
        val quoteText = "\"${quoteResult.quote.q}\""
        val authorText = "— ${quoteResult.quote.a}"

        val textPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 50f
            color = Color.WHITE
            this.typeface = typeface
        }

        val authorPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 34f
            color = Color.parseColor("#94A3B8")
            this.typeface = Typeface.DEFAULT_BOLD
        }

        val width = 800 // High-res width for rendering
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
}
