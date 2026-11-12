package com.fitifysync.app

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import com.fitifysync.app.data.healthconnect.ExistingSessionStatus
import com.fitifysync.app.data.model.ExerciseSegmentNoteItem
import com.fitifysync.app.data.model.FitifyNotesBuilder
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ExistingSessionStatusTest {
    private val start = Instant.parse("2026-11-12T10:00:00Z")
    private val items = listOf(ExerciseSegmentNoteItem(1, "bo233_push_ups", "Push-Ups"))
    private val notes = FitifyNotesBuilder.buildNotes("Workout", 1, items)

    private fun session(text: String = notes, reps: Int = 12, version: Long = 1) = ExerciseSessionRecord(
        startTime = start, startZoneOffset = ZoneOffset.UTC,
        endTime = start.plusSeconds(60), endZoneOffset = ZoneOffset.UTC,
        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
        title = "Workout", notes = text,
        metadata = Metadata(clientRecordId = "fitify_example", clientRecordVersion = version),
        segments = listOf(
            ExerciseSegment(start, start.plusSeconds(10), ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST, 0),
            ExerciseSegment(start.plusSeconds(10), start.plusSeconds(60), ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING, reps)
        )
    )

    private fun calories(amount: Double, version: Long = 1) = ActiveCaloriesBurnedRecord(
        startTime = start, startZoneOffset = ZoneOffset.UTC,
        endTime = start.plusSeconds(60), endZoneOffset = ZoneOffset.UTC,
        energy = Energy.kilocalories(amount),
        metadata = Metadata(clientRecordId = "fitify_example_cal", clientRecordVersion = version)
    )

    @Test
    fun onlyCompleteEquivalentSessionsAreAlreadySynced() {
        val expected = session()
        assertTrue(ExistingSessionStatus(session(version = 8)).matches(expected, null))
        assertFalse(ExistingSessionStatus(session("Malformed fitifysync.exercise_segments")).matches(expected, null))
        assertFalse(ExistingSessionStatus(session(FitifyNotesBuilder.buildNotes("Workout", 1, emptyList()))).matches(expected, null))
        assertFalse(ExistingSessionStatus(session(reps = 8)).matches(expected, null))
    }

    @Test
    fun companionCaloriesMustExistAndMatchAndVersionsRemainMonotonic() {
        val expected = session()
        assertFalse(ExistingSessionStatus(session()).matches(expected, calories(100.0)))
        assertFalse(ExistingSessionStatus(session(), calories(50.0)).matches(expected, calories(100.0)))
        val current = ExistingSessionStatus(session(version = 2), calories(100.0, version = 7))
        assertTrue(current.matches(expected, calories(100.0)))
        assertEquals(7L, current.clientRecordVersion)
    }

    @Test
    fun orphanCaloriesKeepTheirVersionWhenTheMissingSessionIsRecreated() {
        val orphan = ExistingSessionStatus(session = null, calories = calories(100.0, version = 5))
        assertFalse(orphan.matches(session(), calories(100.0)))
        assertEquals(6L, orphan.nextClientRecordVersion())
    }

    @Test(expected = IllegalArgumentException::class)
    fun exhaustedRecordVersionCannotWrapAround() {
        ExistingSessionStatus(session(version = Long.MAX_VALUE)).nextClientRecordVersion()
    }
}
