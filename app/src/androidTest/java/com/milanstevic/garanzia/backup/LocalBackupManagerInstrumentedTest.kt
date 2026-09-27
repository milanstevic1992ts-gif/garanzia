package com.milanstevic.garanzia.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.milanstevic.garanzia.archive.ReceiptPdfManager
import com.milanstevic.garanzia.data.ReceiptRepository
import com.milanstevic.garanzia.data.local.GaranziaDatabase
import com.milanstevic.garanzia.data.local.ProductAttachmentEntity
import com.milanstevic.garanzia.data.local.ReceiptEntity
import com.milanstevic.garanzia.data.local.ReceiptPageEntity
import com.milanstevic.garanzia.data.local.ReceiptProductEntity
import com.milanstevic.garanzia.data.local.ReceiptWithDetails
import com.milanstevic.garanzia.product.attachment.ProductAttachmentStore
import com.milanstevic.garanzia.scanner.ReceiptFileStore
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalBackupManagerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var database: GaranziaDatabase
    private lateinit var repository: ReceiptRepository
    private lateinit var fileStore: ReceiptFileStore
    private lateinit var attachmentStore: ProductAttachmentStore
    private lateinit var manager: LocalBackupManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "receipts").deleteRecursively()
        File(context.filesDir, "product_attachments").deleteRecursively()
        File(context.cacheDir, "receipt_pdfs").deleteRecursively()

        database = Room.inMemoryDatabaseBuilder(
            context,
            GaranziaDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = ReceiptRepository(database.receiptDao())
        fileStore = ReceiptFileStore(context)
        attachmentStore = ProductAttachmentStore(context)
        manager = LocalBackupManager(
            context = context,
            repository = repository,
            fileStore = fileStore,
            productAttachmentStore = attachmentStore,
            pdfManager = ReceiptPdfManager(context),
        )
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "receipts").deleteRecursively()
        File(context.filesDir, "product_attachments").deleteRecursively()
        File(context.cacheDir, "receipt_pdfs").deleteRecursively()
        File(context.cacheDir, VALID_BACKUP).delete()
        File(context.cacheDir, TAMPERED_BACKUP).delete()
        File(context.cacheDir, TAMPERED_ATTACHMENT_BACKUP).delete()
    }

    @Test
    fun backupAndRestorePreserveArchiveWarrantyAndOriginalBytes() = runBlocking {
        repository.replaceArchive(
            listOf(
                receiptGraph(
                    receiptId = "receipt-backup",
                    merchant = "FERRAMENTA TEST",
                    originalBytes = ORIGINAL_BYTES,
                ),
            ),
        )

        val backup = File(context.cacheDir, VALID_BACKUP)
        val summary = manager.createBackup(Uri.fromFile(backup))

        assertEquals(1, summary.receiptCount)
        assertEquals(1, summary.productCount)
        assertEquals(1, summary.pageCount)
        assertEquals(1, summary.attachmentCount)
        assertTrue(backup.length() > 0L)

        repository.replaceArchive(emptyList())
        assertEquals(0, repository.getAllReceipts().size)

        val preview = manager.inspectBackup(Uri.fromFile(backup))
        assertEquals(1, preview.receiptCount)
        assertEquals(1, preview.productCount)
        assertEquals(1, preview.pageCount)
        assertEquals(1, preview.attachmentCount)

        val restored = manager.restoreBackup(Uri.fromFile(backup))
        assertEquals(1, restored.receiptCount)
        assertEquals(1, restored.attachmentCount)

        val stored = requireNotNull(repository.getReceipt("receipt-backup"))
        assertEquals("FERRAMENTA TEST", stored.receipt.merchant)
        assertEquals(24, stored.products.single().warrantyMonths)
        assertEquals(45, stored.products.single().warrantyReminderDays)
        assertEquals(true, stored.products.single().warrantyNotificationsEnabled)

        val restoredUri = Uri.parse(stored.pages.single().originalUri)
        val restoredFile = File(requireNotNull(restoredUri.path))
        assertTrue(restoredFile.isFile)
        assertTrue(ORIGINAL_BYTES.contentEquals(restoredFile.readBytes()))

        val restoredAttachment = stored.attachments.single()
        assertEquals("box", restoredAttachment.category)
        assertEquals("Foto confezione", restoredAttachment.note)
        val restoredAttachmentFile = File(
            requireNotNull(Uri.parse(restoredAttachment.localUri).path),
        )
        assertTrue(restoredAttachmentFile.isFile)
        assertTrue(ATTACHMENT_BYTES.contentEquals(restoredAttachmentFile.readBytes()))
    }

    @Test
    fun tamperedPageIsRejectedWithoutReplacingCurrentArchive() = runBlocking {
        repository.replaceArchive(
            listOf(
                receiptGraph(
                    receiptId = "receipt-backup",
                    merchant = "ARCHIVIO DA BACKUP",
                    originalBytes = ORIGINAL_BYTES,
                ),
            ),
        )

        val valid = File(context.cacheDir, VALID_BACKUP)
        manager.createBackup(Uri.fromFile(valid))

        repository.replaceArchive(
            listOf(
                receiptGraph(
                    receiptId = "current-receipt",
                    merchant = "ARCHIVIO CORRENTE",
                    originalBytes = CURRENT_BYTES,
                    productId = 101L,
                    pageId = 102L,
                ),
            ),
        )

        val tampered = File(context.cacheDir, TAMPERED_BACKUP)
        tamperFirstMatchingFile(
            source = valid,
            target = tampered,
            prefix = "originals/",
        )

        val failure = runCatching {
            manager.restoreBackup(Uri.fromFile(tampered))
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(
            failure?.message.orEmpty().contains("Checksum", ignoreCase = true),
        )

        val current = repository.getAllReceipts()
        assertEquals(1, current.size)
        assertEquals("current-receipt", current.single().receipt.id)
        assertEquals("ARCHIVIO CORRENTE", current.single().receipt.merchant)
    }

    @Test
    fun tamperedAttachmentIsRejectedWithoutReplacingCurrentArchive() = runBlocking {
        repository.replaceArchive(
            listOf(
                receiptGraph(
                    receiptId = "receipt-backup",
                    merchant = "ARCHIVIO DA BACKUP",
                    originalBytes = ORIGINAL_BYTES,
                ),
            ),
        )

        val valid = File(context.cacheDir, VALID_BACKUP)
        manager.createBackup(Uri.fromFile(valid))

        repository.replaceArchive(
            listOf(
                receiptGraph(
                    receiptId = "current-receipt",
                    merchant = "ARCHIVIO CORRENTE",
                    originalBytes = CURRENT_BYTES,
                    productId = 201L,
                    pageId = 202L,
                    attachmentId = 203L,
                ),
            ),
        )

        val tampered = File(context.cacheDir, TAMPERED_ATTACHMENT_BACKUP)
        tamperFirstMatchingFile(
            source = valid,
            target = tampered,
            prefix = "attachments/",
        )

        val failure = runCatching {
            manager.restoreBackup(Uri.fromFile(tampered))
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(
            failure?.message.orEmpty().contains("Checksum", ignoreCase = true),
        )

        val current = repository.getAllReceipts()
        assertEquals(1, current.size)
        assertEquals("current-receipt", current.single().receipt.id)
        assertEquals("ARCHIVIO CORRENTE", current.single().receipt.merchant)
        assertEquals(1, current.single().attachments.size)
    }

    private fun receiptGraph(
        receiptId: String,
        merchant: String,
        originalBytes: ByteArray,
        productId: Long = 11L,
        pageId: Long = 21L,
        attachmentId: Long = 31L,
    ): ReceiptWithDetails {
        val originalUri = fileStore.importRestoredOriginal(
            ByteArrayInputStream(originalBytes),
        )
        val attachmentFile = attachmentStore.importRestoredAttachment(
            input = ByteArrayInputStream(ATTACHMENT_BYTES),
            mimeType = "image/jpeg",
            originalName = "box.jpg",
        )

        return ReceiptWithDetails(
            receipt = ReceiptEntity(
                id = receiptId,
                merchant = merchant,
                purchaseDate = "2026-09-24",
                purchaseTime = "10:30",
                totalAmount = "149.90",
                currency = "EUR",
                vatNumber = "12345678901",
                documentNumber = "A-100",
                paymentMethod = "Carta",
                rawOcrText = "TRAPANO TEST",
                confirmedAtEpochMs = 100L,
            ),
            products = listOf(
                ReceiptProductEntity(
                    id = productId,
                    receiptId = receiptId,
                    position = 0,
                    name = "TRAPANO TEST",
                    quantity = "1",
                    unitPrice = "149.90",
                    lineTotal = "149.90",
                    sourceConfidence = 0.95f,
                    warrantyMonths = 24,
                    warrantyReminderDays = 45,
                    warrantyNotificationsEnabled = true,
                    warrantyLastNotificationKey = null,
                ),
            ),
            pages = listOf(
                ReceiptPageEntity(
                    id = pageId,
                    receiptId = receiptId,
                    pageIndex = 0,
                    originalUri = originalUri.toString(),
                ),
            ),
            attachments = listOf(
                ProductAttachmentEntity(
                    id = attachmentId,
                    receiptId = receiptId,
                    productId = productId,
                    category = "box",
                    localUri = attachmentFile.uri.toString(),
                    mimeType = "image/jpeg",
                    originalName = "box.jpg",
                    note = "Foto confezione",
                    createdAtEpochMs = 150L,
                ),
            ),
        )
    }

    private fun tamperFirstMatchingFile(
        source: File,
        target: File,
        prefix: String,
    ) {
        var tampered = false

        ZipInputStream(source.inputStream().buffered()).use { input ->
            ZipOutputStream(target.outputStream().buffered()).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val bytes = input.readBytes()

                    output.putNextEntry(ZipEntry(entry.name))
                    if (
                        !tampered &&
                        entry.name.startsWith(prefix) &&
                        bytes.isNotEmpty()
                    ) {
                        val changed = bytes.copyOf()
                        changed[0] = (changed[0].toInt() xor 0x01).toByte()
                        output.write(changed)
                        tampered = true
                    } else {
                        output.write(bytes)
                    }
                    output.closeEntry()
                }
            }
        }

        assertTrue(tampered)
    }

    private companion object {
        const val VALID_BACKUP = "backup-valid-test.zip"
        const val TAMPERED_BACKUP = "backup-tampered-test.zip"
        const val TAMPERED_ATTACHMENT_BACKUP = "backup-tampered-attachment-test.zip"
        val ORIGINAL_BYTES = "original-receipt-image".toByteArray()
        val CURRENT_BYTES = "current-receipt-image".toByteArray()
        val ATTACHMENT_BYTES = "product-attachment-image".toByteArray()
    }
}
