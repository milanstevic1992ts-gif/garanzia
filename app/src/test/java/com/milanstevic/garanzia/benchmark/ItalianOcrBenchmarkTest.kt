package com.milanstevic.garanzia.benchmark

import com.milanstevic.garanzia.intelligence.ReceiptInterpreter
import com.milanstevic.garanzia.intelligence.ReceiptProduct
import com.milanstevic.garanzia.ocr.OcrLine
import com.milanstevic.garanzia.ocr.OcrPageResult
import com.milanstevic.garanzia.ocr.OcrReceiptResult
import java.io.File
import java.math.BigDecimal
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test

class ItalianOcrBenchmarkTest {

    private val interpreter = ReceiptInterpreter()

    @Test
    fun italianReceiptCorpusMeetsQualityGate() {
        val caseResults = ItalianOcrBenchmarkCorpus.cases.map(::evaluate)
        val metrics = BenchmarkMetrics.from(caseResults)

        writeReport(metrics, caseResults)

        assertTrue(
            "Merchant accuracy ${metrics.merchantAccuracy.asPercent()} below 95%",
            metrics.merchantAccuracy >= 0.95,
        )
        assertTrue(
            "Date accuracy ${metrics.dateAccuracy.asPercent()} below 95%",
            metrics.dateAccuracy >= 0.95,
        )
        assertTrue(
            "Total accuracy ${metrics.totalAccuracy.asPercent()} below 95%",
            metrics.totalAccuracy >= 0.95,
        )
        assertTrue(
            "Payment accuracy ${metrics.paymentAccuracy.asPercent()} below 95%",
            metrics.paymentAccuracy >= 0.95,
        )
        assertTrue(
            "Product precision ${metrics.productPrecision.asPercent()} below 95%",
            metrics.productPrecision >= 0.95,
        )
        assertTrue(
            "Product recall ${metrics.productRecall.asPercent()} below 95%",
            metrics.productRecall >= 0.95,
        )
        assertTrue(
            "Review decision accuracy ${metrics.reviewAccuracy.asPercent()} below 100%",
            metrics.reviewAccuracy >= 1.0,
        )
        assertTrue(
            "At least one benchmark case failed:\n" +
                caseResults.filterNot { it.passed }.joinToString("\n") { it.summary },
            caseResults.all { it.passed },
        )
    }

    private fun evaluate(case: ItalianOcrBenchmarkCase): CaseResult {
        val interpretation = interpreter.interpret(case.toReceipt())
        val expected = case.expected

        val checks = linkedMapOf<String, Boolean>(
            "merchant" to (interpretation.merchant?.value == expected.merchant),
            "date" to (interpretation.purchaseDate?.value == expected.purchaseDate),
            "time" to (interpretation.purchaseTime?.value == expected.purchaseTime),
            "total" to bigDecimalEquals(
                interpretation.totalAmount?.value,
                expected.totalAmount,
            ),
            "currency" to (interpretation.currency?.value == expected.currency),
            "vat" to (interpretation.vatNumber?.value == expected.vatNumber),
            "document" to (interpretation.documentNumber?.value == expected.documentNumber),
            "payment" to (interpretation.paymentMethod?.value == expected.paymentMethod),
            "needsReview" to (interpretation.needsReview == expected.needsReview),
        )

        val expectedProducts = expected.products.map(::productKey).toSet()
        val actualProducts = interpretation.products.map { productKey(it.value) }.toSet()
        val trueProducts = expectedProducts.intersect(actualProducts).size
        val falseProducts = actualProducts.subtract(expectedProducts).size
        val missedProducts = expectedProducts.subtract(actualProducts).size

        return CaseResult(
            id = case.id,
            description = case.description,
            checks = checks,
            expectedProducts = expectedProducts,
            actualProducts = actualProducts,
            trueProducts = trueProducts,
            falseProducts = falseProducts,
            missedProducts = missedProducts,
        )
    }

    private fun ItalianOcrBenchmarkCase.toReceipt(): OcrReceiptResult =
        OcrReceiptResult(
            pages = pages.mapIndexed { pageIndex, pageLines ->
                OcrPageResult(
                    pageIndex = pageIndex,
                    lines = pageLines.map { line ->
                        OcrLine(
                            text = line.text,
                            confidence = line.confidence,
                            box = emptyList(),
                        )
                    },
                    rawText = pageLines.joinToString("\n") { it.text },
                    totalTimeMs = 0,
                )
            },
        )

    private fun productKey(expected: BenchmarkProductExpectation): String =
        listOf(
            normalizeName(expected.name),
            decimalKey(expected.quantity),
            decimalKey(expected.unitPrice),
            decimalKey(expected.lineTotal),
        ).joinToString("|")

    private fun productKey(actual: ReceiptProduct): String =
        listOf(
            normalizeName(actual.name),
            decimalKey(actual.quantity),
            decimalKey(actual.unitPrice),
            decimalKey(actual.lineTotal),
        ).joinToString("|")

    private fun normalizeName(value: String): String =
        value.uppercase()
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun decimalKey(value: BigDecimal?): String =
        value?.stripTrailingZeros()?.toPlainString().orEmpty()

    private fun bigDecimalEquals(
        actual: BigDecimal?,
        expected: BigDecimal?,
    ): Boolean =
        when {
            actual == null && expected == null -> true
            actual == null || expected == null -> false
            else -> actual.compareTo(expected) == 0
        }

