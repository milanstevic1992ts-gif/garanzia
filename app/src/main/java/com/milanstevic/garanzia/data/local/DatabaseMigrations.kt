package com.milanstevic.garanzia.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Registro autoritativo delle migrazioni Room.
 *
 * Non usare fallbackToDestructiveMigration: un aggiornamento dell'app
 * non deve mai cancellare gli scontrini dell'utente.
 */
object DatabaseMigrations {

    val MIGRATION_1_2: Migration =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS `receipt_search`
                    USING FTS4(
                        `receiptId` TEXT NOT NULL,
                        `searchableText` TEXT NOT NULL,
                        tokenize=unicode61
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    INSERT INTO receipt_search(receiptId, searchableText)
                    SELECT
                        r.id,
                        COALESCE(r.merchant, '') || ' ' ||
                        COALESCE(r.purchaseDate, '') || ' ' ||
                        CASE
                            WHEN length(r.purchaseDate) = 10 THEN
                                substr(r.purchaseDate, 9, 2) || '/' ||
                                substr(r.purchaseDate, 6, 2) || '/' ||
                                substr(r.purchaseDate, 1, 4)
                            ELSE COALESCE(r.purchaseDate, '')
                        END || ' ' ||
                        COALESCE(r.documentNumber, '') || ' ' ||
                        COALESCE(r.vatNumber, '') || ' ' ||
                        COALESCE(r.rawOcrText, '') || ' ' ||
                        COALESCE(
                            (
                                SELECT group_concat(p.name, ' ')
                                FROM receipt_products p
                                WHERE p.receiptId = r.id
                            ),
                            ''
                        )
                    FROM receipts r
                    """.trimIndent(),
                )
            }
        }

    val MIGRATION_2_3: Migration =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE receipt_products ADD COLUMN warrantyMonths INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE receipt_products ADD COLUMN warrantyReminderDays INTEGER NOT NULL DEFAULT 30",
                )
                db.execSQL(
                    "ALTER TABLE receipt_products ADD COLUMN warrantyNotificationsEnabled INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE receipt_products ADD COLUMN warrantyLastNotificationKey TEXT",
                )
            }
        }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
    )
}
