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
    protected abstract suspend fun insertAttachments(entities: List<ProductAttachmentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAttachment(entity: ProductAttachmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertSearch(entity: ReceiptSearchEntity)


    @Update
    protected abstract suspend fun updateReceipt(entity: ReceiptEntity)

    @Update
    protected abstract suspend fun updateProducts(entities: List<ReceiptProductEntity>): Int

    @Query("UPDATE receipt_products SET position = position + 1000000 WHERE receiptId = :receiptId")
    protected abstract suspend fun moveProductPositionsOutOfTheWay(receiptId: String)

    @Query("DELETE FROM receipt_products WHERE receiptId = :receiptId")
    protected abstract suspend fun deleteProductsForReceipt(receiptId: String)

    @Query(
        "DELETE FROM receipt_products " +
            "WHERE receiptId = :receiptId AND id NOT IN (:keptIds)",
    )
    protected abstract suspend fun deleteProductsExcept(
        receiptId: String,
        keptIds: List<Long>,
    )

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

        val existingProducts = products.filter { it.id > 0L }
        val newProducts = products.filter { it.id == 0L }
        val keptIds = existingProducts.map { it.id }

        moveProductPositionsOutOfTheWay(receipt.id)

        if (keptIds.isEmpty()) {
            deleteProductsForReceipt(receipt.id)
        } else {
            deleteProductsExcept(
                receiptId = receipt.id,
                keptIds = keptIds,
            )
        }

        if (existingProducts.isNotEmpty()) {
            val updated = updateProducts(existingProducts)
            check(updated == existingProducts.size) {
                "Uno o più prodotti esistenti non sono stati aggiornati"
            }
        }

        if (newProducts.isNotEmpty()) {
            insertProducts(newProducts)
        }

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
        SELECT * FROM product_attachments
        WHERE productId = :productId AND receiptId = :receiptId
        ORDER BY createdAtEpochMs DESC, id DESC
        """,
    )
    abstract suspend fun getProductAttachments(
        receiptId: String,
        productId: Long,
    ): List<ProductAttachmentEntity>

    @Query(
        """
        UPDATE product_attachments
        SET note = :note
        WHERE id = :attachmentId AND productId = :productId AND receiptId = :receiptId
        """,
    )
    abstract suspend fun updateAttachmentNote(
        receiptId: String,
        productId: Long,
        attachmentId: Long,
        note: String?,
    ): Int

    @Query(
        """
        DELETE FROM product_attachments
        WHERE id = :attachmentId AND productId = :productId AND receiptId = :receiptId
        """,
    )
    abstract suspend fun deleteAttachment(
        receiptId: String,
        productId: Long,
        attachmentId: Long,
    ): Int

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
            insertAttachments(details.attachments)
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
