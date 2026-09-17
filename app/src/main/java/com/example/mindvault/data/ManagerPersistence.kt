package com.example.mindvault.data

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth

/** No manager lock is acquired here: callers already hold their own writer lock. */
internal object ManagerPersistence {
    fun writable(): Boolean = try {
        LocalRestore.checkWritable()
        true
    } catch (_: IllegalStateException) {
        false
    }

    /** Stamp the SAME editor as the data mutation, so apply/commit cannot persist B's data
     * without also persisting the loss of A's exclusive provenance. Never reassign ownership.
     */
    fun attribute(
        context: Context,
        prefs: SharedPreferences,
        editor: SharedPreferences.Editor
    ): SharedPreferences.Editor = editor.putString(DeviceDataOwnership.KEY, nextToken(context, prefs))

    fun observe(context: Context, prefs: SharedPreferences) {
        val next = nextToken(context, prefs)
        if (prefs.getString(DeviceDataOwnership.KEY, null) != next) {
            prefs.edit().putString(DeviceDataOwnership.KEY, next).apply()
        }
    }

    private fun nextToken(context: Context, prefs: SharedPreferences): String {
        val identity = context.getSharedPreferences("mindvault_cloud_identity", Context.MODE_PRIVATE)
        val users = context.getSharedPreferences("mindvault_users", Context.MODE_PRIVATE)
        val principal = runCatching {
            val firebase = FirebaseAuth.getInstance().currentUser
            val localId = users.getString("current_user_id", null)
            WriteProvenance.principal(
                identity.getBoolean("cloud_signed_out", false), firebase?.uid, firebase?.email,
                localId?.let { users.getString("user_${it}_email", null) }
            )
        }.getOrNull()
        val token = prefs.getString(DeviceDataOwnership.KEY, null)
            ?: identity.getString("local_data_owner", null)?.let(DeviceDataOwnership::token)
            ?: DeviceDataOwnership.UNKNOWN
        return WriteProvenance.afterWrite(token, principal)
    }
}
