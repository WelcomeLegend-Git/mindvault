package com.example.mindvault.data

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Disk adapter and lock ordering for the three device-wide preference stores. */
internal object LocalRestore {
    private const val JOURNAL = "mindvault_restore_journal"
    private const val IMAGE = "undo_v1"
    val dataNames = listOf("mindvault_users", "FocusModePrefs", "mindvault_stats")
    private val provenanceNames = listOf("FocusModePrefs", "mindvault_stats")

    @Volatile
    private var blocked = false

    fun blockUntilRestart() {
        blocked = true
    }

    fun checkWritable() {
        check(!blocked) { "Local restore recovery is required. Restart the app before changing data." }
    }

    // Match the existing Focus -> Statistics call order. None of these blocks may suspend.
    fun <T> exclusive(block: () -> T): T = FocusManager.withPersistenceLock {
        synchronized(FocusDataStore) {
            synchronized(StatisticsManager) {
                synchronized(UserManager) { block() }
            }
        }
    }

    private fun prefs(context: Context, name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun snapshot(context: Context): PreferenceImage = dataNames.associateWith { name ->
        prefs(context, name).all.mapValues { (_, value) ->
            checkNotNull(value).let { if (it is Set<*>) it.toSet() else it }
        }
    }

    private fun storage(context: Context) = object : RestoreTransaction.Storage {
        override fun readJournal(): PreferenceImage? {
            val json = prefs(context, JOURNAL).getString(IMAGE, null) ?: return null
            val root = JsonParser.parseString(json).asJsonObject
            require(root.keySet() == dataNames.toSet()) { "Invalid local restore journal" }
            // Decode the ENTIRE journal before modifying any store.
            return dataNames.associateWith { PreferencesBackup.decode(it, root.get(it).asString) }
        }

        override fun prepare(before: PreferenceImage) {
            check(!prefs(context, JOURNAL).contains(IMAGE)) { "Unrecovered local restore journal" }
            val root = JsonObject()
            before.forEach { (name, values) -> root.addProperty(name, PreferencesBackup.encode(values)) }
            check(prefs(context, JOURNAL).edit().putString(IMAGE, root.toString()).commit()) {
                "Could not persist local restore journal"
            }
        }

        override fun replace(name: String, values: Map<String, Any>) {
            require(name in dataNames)
            val editor = prefs(context, name).edit().clear()
            values.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
                    else -> error("Unsupported preference: $name/$key")
                }
            }
            check(editor.commit()) { "Could not persist $name" }
        }

        override fun clearJournal() {
            check(prefs(context, JOURNAL).edit().remove(IMAGE).commit()) { "Could not retire local restore journal" }
        }
    }

    /** Must finish before AuthManager or any data manager loads. Failure intentionally fails startup. */
    fun recoverAtStartup(context: Context) = exclusive {
        blocked = true
        RestoreTransaction(storage(context)).recover()
        val identity = prefs(context, "mindvault_cloud_identity")
        val token = DeviceDataOwnership.resolve(
            identity.getString("local_data_owner", null),
            provenanceNames.map { name -> snapshotValues(context, name) })
        provenanceNames.forEach { name ->
            if (prefs(context, name).getString(DeviceDataOwnership.KEY, null) != token) {
                check(prefs(context, name).edit().putString(DeviceDataOwnership.KEY, token).commit()) {
                    "Could not preserve device data provenance"
                }
            }
        }
        // Only an interrupted CLOUD sign-out cleanup may clear the local login. The
        // cloud_signed_out flag alone also gets set for guest entry, whose local session
        // must survive restart, so it must not trigger this.
        if (identity.getBoolean("pending_local_logout", false)) {
            val users = prefs(context, "mindvault_users")
            if (users.contains("current_user_id")) check(users.edit().remove("current_user_id").commit())
        }
        blocked = false
    }

    private fun snapshotValues(context: Context, name: String): Map<String, Any> =
        prefs(context, name).all.mapValues { checkNotNull(it.value) }

    fun owner(context: Context): String? = DeviceDataOwnership.owner(
        DeviceDataOwnership.resolve(
            prefs(context, "mindvault_cloud_identity").getString("local_data_owner", null),
            provenanceNames.map { snapshotValues(context, it) }
        ))

    /** Reserve ownership BEFORE network submission, including timeout/cancellation/sign-out paths. */
    fun claim(context: Context, uid: String) = exclusive {
        checkWritable()
        check(owner(context).let { it == null || it == uid }) { "Device data belongs to another or unknown owner" }
        // Identity first: a partial provenance write still leaves a durable, conservative owner.
        check(prefs(context, "mindvault_cloud_identity").edit().putString("local_data_owner", uid).commit())
        provenanceNames.forEach { name ->
            check(
                prefs(context, name).edit().putString(DeviceDataOwnership.KEY, DeviceDataOwnership.token(uid)).commit()
            )
        }
    }

    fun restore(context: Context, after: PreferenceImage, valid: () -> Boolean) {
        checkWritable()
        val transaction = RestoreTransaction(storage(context))
        try {
            transaction.execute(snapshot(context), after, valid)
        } finally {
            blocked = transaction.recoveryRequired
        }
    }
}
