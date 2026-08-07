package com.example.mindvault.receivers

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.example.mindvault.data.FocusManager
import com.example.mindvault.ui.notifications.CustomNotificationBuilder

/**
 * BroadcastReceiver that fires 5 minutes before a scheduled Focus session.
 * Checks whether all required permissions (especially Accessibility) are granted.
 * If not, sends a notification reminding the user to re-enable them.
 */
class FocusReminderReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "FocusReminderReceiver"
        const val ACTION_FOCUS_REMINDER = "com.example.mindvault.FOCUS_REMINDER"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FOCUS_REMINDER) return

        Log.i(TAG, "Focus reminder alarm fired — checking permissions")

        // Check if accessibility is enabled
        val accessibilityEnabled = isAccessibilityServiceEnabled(context)

        if (!accessibilityEnabled) {
            Log.i(TAG, "Accessibility NOT enabled — sending reminder notification")
            CustomNotificationBuilder.showPermissionReminderNotification(context)
        } else {
            Log.d(TAG, "All permissions OK — no reminder needed")
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any {
            it.resolveInfo?.serviceInfo?.packageName == context.packageName
        }
    }
}
