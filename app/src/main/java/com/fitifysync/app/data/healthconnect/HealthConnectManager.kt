package com.fitifysync.app.data.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import com.fitifysync.app.data.model.FitifyWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneOffset

data class SyncResult(
    val totalWorkouts: Int,
    val newlySynced: Int,
    val alreadySynced: Int,
    val failedCount: Int,
    val errorMessage: String? = null
)

class HealthConnectManager(private val context: Context) {
    private val TAG = "HealthConnectManager"

    val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    val requiredPermissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
    )

    /**
     * Checks if Health Connect SDK is supported and installed on this device.
     */
    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * Checks if all required Health Connect permissions are granted.
     */
    suspend fun hasAllPermissions(): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext false
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return@withContext granted.containsAll(requiredPermissions)
    }

    /**
     * Retrieves previously synced client record IDs from Health Connect to prevent duplicates.
     */
    suspend fun getExistingClientRecordIds(workouts: List<FitifyWorkout>): Set<String> = withContext(Dispatchers.IO) {
        if (workouts.isEmpty()) return@withContext emptySet()
        val earliest = workouts.minOf { it.startTime }
        val latest = workouts.maxOf { it.endTime }

        try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(earliest, latest)
                )
            )
            response.records.mapNotNull { it.metadata.clientRecordId }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking existing records in Health Connect", e)
            emptySet()
        }
    }

    /**
     * Inserts extracted Fitify workouts into Health Connect.
     * Writes ExerciseSessionRecord and ActiveCaloriesBurnedRecord.
     */
    suspend fun syncWorkouts(workouts: List<FitifyWorkout>): SyncResult = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext SyncResult(workouts.size, 0, 0, workouts.size, "Health Connect is not available on this device.")
        }
        if (!hasAllPermissions()) {
            return@withContext SyncResult(workouts.size, 0, 0, workouts.size, "Health Connect permissions have not been granted.")
        }

        val existingIds = getExistingClientRecordIds(workouts)
        var syncedCount = 0
        var alreadyCount = 0
        var errorCount = 0

        val recordsToInsert = mutableListOf<androidx.health.connect.client.records.Record>()

        for (workout in workouts) {
            val clientRecordId = "fitify_${workout.id}"
            if (existingIds.contains(clientRecordId)) {
                workout.isSynced = true
                alreadyCount++
                continue
            }

            try {
                val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(workout.startTime)

                // 1. Exercise Session Record
                val sessionRecord = ExerciseSessionRecord(
                    startTime = workout.startTime,
                    startZoneOffset = zoneOffset,
                    endTime = workout.endTime,
                    endZoneOffset = zoneOffset,
                    exerciseType = workout.toHealthConnectExerciseType(),
                    title = workout.title,
                    notes = workout.rawNotes ?: "Imported from Fitify with Root Access",
                    metadata = Metadata(clientRecordId = clientRecordId)
                )
                recordsToInsert.add(sessionRecord)

                // 2. Active Calories Burned Record
                if (workout.caloriesBurned > 0) {
                    val caloriesRecord = ActiveCaloriesBurnedRecord(
                        startTime = workout.startTime,
                        startZoneOffset = zoneOffset,
                        endTime = workout.endTime,
                        endZoneOffset = zoneOffset,
                        energy = Energy.kilocalories(workout.caloriesBurned),
                        metadata = Metadata(clientRecordId = "${clientRecordId}_cal")
                    )
                    recordsToInsert.add(caloriesRecord)
                }

                workout.isSynced = true
                syncedCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create Health Connect record for workout ${workout.id}", e)
                errorCount++
            }
        }

        if (recordsToInsert.isNotEmpty()) {
            try {
                // Insert in batches of 50 to respect Health Connect rate limits
                recordsToInsert.chunked(50).forEach { batch ->
                    healthConnectClient.insertRecords(batch)
                }
                Log.i(TAG, "Inserted ${recordsToInsert.size} records into Health Connect.")
            } catch (e: Exception) {
                Log.e(TAG, "Batch insert to Health Connect failed", e)
                return@withContext SyncResult(
                    workouts.size,
                    0,
                    alreadyCount,
                    syncedCount,
                    "Failed inserting records into Health Connect: ${e.localizedMessage}"
                )
            }
        }

        SyncResult(
            totalWorkouts = workouts.size,
            newlySynced = syncedCount,
            alreadySynced = alreadyCount,
            failedCount = errorCount
        )
    }
}
