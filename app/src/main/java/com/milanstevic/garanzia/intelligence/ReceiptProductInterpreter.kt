package com.milanstevic.garanzia.intelligence

import com.milanstevic.garanzia.ocr.OcrReceiptResult
import java.math.BigDecimal

data class ReceiptProduct(
    val name: String,
    val quantity: BigDecimal?,
    val unitPrice: BigDecimal?,
    val lineTotal: BigDecimal?,
)

/**
 * Phase 6 multi-product parser.
 *
 * Supports common receipt layouts:
 * 1. product name + price on the same OCR line
 * 2. product name on one line followed by quantity/price on the next line
 *
 * It intentionally ignores barcodes/EANs (Phase 11) and never turns receipt
 * totals, VAT, payment rows or other footer metadata into products.
 */
class ReceiptProductInterpreter {

    fun interpret(
        receipt: OcrReceiptResult,
        merchantEvidence: String?,
    ): List<InterpretedField<ReceiptProduct>> {
        val lines = receipt.pages
            .flatMap { it.lines }
            .mapIndexedNotNull { index, line ->
                val text = line.text.trim().replace(Regex("""\s+"""), " ")
                if (text.isBlank()) {
                    null
                } else {
                    ProductSourceLine(
                        index = index,
                        text = text,
                        ocrConfidence = line.confidence.coerceIn(0f, 1f),
                    )
                }
            }

        val products = mutableListOf<InterpretedField<ReceiptProduct>>()
        var index = 0

        while (index < lines.size) {
            val source = lines[index]

            if (isMetadata(source.text, merchantEvidence)) {
                index++
                continue
            }

            parseSameLineProduct(source)?.let { product ->
                products += product
                index++
                continue
            }

            if (isNameCandidate(source.text, merchantEvidence) && index + 1 < lines.size) {
                val detail = lines[index + 1]
                if (
                    !isMetadata(detail.text, merchantEvidence) &&
                    moneyValues(detail.text).isNotEmpty()
                ) {
                    parseTwoLineProduct(source, detail)?.let { product ->
                        products += product
                        index += 2
                        continue
                    }
                }
            }

            index++
        }

        return products
            .distinctBy { field ->
                listOf(
                    normalizeProductName(field.value.name),
                    field.value.quantity?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    field.value.unitPrice?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    field.value.lineTotal?.stripTrailingZeros()?.toPlainString().orEmpty(),
                ).joinToString("|")
            }
    }

    private fun parseSameLineProduct(
        source: ProductSourceLine,
    ): InterpretedField<ReceiptProduct>? {
        val amounts = moneyValues(source.text)
        if (amounts.isEmpty()) return null

        val name = extractProductName(source.text)
            ?.takeIf(::looksLikeProductName)
            ?: return null

        val quantityAndUnit = parseQuantityAndUnitPrice(source.text)
        val quantity = quantityAndUnit?.first ?: parsePieceQuantity(source.text)
        val unitPrice = quantityAndUnit?.second

        val lineTotal = when {
            quantityAndUnit != null && amounts.size >= 2 -> amounts.last()
            quantityAndUnit != null -> null
            else -> amounts.last()
        }

        val semantic = when {
            quantity != null && unitPrice != null && lineTotal != null -> 0.97f
            quantity != null && unitPrice != null -> 0.92f
            lineTotal != null -> 0.90f
            else -> 0.75f
        }

        return acceptedProduct(
            product = ReceiptProduct(
                name = name,
                quantity = quantity,
                unitPrice = unitPrice,
                lineTotal = lineTotal,
            ),
            sources = listOf(source),
            semanticConfidence = semantic,
            rule = "prodotto e prezzo sulla stessa riga",
        )
    }

    private fun parseTwoLineProduct(
        nameLine: ProductSourceLine,
        detailLine: ProductSourceLine,
    ): InterpretedField<ReceiptProduct>? {
        val name = cleanNameOnlyLine(nameLine.text)
            .takeIf(::looksLikeProductName)
            ?: return null

        val amounts = moneyValues(detailLine.text)
        if (amounts.isEmpty()) return null

        val quantityAndUnit = parseQuantityAndUnitPrice(detailLine.text)
        val quantity = quantityAndUnit?.first ?: parsePieceQuantity(detailLine.text)
        val unitPrice = quantityAndUnit?.second

        val lineTotal = when {
            quantityAndUnit != null && amounts.size >= 2 -> amounts.last()
            quantityAndUnit != null -> null
            else -> amounts.last()
        }

        return acceptedProduct(
            product = ReceiptProduct(
                name = name,
                quantity = quantity,
                unitPrice = unitPrice,
                lineTotal = lineTotal,
            ),
            sources = listOf(nameLine, detailLine),
            semanticConfidence =
                if (quantity != null && unitPrice != null) 0.91f else 0.84f,
            rule = "nome prodotto seguito da riga quantità/prezzo",
        )
    }

