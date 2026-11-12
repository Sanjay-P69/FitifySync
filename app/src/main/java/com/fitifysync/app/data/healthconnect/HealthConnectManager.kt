package com.fitifysync.app.data.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import com.fitifysync.app.data.model.ExerciseSegmentNoteItem
import com.fitifysync.app.data.model.FitifyNotesBuilder
import com.fitifysync.app.data.model.FitifyWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset

data class SyncResult(
    val totalWorkouts: Int,
    val newlySynced: Int,
    val alreadySynced: Int,
    val failedCount: Int,
    val errorMessage: String? = null,
    val updatedCount: Int = 0
)

data class ExistingSessionStatus(
    val clientRecordId: String,
    val hasEnrichedNotes: Boolean,
    val clientRecordVersion: Long
)

data class SegmentBuildResult(
    val segments: List<ExerciseSegment>,
    val noteItems: List<ExerciseSegmentNoteItem>
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
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
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
     * Wipes all previously synced Fitify records (ExerciseSession, ActiveCalories) from Health Connect.
     */
    suspend fun deleteAllSyncedRecords(): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext false
        try {
            val filter = TimeRangeFilter.after(Instant.EPOCH)
            healthConnectClient.deleteRecords(ExerciseSessionRecord::class, filter)
            healthConnectClient.deleteRecords(ActiveCaloriesBurnedRecord::class, filter)
            healthConnectClient.deleteRecords(TotalCaloriesBurnedRecord::class, filter)
            Log.i(TAG, "Successfully deleted all previous FitifySync records from Health Connect.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping previous records from Health Connect", e)
            false
        }
    }

    /**
     * Retrieves previously synced sessions from Health Connect with version and notes inspection
     * to support updating existing sessions with versioned segment metadata.
     */
    suspend fun getExistingSessions(workouts: List<FitifyWorkout>): Map<String, ExistingSessionStatus> = withContext(Dispatchers.IO) {
        if (workouts.isEmpty()) return@withContext emptyMap()
        val earliest = workouts.minOf { it.startTime }
        val latest = workouts.maxOf { it.endTime }
        val existing = mutableMapOf<String, ExistingSessionStatus>()
        var pageToken: String? = null

        try {
            do {
                val response = healthConnectClient.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(earliest, latest),
                        pageToken = pageToken
                    )
                )
                for (record in response.records) {
                    val id = record.metadata.clientRecordId ?: continue
                    val hasEnriched = record.notes?.contains(FitifyNotesBuilder.SCHEMA_ID) == true
                    existing[id] = ExistingSessionStatus(
                        clientRecordId = id,
                        hasEnrichedNotes = hasEnriched,
                        clientRecordVersion = record.metadata.clientRecordVersion
                    )
                }
                pageToken = response.pageToken
            } while (pageToken != null)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking existing records in Health Connect", e)
        }
        existing
    }

    /**
     * Retrieves previously synced client record IDs from Health Connect with pagination to prevent duplicates.
     */
    suspend fun getExistingClientRecordIds(workouts: List<FitifyWorkout>): Set<String> = withContext(Dispatchers.IO) {
        getExistingSessions(workouts).keys
    }

    /**
     * Reads heart rate samples from Health Connect (e.g. synced from Samsung Health) for a workout interval.
     */
    suspend fun readHeartRateForRange(startTime: Instant, endTime: Instant): Triple<Int, Int, Int>? = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext null
        try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val allSamples = response.records.flatMap { it.samples }
            if (allSamples.isNotEmpty()) {
                val bpms = allSamples.map { it.beatsPerMinute.toInt() }
                Triple(
                    bpms.average().toInt(),
                    bpms.minOrNull() ?: 0,
                    bpms.maxOrNull() ?: 0
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query heart rate for range $startTime to $endTime", e)
            null
        }
    }

    /**
     * Inserts extracted Fitify workouts into Health Connect with full ExerciseSegment breakdowns.
     */
    suspend fun syncWorkouts(workouts: List<FitifyWorkout>, wipePreviousFirst: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext SyncResult(workouts.size, 0, 0, workouts.size, "Health Connect is not available on this device.")
        }
        if (!hasAllPermissions()) {
            return@withContext SyncResult(workouts.size, 0, 0, workouts.size, "Health Connect permissions have not been granted.")
        }

        if (wipePreviousFirst) {
            deleteAllSyncedRecords()
        }

        val existingSessions = if (wipePreviousFirst) emptyMap() else getExistingSessions(workouts)
        var syncedCount = 0
        var alreadyCount = 0
        var updatedCount = 0
        var errorCount = 0
        var lastError: String? = null

        val currentBatch = mutableListOf<androidx.health.connect.client.records.Record>()
        var workoutsInBatch = 0

        for (workout in workouts) {
            val clientRecordId = "fitify_${workout.id}"
            val existing = existingSessions[clientRecordId]

            if (existing != null && existing.hasEnrichedNotes) {
                workout.isSynced = true
                alreadyCount++
                continue
            }

            val targetVersion = if (existing != null) {
                existing.clientRecordVersion + 1
            } else {
                1L
            }

            try {
                val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(workout.startTime)

                // Build nested ExerciseSegment list and companion notes payload items
                val segmentResult = buildExerciseSegments(workout)
                val segments = segmentResult.segments
                val noteText = FitifyNotesBuilder.buildNotes(
                    readableTitle = workout.title,
                    exerciseCount = workout.exercises.size,
                    items = segmentResult.noteItems
                )

                // Effective end time must be after startTime and at or after the end of the last segment
                val lastSegmentEnd = if (segments.isNotEmpty()) segments.last().endTime else workout.endTime
                val effectiveEndTime = if (lastSegmentEnd.isAfter(workout.startTime)) {
                    if (workout.endTime.isAfter(lastSegmentEnd)) workout.endTime else lastSegmentEnd
                } else {
                    workout.startTime.plusSeconds(60L)
                }

                val sessionMetadata = Metadata(
                    clientRecordId = clientRecordId,
                    clientRecordVersion = targetVersion
                )

                // 1. Exercise Session Record with nested ExerciseSegments and versioned notes payload
                val sessionRecord = try {
                    ExerciseSessionRecord(
                        startTime = workout.startTime,
                        startZoneOffset = zoneOffset,
                        endTime = effectiveEndTime,
                        endZoneOffset = zoneOffset,
                        exerciseType = workout.toHealthConnectExerciseType(),
                        title = workout.title,
                        notes = noteText,
                        segments = segments,
                        metadata = sessionMetadata
                    )
                } catch (e: IllegalArgumentException) {
                    // Fallback to universal OTHER_WORKOUT if any segment conflict occurs
                    ExerciseSessionRecord(
                        startTime = workout.startTime,
                        startZoneOffset = zoneOffset,
                        endTime = effectiveEndTime,
                        endZoneOffset = zoneOffset,
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
                        title = workout.title,
                        notes = noteText,
                        segments = segments,
                        metadata = sessionMetadata
                    )
                }
                currentBatch.add(sessionRecord)

                // 2. Active Calories Burned Record
                if (workout.caloriesBurned > 0) {
                    val caloriesRecord = ActiveCaloriesBurnedRecord(
                        startTime = workout.startTime,
                        startZoneOffset = zoneOffset,
                        endTime = effectiveEndTime,
                        endZoneOffset = zoneOffset,
                        energy = Energy.kilocalories(workout.caloriesBurned),
                        metadata = Metadata(
                            clientRecordId = "${clientRecordId}_cal",
                            clientRecordVersion = targetVersion
                        )
                    )
                    currentBatch.add(caloriesRecord)
                }

                workoutsInBatch++
                workout.isSynced = true
                if (existing != null) {
                    updatedCount++
                }

                // Flush batch when reaching 50 records to prevent Binder overflow
                if (currentBatch.size >= 50) {
                    try {
                        healthConnectClient.insertRecords(currentBatch)
                        syncedCount += workoutsInBatch
                    } catch (e: Exception) {
                        Log.w(TAG, "Batch insert encountered error, inserting individually", e)
                        for (rec in currentBatch) {
                            try {
                                healthConnectClient.insertRecords(listOf(rec))
                                if (rec is ExerciseSessionRecord) syncedCount++
                            } catch (singleE: Exception) {
                                Log.e(TAG, "Single record insert failed: ${singleE.message}")
                                if (rec is ExerciseSessionRecord) {
                                    errorCount++
                                    lastError = singleE.localizedMessage
                                }
                            }
                        }
                    }
                    currentBatch.clear()
                    workoutsInBatch = 0
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed creating or inserting batch into Health Connect", e)
                errorCount++
                lastError = e.localizedMessage
                currentBatch.clear()
                workoutsInBatch = 0
            }
        }

        // Flush remaining records
        if (currentBatch.isNotEmpty()) {
            try {
                healthConnectClient.insertRecords(currentBatch)
                syncedCount += workoutsInBatch
                currentBatch.clear()
            } catch (e: Exception) {
                Log.w(TAG, "Final batch insert encountered error, inserting individually", e)
                for (rec in currentBatch) {
                    try {
                        healthConnectClient.insertRecords(listOf(rec))
                        if (rec is ExerciseSessionRecord) syncedCount++
                    } catch (singleE: Exception) {
                        Log.e(TAG, "Single record insert failed: ${singleE.message}")
                        if (rec is ExerciseSessionRecord) {
                            errorCount++
                            lastError = singleE.localizedMessage
                        }
                    }
                }
                currentBatch.clear()
            }
        }

        SyncResult(
            totalWorkouts = workouts.size,
            newlySynced = syncedCount,
            alreadySynced = alreadyCount,
            failedCount = errorCount,
            errorMessage = lastError,
            updatedCount = updatedCount
        )
    }

    private fun buildExerciseSegments(workout: FitifyWorkout): SegmentBuildResult {
        if (workout.exercises.isEmpty()) return SegmentBuildResult(emptyList(), emptyList())

        val segments = mutableListOf<ExerciseSegment>()
        val noteItems = mutableListOf<ExerciseSegmentNoteItem>()
        var cursor = workout.startTime

        for (ex in workout.exercises) {
            // 1. Rest / Transition period (get_ready_duration)
            if (ex.restDurationSeconds > 0) {
                val restEnd = cursor.plusSeconds(ex.restDurationSeconds)
                segments.add(
                    ExerciseSegment(
                        startTime = cursor,
                        endTime = restEnd,
                        segmentType = ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST,
                        repetitions = 0
                    )
                )
                cursor = restEnd
            }

            // 2. Active exercise period
            val exDuration = if (ex.durationSeconds > 0) {
                ex.durationSeconds
            } else if (ex.reps > 0) {
                (ex.reps * 3L).coerceAtLeast(15L)
            } else {
                30L
            }

            val exEnd = cursor.plusSeconds(exDuration)
            val activeSegmentIndex = segments.size
            segments.add(
                ExerciseSegment(
                    startTime = cursor,
                    endTime = exEnd,
                    segmentType = mapSegmentType(ex.code, ex.exerciseName),
                    repetitions = ex.reps
                )
            )
            cursor = exEnd

            if (ex.code.isNotBlank() || ex.exerciseName.isNotBlank()) {
                noteItems.add(
                    ExerciseSegmentNoteItem(
                        segmentIndex = activeSegmentIndex,
                        code = ex.code,
                        name = ex.exerciseName,
                        timingKind = "reconstructed"
                    )
                )
            }
        }
        return SegmentBuildResult(segments, noteItems)
    }

    private fun mapSegmentType(code: String, name: String): Int {
        val lower = (code + " " + name).lowercase()
        return when {
            lower.contains("rest") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST
            lower.contains("pause") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE
            lower.contains("stretch") || lower.contains("neck") || lower.contains("hamstring") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_STRETCHING
            lower.contains("plank") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK
            lower.contains("squat") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT
            lower.contains("lunge") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE
            lower.contains("crunch") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH
            lower.contains("situp") || lower.contains("sit_up") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_SIT_UP
            lower.contains("burpee") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BURPEE
            lower.contains("jump_rope") || lower.contains("skipping") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMP_ROPE
            lower.contains("jumping_jack") || lower.contains("jump") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMPING_JACK
            lower.contains("mountain_climber") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_MOUNTAIN_CLIMBER
            lower.contains("pullup") || lower.contains("pull_up") || lower.contains("chin_up") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP
            lower.contains("bench_press") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS
            lower.contains("pushup") || lower.contains("push_up") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING
            lower.contains("curl") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL
            lower.contains("shoulder_press") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS
            lower.contains("deadlift") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT
            lower.contains("hip_thrust") || lower.contains("bridge") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST
            lower.contains("leg_raise") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_RAISE
            lower.contains("kettlebell") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_KETTLEBELL_SWING
            lower.contains("yoga") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_YOGA
            lower.contains("pilates") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PILATES
            lower.contains("run") || lower.contains("sprint") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING
            lower.contains("walk") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_WALKING
            lower.contains("hiit") || lower.contains("interval") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
            else -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT
        }
    }
}
