package com.fitifysync.app.data.model

import androidx.health.connect.client.records.ExerciseSessionRecord
import java.time.Instant

/**
 * Represents an individual exercise completed inside a workout session.
 */
data class FitifyExerciseDetail(
    val exerciseName: String,
    val code: String = "",
    val sets: Int = 1,
    val reps: Int = 0,
    val durationSeconds: Long = 0,
    val restDurationSeconds: Long = 0,
    val weightKg: Double = 0.0
)

/**
 * Represents a parsed training session from the Fitify local SQLite database.
 */
data class FitifyWorkout(
    val id: String,
    val title: String,
    val workoutType: String,
    val startTime: Instant,
    val endTime: Instant,
    val durationSeconds: Long,
    val caloriesBurned: Double,
    val exercises: List<FitifyExerciseDetail> = emptyList(),
    val rawNotes: String? = null,
    var isSynced: Boolean = false,
    var avgHeartRate: Int? = null,
    var minHeartRate: Int? = null,
    var maxHeartRate: Int? = null,
    val caloriesKnown: Boolean = true
) {
    val hasKnownCalories: Boolean get() = caloriesKnown && caloriesBurned.isFinite() && caloriesBurned >= 0.0
    /**
     * Maps Fitify training categories to universal Health Connect ExerciseSessionRecord types
     * to guarantee compatibility with all nested ExerciseSegment types.
     */
    fun toHealthConnectExerciseType(): Int {
        val lower = workoutType.lowercase()
        val titleLower = title.lowercase()
        return when {
            lower.contains("hiit") || titleLower.contains("hiit") || lower.contains("interval") -> 
                ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
            lower.contains("bootcamp") || lower.contains("camp") -> 
                ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP
            else -> 
                ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        }
    }
}
