package com.milanstevic.garanzia.ocr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.milanstevic.garanzia.intelligence.InterpretedField
import com.milanstevic.garanzia.intelligence.ReceiptInterpretation
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun OcrResultScreen(
    status: String,
    progressPercent: Int?,
    result: OcrReceiptResult?,
    interpretation: ReceiptInterpretation?,
    error: String?,
    onDone: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "OCR scontrino",
                style = MaterialTheme.typography.headlineSmall,
            )

            if (result == null && error == null) {
                CircularProgressIndicator()
                Text(status)
                progressPercent?.let { Text("$it%") }
            }

            error?.let {
                Text(
                    text = "OCR non completato: $it",
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "Lo scontrino originale è comunque già stato conservato.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            result?.let { receipt ->
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = "Dati interpretati",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    if (interpretation?.hasStructuredData == true) {
                        if (interpretation.needsReview) {
                            item {
                                Text(
                                    text = "Verifica consigliata: almeno un dato importante manca o ha affidabilità bassa.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        interpretation.merchant?.let { field ->
                            item { InterpretationField("Negozio", field.value, field) }
                        }
                        interpretation.purchaseDate?.let { field ->
                            item {
                                InterpretationField(
                                    "Data acquisto",
                                    field.value.format(DATE_FORMAT),
                                    field,
                                )
                            }
                        }
                        interpretation.purchaseTime?.let { field ->
                            item {
                                InterpretationField(
                                    "Ora",
                                    field.value.format(TIME_FORMAT),
                                    field,
                                )
                            }
                        }
                        interpretation.totalAmount?.let { field ->
                            item {
                                InterpretationField(
                                    "Totale",
                                    formatAmount(field.value, interpretation.currency?.value),
                                    field,
                                )
                            }
                        }
                        interpretation.currency?.let { field ->
                            item { InterpretationField("Valuta", field.value, field) }
                        }
                        interpretation.vatNumber?.let { field ->
                            item { InterpretationField("Partita IVA", field.value, field) }
                        }
                        interpretation.documentNumber?.let { field ->
                            item { InterpretationField("Numero documento", field.value, field) }
                        }
                        interpretation.paymentMethod?.let { field ->
                            item {
                                InterpretationField(
                                    "Pagamento",
                                    field.value.displayName,
                                    field,
                                )
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "Non ho trovato dati strutturati abbastanza affidabili. Il testo OCR resta disponibile sotto.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    item {
                        HorizontalDivider()
                    }

                    item {
                        Text(
                            text = "Testo OCR originale",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    receipt.pages.forEach { page ->
                        item {
                            Text(
                                text = "Pagina ${page.pageIndex + 1}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        items(page.lines) { line ->
                            val confidence = (line.confidence * 100f).roundToInt()
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(line.text)
                                Text(
                                    text = "Affidabilità OCR: $confidence%",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        item {
                            Text(
                                text = "Tempo OCR: ${page.totalTimeMs} ms",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (result != null || error != null) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Torna alla home")
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Affidabilità: ${field.level.displayName} ($confidence%)",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Evidenza OCR: ${field.evidence}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatAmount(
    amount: BigDecimal,
    currency: String?,
): String {
    val number = amount.setScale(2).toPlainString().replace('.', ',')
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
