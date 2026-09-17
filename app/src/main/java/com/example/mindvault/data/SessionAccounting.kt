package com.example.mindvault.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

internal data class SessionDayAllocation(
    val date: LocalDate,
    val minutes: Long,
    val totalSessions: Int,
    val completedSessions: Int,
    val distractions: Int
)

/** Pure accounting for the existing local-wall-clock, whole-minute statistics format. */
internal object SessionAccounting {
    fun totalMinutes(start: LocalDateTime, end: LocalDateTime?): Long =
        if (end == null) 0L else ChronoUnit.MINUTES.between(start, end).coerceAtLeast(0L)

    /**
     * Counts and untimestamped distractions belong to the start date, like session records.
     * Minutes use [start, end) date slices. Cumulative rounding carries fractional minutes
     * forward, assigning each whole minute to the day on which it finishes; an exact
     * midnight end belongs to the preceding day. No extra minute is lost at a boundary.
     */
    fun split(
        start: LocalDateTime,
        end: LocalDateTime?,
        completed: Boolean,
        distractions: Int
    ): List<SessionDayAllocation> {
        val effectiveEnd = end?.takeIf { it.isAfter(start) } ?: start
        val allocations = mutableListOf<SessionDayAllocation>()
        var cursor = start
        var allocatedMinutes = 0L
        do {
            val date = cursor.toLocalDate()
            // Avoid overflowing LocalDate.MAX for a same-day interval.
            val sliceEnd = if (date == effectiveEnd.toLocalDate()) {
                effectiveEnd
            } else {
                date.plusDays(1).atStartOfDay()
            }
            val cumulativeMinutes = totalMinutes(start, sliceEnd)
            val isStartDate = date == start.toLocalDate()
            allocations.add(
                SessionDayAllocation(
                    date = date,
                    minutes = cumulativeMinutes - allocatedMinutes,
                    totalSessions = if (isStartDate) 1 else 0,
                    completedSessions = if (isStartDate && completed) 1 else 0,
                    distractions = if (isStartDate) distractions.coerceAtLeast(0) else 0
                )
            )
            allocatedMinutes = cumulativeMinutes
            cursor = sliceEnd
        } while (cursor.isBefore(effectiveEnd))
        return allocations
    }

    /** Missing/invalid legacy checkpoints are not evidence of elapsed focus time. */
    fun recoveryEnd(
        start: LocalDateTime,
        lastObserved: LocalDateTime?,
        now: LocalDateTime
    ): LocalDateTime = lastObserved?.takeIf { !it.isBefore(start) && !it.isAfter(now) } ?: start
}
