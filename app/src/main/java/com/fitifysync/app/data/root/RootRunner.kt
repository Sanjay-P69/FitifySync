package com.fitifysync.app.data.root

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val error: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

object RootRunner {
    private const val TAG = "RootRunner"
    const val FITIFY_PACKAGE = "com.fitifyworkouts.bodyweight.workoutapp"

    fun isSuBinaryAvailable(): Boolean {
        val paths = arrayOf(
            "/system_ext/bin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "/data/adb/magisk/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            process.waitFor() == 0 && !line.isNullOrEmpty()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun runSu(command: String, timeoutMillis: Long = 15000L): CommandResult = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(timeoutMillis) {
            var process: Process? = null
            try {
                process = ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start()

                process.outputStream.close()

                val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                val exitCode = process.waitFor()

                Log.d(TAG, "runSu cmd: $command -> exitCode: $exitCode, output: $output")
                CommandResult(exitCode, output, if (exitCode != 0) output else "")
            } catch (e: Exception) {
                Log.e(TAG, "Error executing root command: $command", e)
                CommandResult(-1, "", e.localizedMessage ?: "Execution error")
            } finally {
                try {
                    process?.destroy()
                } catch (_: Exception) {}
            }
        }

        result ?: CommandResult(-1, "", "Command timed out after ${timeoutMillis}ms")
    }

    suspend fun hasRootPermission(): Boolean {
        val res = runSu("id")
        return res.isSuccess && (res.output.contains("uid=0") || res.output.contains("context=u:r:magisk"))
    }

    suspend fun dumpFitifyDatabase(context: Context): Result<File> = withContext(Dispatchers.IO) {
        try {
            val destDir = File(context.filesDir, "fitify_dump").apply { mkdirs() }
            val destPath = destDir.absolutePath

            val candidateLocations = listOf(
                "/data/user/0/$FITIFY_PACKAGE/databases",
                "/data/data/$FITIFY_PACKAGE/databases",
                "/sdcard/Download",
                "/data/media/0/Download"
            )

            val copyCommands = mutableListOf("mkdir -p '$destPath'")
            for (dir in candidateLocations) {
                copyCommands.add("cp -f $dir/firestore* '$destPath/' 2>/dev/null")
                copyCommands.add("cp -f $dir/fitify.db* '$destPath/' 2>/dev/null")
            }
            copyCommands.add("chmod -R 777 '$destPath'")
            copyCommands.add("ls -la '$destPath'")

            val fullCmd = copyCommands.joinToString(" ; ")
            val res = runSu(fullCmd)
            Log.i(TAG, "Database extraction result: exit=${res.exitCode}, output=${res.output}")

            val files = destDir.listFiles()?.filter {
                it.isFile && it.length() > 0 &&
                        !it.name.endsWith("-journal") &&
                        !it.name.endsWith("-wal") &&
                        !it.name.endsWith("-shm")
            } ?: emptyList()

            if (files.isEmpty()) {
                return@withContext Result.failure(
                    RuntimeException("No database files found in candidate locations: $candidateLocations")
                )
            }

            Log.i(TAG, "Successfully extracted ${files.size} database file(s) to $destPath: ${files.map { "${it.name} (${it.length()} bytes)" }}")
            Result.success(destDir)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during database dump", e)
            Result.failure(e)
        }
    }
}
