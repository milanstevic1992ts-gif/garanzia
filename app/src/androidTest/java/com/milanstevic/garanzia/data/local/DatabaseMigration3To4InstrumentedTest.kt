package com.milanstevic.garanzia.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigration3To4InstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        createLegacyV3Database()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrationPreservesWarrantyAndCreatesAttachmentTable() = runBlocking {
        val database = Room.databaseBuilder(
            context,
            GaranziaDatabase::class.java,
            TEST_DATABASE,
        )
            .addMigrations(DatabaseMigrations.MIGRATION_3_4)
            .build()

        try {
            val details = requireNotNull(
                database.receiptDao().getReceipt(RECEIPT_ID),
            )

            val product = details.products.single()
            assertEquals(PRODUCT_ID, product.id)
            assertEquals(24, product.warrantyMonths)
            assertEquals(45, product.warrantyReminderDays)
            assertTrue(product.warrantyNotificationsEnabled)
            assertTrue(details.attachments.isEmpty())

            val attachmentId = database.receiptDao().insertAttachment(
                ProductAttachmentEntity(
                    receiptId = RECEIPT_ID,
                    productId = PRODUCT_ID,
                    category = "serial",
                    localUri = "file:///attachments/serial.jpg",
                    mimeType = "image/jpeg",
                    originalName = "serial.jpg",
                    note = "Matricola sul retro",
                    createdAtEpochMs = 123L,
                ),
            )

            val attachments = database.receiptDao().getProductAttachments(
                receiptId = RECEIPT_ID,
                productId = PRODUCT_ID,
            )
            assertEquals(1, attachments.size)
            assertEquals(attachmentId, attachments.single().id)
            assertEquals("serial", attachments.single().category)
            assertEquals(4, database.openHelper.writableDatabase.version)
        } finally {
            database.close()
        }
    }

    private fun createLegacyV3Database() {
        val file = context.getDatabasePath(TEST_DATABASE)
        file.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS receipts (
                    id TEXT NOT NULL PRIMARY KEY,
                    merchant TEXT NOT NULL,
                    purchaseDate TEXT NOT NULL,
                    purchaseTime TEXT,
                    totalAmount TEXT NOT NULL,
                    currency TEXT,
                    vatNumber TEXT,
                    documentNumber TEXT,
                    paymentMethod TEXT,
                    rawOcrText TEXT,
                    confirmedAtEpochMs INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_receipts_purchaseDate ON receipts(purchaseDate)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_receipts_merchant ON receipts(merchant)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_receipts_confirmedAtEpochMs ON receipts(confirmedAtEpochMs)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS receipt_products (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    receiptId TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    quantity TEXT,
                    unitPrice TEXT,
                    lineTotal TEXT,
                    sourceConfidence REAL,
                    warrantyMonths INTEGER,
                    warrantyReminderDays INTEGER NOT NULL DEFAULT 30,
                    warrantyNotificationsEnabled INTEGER NOT NULL DEFAULT 1,
                    warrantyLastNotificationKey TEXT,
                    FOREIGN KEY(receiptId) REFERENCES receipts(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_receipt_products_receiptId ON receipt_products(receiptId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_receipt_products_name ON receipt_products(name)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_receipt_products_receiptId_position " +
                    "ON receipt_products(receiptId, position)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS receipt_pages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    receiptId TEXT NOT NULL,
                    pageIndex INTEGER NOT NULL,
                    originalUri TEXT NOT NULL,
                    FOREIGN KEY(receiptId) REFERENCES receipts(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_receipt_pages_receiptId ON receipt_pages(receiptId)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_receipt_pages_receiptId_pageIndex " +
                    "ON receipt_pages(receiptId, pageIndex)",
            )

            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS receipt_search
                USING FTS4(
                    receiptId TEXT NOT NULL,
                    searchableText TEXT NOT NULL,
                    tokenize=unicode61
                )
                """.trimIndent(),
            )

            db.execSQL(
                """
                INSERT INTO receipts(
                    id, merchant, purchaseDate, purchaseTime, totalAmount, currency,
                    vatNumber, documentNumber, paymentMethod, rawOcrText, confirmedAtEpochMs
                ) VALUES(
                    '$RECEIPT_ID', 'FERRAMENTA TEST', '2026-09-24', '10:30', '149.90', 'EUR',
                    '12345678901', 'A-100', 'Carta', 'TRAPANO TEST', 1
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO receipt_products(
                    id, receiptId, position, name, quantity, unitPrice, lineTotal, sourceConfidence,
                    warrantyMonths, warrantyReminderDays, warrantyNotificationsEnabled,
                    warrantyLastNotificationKey
                ) VALUES(
                    $PRODUCT_ID, '$RECEIPT_ID', 0, 'TRAPANO TEST', '1', '149.90', '149.90', 0.95,
                    24, 45, 1, NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO receipt_pages(
                    receiptId, pageIndex, originalUri
                ) VALUES(
                    '$RECEIPT_ID', 0, 'file:///legacy/receipt_01.jpg'
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO receipt_search(receiptId, searchableText)
                VALUES('$RECEIPT_ID', 'FERRAMENTA TEST TRAPANO TEST')
                """.trimIndent(),
            )

            db.version = 3
        }
    }

    private companion object {
        const val TEST_DATABASE = "garanzia-migration-v3-v4-test.db"
        const val RECEIPT_ID = "legacy-receipt-3"
        const val PRODUCT_ID = 10L
    }
}
