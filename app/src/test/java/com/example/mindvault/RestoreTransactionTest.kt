package com.example.mindvault

import com.example.mindvault.data.PreferenceImage
import com.example.mindvault.data.RestoreTransaction
import org.junit.Assert.*
import org.junit.Test

class RestoreTransactionTest {
    private val before: PreferenceImage = linkedMapOf(
        "users" to mapOf("name" to "before", "ids" to setOf("a", "b")),
        "focus" to mapOf("enabled" to false),
        "stats" to mapOf("minutes" to 42L)
    )
    private val after: PreferenceImage = linkedMapOf(
        "users" to mapOf("name" to "after"),
        "focus" to mapOf("enabled" to true),
        "stats" to mapOf("minutes" to 100L)
    )

    private class Crash : Error()
    private class MemoryStorage(initial: PreferenceImage) : RestoreTransaction.Storage {
        val data = initial.toMutableMap()
        var journal: PreferenceImage? = null
        var replacements = 0
        var failAt: Set<Int> = emptySet()
        var crashAt: Int? = null
        var failPrepare = false
        var failClear = false
        var corruptJournal = false

        override fun readJournal(): PreferenceImage? {
            check(!corruptJournal) { "Corrupt journal" }
            return journal
        }

        override fun prepare(before: PreferenceImage) {
            check(journal == null)
            journal = before
            check(!failPrepare) { "Journal commit returned false after memory changed" }
        }

        override fun replace(name: String, values: Map<String, Any>) {
            check(journal != null) { "A target write preceded the journal" }
            replacements++
            data[name] = values // Model commit(false) still changing SharedPreferences memory.
            if (replacements == crashAt) throw Crash()
            check(replacements !in failAt) { "Target write failed" }
        }

        override fun clearJournal() {
            check(!failClear) { "Journal retirement failed" }
            journal = null
        }
    }

    private fun expectFailure(block: () -> Unit) {
        try {
            block(); fail("Expected failure")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun successfulTransactionWritesAllStoresAndRetiresJournal() {
        val storage = MemoryStorage(before)
        val tx = RestoreTransaction(storage)
        tx.execute(before, after)
        assertEquals(after, storage.data)
        assertNull(storage.journal)
        assertFalse(tx.recoveryRequired)
    }

    @Test
    fun eachPartialCommitFailureRollsBackIncludingFailedStore() {
        for (failure in 1..3) {
            val storage = MemoryStorage(before).apply { failAt = setOf(failure) }
            val tx = RestoreTransaction(storage)
            expectFailure { tx.execute(before, after) }
            assertEquals(before, storage.data)
            assertNull(storage.journal)
            assertFalse(tx.recoveryRequired)
        }
    }

    @Test
    fun crashAfterAnyTargetWriteRecoversBeforeImageAndRecoveryIsIdempotent() {
        for (crash in 1..3) {
            val storage = MemoryStorage(before).apply { crashAt = crash }
            try {
                RestoreTransaction(storage).execute(before, after); fail()
            } catch (_: Crash) {
            }
            storage.crashAt = null
            val restarted = RestoreTransaction(storage)
            restarted.recover()
            restarted.recover()
            assertEquals(before, storage.data)
            assertNull(storage.journal)
        }
    }

    @Test
    fun failedRollbackKeepsJournalAndRejectsNewTransactionsUntilRecovery() {
        val storage = MemoryStorage(before).apply { failAt = setOf(2, 3) }
        val tx = RestoreTransaction(storage)
        expectFailure { tx.execute(before, after) }
        assertTrue(tx.recoveryRequired)
        assertNotNull(storage.journal)
        expectFailure { tx.execute(before, after) }
        storage.failAt = emptySet()
        RestoreTransaction(storage).recover()
        assertEquals(before, storage.data)
    }

    @Test
    fun failedPreparationNeverTouchesTargetsAndRequiresRecovery() {
        val storage = MemoryStorage(before).apply { failPrepare = true }
        val tx = RestoreTransaction(storage)
        expectFailure { tx.execute(before, after) }
        assertEquals(0, storage.replacements)
        assertTrue(tx.recoveryRequired)
        RestoreTransaction(storage).recover()
        assertEquals(before, storage.data)
    }

    @Test
    fun failedRetirementDoesNotAttemptUnsafeImmediateRollback() {
        val storage = MemoryStorage(before).apply { failClear = true }
        val tx = RestoreTransaction(storage)
        expectFailure { tx.execute(before, after) }
        assertEquals(3, storage.replacements)
        assertEquals(after, storage.data)
        assertTrue(tx.recoveryRequired)
        storage.failClear = false
        RestoreTransaction(storage).recover()
        assertEquals(before, storage.data)
    }

    @Test
    fun crashDuringRecoveryCanBeRetriedFromSameJournal() {
        val storage = MemoryStorage(after).apply { journal = before; crashAt = 2 }
        try {
            RestoreTransaction(storage).recover(); fail()
        } catch (_: Crash) {
        }
        assertNotNull(storage.journal)
        storage.crashAt = null
        RestoreTransaction(storage).recover()
        assertEquals(before, storage.data)
    }

    @Test
    fun corruptJournalFailsClosedWithoutWritingTargets() {
        val storage = MemoryStorage(after).apply { journal = before; corruptJournal = true }
        val tx = RestoreTransaction(storage)
        expectFailure { tx.recover() }
        assertTrue(tx.recoveryRequired)
        assertEquals(0, storage.replacements)
    }

    @Test
    fun revisionChangeDuringRestoreRollsBack() {
        val storage = MemoryStorage(before)
        val tx = RestoreTransaction(storage)
        expectFailure { tx.execute(before, after) { storage.replacements < 1 } }
        assertEquals(before, storage.data)
        assertNull(storage.journal)
    }

    @Test
    fun invalidationDuringRetirementDoesNotPromiseAtomicCancellation() {
        val memory = MemoryStorage(before)
        var valid = true
        val storage = object : RestoreTransaction.Storage by memory {
            override fun clearJournal() {
                valid = false // Sign-out after the final validity check.
                memory.clearJournal()
            }
        }
        RestoreTransaction(storage).execute(before, after) { valid }
        assertFalse(valid)
        assertEquals(after, memory.data)
        assertNull(memory.journal)
    }

    @Test
    fun canceledBeforePreparationDoesNotWriteJournal() {
        val storage = MemoryStorage(before)
        val tx = RestoreTransaction(storage)
        expectFailure { tx.execute(before, after) { false } }
        assertNull(storage.journal)
        assertFalse(tx.recoveryRequired)
    }
}
