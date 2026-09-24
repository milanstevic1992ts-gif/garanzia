package com.milanstevic.garanzia.intelligence

import com.milanstevic.garanzia.ocr.OcrReceiptResult
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

enum class ReceiptPaymentMethod(val displayName: String) {
    CARD("Carta"),
    CASH("Contanti"),
    BANK_TRANSFER("Bonifico"),
}

data class ReceiptInterpretation(
    val merchant: String?,
    val purchaseDate: LocalDate?,
    val purchaseTime: LocalTime?,
    val totalAmount: BigDecimal?,
    val currency: String?,
    val vatNumber: String?,
    val documentNumber: String?,
    val paymentMethod: ReceiptPaymentMethod?,
) {
    val hasStructuredData: Boolean
        get() =
            merchant != null ||
                purchaseDate != null ||
                purchaseTime != null ||
                totalAmount != null ||
                vatNumber != null ||
                documentNumber != null ||
                paymentMethod != null
}

/**
 * Phase 4 interpreter.
 *
 * It derives structured receipt fields only from OCR text already present in
 * [OcrReceiptResult]. It does not manufacture missing values and it does not
 * attempt product splitting: multi-product understanding belongs to Phase 6.
 */
@Singleton
class ReceiptInterpreter @Inject constructor() {

    fun interpret(receipt: OcrReceiptResult): ReceiptInterpretation {
        val lines = receipt.pages
            .flatMap { it.lines }
            .map { it.text.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotBlank() }

        val datedLine = findDatedLine(lines)

        return ReceiptInterpretation(
            merchant = findMerchant(lines),
            purchaseDate = datedLine?.date,
            purchaseTime = findPurchaseTime(lines, datedLine?.lineIndex),
            totalAmount = findTotal(lines),
            currency = findCurrency(lines),
            vatNumber = findVatNumber(lines),
            documentNumber = findDocumentNumber(lines),
            paymentMethod = findPaymentMethod(lines),
        )
    }

    private fun findMerchant(lines: List<String>): String? {
        val excludedTerms = listOf(
            "documento",
            "scontrino",
            "ricevuta",
            "totale",
            "subtotale",
            "importo",
            "pagato",
            "contanti",
            "carta",
            "bancomat",
            "mastercard",
            "visa",
            "p.iva",
            "p iva",
            "partita iva",
            "codice fiscale",
            "www.",
            "http",
            "tel.",
            "telefono",
            "data",
            "ora",
        )

        return lines
            .take(10)
            .mapIndexedNotNull { index, line ->
                val normalized = line.lowercase()
                if (
                    line.length !in 3..64 ||
                    excludedTerms.any(normalized::contains) ||
                    DATE_REGEX.containsMatchIn(line) ||
                    ISO_DATE_REGEX.containsMatchIn(line) ||
                    moneyValues(line).isNotEmpty()
                ) {
                    return@mapIndexedNotNull null
                }

                val letters = line.count(Char::isLetter)
                if (letters < 3) return@mapIndexedNotNull null

                val uppercaseLetters = line.count { it.isLetter() && it.isUpperCase() }
                val uppercaseRatio =
                    if (letters == 0) 0.0 else uppercaseLetters.toDouble() / letters.toDouble()

                var score = 100 - (index * 8)
                if (uppercaseRatio >= 0.65) score += 18
                if (BUSINESS_SUFFIX_REGEX.containsMatchIn(line)) score += 16
                if (line.any(Char::isDigit)) score -= 10
                if (ADDRESS_REGEX.containsMatchIn(line)) score -= 25

                MerchantCandidate(line = line, score = score)
            }
            .maxByOrNull { it.score }
            ?.line
    }

    private fun findDatedLine(lines: List<String>): DatedLine? =
        lines.mapIndexedNotNull { index, line ->
            parseDate(line)?.let { date ->
                val bonus = if (line.contains("data", ignoreCase = true)) 100 else 0
                DatedLine(index, date, bonus - index)
            }
        }.maxByOrNull { it.score }

    private fun findPurchaseTime(lines: List<String>, datedLineIndex: Int?): LocalTime? {
        if (datedLineIndex != null) {
            parseTime(lines[datedLineIndex])?.let { return it }
        }

        return lines.firstNotNullOfOrNull { line ->
            if (
                line.contains("ora", ignoreCase = true) ||
                line.contains("time", ignoreCase = true)
            ) {
                parseTime(line)
            } else {
                null
            }
        }
    }

    private fun findTotal(lines: List<String>): BigDecimal? {
        val candidates = lines.mapIndexedNotNull { index, line ->
            val normalized = line.lowercase()
            val priority = when {
                normalized.contains("subtotale") || normalized.contains("sub totale") -> 20
                normalized.contains("totale complessivo") -> 120
                Regex("""\b(totale|total)\b""", RegexOption.IGNORE_CASE).containsMatchIn(line) -> 100
                normalized.contains("da pagare") -> 95
                normalized.contains("importo") -> 85
                normalized.contains("pagato") -> 70
                else -> 0
            }

            if (priority == 0 || normalized.contains("resto")) {
                return@mapIndexedNotNull null
            }

            val amounts = moneyValues(line)
            if (amounts.isEmpty()) return@mapIndexedNotNull null

            val adjustedPriority =
                if (
                    normalized.contains("iva") &&
                    !normalized.contains("totale complessivo")
                ) {
                    priority - 35
                } else {
                    priority
                }

            PriceCandidate(
                amount = amounts.last(),
                priority = adjustedPriority,
                lineIndex = index,
            )
        }

        return candidates
            .maxWithOrNull(
                compareBy<PriceCandidate> { it.priority }
                    .thenBy { it.lineIndex },
            )
            ?.amount
    }

