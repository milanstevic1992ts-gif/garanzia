package com.milanstevic.garanzia.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigration2To3InstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        createLegacyV2Database()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrationPreservesProductAndAddsWarrantyDefaults() = runBlocking {
        val database = Room.databaseBuilder(
            context,
            GaranziaDatabase::class.java,
            TEST_DATABASE,
        )
            .addMigrations(DatabaseMigrations.MIGRATION_2_3)
            .build()

        try {
            val product = requireNotNull(
                database.receiptDao()
                    .getReceipt(RECEIPT_ID)
                    ?.products
                    ?.single(),
            )

            assertEquals("TRAPANO BOSCH 18V", product.name)
            assertNull(product.warrantyMonths)
            assertEquals(30, product.warrantyReminderDays)
            assertTrue(product.warrantyNotificationsEnabled)
            assertNull(product.warrantyLastNotificationKey)
            assertEquals(3, database.openHelper.writableDatabase.version)
        } finally {
            database.close()
        }
    }

    private fun createLegacyV2Database() {
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
                    '$RECEIPT_ID', 'FERRAMENTA ROSSI', '2026-09-24', '10:30', '149.90', 'EUR',
                    '12345678901', 'A-100', 'Carta', 'TRAPANO BOSCH 18V', 1
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO receipt_products(
                    id, receiptId, position, name, quantity, unitPrice, lineTotal, sourceConfidence
                ) VALUES(
                    10, '$RECEIPT_ID', 0, 'TRAPANO BOSCH 18V', '1', '149.90', '149.90', 0.95
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
                VALUES('$RECEIPT_ID', 'FERRAMENTA ROSSI TRAPANO BOSCH 18V')
                """.trimIndent(),
            )

            db.version = 2
        }
    }

    private companion object {
        const val TEST_DATABASE = "garanzia-migration-v2-v3-test.db"
        const val RECEIPT_ID = "legacy-receipt-2"
    }
}
