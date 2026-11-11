package com.fitifysync.app.ui.state

import com.fitifysync.app.data.model.FitifyWorkout

data class UiState(
    val isRootGranted: Boolean? = null,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthPermissions: Boolean = false,
    val isSyncing: Boolean = false,
    val statusMessage: String = "Ready to extract & sync.",
    val workouts: List<FitifyWorkout> = emptyList(),
    val isPeriodicSyncActive: Boolean = false
) {
    val canSync: Boolean
        get() = isRootGranted == true && hasHealthPermissions && !isSyncing
}
