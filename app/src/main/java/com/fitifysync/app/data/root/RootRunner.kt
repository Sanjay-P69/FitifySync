package com.fitifysync.app.data.root

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
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

    /**
     * Checks if the 'su' binary is available in common PATH locations.
     */
    fun isSuBinaryAvailable(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            process.waitFor() == 0 && !line.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Executes root shell command using 'su'.
     */
    suspend fun runSu(command: String): CommandResult = withContext(Dispatchers.IO) {
        var process: Process? = null
        var os: DataOutputStream? = null
        try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            val stdout = StringBuilder()
            val stderr = StringBuilder()

            var line: String?
            while (stdoutReader.readLine().also { line = it } != null) {
                stdout.append(line).append("\n")
            }
            while (stderrReader.readLine().also { line = it } != null) {
                stderr.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            CommandResult(exitCode, stdout.toString().trim(), stderr.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error executing root command: $command", e)
            CommandResult(-1, "", e.localizedMessage ?: "Unknown error")
        } finally {
            try {
                os?.close()
                process?.destroy()
            } catch (_: Exception) {}
        }
    }

    /**
     * Requests root access and verifies root UID.
     */
    suspend fun hasRootPermission(): Boolean {
        val res = runSu("id")
        return res.isSuccess && res.output.contains("uid=0")
    }

    /**
     * Discovers database files in Fitify's private directories.
     */
    suspend fun discoverFitifyDatabases(): List<String> {
        val candidatePaths = listOf(
            "/data/data/$FITIFY_PACKAGE/databases",
            "/data/user/0/$FITIFY_PACKAGE/databases"
        )

        for (dir in candidatePaths) {
            val res = runSu("ls $dir")
            if (res.isSuccess && res.output.isNotBlank()) {
                val files = res.output.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.endsWith("-journal") && !it.endsWith("-wal") && !it.endsWith("-shm") }
                if (files.isNotEmpty()) {
                    return files.map { "$dir/$it" }
                }
            }
        }
        return emptyList()
    }

    /**
     * Safely copies the Fitify SQLite database (including WAL and SHM journal files)
     * to the app's internal cache directory, then grants read permission to the app.
     */
    suspend fun dumpFitifyDatabase(context: Context): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dbList = discoverFitifyDatabases()
            if (dbList.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("No Fitify database found. Ensure Fitify is installed and has been opened at least once.")
                )
            }

            // Find primary database (prefer fitify.db, workouts.db, or largest file)
            val primaryDbRemotePath = dbList.firstOrNull {
                it.contains("fitify", ignoreCase = true) || it.contains("workout", ignoreCase = true)
            } ?: dbList.first()

            val localDir = File(context.cacheDir, "fitify_dump").apply { mkdirs() }
            val localDbFile = File(localDir, "fitify_extracted.db")

            // Copy main DB file
            val copyCmd = StringBuilder()
            copyCmd.append("cp \"$primaryDbRemotePath\" \"${localDbFile.absolutePath}\"\n")
            copyCmd.append("chmod 666 \"${localDbFile.absolutePath}\"\n")

            // Copy WAL and SHM if present to prevent SQLite corruption or missing recent records
            val walRemote = "$primaryDbRemotePath-wal"
            val walLocal = "${localDbFile.absolutePath}-wal"
            val shmRemote = "$primaryDbRemotePath-shm"
            val shmLocal = "${localDbFile.absolutePath}-shm"

            copyCmd.append("if [ -f \"$walRemote\" ]; then cp \"$walRemote\" \"$walLocal\" && chmod 666 \"$walLocal\"; fi\n")
            copyCmd.append("if [ -f \"$shmRemote\" ]; then cp \"$shmRemote\" \"$shmLocal\" && chmod 666 \"$shmLocal\"; fi\n")

            val result = runSu(copyCmd.toString())
            if (!result.isSuccess || !localDbFile.exists() || localDbFile.length() == 0L) {
                return@withContext Result.failure(
                    RuntimeException("Failed to dump database via root: ${result.error}")
                )
            }

            Log.i(TAG, "Successfully dumped Fitify DB: ${localDbFile.absolutePath} (${localDbFile.length()} bytes)")
            Result.success(localDbFile)
        } catch (e: Exception) {
            Log.e(TAG, "Exception dumping Fitify database", e)
            Result.failure(e)
        }
    }
}
