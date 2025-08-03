package com.example.mindvault

import android.app.Application
import android.util.Log
import com.example.mindvault.data.AuthManager
import com.example.mindvault.data.FocusManager
import com.example.mindvault.data.StatisticsManager
import com.example.mindvault.data.UserManager
import com.example.mindvault.data.AppPasswordManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MindVaultApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    companion object {
        lateinit var instance: MindVaultApplication
            private set
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
        AuthManager.init(this)
        Log.d("MindVaultApplication", "Application onCreate() called")
        
        // Create a simple file to verify this method is called
        try {
            val file = java.io.File(filesDir, "app_started.txt")
            file.writeText("Application started at ${System.currentTimeMillis()}")
            Log.d("MindVaultApplication", "Created verification file at ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("MindVaultApplication", "Error creating verification file", e)
        }
        
        // Initialize managers
        try {
            FocusManager.init(this)
            StatisticsManager.init(this)
            UserManager.init(this)
            AppPasswordManager.init(this)
            // Create notification channels
            com.example.mindvault.notifications.NotificationHelper.createNotificationChannels(this)
            
            Log.d("MindVaultApplication", "All managers initialized successfully")
        } catch (e: Exception) {
            Log.e("MindVaultApplication", "Error initializing managers", e)
        }
    }
}