    private fun acceptedProduct(
        product: ReceiptProduct,
        sources: List<ProductSourceLine>,
        semanticConfidence: Float,
        rule: String,
    ): InterpretedField<ReceiptProduct>? {
        val averageOcr =
            sources.map { it.ocrConfidence }.average().toFloat().coerceIn(0f, 1f)
        val confidence =
            (
                averageOcr * OCR_WEIGHT +
                    semanticConfidence.coerceIn(0f, 1f) * SEMANTIC_WEIGHT
                ).coerceIn(0f, 1f)

        if (confidence < MIN_ACCEPTED_CONFIDENCE) return null

        return InterpretedField(
            value = product,
            confidence = confidence,
            evidence = sources.joinToString(" | ") { it.text },
            rule = rule,
        )
    }

    private fun extractProductName(line: String): String? {
        var value = line
        value = PRODUCT_LABEL_PREFIX_REGEX.replace(value, "")
        value = QUANTITY_X_PRICE_REGEX.replace(value, " ")
        value = PIECE_QUANTITY_PREFIX_REGEX.replace(value, "")
        value = MONEY_TOKEN_REGEX.replace(value, " ")
        value = CURRENCY_REGEX.replace(value, " ")
        value = value
            .replace(Regex("""\s{2,}"""), " ")
            .trim(' ', '-', ':', ';', '|', '*')

        return value.takeIf { it.isNotBlank() }
    }

    private fun cleanNameOnlyLine(line: String): String =
        PRODUCT_LABEL_PREFIX_REGEX
            .replace(line, "")
            .trim(' ', '-', ':', ';', '|', '*')

    private fun looksLikeProductName(value: String): Boolean {
        if (value.length !in 2..100) return false
        if (value.count(Char::isLetter) < 2) return false

        val normalized = value.lowercase()
        if (PRODUCT_STOP_WORDS.any { normalized == it || normalized.startsWith("$it ") }) {
            return false
        }

        return true
    }

    private fun isNameCandidate(
        line: String,
        merchantEvidence: String?,
    ): Boolean =
        moneyValues(line).isEmpty() &&
            !isMetadata(line, merchantEvidence) &&
            looksLikeProductName(cleanNameOnlyLine(line))

    private fun isMetadata(
        line: String,
        merchantEvidence: String?,
    ): Boolean {
        val normalized = line.lowercase()

        if (merchantEvidence != null && line == merchantEvidence) return true
        if (DATE_REGEX.containsMatchIn(line) || ISO_DATE_REGEX.containsMatchIn(line)) return true
        if (VAT_REGEX.containsMatchIn(line) || DOCUMENT_REGEX.containsMatchIn(line)) return true
        if (ADDRESS_REGEX.containsMatchIn(line)) return true
        if (FOOTER_METADATA_REGEX.containsMatchIn(line)) return true
        if (PAYMENT_METADATA_REGEX.containsMatchIn(line)) return true
        if (
            CARD_WORD_REGEX.containsMatchIn(line) &&
            CARD_PAYMENT_CONTEXT_REGEX.containsMatchIn(line)
        ) return true
        if (BARCODE_ONLY_REGEX.matches(line.replace(" ", ""))) return true

        return false
    }

    private fun parseQuantityAndUnitPrice(
        line: String,
    ): Pair<BigDecimal, BigDecimal>? =
        QUANTITY_X_PRICE_REGEX.find(line)?.let { match ->
            val quantity = parseDecimal(match.groupValues[1], allowInteger = true)
                ?: return@let null
            val unitPrice = parseDecimal(match.groupValues[2], allowInteger = false)
                ?: return@let null
            quantity to unitPrice
        }

    private fun parsePieceQuantity(line: String): BigDecimal? =
        PIECE_QUANTITY_PREFIX_REGEX.find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { parseDecimal(it, allowInteger = true) }

    private fun moneyValues(line: String): List<BigDecimal> =
        MONEY_REGEX.findAll(line).mapNotNull { match ->
            parseDecimal(match.value, allowInteger = false)
        }.toList()

