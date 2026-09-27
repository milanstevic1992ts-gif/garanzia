package com.milanstevic.garanzia.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.milanstevic.garanzia.ui.components.GaranziaHeader
import com.milanstevic.garanzia.ui.components.KeyValueRow
import com.milanstevic.garanzia.ui.components.SectionCard
import com.milanstevic.garanzia.ui.components.StatusPill
import com.milanstevic.garanzia.warranty.WarrantyEngine
import com.milanstevic.garanzia.warranty.WarrantyState
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
    onSaveWarranty: (
        warrantyMonths: Int?,
        reminderDays: Int,
        notificationsEnabled: Boolean,
    ) -> Unit,
    warrantySaving: Boolean,
    warrantyMessage: String?,
    warrantyError: String?,
    onBack: () -> Unit,
) {
    var warrantyMonthsText by remember(product.productId, product.warrantyMonths) {
        mutableStateOf(product.warrantyMonths?.toString().orEmpty())
    }
    var reminderDaysText by remember(product.productId, product.warrantyReminderDays) {
        mutableStateOf(product.warrantyReminderDays.toString())
    }
    var notificationsEnabled by remember(
        product.productId,
        product.warrantyNotificationsEnabled,
    ) {
        mutableStateOf(product.warrantyNotificationsEnabled)
    }

    val parsedWarrantyMonths = warrantyMonthsText.trim().toIntOrNull()
    val parsedReminderDays = reminderDaysText.trim().toIntOrNull()
    val monthsValid =
        warrantyMonthsText.isBlank() ||
            parsedWarrantyMonths in 1..WarrantyEngine.MAX_WARRANTY_MONTHS
    val reminderValid =
        parsedReminderDays in 1..WarrantyEngine.MAX_REMINDER_DAYS
    val canSaveWarranty = monthsValid && reminderValid && !warrantySaving

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
                title = "Garanzia",
                subtitle = "Scadenza calcolata dalla data di acquisto e dalla durata inserita.",
            ) {
                product.warranty?.let { warranty ->
                    StatusPill(
                        text =
                            when (warranty.state) {
                                WarrantyState.ACTIVE -> "Garanzia attiva"
                                WarrantyState.EXPIRING_SOON -> "Garanzia in scadenza"
                                WarrantyState.EXPIRED -> "Garanzia scaduta"
                            },
                        positive = warranty.state != WarrantyState.EXPIRED,
                    )

                    KeyValueRow(
                        label = "Scadenza",
                        value = warranty.expiryDate.format(
                            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                        ),
                    )
                    KeyValueRow(
                        label = "Stato",
                        value =
                            when {
                                warranty.daysRemaining < 0 ->
                                    "Scaduta da ${-warranty.daysRemaining} giorni"
                                warranty.daysRemaining == 0 ->
                                    "Scade oggi"
                                else ->
                                    "${warranty.daysRemaining} giorni rimanenti"
                            },
                    )
                }

                OutlinedTextField(
                    value = warrantyMonthsText,
                    onValueChange = { warrantyMonthsText = it.filter(Char::isDigit) },
                    label = { Text("Durata garanzia (mesi)") },
                    supportingText = {
                        Text(
                            if (warrantyMonthsText.isBlank()) {
                                "Lascia vuoto se questo prodotto non ha una garanzia registrata."
                            } else {
                                "Da 1 a ${WarrantyEngine.MAX_WARRANTY_MONTHS} mesi."
                            },
                        )
                    },
                    isError = !monthsValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = reminderDaysText,
                    onValueChange = { reminderDaysText = it.filter(Char::isDigit) },
                    label = { Text("Avvisami prima (giorni)") },
                    supportingText = {
                        Text("Da 1 a ${WarrantyEngine.MAX_REMINDER_DAYS} giorni.")
                    },
                    isError = !reminderValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Notifiche scadenza",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Un avviso all'ingresso nel periodo di preavviso e uno alla scadenza.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it },
                        enabled = warrantyMonthsText.isNotBlank(),
                    )
                }

                Button(
                    onClick = {
                        onSaveWarranty(
                            parsedWarrantyMonths,
                            requireNotNull(parsedReminderDays),
                            notificationsEnabled && parsedWarrantyMonths != null,
                        )
                    },
                    enabled = canSaveWarranty,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (warrantySaving) "Salvataggio…" else "Salva garanzia")
                }

                warrantyMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                warrantyError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
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
        "USD" -> "$number \\$"
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
