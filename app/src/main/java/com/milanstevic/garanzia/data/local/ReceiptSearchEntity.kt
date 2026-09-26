package com.milanstevic.garanzia.data.local

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
)
@Entity(tableName = "receipt_search")
data class ReceiptSearchEntity(
    val receiptId: String,
    val searchableText: String,
) {
    companion object {
        fun from(
            receipt: ReceiptEntity,
            products: List<ReceiptProductEntity>,
        ): ReceiptSearchEntity =
            ReceiptSearchEntity(
                receiptId = receipt.id,
                searchableText = buildString {
                    append(receipt.merchant)
                    append(' ')
                    append(receipt.purchaseDate)
                    append(' ')
                    append(italianDate(receipt.purchaseDate))
                    append(' ')
                    append(receipt.documentNumber.orEmpty())
                    append(' ')
                    append(receipt.vatNumber.orEmpty())
                    append(' ')
                    append(receipt.rawOcrText.orEmpty())
                    products.forEach { product ->
                        append(' ')
                        append(product.name)
                    }
                },
            )

        private fun italianDate(isoDate: String): String =
            if (
                isoDate.length == 10 &&
                isoDate[4] == '-' &&
                isoDate[7] == '-'
            ) {
                "${isoDate.substring(8, 10)}/" +
                    "${isoDate.substring(5, 7)}/" +
                    isoDate.substring(0, 4)
            } else {
                isoDate
            }
    }
}

object ReceiptFtsQuery {
    private val TOKEN_REGEX = Regex("""[\p{L}\p{N}]+""")

    fun build(value: String): String? {
        val tokens = TOKEN_REGEX
            .findAll(value)
            .map { it.value.lowercase() }
            .filter { it.isNotBlank() }
            .take(MAX_TERMS)
            .toList()

        if (tokens.isEmpty()) return null

        return tokens.joinToString(" ") { token ->
            "\"$token\"*"
        }
    }

    private const val MAX_TERMS = 12
}
