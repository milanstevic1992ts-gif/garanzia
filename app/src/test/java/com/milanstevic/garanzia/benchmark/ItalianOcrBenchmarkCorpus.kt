package com.milanstevic.garanzia.benchmark

import com.milanstevic.garanzia.intelligence.ReceiptPaymentMethod
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

data class BenchmarkLine(
    val text: String,
    val confidence: Float = 0.95f,
)

data class BenchmarkProductExpectation(
    val name: String,
    val quantity: BigDecimal? = null,
    val unitPrice: BigDecimal? = null,
    val lineTotal: BigDecimal? = null,
)

data class BenchmarkExpected(
    val merchant: String?,
    val purchaseDate: LocalDate?,
    val purchaseTime: LocalTime? = null,
    val totalAmount: BigDecimal?,
    val currency: String? = "EUR",
    val vatNumber: String? = null,
    val documentNumber: String? = null,
    val paymentMethod: ReceiptPaymentMethod? = null,
    val products: List<BenchmarkProductExpectation> = emptyList(),
    val needsReview: Boolean = false,
)

data class ItalianOcrBenchmarkCase(
    val id: String,
    val description: String,
    val pages: List<List<BenchmarkLine>>,
    val expected: BenchmarkExpected,
)

object ItalianOcrBenchmarkCorpus {

