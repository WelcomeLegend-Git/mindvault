package com.example.mindvault.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/** Type tags preserve SharedPreferences types, which plain JSON numbers cannot express. */
internal object PreferencesBackup {
    private const val VERSION = 1
    private val sections = mapOf(
        "user_prefs" to "mindvault_users",
        "focus_prefs" to "FocusModePrefs",
        "stats_prefs" to "mindvault_stats"
    )

    fun encode(values: Map<String, *>): String {
        val entries = JsonObject()
        values.forEach { (key, value) ->
            val type = when (value) {
                is String -> "string"
                is Boolean -> "boolean"
                is Int -> "int"
                is Long -> "long"
                is Float -> "float"
                is Set<*> -> "string_set"
                else -> error("Unsupported preference type for $key")
            }
            val jsonValue: JsonElement = when (value) {
                is String -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is Number -> {
                    require(value !is Float || value.isFinite()) { "Non-finite preference: $key" }
                    JsonPrimitive(value)
                }

                is Set<*> -> com.google.gson.JsonArray().apply {
                    value.forEach {
                        require(it is String) { "Non-string set member: $key" }
                        add(it)
                    }
                }

                else -> error("Unsupported preference type for $key")
            }
            entries.add(key, JsonObject().apply {
                addProperty("type", type)
                add("value", jsonValue)
            })
        }
        return JsonObject().apply {
            addProperty("version", VERSION)
            add("entries", entries)
        }.toString()
    }

    /** Fully decode every required section before the caller opens any preference editor. */
    fun decodeSections(backup: Map<String, Any?>): Map<String, Map<String, Any>> =
        sections.entries.associate { (field, prefsName) ->
            val json = backup[field]
            require(json is String) { "Missing or invalid backup section: $field" }
            prefsName to decode(prefsName, json)
        }

    fun decode(prefsName: String, json: String): Map<String, Any> {
        val root = JsonParser.parseString(json)
        require(root.isJsonObject) { "Preference backup must be an object" }
        val document = root.asJsonObject
        if (!document.has("version")) {
            return document.entrySet().associate { (key, value) ->
                key to decodeValue(legacyType(prefsName, key, value), value)
            }
        }
        val version = document.get("version")
        require(
            version.isJsonPrimitive && version.asJsonPrimitive.isNumber &&
                    version.asBigDecimal.intValueExact() == VERSION
        ) { "Unsupported backup version" }
        val entries = document.get("entries")
        require(entries != null && entries.isJsonObject) { "Missing backup entries" }
        return entries.asJsonObject.entrySet().associate { (key, entry) ->
            require(entry.isJsonObject) { "Invalid preference entry: $key" }
            val type = entry.asJsonObject.get("type")
            require(type != null && type.isJsonPrimitive && type.asJsonPrimitive.isString) {
                "Missing preference type: $key"
            }
            val value = entry.asJsonObject.get("value")
            require(value != null) { "Missing preference value: $key" }
            key to decodeValue(type.asString, value)
        }
    }

    private fun decodeValue(type: String, value: JsonElement): Any {
        if (type == "string_set") {
            require(value.isJsonArray) { "Expected string set" }
            return value.asJsonArray.map {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "Expected string set member" }
                it.asString
            }.toSet()
        }
        require(value.isJsonPrimitive) { "Expected preference primitive" }
        val primitive = value.asJsonPrimitive
        return when (type) {
            "string" -> {
                require(primitive.isString) { "Expected string" }
                primitive.asString
            }

            "boolean" -> {
                require(primitive.isBoolean) { "Expected boolean" }
                primitive.asBoolean
            }

            "int", "long", "float" -> {
                require(primitive.isNumber) { "Expected number" }
                when (type) {
                    "int" -> primitive.asBigDecimal.intValueExact()
                    "long" -> primitive.asBigDecimal.longValueExact()
                    else -> primitive.asFloat.also { require(it.isFinite()) { "Invalid float" } }
                }
            }

            else -> error("Unsupported preference type: $type")
        }
    }

    private fun legacyType(prefsName: String, key: String, value: JsonElement): String {
        if (value.isJsonArray) return "string_set"
        require(value.isJsonPrimitive) { "Invalid legacy preference: $key" }
        val primitive = value.asJsonPrimitive
        if (primitive.isString) return "string"
        if (primitive.isBoolean) return "boolean"
        // Legacy backups have no numeric type metadata. Use the application's storage schema,
        // not the number's magnitude: even a small goal must be restored with putLong().
        return when {
            prefsName == "mindvault_users" && key.startsWith("user_") &&
                    (key.endsWith("_weekly_goal") || key.endsWith("_monthly_goal")) -> "long"

            prefsName == "mindvault_stats" && (key in setOf(
                "total_focus_hours", "total_focus_minutes", "weekly_goal", "monthly_goal"
            ) || listOf("daily_focus_", "daily_study_", "daily_rest_").any(key::startsWith)) -> "long"

            prefsName == "mindvault_stats" && (key in setOf(
                "total_sessions", "longest_streak", "experience_points"
            ) || listOf("daily_completed_", "daily_total_", "daily_distractions_").any(key::startsWith)) -> "int"

            else -> error("Unknown legacy numeric preference: $prefsName/$key")
        }
    }
}
