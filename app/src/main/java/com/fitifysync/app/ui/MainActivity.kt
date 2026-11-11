package com.fitifysync.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fitifysync.app.data.healthconnect.HealthConnectManager
import com.fitifysync.app.data.model.FitifyWorkout
import com.fitifysync.app.data.parser.FitifyDatabaseParser
import com.fitifysync.app.data.root.RootRunner
import com.fitifysync.app.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.util.concurrent.TimeUnit

data class UiState(
    val isRootGranted: Boolean? = null,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthPermissions: Boolean = false,
    val isSyncing: Boolean = false,
    val statusMessage: String = "Ready to extract & sync.",
    val workouts: List<FitifyWorkout> = emptyList(),
    val isPeriodicSyncActive: Boolean = false
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    fun initChecks(activity: MainActivity) {
        viewModelScope.launch {
            val rootAvailable = RootRunner.isSuBinaryAvailable()
            val hasRoot = if (rootAvailable) RootRunner.hasRootPermission() else false
            val hcManager = HealthConnectManager(activity)
            val hcAvailable = hcManager.isAvailable()
            val hcPerms = if (hcAvailable) hcManager.hasAllPermissions() else false

            _uiState.value = _uiState.value.copy(
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

    fun requestRoot() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(statusMessage = "Requesting root privileges...")
            val granted = RootRunner.hasRootPermission()
            _uiState.value = _uiState.value.copy(
                isRootGranted = granted,
                statusMessage = if (granted) "Root privileges granted!" else "Root request denied or unavailable."
            )
        }
    }

    fun onPermissionsGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(
            hasHealthPermissions = granted,
            statusMessage = if (granted) "Health Connect permissions granted." else "Permissions were not granted."
        )
    }

    fun triggerSync(activity: MainActivity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, statusMessage = "Dumping Fitify database via root...")

            // 1. Root dump
            val dumpResult = RootRunner.dumpFitifyDatabase(activity)
            if (dumpResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    statusMessage = "Root dump failed: ${dumpResult.exceptionOrNull()?.message}"
                )
                return@launch
            }

            val dbFile = dumpResult.getOrThrow()

            // 2. Parse database
            _uiState.value = _uiState.value.copy(statusMessage = "Parsing workout tables...")
            val parsedList = FitifyDatabaseParser.parseWorkouts(dbFile)

            if (parsedList.isEmpty()) {
                dbFile.delete()
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    statusMessage = "No workouts found in Fitify database."
                )
                return@launch
            }

            // 3. Health Connect sync
            _uiState.value = _uiState.value.copy(
                workouts = parsedList,
                statusMessage = "Syncing ${parsedList.size} workouts to Health Connect..."
            )

            val hcManager = HealthConnectManager(activity)
            val syncResult = hcManager.syncWorkouts(parsedList)

            // Cleanup local file
            try {
                dbFile.delete()
                File("${dbFile.absolutePath}-wal").delete()
                File("${dbFile.absolutePath}-shm").delete()
            } catch (_: Exception) {}

            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                workouts = parsedList,
                statusMessage = if (syncResult.errorMessage != null) {
                    "Sync partial/error: ${syncResult.errorMessage}"
                } else {
                    "Success: ${syncResult.newlySynced} new, ${syncResult.alreadySynced} already up-to-date."
                }
            )
        }
    }

    fun exportToCsv(activity: MainActivity) {
        val list = _uiState.value.workouts
        if (list.isEmpty()) {
            Toast.makeText(activity, "No workouts to export. Sync first.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val exportDir = activity.getExternalFilesDir(null) ?: activity.filesDir
            val csvFile = File(exportDir, "fitify_workouts_export.csv")
            val writer = FileWriter(csvFile)
            writer.append("ID,Title,WorkoutType,StartTime,EndTime,DurationSeconds,CaloriesBurned\n")
            for (w in list) {
                writer.append("\"${w.id}\",\"${w.title}\",\"${w.workoutType}\",\"${w.startTime}\",\"${w.endTime}\",${w.durationSeconds},${w.caloriesBurned}\n")
            }
            writer.flush()
            writer.close()
            Toast.makeText(activity, "Exported ${list.size} sessions to: ${csvFile.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun togglePeriodicSync(activity: MainActivity, enable: Boolean) {
        val workManager = WorkManager.getInstance(activity)
        if (enable) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                "FitifyPeriodicSync",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            _uiState.value = _uiState.value.copy(isPeriodicSyncActive = true)
            Toast.makeText(activity, "Automated sync enabled (Every 6 hours)", Toast.LENGTH_SHORT).show()
        } else {
            workManager.cancelUniqueWork("FitifyPeriodicSync")
            _uiState.value = _uiState.value.copy(isPeriodicSyncActive = false)
            Toast.makeText(activity, "Automated sync disabled", Toast.LENGTH_SHORT).show()
        }
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var healthPermissionsLauncher: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hcManager = HealthConnectManager(this)
        val contract = PermissionController.createRequestPermissionResultContract()
        healthPermissionsLauncher = registerForActivityResult(contract) { grantedPermissions ->
            viewModel.onPermissionsGranted(grantedPermissions.containsAll(hcManager.requiredPermissions))
        }

        setContent {
            MaterialTheme {
                Surface {
                    MainScreen(
                        viewModel = viewModel,
                        onRequestRoot = { viewModel.requestRoot() },
                        onRequestHealthPermissions = {
                            healthPermissionsLauncher.launch(hcManager.requiredPermissions)
                        },
                        onSyncNow = { viewModel.triggerSync(this) },
                        onExportCsv = { viewModel.exportToCsv(this) },
                        onTogglePeriodic = { viewModel.togglePeriodicSync(this, it) }
                    )
                }
            }
        }

        viewModel.initChecks(this)
    }
}
