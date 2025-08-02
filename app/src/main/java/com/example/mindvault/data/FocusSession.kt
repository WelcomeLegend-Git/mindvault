package com.example.mindvault.data

import android.content.Context
import android.content.Intent
import android.util.Log

import java.time.LocalTime
import com.example.mindvault.model.FocusConfiguration
import com.example.mindvault.model.FocusType
import com.example.mindvault.model.TimeSlot
import com.example.mindvault.utils.PermissionManager
import com.example.mindvault.utils.AppManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

object FocusManager {
    private lateinit var appContext: Context
    private var currentConfiguration: FocusConfiguration = FocusConfiguration()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _activeSlotFlow = MutableStateFlow<TimeSlot?>(null)
    val activeSlotFlow = _activeSlotFlow.asStateFlow()

    private val _configurationFlow = MutableStateFlow(FocusConfiguration())
    val configurationFlow = _configurationFlow.asStateFlow()

    fun init(context: Context) {
        Log.d("FocusManager", "Initializing FocusManager")
        appContext = context.applicationContext
        currentConfiguration = FocusDataStore.getConfiguration(appContext)
        _configurationFlow.value = currentConfiguration
        
        // Initialize StatisticsManager
        StatisticsManager.init(appContext)
        
        Log.d("FocusManager", "FocusManager initialized with ${currentConfiguration.timeSlots.size} time slots")
        startMonitoring()
    }
    
    fun isInitialized(): Boolean {
        return ::appContext.isInitialized
    }
    
    fun areAllPermissionsGranted(): Boolean {
        if (!::appContext.isInitialized) {
            return false
        }
        return PermissionManager.hasUsageStatsPermission(appContext) &&
               PermissionManager.hasOverlayPermission(appContext) &&
               AppManager.hasNotificationListenerPermission(appContext) &&
               PermissionManager.isAccessibilityServiceEnabled(appContext)
    }

    fun updateConfiguration(config: FocusConfiguration) {
        if (!::appContext.isInitialized) {
            Log.e("FocusManager", "FocusManager not initialized! Cannot update configuration")
            return
        }
        Log.d("FocusManager", "Updating configuration with ${config.timeSlots.size} slots.")

        // 1. Save the new configuration to persistent storage
        FocusDataStore.saveConfiguration(appContext, config)

        // Immediately reload the configuration to ensure the in-memory state is up-to-date
                currentConfiguration = FocusDataStore.getConfiguration(appContext)
        _configurationFlow.value = currentConfiguration
        Log.d("FocusManager", "Configuration reloaded. Active slots: ${currentConfiguration.timeSlots.size}")

        // The Accessibility Service will pick up the new configuration automatically.
        Log.d("FocusManager", "Configuration updated. Accessibility Service will enforce new rules.")

        // Immediately update the flow with the new configuration
        checkAndUpdateActiveSlot()

        // Automatically back up the new configuration to the cloud
        coroutineScope.launch {
            Log.d("FocusManager", "Triggering cloud sync after configuration update.")
            AuthManager.syncUserDataToCloud()
        }
    }





    fun getCurrentConfiguration(): FocusConfiguration {
        if (!::appContext.isInitialized) {
            Log.e("FocusManager", "FocusManager not initialized! Returning empty configuration")
            return FocusConfiguration()
        }
        Log.d("FocusManager", "Getting current configuration with ${currentConfiguration.timeSlots.size} time slots")
        return currentConfiguration
    }
    
    fun checkAndBlockCurrentlyRunningApps() {
        if (!::appContext.isInitialized) {
            Log.w("FocusManager", "FocusManager not initialized, skipping running apps check.")
            return
        }
        
        if (!isFocusModeActive()) {
            Log.d("FocusManager", "Focus mode not active, skipping running apps check.")
            return
        }
        
        Log.d("FocusManager", "Checking for currently running apps to block...")
        
        // Send broadcast to trigger the check in the accessibility service
        val intent = Intent("com.example.mindvault.FOCUS_SESSION_STARTED")
        appContext.sendBroadcast(intent)
    }
    
    fun sendFocusSessionStartedBroadcast() {
        if (!::appContext.isInitialized) {
            Log.w("FocusManager", "FocusManager not initialized, cannot send broadcast.")
            return
        }
        
        Log.d("FocusManager", "Sending focus session started broadcast...")
        val intent = Intent("com.example.mindvault.FOCUS_SESSION_STARTED")
        appContext.sendBroadcast(intent)
    }
    
    fun getCurrentActiveSlot(): TimeSlot? {
        if (!::appContext.isInitialized) {
            Log.e("FocusManager", "FocusManager not initialized! Cannot get active slot")
            return null
        }
        val currentTime = LocalTime.now()
        return currentConfiguration.timeSlots.find { slot ->
            isTimeInRange(currentTime, slot.startTime, slot.endTime)
        }
    }
    
