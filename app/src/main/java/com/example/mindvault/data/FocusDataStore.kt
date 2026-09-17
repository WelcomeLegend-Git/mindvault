package com.example.mindvault.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.google.gson.reflect.TypeToken
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.example.mindvault.model.FocusConfiguration

object FocusDataStore {
    private const val PREFS_NAME = "FocusModePrefs"
    private const val CONFIG_KEY = "FocusConfiguration"
    private const val FOCUS_MODE_ENABLED_KEY = "FocusModeEnabled"

    private val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(LocalTime::class.java, LocalTimeAdapter())
            .create()
    }

    @Synchronized
    fun saveConfiguration(context: Context, config: FocusConfiguration) {
        LocalRestore.checkWritable()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(config)
        ManagerPersistence.attribute(context, prefs, prefs.edit())
            .putString(CONFIG_KEY, json)
            .putBoolean(FOCUS_MODE_ENABLED_KEY, config.focusModeEnabled)
            .apply()
    }

    fun getConfiguration(context: Context): FocusConfiguration {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(CONFIG_KEY, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<FocusConfiguration>() {}.type
                val config = gson.fromJson<FocusConfiguration>(json, type)

                if (config != null) {
                    // Sanitize the config to ensure lists are never null due to Gson reflection
                    val nonNullTimeSlots = config.timeSlots ?: emptyList()
                    val nonNullSelectedApps = config.selectedApps ?: emptyList()
                    return config.copy(
                        timeSlots = nonNullTimeSlots,
                        selectedApps = nonNullSelectedApps,
                        focusModeEnabled = prefs.getBoolean(FOCUS_MODE_ENABLED_KEY, config.focusModeEnabled)
                    )
                } else {
                    return FocusConfiguration(focusModeEnabled = getFocusModeEnabled(context))
                }
            } catch (e: Exception) {
                Log.e("FocusDataStore", "Error parsing configuration JSON", e)
                FocusConfiguration(focusModeEnabled = getFocusModeEnabled(context))
            }
        } else {
            Log.d("FocusDataStore", "No configuration found, returning default.")
            FocusConfiguration(focusModeEnabled = getFocusModeEnabled(context))
        }
    }

    @Synchronized
    fun setFocusModeEnabled(context: Context, enabled: Boolean) {
        LocalRestore.checkWritable()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ManagerPersistence.attribute(context, prefs, prefs.edit()).putBoolean(FOCUS_MODE_ENABLED_KEY, enabled).apply()
    }

    fun getFocusModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(FOCUS_MODE_ENABLED_KEY, true)
    }
}

class LocalTimeAdapter : TypeAdapter<LocalTime>() {
    private val formatter = DateTimeFormatter.ISO_LOCAL_TIME

    override fun write(out: JsonWriter, value: LocalTime?) {
        out.value(value?.format(formatter))
    }

    override fun read(jsonReader: JsonReader): LocalTime? {
        return if (jsonReader.peek() == com.google.gson.stream.JsonToken.NULL) {
            jsonReader.nextNull()
            null
        } else {
            LocalTime.parse(jsonReader.nextString(), formatter)
        }
    }
}
