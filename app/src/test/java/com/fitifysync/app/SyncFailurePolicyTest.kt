package com.fitifysync.app

import com.fitifysync.app.data.healthconnect.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SyncFailurePolicyTest {
    @Test fun preparationFailureIsPermanentAndCleanupFailureRemainsPending() = runBlocking {
        var synced = false
        val batch=SyncWriteBatch<String>({})
        batch.enqueue(false,{}) { throw IllegalArgumentException("oversized") }
        assertEquals(1,batch.failedCount)
        assertEquals(0,batch.retryableFailedCount)
        batch.enqueue(true,{synced=it}) { PreparedSyncRecords(listOf("session"),100,afterWrite={throw java.io.IOException("delete failed")}) }
        batch.flush()
        assertFalse(synced)
        assertEquals(0,batch.updatedCount)
        assertEquals(1,batch.retryableFailedCount)
    }
    @Test fun companionCleanupCompletesBeforeAWorkoutIsMarkedSynced() = runBlocking {
        val events=mutableListOf<String>()
        val batch=SyncWriteBatch<String>({events.add("insert")})
        batch.enqueue(true,{if(it) events.add("confirmed")}) { PreparedSyncRecords(listOf("session"),100,afterWrite={events.add("delete stale calories")}) }
        batch.flush()
        assertEquals(listOf("insert","delete stale calories","confirmed"),events)
        assertEquals(1,batch.updatedCount)
    }
    @Test fun stableRecordLookupHasNoSourceDependentLowerBound() {
        val filter=HealthConnectManager.existingRecordFilter()
        assertNull(filter.startTime)
        assertNotNull(filter.endTime)
    }
    @Test fun workerRetriesTransientReadsButNotUnsupportedSourcesOrPermanentPreparation() {
        assertTrue(com.fitifysync.app.sync.SyncWorker.isRetryableFailure(IllegalStateException("read",java.io.IOException("locked"))))
        assertFalse(com.fitifysync.app.sync.SyncWorker.isRetryableFailure(UnsupportedOperationException("unknown layout")))
        assertEquals(androidx.work.ListenableWorker.Result.failure(),com.fitifysync.app.sync.SyncWorker.syncOutcome(SyncResult(1,0,0,1)))
        assertEquals(androidx.work.ListenableWorker.Result.retry(),com.fitifysync.app.sync.SyncWorker.syncOutcome(SyncResult(1,0,0,1,retryableFailedCount=1)))
    }
    @Test fun unknownOrInvalidCaloriesNeverBecomeExplicitZeroCorrections() {
        val start=java.time.Instant.parse("2026-11-12T10:00:00Z")
        fun workout(value:Double,known:Boolean=true)=com.fitifysync.app.data.model.FitifyWorkout("id","Workout","strength",start,start.plusSeconds(60),60,value,caloriesKnown=known)
        assertFalse(workout(0.0,false).hasKnownCalories)
        assertFalse(workout(Double.NaN).hasKnownCalories)
        assertFalse(workout(-1.0).hasKnownCalories)
        assertTrue(workout(0.0).hasKnownCalories)
    }
}