    private fun parseDecimal(
        raw: String,
        allowInteger: Boolean,
    ): BigDecimal? {
        val cleaned = raw
            .replace("€", "")
            .replace("$", "")
            .replace("£", "")
            .replace("EUR", "", ignoreCase = true)
            .replace("USD", "", ignoreCase = true)
            .replace("GBP", "", ignoreCase = true)
            .replace(" ", "")
            .trim()

        if (!allowInteger && !cleaned.contains(',') && !cleaned.contains('.')) {
            return null
        }

        val normalized = when {
            cleaned.contains(',') && cleaned.contains('.') ->
                cleaned.replace(".", "").replace(',', '.')
            cleaned.contains(',') ->
                cleaned.replace(',', '.')
            cleaned.count { it == '.' } > 1 ->
                cleaned.replace(".", "")
            else -> cleaned
        }

        return runCatching { BigDecimal(normalized) }.getOrNull()
    }

    private fun normalizeProductName(name: String): String =
        name.lowercase().replace(Regex("""\s+"""), " ").trim()

    private data class ProductSourceLine(
        val index: Int,
        val text: String,
        val ocrConfidence: Float,
    )

    private companion object {
        const val OCR_WEIGHT = 0.65f
        const val SEMANTIC_WEIGHT = 0.35f
        const val MIN_ACCEPTED_CONFIDENCE = 0.55f

        val MONEY_REGEX =
            Regex("""(?<!\d)(?:\d{1,3}(?:[.\s]\d{3})*|\d+)[,.]\d{2}(?!\d)""")
        val MONEY_TOKEN_REGEX =
            Regex("""(?<!\d)(?:\d{1,3}(?:[.\s]\d{3})*|\d+)[,.]\d{2}(?!\d)""")
        val QUANTITY_X_PRICE_REGEX =
            Regex(
                """(?<!\d)(\d+(?:[,.]\d+)?)\s*[xX]\s*((?:\d{1,3}(?:[.\s]\d{3})*|\d+)[,.]\d{2})(?!\d)""",
            )
        val PIECE_QUANTITY_PREFIX_REGEX =
            Regex("""^\s*(\d+(?:[,.]\d+)?)\s*(?:PZ\.?|PEZZI)\s+""", RegexOption.IGNORE_CASE)
        val PRODUCT_LABEL_PREFIX_REGEX =
            Regex("""^\s*(?:ARTICOLO|ART\.?|PRODOTTO)\s*[:\-]?\s*""", RegexOption.IGNORE_CASE)
        val CURRENCY_REGEX =
            Regex("""(?:€|£|\$|\bEUR\b|\bUSD\b|\bGBP\b)""", RegexOption.IGNORE_CASE)

        val DATE_REGEX =
            Regex("""(?<!\d)\d{1,2}[./-]\d{1,2}[./-]\d{2,4}(?!\d)""")
        val ISO_DATE_REGEX =
            Regex("""(?<!\d)\d{4}-\d{1,2}-\d{1,2}(?!\d)""")
        val VAT_REGEX =
            Regex(
                """(?:P\.?\s*IVA|PARTITA\s+IVA|VAT)\s*[:\-]?\s*(?:IT\s*)?\d{11}""",
                RegexOption.IGNORE_CASE,
            )
        val DOCUMENT_REGEX =
            Regex(
                """(?:DOCUMENTO(?:\s+COMMERCIALE)?|SCONTRINO|RICEVUTA|DOC\.?)""",
                RegexOption.IGNORE_CASE,
            )
        val ADDRESS_REGEX =
            Regex(
                """\b(VIA|VIALE|PIAZZA|CORSO|STRADA|LOC\.|LOCALITA)\b""",
                RegexOption.IGNORE_CASE,
            )
        val BARCODE_ONLY_REGEX = Regex("""\d{8,14}""")

        val FOOTER_METADATA_REGEX =
            Regex(
                """\b(TOTALE|SUBTOTALE|IVA|IMPONIBILE|RESTO|DOCUMENTO|SCONTRINO|RICEVUTA|GRAZIE|ARRIVEDERCI|OPERATORE|CASSA|DATA|ORA)\b""",
                RegexOption.IGNORE_CASE,
            )
        val PAYMENT_METADATA_REGEX =
            Regex(
                """\b(PAGAMENTO|PAGATO|CONTANTI|BANCOMAT|PAGOBANCOMAT|VISA|MASTERCARD|MAESTRO|BONIFICO|CASH)\b""",
                RegexOption.IGNORE_CASE,
            )
        val CARD_WORD_REGEX =
            Regex("""\bCARTA\b""", RegexOption.IGNORE_CASE)
        val CARD_PAYMENT_CONTEXT_REGEX =
            Regex("""\b(PAGAMENTO|PAGATO|CREDITO|DEBITO|POS)\b""", RegexOption.IGNORE_CASE)

        val PRODUCT_STOP_WORDS = listOf(
            "descrizione",
            "articolo",
            "articoli",
            "quantita",
            "quantità",
            "prezzo",
            "importo",
            "codice",
        )
    }
}
