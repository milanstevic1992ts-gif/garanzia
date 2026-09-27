package com.milanstevic.garanzia.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.milanstevic.garanzia.confirmation.ProductConfirmationDraft
import com.milanstevic.garanzia.confirmation.ReceiptConfirmationDraft
import com.milanstevic.garanzia.data.local.GaranziaDatabase
import com.milanstevic.garanzia.data.local.ReceiptEntity
import com.milanstevic.garanzia.data.local.ReceiptPageEntity
import com.milanstevic.garanzia.data.local.ReceiptProductEntity
import com.milanstevic.garanzia.data.local.ReceiptWithDetails
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptRepositoryRoomTest {

    private lateinit var database: GaranziaDatabase
    private lateinit var repository: ReceiptRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            GaranziaDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = ReceiptRepository(database.receiptDao())
    }

    @After
    @Throws(IOException::class)
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun confirmedReceiptPersistsReceiptProductsAndOriginalPages() = runBlocking {
        val receiptId = repository.saveConfirmedReceipt(
            draft = validDraft(),
            originalUris = listOf(
                Uri.parse("file:///receipt/page1.jpg"),
                Uri.parse("file:///receipt/page2.jpg"),
            ),
            rawOcrText = "FERRAMENTA ROSSI SRL\\nTRAPANO BOSCH 99,90",
            confirmedAtEpochMs = 1_000L,
        )

        val stored = repository.getReceipt(receiptId)

        requireNotNull(stored)
        assertEquals("FERRAMENTA ROSSI SRL", stored.receipt.merchant)
        assertEquals("2026-09-24", stored.receipt.purchaseDate)
        assertEquals("149.80", stored.receipt.totalAmount)
        assertEquals("FERRAMENTA ROSSI SRL\\nTRAPANO BOSCH 99,90", stored.receipt.rawOcrText)
        assertEquals(2, stored.products.size)
        assertEquals("TRAPANO BOSCH", stored.products.minBy { it.position }.name)
        assertEquals(2, stored.pages.size)
        assertEquals("file:///receipt/page1.jpg", stored.pages.minBy { it.pageIndex }.originalUri)
    }

    @Test
    fun replaceArchiveRestoresExactProductWarrantyAndPageIds() = runBlocking {
        val restored = ReceiptWithDetails(
            receipt = ReceiptEntity(
                id = "restored-receipt",
                merchant = "NEGOZIO TEST",
                purchaseDate = "2026-01-15",
                purchaseTime = null,
                totalAmount = "89.90",
                currency = "EUR",
                vatNumber = null,
                documentNumber = "R-1",
                paymentMethod = "Carta",
                rawOcrText = "PRODOTTO TEST",
                confirmedAtEpochMs = 55L,
            ),
            products = listOf(
                ReceiptProductEntity(
                    id = 77L,
                    receiptId = "restored-receipt",
                    position = 0,
                    name = "PRODOTTO TEST",
                    quantity = "1",
                    unitPrice = "89.90",
                    lineTotal = "89.90",
                    sourceConfidence = 0.98f,
                    warrantyMonths = 24,
                    warrantyReminderDays = 45,
                    warrantyNotificationsEnabled = true,
                    warrantyLastNotificationKey = "expiring:2028-01-15",
                ),
            ),
            pages = listOf(
                ReceiptPageEntity(
                    id = 88L,
                    receiptId = "restored-receipt",
                    pageIndex = 0,
                    originalUri = "file:///restored/page.jpg",
                ),
            ),
        )

        repository.replaceArchive(listOf(restored))

        val stored = requireNotNull(repository.getReceipt("restored-receipt"))
        assertEquals(77L, stored.products.single().id)
        assertEquals(24, stored.products.single().warrantyMonths)
        assertEquals(45, stored.products.single().warrantyReminderDays)
        assertEquals("expiring:2028-01-15", stored.products.single().warrantyLastNotificationKey)
        assertEquals(88L, stored.pages.single().id)
        assertEquals("file:///restored/page.jpg", stored.pages.single().originalUri)
    }

    @Test
    fun deletingReceiptCascadesProductsAndPages() = runBlocking {
        val receiptId = repository.saveConfirmedReceipt(
            draft = validDraft(),
            originalUris = listOf(Uri.parse("file:///receipt/page1.jpg")),
            rawOcrText = "OCR TEST",
            confirmedAtEpochMs = 1_000L,
        )

        assertEquals(1, database.receiptDao().receiptCount())

        repository.deleteReceipt(receiptId)

        assertEquals(0, database.receiptDao().receiptCount())
        assertNull(repository.getReceipt(receiptId))
    }

    private fun validDraft() = ReceiptConfirmationDraft(
        merchant = "FERRAMENTA ROSSI SRL",
        purchaseDate = "24/09/2026",
        purchaseTime = "10:30",
        totalAmount = "149,80",
        currency = "EUR",
        vatNumber = "IT12345678901",
        documentNumber = "1234",
        paymentMethod = "Carta",
        products = listOf(
            ProductConfirmationDraft(
                name = "TRAPANO BOSCH",
                quantity = "1",
                unitPrice = "99,90",
                lineTotal = "99,90",
                requiresReview = false,
                sourceConfidence = 0.95f,
            ),
            ProductConfirmationDraft(
                name = "BATTERIA 18V",
                quantity = "1",
                unitPrice = "49,90",
                lineTotal = "49,90",
                requiresReview = false,
                sourceConfidence = 0.92f,
            ),
        ),
        fieldsToReview = emptySet(),
    )
}
