package com.example.mindvault.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Local-only password storage, with transparent upgrades after successful verification. */
object AppPasswordManager {
    private const val PREF_NAME = "mindvault_app_security"
    private const val KEY_HASH = "app_password_hash"
    private const val KEY_SALT = "app_password_salt"
    private val mutex = Mutex()

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // Corrupt credentials must still require unlocking, never silently disable the lock.
    fun isPasswordSet(): Boolean = prefs.contains(KEY_HASH) || prefs.contains(KEY_SALT)

    suspend fun verifyPassword(password: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            when (verifyStored(password)) {
                PasswordHash.Verification.INVALID -> false
                PasswordHash.Verification.VALID -> true
                PasswordHash.Verification.NEEDS_UPGRADE -> {
                    val upgraded = PasswordHash.create(password)
                    currentCoroutineContext().ensureActive()
                    store(upgraded)
                    true
                }
            }
        }
    }

    /** Verify and replace atomically so concurrent screens cannot overwrite a newer password. */
    suspend fun setPassword(password: String, currentPassword: String): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (isPasswordSet() && verifyStored(currentPassword) == PasswordHash.Verification.INVALID) {
                    return@withLock false
                }
                val encoded = PasswordHash.create(password)
                currentCoroutineContext().ensureActive()
                store(encoded)
                true
            }
        }

    suspend fun clearPassword(currentPassword: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (verifyStored(currentPassword) == PasswordHash.Verification.INVALID) return@withLock false
            currentCoroutineContext().ensureActive()
            persist(prefs.edit().remove(KEY_HASH).remove(KEY_SALT))
            true
        }
    }

    private fun verifyStored(password: String): PasswordHash.Verification {
        val stored: String?
        val salt: String?
        try {
            stored = prefs.getString(KEY_HASH, null)
            salt = prefs.getString(KEY_SALT, null)
        } catch (_: ClassCastException) {
            return PasswordHash.Verification.INVALID
        }
        return PasswordHash.verify(password, stored, salt)
    }

    private fun store(encoded: String) {
        persist(prefs.edit().putString(KEY_HASH, encoded).remove(KEY_SALT))
    }

    private fun persist(editor: SharedPreferences.Editor) {
        val previousHash = prefs.getString(KEY_HASH, null)
        val previousSalt = prefs.getString(KEY_SALT, null)
        try {
            check(editor.commit()) { "Unable to persist password settings" }
        } catch (failure: Exception) {
            // commit() updates memory even when the disk write fails. In particular,
            // a failed removal must not leave this process thinking the lock is off.
            try {
                prefs.edit().putString(KEY_HASH, previousHash).putString(KEY_SALT, previousSalt).apply()
            } catch (rollbackFailure: Exception) {
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
    }
}
