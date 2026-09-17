package com.example.mindvault.data

internal typealias PreferenceImage = Map<String, Map<String, Any>>

/** Single-process undo transaction. The caller must exclude ALL writers until this returns. */
internal class RestoreTransaction(private val storage: Storage) {
    interface Storage {
        fun readJournal(): PreferenceImage?
        fun prepare(before: PreferenceImage)
        fun replace(name: String, values: Map<String, Any>)
        fun clearJournal()
    }

    var recoveryRequired: Boolean = false
        private set

    fun recover() {
        recoveryRequired = true
        val before = storage.readJournal()
        if (before != null) {
            before.forEach { (name, values) -> storage.replace(name, values) }
            storage.clearJournal()
        }
        recoveryRequired = false
    }

    fun execute(before: PreferenceImage, after: PreferenceImage, stillValid: () -> Boolean = { true }) {
        check(!recoveryRequired) { "Local restore recovery requires an app restart" }
        require(before.keys == after.keys && before.isNotEmpty())
        // No journal/target writes, manager callbacks, migrations or alarms during preflight.
        BackupPreflight.validate(after)
        check(stillValid()) { "Restore canceled before writing" }
        // A failed commit can still have changed the in-memory preferences (or reached disk).
        // Treat journal preparation/retirement failures as indeterminate, never as 'not written'.
        recoveryRequired = true
        storage.prepare(before)
        try {
            after.forEach { (name, values) ->
                check(stillValid()) { "Account changed; restore canceled" }
                storage.replace(name, values)
            }
            check(stillValid()) { "Account changed; restore canceled" }
        } catch (failure: Exception) {
            try {
                before.forEach { (name, values) -> storage.replace(name, values) }
                storage.clearJournal()
                recoveryRequired = false
            } catch (rollbackFailure: Exception) {
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
        // Cancellation is best effort: invalidation can race this final retirement. A completed
        // local restore may survive sign-out; auth revision checks must still suppress publication.
        // Never start rollback after attempting retirement: disk may already contain no journal.
        // On failure, freeze writers; restart sees either a complete new image or an undo journal.
        storage.clearJournal()
        recoveryRequired = false
    }
}

/** Replicated into data prefs because Android backup rules exclude cloud identity prefs. */
internal object DeviceDataOwnership {
    const val KEY = "__mindvault_data_provenance_v1"
    const val UNOWNED = "unowned"
    const val UNKNOWN = "unknown"

    fun token(uid: String): String = "uid:$uid"

    fun resolve(legacyOwner: String?, images: Collection<Map<String, Any>>): String {
        val tokens = images.mapNotNull { it[KEY] as? String }.toSet()
        val expected = legacyOwner?.let(::token)
        if (expected != null) {
            return if (tokens.any { it != expected && it != UNOWNED }) UNKNOWN else expected
        }
        if (tokens.size > 1) return UNKNOWN
        if (tokens.size == 1) return tokens.single().takeIf {
            it == UNOWNED || it.startsWith("uid:") && it.length > 4
        } ?: UNKNOWN
        // No identity and no provenance on existing data is ambiguous (old sign-out or OS restore).
        return if (images.any { image -> image.keys.any { it != KEY } }) UNKNOWN else UNOWNED
    }

    fun owner(token: String): String? = when {
        token == UNOWNED -> null
        token.startsWith("uid:") -> token.removePrefix("uid:")
        else -> UNKNOWN // Deliberately cannot match a Firebase UID.
    }
}
