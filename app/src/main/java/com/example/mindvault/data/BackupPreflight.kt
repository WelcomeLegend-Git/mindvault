package com.example.mindvault.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.internal.Streams
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.time.LocalDateTime
import java.time.LocalTime

/** Side-effect-free validation against the representations actually read by the data managers.
 * Keep this schema in step with their SharedPreferences getters, not JSON numeric magnitudes.
 */
internal object BackupPreflight {
    fun validate(image: PreferenceImage) {
        image["mindvault_users"]?.let(::users)
        image["FocusModePrefs"]?.let(::focus)
        image["mindvault_stats"]?.let(::stats)
    }

    private inline fun <reified T> optional(values: Map<String, Any>, key: String) {
        require(!values.containsKey(key) || values[key] is T) { "Invalid preference type: $key (expected ${T::class.simpleName})" }
    }

    private fun users(values: Map<String, Any>) {
        optional<String>(values, "current_user_id")
        optional<Set<*>>(values, "all_user_ids")
        val ids = (values["all_user_ids"] as? Set<*>).orEmpty()
        require(ids.all { it is String }) { "Invalid user IDs" }
        val current = values["current_user_id"] as? String
        require(current != null && current in ids) { "Missing current profile" }
        ids.forEach { id ->
            val prefix = "user_${id}_"
            listOf("name", "email", "role", "created", "last_active", "profile_pic", "theme").forEach {
                optional<String>(values, prefix + it)
            }
            listOf("active", "notifications", "reminder", "export", "analytics").forEach {
                optional<Boolean>(values, prefix + it)
            }
            listOf("weekly_goal", "monthly_goal").forEach { optional<Long>(values, prefix + it) }
            // All listed profiles are read by getUserByEmail/getAllUsers, not only the selected one.
            listOf("name", "email", "created", "last_active").forEach {
                require(values[prefix + it] is String) { "Incomplete profile: $id/$it" }
            }
            listOf("created", "last_active").forEach { LocalDateTime.parse(values[prefix + it] as String) }
            require((values[prefix + "role"] ?: "STANDARD") in setOf("ADMIN", "PREMIUM", "STANDARD"))
        }
        require(values["user_${current}_active"] != false) { "Inactive current profile" }
    }

    private fun focus(values: Map<String, Any>) {
        optional<Boolean>(values, "FocusModeEnabled")
        optional<String>(values, "FocusConfiguration")
        val json = values["FocusConfiguration"] as? String ?: return
        val root = objectValue(parse(json))
        root.get("focusModeEnabled")?.let(::boolean)
        root.get("selectedApps")?.let(::strings)
        root.get("timeSlots")?.let { slots ->
            require(slots.isJsonArray) { "Focus timeSlots must be an array" }
            val ids = mutableSetOf<String>()
            slots.asJsonArray.forEach { element ->
                val slot = objectValue(element)
                require(ids.add(string(required(slot, "id")))) { "Duplicate focus slot ID" }
                LocalTime.parse(string(required(slot, "startTime")))
                LocalTime.parse(string(required(slot, "endTime")))
                require(string(required(slot, "type")) in setOf("STUDY_TIME", "REST_TIME")) { "Invalid focus type" }
                slot.get("selectedApps")?.let(::strings)
            }
        }
    }

    private fun stats(values: Map<String, Any>) {
        values.forEach { (key, _) ->
            when {
                key in setOf("total_focus_hours", "total_focus_minutes", "weekly_goal", "monthly_goal") ||
                        listOf("daily_focus_", "daily_study_", "daily_rest_").any(key::startsWith) -> optional<Long>(
                    values,
                    key
                )

                key in setOf("total_sessions", "longest_streak", "experience_points") ||
                        listOf(
                            "daily_completed_",
                            "daily_total_",
                            "daily_distractions_"
                        ).any(key::startsWith) -> optional<Int>(values, key)

                key == "achievements" -> optional<String>(values, key)
                key == "current_session" -> {
                    optional<String>(values, key)
                    val session = objectValue(parse(values.getValue(key) as String))
                    string(required(session, "id"))
                    string(required(session, "type"))
                    LocalDateTime.parse(string(required(session, "startTime")))
                    session.get("lastObservedTime")?.let { LocalDateTime.parse(string(it)) }
                    session.get("blockedApps")?.let(::strings)
                    session.get("distractionCount")?.let { integer(it).intValueExact() }
                }

                key.startsWith("sessions_") -> {
                    optional<String>(values, key)
                    val text = values.getValue(key) as String
                    if (text.isNotEmpty()) {
                        val sessions = parse(text)
                        require(sessions.isJsonArray) { "Session history must be an array" }
                        sessions.asJsonArray.forEach { element ->
                            val session = objectValue(element)
                            listOf("id", "type").forEach { field -> session.get(field)?.let(::string) }
                            listOf("start", "end").forEach { field ->
                                session.get(field)?.let { integer(it).longValueExact() }
                            }
                            session.get("isCompleted")?.let(::boolean)
                            session.get("distractionCount")?.let { integer(it).intValueExact() }
                            session.get("blockedApps")?.let(::strings)
                        }
                    }
                }
            }
        }
    }

    private fun parse(text: String): JsonElement = JsonReader(StringReader(text)).use { reader ->
        reader.isLenient = false
        val value = Streams.parse(reader)
        require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing JSON data" }
        value
    }

    private fun required(obj: JsonObject, key: String): JsonElement =
        requireNotNull(obj.get(key)) { "Missing JSON field: $key" }

    private fun objectValue(value: JsonElement): JsonObject {
        require(value.isJsonObject) { "Expected JSON object" }
        return value.asJsonObject
    }

    private fun string(value: JsonElement): String {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Expected JSON string" }
        return value.asString
    }

    private fun boolean(value: JsonElement) {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "Expected JSON boolean" }
    }

    private fun integer(value: JsonElement): java.math.BigDecimal {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "Expected JSON number" }
        return value.asBigDecimal
    }

    private fun strings(value: JsonElement) {
        require(value.isJsonArray) { "Expected string array" }
        value.asJsonArray.forEach { string(it) }
    }
}
