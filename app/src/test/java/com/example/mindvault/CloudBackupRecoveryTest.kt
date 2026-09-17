package com.example.mindvault

import com.example.mindvault.data.CloudBackupRecovery
import com.example.mindvault.data.CloudBackupRecovery.Decision
import com.example.mindvault.data.CloudBackupSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CloudBackupRecoveryTest {
    @Test
    fun failedRecoveryKeepsSessionRetryableButBlocksUploads() {
        val gate = CloudBackupSession()
        val revision = gate.invalidate()
        gate.activate(revision, "uid", "local", uploadReady = false)
        val session = gate.current()!!
        assertTrue(gate.matches(session, "uid", "local"))
        assertFalse(gate.permits(session, "uid", "local", "uid"))
        assertTrue(gate.setUploadReady(session, true))
        assertTrue(gate.permits(session, "uid", "local", "uid"))
        assertFalse(gate.permits(session, "uid", "local", null))
        assertFalse(gate.permits(session, "other", "local", "uid"))
    }

    @Test
    fun lateRecoveryCannotEnableUploadsAfterSignOut() = runTest {
        val gate = CloudBackupSession()
        gate.activate(gate.invalidate(), "uid", "local", uploadReady = false)
        val session = gate.current()!!
        val release = CompletableDeferred<Unit>()
        val job = launch {
            release.await()
            assertFalse(gate.setUploadReady(session, true))
            assertFalse(gate.permits(session, "uid", "local", "uid"))
        }
        gate.invalidate()
        gate.activate(gate.invalidate(), "uid", "local", uploadReady = false)
        release.complete(Unit)
        job.join()
        assertFalse(gate.permits(gate.current()!!, "uid", "local", "uid"))
    }

    @Test
    fun startupKeepsNewerSameOwnerLocalDataButIncompleteRestoreRequiresRecovery() {
        assertEquals(Decision.KEEP_LOCAL, CloudBackupRecovery.decide("uid", "uid", true, false))
        assertEquals(Decision.OFFER_RESTORE, CloudBackupRecovery.decide("uid", "uid", true, true))
    }

    @Test
    fun existingBackupNeverOffersImportAndAnotherOwnerIsAlwaysBlocked() {
        assertEquals(Decision.OFFER_RESTORE, CloudBackupRecovery.decide(null, "uid", true, false))
        assertEquals(Decision.OFFER_IMPORT, CloudBackupRecovery.decide(null, "uid", false, false))
        assertEquals(Decision.BLOCK_OWNER, CloudBackupRecovery.decide("other", "uid", false, false))
        assertEquals(Decision.BLOCK_OWNER, CloudBackupRecovery.decide("other", "uid", true, false))
    }

    @Test
    fun restoreRequiresIdleDeviceAndCompatibleOwner() {
        assertTrue(CloudBackupRecovery.mayRestore(null, "uid", false, false))
        assertTrue(CloudBackupRecovery.mayRestore("uid", "uid", false, false))
        assertFalse(CloudBackupRecovery.mayRestore("other", "uid", false, false))
        assertFalse(CloudBackupRecovery.mayRestore(null, "uid", true, false))
        assertFalse(CloudBackupRecovery.mayRestore(null, "uid", false, true))
    }

    @Test
    fun guestHasNoRecoveryOrUploadSession() {
        val gate = CloudBackupSession()
        gate.activate(gate.invalidate(), "uid", "local")
        val oldSession = gate.current()!!
        gate.invalidate()
        assertNull(gate.current())
        assertFalse(gate.matches(oldSession, null, "guest"))
        assertFalse(gate.setUploadReady(oldSession, true))
    }
}
