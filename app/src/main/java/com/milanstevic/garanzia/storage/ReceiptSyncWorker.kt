package com.milanstevic.garanzia.storage

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.milanstevic.garanzia.data.ReceiptRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class ReceiptSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ReceiptRepository,
    private val mirrorManager: ReceiptMirrorManager,
    private val storageSettings: StorageSettings,
    private val syncStatusStore: SyncStatusStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
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
