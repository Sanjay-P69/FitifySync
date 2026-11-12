package com.fitifysync.app.data.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Metadata for an active exercise segment exported to Health Connect.
 *
 * @property segmentIndex 0-based index in the session's exported [androidx.health.connect.client.records.ExerciseSegment] list.
 * @property code Original Fitify exercise code (e.g. "push_up", "bo091_front_neck_stretch").
 * @property name Clean display title of the movement.
 * @property timingKind Indicates timing provenance. "reconstructed" indicates boundaries are reconstructed from workout duration/reps.
 */
data class ExerciseSegmentNoteItem(
    @SerializedName("segment_index")
    val segmentIndex: Int,

    @SerializedName("code")
    val code: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("timing_kind")
    val timingKind: String = "reconstructed"
)

/**
 * Root JSON structure serialized into [androidx.health.connect.client.records.ExerciseSessionRecord.notes]
 * alongside readable text to allow downstream consumers (such as RepForge) to recover exact movements.
 */
data class FitifySyncNotesPayload(
    @SerializedName("schema")
    val schema: String = "fitifysync.exercise_segments",

    @SerializedName("version")
    val version: Int = 1,

    @SerializedName("exercises")
    val exercises: List<ExerciseSegmentNoteItem>
)

object FitifyNotesBuilder {
    const val SCHEMA_ID = "fitifysync.exercise_segments"
    const val SCHEMA_VERSION = 1

    /**
     * Maximum character length allowed for notes in Health Connect ExerciseSessionRecord.
     */
    const val MAX_NOTE_LENGTH = 1000

    private val gson = Gson()

    /**
     * Combines a human-readable summary with a compact, versioned JSON payload.
     * Guarantees total string length does not exceed [MAX_NOTE_LENGTH].
     */
    fun buildNotes(
        readableTitle: String,
        exerciseCount: Int,
        items: List<ExerciseSegmentNoteItem>
    ): String {
        val summary = "Fitify $readableTitle | Exercises: $exerciseCount"
        if (items.isEmpty()) {
            return summary.take(MAX_NOTE_LENGTH)
        }

        val payload = FitifySyncNotesPayload(
            schema = SCHEMA_ID,
            version = SCHEMA_VERSION,
            exercises = items
        )
        val json = gson.toJson(payload)
        val combined = "$summary\n\n$json"

        if (combined.length <= MAX_NOTE_LENGTH) {
            return combined
        }

        // If combined exceeds 1000, try trimming the summary header first
        if (json.length <= MAX_NOTE_LENGTH) {
            val available = MAX_NOTE_LENGTH - json.length - 2
            return if (available >= 15) {
                "${summary.take(available - 3)}...\n\n$json"
            } else {
                json
            }
        }

        // If JSON alone exceeds 1000 (many exercises), progressively trim items from the end
        // so downstream readers still receive valid, parseable JSON
        var trimmed = items
        while (trimmed.isNotEmpty()) {
            trimmed = trimmed.dropLast(1)
            val trimmedPayload = FitifySyncNotesPayload(
                schema = SCHEMA_ID,
                version = SCHEMA_VERSION,
                exercises = trimmed
            )
            val trimmedJson = gson.toJson(trimmedPayload)
            if (trimmedJson.length <= MAX_NOTE_LENGTH) {
                return trimmedJson
            }
        }

        return summary.take(MAX_NOTE_LENGTH)
    }

    /**
     * Parses the payload from a note string if present.
     */
    fun parsePayload(notes: String?): FitifySyncNotesPayload? {
        if (notes.isNullOrBlank()) return null
        val jsonStart = notes.indexOf("{\"schema\":\"$SCHEMA_ID\"")
        if (jsonStart == -1) return null
        val jsonStr = notes.substring(jsonStart).trim()
        return try {
            gson.fromJson(jsonStr, FitifySyncNotesPayload::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
