package com.example.mindvault.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Firestore-based OTA update checker.
 *
 * How it works:
 * 1. On app launch, reads "config/app_update" document from Firestore
 * 2. Compares the remote versionCode with the installed versionCode
 * 3. If newer, shows an update dialog with release notes + download link
 *
 * To push an update:
 * 1. Build your APK and upload it anywhere (Google Drive, GitHub Releases, your server, etc.)
 * 2. Go to Firebase Console → Firestore → "config" collection → "app_update" document
 * 3. Set/update these fields:
 *    - versionCode: 5           (number — must be higher than current)
 *    - versionName: "3.2.0"     (string — displayed to user)
 *    - downloadUrl: "https://..." (string — direct APK download link)
 *    - releaseNotes: "Bug fixes" (string — shown in the dialog)
 *    - forceUpdate: false        (boolean — if true, user can't skip)
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"

    fun checkForUpdate(activity: Activity) {
        try {
            val currentVersionCode = activity.packageManager
                .getPackageInfo(activity.packageName, 0)
                .let {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        it.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        it.versionCode
                    }
                }

            FirebaseFirestore.getInstance()
                .collection("config")
                .document("app_update")
                .get()
                .addOnSuccessListener { doc ->
                    if (doc == null || !doc.exists()) {
                        Log.d(TAG, "No update config found in Firestore")
                        return@addOnSuccessListener
                    }

                    val remoteVersionCode = doc.getLong("versionCode")?.toInt() ?: return@addOnSuccessListener
                    val versionName = doc.getString("versionName") ?: "New Version"
                    val downloadUrl = doc.getString("downloadUrl") ?: return@addOnSuccessListener
                    val releaseNotes = doc.getString("releaseNotes") ?: ""
                    val forceUpdate = doc.getBoolean("forceUpdate") ?: false

                    if (remoteVersionCode > currentVersionCode) {
                        Log.i(TAG, "Update available: $versionName (code $remoteVersionCode)")
                        showUpdateDialog(activity, versionName, releaseNotes, downloadUrl, forceUpdate)
                    } else {
                        Log.d(TAG, "App is up to date (current=$currentVersionCode, remote=$remoteVersionCode)")
                    }
                }
                .addOnFailureListener { e ->
                    Log.d(TAG, "Update check failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.d(TAG, "Update checker error: ${e.message}")
        }
    }

    private fun showUpdateDialog(
        activity: Activity,
        versionName: String,
        releaseNotes: String,
        downloadUrl: String,
        forceUpdate: Boolean
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val message = buildString {
            append("Version $versionName is available!")
            if (releaseNotes.isNotBlank()) {
                append("\n\n$releaseNotes")
            }
        }

        val builder = android.app.AlertDialog.Builder(activity)
            .setTitle("Update Available")
            .setMessage(message)
            .setPositiveButton("Update Now") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open download URL", e)
                }
            }
            .setCancelable(!forceUpdate)

        if (!forceUpdate) {
            builder.setNegativeButton("Later", null)
        }

        builder.show()
    }
}
