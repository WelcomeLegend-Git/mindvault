package com.example.mindvault

import com.example.mindvault.data.BackupPreflight
import com.example.mindvault.data.PreferenceImage
import com.example.mindvault.data.RestoreTransaction
import org.junit.Assert.*
import org.junit.Test

class BackupPreflightTest {
    private val profile = mapOf<String, Any>(
        "current_user_id" to "a", "all_user_ids" to setOf("a"),
        "user_a_name" to "A", "user_a_email" to "a@example.com",
        "user_a_created" to "2026-09-17T10:00:00", "user_a_last_active" to "2026-09-17T10:00:00",
        "user_a_weekly_goal" to 1200L, "user_a_monthly_goal" to 5000L
    )
    private val validFocus =
        """{"focusModeEnabled":true,"selectedApps":["app"],"timeSlots":[{"id":"slot","startTime":"10:00","endTime":"11:00","type":"STUDY_TIME","selectedApps":[]}]}"""

    private fun rejects(section: String, values: Map<String, Any>) {
        val image: PreferenceImage = mapOf(section to values)
        var writes = 0
        val tx = RestoreTransaction(object : RestoreTransaction.Storage {
            override fun readJournal(): PreferenceImage? = null
            override fun prepare(before: PreferenceImage) {
                writes++
            }

            override fun replace(name: String, values: Map<String, Any>) {
                writes++
            }

            override fun clearJournal() {
                writes++
            }
        })
        try {
            tx.execute(image, image)
            fail("Invalid manager representation accepted: $values")
        } catch (_: Exception) {
            assertEquals("Preflight must precede even journal preparation", 0, writes)
            assertFalse(tx.recoveryRequired)
        }
    }

    @Test
    fun acceptsManagerReadableTypesAndFocusJson() {
        BackupPreflight.validate(
            mapOf(
                "mindvault_users" to profile,
                "FocusModePrefs" to mapOf("FocusModeEnabled" to true, "FocusConfiguration" to validFocus),
                "mindvault_stats" to mapOf(
                    "weekly_goal" to 1200L, "total_sessions" to 2,
                    "daily_focus_2026-09-17" to 20L, "sessions_2026-09-17" to "[]"
                )
            )
        )
    }

    @Test
    fun rejectsTaggedIntWhereProfileGetterRequiresLong() {
        rejects("mindvault_users", profile + ("user_a_weekly_goal" to 1200))
        rejects("mindvault_users", profile + ("user_a_monthly_goal" to 5000))
    }

    @Test
    fun rejectsStatisticsGetterTypeMismatchesForAllDates() {
        rejects("mindvault_stats", mapOf("weekly_goal" to 1200))
        rejects("mindvault_stats", mapOf("daily_focus_2000-01-01" to 2))
        rejects("mindvault_stats", mapOf("daily_distractions_2000-01-01" to 2L))
        rejects("mindvault_stats", mapOf("achievements" to setOf("x")))
    }

    @Test
    fun rejectsMalformedFocusRatherThanLoadingDefaultConfiguration() {
        for (json in listOf(
            "not json", "null", "[]", "{broken", validFocus + " trailing",
            validFocus.replace("STUDY_TIME", "INVALID"), validFocus.replace("10:00", "25:99"),
            validFocus.replace("\"selectedApps\":[\"app\"]", "\"selectedApps\":[null]"),
            validFocus.replace("\"timeSlots\":[", "\"timeSlots\":[null,")
        )) {
            rejects("FocusModePrefs", mapOf("FocusConfiguration" to json))
        }
        rejects("FocusModePrefs", mapOf("FocusModeEnabled" to "true"))
    }

    @Test
    fun rejectsNullFocusListsAndWrongPrimitiveTypes() {
        rejects("FocusModePrefs", mapOf("FocusConfiguration" to """{"timeSlots":null}"""))
        rejects("FocusModePrefs", mapOf("FocusConfiguration" to """{"focusModeEnabled":"true"}"""))
        rejects("mindvault_users", profile + ("user_a_notifications" to "true"))
        rejects("mindvault_users", profile + ("user_a_created" to "invalid date"))
        rejects("mindvault_users", profile + ("user_a_role" to "UNKNOWN"))
    }

    @Test
    fun validatesCheckpointAndHistoryJsonBeforeAnyMutation() {
        rejects("mindvault_stats", mapOf("current_session" to "{}"))
        rejects("mindvault_stats", mapOf("sessions_2026-09-17" to "{}"))
        rejects("mindvault_stats", mapOf("sessions_2026-09-17" to """[{"blockedApps":[null]}]"""))
        BackupPreflight.validate(
            mapOf(
                "mindvault_stats" to mapOf(
                    "current_session" to
                            """{"id":"s","type":"STUDY_TIME","startTime":"2026-09-17T10:00:00","lastObservedTime":"2026-09-17T10:01:00","blockedApps":[],"distractionCount":0}"""
                )
            )
        )
    }
}
