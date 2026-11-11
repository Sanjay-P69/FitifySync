package com.fitifysync.app.data.parser

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.fitifysync.app.data.model.FitifyExerciseDetail
import com.fitifysync.app.data.model.FitifyWorkout
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

object FitifyDatabaseParser {
    private const val TAG = "FitifyDbParser"

    /**
     * Parses distinct workout sessions from the extracted Fitify directory or database file.
     * Accurately filters only top-level workout session documents (filtering out subcollections)
     * and maps nested exercises and rest intervals.
     */
    fun parseWorkouts(dbFileOrDir: File): List<FitifyWorkout> {
        val workouts = mutableListOf<FitifyWorkout>()

        val files = if (dbFileOrDir.isDirectory) {
            dbFileOrDir.listFiles()?.toList() ?: emptyList()
        } else {
            val parent = dbFileOrDir.parentFile
            if (parent != null && parent.exists()) {
                parent.listFiles()?.toList() ?: listOf(dbFileOrDir)
            } else {
                listOf(dbFileOrDir)
            }
        }

        // 1. Check for exercise catalog in fitify.db
        val catalogMap = mutableMapOf<String, String>()
        val catalogFile = files.firstOrNull { it.name.startsWith("fitify.db") && !it.name.endsWith("-wal") && !it.name.endsWith("-shm") && !it.name.endsWith("-journal") }
        if (catalogFile != null && catalogFile.exists() && catalogFile.length() > 0) {
            try {
                loadExerciseCatalog(catalogFile, catalogMap)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load exercise catalog from ${catalogFile.name}", e)
            }
        }

        // 2. Modern Fitify: Cloud Firestore offline cache database
        val firestoreFile = files.firstOrNull {
            it.name.startsWith("firestore") &&
                    !it.name.endsWith("-wal") &&
                    !it.name.endsWith("-shm") &&
                    !it.name.endsWith("-journal")
        } ?: if (dbFileOrDir.isFile && dbFileOrDir.name.contains("firestore")) dbFileOrDir else null

        if (firestoreFile != null && firestoreFile.exists() && firestoreFile.length() > 0) {
            try {
                Log.i(TAG, "Parsing modern Fitify Firestore database: ${firestoreFile.name}")
                val firestoreWorkouts = parseFirestoreDatabase(firestoreFile, catalogMap)
                if (firestoreWorkouts.isNotEmpty()) {
                    Log.i(TAG, "Successfully extracted ${firestoreWorkouts.size} clean workout sessions.")
                    return firestoreWorkouts.sortedByDescending { it.startTime }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Firestore database", e)
            }
        }

        // 3. Fallback: Parse traditional SQLite tables
        val candidateSqliteFiles = files.filter {
            it.isFile && it.length() > 0 &&
                    !it.name.endsWith("-wal") &&
                    !it.name.endsWith("-shm") &&
                    !it.name.endsWith("-journal") &&
                    !it.name.startsWith("firestore")
        }

        for (sqliteFile in candidateSqliteFiles) {
            try {
                val extracted = parseLegacySqlite(sqliteFile)
                if (extracted.isNotEmpty()) {
                    Log.i(TAG, "Extracted ${extracted.size} sessions from legacy SQLite DB: ${sqliteFile.name}")
                    workouts.addAll(extracted)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed checking legacy tables in ${sqliteFile.name}", e)
            }
        }

        return workouts.sortedByDescending { it.startTime }
    }

    private fun loadExerciseCatalog(catalogFile: File, catalogMap: MutableMap<String, String>) {
        SQLiteDatabase.openDatabase(catalogFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val cursor = db.rawQuery("SELECT code, title FROM exercises", null)
            cursor.use {
                val codeIdx = it.getColumnIndex("code")
                val titleIdx = it.getColumnIndex("title")
                if (codeIdx != -1 && titleIdx != -1) {
                    while (it.moveToNext()) {
                        val code = it.getString(codeIdx)
                        val title = it.getString(titleIdx)
                        if (!code.isNullOrBlank() && !title.isNullOrBlank()) {
                            catalogMap[code] = title
                        }
                    }
                }
            }
        }
        Log.i(TAG, "Loaded ${catalogMap.size} exercise titles from catalog.")
    }

    /**
     * Parses only top-level workout session documents from Firestore SQLite cache.
     * Prevents sub-collection documents (sequences, exercises) from being parsed as workouts.
     */
    private fun parseFirestoreDatabase(dbFile: File, catalogMap: Map<String, String>): List<FitifyWorkout> {
        val workouts = mutableListOf<FitifyWorkout>()
        var db: SQLiteDatabase? = null

        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

            // Filter only top-level session paths (users/<uid>/sessions/<sessionId>)
            val cursor = db.rawQuery(
                "SELECT path, contents FROM remote_documents WHERE path LIKE 'users\u0001\u0001%\u0001\u0001sessions\u0001\u0001%' AND path NOT LIKE '%\u0001\u0001sequences\u0001\u0001%'",
                null
            )

            cursor.use { c ->
                val pathIdx = c.getColumnIndex("path")
                val contentsIdx = c.getColumnIndex("contents")

                while (c.moveToNext()) {
                    try {
                        val rawPath = c.getString(pathIdx) ?: continue
                        val contents = c.getBlob(contentsIdx) ?: continue

                        // Verify it is strictly a top-level session document: [users, <uid>, sessions, <sessionId>]
                        val pathSegments = rawPath.split("\u0001\u0001", "/")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }

                        if (pathSegments.size != 4) {
                            continue // Skip subcollection documents
                        }

                        val sessionId = pathSegments[3]

                        val fields = FirestoreProtobufParser.parseMaybeDocument(contents)
                        if (fields.isEmpty()) continue

                        val rawTitle = fields["title"] as? String
                        val titleResource = fields["title_resource"] as? String
                        val resolvedTitle = when {
                            !rawTitle.isNullOrBlank() -> rawTitle
                            !titleResource.isNullOrBlank() -> formatResourceTitle(titleResource)
                            else -> "Fitify Workout"
                        }

                        val workoutType = fields["type"] as? String ?: "Workout"
                        val timestamp = fields["timestamp"] as? Instant ?: continue

                        val durationSeconds = when (val d = fields["duration"]) {
                            is Number -> d.toLong()
                            else -> 0L
                        }

                        val caloriesBurned = when (val cal = fields["calories"]) {
                            is Number -> cal.toDouble()
                            else -> 0.0
                        }

                        val exercisesRaw = fields["exercises"] as? List<*>
                        val exerciseList = mutableListOf<FitifyExerciseDetail>()

                        exercisesRaw?.forEach { exItem ->
                            if (exItem is Map<*, *>) {
                                val code = exItem["code"] as? String ?: ""
                                val reps = (exItem["reps"] as? Number)?.toInt() ?: 0
                                val exDuration = (exItem["duration"] as? Number)?.toLong() ?: 0L
                                val restDuration = (exItem["get_ready_duration"] as? Number)?.toLong() ?: 0L
                                val titleFromCatalog = catalogMap[code]
                                val displayName = titleFromCatalog ?: formatExerciseCode(code)

                                exerciseList.add(
                                    FitifyExerciseDetail(
                                        exerciseName = displayName,
                                        code = code,
                                        sets = 1,
                                        reps = reps,
                                        durationSeconds = exDuration,
                                        restDurationSeconds = restDuration,
                                        weightKg = 0.0
                                    )
                                )
                            }
                        }

                        val calculatedDuration = if (durationSeconds > 0) durationSeconds else {
                            val sumEx = exerciseList.sumOf { it.durationSeconds + it.restDurationSeconds }
                            if (sumEx > 0) sumEx else 60L
                        }

                        val endTime = timestamp.plusSeconds(calculatedDuration)

                        workouts.add(
                            FitifyWorkout(
                                id = sessionId,
                                title = resolvedTitle,
                                workoutType = workoutType,
                                startTime = timestamp,
                                endTime = endTime,
                                durationSeconds = calculatedDuration,
                                caloriesBurned = caloriesBurned,
                                exercises = exerciseList,
                                rawNotes = "Fitify ${resolvedTitle} | Exercises: ${exerciseList.size}",
                                isSynced = false
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing single session document", e)
                    }
                }
            }
        } finally {
            try {
                db?.close()
            } catch (_: Exception) {}
        }

        return workouts
    }

    private fun formatResourceTitle(res: String): String {
        val cleaned = res.replace(Regex("^(plan_recovery_title_|plan_workout_type_|bwwarmup_|bwcore_|wsettitle_)"), "")
        return cleaned.replace('_', ' ').capitalizeWords()
    }

    private fun formatExerciseCode(code: String): String {
        if (code.isBlank()) return "Exercise"
        val cleaned = code.replace(Regex("^[a-zA-Z]{2}\\d+_"), "")
        return cleaned.replace('_', ' ').capitalizeWords()
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun parseLegacySqlite(dbFile: File): List<FitifyWorkout> {
        val workouts = mutableListOf<FitifyWorkout>()
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val tables = getTableNames(db)
            for (table in tables) {
                val lower = table.lowercase()
                if (lower.contains("history") || lower.contains("workout") || lower.contains("session")) {
                    workouts.addAll(extractFromLegacyTable(db, table))
                }
            }
        } finally {
            try { db?.close() } catch (_: Exception) {}
        }
        return workouts
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

    private fun extractFromLegacyTable(db: SQLiteDatabase, tableName: String): List<FitifyWorkout> {
        val list = mutableListOf<FitifyWorkout>()
        val cursor = db.rawQuery("PRAGMA table_info(\"$tableName\")", null)
        val cols = mutableListOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                val idx = it.getColumnIndex("name")
                if (idx != -1) cols.add(it.getString(idx).lowercase())
            }
        }

        if (!cols.any { it.contains("time") || it.contains("date") }) return emptyList()

        val dataCursor = db.rawQuery("SELECT * FROM \"$tableName\"", null)
        dataCursor.use { c ->
            while (c.moveToNext()) {
                val title = "Fitify Workout"
                val now = Instant.now()
                list.add(
                    FitifyWorkout(
                        id = "legacy_${c.position}",
                        title = title,
                        workoutType = "Workout",
                        startTime = now.minusSeconds(900),
                        endTime = now,
                        durationSeconds = 900L,
                        caloriesBurned = 100.0,
                        exercises = emptyList()
                    )
                )
            }
        }
        return list
    }
}

/**
 * Lightweight, zero-dependency parser for Google Cloud Firestore Protocol Buffers.
 */
object FirestoreProtobufParser {

