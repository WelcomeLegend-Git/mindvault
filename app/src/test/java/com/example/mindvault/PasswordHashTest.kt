package com.example.mindvault

import com.example.mindvault.data.PasswordHash
import com.example.mindvault.data.PasswordHash.Verification
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHashTest {
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    @Test
    fun newRecordsUseRandomSaltAnd600kSha256() {
        val first = PasswordHash.create(" password 🔒 ")
        val second = PasswordHash.create(" password 🔒 ")
        assertNotEquals(first, second)
        val parts = first.split('$')
        assertEquals(listOf("v1", "pbkdf2-sha256", "600000"), parts.take(3))
        assertEquals(16, decoder.decode(parts[3]).size)
        assertEquals(32, decoder.decode(parts[4]).size)
        assertNotEquals(parts[3], second.split('$')[3])
        assertEquals(referencePbkdf2(" password 🔒 ", decoder.decode(parts[3]), 600_000), parts[4])
        assertEquals(Verification.VALID, PasswordHash.verify(" password 🔒 ", first))
        assertEquals(Verification.INVALID, PasswordHash.verify("password 🔒", first))
        assertEquals(Verification.INVALID, PasswordHash.verify("wrong", first))
        assertEquals(Verification.VALID, PasswordHash.verify(" password 🔒 ", second))
    }

    @Test
    fun exactOriginalSha256EncodingIsAcceptedAndMarkedForUpgrade() {
        assertEquals(Verification.NEEDS_UPGRADE, PasswordHash.verify(
            "abc", "ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0="
        ))
        assertEquals(Verification.NEEDS_UPGRADE, PasswordHash.verify(
            "", "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
        ))
        listOf(" päss🔒\n", "\u0000secret", "é", "e\u0301").forEach { password ->
            val legacy = legacyHash(password)
            assertEquals(Verification.NEEDS_UPGRADE, PasswordHash.verify(password, legacy))
            assertEquals(Verification.INVALID, PasswordHash.verify(password + "x", legacy))
        }
        assertEquals(Verification.INVALID, PasswordHash.verify("e\u0301", legacyHash("é")))
    }

    @Test
    fun legacyVerificationCanBeTransparentlyUpgraded() {
        val password = "legacy password"
        val legacy = legacyHash(password)
        assertEquals(Verification.NEEDS_UPGRADE, PasswordHash.verify(password, legacy))
        val upgraded = PasswordHash.create(password)
        assertEquals(Verification.VALID, PasswordHash.verify(password, upgraded))
        assertEquals(Verification.INVALID, PasswordHash.verify("wrong", upgraded))
    }

    @Test
    fun earlierSeparateSaltPbkdf2IsVerifiedAndMarkedForUpgrade() {
        val salt = ByteArray(16) { it.toByte() }
        val hash = referencePbkdf2("pässword", salt, 120_000)
        assertEquals(Verification.NEEDS_UPGRADE, PasswordHash.verify("pässword", hash, encoder.encodeToString(salt)))
        assertEquals(Verification.INVALID, PasswordHash.verify("wrong", hash, encoder.encodeToString(salt)))
        assertEquals(Verification.INVALID, PasswordHash.verify("pässword", hash))
        assertEquals(Verification.INVALID, PasswordHash.verify("pässword", hash, "bad salt"))
        assertEquals(Verification.INVALID, PasswordHash.verify("pässword", legacyHash("pässword"), ""))
    }

    @Test
    fun malformedRecordsFailClosedWithoutDerivingUnboundedIterations() {
        val salt = encoder.encodeToString(ByteArray(16))
        val hash = encoder.encodeToString(ByteArray(32))
        val validShape = listOf("v1", "pbkdf2-sha256", "600000", salt, hash).joinToString("$")
        val invalid = mutableListOf<String?>(null, "", "garbage", "x".repeat(10_000), hash.dropLast(1), hash + "\n")
        listOf("v0", "v2", "V1", "").forEach { invalid += validShape.replace("v1", it) }
        listOf("0", "-1", "1", "120000", "600001", "0600000", "+600000", " 600000", "2147483647", "99999999999999999999")
            .forEach { invalid += validShape.replace("600000", it) }
        invalid += validShape.replace("pbkdf2-sha256", "pbkdf2-sha1")
        invalid += validShape + "$"
        invalid += validShape.substringBeforeLast('$')
        invalid += validShape.replace(salt, "!")
        invalid += validShape.replace(salt, encoder.encodeToString(ByteArray(15)))
        invalid += validShape.replace(hash, encoder.encodeToString(ByteArray(31)))
        invalid += validShape.replace(hash, hash.dropLast(2) + "B=") // Noncanonical pad bits.
        invalid.forEach { assertEquals("Record: $it", Verification.INVALID, PasswordHash.verify("password", it)) }
        assertEquals(Verification.INVALID, PasswordHash.verify("password", validShape, salt))
        assertEquals(Verification.INVALID, PasswordHash.verify("password", null, salt))
    }

    @Test
    fun alteredSaltOrDigestDoesNotVerify() {
        val record = PasswordHash.create("password")
        val parts = record.split('$').toMutableList()
        for (index in listOf(3, 4)) {
            val changed = parts.toMutableList()
            val bytes = decoder.decode(changed[index])
            bytes[0] = (bytes[0].toInt() xor 1).toByte()
            changed[index] = encoder.encodeToString(bytes)
            assertEquals(Verification.INVALID, PasswordHash.verify("password", changed.joinToString("$")))
        }
        assertTrue(record.startsWith("v1"))
        assertFalse(record.contains("password"))
    }

    @Test
    fun emptyPasswordsRemainCompatibleWithTheOriginalHashContract() {
        val record = PasswordHash.create("")
        assertEquals(Verification.VALID, PasswordHash.verify("", record))
        assertEquals(Verification.INVALID, PasswordHash.verify(" ", record))
    }

    private fun legacyHash(password: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8)))

    private fun referencePbkdf2(password: String, salt: ByteArray, iterations: Int): String {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, 256)
        chars.fill('\u0000')
        return try {
            encoder.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
        } finally {
            spec.clearPassword()
        }
    }
}
