package com.example.mindvault.utils

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/** Wake-up calculation only; the service rechecks the live policy before hiding anything. */
internal object OverlayScheduleBoundary {
    fun delayMillis(now: ZonedDateTime, boundaries: List<LocalTime>): Long? {
        if (boundaries.isEmpty()) return null
        val instant = now.toInstant()
        val rules = now.zone.rules
        val candidates = (0L..1L).flatMap { day ->
            boundaries.flatMap { time ->
                val local = now.toLocalDate().plusDays(day).atTime(time)
                val offsets = rules.getValidOffsets(local)
                if (offsets.isEmpty()) {
                    listOf(local.atZone(now.zone).toInstant())
                } else {
                    offsets.map { local.toInstant(it) }
                }
            }
        }.toMutableList()
        // A clock jump at a DST transition can change policy even without a slot boundary.
        rules.nextTransition(instant)?.instant?.let { candidates.add(it) }
        val next = candidates.filter { it > instant }.minOrNull() ?: return null
        val duration = Duration.between(instant, next)
        // Round up so a sub-millisecond remainder cannot wake us before the boundary.
        return duration.toMillis() + if (duration.nano % 1_000_000 == 0) 0L else 1L
    }
}
