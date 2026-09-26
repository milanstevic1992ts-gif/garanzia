package com.milanstevic.garanzia.ocr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.milanstevic.garanzia.intelligence.InterpretedField
import com.milanstevic.garanzia.intelligence.ReceiptInterpretation
import com.milanstevic.garanzia.intelligence.ReceiptProduct
import com.milanstevic.garanzia.ui.components.GaranziaHeader
import com.milanstevic.garanzia.ui.components.InfoStrip
import com.milanstevic.garanzia.ui.components.KeyValueRow
import com.milanstevic.garanzia.ui.components.SectionCard
import com.milanstevic.garanzia.ui.components.StatusPill
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun OcrResultScreen(
    status: String,
    progressPercent: Int?,
    result: OcrReceiptResult?,
    interpretation: ReceiptInterpretation?,
    error: String?,
    onReview: () -> Unit,
    onManualSave: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GaranziaHeader(
                eyebrow = "Lettura intelligente",
                title = "OCR scontrino",
                subtitle = "Riconoscimento locale con conservazione dell'originale.",
            )

            if (result == null && error == null) {
                SectionCard(
                    title = "Sto leggendo lo scontrino",
                    subtitle = status,
                ) {
                    CircularProgressIndicator()
                    progressPercent?.let {
                        StatusPill(
                            text = "$it%",
                            positive = true,
                        )
                    }
                }
            }

            error?.let {
                SectionCard(
                    title = "OCR non completato",
                    subtitle = "La foto originale è al sicuro.",
                ) {
                    InfoStrip(
                        text = it,
                        positive = false,
                    )
                    Text(
                        text = "Puoi comunque compilare i dati manualmente e salvare lo scontrino nell'archivio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            result?.let { receipt ->
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Dati riconosciuti",
                                style = MaterialTheme.typography.titleLarge,
                            )

                            StatusPill(
                                text =
                                    if (interpretation?.needsReview == true) {
                                        "Da verificare"
                                    } else {
                                        "Pronti"
                                    },
                                positive = interpretation?.needsReview != true,
                            )
                        }
                    }

                    if (interpretation?.hasStructuredData == true) {
                        interpretation.merchant?.let { field ->
                            item {
                                InterpretationField(
                                    label = "Negozio",
                                    value = field.value,
                                    field = field,
                                )
                            }
                        }

                        interpretation.purchaseDate?.let { field ->
                            item {
                                InterpretationField(
                                    label = "Data acquisto",
                                    value = field.value.format(DATE_FORMAT),
                                    field = field,
                                )
                            }
                        }

                        interpretation.purchaseTime?.let { field ->
                            item {
                                InterpretationField(
                                    label = "Ora",
                                    value = field.value.format(TIME_FORMAT),
                                    field = field,
                                )
                            }
                        }

                        interpretation.totalAmount?.let { field ->
                            item {
                                InterpretationField(
                                    label = "Totale",
                                    value = formatAmount(
                                        field.value,
                                        interpretation.currency?.value,
                                    ),
                                    field = field,
                                )
                            }
                        }

                        interpretation.currency?.let { field ->
                            item {
                                InterpretationField(
                                    label = "Valuta",
                                    value = field.value,
                                    field = field,
                                )
                            }
                        }

                        interpretation.vatNumber?.let { field ->
                            item {
                                InterpretationField(
                                    label = "Partita IVA",
                                    value = field.value,
                                    field = field,
                                )
                            }
                        }

                        interpretation.documentNumber?.let { field ->
                            item {
                                InterpretationField(
                                    label = "Numero documento",
                                    value = field.value,
                                    field = field,
                                )
                            }
                        }

                        interpretation.paymentMethod?.let { field ->
                            item {
                                InterpretationField(
                                    label = "Pagamento",
                                    value = field.value.displayName,
                                    field = field,
                                )
                            }
                        }

                        if (interpretation.products.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Prodotti rilevati",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }

                            items(interpretation.products) { field ->
                                ProductField(
                                    field = field,
                                    currency = interpretation.currency?.value,
                                )
                            }
                        }
                    } else {
                        item {
                            InfoStrip(
                                text = "Non ho trovato dati strutturati abbastanza affidabili. Il testo OCR resta disponibile sotto.",
                                positive = false,
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Testo OCR originale",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }

                    receipt.pages.forEach { page ->
                        item {
                            SectionCard(
                                title = "Pagina ${page.pageIndex + 1}",
                                subtitle = "Tempo OCR: ${page.totalTimeMs} ms",
                            ) {
                                page.lines.forEach { line ->
                                    val confidence =
                                        (line.confidence * 100f).roundToInt()

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = line.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            text = "OCR $confidence%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (result != null && error == null && interpretation != null) {
                Button(
                    onClick = onReview,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Verifica e conferma")
                }

                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Torna alla home")
                }
            } else if (error != null) {
                Button(
                    onClick = onManualSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Compila e salva comunque")
                }

                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Torna alla home senza archiviare")
                }
            }
        }
    }
}

@Composable
private fun InterpretationField(
    label: String,
    value: String,
    field: InterpretedField<*>,
) {
    val confidence = (field.confidence * 100f).roundToInt()

    SectionCard {
        KeyValueRow(
            label = label,
            value = value,
        )

        StatusPill(
            text = "${field.level.displayName} · $confidence%",
            positive = confidence >= 70,
        )

        Text(
            text = "Evidenza OCR: ${field.evidence}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProductField(
    field: InterpretedField<ReceiptProduct>,
    currency: String?,
) {
    val product = field.value
    val confidence = (field.confidence * 100f).roundToInt()

    SectionCard(
        title = product.name,
    ) {
        product.quantity?.let { quantity ->
            KeyValueRow(
                label = "Quantità",
                value = formatQuantity(quantity),
            )
        }

        product.unitPrice?.let { unitPrice ->
            KeyValueRow(
                label = "Prezzo unitario",
                value = formatAmount(unitPrice, currency),
            )
        }

        product.lineTotal?.let { total ->
            KeyValueRow(
                label = "Importo",
                value = formatAmount(total, currency),
            )
        }

        StatusPill(
            text = "${field.level.displayName} · $confidence%",
            positive = confidence >= 70,
        )
    }
}

private fun formatQuantity(quantity: BigDecimal): String =
    quantity.stripTrailingZeros().toPlainString().replace('.', ',')

private fun formatAmount(
    amount: BigDecimal,
    currency: String?,
): String {
    val number = amount
        .setScale(2, RoundingMode.HALF_UP)
        .toPlainString()
        .replace('.', ',')

    return when (currency) {
        "EUR" -> "$number €"
        "USD" -> "$number \$"
        "GBP" -> "$number £"
        null -> number
        else -> "$number $currency"
    }
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