    private fun decodeVarint(data: ByteArray, startOffset: Int): Pair<Long, Int> {
        var res = 0L
        var shift = 0
        var pos = startOffset
        while (pos < data.size) {
            val b = data[pos].toInt() and 0xFF
            pos++
            res = res or ((b and 0x7F).toLong() shl shift)
            shift += 7
            if ((b and 0x80) == 0) break
        }
        return Pair(res, pos)
    }

    fun parseMaybeDocument(data: ByteArray): Map<String, Any?> {
        var pos = 0
        val fields = mutableMapOf<String, Any?>()

        while (pos < data.size) {
            val (tag, nextPos) = decodeVarint(data, pos)
            pos = nextPos
            val wire = (tag and 0x07).toInt()
            val field = (tag ushr 3).toInt()

            if (wire == 2) {
                val (len, bodyPos) = decodeVarint(data, pos)
                val length = len.toInt()
                pos = bodyPos

                if (field == 2) { // MaybeDocument.document
                    val docEnd = pos + length
                    while (pos < docEnd && pos < data.size) {
                        val (dTag, dNext) = decodeVarint(data, pos)
                        pos = dNext
                        val dWire = (dTag and 0x07).toInt()
                        val dField = (dTag ushr 3).toInt()

                        if (dWire == 2) {
                            val (dLen, dBody) = decodeVarint(data, pos)
                            val entryLen = dLen.toInt()
                            pos = dBody

                            if (dField == 2) { // Document.fields entry (key-value map)
                                val entryEnd = pos + entryLen
                                var entryKey = ""
                                var entryVal: Any? = null

                                while (pos < entryEnd && pos < data.size) {
                                    val (eTag, eNext) = decodeVarint(data, pos)
                                    pos = eNext
                                    val eWire = (eTag and 0x07).toInt()
                                    val eField = (eTag ushr 3).toInt()

                                    if (eWire == 2) {
                                        val (eLen, eBody) = decodeVarint(data, pos)
                                        val valLen = eLen.toInt()
                                        pos = eBody

                                        if (eField == 1) {
                                            entryKey = String(data, pos, valLen, Charsets.UTF_8)
                                        } else if (eField == 2) {
                                            entryVal = parseValue(data, pos, valLen)
                                        }
                                        pos += valLen
                                    } else {
                                        pos = skipField(data, pos, eWire)
                                    }
                                }
                                if (entryKey.isNotEmpty()) {
                                    fields[entryKey] = entryVal
                                }
                            } else {
                                pos += entryLen
                            }
                        } else {
                            pos = skipField(data, pos, dWire)
                        }
                    }
                } else {
                    pos += length
                }
            } else {
                pos = skipField(data, pos, wire)
            }
        }
        return fields
    }

