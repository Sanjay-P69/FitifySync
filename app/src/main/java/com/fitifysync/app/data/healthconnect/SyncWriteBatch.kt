package com.fitifysync.app.data.healthconnect

import kotlinx.coroutines.CancellationException

internal data class PreparedSyncRecords<T>(val records: List<T>, val estimatedBytes: Int, val afterWrite: suspend () -> Unit = {})

internal class SyncWriteBatch<T>(
    private val insertRecords: suspend (List<T>) -> Unit,
    private val maxRecords: Int = 50,
    private val maxEstimatedBytes: Int = 256 * 1024
) {
    private data class Pending<T>(val prepared: PreparedSyncRecords<T>, val isUpdate: Boolean, val markSynced: (Boolean) -> Unit)
    private val pending = mutableListOf<Pending<T>>()
    private var recordCount = 0
    private var estimatedBytes = 0
    var newlySynced = 0
        private set
    var updatedCount = 0
        private set
    var alreadySynced = 0
        private set
    var failedCount = 0
        private set
    var retryableFailedCount = 0
        private set
    var errorMessage: String? = null
        private set

    suspend fun enqueue(isUpdate: Boolean, markSynced: (Boolean) -> Unit, prepare: () -> PreparedSyncRecords<T>?) {
        markSynced(false)
        try {
            val prepared = prepare()
            if (prepared == null) {
                markSynced(true)
                alreadySynced++
                return
            }
            require(prepared.records.isNotEmpty() && prepared.records.size <= maxRecords &&
                prepared.estimatedBytes in 0..maxEstimatedBytes) { "One workout exceeds FitifySync's transport budget." }
            if (recordCount + prepared.records.size > maxRecords || estimatedBytes + prepared.estimatedBytes > maxEstimatedBytes) flush()
            pending.add(Pending(prepared, isUpdate, markSynced))
            recordCount += prepared.records.size
            estimatedBytes += prepared.estimatedBytes
            if (recordCount == maxRecords || estimatedBytes == maxEstimatedBytes) flush()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failedCount++
            errorMessage = error.localizedMessage
        }
    }

    suspend fun flush() {
        if (pending.isEmpty()) return
        val batch = pending.toList()
        pending.clear()
        recordCount = 0
        estimatedBytes = 0
        try {
            insertRecords(batch.flatMap { it.prepared.records })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            for (workout in batch) {
                try {
                    insertRecords(workout.prepared.records)
                    confirm(workout)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failedCount++
                    if (error !is SecurityException && error !is IllegalArgumentException && error !is UnsupportedOperationException) retryableFailedCount++
                    errorMessage = error.localizedMessage
                }
            }
            return
        }
        for (workout in batch) confirm(workout)
    }

    private suspend fun confirm(workout: Pending<T>) {
        try {
            workout.prepared.afterWrite()
            workout.markSynced(true)
            if (workout.isUpdate) updatedCount++ else newlySynced++
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failedCount++
            if (error !is SecurityException && error !is IllegalArgumentException && error !is UnsupportedOperationException) retryableFailedCount++
            errorMessage = error.localizedMessage
        }
    }
}
