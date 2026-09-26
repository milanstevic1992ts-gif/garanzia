package com.milanstevic.garanzia.product

import com.milanstevic.garanzia.data.local.ReceiptEntity
import com.milanstevic.garanzia.data.local.ReceiptPageEntity
import com.milanstevic.garanzia.data.local.ReceiptProductEntity
import com.milanstevic.garanzia.data.local.ReceiptWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductDetailMapperTest {

    @Test
    fun mapsProductWithReceiptPurchaseContext() {
        val details = receiptDetails()
        val mapped = ProductDetailMapper.from(
            details = details,
            productId = 11L,
        )

        requireNotNull(mapped)

        assertEquals("receipt-1", mapped.receiptId)
        assertEquals(11L, mapped.productId)
        assertEquals("TRAPANO BOSCH 18V", mapped.name)
        assertEquals("2", mapped.quantity)
        assertEquals("79.95", mapped.unitPrice)
        assertEquals("159.90", mapped.lineTotal)
        assertEquals("FERRAMENTA ROSSI", mapped.merchant)
        assertEquals("2026-09-24", mapped.purchaseDate)
        assertEquals("A-100", mapped.documentNumber)
        assertEquals(0.94f, mapped.sourceConfidence)
        assertEquals(2, mapped.pageCount)
    }

    @Test
    fun unknownProductIdReturnsNull() {
        assertNull(
            ProductDetailMapper.from(
                details = receiptDetails(),
                productId = 999L,
            ),
        )
    }

    @Test
    fun rejectsProductBelongingToAnotherReceipt() {
        val foreign = ReceiptProductEntity(
            id = 55L,
            receiptId = "receipt-other",
            position = 0,
            name = "PRODOTTO ESTERNO",
            quantity = "1",
            unitPrice = "10.00",
            lineTotal = "10.00",
            sourceConfidence = 0.90f,
        )

        assertNull(
            ProductDetailMapper.from(
                details = receiptDetails(),
                product = foreign,
            ),
        )
    }

    private fun receiptDetails(): ReceiptWithDetails =
        ReceiptWithDetails(
            receipt = ReceiptEntity(
                id = "receipt-1",
                merchant = "FERRAMENTA ROSSI",
                purchaseDate = "2026-09-24",
                purchaseTime = "10:30",
                totalAmount = "159.90",
                currency = "EUR",
                vatNumber = "12345678901",
                documentNumber = "A-100",
                paymentMethod = "Carta",
                rawOcrText = "TRAPANO BOSCH 18V",
                confirmedAtEpochMs = 1L,
            ),
            products = listOf(
                ReceiptProductEntity(
                    id = 11L,
                    receiptId = "receipt-1",
                    position = 0,
                    name = "TRAPANO BOSCH 18V",
                    quantity = "2",
                    unitPrice = "79.95",
                    lineTotal = "159.90",
                    sourceConfidence = 0.94f,
                ),
            ),
            pages = listOf(
                ReceiptPageEntity(
                    id = 21L,
                    receiptId = "receipt-1",
                    pageIndex = 0,
                    originalUri = "file:///receipt-1-01.jpg",
                ),
                ReceiptPageEntity(
                    id = 22L,
                    receiptId = "receipt-1",
                    pageIndex = 1,
                    originalUri = "file:///receipt-1-02.jpg",
                ),
            ),
        )
}
