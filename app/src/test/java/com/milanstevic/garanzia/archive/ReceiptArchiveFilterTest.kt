package com.milanstevic.garanzia.archive

import com.milanstevic.garanzia.data.local.ReceiptEntity
import com.milanstevic.garanzia.data.local.ReceiptPageEntity
import com.milanstevic.garanzia.data.local.ReceiptProductEntity
import com.milanstevic.garanzia.data.local.ReceiptWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptArchiveFilterTest {

    private val receipts = listOf(
        receipt(
            id = "r1",
            merchant = "FERRAMENTA ROSSI SRL",
            purchaseDate = "2026-09-24",
            documentNumber = "A-100",
            rawOcr = "TRAPANO BOSCH 99,90",
            product = "TRAPANO BOSCH",
            confirmedAt = 300L,
        ),
        receipt(
            id = "r2",
            merchant = "BRICO CASA",
            purchaseDate = "2026-08-10",
            documentNumber = "B-200",
            rawOcr = "VERNICE BIANCA 29,90",
            product = "VERNICE BIANCA",
            confirmedAt = 200L,
        ),
        receipt(
            id = "r3",
            merchant = "CASA UTENSILI",
            purchaseDate = "2026-07-01",
            documentNumber = "C-300",
            rawOcr = "SET PUNTE HSS 14,50",
            product = "SET PUNTE HSS",
            confirmedAt = 100L,
        ),
    )

    @Test
    fun searchesMerchantCaseInsensitively() {
        val result = ReceiptArchiveFilter.apply(
            receipts,
            ArchiveFilterState(query = "ferramenta"),
        )

        assertEquals(listOf("r1"), result.map { it.receipt.id })
    }

    @Test
    fun searchesProductDocumentRawOcrAndItalianDate() {
        assertEquals(
            listOf("r1"),
            ReceiptArchiveFilter.apply(
                receipts,
                ArchiveFilterState(query = "trapano"),
            ).map { it.receipt.id },
        )
        assertEquals(
            listOf("r2"),
            ReceiptArchiveFilter.apply(
                receipts,
                ArchiveFilterState(query = "B-200"),
            ).map { it.receipt.id },
        )
        assertEquals(
            listOf("r3"),
            ReceiptArchiveFilter.apply(
                receipts,
                ArchiveFilterState(query = "SET PUNTE"),
            ).map { it.receipt.id },
        )
        assertEquals(
            listOf("r1"),
            ReceiptArchiveFilter.apply(
                receipts,
                ArchiveFilterState(query = "24/09/2026"),
            ).map { it.receipt.id },
        )
    }

    @Test
    fun usesFtsMatchingIdsWhenProvided() {
        val result = ReceiptArchiveFilter.apply(
            receipts = receipts,
            state = ArchiveFilterState(query = "qualunque testo"),
            matchingIds = setOf("r2"),
        )

        assertEquals(listOf("r2"), result.map { it.receipt.id })
    }

    @Test
    fun dateRangeIsInclusive() {
        val result = ReceiptArchiveFilter.apply(
            receipts,
            ArchiveFilterState(
                fromDate = "01/08/2026",
                toDate = "24/09/2026",
            ),
        )

        assertEquals(listOf("r1", "r2"), result.map { it.receipt.id })
    }

    @Test
    fun invalidDateOrReversedRangeReturnsNoResults() {
        val invalidDate = ArchiveFilterState(fromDate = "31/02/2026")
        val reversed = ArchiveFilterState(
            fromDate = "24/09/2026",
            toDate = "01/09/2026",
        )

        assertTrue(invalidDate.hasInvalidDate)
        assertTrue(reversed.hasInvalidRange)
        assertTrue(ReceiptArchiveFilter.apply(receipts, invalidDate).isEmpty())
        assertTrue(ReceiptArchiveFilter.apply(receipts, reversed).isEmpty())
    }

    @Test
    fun supportsNewestAndOldestOrdering() {
        val newest = ReceiptArchiveFilter.apply(
            receipts,
            ArchiveFilterState(sort = ArchiveSort.NEWEST),
        )
        val oldest = ReceiptArchiveFilter.apply(
            receipts,
            ArchiveFilterState(sort = ArchiveSort.OLDEST),
        )

        assertEquals(listOf("r1", "r2", "r3"), newest.map { it.receipt.id })
        assertEquals(listOf("r3", "r2", "r1"), oldest.map { it.receipt.id })
    }

    private fun receipt(
        id: String,
        merchant: String,
        purchaseDate: String,
        documentNumber: String,
        rawOcr: String,
        product: String,
        confirmedAt: Long,
    ) = ReceiptWithDetails(
        receipt = ReceiptEntity(
            id = id,
            merchant = merchant,
            purchaseDate = purchaseDate,
            purchaseTime = "10:00",
            totalAmount = "99.90",
            currency = "EUR",
            vatNumber = null,
            documentNumber = documentNumber,
            paymentMethod = "Carta",
            rawOcrText = rawOcr,
            confirmedAtEpochMs = confirmedAt,
        ),
        products = listOf(
            ReceiptProductEntity(
                id = confirmedAt,
                receiptId = id,
                position = 0,
                name = product,
                quantity = "1",
                unitPrice = "99.90",
                lineTotal = "99.90",
                sourceConfidence = 0.95f,
            ),
        ),
        pages = listOf(
            ReceiptPageEntity(
                id = confirmedAt,
                receiptId = id,
                pageIndex = 0,
                originalUri = "file:///receipt/$id.jpg",
            ),
        ),
    )
}
