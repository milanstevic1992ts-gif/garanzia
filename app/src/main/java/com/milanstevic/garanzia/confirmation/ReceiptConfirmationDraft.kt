package com.milanstevic.garanzia.confirmation

import com.milanstevic.garanzia.intelligence.ReceiptInterpretation
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

enum class ConfirmationField {
    MERCHANT,
    PURCHASE_DATE,
    PURCHASE_TIME,
    TOTAL,
    CURRENCY,
    VAT_NUMBER,
    DOCUMENT_NUMBER,
    PAYMENT_METHOD,
}

data class ProductConfirmationDraft(
    val name: String,
    val quantity: String,
    val unitPrice: String,
    val lineTotal: String,
    val requiresReview: Boolean,
    val sourceConfidence: Float?,
)

data class ReceiptConfirmationDraft(
    val merchant: String,
    val purchaseDate: String,
    val purchaseTime: String,
    val totalAmount: String,
    val currency: String,
    val vatNumber: String,
    val documentNumber: String,
    val paymentMethod: String,
    val products: List<ProductConfirmationDraft>,
    val fieldsToReview: Set<ConfirmationField>,
) {
    val canConfirm: Boolean
        get() =
            merchant.isNotBlank() &&
                isValidDate(purchaseDate) &&
                parseMoney(totalAmount) != null &&
                products.isNotEmpty() &&
                products.all { product ->
                    product.name.isNotBlank() &&
                        (product.lineTotal.isBlank() || parseMoney(product.lineTotal) != null) &&
                        (product.unitPrice.isBlank() || parseMoney(product.unitPrice) != null) &&
                        (product.quantity.isBlank() || parseQuantity(product.quantity) != null)
                }

    val attentionCount: Int
        get() =
            fieldsToReview.size +
                products.count { it.requiresReview } +
                if (products.isEmpty()) 1 else 0

    companion object {
        private const val REVIEW_THRESHOLD = 0.70f
        private val DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT)

        fun manualFallback(): ReceiptConfirmationDraft =
            ReceiptConfirmationDraft(
                merchant = "",
                purchaseDate = "",
                purchaseTime = "",
                totalAmount = "",
                currency = "",
                vatNumber = "",
                documentNumber = "",
                paymentMethod = "",
                products = emptyList(),
                fieldsToReview = setOf(
                    ConfirmationField.MERCHANT,
                    ConfirmationField.PURCHASE_DATE,
                    ConfirmationField.TOTAL,
                ),
            )

        fun from(
            interpretation: ReceiptInterpretation,
        ): ReceiptConfirmationDraft {
            val fieldsToReview = buildSet {
                if (
                    interpretation.merchant == null ||
                    interpretation.merchant.confidence < REVIEW_THRESHOLD
                ) add(ConfirmationField.MERCHANT)

                if (
                    interpretation.purchaseDate == null ||
                    interpretation.purchaseDate.confidence < REVIEW_THRESHOLD
                ) add(ConfirmationField.PURCHASE_DATE)

                interpretation.purchaseTime
                    ?.takeIf { it.confidence < REVIEW_THRESHOLD }
                    ?.let { add(ConfirmationField.PURCHASE_TIME) }

                if (
                    interpretation.totalAmount == null ||
                    interpretation.totalAmount.confidence < REVIEW_THRESHOLD
                ) add(ConfirmationField.TOTAL)

                interpretation.currency
                    ?.takeIf { it.confidence < REVIEW_THRESHOLD }
                    ?.let { add(ConfirmationField.CURRENCY) }

                interpretation.vatNumber
                    ?.takeIf { it.confidence < REVIEW_THRESHOLD }
                    ?.let { add(ConfirmationField.VAT_NUMBER) }

                interpretation.documentNumber
                    ?.takeIf { it.confidence < REVIEW_THRESHOLD }
                    ?.let { add(ConfirmationField.DOCUMENT_NUMBER) }

                interpretation.paymentMethod
                    ?.takeIf { it.confidence < REVIEW_THRESHOLD }
                    ?.let { add(ConfirmationField.PAYMENT_METHOD) }
            }

            return ReceiptConfirmationDraft(
                merchant = interpretation.merchant?.value.orEmpty(),
                purchaseDate = interpretation.purchaseDate
                    ?.value
                    ?.format(DATE_FORMAT)
                    .orEmpty(),
                purchaseTime = interpretation.purchaseTime
                    ?.value
                    ?.toString()
                    .orEmpty(),
                totalAmount = interpretation.totalAmount
                    ?.value
                    ?.toPlainString()
                    ?.replace('.', ',')
                    .orEmpty(),
                currency = interpretation.currency?.value.orEmpty(),
                vatNumber = interpretation.vatNumber?.value.orEmpty(),
                documentNumber = interpretation.documentNumber?.value.orEmpty(),
                paymentMethod = interpretation.paymentMethod?.value?.displayName.orEmpty(),
                products = interpretation.products.map { field ->
                    val product = field.value
                    ProductConfirmationDraft(
                        name = product.name,
                        quantity = product.quantity
                            ?.stripTrailingZeros()
                            ?.toPlainString()
                            ?.replace('.', ',')
                            .orEmpty(),
                        unitPrice = product.unitPrice
                            ?.toPlainString()
                            ?.replace('.', ',')
                            .orEmpty(),
                        lineTotal = product.lineTotal
                            ?.toPlainString()
                            ?.replace('.', ',')
                            .orEmpty(),
                        requiresReview =
                            field.confidence < REVIEW_THRESHOLD ||
                                product.name.isBlank() ||
                                (
                                    product.lineTotal == null &&
                                        product.unitPrice == null
                                    ),
                        sourceConfidence = field.confidence,
                    )
                },
                fieldsToReview = fieldsToReview,
            )
        }

        fun isValidDate(value: String): Boolean {
            if (value.isBlank()) return false

            return try {
                LocalDate.parse(value.trim(), DATE_FORMAT)
                true
            } catch (_: DateTimeParseException) {
                false
            }
        }

        fun parseMoney(value: String): BigDecimal? {
            val normalized = value
                .trim()
                .replace("€", "")
                .replace("EUR", "", ignoreCase = true)
                .replace(" ", "")
                .let { raw ->
                    when {
                        raw.contains(',') && raw.contains('.') ->
                            raw.replace(".", "").replace(',', '.')
                        raw.contains(',') ->
                            raw.replace(',', '.')
                        else -> raw
                    }
                }

            return normalized
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { BigDecimal(it) }.getOrNull() }
        }

        fun parseQuantity(value: String): BigDecimal? {
            val normalized = value.trim().replace(',', '.')
            return normalized
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { BigDecimal(it) }.getOrNull() }
        }
    }
}
