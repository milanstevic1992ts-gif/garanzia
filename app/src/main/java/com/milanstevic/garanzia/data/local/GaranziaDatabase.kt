package com.milanstevic.garanzia.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReceiptEntity::class,
        ReceiptProductEntity::class,
        ReceiptPageEntity::class,
    ],
    version = GaranziaDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class GaranziaDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
