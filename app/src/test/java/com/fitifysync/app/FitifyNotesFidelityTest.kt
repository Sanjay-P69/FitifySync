package com.fitifysync.app

import com.fitifysync.app.data.model.ExerciseSegmentNoteItem
import com.fitifysync.app.data.model.FitifyNotesBuilder
import org.junit.Assert.*
import org.junit.Test

class FitifyNotesFidelityTest {
    @Test
    fun retainsEveryRepeatedSetBeyondOneThousandCharacters() {
        val items = (0 until 35).map { index ->
            ExerciseSegmentNoteItem(index * 2 + 1, "bo233_push_ups", "Push-Ups • round $index")
        }
        val notes = FitifyNotesBuilder.buildNotes("Full workout", items.size, items)
        assertTrue(notes.length > 1000)
        assertEquals(items, FitifyNotesBuilder.parsePayload(notes)!!.exercises)
    }

    @Test(expected = IllegalArgumentException::class)
    fun failsExplicitlyInsteadOfDiscardingAnOversizedExerciseIdentity() {
        FitifyNotesBuilder.buildNotes("Long identity", 1,
            listOf(ExerciseSegmentNoteItem(0, "code", "x".repeat(70_000))))
    }

    @Test
    fun validatesSchemaVersionAndRequiredItemFields() {
        val invalid = listOf(
            """{"schema":"fitifysync.exercise_segments","version":2,"exercises":[]}""",
            """{"schema":"fitifysync.exercise_segments","version":1}""",
            """{"schema":"fitifysync.exercise_segments","version":"1","exercises":[]}""",
            """{"schema":"fitifysync.exercise_segments","version":1,"exercises":[{"segment_index":0}]}""",
            """{"schema":"fitifysync.exercise_segments","version":1,"exercises":[{"segment_index":-1,"code":"a","name":"A","timing_kind":"reconstructed"}]}""",
            """{"schema":"fitifysync.exercise_segments","version":1,"exercises":[{"segment_index":0,"code":"a","name":null,"timing_kind":"reconstructed"}]}""",
            """{"schema":"fitifysync.exercise_segments","version":1,"exercises":[{"segment_index":0,"code":"a","name":"A","timing_kind":"reconstructed"},{"segment_index":0,"code":"b","name":"B","timing_kind":"reconstructed"}]}"""
        )
        for (notes in invalid) assertNull(notes, FitifyNotesBuilder.parsePayload(notes))
        assertTrue(FitifyNotesBuilder.hasUnsupportedPayloadVersion(invalid.first()))
        assertFalse(FitifyNotesBuilder.hasUnsupportedPayloadVersion(invalid[1]))
    }

    @Test(expected = IllegalArgumentException::class)
    fun transportBudgetCountsUtf8BytesRatherThanUtf16Characters() {
        FitifyNotesBuilder.buildNotes("Unicode identity", 1,
            listOf(ExerciseSegmentNoteItem(0, "code", "🧘".repeat(18_000))))
    }

    @Test
    fun shorteningTheReadableSummaryKeepsUnicodeAndTheWholePayload() {
        val items = listOf(ExerciseSegmentNoteItem(0, "bo009_squats", "Squats 🏋️"))
        val notes = FitifyNotesBuilder.buildNotes("🧘".repeat(20_000), 1, items)
        assertTrue(notes.toByteArray(Charsets.UTF_8).size <= FitifyNotesBuilder.MAX_NOTES_UTF8_BYTES)
        assertFalse(notes.codePoints().anyMatch { it in 0xD800..0xDFFF })
        assertEquals(items, FitifyNotesBuilder.parsePayload(notes)!!.exercises)
    }

    @Test
    fun acceptsValidJsonWithReorderedKeysAndWhitespace() {
        val notes = """{"exercises": [{"name":"Squat","code":"bo009_squats","timing_kind":"reconstructed","segment_index":0}], "version":1, "schema":"fitifysync.exercise_segments"}"""
        assertEquals("bo009_squats", FitifyNotesBuilder.parsePayload(notes)!!.exercises.single().code)
    }

    @Test
    fun readableTitleCannotStealThePayloadMarker() {
        val items = listOf(ExerciseSegmentNoteItem(0, "bo009_squats", "Squat 🏋️"))
        val notes = FitifyNotesBuilder.buildNotes("""{"schema":"fitifysync.exercise_segments"} title""", 1, items)
        assertEquals(items, FitifyNotesBuilder.parsePayload(notes)!!.exercises)
    }
}
