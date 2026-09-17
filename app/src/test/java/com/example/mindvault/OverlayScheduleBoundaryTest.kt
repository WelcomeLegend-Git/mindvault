package com.example.mindvault

import com.example.mindvault.utils.OverlayScheduleBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime
import java.time.ZonedDateTime

class OverlayScheduleBoundaryTest {
    private fun delay(now: String, vararg boundaries: String) = OverlayScheduleBoundary.delayMillis(
        ZonedDateTime.parse(now), boundaries.map { LocalTime.parse(it) }
    )

    @Test
    fun wakesAtEndWithoutAccessibilityEventsOrPermissionGatedFlow() {
        assertEquals(30_000L, delay("2026-09-17T10:59:30Z", "10:00", "11:00"))
    }

    @Test
    fun overnightEndAndNextDayBoundaryAreScheduled() {
        assertEquals(30_000L, delay("2026-09-18T06:59:30Z", "23:00", "07:00"))
        assertEquals(60_000L, delay("2026-09-17T23:59:00Z", "00:00", "01:00"))
    }

    @Test
    fun adjacentSlotsRecheckAtSharedBoundaryThenScheduleNext() {
        assertEquals(1L, delay("2026-09-17T10:59:59.999Z", "10:00", "11:00", "11:00", "12:00"))
        assertEquals(3_600_000L, delay("2026-09-17T11:00:00Z", "10:00", "11:00", "11:00", "12:00"))
    }

    @Test
    fun overlappingSlotsIncludeStartsAsWellAsEnds() {
        assertEquals(60_000L, delay("2026-09-17T10:29:00Z", "10:00", "11:00", "10:30", "12:00"))
    }

    @Test
    fun subMillisecondRemainderRoundsUpAndEmptyScheduleHasNoTimer() {
        assertEquals(1L, delay("2026-09-17T10:59:59.999999999Z", "11:00"))
        assertNull(delay("2026-09-17T11:00:00Z"))
    }

    @Test
    fun daylightSavingGapRechecksAtClockJump() {
        assertEquals(1_000L, delay("2026-03-08T01:59:59-05:00[America/New_York]", "02:30", "04:00"))
    }

    @Test
    fun daylightSavingOverlapIncludesSecondOccurrence() {
        // Recheck at the backward clock jump first, then at the repeated boundary.
        assertEquals(600_000L, delay("2026-11-01T01:50:00-04:00[America/New_York]", "01:30"))
        assertEquals(1_800_000L, delay("2026-11-01T01:00:00-05:00[America/New_York]", "01:30"))
    }
}
