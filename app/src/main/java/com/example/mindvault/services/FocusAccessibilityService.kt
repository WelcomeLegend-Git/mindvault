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

class FocusAccessibilityService : AccessibilityService() {

    private val TAG = "FocusAccessibilityService"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (packageName != null) {
                // Log.d(TAG, "Foreground app: $packageName") // Verbose logging
                checkAndBlockApp(packageName)
            }
        }
    }

    private fun checkAndBlockApp(packageName: String) {
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
                
                showBlockedScreen(packageName)
            }
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

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
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
