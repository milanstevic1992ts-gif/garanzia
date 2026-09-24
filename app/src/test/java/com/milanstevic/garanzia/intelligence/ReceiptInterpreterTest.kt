package com.milanstevic.garanzia.intelligence

import com.milanstevic.garanzia.ocr.OcrLine
import com.milanstevic.garanzia.ocr.OcrPageResult
import com.milanstevic.garanzia.ocr.OcrReceiptResult
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptInterpreterTest {

    private val interpreter = ReceiptInterpreter()

    @Test
    fun interpretsTypicalItalianReceipt() {
        val result = interpreter.interpret(
            receiptOf(
                "MEDIAWORLD S.P.A.",
                "Via Example 12",
                "P.IVA IT12345678901",
                "DOCUMENTO COMMERCIALE N. 1234-5678",
                "DATA 22/09/2026 ORA 18:43",
                "SUBTOTALE 899,00",
                "TOTALE € 899,00",
                "PAGAMENTO VISA",
            ),
        )

        assertEquals("MEDIAWORLD S.P.A.", result.merchant)
        assertEquals(LocalDate.of(2026, 9, 22), result.purchaseDate)
        assertEquals(LocalTime.of(18, 43), result.purchaseTime)
        assertEquals(BigDecimal("899.00"), result.totalAmount)
        assertEquals("EUR", result.currency)
        assertEquals("IT12345678901", result.vatNumber)
        assertEquals("1234-5678", result.documentNumber)
        assertEquals(ReceiptPaymentMethod.CARD, result.paymentMethod)
    }

    @Test
    fun prefersGrandTotalOverSubtotalAndVat() {
        val result = interpreter.interpret(
            receiptOf(
                "NEGOZIO TEST SRL",
                "SUBTOTALE 81,97",
                "TOTALE IVA 18,03",
                "TOTALE COMPLESSIVO 100,00 EUR",
            ),
        )

        assertEquals(BigDecimal("100.00"), result.totalAmount)
    }

    @Test
    fun leavesTotalEmptyWhenNoTotalLabelExists() {
        val result = interpreter.interpret(
            receiptOf(
                "NEGOZIO TEST SRL",
                "ARTICOLO 12,90",
                "ARTICOLO 42,50",
            ),
        )

        assertNull(result.totalAmount)
    }

    @Test
    fun acceptsShortItalianDateAndCashPayment() {
        val result = interpreter.interpret(
            receiptOf(
                "FERRAMENTA ROSSI SNC",
                "DATA 3-9-26",
                "ORA 09:07",
                "TOTALE 1.299,90 €",
                "CONTANTI",
            ),
        )

        assertEquals(LocalDate.of(2026, 9, 3), result.purchaseDate)
        assertEquals(LocalTime.of(9, 7), result.purchaseTime)
        assertEquals(BigDecimal("1299.90"), result.totalAmount)
        assertEquals(ReceiptPaymentMethod.CASH, result.paymentMethod)
    }

    private fun receiptOf(vararg lines: String): OcrReceiptResult =
        OcrReceiptResult(
            pages = listOf(
                OcrPageResult(
                    pageIndex = 0,
                    lines = lines.map {
                        OcrLine(
                            text = it,
                            confidence = 0.95f,
                            box = emptyList(),
                        )
                    },
                    rawText = lines.joinToString("\n"),
                    totalTimeMs = 0,
                ),
            ),
        )
}
