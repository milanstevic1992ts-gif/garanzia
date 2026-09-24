package com.milanstevic.garanzia.intelligence

import com.milanstevic.garanzia.ocr.OcrLine
import com.milanstevic.garanzia.ocr.OcrPageResult
import com.milanstevic.garanzia.ocr.OcrReceiptResult
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptProductInterpreterTest {

    private val interpreter = ReceiptProductInterpreter()

    @Test
    fun extractsMultipleProductsFromSameLineLayout() {
        val products = interpreter.interpret(
            receipt = receiptOf(
                line("FERRAMENTA ROSSI SRL"),
                line("TRAPANO BOSCH GSB 18V 129,90"),
                line("BATTERIA BOSCH 18V 59,90"),
                line("SET PUNTE HSS 14,50"),
                line("SUBTOTALE 204,30"),
                line("TOTALE 204,30 €"),
                line("PAGAMENTO VISA"),
            ),
            merchantEvidence = "FERRAMENTA ROSSI SRL",
        )

        assertEquals(3, products.size)
        assertEquals("TRAPANO BOSCH GSB 18V", products[0].value.name)
        assertEquals(BigDecimal("129.90"), products[0].value.lineTotal)
        assertEquals("BATTERIA BOSCH 18V", products[1].value.name)
        assertEquals(BigDecimal("59.90"), products[1].value.lineTotal)
        assertEquals("SET PUNTE HSS", products[2].value.name)
        assertEquals(BigDecimal("14.50"), products[2].value.lineTotal)
    }

    @Test
    fun combinesProductNameWithFollowingQuantityPriceLine() {
        val products = interpreter.interpret(
            receipt = receiptOf(
                line("NEGOZIO TEST SRL"),
                line("TASSELLI UNIVERSALI 8MM"),
                line("2 x 4,50 9,00"),
                line("TOTALE 9,00 €"),
            ),
            merchantEvidence = "NEGOZIO TEST SRL",
        )

        assertEquals(1, products.size)
        val product = products.single().value
        assertEquals("TASSELLI UNIVERSALI 8MM", product.name)
        assertEquals(BigDecimal("2"), product.quantity)
        assertEquals(BigDecimal("4.50"), product.unitPrice)
        assertEquals(BigDecimal("9.00"), product.lineTotal)
    }

    @Test
    fun extractsQuantityWhenNameAndPricesAreOnSameLine() {
        val products = interpreter.interpret(
            receipt = receiptOf(
                line("NEGOZIO TEST SRL"),
                line("SACCHI MACERIE 3 x 2,50 7,50"),
                line("TOTALE 7,50 €"),
            ),
            merchantEvidence = "NEGOZIO TEST SRL",
        )

        val product = products.single().value
        assertEquals("SACCHI MACERIE", product.name)
        assertEquals(BigDecimal("3"), product.quantity)
        assertEquals(BigDecimal("2.50"), product.unitPrice)
        assertEquals(BigDecimal("7.50"), product.lineTotal)
    }

    @Test
    fun doesNotTurnReceiptMetadataIntoProducts() {
        val products = interpreter.interpret(
            receipt = receiptOf(
                line("NEGOZIO TEST SRL"),
                line("Via Roma 12"),
                line("P.IVA IT12345678901"),
                line("DOCUMENTO COMMERCIALE N. 1234"),
                line("DATA 22/09/2026"),
                line("TOTALE 25,00 €"),
                line("CONTANTI 25,00"),
                line("RESTO 0,00"),
                line("GRAZIE"),
            ),
            merchantEvidence = "NEGOZIO TEST SRL",
        )

        assertEquals(0, products.size)
    }

    @Test
    fun rejectsProductWhenOcrConfidenceIsTooLow() {
        val products = interpreter.interpret(
            receipt = receiptOf(
                line("NEGOZIO TEST SRL"),
                line("TRAPANO BOSCH 99,90", confidence = 0.15f),
                line("TOTALE 99,90 €"),
            ),
            merchantEvidence = "NEGOZIO TEST SRL",
        )

        assertEquals(0, products.size)
    }

    @Test
    fun quantityWithoutExplicitSecondAmountKeepsLineTotalUnknown() {
        val products = interpreter.interpret(
            receipt = receiptOf(
                line("NEGOZIO TEST SRL"),
                line("VITI ZINCATE"),
                line("2 x 4,50"),
                line("TOTALE 9,00 €"),
            ),
            merchantEvidence = "NEGOZIO TEST SRL",
        )

        val product = products.single().value
        assertEquals(BigDecimal("2"), product.quantity)
        assertEquals(BigDecimal("4.50"), product.unitPrice)
        assertNull(product.lineTotal)
    }


    @Test
    fun cartaAbrasivaRemainsAProductNotPaymentMetadata() {
        val products = interpreter.interpret(
            receipt = receiptOf(
                line("FERRAMENTA TEST SRL"),
                line("CARTA ABRASIVA GRANA 120 6,90"),
                line("TOTALE 6,90 €"),
            ),
            merchantEvidence = "FERRAMENTA TEST SRL",
        )

        assertEquals(1, products.size)
        assertEquals("CARTA ABRASIVA GRANA 120", products.single().value.name)
    }

    @Test
    fun acceptsArticlePrefixAndPieceQuantity() {
        val products = interpreter.interpret(
            receipt = receiptOf(
                line("FERRAMENTA TEST SRL"),
                line("ARTICOLO 2 PZ GUANTI LAVORO 12,00"),
                line("TOTALE 12,00 €"),
            ),
            merchantEvidence = "FERRAMENTA TEST SRL",
        )

        val product = products.single().value
        assertEquals("GUANTI LAVORO", product.name)
        assertEquals(BigDecimal("2"), product.quantity)
        assertEquals(BigDecimal("12.00"), product.lineTotal)
    }


    @Test
    fun cassaAttrezziRemainsAProductWhileRegisterRowIsIgnored() {
        val products = interpreter.interpret(
            receipt = receiptOf(
                line("FERRAMENTA TEST SRL"),
                line("CASSA 03"),
                line("CASSA ATTREZZI 29,90"),
                line("TOTALE 29,90 €"),
            ),
            merchantEvidence = "FERRAMENTA TEST SRL",
        )

        assertEquals(1, products.size)
        assertEquals("CASSA ATTREZZI", products.single().value.name)
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
