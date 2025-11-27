package com.example.mindvault.utils

import com.example.mindvault.model.AppInfo

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppManager {
    
    suspend fun getInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        
        val filteredApps = installedApps
            .filter { app ->
                // Include user-installed apps OR updated system apps OR common apps like YouTube
                val isUserApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                val isUpdatedSystemApp = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val isCommonApp = getCommonDistractingApps().contains(app.packageName) ||
                                 getEssentialApps().contains(app.packageName)
                
                isUserApp || isUpdatedSystemApp || isCommonApp
            }
            .filter { app ->
                // Filter out apps without launcher intent (non-launchable apps)
                val hasLaunchIntent = packageManager.getLaunchIntentForPackage(app.packageName) != null
                if (!hasLaunchIntent) {
                    android.util.Log.d("AppManager", "Filtered out ${app.packageName} - no launch intent")
                }
                hasLaunchIntent
            }
            .map { app ->
                AppInfo(
                    packageName = app.packageName,
                    appName = app.loadLabel(packageManager).toString(),
                    icon = app.loadIcon(packageManager)
                )
            }
            .sortedBy { it.appName }
            
        android.util.Log.d("AppManager", "Loaded ${filteredApps.size} apps")
        // Log if YouTube is included
        val youtubeApp = filteredApps.find { it.packageName == "com.google.android.youtube" }
        if (youtubeApp != null) {
            android.util.Log.d("AppManager", "YouTube found: ${youtubeApp.appName}")
        } else {
            android.util.Log.w("AppManager", "YouTube not found in app list")
        }
        
        return@withContext filteredApps
    }
    
    fun getAppName(context: Context, packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
    
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    // Common apps that users might want to block during study time
    fun getCommonDistractingApps(): List<String> {
        return listOf(
            "com.facebook.katana", // Facebook
            "com.instagram.android", // Instagram
            "com.twitter.android", // Twitter
            "com.snapchat.android", // Snapchat
            "com.tiktok.android", // TikTok
            "com.whatsapp", // WhatsApp
            "com.google.android.youtube", // YouTube
            "com.netflix.mediaclient", // Netflix
            "com.spotify.music", // Spotify
            "com.discord", // Discord
            "com.reddit.frontpage", // Reddit
            "com.pinterest", // Pinterest
            "com.linkedin.android", // LinkedIn
            "com.telegram.messenger", // Telegram
            "com.zhiliaoapp.musically", // TikTok (alternative package)
            "com.amazon.mShop.android.shopping", // Amazon
            "com.ebay.mobile", // eBay
            "com.google.android.apps.photos", // Google Photos
            "com.google.android.gm", // Gmail
            "com.microsoft.office.outlook", // Outlook
        )
    }
    
    // Essential apps that should typically be allowed
    fun getEssentialApps(): List<String> {
        return listOf(
            "com.android.dialer", // Phone
            "com.android.mms", // Messages
            "com.android.settings", // Settings
            "com.android.calculator2", // Calculator
            "com.android.calendar", // Calendar
            "com.android.clock", // Clock
            "com.android.camera2", // Camera
            "com.google.android.apps.maps", // Google Maps
            "com.android.chrome", // Chrome (for study purposes)
            "com.google.android.apps.docs.editors.docs", // Google Docs
            "com.microsoft.office.word", // Microsoft Word
            "com.adobe.reader", // Adobe Reader
            "com.evernote", // Evernote
            "com.google.android.keep", // Google Keep
        )
    }

    fun hasSystemAlertWindowPermission(context: Context): Boolean {
        return android.provider.Settings.canDrawOverlays(context)
    }
    
    fun hasAccessibilityServicePermission(context: Context): Boolean {
        val enabledServices = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        android.util.Log.d("AppManager", "Raw accessibility services string: $enabledServices")
        
        if (enabledServices.isNullOrEmpty()) {
            android.util.Log.d("AppManager", "No accessibility services enabled")
            return false
        }
        
        // Split the enabled services and check each one
        val services = enabledServices.split(':').map { it.trim() }
        
        // The exact service name should match what's in AndroidManifest.xml
        val expectedServiceName = "com.example.mindvault/com.example.mindvault.services.FocusAccessibilityService"
        
        val isEnabled = services.any { service ->
            service.contains("FocusAccessibilityService") || service == expectedServiceName
        }
        
        android.util.Log.d("AppManager", "Services found: $services")
        android.util.Log.d("AppManager", "FocusAccessibilityService enabled: $isEnabled")
        return isEnabled
    }
    
    fun isAccessibilityServiceRunning(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        if (enabledServices.isNullOrEmpty()) return false
        
        val services = enabledServices.split(':').map { it.trim() }
        return services.any { service ->
            service.contains("FocusAccessibilityService")
        }
    }
    
    fun getAccessibilityServiceStatus(context: Context): String {
        val enabledServices = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return "Enabled services: ${enabledServices ?: "None"}"
    }
    
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return hasSystemAlertWindowPermission(context) &&
               hasAccessibilityServicePermission(context)
    }
    
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun hasNotificationListenerPermission(context: Context): Boolean {
        val enabledListeners = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val componentName = "${context.packageName}/${com.example.mindvault.services.FocusNotificationListenerService::class.java.name}"
        return enabledListeners?.contains(componentName) == true
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
