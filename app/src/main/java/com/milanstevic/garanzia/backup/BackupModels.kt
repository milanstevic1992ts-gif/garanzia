package com.milanstevic.garanzia.backup

import com.milanstevic.garanzia.data.local.ReceiptEntity
import com.milanstevic.garanzia.data.local.ReceiptProductEntity

data class BackupPageRecord(
    val id: Long,
    val receiptId: String,
    val pageIndex: Int,
    val entryName: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class BackupAttachmentRecord(
    val id: Long,
    val receiptId: String,
    val productId: Long,
    val category: String,
    val mimeType: String,
    val originalName: String?,
    val note: String?,
    val createdAtEpochMs: Long,
    val entryName: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class BackupReceiptRecord(
    val receipt: ReceiptEntity,
    val products: List<ReceiptProductEntity>,
    val pages: List<BackupPageRecord>,
    val attachments: List<BackupAttachmentRecord> = emptyList(),
)

data class BackupManifest(
    val format: String,
    val formatVersion: Int,
    val databaseSchemaVersion: Int,
    val createdAtEpochMs: Long,
    val receipts: List<BackupReceiptRecord>,
) {
    val receiptCount: Int get() = receipts.size
    val productCount: Int get() = receipts.sumOf { it.products.size }
    val pageCount: Int get() = receipts.sumOf { it.pages.size }
    val attachmentCount: Int get() = receipts.sumOf { it.attachments.size }
}

data class BackupSummary(
    val receiptCount: Int,
    val productCount: Int,
    val pageCount: Int,
    val sizeBytes: Long,
    val attachmentCount: Int = 0,
)

data class BackupPreview(
    val createdAtEpochMs: Long,
    val receiptCount: Int,
    val productCount: Int,
    val pageCount: Int,
    val attachmentCount: Int = 0,
)

data class RestoreSummary(
    val receiptCount: Int,
    val productCount: Int,
    val pageCount: Int,
    val attachmentCount: Int = 0,
)
