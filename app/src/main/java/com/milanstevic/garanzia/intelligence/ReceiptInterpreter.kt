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

enum class ConfidenceLevel(val displayName: String) {
    HIGH("Alta"),
    MEDIUM("Media"),
    LOW("Bassa");

    companion object {
        fun from(score: Float): ConfidenceLevel = when {
            score >= 0.85f -> HIGH
            score >= 0.70f -> MEDIUM
            else -> LOW
        }
    }
}

data class InterpretedField<T>(
    val value: T,
    val confidence: Float,
    val evidence: String,
    val rule: String,
) {
    init {
        require(confidence in 0f..1f) { "Confidence must be between 0 and 1" }
    }

    val level: ConfidenceLevel
        get() = ConfidenceLevel.from(confidence)
}

data class ReceiptInterpretation(
    val merchant: InterpretedField<String>?,
    val purchaseDate: InterpretedField<LocalDate>?,
    val purchaseTime: InterpretedField<LocalTime>?,
    val totalAmount: InterpretedField<BigDecimal>?,
    val currency: InterpretedField<String>?,
    val vatNumber: InterpretedField<String>?,
    val documentNumber: InterpretedField<String>?,
    val paymentMethod: InterpretedField<ReceiptPaymentMethod>?,
    val products: List<InterpretedField<ReceiptProduct>>,
) {
    val hasStructuredData: Boolean
        get() =
            merchant != null ||
                purchaseDate != null ||
                purchaseTime != null ||
                totalAmount != null ||
                currency != null ||
                vatNumber != null ||
                documentNumber != null ||
                paymentMethod != null ||
                products.isNotEmpty()

    val needsReview: Boolean
        get() =
            merchant == null ||
                purchaseDate == null ||
                totalAmount == null ||
                sequenceOf(
                    merchant?.confidence,
                    purchaseDate?.confidence,
                    purchaseTime?.confidence,
                    totalAmount?.confidence,
                    currency?.confidence,
                    vatNumber?.confidence,
                    documentNumber?.confidence,
                    paymentMethod?.confidence,
                ).filterNotNull().any { it < 0.70f } ||
                products.any { it.confidence < 0.70f }
}

/**
 * Phase 6 receipt interpreter.
 *
 * Every accepted value is tied to real OCR evidence and receives a confidence
 * score built from OCR confidence + deterministic semantic rules. Values below
 * the minimum acceptance threshold are discarded instead of being guessed.
 *
 * Phase 6 adds conservative multi-product extraction while keeping every
 * product tied to OCR evidence and the same confidence rules.
 */
@Singleton
class ReceiptInterpreter @Inject constructor() {

    private val productInterpreter = ReceiptProductInterpreter()

    fun interpret(receipt: OcrReceiptResult): ReceiptInterpretation {
        val lines = receipt.pages
            .flatMap { it.lines }
            .mapIndexedNotNull { index, line ->
                val text = line.text.trim().replace(Regex("""\s+"""), " ")
                if (text.isBlank()) {
                    null
                } else {
                    SourceLine(
                        index = index,
                        text = text,
                        ocrConfidence = line.confidence.coerceIn(0f, 1f),
                    )
                }
            }

        val datedLine = findDatedLine(lines)
        val merchant = findMerchant(lines)

        return ReceiptInterpretation(
            merchant = merchant,
            purchaseDate = datedLine?.field,
            purchaseTime = findPurchaseTime(lines, datedLine),
            totalAmount = findTotal(lines),
            currency = findCurrency(lines),
            vatNumber = findVatNumber(lines),
            documentNumber = findDocumentNumber(lines),
            paymentMethod = findPaymentMethod(lines),
            products = productInterpreter.interpret(
                receipt = receipt,
                merchantEvidence = merchant?.evidence,
            ),
        )
    }

    private fun findMerchant(lines: List<SourceLine>): InterpretedField<String>? =
        lines
            .take(10)
            .mapNotNull { source ->
                val line = source.text
                val normalized = line.lowercase()

                if (
                    line.length !in 3..64 ||
                    MERCHANT_EXCLUDED_TERMS.any(normalized::contains) ||
                    DATE_REGEX.containsMatchIn(line) ||
                    ISO_DATE_REGEX.containsMatchIn(line) ||
                    moneyValues(line).isNotEmpty()
                ) {
                    return@mapNotNull null
                }

                val letters = line.count(Char::isLetter)
                if (letters < 3) return@mapNotNull null

                val uppercaseLetters = line.count { it.isLetter() && it.isUpperCase() }
                val uppercaseRatio = uppercaseLetters.toFloat() / letters.toFloat()

                var semantic = 0.58f
                if (source.index <= 2) semantic += 0.10f
                if (uppercaseRatio >= 0.65f) semantic += 0.10f
                if (BUSINESS_SUFFIX_REGEX.containsMatchIn(line)) semantic += 0.14f
                if (line.any(Char::isDigit)) semantic -= 0.08f
                if (ADDRESS_REGEX.containsMatchIn(line)) semantic -= 0.25f

                acceptedField(
                    value = line,
                    source = source,
                    semanticConfidence = semantic.coerceIn(0f, 1f),
                    rule = "intestazione esercente",
                )
            }
            .maxByOrNull { it.confidence }

