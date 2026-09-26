package com.milanstevic.garanzia.product

import com.milanstevic.garanzia.data.local.ReceiptProductEntity
import com.milanstevic.garanzia.data.local.ReceiptWithDetails

data class ProductDetailData(
    val receiptId: String,
    val productId: Long,
    val name: String,
    val position: Int,
    val quantity: String?,
    val unitPrice: String?,
    val lineTotal: String?,
    val sourceConfidence: Float?,
    val merchant: String,
    val purchaseDate: String,
    val purchaseTime: String?,
    val currency: String?,
    val documentNumber: String?,
    val vatNumber: String?,
    val paymentMethod: String?,
    val pageCount: Int,
)

object ProductDetailMapper {

    fun from(
        details: ReceiptWithDetails,
        productId: Long,
    ): ProductDetailData? {
        val product = details.products.firstOrNull { it.id == productId }
            ?: return null

        return from(details, product)
    }

    fun from(
        details: ReceiptWithDetails,
        product: ReceiptProductEntity,
    ): ProductDetailData? {
        if (product.receiptId != details.receipt.id) return null

        return ProductDetailData(
            receiptId = details.receipt.id,
            productId = product.id,
            name = product.name,
            position = product.position,
            quantity = product.quantity,
            unitPrice = product.unitPrice,
            lineTotal = product.lineTotal,
            sourceConfidence = product.sourceConfidence,
            merchant = details.receipt.merchant,
            purchaseDate = details.receipt.purchaseDate,
            purchaseTime = details.receipt.purchaseTime,
            currency = details.receipt.currency,
            documentNumber = details.receipt.documentNumber,
            vatNumber = details.receipt.vatNumber,
            paymentMethod = details.receipt.paymentMethod,
            pageCount = details.pages.size,
        )
    }
}
