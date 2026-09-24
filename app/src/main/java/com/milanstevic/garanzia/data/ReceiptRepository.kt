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
import kotlinx.coroutines.flow.Flow

@Singleton
class ReceiptRepository @Inject constructor(
    private val receiptDao: ReceiptDao,
) {
    suspend fun saveConfirmedReceipt(
        draft: ReceiptConfirmationDraft,
        originalUris: List<Uri>,
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

        return receiptId
    }

    fun observeReceipts(): Flow<List<ReceiptWithDetails>> =
        receiptDao.observeReceipts()

    fun observeReceiptCount(): Flow<Int> =
        receiptDao.observeReceiptCount()

    suspend fun getReceipt(receiptId: String): ReceiptWithDetails? =
        receiptDao.getReceipt(receiptId)

    suspend fun deleteReceipt(receiptId: String) {
        receiptDao.deleteReceipt(receiptId)
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT)
    }
}
