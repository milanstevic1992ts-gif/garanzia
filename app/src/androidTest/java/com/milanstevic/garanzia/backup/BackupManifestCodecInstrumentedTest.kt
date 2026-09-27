package com.milanstevic.garanzia.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.milanstevic.garanzia.data.local.ReceiptEntity
import com.milanstevic.garanzia.data.local.ReceiptProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupManifestCodecInstrumentedTest {

    @Test
    fun manifestRoundTripPreservesReceiptProductWarrantyAndPageMetadata() {
        val manifest = BackupManifest(
            format = "garanzia-backup",
            formatVersion = 1,
            databaseSchemaVersion = 3,
            createdAtEpochMs = 123456789L,
            receipts = listOf(
                BackupReceiptRecord(
                    receipt = ReceiptEntity(
                        id = "receipt-1",
                        merchant = "FERRAMENTA ROSSI",
                        purchaseDate = "2026-09-24",
                        purchaseTime = "10:30",
                        totalAmount = "149.90",
                        currency = "EUR",
                        vatNumber = "12345678901",
                        documentNumber = "A-100",
                        paymentMethod = "Carta",
                        rawOcrText = "TRAPANO BOSCH 18V",
                        confirmedAtEpochMs = 999L,
                    ),
                    products = listOf(
                        ReceiptProductEntity(
                            id = 11L,
                            receiptId = "receipt-1",
                            position = 0,
                            name = "TRAPANO BOSCH 18V",
                            quantity = "1",
                            unitPrice = "149.90",
                            lineTotal = "149.90",
                            sourceConfidence = 0.94f,
                            warrantyMonths = 24,
                            warrantyReminderDays = 45,
                            warrantyNotificationsEnabled = true,
                            warrantyLastNotificationKey = "expiring:2028-09-24",
                        ),
                    ),
                    pages = listOf(
                        BackupPageRecord(
                            id = 21L,
                            receiptId = "receipt-1",
                            pageIndex = 0,
                            entryName = "originals/receipt-1/page_0.jpg",
                            sizeBytes = 321L,
                            sha256 = "a".repeat(64),
                        ),
                    ),
                ),
            ),
        )

        val decoded = BackupManifestCodec.decode(
            BackupManifestCodec.encode(manifest),
        )

        assertEquals("garanzia-backup", decoded.format)
        assertEquals(1, decoded.formatVersion)
        assertEquals(3, decoded.databaseSchemaVersion)
        assertEquals(1, decoded.receiptCount)
        assertEquals(1, decoded.productCount)
        assertEquals(1, decoded.pageCount)

        val record = decoded.receipts.single()
        assertEquals("FERRAMENTA ROSSI", record.receipt.merchant)
        assertEquals("TRAPANO BOSCH 18V", record.products.single().name)
        assertEquals(24, record.products.single().warrantyMonths)
        assertEquals(45, record.products.single().warrantyReminderDays)
        assertEquals(true, record.products.single().warrantyNotificationsEnabled)
        assertEquals(
            "expiring:2028-09-24",
            record.products.single().warrantyLastNotificationKey,
        )
        assertEquals("originals/receipt-1/page_0.jpg", record.pages.single().entryName)
        assertEquals("a".repeat(64), record.pages.single().sha256)
    }

    @Test
    fun nullableFieldsStayNullAfterRoundTrip() {
        val manifest = BackupManifest(
            format = "garanzia-backup",
            formatVersion = 1,
            databaseSchemaVersion = 3,
            createdAtEpochMs = 1L,
            receipts = listOf(
                BackupReceiptRecord(
                    receipt = ReceiptEntity(
                        id = "receipt-null",
                        merchant = "NEGOZIO",
                        purchaseDate = "2026-01-01",
                        purchaseTime = null,
                        totalAmount = "10.00",
                        currency = null,
                        vatNumber = null,
                        documentNumber = null,
                        paymentMethod = null,
                        rawOcrText = null,
                        confirmedAtEpochMs = 1L,
                    ),
                    products = listOf(
                        ReceiptProductEntity(
                            id = 1L,
                            receiptId = "receipt-null",
                            position = 0,
                            name = "PRODOTTO",
                            quantity = null,
                            unitPrice = null,
                            lineTotal = null,
                            sourceConfidence = null,
                            warrantyMonths = null,
                            warrantyReminderDays = 30,
                            warrantyNotificationsEnabled = false,
                            warrantyLastNotificationKey = null,
                        ),
                    ),
                    pages = listOf(
                        BackupPageRecord(
                            id = 1L,
                            receiptId = "receipt-null",
                            pageIndex = 0,
                            entryName = "originals/receipt-null/page_0.jpg",
                            sizeBytes = 1L,
                            sha256 = "b".repeat(64),
                        ),
                    ),
                ),
            ),
        )

        val decoded = BackupManifestCodec.decode(
            BackupManifestCodec.encode(manifest),
        )
        val product = decoded.receipts.single().products.single()

        assertNull(decoded.receipts.single().receipt.purchaseTime)
        assertNull(decoded.receipts.single().receipt.rawOcrText)
        assertNull(product.sourceConfidence)
        assertNull(product.warrantyMonths)
        assertNull(product.warrantyLastNotificationKey)
    }
}
