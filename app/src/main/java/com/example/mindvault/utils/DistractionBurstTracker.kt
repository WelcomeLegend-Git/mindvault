package com.example.mindvault.utils

/** Accounting only: callers must still perform every blocking action. */
internal class DistractionBurstTracker(private val quietPeriodMillis: Long = 1_000L) {
    private var sessionId: String? = null
    private val lastEvents = mutableMapOf<String, Long>()

    fun shouldRecord(session: String?, packageName: String, elapsedMillis: Long): Boolean {
        if (sessionId != session) {
            lastEvents.clear()
            sessionId = session
        }
        if (session == null) return false
        val previous = lastEvents.put(packageName, elapsedMillis)
        // A continuous stream is one burst, not one count every quietPeriodMillis.
        return previous == null || elapsedMillis < previous || elapsedMillis - previous >= quietPeriodMillis
    }

    fun clear() {
        sessionId = null
        lastEvents.clear()
    }
}
