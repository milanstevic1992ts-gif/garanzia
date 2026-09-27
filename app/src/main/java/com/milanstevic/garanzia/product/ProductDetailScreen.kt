package com.milanstevic.garanzia.product

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.milanstevic.garanzia.product.attachment.ProductAttachmentCategory
import com.milanstevic.garanzia.product.attachment.ProductAttachmentItem
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
    attachmentBusy: Boolean,
    attachmentMessage: String?,
    onAddAttachmentFromGallery: (ProductAttachmentCategory) -> Unit,
    onTakeAttachmentPhoto: (ProductAttachmentCategory) -> Unit,
    onUpdateAttachmentNote: (ProductAttachmentItem, String?) -> Unit,
    onDeleteAttachment: (ProductAttachmentItem) -> Unit,
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
    var attachmentCategory by remember(product.productId) {
        mutableStateOf(ProductAttachmentCategory.OTHER)
    }
    var previewAttachment by remember { mutableStateOf<ProductAttachmentItem?>(null) }
    var deleteAttachment by remember { mutableStateOf<ProductAttachmentItem?>(null) }

    val parsedWarrantyMonths = warrantyMonthsText.trim().toIntOrNull()
    val parsedReminderDays = reminderDaysText.trim().toIntOrNull()
    val monthsValid =
        warrantyMonthsText.isBlank() ||
            (
                parsedWarrantyMonths != null &&
                    parsedWarrantyMonths in 1..WarrantyEngine.MAX_WARRANTY_MONTHS
                )
    val reminderValid =
        parsedReminderDays != null &&
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
                title = "Foto e prove",
                subtitle =
                    if (product.attachments.isEmpty()) {
                        "Aggiungi pagamento, scatola, seriale o qualsiasi foto utile per il futuro."
                    } else {
                        "${product.attachments.size} allegati collegati a questo prodotto."
                    },
            ) {
                Text(
                    text = "Tipo di allegato",
                    style = MaterialTheme.typography.titleSmall,
                )

                ProductAttachmentCategory.entries
                    .chunked(3)
                    .forEach { rowCategories ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowCategories.forEach { category ->
                                if (category == attachmentCategory) {
                                    Button(
                                        onClick = { attachmentCategory = category },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(category.label)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { attachmentCategory = category },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(category.label)
                                    }
                                }
                            }

                            repeat(3 - rowCategories.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { onTakeAttachmentPhoto(attachmentCategory) },
                        enabled = !attachmentBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Scatta foto")
                    }
                    OutlinedButton(
                        onClick = { onAddAttachmentFromGallery(attachmentCategory) },
                        enabled = !attachmentBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Da galleria")
                    }
                }

                attachmentMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (
                                message.contains("impossibile", ignoreCase = true) ||
                                message.contains("errore", ignoreCase = true)
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                }

                if (product.attachments.isEmpty()) {
                    Text(
                        text = "Nessuna foto allegata.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    product.attachments.forEachIndexed { index, attachment ->
                        ProductAttachmentCard(
                            attachment = attachment,
                            busy = attachmentBusy,
                            onOpen = { previewAttachment = attachment },
                            onSaveNote = { note ->
                                onUpdateAttachmentNote(attachment, note)
                            },
                            onDelete = { deleteAttachment = attachment },
                        )

                        if (index != product.attachments.lastIndex) {
                            HorizontalDivider()
                        }
                    }
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

    previewAttachment?.let { attachment ->
        Dialog(
            onDismissRequest = { previewAttachment = null },
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AsyncImage(
                        model = Uri.parse(attachment.localUri),
                        contentDescription = attachment.category.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(520.dp),
                    )
                    Text(
                        text = attachment.category.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    attachment.note?.takeIf(String::isNotBlank)?.let {
                        Text(it)
                    }
                    Button(
                        onClick = { previewAttachment = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Chiudi")
                    }
                }
            }
        }
    }

    deleteAttachment?.let { attachment ->
        AlertDialog(
            onDismissRequest = { deleteAttachment = null },
            title = { Text("Eliminare questo allegato?") },
            text = {
                Text(
                    "La foto verrà rimossa dalla scheda prodotto e dall'archivio interno.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteAttachment = null
                        onDeleteAttachment(attachment)
                    },
                    enabled = !attachmentBusy,
                ) {
                    Text("Elimina")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteAttachment = null },
                    enabled = !attachmentBusy,
                ) {
                    Text("Annulla")
                }
            },
        )
    }
}

@Composable
private fun ProductAttachmentCard(
    attachment: ProductAttachmentItem,
    busy: Boolean,
    onOpen: () -> Unit,
    onSaveNote: (String?) -> Unit,
    onDelete: () -> Unit,
) {
    var note by remember(attachment.id, attachment.note) {
        mutableStateOf(attachment.note.orEmpty())
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = Uri.parse(attachment.localUri),
            contentDescription = attachment.category.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(106.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onOpen),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = attachment.category.label,
                style = MaterialTheme.typography.titleSmall,
            )

            attachment.originalName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(250) },
                label = { Text("Nota") },
                placeholder = { Text("Es. pagamento Intesa, seriale sul retro…") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onSaveNote(note.ifBlank { null }) },
                    enabled = !busy && note.trim() != attachment.note.orEmpty().trim(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Salva nota")
                }
                TextButton(
                    onClick = onDelete,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Elimina")
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
