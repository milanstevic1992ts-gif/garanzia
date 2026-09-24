package com.milanstevic.garanzia.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReceiptEntity::class,
        ReceiptProductEntity::class,
        ReceiptPageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class GaranziaDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
}
