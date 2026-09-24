package com.milanstevic.garanzia.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ReceiptDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertReceipt(entity: ReceiptEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertProducts(entities: List<ReceiptProductEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPages(entities: List<ReceiptPageEntity>)

    @Transaction
    open suspend fun insertReceiptGraph(
        receipt: ReceiptEntity,
        products: List<ReceiptProductEntity>,
        pages: List<ReceiptPageEntity>,
    ) {
        insertReceipt(receipt)
        insertProducts(products)
        insertPages(pages)
    }

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :receiptId LIMIT 1")
    abstract suspend fun getReceipt(receiptId: String): ReceiptWithDetails?

    @Transaction
    @Query("SELECT * FROM receipts ORDER BY confirmedAtEpochMs DESC")
    abstract fun observeReceipts(): Flow<List<ReceiptWithDetails>>

    @Query("SELECT COUNT(*) FROM receipts")
    abstract fun observeReceiptCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM receipts")
    abstract suspend fun receiptCount(): Int

    @Query("DELETE FROM receipts WHERE id = :receiptId")
    abstract suspend fun deleteReceipt(receiptId: String)
}
