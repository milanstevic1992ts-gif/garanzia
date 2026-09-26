package com.milanstevic.garanzia.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.milanstevic.garanzia.ui.components.GaranziaHeader
import com.milanstevic.garanzia.ui.components.KeyValueRow
import com.milanstevic.garanzia.ui.components.SectionCard
import com.milanstevic.garanzia.ui.components.StatusPill
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun ProductDetailScreen(
    product: ProductDetailData,
    onOpenReceiptPdf: () -> Unit,
    onEditReceipt: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onBack) {
                    Text("Indietro")
                }

                GaranziaHeader(
                    eyebrow = "Scheda prodotto",
                    title = product.name,
                    subtitle = "${product.merchant} · ${formatDate(product.purchaseDate)}",
                    modifier = Modifier.weight(1f),
                )
            }

            product.sourceConfidence?.let { confidence ->
                StatusPill(
                    text = "Riconoscimento ${(confidence * 100f).roundToInt()}%",
                    positive = confidence >= 0.70f,
                )
            }

            SectionCard(
                title = "Prodotto",
                subtitle = "Dati confermati nello scontrino salvato.",
            ) {
                product.quantity?.let {
                    KeyValueRow(
                        label = "Quantità",
                        value = formatDecimal(it),
                    )
                }

                product.unitPrice?.let {
                    KeyValueRow(
                        label = "Prezzo unitario",
                        value = formatAmount(it, product.currency),
                    )
                }

                product.lineTotal?.let {
                    KeyValueRow(
                        label = "Importo riga",
                        value = formatAmount(it, product.currency),
                    )
                }
            }

            SectionCard(
                title = "Acquisto",
                subtitle = "Origine del prodotto nell'archivio.",
            ) {
                KeyValueRow(
                    label = "Negozio",
                    value = product.merchant,
                )
                KeyValueRow(
                    label = "Data",
                    value = formatDate(product.purchaseDate),
                )
                product.purchaseTime?.let {
                    KeyValueRow(
                        label = "Ora",
                        value = it,
                    )
                }
                product.documentNumber?.let {
                    KeyValueRow(
                        label = "Documento",
                        value = it,
                    )
                }
                product.vatNumber?.let {
                    KeyValueRow(
                        label = "Partita IVA",
                        value = it,
                    )
                }
                product.paymentMethod?.let {
                    KeyValueRow(
                        label = "Pagamento",
                        value = it,
                    )
                }
            }

            SectionCard(
                title = "Documento originale",
                subtitle =
                    if (product.pageCount == 1) {
                        "1 pagina originale conservata."
                    } else {
                        "${product.pageCount} pagine originali conservate."
                    },
            ) {
                Button(
                    onClick = onOpenReceiptPdf,
                    enabled = product.pageCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Apri PDF dello scontrino")
                }
            }

            SectionCard(
                title = "Gestione",
                subtitle = "Le modifiche al prodotto restano legate allo scontrino originale.",
            ) {
                OutlinedButton(
                    onClick = onEditReceipt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Modifica scontrino")
                }
            }
        }
    }
}

private fun formatDate(isoDate: String): String =
    runCatching {
        LocalDate
            .parse(isoDate)
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrDefault(isoDate)

private fun formatAmount(
    storedAmount: String,
    currency: String?,
): String {
    val amount = runCatching { BigDecimal(storedAmount) }.getOrNull()
    val number = amount
        ?.setScale(2, RoundingMode.HALF_UP)
        ?.toPlainString()
        ?.replace('.', ',')
        ?: storedAmount

    return when (currency) {
        "EUR" -> "$number €"
        "USD" -> "$number \$"
        "GBP" -> "$number £"
        null -> number
        else -> "$number $currency"
    }
}

private fun formatDecimal(value: String): String =
    runCatching {
        BigDecimal(value)
            .stripTrailingZeros()
            .toPlainString()
    }.getOrDefault(value)
        .replace('.', ',')
