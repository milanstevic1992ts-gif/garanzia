package com.milanstevic.garanzia.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

const val GARANZIA_SCHEMA_VERSION = 2

@Database(
    entities = [
        ReceiptEntity::class,
        ReceiptProductEntity::class,
        ReceiptPageEntity::class,
        ReceiptSearchEntity::class,
    ],
    version = GARANZIA_SCHEMA_VERSION,
    exportSchema = true,
)
abstract class GaranziaDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao

    companion object {
        const val SCHEMA_VERSION = GARANZIA_SCHEMA_VERSION
    }
}