    val cases: List<ItalianOcrBenchmarkCase> = listOf(
        ItalianOcrBenchmarkCase(
            id = "ferramenta_completo",
            description = "Ferramenta, documento completo con tre prodotti e carta.",
            pages = pages(
                "FERRAMENTA ALFA SRL",
                "Via Roma 12",
                "P.IVA IT12345678901",
                "DOCUMENTO COMMERCIALE N. 1234-5678",
                "DATA 22/09/2026 ORA 18:43",
                "TRAPANO 18V 129,90",
                "BATTERIA 18V 59,90",
                "SET PUNTE HSS 14,50",
                "SUBTOTALE 204,30",
                "TOTALE € 204,30",
                "PAGAMENTO VISA",
            ),
            expected = BenchmarkExpected(
                merchant = "FERRAMENTA ALFA SRL",
                purchaseDate = LocalDate.of(2026, 9, 22),
                purchaseTime = LocalTime.of(18, 43),
                totalAmount = BigDecimal("204.30"),
                vatNumber = "IT12345678901",
                documentNumber = "1234-5678",
                paymentMethod = ReceiptPaymentMethod.CARD,
                products = listOf(
                    product("TRAPANO 18V", total = "129.90"),
                    product("BATTERIA 18V", total = "59.90"),
                    product("SET PUNTE HSS", total = "14.50"),
                ),
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "supermercato_contanti",
            description = "Supermercato con data corta, contanti e prezzi riga.",
            pages = pages(
                "MARKET BETA SNC",
                "DATA 3-9-26",
                "ORA 09:07",
                "PASTA INTEGRALE 1,89",
                "LATTE INTERO 1,69",
                "CAFFE MACINATO 4,79",
                "TOTALE 8,37 €",
                "CONTANTI",
            ),
            expected = BenchmarkExpected(
                merchant = "MARKET BETA SNC",
                purchaseDate = LocalDate.of(2026, 9, 3),
                purchaseTime = LocalTime.of(9, 7),
                totalAmount = BigDecimal("8.37"),
                paymentMethod = ReceiptPaymentMethod.CASH,
                products = listOf(
                    product("PASTA INTEGRALE", total = "1.89"),
                    product("LATTE INTERO", total = "1.69"),
                    product("CAFFE MACINATO", total = "4.79"),
                ),
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "quantita_x_prezzo",
            description = "Prodotto su due righe con quantità x prezzo unitario.",
            pages = pages(
                "EDILIZIA GAMMA SRL",
                "DATA 18/09/2026",
                "TASSELLI UNIVERSALI 8MM",
                "2 x 4,50 9,00",
                "TOTALE 9,00 EUR",
                "PAGAMENTO MASTERCARD",
            ),
            expected = BenchmarkExpected(
                merchant = "EDILIZIA GAMMA SRL",
                purchaseDate = LocalDate.of(2026, 9, 18),
                totalAmount = BigDecimal("9.00"),
                paymentMethod = ReceiptPaymentMethod.CARD,
                products = listOf(
                    product(
                        name = "TASSELLI UNIVERSALI 8MM",
                        quantity = "2",
                        unit = "4.50",
                        total = "9.00",
                    ),
                ),
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "quantita_pezzi",
            description = "Quantità italiana in PZ sulla stessa riga.",
            pages = pages(
                "UTENSILI DELTA SRL",
                "DATA 19/09/2026",
                "ARTICOLO 2 PZ GUANTI LAVORO 12,00",
                "TOTALE 12,00 €",
                "PAGATO BANCOMAT",
            ),
            expected = BenchmarkExpected(
                merchant = "UTENSILI DELTA SRL",
                purchaseDate = LocalDate.of(2026, 9, 19),
                totalAmount = BigDecimal("12.00"),
                paymentMethod = ReceiptPaymentMethod.CARD,
                products = listOf(
                    product("GUANTI LAVORO", quantity = "2", total = "12.00"),
                ),
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "migliaia_italiane",
            description = "Totale italiano con separatore delle migliaia.",
            pages = pages(
                "CASA EPSILON SPA",
                "DATA 20/09/2026",
                "MOBILE BAGNO 899,90",
                "SPECCHIO LED 400,00",
                "TOTALE 1.299,90 €",
                "PAGAMENTO VISA",
            ),
            expected = BenchmarkExpected(
                merchant = "CASA EPSILON SPA",
                purchaseDate = LocalDate.of(2026, 9, 20),
                totalAmount = BigDecimal("1299.90"),
                paymentMethod = ReceiptPaymentMethod.CARD,
                products = listOf(
                    product("MOBILE BAGNO", total = "899.90"),
                    product("SPECCHIO LED", total = "400.00"),
                ),
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "carta_fedelta",
            description = "Carta fedeltà presente ma nessun metodo di pagamento esplicito.",
            pages = pages(
                "EMPORIO ZETA SRL",
                "DATA 21/09/2026",
                "CARTA FEDELTA 123456789",
                "DETERGENTE CASA 5,90",
                "TOTALE 5,90 €",
            ),
            expected = BenchmarkExpected(
                merchant = "EMPORIO ZETA SRL",
                purchaseDate = LocalDate.of(2026, 9, 21),
                totalAmount = BigDecimal("5.90"),
                paymentMethod = null,
                products = listOf(
                    product("DETERGENTE CASA", total = "5.90"),
                ),
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "solo_subtotale",
            description = "Nessun totale reale: il subtotale non deve essere inventato come totale.",
            pages = pages(
                "NEGOZIO ETA SRL",
                "DATA 22/09/2026",
                "NASTRO CARTA 3,50",
                "SUBTOTALE 3,50",
                "IVA 0,77",
            ),
            expected = BenchmarkExpected(
                merchant = "NEGOZIO ETA SRL",
                purchaseDate = LocalDate.of(2026, 9, 22),
                totalAmount = null,
                currency = null,
                products = listOf(
                    product("NASTRO CARTA", total = "3.50"),
                ),
                needsReview = true,
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "ocr_confidence_bassa",
            description = "Campi critici troppo poco affidabili: devono restare vuoti.",
            pages = listOf(
                listOf(
                    BenchmarkLine("NEGOZIO THETA SRL", 0.20f),
                    BenchmarkLine("DATA 23/09/2026", 0.20f),
                    BenchmarkLine("PRODOTTO TEST 10,00", 0.15f),
                    BenchmarkLine("TOTALE 10,00 €", 0.20f),
                ),
            ),
            expected = BenchmarkExpected(
                merchant = null,
                purchaseDate = null,
                totalAmount = null,
                currency = null,
                products = emptyList(),
                needsReview = true,
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "data_iso",
            description = "Data ISO e pagamento Maestro.",
            pages = pages(
                "BOTTEGA IOTA SRL",
                "2026-09-24",
                "LAMPADA LED 16,90",
                "TOTALE 16,90 EUR",
                "PAGAMENTO MAESTRO",
            ),
            expected = BenchmarkExpected(
                merchant = "BOTTEGA IOTA SRL",
                purchaseDate = LocalDate.of(2026, 9, 24),
                totalAmount = BigDecimal("16.90"),
                paymentMethod = ReceiptPaymentMethod.CARD,
                products = listOf(
                    product("LAMPADA LED", total = "16.90"),
                ),
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "documento_ricevuta",
            description = "Numero ricevuta con separatori e P.IVA senza prefisso IT.",
            pages = pages(
                "SERVIZI KAPPA SNC",
                "P. IVA: 12345678901",
                "RICEVUTA N° A45/2026",
                "DATA 25/09/2026",
                "MATERIALE CONSUMO 22,40",
                "TOTALE 22,40 €",
                "CONTANTI",
            ),
            expected = BenchmarkExpected(
                merchant = "SERVIZI KAPPA SNC",
                purchaseDate = LocalDate.of(2026, 9, 25),
                totalAmount = BigDecimal("22.40"),
                vatNumber = "12345678901",
                documentNumber = "A45/2026",
                paymentMethod = ReceiptPaymentMethod.CASH,
                products = listOf(
                    product("MATERIALE CONSUMO", total = "22.40"),
                ),
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "rumore_cassa_indirizzo",
            description = "Indirizzo, cassa e operatore non devono diventare prodotti.",
            pages = pages(
                "RICAMBI LAMBDA SRL",
                "VIALE EUROPA 88",
                "CASSA 03",
                "OPERATORE 17",
                "DATA 26/09/2026",
                "FILTRO ARIA 18,00",
                "TOTALE 18,00 €",
                "PAGAMENTO POS CARTA",
            ),
            expected = BenchmarkExpected(
                merchant = "RICAMBI LAMBDA SRL",
                purchaseDate = LocalDate.of(2026, 9, 26),
                totalAmount = BigDecimal("18.00"),
                paymentMethod = ReceiptPaymentMethod.CARD,
                products = listOf(
                    product("FILTRO ARIA", total = "18.00"),
                ),
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "multipagina",
            description = "Scontrino OCR diviso su due pagine.",
            pages = listOf(
                listOf(
                    BenchmarkLine("CASALINGHI MU SRL"),
                    BenchmarkLine("P.IVA IT10987654321"),
                    BenchmarkLine("DATA 27/09/2026"),
                    BenchmarkLine("PADELLA ACCIAIO 29,90"),
                ),
                listOf(
                    BenchmarkLine("COPERCHIO VETRO 12,50"),
                    BenchmarkLine("TOTALE COMPLESSIVO 42,40 €"),
                    BenchmarkLine("PAGAMENTO VISA"),
                ),
            ),
            expected = BenchmarkExpected(
                merchant = "CASALINGHI MU SRL",
                purchaseDate = LocalDate.of(2026, 9, 27),
                totalAmount = BigDecimal("42.40"),
                vatNumber = "IT10987654321",
                paymentMethod = ReceiptPaymentMethod.CARD,
                products = listOf(
                    product("PADELLA ACCIAIO", total = "29.90"),
                    product("COPERCHIO VETRO", total = "12.50"),
                ),
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "carta_abrasiva",
            description = "La parola CARTA nel prodotto non deve essere scambiata per pagamento.",
            pages = pages(
                "COLORI NU SRL",
                "DATA 27/09/2026",
                "CARTA ABRASIVA GRANA 120 6,90",
                "TOTALE 6,90 €",
                "PAGAMENTO CARTA DI CREDITO",
            ),
            expected = BenchmarkExpected(
                merchant = "COLORI NU SRL",
                purchaseDate = LocalDate.of(2026, 9, 27),
                totalAmount = BigDecimal("6.90"),
                paymentMethod = ReceiptPaymentMethod.CARD,
                products = listOf(
                    product("CARTA ABRASIVA GRANA 120", total = "6.90"),
                ),
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "bonifico",
            description = "Pagamento tramite bonifico.",
            pages = pages(
                "FORNITURE XI SRL",
                "DATA 27/09/2026",
                "MALTA RAPIDA 32,00",
                "TOTALE 32,00 €",
                "PAGAMENTO BONIFICO",
            ),
            expected = BenchmarkExpected(
                merchant = "FORNITURE XI SRL",
                purchaseDate = LocalDate.of(2026, 9, 27),
                totalAmount = BigDecimal("32.00"),
                paymentMethod = ReceiptPaymentMethod.BANK_TRANSFER,
                products = listOf(
                    product("MALTA RAPIDA", total = "32.00"),
                ),
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "iva_e_grand_total",
            description = "IVA separata e totale complessivo: deve vincere il totale reale.",
            pages = pages(
                "MATERIALI OMICRON SRL",
                "DATA 27/09/2026",
                "ADESIVO CERAMICA 81,97",
                "TOTALE IVA 18,03",
                "TOTALE COMPLESSIVO 100,00 EUR",
                "PAGATO VISA",
            ),
            expected = BenchmarkExpected(
                merchant = "MATERIALI OMICRON SRL",
                purchaseDate = LocalDate.of(2026, 9, 27),
                totalAmount = BigDecimal("100.00"),
                paymentMethod = ReceiptPaymentMethod.CARD,
                products = listOf(
                    product("ADESIVO CERAMICA", total = "81.97"),
                ),
            ),
        ),
        ItalianOcrBenchmarkCase(
            id = "totale_corrotto_non_inventare",
            description = "Etichetta totale OCR corrotta: meglio revisione che inventare il totale.",
            pages = pages(
                "NEGOZIO PI SRL",
                "DATA 27/09/2026",
                "SPUGNA ABRASIVA 4,50",
                "T0TALE 4,50 €",
            ),
            expected = BenchmarkExpected(
                merchant = "NEGOZIO PI SRL",
                purchaseDate = LocalDate.of(2026, 9, 27),
                totalAmount = null,
                currency = "EUR",
                products = listOf(
                    product("SPUGNA ABRASIVA", total = "4.50"),
                    product("T0TALE", total = "4.50"),
                ),
                needsReview = true,
            ),
        ),
    )

    private fun pages(vararg lines: String): List<List<BenchmarkLine>> =
        listOf(lines.map(::BenchmarkLine))

    private fun product(
        name: String,
        quantity: String? = null,
        unit: String? = null,
        total: String? = null,
    ) = BenchmarkProductExpectation(
        name = name,
        quantity = quantity?.let(::BigDecimal),
        unitPrice = unit?.let(::BigDecimal),
        lineTotal = total?.let(::BigDecimal),
    )
}
