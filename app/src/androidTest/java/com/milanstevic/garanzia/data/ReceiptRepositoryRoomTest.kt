package com.milanstevic.garanzia.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.milanstevic.garanzia.confirmation.ProductConfirmationDraft
import com.milanstevic.garanzia.confirmation.ReceiptConfirmationDraft
import com.milanstevic.garanzia.data.local.GaranziaDatabase
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
            confirmedAtEpochMs = 1_000L,
        )

        val stored = repository.getReceipt(receiptId)

        requireNotNull(stored)
        assertEquals("FERRAMENTA ROSSI SRL", stored.receipt.merchant)
        assertEquals("2026-09-24", stored.receipt.purchaseDate)
        assertEquals("149.80", stored.receipt.totalAmount)
        assertEquals(2, stored.products.size)
        assertEquals("TRAPANO BOSCH", stored.products.minBy { it.position }.name)
        assertEquals(2, stored.pages.size)
        assertEquals("file:///receipt/page1.jpg", stored.pages.minBy { it.pageIndex }.originalUri)
    }

    @Test
    fun deletingReceiptCascadesProductsAndPages() = runBlocking {
        val receiptId = repository.saveConfirmedReceipt(
            draft = validDraft(),
            originalUris = listOf(Uri.parse("file:///receipt/page1.jpg")),
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