    private fun findDatedLine(lines: List<SourceLine>): DatedLine? =
        lines.mapNotNull { source ->
            parseDate(source.text)?.let { date ->
                val semantic =
                    if (source.text.contains("data", ignoreCase = true)) 0.98f else 0.88f

                acceptedField(
                    value = date,
                    source = source,
                    semanticConfidence = semantic,
                    rule = "data esplicita",
                )?.let { field ->
                    DatedLine(
                        lineIndex = source.index,
                        field = field,
                    )
                }
            }
        }.maxByOrNull { it.field.confidence }

    private fun findPurchaseTime(
        lines: List<SourceLine>,
        datedLine: DatedLine?,
    ): InterpretedField<LocalTime>? {
        if (datedLine != null) {
            val source = lines.firstOrNull { it.index == datedLine.lineIndex }
            if (source != null) {
                parseTime(source.text)?.let { time ->
                    acceptedField(
                        value = time,
                        source = source,
                        semanticConfidence =
                            if (source.text.contains("ora", ignoreCase = true)) 0.98f else 0.88f,
                        rule = "ora sulla riga della data",
                    )?.let { return it }
                }
            }
        }

        return lines.mapNotNull { source ->
            val hasTimeLabel =
                source.text.contains("ora", ignoreCase = true) ||
                    source.text.contains("time", ignoreCase = true)

            if (!hasTimeLabel) return@mapNotNull null

            parseTime(source.text)?.let { time ->
                acceptedField(
                    value = time,
                    source = source,
                    semanticConfidence = 0.97f,
                    rule = "ora esplicita",
                )
            }
        }.maxByOrNull { it.confidence }
    }

    private fun findTotal(lines: List<SourceLine>): InterpretedField<BigDecimal>? =
        lines.mapNotNull { source ->
            val normalized = source.text.lowercase()

            // Anti-hallucination: subtotals, VAT-only amounts and change are never
            // promoted to receipt total.
            if (
                normalized.contains("subtotale") ||
                normalized.contains("sub totale") ||
                normalized.contains("resto") ||
                (normalized.contains("iva") && !normalized.contains("totale complessivo"))
            ) {
                return@mapNotNull null
            }

            val semantic = when {
                normalized.contains("totale complessivo") -> 0.99f
                TOTAL_REGEX.containsMatchIn(source.text) -> 0.96f
                normalized.contains("da pagare") -> 0.94f
                normalized.contains("importo dovuto") -> 0.92f
                normalized.contains("importo") -> 0.82f
                normalized.contains("pagato") -> 0.76f
                else -> return@mapNotNull null
            }

            val amounts = moneyValues(source.text)
            if (amounts.isEmpty()) return@mapNotNull null

            acceptedField(
                value = amounts.last(),
                source = source,
                semanticConfidence = semantic,
                rule = "importo totale etichettato",
            )
        }.maxByOrNull { it.confidence }

    private fun findCurrency(lines: List<SourceLine>): InterpretedField<String>? =
        lines.mapNotNull { source ->
            val value = when {
                source.text.contains("€") ||
                    EUR_REGEX.containsMatchIn(source.text) -> "EUR"
                source.text.contains("$") ||
                    USD_REGEX.containsMatchIn(source.text) -> "USD"
                source.text.contains("£") ||
                    GBP_REGEX.containsMatchIn(source.text) -> "GBP"
                else -> return@mapNotNull null
            }

            acceptedField(
                value = value,
                source = source,
                semanticConfidence = 0.99f,
                rule = "simbolo o codice valuta esplicito",
            )
        }.maxByOrNull { it.confidence }

    private fun findVatNumber(lines: List<SourceLine>): InterpretedField<String>? =
        lines.mapNotNull { source ->
            val value = VAT_REGEX.find(source.text)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(" ", "")
                ?.uppercase()
                ?: return@mapNotNull null

            acceptedField(
                value = value,
                source = source,
                semanticConfidence = 0.99f,
                rule = "partita IVA etichettata",
            )
        }.maxByOrNull { it.confidence }

