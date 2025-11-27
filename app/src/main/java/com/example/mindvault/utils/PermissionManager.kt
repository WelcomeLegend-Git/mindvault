package com.example.mindvault.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.example.mindvault.services.FocusAccessibilityService

object PermissionManager {

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
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
