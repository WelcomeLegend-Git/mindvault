package com.example.mindvault.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest

/**
 * Lightweight manager that stores an app-specific password locally only, never synced.
 */
object AppPasswordManager {
    private const val PREF_NAME = "mindvault_app_security"
    private const val KEY_HASH = "app_password_hash"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isPasswordSet(): Boolean = prefs.contains(KEY_HASH)

    fun verifyPassword(password: String): Boolean {
        if (!isPasswordSet()) return false
        return hash(password) == prefs.getString(KEY_HASH, null)
    }

    fun setPassword(password: String) {
        prefs.edit().putString(KEY_HASH, hash(password)).apply()
    }

    fun clearPassword() {
        prefs.edit().remove(KEY_HASH).apply()
    }

    private fun hash(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