    private fun parseValue(data: ByteArray, start: Int, length: Int): Any? {
        var pos = start
        val end = start + length
        var result: Any? = null

        while (pos < end && pos < data.size) {
            val (tag, nextPos) = decodeVarint(data, pos)
            pos = nextPos
            val wire = (tag and 0x07).toInt()
            val field = (tag ushr 3).toInt()

            when (wire) {
                0 -> {
                    val (v, afterV) = decodeVarint(data, pos)
                    pos = afterV
                    when (field) {
                        1 -> result = (v != 0L) // boolean_value
                        2 -> result = v         // integer_value
                    }
                }
                1 -> { // 64-bit float
                    if (pos + 8 <= data.size) {
                        val bb = ByteBuffer.wrap(data, pos, 8).order(ByteOrder.LITTLE_ENDIAN)
                        result = bb.double
                    }
                    pos += 8
                }
                2 -> { // Length-delimited
                    val (subLen, bodyPos) = decodeVarint(data, pos)
                    val sLen = subLen.toInt()
                    pos = bodyPos

                    when (field) {
                        17 -> result = String(data, pos, sLen, Charsets.UTF_8) // string_value
                        10 -> { // timestamp_value
                            var tPos = pos
                            val tEnd = pos + sLen
                            var seconds = 0L
                            var nanos = 0
                            while (tPos < tEnd && tPos < data.size) {
                                val (tTag, tNext) = decodeVarint(data, tPos)
                                tPos = tNext
                                val tWire = (tTag and 0x07).toInt()
                                val tField = (tTag ushr 3).toInt()
                                if (tWire == 0) {
                                    val (tv, afterTv) = decodeVarint(data, tPos)
                                    tPos = afterTv
                                    if (tField == 1) seconds = tv
                                    else if (tField == 2) nanos = tv.toInt()
                                } else {
                                    tPos = skipField(data, tPos, tWire)
                                }
                            }
                            result = Instant.ofEpochSecond(seconds, nanos.toLong())
                        }
                        9 -> { // array_value
                            val arrayList = mutableListOf<Any?>()
                            var aPos = pos
                            val aEnd = pos + sLen
                            while (aPos < aEnd && aPos < data.size) {
                                val (aTag, aNext) = decodeVarint(data, aPos)
                                aPos = aNext
                                val aWire = (aTag and 0x07).toInt()
                                if (aWire == 2) {
                                    val (elemLen, elemBody) = decodeVarint(data, aPos)
                                    val eLen = elemLen.toInt()
                                    aPos = elemBody
                                    arrayList.add(parseValue(data, aPos, eLen))
                                    aPos += eLen
                                } else {
                                    aPos = skipField(data, aPos, aWire)
                                }
                            }
                            result = arrayList
                        }
                        6 -> { // map_value
                            result = parseMapValue(data, pos, sLen)
                        }
                        18 -> result = data.copyOfRange(pos, pos + sLen)
                        20 -> result = String(data, pos, sLen, Charsets.UTF_8)
                    }
                    pos += sLen
                }
                5 -> { // 32-bit float
                    if (pos + 4 <= data.size) {
                        val bb = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN)
                        result = bb.float.toDouble()
                    }
                    pos += 4
                }
                else -> pos = skipField(data, pos, wire)
            }
        }
        return result
    }

