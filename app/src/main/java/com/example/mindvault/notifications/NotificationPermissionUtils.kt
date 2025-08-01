package com.example.mindvault.notifications

import android.Manifest
import androidx.activity.ComponentActivity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

object NotificationPermissionUtils {
    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun requestPermission(activity: ComponentActivity, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(true)
            return
        }
        val launcher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            onResult(granted)
        }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
