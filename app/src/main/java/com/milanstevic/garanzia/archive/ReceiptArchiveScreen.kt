package com.milanstevic.garanzia.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.milanstevic.garanzia.data.local.ReceiptWithDetails
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ReceiptArchiveScreen(
    receipts: List<ReceiptWithDetails>,
    onOpenReceipt: (String) -> Unit,
    onBack: () -> Unit,
) {
    var filters by remember { mutableStateOf(ArchiveFilterState()) }
    val filtered = remember(receipts, filters) {
        ReceiptArchiveFilter.apply(receipts, filters)
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onBack) {
                    Text("Indietro")
                }
                Text(
                    text = "Archivio garanzie",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = "${filtered.size} di ${receipts.size} scontrini",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = filters.query,
                onValueChange = { filters = filters.copy(query = it) },
                label = { Text("Cerca negozio, prodotto o documento") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = filters.fromDate,
                    onValueChange = { filters = filters.copy(fromDate = it) },
                    label = { Text("Dal") },
                    supportingText = { Text("gg/mm/aaaa") },
                    isError =
                        filters.fromDate.isNotBlank() &&
                            ArchiveFilterState.parseItalianDate(filters.fromDate) == null,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )

                OutlinedTextField(
                    value = filters.toDate,
                    onValueChange = { filters = filters.copy(toDate = it) },
                    label = { Text("Al") },
                    supportingText = { Text("gg/mm/aaaa") },
                    isError =
                        filters.toDate.isNotBlank() &&
                            ArchiveFilterState.parseItalianDate(filters.toDate) == null,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        filters = filters.copy(
                            sort =
                                if (filters.sort == ArchiveSort.NEWEST) {
                                    ArchiveSort.OLDEST
                                } else {
                                    ArchiveSort.NEWEST
                                },
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (filters.sort == ArchiveSort.NEWEST) {
                            "Più recenti"
                        } else {
                            "Più vecchi"
                        },
                    )
                }

                OutlinedButton(
                    onClick = { filters = ArchiveFilterState() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Azzera filtri")
                }
            }

            if (receipts.isEmpty()) {
                Text(
                    text = "L'archivio è ancora vuoto. Scansiona e conferma il primo scontrino.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else if (filtered.isEmpty()) {
                Text(
                    text =
                        if (filters.hasInvalidDate) {
                            "Correggi il formato delle date per applicare il filtro."
                        } else {
                            "Nessuno scontrino corrisponde ai filtri."
                        },
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = filtered,
                        key = { it.receipt.id },
                    ) { details ->
                        ReceiptArchiveCard(
                            details = details,
                            onOpen = { onOpenReceipt(details.receipt.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptArchiveCard(
    details: ReceiptWithDetails,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = details.receipt.merchant,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = formatDate(details.receipt.purchaseDate),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = formatAmount(
                    details.receipt.totalAmount,
                    details.receipt.currency,
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "${details.products.size} prodotto/i · ${details.pages.size} pagina/e",
                style = MaterialTheme.typography.bodySmall,
            )

            val productPreview = details.products
                .sortedBy { it.position }
                .take(3)
                .joinToString(" · ") { it.name }

            if (productPreview.isNotBlank()) {
                Text(
                    text = productPreview,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (details.products.size > 3) {
                Text(
                    text = "+${details.products.size - 3} altri",
                    style = MaterialTheme.typography.bodySmall,
                )
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
        ?.setScale(2)
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
