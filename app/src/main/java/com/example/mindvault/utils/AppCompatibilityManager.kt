package com.example.mindvault.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Manages the list of "compatibility apps" — apps (like banking apps) that
 * don't work when an Accessibility Service is active.
 *
 * When one of these apps is opened OUTSIDE of Focus Mode, the Accessibility
 * Service will call disableSelf() to temporarily turn itself off.
 */
object AppCompatibilityManager {

    private const val TAG = "AppCompatibilityManager"
    private const val PREFS_NAME = "app_compatibility_prefs"
    private const val KEY_APPS = "compatibility_apps"
    private const val KEY_AUTO_DISABLED = "compatibility_accessibility_disabled"
    private const val KEY_FEATURE_ENABLED = "compatibility_feature_enabled"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun ensureInit(context: Context? = null) {
        if (!::prefs.isInitialized) {
            if (context != null) {
                init(context)
            } else {
                throw IllegalStateException("AppCompatibilityManager not initialized. Call init() first.")
            }
        }
    }

    /**
     * Whether the entire compatibility feature is enabled by the user.
     */
    fun isFeatureEnabled(context: Context? = null): Boolean {
        ensureInit(context)
        return prefs.getBoolean(KEY_FEATURE_ENABLED, false)
    }

    fun setFeatureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FEATURE_ENABLED, enabled).apply()
        Log.i(TAG, "Compatibility feature enabled: $enabled")
    }

    /**
     * Returns the set of package names marked as compatibility apps.
     */
    fun getCompatibilityApps(context: Context? = null): Set<String> {
        ensureInit(context)
        return prefs.getStringSet(KEY_APPS, emptySet()) ?: emptySet()
    }

    /**
     * Adds a package name to the compatibility list.
     */
    fun addApp(packageName: String) {
        val current = getCompatibilityApps().toMutableSet()
        current.add(packageName)
        prefs.edit().putStringSet(KEY_APPS, current).apply()
        Log.i(TAG, "Added compatibility app: $packageName")
    }

    /**
     * Removes a package name from the compatibility list.
     */
    fun removeApp(packageName: String) {
        val current = getCompatibilityApps().toMutableSet()
        current.remove(packageName)
        prefs.edit().putStringSet(KEY_APPS, current).apply()
        Log.i(TAG, "Removed compatibility app: $packageName")
    }

    /**
     * Checks if a given package is in the compatibility list.
     */
    fun isCompatibilityApp(packageName: String, context: Context? = null): Boolean {
        ensureInit(context)
        if (!isFeatureEnabled()) return false
        return getCompatibilityApps().contains(packageName)
    }

    /**
     * Flag indicating that the Accessibility Service was auto-disabled
     * for a compatibility app. Used to show the re-enable popup when
     * the user returns to MindVault.
     */
    fun wasAutoDisabled(context: Context? = null): Boolean {
        ensureInit(context)
        return prefs.getBoolean(KEY_AUTO_DISABLED, false)
    }

    fun markAutoDisabled(disabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DISABLED, disabled).apply()
        Log.i(TAG, "Auto-disabled flag set to: $disabled")
    }
}
