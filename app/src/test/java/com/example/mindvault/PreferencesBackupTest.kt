package com.example.mindvault

import com.example.mindvault.data.PreferencesBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PreferencesBackupTest {
    @Test
    fun roundTripPreservesValuesAndExactTypes() {
        val original = mapOf(
            "count" to 12,
            "smallLong" to 12L,
            "largeLong" to Long.MAX_VALUE,
            "minimumLong" to Long.MIN_VALUE,
            "minimumInt" to Int.MIN_VALUE,
            "ratio" to 1.25f,
            "name" to "Study \"goal\" ☀",
            "enabled" to true,
            "disabled" to false,
            "ids" to setOf("alice", "bob"),
            "emptySet" to emptySet<String>(),
            "emptyString" to ""
        )

        val restored = PreferencesBackup.decode("mindvault_stats", PreferencesBackup.encode(original))

        assertEquals(original, restored)
        assertEquals(Int::class.javaObjectType, restored.getValue("count").javaClass)
        assertEquals(Long::class.javaObjectType, restored.getValue("smallLong").javaClass)
        assertEquals(Float::class.javaObjectType, restored.getValue("ratio").javaClass)
    }

    @Test
    fun legacyStatsRestoreUsingTheStoredPreferenceTypes() {
        val restored = PreferencesBackup.decode(
            "mindvault_stats", """
            {
                "total_focus_minutes": 12,
                "total_focus_hours": 2,
                "weekly_goal": 900,
                "monthly_goal": 4500,
                "total_sessions": 3,
                "longest_streak": 2,
                "experience_points": 45,
                "daily_focus_2026-09-17": 12,
                "daily_study_2026-09-17": 10,
                "daily_rest_2026-09-17": 2,
                "daily_completed_2026-09-17": 1,
                "daily_total_2026-09-17": 3,
                "daily_distractions_2026-09-17": 2,
                "achievements": "first_session"
            }
        """.trimIndent()
        )

        listOf(
            "total_focus_minutes", "total_focus_hours", "weekly_goal", "monthly_goal",
            "daily_focus_2026-09-17", "daily_study_2026-09-17", "daily_rest_2026-09-17"
        ).forEach { assertEquals(it, Long::class.javaObjectType, restored.getValue(it).javaClass) }
        listOf(
            "total_sessions", "longest_streak", "experience_points",
            "daily_completed_2026-09-17", "daily_total_2026-09-17", "daily_distractions_2026-09-17"
        ).forEach { assertEquals(it, Int::class.javaObjectType, restored.getValue(it).javaClass) }
        assertEquals(12L, restored["total_focus_minutes"])
        assertEquals("first_session", restored["achievements"])
    }

    @Test
    fun legacyUsersRestoreGoalsAndStringSetsOnAFreshInstall() {
        val restored = PreferencesBackup.decode(
            "mindvault_users", """
            {"all_user_ids":["alice","bob"],"user_alice_weekly_goal":900,
             "user_alice_monthly_goal":4500,"user_alice_active":true,"current_user_id":"alice"}
        """.trimIndent()
        )

        assertEquals(setOf("alice", "bob"), restored["all_user_ids"])
        assertEquals(900L, restored["user_alice_weekly_goal"])
        assertEquals(4500L, restored["user_alice_monthly_goal"])
        assertEquals(true, restored["user_alice_active"])
        assertEquals("alice", restored["current_user_id"])
    }

    @Test
    fun legacyFocusConfigurationRemainsAnOpaqueString() {
        val restored = PreferencesBackup.decode(
            "FocusModePrefs", """
            {"FocusModeEnabled":false,"FocusConfiguration":"{\"timeSlots\":[]}"}
        """.trimIndent()
        )
        assertEquals(false, restored["FocusModeEnabled"])
        assertEquals("{\"timeSlots\":[]}", restored["FocusConfiguration"])
    }

    @Test
    fun invalidTypesVersionsAndNumericValuesAreRejected() {
        val invalid = listOf(
            "null", "[]", "{", "{\"version\":2,\"entries\":{}}",
            "{\"version\":1}",
            """{"version":1,"entries":{"x":{"type":"int","value":1.5}}}""",
            """{"version":1,"entries":{"x":{"type":"int","value":2147483648}}}""",
            """{"version":1,"entries":{"x":{"type":"long","value":9223372036854775808}}}""",
            """{"version":1,"entries":{"x":{"type":"float","value":1e100}}}""",
            """{"version":1,"entries":{"x":{"type":"boolean","value":"true"}}}""",
            """{"version":1,"entries":{"x":{"type":"string_set","value":["a",2]}}}""",
            """{"version":1,"entries":{"x":{"type":"unknown","value":2}}}""",
            """{"version":1,"entries":{"x":{"type":"string","value":null}}}""",
            """{"unknown_numeric_key":4}"""
        )
        invalid.forEach { json ->
            assertThrows(json, RuntimeException::class.java) {
                PreferencesBackup.decode("mindvault_stats", json)
            }
        }
    }

    @Test
    fun allSectionsAreValidatedBeforeReturningRestoreData() {
        val valid = mapOf<String, Any?>(
            "user_prefs" to """{"all_user_ids":[]}""",
            "focus_prefs" to """{"FocusModeEnabled":true}""",
            "stats_prefs" to """{"total_sessions":3}"""
        )
        val restored = PreferencesBackup.decodeSections(valid)
        assertEquals(3, restored.getValue("mindvault_stats")["total_sessions"])
        assertEquals(emptySet<String>(), restored.getValue("mindvault_users")["all_user_ids"])

        listOf(
            valid - "stats_prefs", valid + ("stats_prefs" to 3),
            valid + ("stats_prefs" to "{bad json"), valid + ("stats_prefs" to null)
        ).forEach { invalid ->
            assertThrows(RuntimeException::class.java) { PreferencesBackup.decodeSections(invalid) }
        }
    }

    @Test
    fun emptyPreferencesRoundTrip() {
        assertEquals(
            emptyMap<String, Any>(),
            PreferencesBackup.decode("FocusModePrefs", PreferencesBackup.encode(emptyMap<String, Any>()))
        )
    }
}