    fun isAppBlocked(packageName: String): Boolean {
        if (!::appContext.isInitialized) {
            Log.e("FocusManager", "FocusManager not initialized! Cannot check if app is blocked")
            return false
        }
        val activeSlot = getCurrentActiveSlot() ?: return false
        val isSelectedApp = currentConfiguration.selectedApps.contains(packageName)
        
        return when (activeSlot.type) {
                FocusType.STUDY_TIME -> isSelectedApp // Block selected apps during study time
                FocusType.REST_TIME -> !isSelectedApp // Block non-selected apps during rest time
            }
    }
    
    private fun isTimeInRange(current: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        // Handle overnight sessions (e.g., 11 PM to 7 AM)
        return if (start.isAfter(end)) {
            (current.isAfter(start) || current == start) || (current.isBefore(end) && current != end)
        } else {
            // Handle same-day sessions (e.g., 9 AM to 5 PM)
            (current.isAfter(start) || current == start) && current.isBefore(end)
        }
    }
    
    fun isFocusModeActive(): Boolean {
        if (!::appContext.isInitialized) {
            Log.e("FocusManager", "FocusManager not initialized! Cannot check if focus mode is active")
            return false
        }
        return getCurrentActiveSlot() != null
    }
    
    private fun startMonitoring() {
        coroutineScope.launch {
            while (true) {
                checkAndUpdateActiveSlot()
                delay(1000) // Check every second
            }
        }
    }

    private fun checkAndUpdateActiveSlot() {
        val currentTime = LocalTime.now()
        val potentialActiveSlot = currentConfiguration.timeSlots.find {
            isTimeInRange(currentTime, it.startTime, it.endTime)
        }
        
        // Only consider slot "active" if all permissions are granted
        val activeSlot = if (areAllPermissionsGranted()) potentialActiveSlot else null

        if (_activeSlotFlow.value?.id != activeSlot?.id) {
            // End previous session if there was one
            if (_activeSlotFlow.value != null && activeSlot == null) {
                StatisticsManager.endFocusSession(completed = true)
                Log.d("FocusManager", "Ended focus session")
            }
            
            // Start new session if entering a slot AND permissions are granted
            if (_activeSlotFlow.value == null && activeSlot != null) {
                StatisticsManager.startFocusSession(
                    type = activeSlot.type.name,
                    blockedApps = currentConfiguration.selectedApps
                )
                Log.d("FocusManager", "Started focus session: ${activeSlot.type}")
                
                // Check and block currently running apps when session starts
                checkAndBlockCurrentlyRunningApps()
            }
            
            _activeSlotFlow.value = activeSlot
            Log.d("FocusManager", "Active slot changed: ${activeSlot?.type ?: "None"} (Permissions: ${areAllPermissionsGranted()})")
        }
    }

    fun getNextSlotInfo(): String {
        if (!::appContext.isInitialized) {
            Log.e("FocusManager", "FocusManager not initialized! Cannot get next slot info")
            return "Focus mode not initialized"
        }
        
        if (currentConfiguration.timeSlots.isEmpty()) {
            Log.d("FocusManager", "No time slots configured")
            return "No time slots configured"
        }
        
        val currentTime = LocalTime.now()
        Log.d("FocusManager", "Current time: $currentTime")
        Log.d("FocusManager", "Available time slots: ${currentConfiguration.timeSlots.map { "${it.startTime}-${it.endTime} (${it.type})" }}")
        
        // Find the next upcoming slot
        val upcomingSlots = currentConfiguration.timeSlots
            .filter { slot ->
                // Check if slot starts after current time (same day)
                val isUpcoming = slot.startTime.isAfter(currentTime)
                Log.d("FocusManager", "Slot ${slot.startTime}-${slot.endTime}: isUpcoming=$isUpcoming")
                isUpcoming
            }
            .sortedBy { it.startTime }
        
        return if (upcomingSlots.isNotEmpty()) {
            val nextSlot = upcomingSlots.first()
            val typeDisplay = when (nextSlot.type) {
                FocusType.STUDY_TIME -> "Study Time"
                FocusType.REST_TIME -> "Rest Time"
            }
            val timeDisplay = nextSlot.startTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            "Next: $typeDisplay at $timeDisplay"
        } else {
            // Check if there are any slots for tomorrow (wrap around)
            val tomorrowSlots = currentConfiguration.timeSlots
                .sortedBy { it.startTime }
            if (tomorrowSlots.isNotEmpty()) {
                val nextSlot = tomorrowSlots.first()
                val typeDisplay = when (nextSlot.type) {
                    FocusType.STUDY_TIME -> "Study Time"
                    FocusType.REST_TIME -> "Rest Time"
                }
                val timeDisplay = nextSlot.startTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                "Next: $typeDisplay at $timeDisplay (tomorrow)"
            } else {
                "No upcoming sessions"
            }
        }
    }
}
