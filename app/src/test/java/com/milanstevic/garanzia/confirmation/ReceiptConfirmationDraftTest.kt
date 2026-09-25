package com.milanstevic.garanzia.confirmation

import com.milanstevic.garanzia.intelligence.InterpretedField
import com.milanstevic.garanzia.intelligence.ReceiptInterpretation
import com.milanstevic.garanzia.intelligence.ReceiptPaymentMethod
import com.milanstevic.garanzia.intelligence.ReceiptProduct
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptConfirmationDraftTest {

    @Test
    fun manualFallbackStartsEmptyAndRequiresUserConfirmation() {
        val draft = ReceiptConfirmationDraft.manualFallback()

        assertTrue(ConfirmationField.MERCHANT in draft.fieldsToReview)
        assertTrue(ConfirmationField.PURCHASE_DATE in draft.fieldsToReview)
        assertTrue(ConfirmationField.TOTAL in draft.fieldsToReview)
        assertTrue(draft.products.isEmpty())
        assertFalse(draft.canConfirm)
    }

    @Test
    fun highConfidenceReceiptNeedsNoAttentionAndCanConfirm() {
        val draft = ReceiptConfirmationDraft.from(
            interpretation(
                merchant = field("NEGOZIO TEST SRL", 0.95f),
                date = field(LocalDate.of(2026, 9, 24), 0.95f),
                total = field(BigDecimal("99.90"), 0.95f),
                products = listOf(
                    field(
                        ReceiptProduct(
                            name = "TRAPANO BOSCH",
                            quantity = BigDecimal.ONE,
                            unitPrice = BigDecimal("99.90"),
                            lineTotal = BigDecimal("99.90"),
                        ),
                        0.93f,
                    ),
                ),
            ),
        )

        assertEquals(0, draft.attentionCount)
        assertTrue(draft.canConfirm)
        assertEquals("24/09/2026", draft.purchaseDate)
        assertEquals("99,90", draft.totalAmount)
    }

    @Test
    fun missingProductBlocksConfirmation() {
        val draft = ReceiptConfirmationDraft.from(
            interpretation(
                merchant = field("NEGOZIO TEST SRL", 0.95f),
                date = field(LocalDate.of(2026, 9, 24), 0.95f),
                total = field(BigDecimal("99.90"), 0.95f),
                products = emptyList(),
            ),
        )

        assertEquals(1, draft.attentionCount)
        assertFalse(draft.canConfirm)
    }

    @Test
    fun missingRequiredFieldsAreFlaggedAndBlockConfirmation() {
        val draft = ReceiptConfirmationDraft.from(
            interpretation(
                merchant = null,
                date = null,
                total = null,
            ),
        )

        assertTrue(ConfirmationField.MERCHANT in draft.fieldsToReview)
        assertTrue(ConfirmationField.PURCHASE_DATE in draft.fieldsToReview)
        assertTrue(ConfirmationField.TOTAL in draft.fieldsToReview)
        assertFalse(draft.canConfirm)
    }

    @Test
    fun lowConfidenceProductIsFlaggedForReview() {
        val draft = ReceiptConfirmationDraft.from(
            interpretation(
                merchant = field("NEGOZIO TEST SRL", 0.95f),
                date = field(LocalDate.of(2026, 9, 24), 0.95f),
                total = field(BigDecimal("20.00"), 0.95f),
                products = listOf(
                    field(
                        ReceiptProduct(
                            name = "ARTICOLO DUBBIO",
                            quantity = null,
                            unitPrice = null,
                            lineTotal = BigDecimal("20.00"),
                        ),
                        0.60f,
                    ),
                ),
            ),
        )

        assertTrue(draft.products.single().requiresReview)
        assertEquals(1, draft.attentionCount)
    }

    @Test
    fun italianMoneyAndDateValidationAcceptExpectedFormats() {
        assertEquals(BigDecimal("1299.90"), ReceiptConfirmationDraft.parseMoney("1.299,90 €"))
        assertEquals(BigDecimal("12.50"), ReceiptConfirmationDraft.parseMoney("12,50"))
        assertTrue(ReceiptConfirmationDraft.isValidDate("24/09/2026"))
        assertFalse(ReceiptConfirmationDraft.isValidDate("31/02/2026"))
    }

    private fun interpretation(
        merchant: InterpretedField<String>?,
        date: InterpretedField<LocalDate>?,
        total: InterpretedField<BigDecimal>?,
        products: List<InterpretedField<ReceiptProduct>> = emptyList(),
    ) = ReceiptInterpretation(
        merchant = merchant,
        purchaseDate = date,
        purchaseTime = field(LocalTime.of(10, 30), 0.95f),
        totalAmount = total,
        currency = field("EUR", 0.95f),
        vatNumber = null,
        documentNumber = null,
        paymentMethod = field(ReceiptPaymentMethod.CARD, 0.95f),
        products = products,
    )

    private fun <T> field(
        value: T,
        confidence: Float,
    ) = InterpretedField(
        value = value,
        confidence = confidence,
        evidence = value.toString(),
        rule = "test",
    )
}
