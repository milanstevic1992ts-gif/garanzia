package com.milanstevic.garanzia.data.local

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
    indices = [Index(value = ["receiptId"])],
)
data class ReceiptPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: String,
    val pageIndex: Int,
    val originalUri: String,
)
