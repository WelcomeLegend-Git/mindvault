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
        

        
        // Initialize managers (but don't create demo users)
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
    
    private fun createSampleStatistics() {
        try {
            // Create sample data for demonstration
            val prefs = getSharedPreferences("mindvault_stats", MODE_PRIVATE)
            val today = java.time.LocalDate.now()
            val dateKey = today.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            
            // Only create sample data if none exists
            if (prefs.getLong("daily_focus_${dateKey}", 0L) == 0L) {
                val editor = prefs.edit()
                
                // Today's sample data
                editor.putLong("daily_focus_${dateKey}", 145L) // 2 hours 25 minutes
                editor.putLong("daily_study_${dateKey}", 95L) // 1 hour 35 minutes
                editor.putLong("daily_rest_${dateKey}", 50L) // 50 minutes
                editor.putInt("daily_completed_${dateKey}", 4)
                editor.putInt("daily_total_${dateKey}", 5)
                editor.putInt("daily_distractions_${dateKey}", 2)
                editor.putFloat("daily_productivity_${dateKey}", 92.5f)
                
                // Previous days sample data
                for (i in 1..7) {
                    val pastDate = today.minusDays(i.toLong())
                    val pastDateKey = pastDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                    val focusTime = (60..180).random().toLong()
                    val studyTime = (focusTime * 0.6).toLong()
                    val restTime = focusTime - studyTime
                    
                    editor.putLong("daily_focus_${pastDateKey}", focusTime)
                    editor.putLong("daily_study_${pastDateKey}", studyTime)
                    editor.putLong("daily_rest_${pastDateKey}", restTime)
                    editor.putInt("daily_completed_${pastDateKey}", (2..6).random())
                    editor.putInt("daily_total_${pastDateKey}", (3..7).random())
                    editor.putInt("daily_distractions_${pastDateKey}", (0..5).random())
                    editor.putFloat("daily_productivity_${pastDateKey}", (75..98).random().toFloat())
                }
                
                // User stats
                editor.putLong("total_focus_hours", 45L) // 45 hours total
                editor.putInt("total_sessions", 67)
                editor.putInt("experience_points", 3450)
                editor.putInt("longest_streak", 12)
                editor.putLong("weekly_goal", 1200L) // 20 hours
                editor.putLong("monthly_goal", 5000L) // ~83 hours
                
                // Achievements
                editor.putString("achievements", "first_session,week_warrior,focus_master,distraction_free")
                
                editor.apply()
                Log.d("MindVaultApplication", "Created sample statistics data")
            }
            
        } catch (e: Exception) {
            Log.e("MindVaultApplication", "Error creating sample statistics", e)
        }
    }
}