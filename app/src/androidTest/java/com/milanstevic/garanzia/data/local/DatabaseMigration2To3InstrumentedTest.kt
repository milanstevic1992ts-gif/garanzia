package com.milanstevic.garanzia.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigration2To3InstrumentedTest {

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        databaseClass = GaranziaDatabase::class.java,
        specs = emptyList(),
        openFactory = FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    @Throws(IOException::class)
    fun migrate2To3PreservesReceiptAndAddsWarrantyDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO receipts(
                    id, merchant, purchaseDate, purchaseTime, totalAmount, currency,
                    vatNumber, documentNumber, paymentMethod, rawOcrText, confirmedAtEpochMs
                ) VALUES(
                    'receipt-1', 'Ferramenta Rossi', '2026-09-01', NULL, '99.90', 'EUR',
                    NULL, 'A-1', 'Carta', 'TRAPANO', 1
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO receipt_products(
                    id, receiptId, position, name, quantity, unitPrice, lineTotal, sourceConfidence
                ) VALUES(
                    10, 'receipt-1', 0, 'Trapano', '1', '99.90', '99.90', 0.95
                )
                """.trimIndent(),
            )
            close()
        }

        Room.databaseBuilder(
            context,
            GaranziaDatabase::class.java,
            TEST_DB,
        )
            .addMigrations(DatabaseMigrations.MIGRATION_2_3)
            .build()
            .use { database ->
                val cursor = database.openHelper.readableDatabase.query(
                    """
                    SELECT warrantyMonths, warrantyReminderDays,
                           warrantyNotificationsEnabled, warrantyLastNotificationKey
                    FROM receipt_products
                    WHERE id = 10
                    """.trimIndent(),
                )

                cursor.use {
                    check(it.moveToFirst())
                    assertEquals(true, it.isNull(0))
                    assertEquals(30, it.getInt(1))
                    assertEquals(1, it.getInt(2))
                    assertEquals(true, it.isNull(3))
                }
            }
    }

    private companion object {
        const val TEST_DB = "garanzia-migration-2-3-test"
    }
}
