package com.example.mindvault.utils

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.example.mindvault.services.FocusAccessibilityService

object PermissionManager {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun requestUsageStatsPermission(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun requestOverlayPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = context.packageName + "/" + FocusAccessibilityService::class.java.canonicalName
        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val stringColonSplitter = TextUtils.SimpleStringSplitter(':')
            stringColonSplitter.setString(enabledServices)
            while (stringColonSplitter.hasNext()) {
                val componentName = stringColonSplitter.next()
                if (componentName.equals(service, ignoreCase = true)) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return false
    }

    fun requestAccessibilityPermission(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
