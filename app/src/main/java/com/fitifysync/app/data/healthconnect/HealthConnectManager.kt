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
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import com.fitifysync.app.data.model.ExerciseSegmentNoteItem
import com.fitifysync.app.data.model.FitifyNotesBuilder
import com.fitifysync.app.data.model.FitifyWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneOffset

data class SyncResult(
    val totalWorkouts: Int,
    val newlySynced: Int,
    val alreadySynced: Int,
    val failedCount: Int,
    val errorMessage: String? = null,
    val updatedCount: Int = 0
) {
    val summaryText: String
        get() = "Health Connect: $newlySynced new, $updatedCount updated, $alreadySynced already current, $failedCount failed."
}

data class ExistingSessionStatus(
    val session: ExerciseSessionRecord?,
    val calories: ActiveCaloriesBurnedRecord? = null
) {
    val clientRecordVersion: Long
        get() = maxOf(session?.metadata?.clientRecordVersion ?: 0L, calories?.metadata?.clientRecordVersion ?: 0L)

    fun nextClientRecordVersion(): Long {
        require(clientRecordVersion != Long.MAX_VALUE) { "Health Connect record version cannot be increased." }
        return clientRecordVersion + 1L
    }

    fun matches(expected: ExerciseSessionRecord, expectedCalories: ActiveCaloriesBurnedRecord?): Boolean {
        val session = session ?: return false
        val payload = FitifyNotesBuilder.parsePayload(session.notes) ?: return false
        if (payload != FitifyNotesBuilder.parsePayload(expected.notes)) return false
        if (session.startTime != expected.startTime || session.endTime != expected.endTime ||
            session.startZoneOffset != expected.startZoneOffset || session.endZoneOffset != expected.endZoneOffset ||
            session.title != expected.title || session.exerciseType != expected.exerciseType || session.segments != expected.segments) return false
        if (expectedCalories == null) return true
        val existingCalories = calories ?: return false
        return existingCalories.startTime == expectedCalories.startTime && existingCalories.endTime == expectedCalories.endTime &&
            existingCalories.energy == expectedCalories.energy && existingCalories.startZoneOffset == expectedCalories.startZoneOffset &&
            existingCalories.endZoneOffset == expectedCalories.endZoneOffset
    }
}
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
        } catch (cancelled: CancellationException) {
            throw cancelled
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
        val filter = TimeRangeFilter.after(workouts.minOf { it.startTime })
        val origin = setOf(DataOrigin(context.packageName))
        val existing = mutableMapOf<String, ExistingSessionStatus>()
        var pageToken: String? = null
        do {
            val response = healthConnectClient.readRecords(ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = filter,
                dataOriginFilter = origin,
                pageToken = pageToken
            ))
            for (record in response.records) {
                if (record.metadata.dataOrigin.packageName != context.packageName) continue
                val id = record.metadata.clientRecordId ?: continue
                existing[id] = ExistingSessionStatus(record)
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        do {
            val response = healthConnectClient.readRecords(ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = filter,
                dataOriginFilter = origin,
                pageToken = pageToken
            ))
            for (record in response.records) {
                if (record.metadata.dataOrigin.packageName != context.packageName) continue
                val id = record.metadata.clientRecordId ?: continue
                if (!id.endsWith("_cal")) continue
                val sessionId = id.removeSuffix("_cal")
                existing[sessionId] = existing[sessionId]?.copy(calories = record) ?: ExistingSessionStatus(null, record)
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        existing
    }
    /**
     * Retrieves previously synced client record IDs from Health Connect with pagination to prevent duplicates.
     */
    suspend fun getExistingClientRecordIds(workouts: List<FitifyWorkout>): Set<String> = withContext(Dispatchers.IO) {
        getExistingSessions(workouts).filterValues { it.session != null }.keys
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Could not query heart rate for range $startTime to $endTime", e)
            null
        }
    }

    /**
     * Inserts extracted Fitify workouts into Health Connect with full ExerciseSegment breakdowns.
     */
    suspend fun syncWorkouts(workouts: List<FitifyWorkout>, wipePreviousFirst: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            if (!isAvailable()) return@withContext SyncResult(workouts.size, 0, 0, workouts.size, "Health Connect is not available on this device.")
            if (!hasAllPermissions()) return@withContext SyncResult(workouts.size, 0, 0, workouts.size, "Health Connect permissions have not been granted.")
            if (wipePreviousFirst && !deleteAllSyncedRecords()) {
                workouts.forEach { it.isSynced = false }
                return@withContext SyncResult(workouts.size, 0, 0, workouts.size, "Could not clear previous FitifySync records; sync was not started.")
            }
            val existingSessions = try {
                if (wipePreviousFirst) emptyMap() else getExistingSessions(workouts)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                workouts.forEach { it.isSynced = false }
                Log.w(TAG, "Could not verify existing FitifySync records", error)
                return@withContext SyncResult(workouts.size, 0, 0, workouts.size, "Could not verify existing Health Connect records; no records were written.")
            }
            val batch = SyncWriteBatch<Record>({ healthConnectClient.insertRecords(it) })
            for (workout in workouts) {
                val existing = existingSessions["fitify_${workout.id}"]
                batch.enqueue(existing?.session != null, { workout.isSynced = it }) {
                    require((existing?.session?.notes?.toByteArray(Charsets.UTF_8)?.size ?: 0) <= FitifyNotesBuilder.MAX_NOTES_UTF8_BYTES) {
                        "Existing notes exceed FitifySync's reader budget; this workout was not overwritten."
                    }
                    require(!FitifyNotesBuilder.hasUnsupportedPayloadVersion(existing?.session?.notes)) {
                        "This workout has a newer FitifySync notes schema; it was not overwritten."
                    }
                    val targetVersion = existing?.nextClientRecordVersion() ?: 1L
                    val prepared = prepareWorkoutRecords(workout, targetVersion)
                    val session = prepared.records.first() as ExerciseSessionRecord
                    val calories = prepared.records.filterIsInstance<ActiveCaloriesBurnedRecord>().singleOrNull()
                    if (existing?.matches(session, calories) == true) null else prepared
                }
            }
            batch.flush()
            SyncResult(
                totalWorkouts = workouts.size,
                newlySynced = batch.newlySynced,
                alreadySynced = batch.alreadySynced,
                failedCount = batch.failedCount,
                errorMessage = batch.errorMessage,
                updatedCount = batch.updatedCount
            )
        }
    }

    private fun prepareWorkoutRecords(workout: FitifyWorkout, version: Long): PreparedSyncRecords<Record> {
        val clientRecordId = "fitify_${workout.id}"
        val result = buildExerciseSegments(workout)
        val notes = FitifyNotesBuilder.buildNotes(workout.title, workout.exercises.size, result.noteItems)
        val lastSegmentEnd = result.segments.lastOrNull()?.endTime ?: workout.endTime
        val effectiveEndTime = maxOf(workout.endTime, lastSegmentEnd).let {
            if (it.isAfter(workout.startTime)) it else workout.startTime.plusSeconds(60L)
        }
        val rules = ZoneOffset.systemDefault().rules
        val startOffset = rules.getOffset(workout.startTime)
        val endOffset = rules.getOffset(effectiveEndTime)
        val metadata = Metadata(clientRecordId = clientRecordId, clientRecordVersion = version)
        fun session(type: Int) = ExerciseSessionRecord(
            startTime = workout.startTime,
            startZoneOffset = startOffset,
            endTime = effectiveEndTime,
            endZoneOffset = endOffset,
            exerciseType = type,
            title = workout.title,
            notes = notes,
            segments = result.segments,
            metadata = metadata
        )
        val session = try { session(workout.toHealthConnectExerciseType()) } catch (_: IllegalArgumentException) {
            session(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT)
        }
        val records = mutableListOf<Record>(session)
        if (workout.caloriesBurned > 0) records.add(ActiveCaloriesBurnedRecord(
            startTime = workout.startTime,
            startZoneOffset = startOffset,
            endTime = effectiveEndTime,
            endZoneOffset = endOffset,
            energy = Energy.kilocalories(workout.caloriesBurned),
            metadata = Metadata(clientRecordId = "${clientRecordId}_cal", clientRecordVersion = version)
        ))
        val estimatedBytes = notes.length.toLong() * 2 + workout.title.length.toLong() * 2 + result.segments.size.toLong() * 128 + 4096
        require(estimatedBytes <= Int.MAX_VALUE) { "Workout metadata exceeds FitifySync's transport budget." }
        return PreparedSyncRecords(records, estimatedBytes.toInt())
    }
    companion object {
        private val syncMutex = Mutex()
        internal fun buildExerciseSegments(workout: FitifyWorkout): SegmentBuildResult {
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

                noteItems.add(
                        ExerciseSegmentNoteItem(
                            segmentIndex = activeSegmentIndex,
                            code = ex.code,
                            name = ex.exerciseName,
                            timingKind = "reconstructed"
                        )
                    )
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
}
