package com.milanstevic.garanzia.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.milanstevic.garanzia.data.local.ReceiptWithDetails
import com.milanstevic.garanzia.ui.components.GaranziaHeader
import com.milanstevic.garanzia.ui.components.InfoStrip
import com.milanstevic.garanzia.ui.components.KeyValueRow
import com.milanstevic.garanzia.ui.components.SectionCard
import com.milanstevic.garanzia.ui.components.StatusPill
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ReceiptArchiveDetailScreen(
    details: ReceiptWithDetails,
    onOpenPdf: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isDeleting: Boolean,
    deleteError: String?,
    onBack: () -> Unit,
) {
    var showDeleteDialog by remember(details.receipt.id) {
        mutableStateOf(false)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) showDeleteDialog = false
            },
            title = {
                Text("Eliminare questo scontrino?")
            },
            text = {
                Text(
                    "Verranno rimossi archivio, originali locali, PDF cache e copie esterne gestite dall'app. Le copie Drive non raggiungibili saranno messe in coda per una pulizia successiva.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    enabled = !isDeleting,
                ) {
                    Text("Elimina")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeleting,
                ) {
                    Text("Annulla")
                }
            },
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = onBack) {
                        Text("Indietro")
                    }

                    GaranziaHeader(
                        eyebrow = "Scontrino salvato",
                        title = details.receipt.merchant,
                        subtitle = formatDetailDate(details.receipt.purchaseDate),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                SectionCard(
                    title = "Riepilogo acquisto",
                    subtitle = "Dati confermati e conservati nell'archivio.",
                ) {
                    KeyValueRow(
                        label = "Totale",
                        value = formatDetailAmount(
                            details.receipt.totalAmount,
                            details.receipt.currency,
                        ),
                    )

                    details.receipt.purchaseTime?.let {
                        KeyValueRow("Ora", it)
                    }
                    details.receipt.documentNumber?.let {
                        KeyValueRow("Documento", it)
                    }
                    details.receipt.vatNumber?.let {
                        KeyValueRow("Partita IVA", it)
                    }
                    details.receipt.paymentMethod?.let {
                        KeyValueRow("Pagamento", it)
                    }
                }
            }

            item {
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
            }

            item {
                SectionCard(
                    title = "Gestione scontrino",
                    subtitle = "Modifica i dati salvati o elimina completamente il documento.",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onEdit,
                            enabled = !isDeleting,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Modifica")
                        }

                        Button(
                            onClick = { showDeleteDialog = true },
                            enabled = !isDeleting,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (isDeleting) "Eliminazione…" else "Elimina")
                        }
                    }

                    deleteError?.let {
                        InfoStrip(
                            text = it,
                            positive = false,
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Prodotti",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            items(
                items = details.products.sortedBy { it.position },
                key = { it.id },
            ) { product ->
                SectionCard {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    product.quantity?.let {
                        KeyValueRow(
                            label = "Quantità",
                            value = formatDecimal(it),
                        )
                    }
                    product.unitPrice?.let {
                        KeyValueRow(
                            label = "Prezzo unitario",
                            value = formatDetailAmount(
                                it,
                                details.receipt.currency,
                            ),
                        )
                    }
                    product.lineTotal?.let {
                        KeyValueRow(
                            label = "Importo",
                            value = formatDetailAmount(
                                it,
                                details.receipt.currency,
                            ),
                        )
                    }
                }
            }

            item {
                SectionCard(
                    title = "Documento originale",
                    subtitle = "Il PDF viene generato solo quando lo apri, così l'archivio resta leggero.",
                ) {
                    Button(
                        onClick = onOpenPdf,
                        enabled = details.pages.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Apri PDF integrato")
                    }
                }
            }

            details.receipt.rawOcrText?.let { raw ->
                item {
                    SectionCard(
                        title = "Testo OCR",
                        subtitle = "Trascrizione originale usata per riconoscere i dati.",
                    ) {
                        Text(
                            text = raw,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
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
