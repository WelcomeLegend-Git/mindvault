package com.example.mindvault.utils

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.MainThread
import com.example.mindvault.R

/** Touch-blocking overlay for a blocked floating/freeform window. Owned by the main thread. */
object OverlayBlocker {
    private const val TAG = "OverlayBlocker"

    private class Overlay(
        val owner: Context,
        val windowManager: WindowManager,
        val view: FrameLayout,
        val message: TextView,
        var packageName: String,
        var label: String,
        var bounds: Rect,
        var wasAttached: Boolean = false
    )

    private var overlay: Overlay? = null

    @MainThread
    fun targetPackage(context: Context): String? {
        checkMainThread()
        return overlay?.takeIf { it.owner === context }?.packageName
    }

    @MainThread
    fun show(context: Context, packageName: String, targetBounds: Rect, appLabel: String) {
        checkMainThread()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (targetBounds.isEmpty) return
        // A declaration in the manifest does not guarantee the grant is still current.
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission not granted; cannot block floating window")
            return
        }

        var existing = overlay
        if (existing != null && existing.owner !== context) {
            remove(existing)
            if (overlay != null) return // Do not lose ownership of a window whose removal failed.
            existing = null
        }
        if (existing != null && existing.wasAttached && !existing.view.isAttachedToWindow) {
            remove(existing)
            if (overlay != null) return
            existing = null
        }
        if (existing != null) {
            if (existing.packageName != packageName || existing.label != appLabel) {
                existing.message.text = context.getString(R.string.overlay_blocker_message, appLabel)
                existing.packageName = packageName
                existing.label = appLabel
            }
            if (existing.bounds != targetBounds) updateLayout(existing, targetBounds)
            return
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val container = FrameLayout(context).apply {
            setBackgroundColor(0xE61E3A8AFF.toInt())
            isClickable = true
            isFocusable = true
        }
        val message = TextView(context).apply {
            text = context.getString(R.string.overlay_blocker_message, appLabel)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }
        container.addView(
            message, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val current = Overlay(context, wm, container, message, packageName, appLabel, Rect(targetBounds))
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                current.wasAttached = true
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        })
        try {
            wm.addView(container, layoutParams(targetBounds))
            overlay = current
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show blocking overlay", e)
        }
    }

    @MainThread
    fun hide(context: Context) {
        checkMainThread()
        val current = overlay ?: return
        // A late teardown from an old service must not remove its replacement's overlay.
        if (current.owner === context) remove(current)
    }

    private fun remove(current: Overlay) {
        try {
            current.windowManager.removeViewImmediate(current.view)
            if (overlay === current) overlay = null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay was already detached", e)
            if (!current.view.isAttachedToWindow && overlay === current) overlay = null
        } catch (e: Exception) {
            // Retain the handle so a later cleanup can retry rather than orphaning a window.
            Log.e(TAG, "Failed to remove blocking overlay", e)
        }
    }

    private fun updateLayout(current: Overlay, bounds: Rect) {
        try {
            current.windowManager.updateViewLayout(current.view, layoutParams(bounds))
            current.bounds = Rect(bounds)
        } catch (e: Exception) {
            // Keep the last successful bounds, so the next show retries this update.
            Log.e(TAG, "Failed to update overlay bounds", e)
        }
    }

    private fun layoutParams(bounds: Rect) = WindowManager.LayoutParams(
        bounds.width(),
        bounds.height(),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Back still goes to the host app; touches are consumed by the overlay.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bounds.left
        y = bounds.top
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "OverlayBlocker requires the main thread" }
    }
}
