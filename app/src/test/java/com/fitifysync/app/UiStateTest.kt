package com.fitifysync.app

import com.fitifysync.app.data.model.FitifyExerciseDetail
import com.fitifysync.app.data.model.FitifyWorkout
import com.fitifysync.app.ui.state.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UiStateTest {

    @Test
    fun testCanSyncCondition() {
        val stateInitial = UiState()
        assertFalse("Cannot sync initially", stateInitial.canSync)

        val stateRootOnly = stateInitial.copy(isRootGranted = true, hasHealthPermissions = false)
        assertFalse("Cannot sync without Health Connect permissions", stateRootOnly.canSync)

        val stateReady = stateInitial.copy(isRootGranted = true, hasHealthPermissions = true, isSyncing = false)
        assertTrue("Can sync when root granted and permissions given", stateReady.canSync)

        val stateSyncing = stateReady.copy(isSyncing = true)
        assertFalse("Cannot trigger sync when already syncing", stateSyncing.canSync)
    }

    @Test
    fun testFitifyWorkoutDataIntegrity() {
        val now = Instant.now()
        val workout = FitifyWorkout(
            id = "test-id-123",
            title = "HIIT Morning Burn",
            workoutType = "workout_hiit",
            startTime = now.minusSeconds(1200),
            endTime = now,
            durationSeconds = 1200,
            caloriesBurned = 185.0,
            exercises = listOf(
                FitifyExerciseDetail(
                    exerciseName = "Plank Hold",
                    durationSeconds = 60,
                    reps = 0,
                    restDurationSeconds = 15
                ),
                FitifyExerciseDetail(
                    exerciseName = "Push Ups",
                    durationSeconds = 0,
                    reps = 20,
                    restDurationSeconds = 20
                )
            ),
            avgHeartRate = 138,
            minHeartRate = 110,
            maxHeartRate = 165,
            isSynced = true
        )

        assertEquals("test-id-123", workout.id)
        assertEquals(2, workout.exercises.size)
        assertEquals(138, workout.avgHeartRate)
        assertTrue(workout.isSynced)
        assertEquals("Plank Hold", workout.exercises[0].exerciseName)
        assertEquals(60, workout.exercises[0].durationSeconds)
        assertEquals(20, workout.exercises[1].reps)
    }
}
