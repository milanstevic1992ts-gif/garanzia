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
            syncStatusStore.markResult(
                successfulCopies = 0,
                failedCopies = 0,
                message = "Nessuna cartella esterna configurata",
            )
            return@withContext Result.success()
        }

        syncStatusStore.markRunning()

        return@withContext try {
            val receipts = repository.getAllReceipts()

            if (receipts.isEmpty()) {
                syncStatusStore.markResult(
                    successfulCopies = 0,
                    failedCopies = 0,
                    message = "Archivio vuoto: nulla da sincronizzare",
                )
                Result.success()
            } else {
                val summary = mirrorManager.mirrorArchive(receipts)
                val message =
                    if (summary.failedCopies == 0) {
                        "Sincronizzazione background completata"
                    } else {
                        "Sincronizzazione background parziale"
                    }

                syncStatusStore.markResult(
                    successfulCopies = summary.successfulCopies,
                    failedCopies = summary.failedCopies,
                    message = message,
                )

                if (summary.failedCopies == 0) {
                    Result.success()
                } else {
                    Result.retry()
                }
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
