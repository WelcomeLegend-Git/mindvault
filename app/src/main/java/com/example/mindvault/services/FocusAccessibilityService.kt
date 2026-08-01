package com.example.mindvault.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.mindvault.AppBlockedActivity
import com.example.mindvault.data.FocusManager
import com.example.mindvault.data.StatisticsManager
import android.graphics.Rect
import com.example.mindvault.utils.AppManager
import com.example.mindvault.utils.OverlayBlocker

class FocusAccessibilityService : AccessibilityService() {

    private val TAG = "FocusAccessibilityService"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val packageName = event.packageName?.toString()
            if (packageName != null) {
                // Log.d(TAG, "Foreground app: $packageName") // Verbose logging
                checkAndBlockApp(packageName, event)
            }
        }
    }

    private fun checkAndBlockApp(packageName: String, event: AccessibilityEvent?) {
        if (!FocusManager.isInitialized()) {
            Log.w(TAG, "FocusManager not initialized, skipping block check.")
            return
        }

        val config = FocusManager.getCurrentConfiguration()
        val isCurrentlyInFocusSession = FocusManager.isFocusModeActive()

        if (isCurrentlyInFocusSession && config.selectedApps.contains(packageName)) {
            val isWhitelisted = packageName == "com.example.mindvault" || isLauncher(packageName)
            if (!isWhitelisted) {
                Log.i(TAG, "Blocking app: $packageName")
                
                // Record distraction in statistics
                StatisticsManager.recordDistraction(packageName)
                
                // Detect if the blocked app is running in a floating/freeform/split window
                val windowBounds = getWindowBoundsForPackage(packageName)
                val screenWidth = resources.displayMetrics.widthPixels
                val screenHeight = resources.displayMetrics.heightPixels
                val isFloating = windowBounds != null &&
                        (windowBounds.width() < screenWidth || windowBounds.height() < screenHeight)

                if (isFloating && windowBounds != null) {
                    Log.i(TAG, "Floating window detected for $packageName, showing overlay")
                    val label = AppManager.getAppName(this, packageName)
                    OverlayBlocker.show(this, windowBounds, label)
                    // Force-close the floating window
                    try {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    } catch (_: Exception) { }
                } else {
                    OverlayBlocker.hide(this)
                    showBlockedScreen(packageName)
                }
            }
        } else {
            // App is not blocked (or focus mode inactive) — clean up any lingering overlay
            OverlayBlocker.hide(this)
        }
    }

    private fun isLauncher(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun showBlockedScreen(packageName: String) {
        val activeSlot = FocusManager.getCurrentActiveSlot() ?: return // Slot might have just ended

        val intent = Intent(this, AppBlockedActivity::class.java).apply {
            putExtra("blocked_app", packageName)
            putExtra("slot_type", activeSlot.type.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
    }

    /**
     * Scans all visible windows via the Accessibility API to find the actual
     * window belonging to [packageName]. Returns its screen bounds, or null
     * if no matching window is found.
     *
     * This is far more reliable than event.source.getBoundsInScreen() which
     * often returns null or returns the bounds of a single UI node rather
     * than the window itself.
     */
    private fun getWindowBoundsForPackage(packageName: String): Rect? {
        try {
            val windowList = windows ?: return null
            for (window in windowList) {
                val root = window.root
                if (root != null && root.packageName?.toString() == packageName) {
                    val rect = Rect()
                    window.getBoundsInScreen(rect)
                    root.recycle()
                    // Ignore zero-sized or invalid bounds
                    if (rect.width() > 0 && rect.height() > 0) {
                        return rect
                    }
                }
                root?.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning windows for $packageName", e)
        }
        return null
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
        Log.i(TAG, "Accessibility Service connected and configured.")
        isServiceRunning = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        Log.i(TAG, "Accessibility Service destroyed.")
    }

    companion object {
        var isServiceRunning = false
            private set
    }
}
