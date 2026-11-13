package com.fitifysync.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitifysync.app.data.model.FitifyWorkout
import com.fitifysync.app.data.repository.DefaultFitifySyncRepository
import com.fitifysync.app.data.repository.FitifySyncRepository
import com.fitifysync.app.ui.state.UiEvent
import com.fitifysync.app.ui.state.UiState
import com.fitifysync.app.ui.state.WorkoutStats
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val repository: FitifySyncRepository
) : AndroidViewModel(application) {

    // Secondary constructor for default Android ViewModelProvider.Factory
    constructor(application: Application) : this(
        application,
        DefaultFitifySyncRepository(application)
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    fun initChecks() {
        viewModelScope.launch {
            val rootBinaryAvailable = repository.isSuBinaryAvailable()
            val hasRoot = if (rootBinaryAvailable) repository.hasRootPermission() else false
            val hcAvailable = repository.isHealthConnectAvailable()
            val hcPerms = if (hcAvailable) repository.hasHealthConnectPermissions() else false

            _uiState.update { current ->
                current.copy(
                    isRootGranted = hasRoot,
                    isHealthConnectAvailable = hcAvailable,
                    hasHealthPermissions = hcPerms,
                    statusMessage = when {
                        !hasRoot -> "Root access required. Grant 'su' in Magisk/KernelSU."
                        !hcAvailable -> "Health Connect not installed. Install from Play Store."
                        !hcPerms -> "Health Connect permissions required."
                        else -> "Ready to extract Fitify workouts."
                    }
                )
            }
        }
    }

    fun requestRoot() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Requesting root privileges...") }
            val granted = repository.hasRootPermission()
            _uiState.update {
                it.copy(
                    isRootGranted = granted,
                    statusMessage = if (granted) "Root privileges granted!" else "Root request denied or unavailable."
                )
            }
        }
    }

    fun requestHealthPermissions() {
        viewModelScope.launch {
            val perms = repository.getRequiredHealthPermissions()
            _events.emit(UiEvent.RequestHealthPermissions(perms))
        }
    }

    fun onPermissionsGranted(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasHealthPermissions = granted,
                statusMessage = if (granted) "Health Connect permissions granted." else "Permissions were not granted."
            )
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSyncing = true,
                    statusMessage = "Dumping Fitify database via root..."
                )
            }

            val result = repository.extractAndSyncWorkouts()

            result.onSuccess { pair ->
                val (workouts, summary) = pair
                val computedStats = computeStats(workouts)
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        workouts = workouts,
                        stats = computedStats,
                        statusMessage = summary.summaryText + (summary.errorMessage?.let { message -> " $message" } ?: "")
                    )
                }
                _events.emit(UiEvent.ShowToast(summary.summaryText, isLong = summary.failedCount > 0))
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        statusMessage = "Extraction / sync failed: ${error.localizedMessage}"
                    )
                }
                _events.emit(UiEvent.ShowToast("Sync error: ${error.localizedMessage}", isLong = true))
            }
        }
    }

    fun toggleWorkoutExpansion(workoutId: String) {
        _uiState.update { current ->
            val updated = if (current.expandedWorkoutIds.contains(workoutId)) {
                current.expandedWorkoutIds - workoutId
            } else {
                current.expandedWorkoutIds + workoutId
            }
            current.copy(expandedWorkoutIds = updated)
        }
    }

    fun exportToCsv() {
        viewModelScope.launch {
            val workouts = _uiState.value.workouts
            if (workouts.isEmpty()) {
                _events.emit(UiEvent.ShowToast("No workouts to export. Sync first."))
                return@launch
            }

            val result = repository.exportWorkoutsToCsv(workouts)
            result.onSuccess { file ->
                _events.emit(UiEvent.ShowToast("Exported ${workouts.size} sessions to: ${file.name}", isLong = true))
            }.onFailure { error ->
                _events.emit(UiEvent.ShowToast("Export failed: ${error.localizedMessage}"))
            }
        }
    }

    fun togglePeriodicSync(enable: Boolean) {
        repository.setPeriodicSync(enable)
        _uiState.update { it.copy(isPeriodicSyncActive = enable) }
        viewModelScope.launch {
            _events.emit(
                UiEvent.ShowToast(
                    if (enable) "Automated sync enabled (Every 6 hours)" else "Automated sync disabled"
                )
            )
        }
    }

    private fun computeStats(workouts: List<FitifyWorkout>): WorkoutStats {
        if (workouts.isEmpty()) return WorkoutStats()
        val totalDuration = workouts.sumOf { it.durationSeconds }
        val totalCalories = workouts.sumOf { it.caloriesBurned }
        val hrList = workouts.mapNotNull { it.avgHeartRate }
        val avgHr = if (hrList.isNotEmpty()) hrList.average().toInt() else null
        val minHr = workouts.mapNotNull { it.minHeartRate }.minOrNull()
        val maxHr = workouts.mapNotNull { it.maxHeartRate }.maxOrNull()
        return WorkoutStats(
            totalSessions = workouts.size,
            totalDurationSeconds = totalDuration,
            totalCalories = totalCalories,
            avgHeartRate = avgHr,
            minHeartRate = minHr,
            maxHeartRate = maxHr
        )
    }
}
