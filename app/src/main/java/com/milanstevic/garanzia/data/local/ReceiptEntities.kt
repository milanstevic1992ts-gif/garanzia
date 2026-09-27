package com.milanstevic.garanzia.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "receipts",
    indices = [
        Index(value = ["purchaseDate"]),
        Index(value = ["merchant"]),
        Index(value = ["confirmedAtEpochMs"]),
    ],
)
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val merchant: String,
    val purchaseDate: String,
    val purchaseTime: String?,
    val totalAmount: String,
    val currency: String?,
    val vatNumber: String?,
    val documentNumber: String?,
    val paymentMethod: String?,
    val rawOcrText: String?,
    val confirmedAtEpochMs: Long,
)

@Entity(
    tableName = "receipt_products",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["receiptId"]),
        Index(value = ["name"]),
        Index(value = ["receiptId", "position"], unique = true),
    ],
)
data class ReceiptProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: String,
    val position: Int,
    val name: String,
    val quantity: String?,
    val unitPrice: String?,
    val lineTotal: String?,
    val sourceConfidence: Float?,
    val warrantyMonths: Int? = null,
    @ColumnInfo(defaultValue = "30")
    val warrantyReminderDays: Int = 30,
    @ColumnInfo(defaultValue = "1")
    val warrantyNotificationsEnabled: Boolean = true,
    val warrantyLastNotificationKey: String? = null,
)

@Entity(
    tableName = "receipt_pages",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["receiptId"]),
        Index(value = ["receiptId", "pageIndex"], unique = true),
    ],
)
data class ReceiptPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: String,
    val pageIndex: Int,
    val originalUri: String,
)


@Entity(
    tableName = "product_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ReceiptProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["receiptId"]),
        Index(value = ["productId"]),
        Index(value = ["productId", "createdAtEpochMs"]),
    ],
)
data class ProductAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: String,
    val productId: Long,
    val category: String,
    val localUri: String,
    val mimeType: String,
    val originalName: String?,
    val note: String?,
    val createdAtEpochMs: Long,
)
