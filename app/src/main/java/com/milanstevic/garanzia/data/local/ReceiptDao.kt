package com.milanstevic.garanzia.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ReceiptDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertReceipt(entity: ReceiptEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertProducts(entities: List<ReceiptProductEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPages(entities: List<ReceiptPageEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertSearch(entity: ReceiptSearchEntity)


    @Update
    protected abstract suspend fun updateReceipt(entity: ReceiptEntity)

    @Query("DELETE FROM receipt_products WHERE receiptId = :receiptId")
    protected abstract suspend fun deleteProductsForReceipt(receiptId: String)

    @Query("DELETE FROM receipt_search WHERE receiptId = :receiptId")
    protected abstract suspend fun deleteSearchForReceipt(receiptId: String)



    @Transaction
    open suspend fun insertReceiptGraph(
        receipt: ReceiptEntity,
        products: List<ReceiptProductEntity>,
        pages: List<ReceiptPageEntity>,
        search: ReceiptSearchEntity,
    ) {
        insertReceipt(receipt)
        insertProducts(products)
        insertPages(pages)
        insertSearch(search)
    }

    @Transaction
    open suspend fun updateReceiptGraph(
        receipt: ReceiptEntity,
        products: List<ReceiptProductEntity>,
        search: ReceiptSearchEntity,
    ) {
        updateReceipt(receipt)
        deleteProductsForReceipt(receipt.id)
        insertProducts(products)
        deleteSearchForReceipt(receipt.id)
        insertSearch(search)
    }


    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :receiptId LIMIT 1")
    abstract suspend fun getReceipt(receiptId: String): ReceiptWithDetails?

    @Transaction
    @Query("SELECT * FROM receipts ORDER BY confirmedAtEpochMs DESC")
    abstract suspend fun getReceipts(): List<ReceiptWithDetails>

    @Transaction
    @Query("SELECT * FROM receipts ORDER BY confirmedAtEpochMs DESC")
    abstract fun observeReceipts(): Flow<List<ReceiptWithDetails>>

    @Query("SELECT COUNT(*) FROM receipts")
    abstract fun observeReceiptCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM receipts")
    abstract suspend fun receiptCount(): Int

    @Query(
        "SELECT DISTINCT receiptId FROM receipt_search " +
            "WHERE receipt_search MATCH :ftsQuery",
    )
    abstract suspend fun searchReceiptIds(ftsQuery: String): List<String>

    @Query(
        """
        UPDATE receipt_products
        SET warrantyMonths = :warrantyMonths,
            warrantyReminderDays = :reminderDays,
            warrantyNotificationsEnabled = :notificationsEnabled,
            warrantyLastNotificationKey = NULL
        WHERE id = :productId AND receiptId = :receiptId
        """,
    )
    abstract suspend fun updateProductWarranty(
        receiptId: String,
        productId: Long,
        warrantyMonths: Int?,
        reminderDays: Int,
        notificationsEnabled: Boolean,
    ): Int

    @Query(
        """
        UPDATE receipt_products
        SET warrantyLastNotificationKey = :notificationKey
        WHERE id = :productId AND receiptId = :receiptId
        """,
    )
    abstract suspend fun markWarrantyNotification(
        receiptId: String,
        productId: Long,
        notificationKey: String,
    ): Int

    @Query("DELETE FROM receipts WHERE id = :receiptId")
    protected abstract suspend fun deleteReceiptRow(receiptId: String)

    @Query("DELETE FROM receipt_search")
    protected abstract suspend fun deleteAllSearchRows()

    @Query("DELETE FROM receipts")
    protected abstract suspend fun deleteAllReceiptRows()

    @Transaction
    open suspend fun replaceArchive(receipts: List<ReceiptWithDetails>) {
        deleteAllSearchRows()
        deleteAllReceiptRows()

        receipts.forEach { details ->
            insertReceipt(details.receipt)
            insertProducts(details.products)
            insertPages(details.pages)
            insertSearch(
                ReceiptSearchEntity.from(
                    receipt = details.receipt,
                    products = details.products,
                ),
            )
        }
    }

    @Transaction
    open suspend fun deleteReceiptGraph(receiptId: String) {
        deleteSearchForReceipt(receiptId)
        deleteReceiptRow(receiptId)
    }
}
