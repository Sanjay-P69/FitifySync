package com.fitifysync.app.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitifysync.app.data.healthconnect.HealthConnectManager
import com.fitifysync.app.data.parser.FitifyDatabaseParser
import com.fitifysync.app.data.root.RootRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "SyncWorker"
    private val CHANNEL_ID = "fitify_sync_channel"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting Fitify background sync worker...")

        // 1. Verify root permission
        if (!RootRunner.hasRootPermission()) {
            Log.e(TAG, "Root permission not available during background sync.")
            return@withContext Result.retry()
        }

        // 2. Dump Fitify database
        val dumpResult = RootRunner.dumpFitifyDatabase(applicationContext)
        if (dumpResult.isFailure) {
            Log.e(TAG, "Failed to dump Fitify DB: ${dumpResult.exceptionOrNull()?.message}")
            return@withContext Result.retry()
        }

        val dumpDir = dumpResult.getOrThrow()

        // 3. Parse workout sessions
        val workouts = FitifyDatabaseParser.parseWorkouts(dumpDir)
        Log.i(TAG, "Parsed ${workouts.size} workouts from Fitify database.")

        if (workouts.isEmpty()) {
            try { dumpDir.deleteRecursively() } catch (_: Exception) {}
            return@withContext Result.success()
        }

        // 4. Sync to Health Connect
        val healthConnectManager = HealthConnectManager(applicationContext)
        if (!healthConnectManager.hasAllPermissions()) {
            Log.w(TAG, "Health Connect permissions missing during background sync.")
            try { dumpDir.deleteRecursively() } catch (_: Exception) {}
            return@withContext Result.failure()
        }

        val syncResult = healthConnectManager.syncWorkouts(workouts)
        Log.i(TAG, syncResult.summaryText)

        // 5. Clean up temporary database dump
        try {
            RootRunner.runSu("rm -rf /data/local/tmp/fitify_dump")
            dumpDir.deleteRecursively()
        } catch (_: Exception) {}

        // 6. Notify user if new sessions synced
        if (syncResult.newlySynced > 0 || syncResult.updatedCount > 0 || syncResult.failedCount > 0) {
            sendNotification(
                if (syncResult.failedCount == 0) "Fitify Sync Complete" else "Fitify Sync Incomplete",
                syncResult.summaryText
            )
        }

        if (syncResult.failedCount > 0) Result.retry() else Result.success()
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fitify Sync Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when workouts are synced to Health Connect"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
