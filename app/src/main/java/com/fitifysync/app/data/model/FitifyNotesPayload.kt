package com.fitifysync.app.data.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import com.google.gson.annotations.SerializedName

/**
 * Metadata for an active exercise segment exported to Health Connect.
 *
 * @property segmentIndex 0-based index in the session's exported [androidx.health.connect.client.records.ExerciseSegment] list.
 * @property code Original Fitify exercise code (e.g. "push_up", "bo091_front_neck_stretch").
 * @property name Exact parser label: catalogue title when available, otherwise a code-derived label.
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

    // Application transport budget, not a documented Health Connect notes limit.
    const val MAX_NOTES_UTF8_BYTES = 64 * 1024
    private val gson = Gson()

    fun buildNotes(readableTitle: String, exerciseCount: Int, items: List<ExerciseSegmentNoteItem>): String {
        validateItems(items)
        val json = gson.toJson(FitifySyncNotesPayload(exercises = items))
        val jsonBytes = json.toByteArray(Charsets.UTF_8).size
        require(jsonBytes <= MAX_NOTES_UTF8_BYTES) {
            "The complete exercise identity payload exceeds FitifySync's transport budget. No exercises were discarded."
        }
        val summary = "Fitify $readableTitle | Exercises: $exerciseCount"
        val combined = "$summary\n\n$json"
        if (combined.toByteArray(Charsets.UTF_8).size <= MAX_NOTES_UTF8_BYTES) return combined
        val available = MAX_NOTES_UTF8_BYTES - jsonBytes - 2
        if (available < 15) return json
        return "${prefixWithinBytes(summary, available - 3)}...\n\n$json"
    }

    fun parsePayload(notes: String?): FitifySyncNotesPayload? {
        val json = payloadJson(notes) ?: return null
        return try {
            JsonReader(StringReader(json)).use { reader ->
                reader.isLenient = false
                reader.beginObject()
                val fields = mutableSetOf<String>()
                var schema: String? = null
                var version: Int? = null
                var exercises: List<ExerciseSegmentNoteItem>? = null
                while (reader.hasNext()) {
                    val field = reader.nextName()
                    require(fields.add(field)) { "Duplicate payload field" }
                    when (field) {
                        "schema" -> schema = reader.stringValue()
                        "version" -> version = reader.integerValue()
                        "exercises" -> exercises = readItems(reader)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                require(reader.peek() == JsonToken.END_DOCUMENT)
                require(schema == SCHEMA_ID && version == SCHEMA_VERSION && exercises != null)
                validateItems(exercises)
                FitifySyncNotesPayload(exercises = exercises)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun hasUnsupportedPayloadVersion(notes: String?): Boolean {
        val json = payloadJson(notes) ?: return false
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val version = root.get("version")
            root.get("schema")?.asString == SCHEMA_ID && version?.isJsonPrimitive == true &&
                version.asJsonPrimitive.isNumber && (version.asString.toIntOrNull() ?: 0) > SCHEMA_VERSION
        } catch (_: Exception) {
            false
        }
    }

    private fun payloadJson(notes: String?): String? {
        if (notes.isNullOrBlank() || notes.toByteArray(Charsets.UTF_8).size > MAX_NOTES_UTF8_BYTES) return null
        val text = notes.trim()
        val candidate = if (text.startsWith("{")) text else text.substringAfterLast("\n\n").trim()
        return candidate.takeIf { it.startsWith("{") }
    }

    private fun readItems(reader: JsonReader): List<ExerciseSegmentNoteItem> {
        val items = mutableListOf<ExerciseSegmentNoteItem>()
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            val fields = mutableSetOf<String>()
            var index: Int? = null
            var code: String? = null
            var name: String? = null
            var timing: String? = null
            while (reader.hasNext()) {
                val field = reader.nextName()
                require(fields.add(field)) { "Duplicate segment field" }
                when (field) {
                    "segment_index" -> index = reader.integerValue()
                    "code" -> code = reader.stringValue()
                    "name" -> name = reader.stringValue()
                    "timing_kind" -> timing = reader.stringValue()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            require(index != null && code != null && name != null && timing != null)
            items.add(ExerciseSegmentNoteItem(index, code, name, timing))
        }
        reader.endArray()
        return items
    }

    private fun JsonReader.stringValue(): String {
        require(peek() == JsonToken.STRING)
        return nextString()
    }

    private fun JsonReader.integerValue(): Int {
        require(peek() == JsonToken.NUMBER)
        return nextString().toInt()
    }

    private fun validateItems(items: List<ExerciseSegmentNoteItem>) {
        val indices = mutableSetOf<Int>()
        require(items.all { it.segmentIndex >= 0 && indices.add(it.segmentIndex) && it.timingKind == "reconstructed" }) {
            "Exercise segment identities require unique nonnegative indices and reconstructed timing."
        }
    }

    private fun prefixWithinBytes(text: String, maxBytes: Int): String {
        var end = 0
        var bytes = 0
        while (end < text.length) {
            val point = text.codePointAt(end)
            val width = when { point <= 0x7f -> 1; point <= 0x7ff -> 2; point <= 0xffff -> 3; else -> 4 }
            if (bytes + width > maxBytes) break
            bytes += width
            end += Character.charCount(point)
        }
        return text.substring(0, end)
    }
}
