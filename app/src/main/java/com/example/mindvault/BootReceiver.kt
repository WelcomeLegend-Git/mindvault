package com.example.mindvault

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.mindvault.data.FocusManager

class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed, checking for active focus session.")

            // Initialize FocusManager. It's safe to call this again.
            FocusManager.init(context.applicationContext)

            Log.i(TAG, "FocusManager initialized on boot.")
        }
    }
}
