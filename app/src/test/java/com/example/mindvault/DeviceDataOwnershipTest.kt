package com.example.mindvault

import com.example.mindvault.data.CloudBackupRecovery
import com.example.mindvault.data.DeviceDataOwnership as Ownership
import org.junit.Assert.*
import org.junit.Test

class DeviceDataOwnershipTest {
    private fun image(token: String) = mapOf<String, Any>(Ownership.KEY to token, "history" to 42L)

    @Test
    fun freshInstallIsExplicitlyUnowned() {
        assertEquals(Ownership.UNOWNED, Ownership.resolve(null, listOf(emptyMap(), emptyMap())))
        assertNull(Ownership.owner(Ownership.UNOWNED))
    }

    @Test
    fun oldSignOutOrOsRestoreWithoutProvenanceIsNeverGuessedIntoAnAccount() {
        val token = Ownership.resolve(null, listOf(mapOf("history" to 42L), emptyMap()))
        assertEquals(Ownership.UNKNOWN, token)
        assertEquals(
            CloudBackupRecovery.Decision.BLOCK_OWNER,
            CloudBackupRecovery.decide(Ownership.owner(token), "account-b", false, false)
        )
    }

    @Test
    fun knownLegacyOwnerIsPreserved() {
        assertEquals(Ownership.token("a"), Ownership.resolve("a", listOf(mapOf("history" to 42L))))
    }

    @Test
    fun signedOutAndGuestDataRemainOwnedEvenWithoutIdentityPreferences() {
        val token = Ownership.resolve(null, listOf(image(Ownership.token("a")), image(Ownership.token("a"))))
        assertEquals("a", Ownership.owner(token))
        for (exists in listOf(false, true)) {
            assertEquals(
                CloudBackupRecovery.Decision.BLOCK_OWNER,
                CloudBackupRecovery.decide(Ownership.owner(token), "b", exists, false)
            )
        }
    }

    @Test
    fun conflictsFailClosedInsteadOfChoosingMostRecentAccount() {
        assertEquals(Ownership.UNKNOWN, Ownership.resolve("b", listOf(image(Ownership.token("a")))))
        assertEquals(
            Ownership.UNKNOWN, Ownership.resolve(
                null,
                listOf(image(Ownership.token("a")), image(Ownership.token("b")))
            )
        )
    }

    @Test
    fun interruptedOwnershipReservationStillBelongsToOriginalClaimant() {
        assertEquals(
            Ownership.token("a"), Ownership.resolve(
                "a",
                listOf(image(Ownership.token("a")), image(Ownership.UNOWNED))
            )
        )
    }

    @Test
    fun explicitUnownedGuestDataCanStillBeImportedWithConfirmation() {
        val token = Ownership.resolve(null, listOf(image(Ownership.UNOWNED), image(Ownership.UNOWNED)))
        assertEquals(
            CloudBackupRecovery.Decision.OFFER_IMPORT,
            CloudBackupRecovery.decide(Ownership.owner(token), "a", false, false)
        )
    }
}
