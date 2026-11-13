package com.fitifysync.app

import com.fitifysync.app.data.healthconnect.PreparedSyncRecords
import com.fitifysync.app.data.healthconnect.SyncWriteBatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SyncWriteBatchTest {
    @Test
    fun constructionFailureDoesNotDiscardAnEarlierQueuedWorkout() = runBlocking {
        val writes = mutableListOf<List<String>>()
        var firstSynced = false
        var secondSynced = true
        val batch = SyncWriteBatch<String>({ writes.add(it) })
        batch.enqueue(false, { firstSynced = it }) { PreparedSyncRecords(listOf("A"), 100) }
        batch.enqueue(false, { secondSynced = it }) { error("Invalid B") }
        batch.flush()
        assertEquals(listOf(listOf("A")), writes)
        assertTrue(firstSynced)
        assertFalse(secondSynced)
        assertEquals(1, batch.newlySynced)
        assertEquals(1, batch.failedCount)
    }

    @Test
    fun failedBatchRetriesPerWorkoutAndCountsOnlyConfirmedSuccess() = runBlocking {
        var updatedSynced = true
        var newSynced = false
        val batch = SyncWriteBatch<String>({ records ->
            if (records.size > 2 || records.contains("B")) error("Insert failed")
        })
        batch.enqueue(false, { newSynced = it }) { PreparedSyncRecords(listOf("A", "A-cal"), 100) }
        batch.enqueue(true, { updatedSynced = it }) { PreparedSyncRecords(listOf("B", "B-cal"), 100) }
        assertFalse(newSynced)
        assertFalse(updatedSynced)
        batch.flush()
        assertTrue(newSynced)
        assertFalse(updatedSynced)
        assertEquals(1, batch.newlySynced)
        assertEquals(0, batch.updatedCount)
        assertEquals(1, batch.failedCount)
    }

    @Test
    fun successfulUpdatesAreNotAlsoCountedAsNewWorkouts() = runBlocking {
        val batch = SyncWriteBatch<String>({ })
        batch.enqueue(true, { }) { PreparedSyncRecords(listOf("update"), 100) }
        batch.enqueue(false, { }) { null }
        batch.flush()
        assertEquals(1, batch.updatedCount)
        assertEquals(0, batch.newlySynced)
        assertEquals(1, batch.alreadySynced)
    }

    @Test
    fun respectsRecordAndByteBudgetsWithoutSplittingOneWorkout() = runBlocking {
        val writes = mutableListOf<List<String>>()
        val batch = SyncWriteBatch<String>({ writes.add(it) }, maxRecords = 3, maxEstimatedBytes = 250)
        repeat(2) { n -> batch.enqueue(false, { }) { PreparedSyncRecords(listOf("$n", "$n-cal"), 150) } }
        batch.flush()
        assertEquals(listOf(listOf("0", "0-cal"), listOf("1", "1-cal")), writes)
        assertEquals(2, batch.newlySynced)
    }

    @Test
    fun cancellationIsNotReportedAsSuccessfulOrRetried() = runBlocking {
        var attempts = 0
        var synced = true
        val batch = SyncWriteBatch<String>({ attempts++; throw CancellationException("Cancelled") })
        batch.enqueue(false, { synced = it }) { PreparedSyncRecords(listOf("A"), 100) }
        try { batch.flush(); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertEquals(1, attempts)
        assertFalse(synced)
        assertEquals(0, batch.newlySynced)
    }
}
