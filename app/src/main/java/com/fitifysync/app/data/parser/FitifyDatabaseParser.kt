package com.fitifysync.app.data.parser

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.fitifysync.app.data.model.FitifyExerciseDetail
import com.fitifysync.app.data.model.FitifyWorkout
import java.io.File
import java.time.Instant
import java.time.format.DateTimeParseException

object FitifyDatabaseParser {
    private const val TAG = "FitifyDbParser"

    /**
     * Parses all recorded workouts from an extracted Fitify SQLite database.
     * Uses dynamic schema inspection to support multiple Fitify app versions.
     */
    fun parseWorkouts(dbFile: File): List<FitifyWorkout> {
        val workouts = mutableListOf<FitifyWorkout>()
        var db: SQLiteDatabase? = null

        try {
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            val tables = getTableNames(db)
            Log.d(TAG, "Found tables in Fitify DB: $tables")

            // Identify workout history table
            val sessionTable = findSessionTable(db, tables)
            if (sessionTable != null) {
                Log.i(TAG, "Using primary session table: $sessionTable")
                workouts.addAll(extractFromSessionTable(db, sessionTable))
            } else {
                Log.w(TAG, "No designated session table identified by heuristic. Scanning all candidate tables...")
                for (table in tables) {
                    if (isPotentialWorkoutTable(table)) {
                        val extracted = extractFromSessionTable(db, table)
                        if (extracted.isNotEmpty()) {
                            Log.i(TAG, "Extracted ${extracted.size} sessions from table: $table")
                            workouts.addAll(extracted)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening or reading Fitify SQLite database", e)
        } finally {
            try {
                db?.close()
            } catch (_: Exception) {}
        }

        // Sort by start time descending (newest first)
        return workouts.sortedByDescending { it.startTime }
    }

    private fun getTableNames(db: SQLiteDatabase): List<String> {
        val tableList = mutableListOf<String>()
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                tableList.add(it.getString(0))
            }
        }
        return tableList
    }

    private fun isPotentialWorkoutTable(tableName: String): Boolean {
        val lower = tableName.lowercase()
        return lower.contains("history") ||
                lower.contains("session") ||
                lower.contains("workout") ||
                lower.contains("log") ||
                lower.contains("completed") ||
                lower.contains("training")
    }

    private fun findSessionTable(db: SQLiteDatabase, tables: List<String>): String? {
        val priorityKeywords = listOf(
            "workout_history",
            "completed_workout",
            "session_history",
            "user_workout_history",
            "history",
            "workout_session",
            "workouts"
        )

        for (kw in priorityKeywords) {
            val match = tables.firstOrNull { it.equals(kw, ignoreCase = true) }
            if (match != null) return match
        }

        for (table in tables) {
            val columns = getColumnNames(db, table).map { it.lowercase() }
            val hasTime = columns.any { it.contains("time") || it.contains("date") || it.contains("timestamp") }
            val hasDuration = columns.any { it.contains("duration") || it.contains("length") || it.contains("spent") }
            val hasCal = columns.any { it.contains("cal") || it.contains("energy") || it.contains("kcal") }

            if (hasTime && (hasDuration || hasCal)) {
                return table
            }
        }
        return null
    }

    private fun getColumnNames(db: SQLiteDatabase, tableName: String): List<String> {
        val cols = mutableListOf<String>()
        val cursor = db.rawQuery("PRAGMA table_info(\"$tableName\")", null)
        cursor.use {
            while (it.moveToNext()) {
                val nameIndex = it.getColumnIndex("name")
                if (nameIndex != -1) {
                    cols.add(it.getString(nameIndex))
                }
            }
        }
        return cols
    }

    private fun extractFromSessionTable(db: SQLiteDatabase, tableName: String): List<FitifyWorkout> {
        val list = mutableListOf<FitifyWorkout>()
        val cols = getColumnNames(db, tableName)
        val colMap = cols.associateBy { it.lowercase() }

        // Resolve column names dynamically
        val idCol = findColumn(colMap, listOf("id", "_id", "workout_id", "session_id", "history_id", "uuid"))
        val titleCol = findColumn(colMap, listOf("title", "name", "workout_name", "plan_name", "routine_name", "type_name"))
        val timeCol = findColumn(colMap, listOf("timestamp", "start_time", "date", "created_at", "completed_at", "start_date"))
        val durationCol = findColumn(colMap, listOf("duration", "duration_seconds", "time_spent", "total_time", "elapsed_time"))
        val caloriesCol = findColumn(colMap, listOf("calories", "calories_burned", "kcal", "energy", "burned_calories"))
        val typeCol = findColumn(colMap, listOf("type", "category", "workout_type", "training_type"))
        val notesCol = findColumn(colMap, listOf("notes", "description", "comment"))

        val cursor = db.rawQuery("SELECT * FROM \"$tableName\"", null)
        cursor.use {
            while (it.moveToNext()) {
                try {
                    val rawId = if (idCol != null) getColumnString(it, idCol) else "fitify_${it.position}"
                    val title = if (titleCol != null) getColumnString(it, titleCol).ifBlank { "Fitify Training" } else "Fitify Training"
                    val workoutType = if (typeCol != null) getColumnString(it, typeCol) else "Bodyweight & Fitness"
                    val notes = if (notesCol != null) getColumnString(it, notesCol) else null

                    // Parse timestamp
                    val startTime = parseInstant(it, timeCol) ?: Instant.now()

                    // Parse duration
                    var durationSec = 0L
                    if (durationCol != null) {
                        durationSec = getColumnLong(it, durationCol)
                        // If duration stored in ms, normalize to seconds
                        if (durationSec > 100_000L) {
                            durationSec /= 1000L
                        }
                    }
                    if (durationSec <= 0L) {
                        durationSec = 1200L // Default fallback: 20 minutes
                    }

                    val endTime = startTime.plusSeconds(durationSec)

                    // Parse calories
                    var calories = 0.0
                    if (caloriesCol != null) {
                        calories = getColumnDouble(it, caloriesCol)
                    }
                    if (calories <= 0.0) {
                        // Estimate moderate MET for bodyweight session (~6.5 kcal/min)
                        calories = (durationSec / 60.0) * 6.5
                    }

                    list.add(
                        FitifyWorkout(
                            id = rawId,
                            title = title,
                            workoutType = workoutType,
                            startTime = startTime,
                            endTime = endTime,
                            durationSeconds = durationSec,
                            caloriesBurned = calories,
                            rawNotes = notes
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse single row from $tableName", e)
                }
            }
        }
        return list
    }

    private fun findColumn(colMap: Map<String, String>, candidates: List<String>): String? {
        for (c in candidates) {
            colMap[c]?.let { return it }
        }
        // Substring matching
        for (c in candidates) {
            colMap.keys.firstOrNull { it.contains(c) }?.let { return colMap[it] }
        }
        return null
    }

    private fun getColumnString(cursor: Cursor, colName: String): String {
        val idx = cursor.getColumnIndex(colName)
        return if (idx != -1 && !cursor.isNull(idx)) cursor.getString(idx) else ""
    }

    private fun getColumnLong(cursor: Cursor, colName: String): Long {
        val idx = cursor.getColumnIndex(colName)
        return if (idx != -1 && !cursor.isNull(idx)) cursor.getLong(idx) else 0L
    }

    private fun getColumnDouble(cursor: Cursor, colName: String): Double {
        val idx = cursor.getColumnIndex(colName)
        return if (idx != -1 && !cursor.isNull(idx)) cursor.getDouble(idx) else 0.0
    }

    private fun parseInstant(cursor: Cursor, colName: String?): Instant? {
        if (colName == null) return null
        val idx = cursor.getColumnIndex(colName)
        if (idx == -1 || cursor.isNull(idx)) return null

        return try {
            // Check if numeric timestamp
            val num = cursor.getLong(idx)
            when {
                num > 1_000_000_000_000L -> Instant.ofEpochMilli(num)
                num > 1_000_000_000L -> Instant.ofEpochSecond(num)
                else -> {
                    // Try parsing as ISO-8601 string
                    val text = cursor.getString(idx)
                    Instant.parse(text)
                }
            }
        } catch (_: DateTimeParseException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
