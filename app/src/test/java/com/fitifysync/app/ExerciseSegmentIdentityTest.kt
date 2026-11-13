package com.fitifysync.app

import com.fitifysync.app.data.healthconnect.HealthConnectManager
import com.fitifysync.app.data.model.FitifyExerciseDetail
import com.fitifysync.app.data.model.FitifyWorkout
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ExerciseSegmentIdentityTest {
    private val start = Instant.parse("2026-11-12T10:00:00Z")
    private fun workout(exercises: List<FitifyExerciseDetail>) = FitifyWorkout(
        "example", "Workout", "strength", start, start.plusSeconds(120), 120, 100.0, exercises
    )

    @Test
    fun repeatedSetsKeepTheirRepsAndIndicesAmongRestSegments() {
        val built = HealthConnectManager.buildExerciseSegments(workout(listOf(
            FitifyExerciseDetail("Push-Ups", "bo233_push_ups", reps = 8, durationSeconds = 20, restDurationSeconds = 10),
            FitifyExerciseDetail("Push-Ups", "bo233_push_ups", reps = 6, durationSeconds = 20, restDurationSeconds = 10)
        )))
        assertEquals(4, built.segments.size)
        assertEquals(listOf(1, 3), built.noteItems.map { it.segmentIndex })
        assertEquals(listOf(8, 6), built.noteItems.map { built.segments[it.segmentIndex].repetitions })
        assertEquals(start.plusSeconds(10), built.segments[1].startTime)
        assertEquals(start.plusSeconds(60), built.segments[3].endTime)
        assertTrue(built.noteItems.all { it.timingKind == "reconstructed" })
    }

    @Test
    fun unavailableSourceIdentityIsRetainedForDownstreamReview() {
        val built = HealthConnectManager.buildExerciseSegments(workout(listOf(FitifyExerciseDetail("", "", durationSeconds = 20))))
        assertEquals(1, built.noteItems.size)
        assertEquals(0, built.noteItems.single().segmentIndex)
        assertEquals("", built.noteItems.single().code)
        assertEquals("", built.noteItems.single().name)
    }
}
