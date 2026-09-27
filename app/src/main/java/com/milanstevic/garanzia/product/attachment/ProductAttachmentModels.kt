package com.milanstevic.garanzia.product.attachment

import com.milanstevic.garanzia.data.local.ProductAttachmentEntity

enum class ProductAttachmentCategory(
    val storedValue: String,
    val label: String,
) {
    PAYMENT("payment", "Pagamento"),
    BOX("box", "Scatola"),
    SERIAL("serial", "Seriale"),
    WARRANTY("warranty", "Garanzia"),
    OTHER("other", "Altro"),
    ;

    companion object {
        fun fromStored(value: String): ProductAttachmentCategory =
            entries.firstOrNull { it.storedValue == value } ?: OTHER
    }
}

data class ProductAttachmentItem(
    val id: Long,
    val receiptId: String,
    val productId: Long,
    val category: ProductAttachmentCategory,
    val localUri: String,
    val mimeType: String,
    val originalName: String?,
    val note: String?,
    val createdAtEpochMs: Long,
) {
    companion object {
        fun from(entity: ProductAttachmentEntity): ProductAttachmentItem =
            ProductAttachmentItem(
                id = entity.id,
                receiptId = entity.receiptId,
                productId = entity.productId,
                category = ProductAttachmentCategory.fromStored(entity.category),
                localUri = entity.localUri,
                mimeType = entity.mimeType,
                originalName = entity.originalName,
                note = entity.note,
                createdAtEpochMs = entity.createdAtEpochMs,
            )
    }
}
