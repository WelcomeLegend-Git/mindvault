package com.example.mindvault

import com.example.mindvault.utils.DistractionBurstTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistractionBurstTrackerTest {
    @Test
    fun pairedWindowEventsAndContinuousResizeBurstCountOnce() {
        val tracker = DistractionBurstTracker()
        assertTrue(tracker.shouldRecord("session", "selected", 0L))
        assertFalse(tracker.shouldRecord("session", "selected", 100L))
        assertFalse(tracker.shouldRecord("session", "selected", 900L))
        assertFalse(tracker.shouldRecord("session", "selected", 1_700L))
        assertTrue(tracker.shouldRecord("session", "selected", 2_700L))
    }

    @Test
    fun packagesHaveIndependentBursts() {
        val tracker = DistractionBurstTracker()
        assertTrue(tracker.shouldRecord("session", "a", 0L))
        assertTrue(tracker.shouldRecord("session", "b", 50L))
        assertFalse(tracker.shouldRecord("session", "a", 100L))
    }

    @Test
    fun newSessionCountsEvenInsidePreviousBurst() {
        val tracker = DistractionBurstTracker()
        assertTrue(tracker.shouldRecord("first", "a", 0L))
        assertTrue(tracker.shouldRecord("second", "a", 10L))
    }

    @Test
    fun absentSessionDoesNotConsumeFirstCount() {
        val tracker = DistractionBurstTracker()
        assertFalse(tracker.shouldRecord(null, "a", 0L))
        assertTrue(tracker.shouldRecord("session", "a", 10L))
        assertFalse(tracker.shouldRecord(null, "a", 20L))
        assertTrue(tracker.shouldRecord("session", "a", 30L))
    }

    @Test
    fun resetAndMonotonicClockRollbackDoNotSuppressAccounting() {
        val tracker = DistractionBurstTracker()
        assertTrue(tracker.shouldRecord("session", "a", 5_000L))
        assertTrue(tracker.shouldRecord("session", "a", 10L))
        tracker.clear()
        assertTrue(tracker.shouldRecord("session", "a", 20L))
    }
}
