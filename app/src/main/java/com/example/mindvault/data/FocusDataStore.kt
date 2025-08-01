package com.example.mindvault.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.example.mindvault.model.FocusConfiguration

object FocusDataStore {
    private const val PREFS_NAME = "FocusModePrefs"
    private const val CONFIG_KEY = "FocusConfiguration"

    private val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(LocalTime::class.java, LocalTimeAdapter())
            .create()
    }

    fun saveConfiguration(context: Context, config: FocusConfiguration) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        try {
            val json = gson.toJson(config)
            editor.putString(CONFIG_KEY, json)
            editor.commit() // Using commit for synchronous save
            Log.d("FocusDataStore", "Configuration saved successfully: $json")
        } catch (e: Exception) {
            Log.e("FocusDataStore", "Error saving configuration", e)
        }
    }

    fun getConfiguration(context: Context): FocusConfiguration {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(CONFIG_KEY, null)
        return if (json != null) {
            try {
                val config = gson.fromJson(json, FocusConfiguration::class.java)
                Log.d("FocusDataStore", "Configuration loaded successfully: $json")
                config ?: FocusConfiguration()
            } catch (e: JsonSyntaxException) {
                Log.e("FocusDataStore", "Error parsing configuration JSON", e)
                FocusConfiguration() // Return default config on error
            }
        } else {
            Log.d("FocusDataStore", "No configuration found, returning default.")
            FocusConfiguration() // Return default if no config saved
        }
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
