package com.milanstevic.garanzia.archive

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.milanstevic.garanzia.data.local.ReceiptWithDetails
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ReceiptArchiveDetailScreen(
    details: ReceiptWithDetails,
    onBack: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Indietro all'archivio")
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = details.receipt.merchant,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }

                item {
                    ArchiveDetailLine("Data", formatDetailDate(details.receipt.purchaseDate))
                    details.receipt.purchaseTime?.let {
                        ArchiveDetailLine("Ora", it)
                    }
                    ArchiveDetailLine(
                        "Totale",
                        formatDetailAmount(
                            details.receipt.totalAmount,
                            details.receipt.currency,
                        ),
                    )
                    details.receipt.documentNumber?.let {
                        ArchiveDetailLine("Documento", it)
                    }
                    details.receipt.vatNumber?.let {
                        ArchiveDetailLine("Partita IVA", it)
                    }
                    details.receipt.paymentMethod?.let {
                        ArchiveDetailLine("Pagamento", it)
                    }
                }

                item {
                    HorizontalDivider()
                    Text(
                        text = "Prodotti (${details.products.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                items(
                    items = details.products.sortedBy { it.position },
                    key = { it.id },
                ) { product ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        product.quantity?.let {
                            Text("Quantità: ${formatDecimal(it)}")
                        }
                        product.unitPrice?.let {
                            Text(
                                "Prezzo unitario: ${formatDetailAmount(it, details.receipt.currency)}",
                            )
                        }
                        product.lineTotal?.let {
                            Text(
                                "Importo riga: ${formatDetailAmount(it, details.receipt.currency)}",
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider()
                    Text(
                        text = "Scontrino originale (${details.pages.size} pagina/e)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                items(
                    items = details.pages.sortedBy { it.pageIndex },
                    key = { it.id },
                ) { page ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Pagina ${page.pageIndex + 1}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        AsyncImage(
                            model = Uri.parse(page.originalUri),
                            contentDescription = "Pagina ${page.pageIndex + 1} dello scontrino",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }

                details.receipt.rawOcrText?.let { raw ->
                    item {
                        HorizontalDivider()
                        Text(
                            text = "Testo OCR originale",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Text(
                            text = raw,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveDetailLine(
    label: String,
    value: String,
) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyLarge,
    )
}

private fun formatDetailDate(isoDate: String): String =
    runCatching {
        LocalDate
            .parse(isoDate)
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrDefault(isoDate)

private fun formatDetailAmount(
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
    runCatching { BigDecimal(value).stripTrailingZeros().toPlainString() }
        .getOrDefault(value)
        .replace('.', ',')
