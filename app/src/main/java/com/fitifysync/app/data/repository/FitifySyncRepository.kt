package com.fitifysync.app.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fitifysync.app.data.healthconnect.HealthConnectManager
import com.fitifysync.app.data.healthconnect.SyncResult
import com.fitifysync.app.data.model.FitifyWorkout
import com.fitifysync.app.data.parser.FitifyDatabaseParser
import com.fitifysync.app.data.root.RootRunner
import com.fitifysync.app.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.util.concurrent.TimeUnit

interface FitifySyncRepository {
    fun isSuBinaryAvailable(): Boolean
    suspend fun hasRootPermission(): Boolean
    fun isHealthConnectAvailable(): Boolean
    suspend fun hasHealthConnectPermissions(): Boolean
    fun getRequiredHealthPermissions(): Set<String>
    suspend fun extractAndSyncWorkouts(wipePreviousFirst: Boolean = false): Result<Pair<List<FitifyWorkout>, SyncResult>>
    suspend fun exportWorkoutsToCsv(workouts: List<FitifyWorkout>): Result<File>
    fun setPeriodicSync(enabled: Boolean)
}

class DefaultFitifySyncRepository(
    private val context: Context,
    private val healthConnectManager: HealthConnectManager = HealthConnectManager(context)
) : FitifySyncRepository {

    override fun isSuBinaryAvailable(): Boolean = RootRunner.isSuBinaryAvailable()

    override suspend fun hasRootPermission(): Boolean = RootRunner.hasRootPermission()

    override fun isHealthConnectAvailable(): Boolean = healthConnectManager.isAvailable()

    override suspend fun hasHealthConnectPermissions(): Boolean = healthConnectManager.hasAllPermissions()

    override fun getRequiredHealthPermissions(): Set<String> = healthConnectManager.requiredPermissions

    override suspend fun extractAndSyncWorkouts(wipePreviousFirst: Boolean): Result<Pair<List<FitifyWorkout>, SyncResult>> =
        withContext(Dispatchers.IO) {
            try {
                // 1. Dump database via root
                val dumpResult = RootRunner.dumpFitifyDatabase(context)
                if (dumpResult.isFailure) {
                    return@withContext Result.failure(
                        dumpResult.exceptionOrNull() ?: RuntimeException("Root database dump failed")
                    )
                }
                val dumpDir = dumpResult.getOrThrow()

                // 2. Parse workouts and exercise segments
                val parsedWorkouts = FitifyDatabaseParser.parseWorkouts(dumpDir)
                if (parsedWorkouts.isEmpty()) {
                    try { dumpDir.deleteRecursively() } catch (_: Exception) {}
                    return@withContext Result.failure(RuntimeException("No workouts found in Fitify database"))
                }

                // 3. Sync to Health Connect
                val syncResult = healthConnectManager.syncWorkouts(parsedWorkouts, wipePreviousFirst = wipePreviousFirst)

                // 4. Enrich workouts with heart rate from Samsung Health via Health Connect
                try {
                    for (workout in parsedWorkouts.take(30)) {
                        val hr = healthConnectManager.readHeartRateForRange(workout.startTime, workout.endTime)
                        if (hr != null) {
                            workout.avgHeartRate = hr.first
                            workout.minHeartRate = hr.second
                            workout.maxHeartRate = hr.third
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {}

                // 5. Cleanup dump directory
                try {
                    RootRunner.runSu("rm -rf '${dumpDir.absolutePath}'")
                } catch (_: Exception) {}

                Result.success(Pair(parsedWorkouts, syncResult))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun exportWorkoutsToCsv(workouts: List<FitifyWorkout>): Result<File> =
        withContext(Dispatchers.IO) {
            if (workouts.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No workouts available to export"))
            }
            try {
                val exportDir = context.getExternalFilesDir(null) ?: context.filesDir
                val csvFile = File(exportDir, "fitify_workouts_export.csv")
                FileWriter(csvFile).use { writer ->
                    writer.append("ID,Title,WorkoutType,StartTime,EndTime,DurationSeconds,CaloriesBurned,ExercisesCount,AvgHeartRate\n")
                    for (w in workouts) {
                        writer.append("\"${w.id}\",\"${w.title}\",\"${w.workoutType}\",\"${w.startTime}\",\"${w.endTime}\",${w.durationSeconds},${w.caloriesBurned},${w.exercises.size},${w.avgHeartRate ?: ""}\n")
                    }
                    writer.flush()
                }
                Result.success(csvFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override fun setPeriodicSync(enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "FitifyPeriodicSync"
    }
}
