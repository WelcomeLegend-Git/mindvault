package com.example.mindvault.utils

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Draws a small, touch‑blocking overlay on top of a floating/small window
 * using TYPE_APPLICATION_OVERLAY. This prevents the user from interacting
 * with a blocked app that is opened in a vendor side bar or freeform window.
 */
object OverlayBlocker {
    @Volatile private var overlayView: View? = null

    fun show(context: Context, targetBounds: Rect, appLabel: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // If already showing, update position/size
        val existing = overlayView
        if (existing != null) {
            updateLayout(wm, existing, targetBounds)
            return
        }

        val container = FrameLayout(context).apply {
            setBackgroundColor(0xE61E3A8AFF.toInt()) // semi‑opaque blue like AppBlockedActivity
            isClickable = true  // consume touches
            isFocusable = true
        }

        val message = TextView(context).apply {
            text = "App Blocked\n$appLabel"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }
        container.addView(message, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val lp = WindowManager.LayoutParams(
            targetBounds.width(),
            targetBounds.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // not focusable so back goes to host, but we still consume touches
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = targetBounds.left
            y = targetBounds.top
        }

        wm.addView(container, lp)
        overlayView = container
    }

    fun hide(context: Context) {
        val view = overlayView ?: return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try { wm.removeView(view) } catch (_: Exception) {}
        overlayView = null
    }

    private fun updateLayout(wm: WindowManager, view: View, bounds: Rect) {
        val lp = view.layoutParams as WindowManager.LayoutParams
        lp.width = bounds.width()
        lp.height = bounds.height()
        lp.x = bounds.left
        lp.y = bounds.top
        wm.updateViewLayout(view, lp)
    }
}


