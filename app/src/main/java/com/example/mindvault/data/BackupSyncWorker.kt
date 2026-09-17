package com.example.mindvault.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

/**
 * Background worker that uploads the signed-in account's data to Firestore. It is
 * scheduled periodically and used for one-time retries when a foreground sync fails.
 * Uploads are bound to the account that requested them via [ACCOUNT_UID], so a retry
 * that runs after sign-out or an account change is skipped instead of misfiled.
 */
class BackupSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val expectedUid = inputData.getString(ACCOUNT_UID)
        return try {
            when (AuthManager.backupToCloud(expectedUid)) {
                CloudBackupResult.UPLOADED -> Result.success()
                // Signed out or account changed: retrying cannot help and must not re-upload.
                CloudBackupResult.SKIPPED -> Result.success()
                CloudBackupResult.RETRY -> Result.retry()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("BackupSyncWorker", "Exception during backup", e)
            Result.retry()
        }
    }

    companion object {
        const val ACCOUNT_UID = "account_uid"
    }
}
