package com.example.mindvault

import com.example.mindvault.data.SessionAccounting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SessionAccountingTest {
    private fun time(value: String) = LocalDateTime.parse(value)

    @Test
    fun sameDayKeepsWholeMinutesAndSessionMetadata() {
        val start = time("2026-09-17T10:00:15")
        val end = time("2026-09-17T10:45:59")
        val days = SessionAccounting.split(start, end, true, 3)
        assertEquals(1, days.size)
        assertEquals(start.toLocalDate(), days.single().date)
        assertEquals(45L, days.single().minutes)
        assertEquals(1, days.single().totalSessions)
        assertEquals(1, days.single().completedSessions)
        assertEquals(3, days.single().distractions)
    }

    @Test
    fun midnightSplitsMinutesButCountsSessionOnlyOnStartDate() {
        val days = SessionAccounting.split(
            time("2026-09-17T23:45:00"), time("2026-09-18T00:30:00"), true, 2
        )
        assertEquals(listOf(LocalDate.parse("2026-09-17"), LocalDate.parse("2026-09-18")), days.map { it.date })
        assertEquals(listOf(15L, 30L), days.map { it.minutes })
        assertEquals(listOf(1, 0), days.map { it.totalSessions })
        assertEquals(listOf(1, 0), days.map { it.completedSessions })
        assertEquals(listOf(2, 0), days.map { it.distractions })
    }

    @Test
    fun exactMidnightEndDoesNotCreateAnEndingDayAllocation() {
        val days = SessionAccounting.split(
            time("2026-09-17T23:30:00"), time("2026-09-18T00:00:00"), true, 0
        )
        assertEquals(1, days.size)
        assertEquals(LocalDate.parse("2026-09-17"), days.single().date)
        assertEquals(30L, days.single().minutes)
    }

    @Test
    fun exactMidnightStartAndFullDayStayOnThatDate() {
        val days = SessionAccounting.split(
            time("2026-09-17T00:00:00"), time("2026-09-18T00:00:00"), false, 0
        )
        assertEquals(1, days.size)
        assertEquals(LocalDate.parse("2026-09-17"), days.single().date)
        assertEquals(1440L, days.single().minutes)
        assertEquals(0, days.single().completedSessions)
    }

    @Test
    fun fractionalMinuteCarriesAcrossMidnightWithoutLosingLifetimeMinutes() {
        val start = time("2026-09-17T23:59:30")
        val end = time("2026-09-18T00:00:30")
        val days = SessionAccounting.split(start, end, false, 0)
        assertEquals(listOf(0L, 1L), days.map { it.minutes })
        assertEquals(SessionAccounting.totalMinutes(start, end), days.sumOf { it.minutes })
    }

    @Test
    fun subMinuteSessionCrossingMidnightDoesNotInventMinutes() {
        val days = SessionAccounting.split(
            time("2026-09-17T23:59:40"), time("2026-09-18T00:00:20"), false, -3
        )
        assertEquals(listOf(0L, 0L), days.map { it.minutes })
        assertEquals(1, days.sumOf { it.totalSessions })
        assertEquals(0, days.sumOf { it.distractions })
    }

    @Test
    fun multiDaySessionSplitsAcrossYearBoundary() {
        val start = time("2025-12-31T23:30:00")
        val end = time("2026-01-02T00:15:00")
        val days = SessionAccounting.split(start, end, true, 4)
        assertEquals(
            listOf("2025-12-31", "2026-01-01", "2026-01-02"),
            days.map { it.date.toString() }
        )
        assertEquals(listOf(30L, 1440L, 15L), days.map { it.minutes })
        assertEquals(SessionAccounting.totalMinutes(start, end), days.sumOf { it.minutes })
        assertEquals(1, days.sumOf { it.totalSessions })
        assertEquals(1, days.sumOf { it.completedSessions })
        assertEquals(4, days.sumOf { it.distractions })
    }

    @Test
    fun sundayToMondaySplitsAtTheWeekBoundary() {
        val days = SessionAccounting.split(
            time("2026-09-20T23:50:00"), time("2026-09-21T00:20:00"), true, 0
        )
        assertEquals(listOf(7, 1), days.map { it.date.dayOfWeek.value })
        assertEquals(listOf(10L, 20L), days.map { it.minutes })
    }

    @Test
    fun leapDayIsIncluded() {
        val days = SessionAccounting.split(
            time("2024-02-28T23:30:00"), time("2024-03-01T00:30:00"), false, 0
        )
        assertEquals(listOf("2024-02-28", "2024-02-29", "2024-03-01"), days.map { it.date.toString() })
        assertEquals(listOf(30L, 1440L, 30L), days.map { it.minutes })
    }

    @Test
    fun missingEqualOrBackwardEndCreditsNoMinutes() {
        val start = time("2026-09-17T10:00:00")
        for (end in listOf(null, start, start.minusDays(1))) {
            val days = SessionAccounting.split(start, end, false, 1)
            assertEquals(1, days.size)
            assertEquals(start.toLocalDate(), days.single().date)
            assertEquals(0L, days.single().minutes)
            assertEquals(0L, SessionAccounting.totalMinutes(start, end))
            assertEquals(1, days.single().totalSessions)
            assertEquals(0, days.single().completedSessions)
        }
    }

    @Test
    fun nanosecondBoundariesPreserveTotalWithoutNegativeOrOversizedDays() {
        val midnight = time("2026-09-18T00:00:00")
        for (secondsBefore in listOf(0L, 1L, 29L, 30L, 59L, 60L, 90L)) {
            for (secondsAfter in listOf(0L, 1L, 29L, 30L, 59L, 60L, 90L, 172800L)) {
                val start = midnight.minusSeconds(secondsBefore).minusNanos(1)
                val end = midnight.plusSeconds(secondsAfter)
                val days = SessionAccounting.split(start, end, true, 2)
                assertEquals(SessionAccounting.totalMinutes(start, end), days.sumOf { it.minutes })
                assertTrue(days.all { it.minutes in 0L..1440L })
                assertEquals(1, days.sumOf { it.totalSessions })
                assertEquals(1, days.sumOf { it.completedSessions })
                assertEquals(2, days.sumOf { it.distractions })
            }
        }
    }

    @Test
    fun maximumDateSameDayDoesNotOverflow() {
        val start = LocalDate.MAX.atStartOfDay()
        val days = SessionAccounting.split(start, start.plusHours(1), false, 0)
        assertEquals(60L, days.single().minutes)
    }

    @Test
    fun recoveryStopsAtLastObservationNotRestartTime() {
        val start = time("2026-09-17T23:50:00")
        val observed = time("2026-09-18T00:10:00")
        val restarted = time("2026-09-22T09:00:00")
        val end = SessionAccounting.recoveryEnd(start, observed, restarted)
        assertEquals(observed, end)
        val days = SessionAccounting.split(start, end, false, 2)
        assertEquals(listOf(10L, 10L), days.map { it.minutes })
        assertEquals(20L, SessionAccounting.totalMinutes(start, end))
        assertEquals(0, days.sumOf { it.completedSessions })
    }

    @Test
    fun legacyOrInvalidObservationRecoversNoElapsedTime() {
        val start = time("2026-09-17T10:00:00")
        val now = start.plusDays(3)
        for (observed in listOf(null, start.minusSeconds(1), now.plusSeconds(1))) {
            val end = SessionAccounting.recoveryEnd(start, observed, now)
            assertEquals(start, end)
            assertEquals(0L, SessionAccounting.totalMinutes(start, end))
        }
        assertEquals(start, SessionAccounting.recoveryEnd(start, start, start.minusHours(1)))
    }

    @Test
    fun recoveryAcceptsObservationExactlyAtStartOrNow() {
        val start = time("2026-09-17T10:00:00")
        val now = start.plusMinutes(30)
        assertEquals(start, SessionAccounting.recoveryEnd(start, start, now))
        assertEquals(now, SessionAccounting.recoveryEnd(start, now, now))
    }
}
