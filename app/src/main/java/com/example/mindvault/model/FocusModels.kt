package com.example.mindvault.model

import android.graphics.drawable.Drawable
import java.time.LocalTime

enum class FocusType {
    STUDY_TIME,
    REST_TIME
}

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable
)

data class TimeSlot(
    val id: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val type: FocusType,
    val selectedApps: List<String> = emptyList()
)

data class FocusConfiguration(
    val timeSlots: List<TimeSlot> = emptyList(),
    val selectedApps: List<String> = emptyList(),
    val focusModeEnabled: Boolean = true
)
