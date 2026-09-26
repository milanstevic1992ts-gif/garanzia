package com.milanstevic.garanzia.storage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.milanstevic.garanzia.data.ReceiptRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReceiptSyncWorkerEntryPoint {
    fun receiptRepository(): ReceiptRepository
    fun receiptMirrorManager(): ReceiptMirrorManager
    fun storageSettings(): StorageSettings
    fun syncStatusStore(): SyncStatusStore
}

class ReceiptSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            ReceiptSyncWorkerEntryPoint::class.java,
        )
        val repository = dependencies.receiptRepository()
        val mirrorManager = dependencies.receiptMirrorManager()
        val storageSettings = dependencies.storageSettings()
        val syncStatusStore = dependencies.syncStatusStore()

        val storage = storageSettings.state.value
        if (!storage.phoneConfigured && !storage.driveConfigured) {
            syncStatusStore.markSkipped(
                message = "Nessuna cartella esterna configurata",
            )
            return@withContext Result.success()
        }

        syncStatusStore.markRunning()

        return@withContext try {
            val receipts = repository.getAllReceipts()

            val summary = mirrorManager.mirrorArchive(receipts)
            val totalFailures =
                summary.failedCopies + summary.pendingDeletionFailures

            val message =
                when {
                    totalFailures > 0 ->
                        "Sincronizzazione background parziale"
                    receipts.isEmpty() ->
                        "Archivio vuoto: pulizia esterna verificata"
                    else ->
                        "Sincronizzazione background completata"
                }

            syncStatusStore.markResult(
                successfulCopies = summary.successfulCopies,
                failedCopies = totalFailures,
                message = message,
            )

            if (totalFailures == 0) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (t: Throwable) {
            syncStatusStore.markResult(
                successfulCopies = 0,
                failedCopies = 1,
                message = t.message ?: "Errore sincronizzazione background",
            )
            Result.retry()
        }
    }
}