    private fun writeReport(
        metrics: BenchmarkMetrics,
        cases: List<CaseResult>,
    ) {
        val reportDir = File(
            System.getProperty("ocrBenchmarkReportDir")
                ?: "build/reports/ocr-benchmark",
        )
        reportDir.mkdirs()

        File(reportDir, "italian-ocr-benchmark.txt").writeText(
            buildString {
                appendLine("GARANZIA — BANCO PROVA OCR ITALIANO")
                appendLine("Corpus: ${cases.size} casi")
                appendLine("Passati: ${cases.count { it.passed }}/${cases.size}")
                appendLine("Merchant accuracy: ${metrics.merchantAccuracy.asPercent()}")
                appendLine("Date accuracy: ${metrics.dateAccuracy.asPercent()}")
                appendLine("Total accuracy: ${metrics.totalAccuracy.asPercent()}")
                appendLine("Payment accuracy: ${metrics.paymentAccuracy.asPercent()}")
                appendLine("Product precision: ${metrics.productPrecision.asPercent()}")
                appendLine("Product recall: ${metrics.productRecall.asPercent()}")
                appendLine("Review accuracy: ${metrics.reviewAccuracy.asPercent()}")
                appendLine()
                cases.forEach { result ->
                    appendLine(result.summary)
                }
            },
        )

        File(reportDir, "italian-ocr-benchmark.json").writeText(
            buildString {
                append("{\n")
                append("  \"caseCount\": ${cases.size},\n")
                append("  \"passedCases\": ${cases.count { it.passed }},\n")
                append("  \"merchantAccuracy\": ${metrics.merchantAccuracy},\n")
                append("  \"dateAccuracy\": ${metrics.dateAccuracy},\n")
                append("  \"totalAccuracy\": ${metrics.totalAccuracy},\n")
                append("  \"paymentAccuracy\": ${metrics.paymentAccuracy},\n")
                append("  \"productPrecision\": ${metrics.productPrecision},\n")
                append("  \"productRecall\": ${metrics.productRecall},\n")
                append("  \"reviewAccuracy\": ${metrics.reviewAccuracy},\n")
                append("  \"cases\": [\n")
                cases.forEachIndexed { index, result ->
                    append("    {\"id\": \"${escapeJson(result.id)}\", ")
                    append("\"passed\": ${result.passed}, ")
                    append("\"falseProducts\": ${result.falseProducts}, ")
                    append("\"missedProducts\": ${result.missedProducts}}")
                    if (index != cases.lastIndex) append(",")
                    append("\n")
                }
                append("  ]\n")
                append("}\n")
            },
        )
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private data class CaseResult(
        val id: String,
        val description: String,
        val checks: Map<String, Boolean>,
        val expectedProducts: Set<String>,
        val actualProducts: Set<String>,
        val trueProducts: Int,
        val falseProducts: Int,
        val missedProducts: Int,
    ) {
        val passed: Boolean
            get() = checks.values.all { it } && falseProducts == 0 && missedProducts == 0

        val summary: String
            get() {
                val failedChecks = checks.filterValues { !it }.keys
                val status = if (passed) "PASS" else "FAIL"
                return buildString {
                    append("[$status] $id — $description")
                    if (failedChecks.isNotEmpty()) {
                        append(" | campi: ")
                        append(failedChecks.joinToString(", "))
                    }
                    if (falseProducts > 0) {
                        append(" | falsi prodotti: ")
                        append(actualProducts.subtract(expectedProducts))
                    }
                    if (missedProducts > 0) {
                        append(" | prodotti mancanti: ")
                        append(expectedProducts.subtract(actualProducts))
                    }
                }
            }
    }

    private data class BenchmarkMetrics(
        val merchantAccuracy: Double,
        val dateAccuracy: Double,
        val totalAccuracy: Double,
        val paymentAccuracy: Double,
        val productPrecision: Double,
        val productRecall: Double,
        val reviewAccuracy: Double,
    ) {
        companion object {
            fun from(cases: List<CaseResult>): BenchmarkMetrics {
                fun fieldAccuracy(name: String): Double =
                    cases.count { it.checks[name] == true }.toDouble() / cases.size

                val trueProducts = cases.sumOf { it.trueProducts }
                val falseProducts = cases.sumOf { it.falseProducts }
                val missedProducts = cases.sumOf { it.missedProducts }

                val precision =
                    if (trueProducts + falseProducts == 0) {
                        1.0
                    } else {
                        trueProducts.toDouble() / (trueProducts + falseProducts)
                    }
                val recall =
                    if (trueProducts + missedProducts == 0) {
                        1.0
                    } else {
                        trueProducts.toDouble() / (trueProducts + missedProducts)
                    }

                return BenchmarkMetrics(
                    merchantAccuracy = fieldAccuracy("merchant"),
                    dateAccuracy = fieldAccuracy("date"),
                    totalAccuracy = fieldAccuracy("total"),
                    paymentAccuracy = fieldAccuracy("payment"),
                    productPrecision = precision,
                    productRecall = recall,
                    reviewAccuracy = fieldAccuracy("needsReview"),
                )
            }
        }
    }

    private fun Double.asPercent(): String =
        "${(this * 1000.0).roundToInt() / 10.0}%"
}
