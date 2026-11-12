package com.fitifysync.app

import com.fitifysync.app.data.model.ExerciseSegmentNoteItem
import com.fitifysync.app.data.model.FitifyNotesBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitifyNotesPayloadTest {

    @Test
    fun testBuildNotesStandardPayload() {
        val items = listOf(
            ExerciseSegmentNoteItem(
                segmentIndex = 1,
                code = "push_up",
                name = "Push Up",
                timingKind = "reconstructed"
            ),
            ExerciseSegmentNoteItem(
                segmentIndex = 3,
                code = "bw012_jumping_jacks",
                name = "Jumping Jacks",
                timingKind = "reconstructed"
            )
        )

        val notes = FitifyNotesBuilder.buildNotes(
            readableTitle = "Morning Routine",
            exerciseCount = 2,
            items = items
        )

        assertTrue(notes.startsWith("Fitify Morning Routine | Exercises: 2\n\n"))
        assertTrue(notes.length <= FitifyNotesBuilder.MAX_NOTE_LENGTH)

        val parsed = FitifyNotesBuilder.parsePayload(notes)
        assertNotNull("Payload should be parsed from notes", parsed)
        assertEquals("fitifysync.exercise_segments", parsed!!.schema)
        assertEquals(1, parsed.version)
        assertEquals(2, parsed.exercises.size)

        assertEquals(1, parsed.exercises[0].segmentIndex)
        assertEquals("push_up", parsed.exercises[0].code)
        assertEquals("Push Up", parsed.exercises[0].name)
        assertEquals("reconstructed", parsed.exercises[0].timingKind)

        assertEquals(3, parsed.exercises[1].segmentIndex)
        assertEquals("bw012_jumping_jacks", parsed.exercises[1].code)
        assertEquals("Jumping Jacks", parsed.exercises[1].name)
    }

    @Test
    fun testBuildNotesEmptyExercises() {
        val notes = FitifyNotesBuilder.buildNotes(
            readableTitle = "Empty Workout",
            exerciseCount = 0,
            items = emptyList()
        )
        assertEquals("Fitify Empty Workout | Exercises: 0", notes)
        assertNull(FitifyNotesBuilder.parsePayload(notes))
    }

    @Test
    fun testBuildNotesOversizedSafety() {
        // Create 35 exercise items that would easily exceed 1000 characters
        val largeItems = (0 until 35).map { idx ->
            ExerciseSegmentNoteItem(
                segmentIndex = idx * 2 + 1,
                code = "exercise_code_long_identifier_variant_$idx",
                name = "Very Descriptive Exercise Name With Variants Number $idx",
                timingKind = "reconstructed"
            )
        }

        val notes = FitifyNotesBuilder.buildNotes(
            readableTitle = "Extreme Endurance Workout",
            exerciseCount = 35,
            items = largeItems
        )

        assertTrue("Note must never exceed 1000 chars", notes.length <= FitifyNotesBuilder.MAX_NOTE_LENGTH)
        val parsed = FitifyNotesBuilder.parsePayload(notes)
        assertNotNull("Should still contain a valid parseable JSON payload", parsed)
        assertEquals("fitifysync.exercise_segments", parsed!!.schema)
        assertTrue("Should have trimmed items to fit within limit", parsed.exercises.isNotEmpty())
        assertTrue("Trimmed count should be less than 35", parsed.exercises.size < 35)
    }

    @Test
    fun testSpecialCharactersEscaped() {
        val items = listOf(
            ExerciseSegmentNoteItem(
                segmentIndex = 0,
                code = "custom_\"quote\"_and_\\slash",
                name = "Dumbbell Press (30° incline) & \"Hammer\" Curls",
                timingKind = "reconstructed"
            )
        )

        val notes = FitifyNotesBuilder.buildNotes(
            readableTitle = "Upper Body & Arms",
            exerciseCount = 1,
            items = items
        )

        assertTrue(notes.length <= FitifyNotesBuilder.MAX_NOTE_LENGTH)
        val parsed = FitifyNotesBuilder.parsePayload(notes)
        assertNotNull(parsed)
        assertEquals("custom_\"quote\"_and_\\slash", parsed!!.exercises[0].code)
        assertEquals("Dumbbell Press (30° incline) & \"Hammer\" Curls", parsed.exercises[0].name)
    }
}
