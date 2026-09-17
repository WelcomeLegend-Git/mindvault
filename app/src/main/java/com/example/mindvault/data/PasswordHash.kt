package com.example.mindvault.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Pure JVM password encoding. Parsing is bounded before any expensive derivation. */
internal object PasswordHash {
    private const val VERSION = "v1"
    private const val ALGORITHM = "pbkdf2-sha256"
    private const val ITERATIONS = 600_000
    private const val OLD_ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val HASH_BYTES = 32
    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    enum class Verification { INVALID, VALID, NEEDS_UPGRADE }

    fun create(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derive(password, salt, ITERATIONS)
        return try {
            listOf(VERSION, ALGORITHM, ITERATIONS.toString(), encode(salt), encode(hash))
                .joinToString("$")
        } finally {
            hash.fill(0)
        }
    }

    fun verify(password: String, stored: String?, legacySalt: String? = null): Verification {
        if (stored == null || stored.length > 160) return Verification.INVALID
        if ('$' in stored) {
            // Never reinterpret a malformed/unknown version as a legacy credential.
            if (legacySalt != null) return Verification.INVALID
            val parts = stored.split('$')
            if (parts.size != 5 || parts[0] != VERSION || parts[1] != ALGORITHM ||
                parts[2] != ITERATIONS.toString()
            ) return Verification.INVALID
            val salt = decode(parts[3], SALT_BYTES) ?: return Verification.INVALID
            val expected = decode(parts[4], HASH_BYTES) ?: return Verification.INVALID
            return compare(expected, derive(password, salt, ITERATIONS), Verification.VALID)
        }

        val expected = decode(stored, HASH_BYTES) ?: return Verification.INVALID
        val actual = if (legacySalt != null) {
            // Compatibility with the earlier unversioned, separate-salt PBKDF2 format.
            val salt = decode(legacySalt, SALT_BYTES) ?: return Verification.INVALID
            derive(password, salt, OLD_ITERATIONS)
        } else {
            // Exactly the original UTF-8 SHA-256 + padded Base64.NO_WRAP encoding.
            val bytes = password.toByteArray(Charsets.UTF_8)
            try {
                MessageDigest.getInstance("SHA-256").digest(bytes)
            } finally {
                bytes.fill(0)
            }
        }
        return compare(expected, actual, Verification.NEEDS_UPGRADE)
    }

    private fun compare(expected: ByteArray, actual: ByteArray, success: Verification): Verification =
        try {
            if (MessageDigest.isEqual(expected, actual)) success else Verification.INVALID
        } finally {
            actual.fill(0)
        }

    private fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)

    private fun decode(value: String, size: Int): ByteArray? {
        if (value.length != ((size + 2) / 3) * 4) return null
        val bytes = try {
            decoder.decode(value)
        } catch (_: IllegalArgumentException) {
            return null
        }
        // Reject missing padding, whitespace, alternate alphabets and noncanonical pad bits.
        return bytes.takeIf { it.size == size && encode(it) == value }
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val chars = password.toCharArray()
        val spec = try {
            PBEKeySpec(chars, salt, iterations, HASH_BYTES * 8)
        } finally {
            chars.fill('\u0000')
        }
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
