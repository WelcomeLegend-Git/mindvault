package com.example.mindvault.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindvault.data.FocusDataStore
import com.example.mindvault.data.FocusManager
import com.example.mindvault.data.AuthManager
import com.example.mindvault.model.AppInfo
import com.example.mindvault.model.FocusConfiguration
import com.example.mindvault.model.TimeSlot
import com.example.mindvault.utils.AppManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FocusModeSetupViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FocusModeSetupState())
    val uiState: StateFlow<FocusModeSetupState> = _uiState.asStateFlow()

    fun loadConfiguration(context: Context) {
        viewModelScope.launch {
            try {
                // Load configuration on IO thread
                val config = withContext(Dispatchers.IO) {
                    FocusDataStore.getConfiguration(context)
                }

                // Load installed apps on IO thread with error handling
                // Only load installed apps if the list is not already populated.
                val installedApps = if (_uiState.value.installedApps.isEmpty()) {
                    withContext(Dispatchers.IO) {
                        try {
                            AppManager.getInstalledApps(context)
                        } catch (e: Exception) {
                            Log.e("FocusModeSetupViewModel", "Error loading installed apps", e)
                            emptyList<AppInfo>()
                        }
                    }
                } else {
                    _uiState.value.installedApps
                }

                val isFocusModeActive = withContext(Dispatchers.IO) {
                    FocusManager.isFocusModeActive()
                }

                _uiState.update {
                    it.copy(
                        timeSlots = config.timeSlots,
                        selectedApps = config.selectedApps,
                        installedApps = installedApps,
                        isFocusModeActive = isFocusModeActive
                    )
                }
            } catch (e: Exception) {
                Log.e("FocusModeSetupViewModel", "Error loading configuration", e)
            }
        }
    }

    /**
     * Single save path: FocusManager owns storage + cloud sync, so the ViewModel must not
     * write preferences or trigger a second backup. Disk work runs off the main thread and
     * the result is reported to the caller.
     */
    fun saveConfiguration(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val currentState = _uiState.value
                    // Preserve the authoritative enabled state across configuration edits.
                    val newConfig = FocusConfiguration(
                        timeSlots = currentState.timeSlots,
                        selectedApps = currentState.selectedApps,
                        focusModeEnabled = FocusManager.getFocusModeEnabled()
                    )
                    Log.d("FocusModeSetupViewModel", "Saving configuration with ${newConfig.timeSlots.size} slots")
                    FocusManager.updateConfiguration(newConfig)
                    true
                } catch (e: Exception) {
                    Log.e("FocusModeSetupViewModel", "Error saving configuration", e)
                    false
                }
            }
            onResult(ok)
        }
    }

    fun addTimeSlot(timeSlot: TimeSlot) {
        // Draft edits stay local; the single backup happens on save.
        _uiState.update {
            it.copy(timeSlots = it.timeSlots + timeSlot)
        }
    }

    fun deleteTimeSlot(timeSlot: TimeSlot) {
        _uiState.update {
            it.copy(timeSlots = it.timeSlots.filter { it.id != timeSlot.id })
        }
    }

    fun onAppsSelected(selectedApps: List<String>) {
        _uiState.update {
            it.copy(selectedApps = selectedApps)
        }
    }
}

data class FocusModeSetupState(
    val timeSlots: List<TimeSlot> = emptyList(),
    val selectedApps: List<String> = emptyList(),
    val installedApps: List<AppInfo> = emptyList(),
    val isFocusModeActive: Boolean = false
)
