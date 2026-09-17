package com.example.mindvault.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import com.example.mindvault.utils.DistractionBurstTracker
import com.example.mindvault.utils.OverlayScheduleBoundary
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.mindvault.AppBlockedActivity
import com.example.mindvault.data.FocusManager
import com.example.mindvault.data.StatisticsManager
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.mindvault.utils.AppManager
import com.example.mindvault.utils.AppCompatibilityManager
import com.example.mindvault.utils.OverlayBlocker
import com.example.mindvault.ui.notifications.CustomNotificationBuilder

class FocusAccessibilityService : AccessibilityService() {

    private val TAG = "FocusAccessibilityService"
    private val distractionBursts = DistractionBurstTracker()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var configurationJob: Job? = null
    private var overlayBoundaryJob: Job? = null
    private var clockReceiverRegistered = false
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reconcileOverlaySchedule()
        }
    }

    /**
     * Packages that can trigger uninstall flows we need to guard against.
     */
    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.android.settings.intelligence"
    )
    private val INSTALLER_PACKAGES = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )
    private val PLAY_STORE_PACKAGE = "com.android.vending"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            reconcileOverlaySchedule()
            val packageName = event.packageName?.toString()
            if (packageName != null) {

                // ------ COMPATIBILITY APP CHECK ------
                // If this app is in the compatibility list AND Focus Mode is NOT active,
                // disable the accessibility service so the app can function normally.
                // SAFETY: This NEVER runs during Focus Mode.
                if (checkAndDisableForCompatibility(packageName)) return

                // ------ SELF-PROTECTION GUARDS ------
                // These guards activate when Focus Mode is active OR Advanced Protection is on
                if (shouldGuardSelf()) {
                    // Guard 1: Settings app — block App Info, Device Admin, Accessibility pages
                    if (packageName in SETTINGS_PACKAGES) {
                        if (guardMindVaultAppInfo(event)) return
                        if (guardDeviceAdminSettings(event)) return
                        if (guardAccessibilitySettings(event)) return
                    }

                    // Guard 2: Package Installer — block uninstall confirmation dialogs
                    if (packageName in INSTALLER_PACKAGES) {
                        if (guardUninstallDialog(event)) return
                    }

                    // Guard 3: Play Store — block MindVault's Play Store page
                    if (packageName == PLAY_STORE_PACKAGE) {
                        if (guardPlayStore(event)) return
                    }
                }

                // ------ APP BLOCKING ------
                checkAndBlockApp(packageName, event)
            }
        }
    }

    // ========================== COMPATIBILITY ==========================

    /**
     * Checks if the current foreground app is in the user's compatibility list
     * (e.g., banking apps). If so, AND Focus Mode is NOT active, the service
     * disables itself so the app can function normally.
     *
     * Returns true if the service disabled itself (caller should return immediately).
     */
    private fun checkAndDisableForCompatibility(packageName: String): Boolean {
        // SAFETY: Never disable during Focus Mode
        val focusActive = try {
            FocusManager.isInitialized() && FocusManager.isFocusModeActive()
        } catch (_: Exception) {
            false
        }
        if (focusActive) return false

        // Check if this app is in the compatibility list
        if (!AppCompatibilityManager.isCompatibilityApp(packageName, this)) return false

        Log.i(TAG, "COMPAT: Compatibility app detected ($packageName) — disabling Accessibility Service")

        // Mark that we auto-disabled (for the re-enable popup in MainActivity)
        AppCompatibilityManager.markAutoDisabled(true)

        // Send notification reminding user to re-enable
        try {
            CustomNotificationBuilder.showCompatibilityDisabledNotification(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send compatibility notification", e)
        }

        // Disable self — this is a real Android API on API 24+
        disableSelf()
        return true
    }

    // ========================== SELF-PROTECTION ==========================

    /**
     * Determines if self-protection guards should be active.
     * Guards activate when Focus Mode is currently running
     * OR when Advanced Protection is enabled in settings.
     * Outside of both, users have full access to device settings.
     */
    private fun shouldGuardSelf(): Boolean {
        val focusActive = try {
            FocusManager.isInitialized() && FocusManager.isFocusModeActive()
        } catch (_: Exception) {
            false
        }

        return focusActive || isAdvancedProtectionEnabled()
    }

    /**
     * Blocks the user from reaching MindVault's App Info page in Settings
     * during a protected session. This prevents the uninstall flow entirely.
     *
     * Uses two-pass scanning: first collects ALL text on screen, then checks
     * if the page is about MindVault + has dangerous actions.
     */
    private fun guardMindVaultAppInfo(event: AccessibilityEvent?): Boolean {
        try {
            val allText = collectEventText(event) ?: return false

            val hasMindVault = allText.contains("mindvault") ||
                    allText.contains("com.example.mindvault")

            if (!hasMindVault) return false

            // If we're on a page that mentions MindVault AND has dangerous actions
            val hasDangerousAction = allText.contains("uninstall") ||
                    allText.contains("force stop") ||
                    allText.contains("force-stop") ||
                    allText.contains("disable") ||
                    allText.contains("app info")

            if (hasDangerousAction) {
                Log.i(TAG, "GUARD: MindVault App Info page detected — pressing Back")
                performGlobalAction(GLOBAL_ACTION_BACK)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error guarding app info", e)
        }
        return false
    }

    /**
     * Guards the Device Admin settings page. Uses two-pass text collection
     * so that "MindVault" and "Deactivate" can be in different text nodes.
     *
     * Only blocks deactivation (not activation).
     */
    private fun guardDeviceAdminSettings(event: AccessibilityEvent?): Boolean {
        if (!isDeviceAdminCurrentlyActive()) return false
        try {
            val allText = collectEventText(event) ?: return false

            val hasMindVault = allText.contains("mindvault") ||
                    allText.contains("com.example.mindvault")

            if (!hasMindVault) return false

            val hasAdminAction = allText.contains("deactivate") ||
                    allText.contains("device admin") ||
                    allText.contains("remove this device admin") ||
                    allText.contains("deactivate this device admin")

            if (hasAdminAction) {
                Log.i(TAG, "GUARD: Device Admin deactivation page detected — pressing Back")
                performGlobalAction(GLOBAL_ACTION_BACK)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error guarding device admin settings", e)
        }
        return false
    }

    /**
     * Guards the Accessibility Settings page for MindVault. Prevents the user
     * from reaching the toggle to disable MindVault's accessibility service.
     */
    private fun guardAccessibilitySettings(event: AccessibilityEvent?): Boolean {
        try {
            val allText = collectEventText(event) ?: return false

            val hasMindVault = allText.contains("mindvault") ||
                    allText.contains("focus accessibility") ||
                    allText.contains("mindvault focus service")

            if (!hasMindVault) return false

            val hasAccessibilityContext = allText.contains("accessibility") ||
                    allText.contains("use mindvault") ||
                    allText.contains("installed service") ||
                    allText.contains("shortcut") ||
                    allText.contains("downloaded apps")

            if (hasAccessibilityContext) {
                Log.i(TAG, "GUARD: Accessibility settings for MindVault detected — pressing Back")
                performGlobalAction(GLOBAL_ACTION_BACK)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error guarding accessibility settings", e)
        }
        return false
    }

    /**
     * Guards the system uninstall confirmation dialog. When Android shows
     * "Do you want to uninstall this app?", we block it if it's about MindVault.
     */
    private fun guardUninstallDialog(event: AccessibilityEvent?): Boolean {
        try {
            val allText = collectEventText(event) ?: return false

            val hasMindVault = allText.contains("mindvault") ||
                    allText.contains("com.example.mindvault")

            if (!hasMindVault) return false

            val hasUninstallAction = allText.contains("uninstall") ||
                    allText.contains("do you want to") ||
                    allText.contains("remove") ||
                    allText.contains("deactivate")

            if (hasUninstallAction) {
                Log.i(TAG, "GUARD: Uninstall dialog for MindVault detected — pressing Back")
                performGlobalAction(GLOBAL_ACTION_BACK)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error guarding uninstall dialog", e)
        }
        return false
    }

    /**
     * Guards the Google Play Store. If the user opens MindVault's page on the
     * Play Store (where they could tap Uninstall), we press Back.
     */
    private fun guardPlayStore(event: AccessibilityEvent?): Boolean {
        try {
            val allText = collectEventText(event) ?: return false

            val hasMindVault = allText.contains("mindvault") ||
                    allText.contains("com.example.mindvault")

            // Only block if we see MindVault + uninstall on the Play Store page
            if (hasMindVault && allText.contains("uninstall")) {
                Log.i(TAG, "GUARD: Play Store uninstall page for MindVault detected — pressing Back")
                performGlobalAction(GLOBAL_ACTION_BACK)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error guarding Play Store", e)
        }
        return false
    }

    // ========================== TEXT COLLECTION ==========================

    private fun collectEventText(event: AccessibilityEvent?): String? {
        val source = event?.source ?: return null
        return try {
            collectAllText(source)
        } finally {
            source.recycle()
        }
    }

    /**
     * Collects ALL visible text from the entire view hierarchy into a single
     * lowercase string. This enables two-pass detection where keywords like
     * "MindVault" and "Deactivate" may appear in completely different nodes.
     */
    private fun collectAllText(node: AccessibilityNodeInfo, depth: Int = 0): String {
        if (depth > 20) return "" // Safety limit

        val sb = StringBuilder()
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (text.isNotEmpty()) sb.append(text).append(" ")
        if (desc.isNotEmpty()) sb.append(desc).append(" ")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                sb.append(collectAllText(child, depth + 1))
            } finally {
                child.recycle()
            }
        }
        return sb.toString().lowercase()
    }

    // ========================== APP BLOCKING ==========================

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

                // Coalesce accounting only; never suppress the blocking actions below.
                if (distractionBursts.shouldRecord(
                        StatisticsManager.currentSession.value?.id,
                        packageName,
                        SystemClock.elapsedRealtime()
                    )
                ) {
                    try {
                        StatisticsManager.recordDistraction(packageName)
                    } catch (e: Exception) {
                        distractionBursts.clear()
                        Log.e(TAG, "Failed to record distraction", e)
                    }
                }

                // Detect if the blocked app is running in a floating/freeform/split window
                val windowBounds = getWindowBoundsForPackage(packageName)
                val screenWidth = resources.displayMetrics.widthPixels
                val screenHeight = resources.displayMetrics.heightPixels
                val isFloating = windowBounds != null &&
                        (windowBounds.width() < screenWidth || windowBounds.height() < screenHeight)

                if (isFloating && windowBounds != null) {
                    Log.i(TAG, "Floating window detected for $packageName, showing overlay")
                    val label = AppManager.getAppName(this, packageName)
                    OverlayBlocker.show(this, packageName, windowBounds, label)
                    reconcileOverlaySchedule()
                    // Force-close the floating window
                    try {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    } catch (_: Exception) {
                    }
                } else {
                    hideOverlay()
                    showBlockedScreen(packageName)
                }
            }
        } else {
            // App is not blocked (or focus mode inactive) — clean up any lingering overlay
            hideOverlay()
            if (!isCurrentlyInFocusSession) distractionBursts.clear()
        }
    }

    private fun hideOverlay() {
        OverlayBlocker.hide(this)
        overlayBoundaryJob?.cancel()
        overlayBoundaryJob = null
    }

    private fun reconcileOverlaySchedule() {
        overlayBoundaryJob?.cancel()
        overlayBoundaryJob = null
        val target = OverlayBlocker.targetPackage(this) ?: return
        if (!FocusManager.isInitialized()) return
        // Capture before the policy check: crossing an end during this callback must
        // schedule an immediate recheck, not the same boundary tomorrow.
        val observedAt = ZonedDateTime.now()
        val observedElapsed = SystemClock.elapsedRealtime()
        val config = FocusManager.getCurrentConfiguration()
        // Deliberately retain accessibility's selected-app policy in BOTH slot types.
        // activeSlotFlow is permission-gated and is not an expiry signal for this overlay.
        if (!FocusManager.isFocusModeActive() || target !in config.selectedApps) {
            OverlayBlocker.hide(this)
        }
        if (OverlayBlocker.targetPackage(this) == null) return
        val waitMillis = OverlayScheduleBoundary.delayMillis(
            observedAt,
            config.timeSlots.flatMap { listOf(it.startTime, it.endTime) }
        ) ?: return
        overlayBoundaryJob = serviceScope.launch {
            delay((waitMillis - (SystemClock.elapsedRealtime() - observedElapsed)).coerceAtLeast(1L))
            reconcileOverlaySchedule()
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
     */
    private fun getWindowBoundsForPackage(packageName: String): Rect? {
        try {
            val windowList = windows ?: return null
            for (window in windowList) {
                val root = window.root ?: continue
                try {
                    if (root.packageName?.toString() == packageName) {
                        val rect = Rect()
                        window.getBoundsInScreen(rect)
                        if (rect.width() > 0 && rect.height() > 0) {
                            return rect
                        }
                    }
                } finally {
                    root.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning windows for $packageName", e)
        }
        return null
    }

    // ========================== HELPERS ==========================

    /**
     * Checks if MindVault's Device Admin is currently active.
     */
    private fun isDeviceAdminCurrentlyActive(): Boolean {
        return try {
            val dpm =
                getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val adminComponent = android.content.ComponentName(
                this,
                com.example.mindvault.receivers.MindVaultDeviceAdminReceiver::class.java
            )
            dpm.isAdminActive(adminComponent)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if Advanced Protection is currently enabled in SharedPreferences.
     */
    private fun isAdvancedProtectionEnabled(): Boolean {
        return try {
            val prefs = getSharedPreferences("mindvault_prefs", android.content.Context.MODE_PRIVATE)
            prefs.getBoolean("advanced_protection_enabled", false)
        } catch (_: Exception) {
            false
        }
    }

    // ========================== LIFECYCLE ==========================

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
        configurationJob?.cancel()
        configurationJob = serviceScope.launch {
            FocusManager.configurationFlow.collect { reconcileOverlaySchedule() }
        }
        if (!clockReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                clockReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                    addAction(Intent.ACTION_SCREEN_ON)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            clockReceiverRegistered = true
        }
        Log.i(TAG, "Accessibility Service connected and configured.")
        isServiceRunning = true
    }

    override fun onDestroy() {
        serviceScope.cancel()
        distractionBursts.clear()
        if (clockReceiverRegistered) {
            unregisterReceiver(clockReceiver)
            clockReceiverRegistered = false
        }
        super.onDestroy()
        // The singleton overlay holds a service-context view; leaking it keeps the
        // dead service alive and the blocker on screen.
        OverlayBlocker.hide(this)
        isServiceRunning = false
        Log.i(TAG, "Accessibility Service destroyed.")
    }

    companion object {
        var isServiceRunning = false
            private set
    }
}