    private fun findCurrency(lines: List<String>): String? {
        val text = lines.joinToString(" ")
        return when {
            text.contains("€") || Regex("""\bEUR\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "EUR"
            text.contains("$") || Regex("""\bUSD\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "USD"
            text.contains("£") || Regex("""\bGBP\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "GBP"
            else -> null
        }
    }

    private fun findVatNumber(lines: List<String>): String? =
        lines.firstNotNullOfOrNull { line ->
            VAT_REGEX.find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(" ", "")
                ?.uppercase()
        }

    private fun findDocumentNumber(lines: List<String>): String? =
        lines.firstNotNullOfOrNull { line ->
            DOCUMENT_NUMBER_REGEX.find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.length >= 2 }
        }

    private fun findPaymentMethod(lines: List<String>): ReceiptPaymentMethod? {
        val text = lines.joinToString(" ").lowercase()
        return when {
            listOf("visa", "mastercard", "maestro", "bancomat", "pagobancomat", "carta", "pos")
                .any(text::contains) -> ReceiptPaymentMethod.CARD
            listOf("contanti", "cash").any(text::contains) -> ReceiptPaymentMethod.CASH
            listOf("bonifico", "bank transfer").any(text::contains) -> ReceiptPaymentMethod.BANK_TRANSFER
            else -> null
        }
    }

    private fun parseDate(line: String): LocalDate? {
        ISO_DATE_REGEX.find(line)?.let { match ->
            return safeDate(
                year = match.groupValues[1].toInt(),
                month = match.groupValues[2].toInt(),
                day = match.groupValues[3].toInt(),
            )
        }

        DATE_REGEX.find(line)?.let { match ->
            val rawYear = match.groupValues[3].toInt()
            val year = if (rawYear < 100) 2000 + rawYear else rawYear
            return safeDate(
                year = year,
                month = match.groupValues[2].toInt(),
                day = match.groupValues[1].toInt(),
            )
        }

        return null
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }

    private fun parseTime(line: String): LocalTime? =
        TIME_REGEX.find(line)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val second = match.groupValues.getOrNull(3)
                ?.takeIf(String::isNotBlank)
                ?.toIntOrNull()
                ?: 0

            try {
                LocalTime.of(hour, minute, second)
            } catch (_: DateTimeException) {
                null
            }
        }

    private fun moneyValues(line: String): List<BigDecimal> =
        MONEY_REGEX.findAll(line).mapNotNull { match ->
            val whole = match.groupValues[1]
                .replace(".", "")
                .replace(" ", "")
            val decimal = match.groupValues[2]
            runCatching { BigDecimal("$whole.$decimal") }.getOrNull()
        }.toList()

    private data class MerchantCandidate(
        val line: String,
        val score: Int,
    )

    private data class DatedLine(
        val lineIndex: Int,
        val date: LocalDate,
        val score: Int,
    )

    private data class PriceCandidate(
        val amount: BigDecimal,
        val priority: Int,
        val lineIndex: Int,
    )

    private companion object {
        val DATE_REGEX =
            Regex("""(?<!\d)(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})(?!\d)""")
        val ISO_DATE_REGEX =
            Regex("""(?<!\d)(\d{4})-(\d{1,2})-(\d{1,2})(?!\d)""")
        val TIME_REGEX =
            Regex("""(?<!\d)([01]?\d|2[0-3])[:.](\d{2})(?::(\d{2}))?(?!\d)""")
        val MONEY_REGEX =
            Regex("""(?<!\d)(\d{1,3}(?:[.\s]\d{3})*|\d+)[,.](\d{2})(?!\d)""")
        val VAT_REGEX =
            Regex(
                """(?:P\.?\s*IVA|PARTITA\s+IVA|VAT)\s*[:\-]?\s*((?:IT\s*)?\d{11})""",
                RegexOption.IGNORE_CASE,
            )
        val DOCUMENT_NUMBER_REGEX =
            Regex(
                """(?:DOCUMENTO(?:\s+COMMERCIALE)?|SCONTRINO|RICEVUTA|DOC\.?)\s*(?:N(?:R|RO)?\.?|N°|#|:)?\s*([A-Z0-9][A-Z0-9./-]{1,})""",
                RegexOption.IGNORE_CASE,
            )
        val BUSINESS_SUFFIX_REGEX =
            Regex("""\b(SRL|S\.R\.L\.|SPA|S\.P\.A\.|SNC|SAS|COOP)\b""", RegexOption.IGNORE_CASE)
        val ADDRESS_REGEX =
            Regex("""\b(VIA|VIALE|PIAZZA|CORSO|STRADA|LOC\.|LOCALITA)\b""", RegexOption.IGNORE_CASE)
    }
}
