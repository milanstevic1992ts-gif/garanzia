package com.milanstevic.garanzia.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigration1To2InstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        createLegacyV1Database()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrationPreservesLegacyReceiptAndBuildsFtsIndex() = runBlocking {
        val database = Room.databaseBuilder(
            context,
            GaranziaDatabase::class.java,
            TEST_DATABASE,
        )
            .addMigrations(DatabaseMigrations.MIGRATION_1_2)
            .build()

        try {
            val dao = database.receiptDao()
            val migrated = dao.getReceipt(RECEIPT_ID)

            assertNotNull(migrated)
            requireNotNull(migrated)

            assertEquals("FERRAMENTA ROSSI SRL", migrated.receipt.merchant)
            assertEquals("2026-09-24", migrated.receipt.purchaseDate)
            assertEquals("149.90", migrated.receipt.totalAmount)
            assertEquals("A-100", migrated.receipt.documentNumber)
            assertEquals("12345678901", migrated.receipt.vatNumber)

            assertEquals(1, migrated.products.size)
            assertEquals("TRAPANO BOSCH 18V", migrated.products.single().name)
            assertEquals("1", migrated.products.single().quantity)

            assertEquals(1, migrated.pages.size)
            assertEquals(
                "file:///legacy/receipt_01.jpg",
                migrated.pages.single().originalUri,
            )

            assertEquals(
                listOf(RECEIPT_ID),
                dao.searchReceiptIds(requireNotNull(ReceiptFtsQuery.build("bosch"))),
            )
            assertEquals(
                listOf(RECEIPT_ID),
                dao.searchReceiptIds(requireNotNull(ReceiptFtsQuery.build("A-100"))),
            )
            assertEquals(
                listOf(RECEIPT_ID),
                dao.searchReceiptIds(requireNotNull(ReceiptFtsQuery.build("24/09/2026"))),
            )

            assertTrue(database.openHelper.writableDatabase.version == 2)
        } finally {
            database.close()
        }
    }

    private fun createLegacyV1Database() {
        val file = context.getDatabasePath(TEST_DATABASE)
        file.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `receipts` (
                    `id` TEXT NOT NULL,
                    `merchant` TEXT NOT NULL,
                    `purchaseDate` TEXT NOT NULL,
                    `purchaseTime` TEXT,
                    `totalAmount` TEXT NOT NULL,
                    `currency` TEXT,
                    `vatNumber` TEXT,
                    `documentNumber` TEXT,
                    `paymentMethod` TEXT,
                    `rawOcrText` TEXT,
                    `confirmedAtEpochMs` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_receipts_purchaseDate` " +
                    "ON `receipts` (`purchaseDate`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_receipts_merchant` " +
                    "ON `receipts` (`merchant`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_receipts_confirmedAtEpochMs` " +
                    "ON `receipts` (`confirmedAtEpochMs`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `receipt_products` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `receiptId` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `quantity` TEXT,
                    `unitPrice` TEXT,
                    `lineTotal` TEXT,
                    `sourceConfidence` REAL,
                    FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_receipt_products_receiptId` " +
                    "ON `receipt_products` (`receiptId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_receipt_products_name` " +
                    "ON `receipt_products` (`name`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_receipt_products_receiptId_position` " +
                    "ON `receipt_products` (`receiptId`, `position`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `receipt_pages` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `receiptId` TEXT NOT NULL,
                    `pageIndex` INTEGER NOT NULL,
                    `originalUri` TEXT NOT NULL,
                    FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_receipt_pages_receiptId` " +
                    "ON `receipt_pages` (`receiptId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_receipt_pages_receiptId_pageIndex` " +
                    "ON `receipt_pages` (`receiptId`, `pageIndex`)",
            )

            db.execSQL(
                """
                INSERT INTO receipts(
                    id,
                    merchant,
                    purchaseDate,
                    purchaseTime,
                    totalAmount,
                    currency,
                    vatNumber,
                    documentNumber,
                    paymentMethod,
                    rawOcrText,
                    confirmedAtEpochMs
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    RECEIPT_ID,
                    "FERRAMENTA ROSSI SRL",
                    "2026-09-24",
                    "10:30",
                    "149.90",
                    "EUR",
                    "12345678901",
                    "A-100",
                    "Carta",
                    "TRAPANO BOSCH 18V 149,90",
                    1_790_236_200_000L,
                ),
            )

            db.execSQL(
                """
                INSERT INTO receipt_products(
                    receiptId,
                    position,
                    name,
                    quantity,
                    unitPrice,
                    lineTotal,
                    sourceConfidence
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    RECEIPT_ID,
                    0,
                    "TRAPANO BOSCH 18V",
                    "1",
                    "149.90",
                    "149.90",
                    0.95,
                ),
            )

            db.execSQL(
                """
                INSERT INTO receipt_pages(
                    receiptId,
                    pageIndex,
                    originalUri
                ) VALUES (?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    RECEIPT_ID,
                    0,
                    "file:///legacy/receipt_01.jpg",
                ),
            )

            db.version = 1
        }
    }

    private companion object {
        const val TEST_DATABASE = "garanzia-migration-v1-v2-test.db"
        const val RECEIPT_ID = "legacy-receipt-1"
    }
}
