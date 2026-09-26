package com.milanstevic.garanzia.archive

import android.net.Uri
import com.milanstevic.garanzia.confirmation.ReceiptConfirmationDraft
import com.milanstevic.garanzia.data.ReceiptRepository
import com.milanstevic.garanzia.scanner.ReceiptFileStore
import com.milanstevic.garanzia.storage.ReceiptMirrorManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReceiptMutationResult(
    val success: Boolean,
    val warning: String? = null,
)

@Singleton
class ReceiptLifecycleManager @Inject constructor(
    private val repository: ReceiptRepository,
    private val fileStore: ReceiptFileStore,
    private val pdfManager: ReceiptPdfManager,
    private val mirrorManager: ReceiptMirrorManager,
) {
    suspend fun updateReceipt(
        receiptId: String,
        draft: ReceiptConfirmationDraft,
    ): ReceiptMutationResult = withContext(Dispatchers.IO) {
        val before = requireNotNull(repository.getReceipt(receiptId)) {
            "Scontrino non trovato"
        }

        repository.updateConfirmedReceipt(
            receiptId = receiptId,
            draft = draft,
        )

        val externalCleanup = mirrorManager.deleteReceiptCopies(before)
        val failedExternal = externalCleanup.configuredFailures.size

        ReceiptMutationResult(
            success = true,
            warning =
                if (failedExternal > 0) {
                    "$failedExternal vecchie copie esterne verranno eliminate automaticamente appena disponibili."
                } else {
                    null
                },
        )
    }

    suspend fun deleteReceipt(
        receiptId: String,
    ): ReceiptMutationResult = withContext(Dispatchers.IO) {
        val details = requireNotNull(repository.getReceipt(receiptId)) {
            "Scontrino non trovato"
        }

        repository.deleteReceipt(receiptId)

        val localFailures = fileStore.deleteOriginals(
            details.pages.map { Uri.parse(it.originalUri) },
        )
        val pdfDeleted = pdfManager.deleteCachedPdf(receiptId)
        val externalCleanup = mirrorManager.deleteReceiptCopies(details)
        val externalFailures = externalCleanup.configuredFailures.size

        val warnings = buildList {
            if (localFailures > 0) {
                add("$localFailures file originali locali non sono stati eliminati")
            }
            if (!pdfDeleted) {
                add("la cache PDF non è stata eliminata completamente")
            }
            if (externalFailures > 0) {
                add("$externalFailures copie esterne sono in coda di pulizia")
            }
        }

        ReceiptMutationResult(
            success = true,
            warning = warnings.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        )
    }
}
