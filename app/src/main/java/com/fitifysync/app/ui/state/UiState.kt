package com.fitifysync.app.ui.state

import com.fitifysync.app.data.model.FitifyWorkout
import java.util.Locale

data class WorkoutStats(
    val totalSessions: Int = 0,
    val totalDurationSeconds: Long = 0L,
    val totalCalories: Double = 0.0,
    val avgHeartRate: Int? = null,
    val minHeartRate: Int? = null,
    val maxHeartRate: Int? = null
) {
    val formattedDuration: String
        get() {
            val hours = totalDurationSeconds / 3600
            val minutes = (totalDurationSeconds % 3600) / 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }

    val formattedCalories: String
        get() = when {
            totalCalories >= 1000 -> "${String.format(Locale.US, "%.1f", totalCalories / 1000)}k kcal"
            else -> "${totalCalories.toInt()} kcal"
        }
}

data class UiState(
    val isRootGranted: Boolean? = null,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthPermissions: Boolean = false,
    val isSyncing: Boolean = false,
    val statusMessage: String = "Ready to extract & sync.",
    val workouts: List<FitifyWorkout> = emptyList(),
    val stats: WorkoutStats = WorkoutStats(),
    val expandedWorkoutIds: Set<String> = emptySet(),
    val isPeriodicSyncActive: Boolean = false
) {
    val canSync: Boolean
        get() = isRootGranted == true && hasHealthPermissions && !isSyncing
}
