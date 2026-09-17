package com.example.mindvault

import com.example.mindvault.data.DeviceDataOwnership as Ownership
import com.example.mindvault.data.WriteProvenance
import com.example.mindvault.data.CloudBackupSession
import org.junit.Assert.*
import org.junit.Test

class WriteProvenanceTest {
    @Test
    fun sameOwnerStartupDoesNotRequireActivatedCloudUploadSession() {
        val principal = WriteProvenance.principal(false, "a", "a@example.com", "a@example.com")
        assertEquals("a", principal)
        assertEquals(Ownership.token("a"), WriteProvenance.afterWrite(Ownership.token("a"), principal))
    }

    @Test
    fun signedOutCachedFirebaseIdentityIsNotAnActivePrincipal() {
        assertNull(WriteProvenance.principal(true, "a", "a@example.com", "a@example.com"))
        assertNull(WriteProvenance.principal(false, "a", "a@example.com", "guest@mindvault.com"))
        assertNull(WriteProvenance.principal(false, "a", "a@example.com", null))
    }

    @Test
    fun otherAccountOrGuestWritePermanentlyBlocksOriginalOwnersUpload() {
        for (principal in listOf(null, "b")) {
            val tainted = WriteProvenance.afterWrite(Ownership.token("a"), principal)
            assertEquals(Ownership.UNKNOWN, tainted)
            assertEquals(Ownership.UNKNOWN, WriteProvenance.afterWrite(tainted, "a"))
            val resolved = Ownership.owner(Ownership.resolve("a", listOf(mapOf(Ownership.KEY to tainted))))
            val cloud = CloudBackupSession()
            cloud.activate(cloud.invalidate(), "a", "local")
            assertFalse(cloud.permits(cloud.current()!!, "a", "local", resolved))
        }
    }

    @Test
    fun unownedGuestDataRemainsUnownedRatherThanSilentlyClaimed() {
        assertEquals(Ownership.UNOWNED, WriteProvenance.afterWrite(Ownership.UNOWNED, null))
        assertEquals(Ownership.UNOWNED, WriteProvenance.afterWrite(Ownership.UNOWNED, "a"))
    }
}
