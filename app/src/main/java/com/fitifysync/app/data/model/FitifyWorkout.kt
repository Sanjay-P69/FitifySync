package com.fitifysync.app.data.model

import java.time.Instant

/**
 * Represents an individual exercise completed inside a workout session.
 */
data class FitifyExerciseDetail(
    val exerciseName: String,
    val sets: Int = 1,
    val reps: Int = 0,
    val durationSeconds: Long = 0,
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
    var isSynced: Boolean = false
) {
    /**
     * Maps Fitify training categories to Health Connect ExerciseSessionRecord types.
     * Health Connect supports:
     * - EXERCISE_TYPE_STRENGTH_TRAINING
     * - EXERCISE_TYPE_CALISTHENICS
     * - EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
     * - EXERCISE_TYPE_STRETCHING
     * - EXERCISE_TYPE_OTHER_WORKOUT
     */
    fun toHealthConnectExerciseType(): Int {
        val lower = workoutType.lowercase()
        val titleLower = title.lowercase()
        return when {
            lower.contains("hiit") || titleLower.contains("hiit") || lower.contains("interval") -> 27 // EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
            lower.contains("calisthenic") || lower.contains("bodyweight") -> 10 // EXERCISE_TYPE_CALISTHENICS
            lower.contains("stretch") || lower.contains("warmup") || lower.contains("cool") -> 68 // EXERCISE_TYPE_STRETCHING
            lower.contains("strength") || lower.contains("weight") || lower.contains("muscle") -> 69 // EXERCISE_TYPE_STRENGTH_TRAINING
            lower.contains("cardio") -> 8 // EXERCISE_TYPE_BOOT_CAMP or AEROBICS
            else -> 0 // EXERCISE_TYPE_OTHER_WORKOUT
        }
    }
}