    private fun findDocumentNumber(lines: List<SourceLine>): InterpretedField<String>? =
        lines.mapNotNull { source ->
            val value = DOCUMENT_NUMBER_REGEX.find(source.text)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.length >= 2 }
                ?: return@mapNotNull null

            acceptedField(
                value = value,
                source = source,
                semanticConfidence = 0.96f,
                rule = "numero documento etichettato",
            )
        }.maxByOrNull { it.confidence }

    private fun findPaymentMethod(
        lines: List<SourceLine>,
    ): InterpretedField<ReceiptPaymentMethod>? =
        lines.mapNotNull { source ->
            val normalized = source.text.lowercase()

            val method = when {
                CARD_BRANDS.any(normalized::contains) -> ReceiptPaymentMethod.CARD
                normalized.contains("carta") &&
                    PAYMENT_CONTEXT.any(normalized::contains) -> ReceiptPaymentMethod.CARD
                normalized.contains("contanti") ||
                    CASH_REGEX.containsMatchIn(source.text) -> ReceiptPaymentMethod.CASH
                normalized.contains("bonifico") ||
                    BANK_TRANSFER_REGEX.containsMatchIn(source.text) -> ReceiptPaymentMethod.BANK_TRANSFER
                else -> return@mapNotNull null
            }

            val semantic = when {
                CARD_BRANDS.any(normalized::contains) -> 0.98f
                normalized.contains("contanti") -> 0.98f
                normalized.contains("bonifico") -> 0.98f
                else -> 0.92f
            }

            acceptedField(
                value = method,
                source = source,
                semanticConfidence = semantic,
                rule = "metodo di pagamento esplicito",
            )
        }.maxByOrNull { it.confidence }

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

    private fun <T> acceptedField(
        value: T,
        source: SourceLine,
        semanticConfidence: Float,
        rule: String,
    ): InterpretedField<T>? {
        val confidence =
            (
                source.ocrConfidence * OCR_WEIGHT +
                    semanticConfidence.coerceIn(0f, 1f) * SEMANTIC_WEIGHT
                ).coerceIn(0f, 1f)

        if (confidence < MIN_ACCEPTED_CONFIDENCE) return null

        return InterpretedField(
            value = value,
            confidence = confidence,
            evidence = source.text,
            rule = rule,
        )
    }

    private data class SourceLine(
        val index: Int,
        val text: String,
        val ocrConfidence: Float,
    )

    private data class DatedLine(
        val lineIndex: Int,
        val field: InterpretedField<LocalDate>,
    )

    private companion object {
        const val OCR_WEIGHT = 0.65f
        const val SEMANTIC_WEIGHT = 0.35f
        const val MIN_ACCEPTED_CONFIDENCE = 0.55f

        val MERCHANT_EXCLUDED_TERMS = listOf(
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

        val CARD_BRANDS = listOf(
            "visa",
            "mastercard",
            "maestro",
            "bancomat",
            "pagobancomat",
        )

        val PAYMENT_CONTEXT = listOf(
            "pagamento",
            "pagato",
            "credito",
            "debito",
            "pos",
        )

        val DATE_REGEX =
            Regex("""(?<!\d)(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})(?!\d)""")
        val ISO_DATE_REGEX =
            Regex("""(?<!\d)(\d{4})-(\d{1,2})-(\d{1,2})(?!\d)""")
        val TIME_REGEX =
            Regex("""(?<!\d)([01]?\d|2[0-3])[:.](\d{2})(?::(\d{2}))?(?!\d)""")
        val MONEY_REGEX =
            Regex("""(?<!\d)(\d{1,3}(?:[.\s]\d{3})*|\d+)[,.](\d{2})(?!\d)""")
        val TOTAL_REGEX =
            Regex("""\b(totale|total)\b""", RegexOption.IGNORE_CASE)
        val EUR_REGEX =
            Regex("""\bEUR\b""", RegexOption.IGNORE_CASE)
        val USD_REGEX =
            Regex("""\bUSD\b""", RegexOption.IGNORE_CASE)
        val GBP_REGEX =
            Regex("""\bGBP\b""", RegexOption.IGNORE_CASE)
        val CASH_REGEX =
            Regex("""\bCASH\b""", RegexOption.IGNORE_CASE)
        val BANK_TRANSFER_REGEX =
            Regex("""\bBANK\s+TRANSFER\b""", RegexOption.IGNORE_CASE)
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
