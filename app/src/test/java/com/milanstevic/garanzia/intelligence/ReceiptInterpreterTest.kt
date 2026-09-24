package com.milanstevic.garanzia.intelligence

import com.milanstevic.garanzia.ocr.OcrLine
import com.milanstevic.garanzia.ocr.OcrPageResult
import com.milanstevic.garanzia.ocr.OcrReceiptResult
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptInterpreterTest {

    private val interpreter = ReceiptInterpreter()

    @Test
    fun interpretsTypicalItalianReceiptWithConfidenceAndEvidence() {
        val result = interpreter.interpret(
            receiptOf(
                line("MEDIAWORLD S.P.A."),
                line("Via Example 12"),
                line("P.IVA IT12345678901"),
                line("DOCUMENTO COMMERCIALE N. 1234-5678"),
                line("DATA 22/09/2026 ORA 18:43"),
                line("SUBTOTALE 899,00"),
                line("TOTALE € 899,00"),
                line("PAGAMENTO VISA"),
            ),
        )

        assertEquals("MEDIAWORLD S.P.A.", result.merchant?.value)
        assertEquals(LocalDate.of(2026, 9, 22), result.purchaseDate?.value)
        assertEquals(LocalTime.of(18, 43), result.purchaseTime?.value)
        assertEquals(BigDecimal("899.00"), result.totalAmount?.value)
        assertEquals("EUR", result.currency?.value)
        assertEquals("IT12345678901", result.vatNumber?.value)
        assertEquals("1234-5678", result.documentNumber?.value)
        assertEquals(ReceiptPaymentMethod.CARD, result.paymentMethod?.value)

        assertEquals("TOTALE € 899,00", result.totalAmount?.evidence)
        assertEquals(ConfidenceLevel.HIGH, result.totalAmount?.level)
    }

    @Test
    fun prefersGrandTotalOverVatAndNeverPromotesSubtotal() {
        val result = interpreter.interpret(
            receiptOf(
                line("NEGOZIO TEST SRL"),
                line("SUBTOTALE 81,97"),
                line("TOTALE IVA 18,03"),
                line("TOTALE COMPLESSIVO 100,00 EUR"),
            ),
        )

        assertEquals(BigDecimal("100.00"), result.totalAmount?.value)
        assertEquals("TOTALE COMPLESSIVO 100,00 EUR", result.totalAmount?.evidence)
    }

    @Test
    fun leavesTotalEmptyWhenOnlySubtotalOrProductPricesExist() {
        val result = interpreter.interpret(
            receiptOf(
                line("NEGOZIO TEST SRL"),
                line("ARTICOLO 12,90"),
                line("ARTICOLO 42,50"),
                line("SUBTOTALE 55,40"),
            ),
        )

        assertNull(result.totalAmount)
    }

    @Test
    fun acceptsShortItalianDateAndCashPayment() {
        val result = interpreter.interpret(
            receiptOf(
                line("FERRAMENTA ROSSI SNC"),
                line("DATA 3-9-26"),
                line("ORA 09:07"),
                line("TOTALE 1.299,90 €"),
                line("CONTANTI"),
            ),
        )

        assertEquals(LocalDate.of(2026, 9, 3), result.purchaseDate?.value)
        assertEquals(LocalTime.of(9, 7), result.purchaseTime?.value)
        assertEquals(BigDecimal("1299.90"), result.totalAmount?.value)
        assertEquals(ReceiptPaymentMethod.CASH, result.paymentMethod?.value)
    }

    @Test
    fun rejectsFieldsWhenOcrConfidenceIsTooLow() {
        val result = interpreter.interpret(
            receiptOf(
                line("NEGOZIO TEST SRL", confidence = 0.20f),
                line("DATA 22/09/2026", confidence = 0.20f),
                line("TOTALE 100,00 €", confidence = 0.20f),
            ),
        )

        assertNull(result.merchant)
        assertNull(result.purchaseDate)
        assertNull(result.totalAmount)
    }

    @Test
    fun loyaltyCardDoesNotBecomePaymentMethod() {
        val result = interpreter.interpret(
            receiptOf(
                line("NEGOZIO TEST SRL"),
                line("CARTA FEDELTA 123456"),
                line("DATA 22/09/2026"),
                line("TOTALE 25,00 €"),
            ),
        )

        assertNull(result.paymentMethod)
    }

    @Test
    fun lowAcceptedConfidenceRequestsReview() {
        val result = interpreter.interpret(
            receiptOf(
                line("NEGOZIO TEST SRL"),
                line("DATA 22/09/2026"),
                line("TOTALE 100,00 €", confidence = 0.55f),
            ),
        )

        assertEquals(ConfidenceLevel.LOW, result.totalAmount?.level)
        assertTrue(result.needsReview)
    }


    @Test
    fun attachesDetectedProductsToReceiptInterpretation() {
        val result = interpreter.interpret(
            receiptOf(
                line("FERRAMENTA ROSSI SRL"),
                line("DATA 22/09/2026"),
                line("TRAPANO BOSCH 99,90"),
                line("BATTERIA 18V 49,90"),
                line("TOTALE 149,80 €"),
            ),
        )

        assertEquals(2, result.products.size)
        assertEquals("TRAPANO BOSCH", result.products[0].value.name)
        assertEquals("BATTERIA 18V", result.products[1].value.name)
    }

    private fun line(
        text: String,
        confidence: Float = 0.95f,
    ) = TestLine(text, confidence)

    private fun receiptOf(vararg lines: TestLine): OcrReceiptResult =
        OcrReceiptResult(
            pages = listOf(
                OcrPageResult(
                    pageIndex = 0,
                    lines = lines.map {
                        OcrLine(
                            text = it.text,
                            confidence = it.confidence,
                            box = emptyList(),
                        )
                    },
                    rawText = lines.joinToString("\n") { it.text },
                    totalTimeMs = 0,
                ),
            ),
        )

    private data class TestLine(
        val text: String,
        val confidence: Float,
    )
}
