package com.example.mindvault.services

import android.app.Notification
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.util.Log
import com.example.mindvault.data.FocusManager

class FocusNotificationListenerService : NotificationListenerService() {

    private val TAG = "NotificationListener"
    private val handler = Handler(Looper.getMainLooper())
    private var savedRingerMode: Int? = null
    private var savedRingVolume: Int? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification Listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        Log.d(TAG, "Notification posted from: $packageName")

        if (FocusManager.isInitialized() && FocusManager.isAppBlocked(packageName)) {
            val isCallNotification = isCallNotification(sbn)

            if (isCallNotification) {
                Log.i(TAG, "Silencing incoming call from blocked app: $packageName")
                muteRinger()
                // Try to decline native phone calls
                if (isNativeDialer(packageName)) {
                    declineCall()
                }
            }

            Log.i(TAG, "Blocking notification from: $packageName")
            cancelNotification(sbn.key)

            // Restore ringer after a short delay (gives time for the notification
            // to be fully cancelled and any ringtone to stop)
            if (isCallNotification) {
                handler.postDelayed({ restoreRinger() }, 3000)
            }
        }
    }

    /**
     * Checks whether a notification is a call notification.
     * Detects both native phone calls (CATEGORY_CALL) and VoIP calls
     * (WhatsApp, Telegram, etc.) which may use CATEGORY_CALL or
     * full-screen intents typical of call UIs.
     */
    private fun isCallNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification

        // Standard call category (used by most calling apps)
        if (notification.category == Notification.CATEGORY_CALL) {
            return true
        }

        // Some apps use full-screen intent for incoming calls without CATEGORY_CALL
        if (notification.fullScreenIntent != null) {
            return true
        }

        // Check for known VoIP calling app patterns
        val pkg = sbn.packageName
        val knownCallingApps = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "com.google.android.apps.tachyon",  // Google Duo/Meet
            "com.viber.voip",
            "com.skype.raider",
            "us.zoom.videomeetings",
            "com.discord",
            "com.facebook.orca",      // Messenger
            "com.facebook.mlite",     // Messenger Lite
            "com.microsoft.teams",
            "com.Slack"
        )
        if (knownCallingApps.contains(pkg) && notification.fullScreenIntent != null) {
            return true
        }

        return false
    }

    /**
     * Checks if the package is the system's native phone/dialer app.
     */
    private fun isNativeDialer(packageName: String): Boolean {
        val dialerPackages = setOf(
            "com.android.dialer",
            "com.android.incallui",
            "com.android.phone",
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer"
        )
        return dialerPackages.contains(packageName)
    }

    /**
     * Temporarily mutes the device ringer to silence an incoming call.
     */
    private fun muteRinger() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Save current state to restore later
            if (savedRingerMode == null) {
                savedRingerMode = audioManager.ringerMode
                savedRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            }

            // Mute the ring stream
            audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
            // Also try setting ringer mode to silent (may require DND access on some devices)
            try {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            } catch (e: SecurityException) {
                // DND access not granted — volume mute above should still work
                Log.w(TAG, "Cannot set ringer mode to silent (DND access needed)", e)
            }

            Log.d(TAG, "Ringer muted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mute ringer", e)
        }
    }

    /**
     * Restores the ringer to its previous state.
     */
    private fun restoreRinger() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val mode = savedRingerMode
            val volume = savedRingVolume

            if (mode != null) {
                try {
                    audioManager.ringerMode = mode
                } catch (e: SecurityException) {
                    Log.w(TAG, "Cannot restore ringer mode", e)
                }
            }

            if (volume != null) {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, volume, 0)
            }

            savedRingerMode = null
            savedRingVolume = null
            Log.d(TAG, "Ringer restored")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore ringer", e)
        }
    }

    /**
     * Attempts to decline/end an incoming native phone call using TelecomManager.
     * Requires API 28+.
     */
    private fun declineCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                @Suppress("DEPRECATION")
                val ended = telecomManager.endCall()
                Log.i(TAG, "Attempted to decline call: success=$ended")
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot decline call (ANSWER_PHONE_CALLS permission needed)", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decline call", e)
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Clean up any muted state
        restoreRinger()
        Log.d(TAG, "Notification Listener disconnected")
    }
}
