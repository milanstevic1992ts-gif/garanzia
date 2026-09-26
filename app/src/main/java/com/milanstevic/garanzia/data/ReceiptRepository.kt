package com.milanstevic.garanzia.data

import android.net.Uri
import com.milanstevic.garanzia.confirmation.ReceiptConfirmationDraft
import com.milanstevic.garanzia.data.local.ReceiptDao
import com.milanstevic.garanzia.data.local.ReceiptEntity
import com.milanstevic.garanzia.data.local.ReceiptPageEntity
import com.milanstevic.garanzia.data.local.ReceiptProductEntity
import com.milanstevic.garanzia.data.local.ReceiptWithDetails
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

data class ReceiptArchiveState(
    val receipts: List<ReceiptWithDetails> = emptyList(),
    val error: String? = null,
)

@Singleton
class ReceiptRepository @Inject constructor(
    private val receiptDao: ReceiptDao,
) {
    suspend fun saveConfirmedReceipt(
        draft: ReceiptConfirmationDraft,
        originalUris: List<Uri>,
        rawOcrText: String?,
        confirmedAtEpochMs: Long = System.currentTimeMillis(),
    ): String {
        require(draft.canConfirm) { "Receipt confirmation is not valid" }
        require(originalUris.isNotEmpty()) { "At least one original receipt page is required" }

        val receiptId = UUID.randomUUID().toString()
        val purchaseDate = LocalDate
            .parse(draft.purchaseDate.trim(), DATE_FORMAT)
            .toString()

        val receipt = ReceiptEntity(
            id = receiptId,
            merchant = draft.merchant.trim(),
            purchaseDate = purchaseDate,
            purchaseTime = draft.purchaseTime.trim().ifBlank { null },
            totalAmount = requireNotNull(
                ReceiptConfirmationDraft.parseMoney(draft.totalAmount),
            ).toPlainString(),
            currency = draft.currency.trim().uppercase().ifBlank { null },
            vatNumber = draft.vatNumber.trim().ifBlank { null },
            documentNumber = draft.documentNumber.trim().ifBlank { null },
            paymentMethod = draft.paymentMethod.trim().ifBlank { null },
            rawOcrText = rawOcrText?.trim()?.ifBlank { null },
            confirmedAtEpochMs = confirmedAtEpochMs,
        )

        val products = draft.products.mapIndexed { index, product ->
            ReceiptProductEntity(
                receiptId = receiptId,
                position = index,
                name = product.name.trim(),
                quantity = product.quantity
                    .takeIf(String::isNotBlank)
                    ?.let(ReceiptConfirmationDraft::parseQuantity)
                    ?.stripTrailingZeros()
                    ?.toPlainString(),
                unitPrice = product.unitPrice
                    .takeIf(String::isNotBlank)
                    ?.let(ReceiptConfirmationDraft::parseMoney)
                    ?.toPlainString(),
                lineTotal = product.lineTotal
                    .takeIf(String::isNotBlank)
                    ?.let(ReceiptConfirmationDraft::parseMoney)
                    ?.toPlainString(),
                sourceConfidence = product.sourceConfidence,
            )
        }

        val pages = originalUris.mapIndexed { index, uri ->
            ReceiptPageEntity(
                receiptId = receiptId,
                pageIndex = index,
                originalUri = uri.toString(),
            )
        }

        receiptDao.insertReceiptGraph(
            receipt = receipt,
            products = products,
            pages = pages,
        )

        checkNotNull(receiptDao.getReceipt(receiptId)) {
            "Lo scontrino non risulta presente dopo il salvataggio"
        }

        return receiptId
    }

    fun observeArchiveState(): Flow<ReceiptArchiveState> =
        flow {
            while (true) {
                try {
                    receiptDao.observeReceipts().collect { receipts ->
                        emit(
                            ReceiptArchiveState(
                                receipts = receipts,
                                error = null,
                            ),
                        )
                    }
                    return@flow
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t

                    emit(
                        ReceiptArchiveState(
                            receipts = emptyList(),
                            error = t.message ?: "Errore lettura archivio",
                        ),
                    )
                    delay(DATABASE_RETRY_MS)
                }
            }
        }

    fun observeReceipts(): Flow<List<ReceiptWithDetails>> =
        flow {
            observeArchiveState().collect { state ->
                emit(state.receipts)
            }
        }

    fun observeReceiptCount(): Flow<Int> =
        flow {
            observeArchiveState().collect { state ->
                emit(state.receipts.size)
            }
        }

    suspend fun getReceipt(receiptId: String): ReceiptWithDetails? =
        receiptDao.getReceipt(receiptId)

    suspend fun deleteReceipt(receiptId: String) {
        receiptDao.deleteReceipt(receiptId)
    }

    private companion object {
        const val DATABASE_RETRY_MS = 2_000L

        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT)
    }
}
