package com.example.mindvault.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Background worker that uploads user data (stats, achievements, focus prefs, users)
 * to Firestore. It is scheduled periodically and also used for one‑time retries
 * when previous foreground sync attempts fail due to no network.
 */
class BackupSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val ok = AuthManager.syncUserDataToCloud()
            if (ok) {
                Log.d("BackupSyncWorker", "Cloud backup completed successfully")
                Result.success()
            } else {
                Log.w("BackupSyncWorker", "Cloud backup failed, will retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("BackupSyncWorker", "Exception during backup", e)
            Result.retry()
        }
    }
}


