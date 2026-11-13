package com.fitifysync.app

import com.fitifysync.app.data.healthconnect.SyncResult
import com.fitifysync.app.data.model.FitifyWorkout
import com.fitifysync.app.data.repository.FitifySyncRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RepositorySyncDefaultsTest {
    @Test
    fun ordinarySyncDoesNotDeleteAndRecreateExistingHealthConnectRecords() = runBlocking {
        var requestedWipe: Boolean? = null
        val repository: FitifySyncRepository = object : FitifySyncRepository {
            override fun isSuBinaryAvailable() = true
            override suspend fun hasRootPermission() = true
            override fun isHealthConnectAvailable() = true
            override suspend fun hasHealthConnectPermissions() = true
            override fun getRequiredHealthPermissions() = emptySet<String>()
            override suspend fun extractAndSyncWorkouts(wipePreviousFirst: Boolean): Result<Pair<List<FitifyWorkout>, SyncResult>> {
                requestedWipe = wipePreviousFirst
                return Result.success(emptyList<FitifyWorkout>() to SyncResult(0, 0, 0, 0))
            }
            override suspend fun exportWorkoutsToCsv(workouts: List<FitifyWorkout>): Result<File> = error("Unused")
            override fun setPeriodicSync(enabled: Boolean) = Unit
        }
        repository.extractAndSyncWorkouts()
        assertEquals(false, requestedWipe)
        repository.extractAndSyncWorkouts(wipePreviousFirst = true)
        assertEquals(true, requestedWipe)
    }
}
