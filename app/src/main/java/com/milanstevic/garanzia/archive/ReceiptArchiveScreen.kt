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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.milanstevic.garanzia.data.local.ReceiptWithDetails
import com.milanstevic.garanzia.ui.components.GaranziaHeader
import com.milanstevic.garanzia.ui.components.InfoStrip
import com.milanstevic.garanzia.ui.components.PremiumBottomBar
import com.milanstevic.garanzia.ui.components.PremiumDestination
import com.milanstevic.garanzia.ui.components.SectionCard
import com.milanstevic.garanzia.ui.components.StatusPill
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ReceiptArchiveScreen(
    receipts: List<ReceiptWithDetails>,
    filters: ArchiveFilterState,
    onFiltersChange: (ArchiveFilterState) -> Unit,
    onOpenReceipt: (String) -> Unit,
    onOpenHome: () -> Unit,
    onOpenStorage: () -> Unit,
) {
    val filtered = ReceiptArchiveFilter.apply(receipts, filters)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            PremiumBottomBar(
                selected = PremiumDestination.ARCHIVE,
                onHome = onOpenHome,
                onArchive = {},
                onStorage = onOpenStorage,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GaranziaHeader(
                eyebrow = "I tuoi documenti",
                title = "Archivio",
                subtitle = "Trova uno scontrino per negozio, prodotto o data.",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(
                    text = "${filtered.size} visualizzati",
                    positive = true,
                )
                StatusPill(
                    text = "${receipts.size} totali",
                    positive = true,
                )
            }

            SectionCard(
                title = "Ricerca e filtri",
                subtitle = "Puoi cercare anche per numero documento o testo OCR.",
            ) {
                OutlinedTextField(
                    value = filters.query,
                    onValueChange = { onFiltersChange(filters.copy(query = it)) },
                    label = { Text("Cerca") },
                    placeholder = { Text("Negozio, prodotto, documento…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = filters.fromDate,
                        onValueChange = { onFiltersChange(filters.copy(fromDate = it)) },
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
                        onValueChange = { onFiltersChange(filters.copy(toDate = it)) },
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
                    FilledTonalButton(
                        onClick = {
                            onFiltersChange(
                                filters.copy(
                                    sort =
                                        if (filters.sort == ArchiveSort.NEWEST) {
                                            ArchiveSort.OLDEST
                                        } else {
                                            ArchiveSort.NEWEST
                                        },
                                ),
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
                        onClick = { onFiltersChange(ArchiveFilterState()) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Azzera")
                    }
                }
            }

            when {
                receipts.isEmpty() -> {
                    InfoStrip(
                        text = "L'archivio è ancora vuoto. Scansiona e conferma il primo scontrino.",
                        positive = true,
                    )
                }

                filtered.isEmpty() -> {
                    InfoStrip(
                        text =
                            when {
                                filters.hasInvalidDate ->
                                    "Correggi il formato delle date."
                                filters.hasInvalidRange ->
                                    "La data iniziale non può essere successiva alla data finale."
                                else ->
                                    "Nessuno scontrino corrisponde ai filtri."
                            },
                        positive = false,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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
}

@Composable
private fun ReceiptArchiveCard(
    details: ReceiptWithDetails,
    onOpen: () -> Unit,
) {
    ElevatedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = details.receipt.merchant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatDate(details.receipt.purchaseDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = formatAmount(
                        details.receipt.totalAmount,
                        details.receipt.currency,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            val productPreview = details.products
                .sortedBy { it.position }
                .take(2)
                .joinToString(" · ") { it.name }

            if (productPreview.isNotBlank()) {
                Text(
                    text = productPreview,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(
                    text = "${details.products.size} prodotti",
                    positive = true,
                )
                StatusPill(
                    text = "${details.pages.size} pagine",
                    positive = true,
                )
            }

            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apri scontrino")
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
