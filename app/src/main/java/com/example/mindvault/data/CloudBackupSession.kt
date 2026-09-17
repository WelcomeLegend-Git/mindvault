package com.example.mindvault.data

enum class CloudBackupStatus { SIGNED_OUT, CHECKING, READY, RESTORE_AVAILABLE, IMPORT_AVAILABLE, BLOCKED, ERROR, UPLOADING }

data class CloudBackupState(
    val status: CloudBackupStatus = CloudBackupStatus.SIGNED_OUT,
    val message: String = "Cloud backup is off. Sign in with Google to recover or back up data.",
    val accountEmail: String? = null,
    val revision: Long? = null
) {
    val busy: Boolean get() = status == CloudBackupStatus.CHECKING || status == CloudBackupStatus.UPLOADING
}

/** Decisions require a successful server read; unknown/offline is never equivalent to absent. */
internal object CloudBackupRecovery {
    enum class Decision { KEEP_LOCAL, OFFER_RESTORE, OFFER_IMPORT, BLOCK_OWNER }

    fun decide(owner: String?, uid: String, backupExists: Boolean, incompleteRestore: Boolean): Decision = when {
        owner != null && owner != uid -> Decision.BLOCK_OWNER
        backupExists && (owner != uid || incompleteRestore) -> Decision.OFFER_RESTORE
        backupExists -> Decision.KEEP_LOCAL
        else -> Decision.OFFER_IMPORT
    }

    fun mayRestore(owner: String?, uid: String, sessionRunning: Boolean, configurationActive: Boolean): Boolean =
        (owner == null || owner == uid) && !sessionRunning && !configurationActive
}

/** A revision invalidates work captured before sign-out, even if the same account returns. */
internal class CloudBackupSession {
    data class Session(val revision: Long, val uid: String, val localUserId: String)

    private var revision = 0L
    private var active: Session? = null
    private var uploadsEnabled = false

    @Synchronized
    fun invalidate(): Long {
        active = null
        uploadsEnabled = false
        return ++revision
    }

    @Synchronized
    fun isRevisionCurrent(expected: Long): Boolean = revision == expected

    @Synchronized
    fun activate(expected: Long, uid: String, localUserId: String, uploadReady: Boolean = true): Boolean {
        if (revision != expected) return false
        active = Session(expected, uid, localUserId)
        uploadsEnabled = uploadReady
        return true
    }

    @Synchronized
    fun current(): Session? = active

    @Synchronized
    fun setUploadReady(session: Session, ready: Boolean): Boolean {
        if (active != session || revision != session.revision) return false
        uploadsEnabled = ready
        return true
    }

    @Synchronized
    fun matches(session: Session, firebaseUid: String?, localUserId: String?): Boolean =
        active == session && revision == session.revision && session.uid == firebaseUid &&
                session.localUserId == localUserId

    @Synchronized
    fun permits(session: Session, firebaseUid: String?, localUserId: String?, ownerUid: String?): Boolean =
        uploadsEnabled && matches(session, firebaseUid, localUserId) && session.uid == ownerUid
}

internal object AccountBackupData {
    private val profileFields = setOf(
        "name", "email", "role", "created", "last_active", "active", "profile_pic",
        "theme", "notifications", "weekly_goal", "monthly_goal", "reminder", "export", "analytics"
    )

    fun profile(values: Map<String, *>, userId: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>(
            "current_user_id" to userId,
            "all_user_ids" to setOf(userId)
        )
        profileFields.forEach { field ->
            val key = "user_${userId}_$field"
            values[key]?.let { result[key] = it }
        }
        return result
    }

    /** Import only this profile's allowlisted fields, retaining device-local IDs and other users. */
    fun restoredProfile(local: Map<String, Any>, remote: Map<String, Any>, localUserId: String): Map<String, Any> {
        val remoteId = remote["current_user_id"] as? String ?: error("Backup profile is missing")
        val result = local.toMutableMap()
        profileFields.forEach { field ->
            val localKey = "user_${localUserId}_$field"
            result.remove(localKey)
            remote["user_${remoteId}_$field"]?.let { result[localKey] = it }
        }
        result["current_user_id"] = localUserId
        result["all_user_ids"] = (local["all_user_ids"] as? Set<*>).orEmpty()
            .map { it as String }.toSet() + localUserId
        return result
    }

    fun mayClaimLocalData(ownerUid: String?, uid: String, isEmpty: Boolean): Boolean =
        ownerUid == uid || (ownerUid == null && isEmpty)
}
