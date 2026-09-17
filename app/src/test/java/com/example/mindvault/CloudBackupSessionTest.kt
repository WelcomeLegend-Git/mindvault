package com.example.mindvault

import com.example.mindvault.data.AccountBackupData
import com.example.mindvault.data.CloudBackupSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBackupSessionTest {
    @Test
    fun revisionInvalidatesPriorWorkEvenForTheSameAccount() {
        val session = CloudBackupSession()
        val first = session.invalidate()
        assertTrue(session.activate(first, "uid", "local"))
        val captured = session.current()
        session.invalidate()
        assertFalse(session.isRevisionCurrent(first))
        assertNull(session.current())
        assertFalse(session.permits(captured!!, "uid", "local", "uid"))
    }

    @Test
    fun permitsRequiresEveryIdentityToMatch() {
        val session = CloudBackupSession()
        val revision = session.invalidate()
        assertTrue(session.activate(revision, "uid", "local"))
        val captured = session.current()!!
        assertTrue(session.permits(captured, "uid", "local", "uid"))
        assertFalse(session.permits(captured, "other", "local", "uid"))
        assertFalse(session.permits(captured, "uid", "other", "uid"))
        assertFalse(session.permits(captured, "uid", "local", "other"))
        assertFalse(session.permits(captured, null, "local", "uid"))
        assertFalse(session.permits(captured, "uid", null, "uid"))
        assertFalse(session.permits(captured, "uid", "local", null))
    }

    @Test
    fun activateOnlySucceedsOnTheCurrentRevision() {
        val session = CloudBackupSession()
        val stale = session.invalidate()
        session.invalidate()
        assertFalse(session.activate(stale, "uid", "local"))
        assertNull(session.current())
        val fresh = session.invalidate()
        assertTrue(session.activate(fresh, "uid", "local"))
        assertEquals(fresh, session.current()?.revision)
    }

    @Test
    fun signOutInvalidatesAnInFlightSession() = runTest {
        val session = CloudBackupSession()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = launch {
            val revision = session.invalidate()
            assertTrue(session.activate(revision, "uid", "local"))
            val captured = session.current()!!
            started.complete(Unit)
            release.await()
            assertFalse(
                "Work captured before sign-out must not survive it",
                session.permits(captured, "uid", "local", "uid")
            )
        }
        started.await()
        session.invalidate()
        release.complete(Unit)
        job.join()
    }

    @Test
    fun profileBackupExcludesOtherLocalUsersAndSessions() {
        val users = mapOf<String, Any>(
            "user_a_name" to "Alice", "user_a_email" to "a@x.com", "user_a_role" to "PREMIUM",
            "user_b_name" to "Bob", "user_b_email" to "b@x.com",
            "session_t_user" to "a", "session_t_login" to "x", "session_t_expires" to "y",
            "current_user_id" to "b", "all_user_ids" to setOf("a", "b")
        )
        val profile = AccountBackupData.profile(users, "a")
        assertEquals(
            setOf("user_a_name", "user_a_email", "user_a_role", "current_user_id", "all_user_ids"),
            profile.keys
        )
        assertEquals("a", (profile["all_user_ids"] as Set<*>).single())
        assertEquals("a", profile["current_user_id"])
    }

    @Test
    fun restoreRetainsLocalIdentityAndOtherProfilesWithoutImportingRemoteSessions() {
        val local = mapOf<String, Any>(
            "current_user_id" to "a", "all_user_ids" to setOf("a", "b"),
            "user_a_name" to "Old", "user_a_profile_pic" to "stale",
            "user_b_name" to "Other", "session_local_user" to "a"
        )
        val remote = mapOf<String, Any>(
            "current_user_id" to "remote", "all_user_ids" to setOf("remote", "stranger"),
            "user_remote_name" to "Restored", "user_stranger_name" to "Stranger",
            "session_remote_user" to "remote"
        )
        val restored = AccountBackupData.restoredProfile(local, remote, "a")
        assertEquals("a", restored["current_user_id"])
        assertEquals(setOf("a", "b"), restored["all_user_ids"])
        assertEquals("Restored", restored["user_a_name"])
        assertEquals("Other", restored["user_b_name"])
        assertFalse(restored.containsKey("user_a_profile_pic"))
        assertFalse(restored.containsKey("user_stranger_name"))
        assertFalse(restored.containsKey("session_remote_user"))
        assertEquals("a", restored["session_local_user"])
    }

    @Test
    fun localDataIsOnlyClaimedWhenUnownedAndEmptyOrAlreadyOwned() {
        assertTrue(AccountBackupData.mayClaimLocalData("uid", "uid", isEmpty = false))
        assertTrue(AccountBackupData.mayClaimLocalData(null, "uid", isEmpty = true))
        assertFalse(AccountBackupData.mayClaimLocalData(null, "uid", isEmpty = false))
        assertFalse(AccountBackupData.mayClaimLocalData("other", "uid", isEmpty = true))
    }
}
