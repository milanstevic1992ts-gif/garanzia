package com.milanstevic.garanzia.archive

import com.milanstevic.garanzia.data.local.ReceiptWithDetails
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

enum class ArchiveSort {
    NEWEST,
    OLDEST,
}

data class ArchiveFilterState(
    val query: String = "",
    val fromDate: String = "",
    val toDate: String = "",
    val sort: ArchiveSort = ArchiveSort.NEWEST,
) {
    val hasInvalidDate: Boolean
        get() =
            (fromDate.isNotBlank() && parseItalianDate(fromDate) == null) ||
                (toDate.isNotBlank() && parseItalianDate(toDate) == null)

    val hasInvalidRange: Boolean
        get() {
            val from = parseItalianDate(fromDate) ?: return false
            val to = parseItalianDate(toDate) ?: return false
            return from.isAfter(to)
        }

    val isValid: Boolean
        get() = !hasInvalidDate && !hasInvalidRange

    companion object {
        private val ITALIAN_DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT)

        fun parseItalianDate(value: String): LocalDate? =
            try {
                LocalDate.parse(value.trim(), ITALIAN_DATE_FORMAT)
            } catch (_: DateTimeParseException) {
                null
            }
    }
}

object ReceiptArchiveFilter {

    fun apply(
        receipts: List<ReceiptWithDetails>,
        state: ArchiveFilterState,
        matchingIds: Set<String>? = null,
    ): List<ReceiptWithDetails> {
        if (!state.isValid) return emptyList()

        val query = state.query.trim().lowercase()
        val from = ArchiveFilterState.parseItalianDate(state.fromDate)
        val to = ArchiveFilterState.parseItalianDate(state.toDate)

        return receipts
            .asSequence()
            .filter { details ->
                when {
                    query.isBlank() -> true
                    matchingIds != null -> details.receipt.id in matchingIds
                    else -> searchableText(details).contains(query)
                }
            }
            .filter { details ->
                val date = runCatching {
                    LocalDate.parse(details.receipt.purchaseDate)
                }.getOrNull() ?: return@filter false

                (from == null || !date.isBefore(from)) &&
                    (to == null || !date.isAfter(to))
            }
            .sortedWith(
                when (state.sort) {
                    ArchiveSort.NEWEST ->
                        compareByDescending<ReceiptWithDetails> {
                            it.receipt.purchaseDate
                        }.thenByDescending {
                            it.receipt.confirmedAtEpochMs
                        }
                    ArchiveSort.OLDEST ->
                        compareBy<ReceiptWithDetails> {
                            it.receipt.purchaseDate
                        }.thenBy {
                            it.receipt.confirmedAtEpochMs
                        }
                },
            )
            .toList()
    }

    private fun searchableText(details: ReceiptWithDetails): String =
        buildString {
            append(details.receipt.merchant)
            append(' ')
            append(details.receipt.purchaseDate)
            append(' ')
            runCatching {
                LocalDate
                    .parse(details.receipt.purchaseDate)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            }.getOrNull()?.let {
                append(it)
                append(' ')
            }
            append(details.receipt.documentNumber.orEmpty())
            append(' ')
            append(details.receipt.vatNumber.orEmpty())
            append(' ')
            append(details.receipt.rawOcrText.orEmpty())
            details.products.forEach { product ->
                append(' ')
                append(product.name)
            }
        }.lowercase()
}
