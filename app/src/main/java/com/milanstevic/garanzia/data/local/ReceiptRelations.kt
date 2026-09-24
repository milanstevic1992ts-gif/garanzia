package com.milanstevic.garanzia.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class ReceiptWithDetails(
    @Embedded val receipt: ReceiptEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "receiptId",
    )
    val products: List<ReceiptProductEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "receiptId",
    )
    val pages: List<ReceiptPageEntity>,
)
