package com.example.mindvault.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.mindvault.data.FocusManager

class FocusNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotificationListener", "Notification Listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        Log.d("NotificationListener", "Notification posted from: $packageName")

        if (FocusManager.isInitialized() && FocusManager.isAppBlocked(packageName)) {
            Log.i("NotificationListener", "Blocking notification from: $packageName")
            cancelNotification(sbn.key)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("NotificationListener", "Notification Listener disconnected")
    }
}