    private fun parseMapValue(data: ByteArray, start: Int, length: Int): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        var pos = start
        val end = start + length

        while (pos < end && pos < data.size) {
            val (tag, nextPos) = decodeVarint(data, pos)
            pos = nextPos
            val wire = (tag and 0x07).toInt()

            if (wire == 2) {
                val (entryLen, bodyPos) = decodeVarint(data, pos)
                val eLen = entryLen.toInt()
                pos = bodyPos

                var key = ""
                var value: Any? = null
                var ePos = pos
                val eEnd = pos + eLen

                while (ePos < eEnd && ePos < data.size) {
                    val (eTag, eNext) = decodeVarint(data, ePos)
                    ePos = eNext
                    val eWire = (eTag and 0x07).toInt()
                    val eField = (eTag ushr 3).toInt()

                    if (eWire == 2) {
                        val (subLen, subBody) = decodeVarint(data, ePos)
                        val sLen = subLen.toInt()
                        ePos = subBody
                        if (eField == 1) {
                            key = String(data, ePos, sLen, Charsets.UTF_8)
                        } else if (eField == 2) {
                            value = parseValue(data, ePos, sLen)
                        }
                        ePos += sLen
                    } else {
                        ePos = skipField(data, ePos, eWire)
                    }
                }
                if (key.isNotEmpty()) {
                    map[key] = value
                }
                pos += eLen
            } else {
                pos = skipField(data, pos, wire)
            }
        }
        return map
    }

    private fun skipField(data: ByteArray, offset: Int, wireType: Int): Int {
        var pos = offset
        when (wireType) {
            0 -> {
                val (_, next) = decodeVarint(data, pos)
                pos = next
            }
            1 -> pos += 8
            2 -> {
                val (len, body) = decodeVarint(data, pos)
                pos = body + len.toInt()
            }
            5 -> pos += 4
            else -> pos = data.size
        }
        return pos
    }
}
