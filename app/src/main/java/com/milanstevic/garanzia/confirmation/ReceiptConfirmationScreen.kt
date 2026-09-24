package com.milanstevic.garanzia.confirmation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReceiptConfirmationScreen(
    draft: ReceiptConfirmationDraft,
    onDraftChange: (ReceiptConfirmationDraft) -> Unit,
    onConfirm: () -> Unit,
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
            Text(
                text = "Conferma scontrino",
                style = MaterialTheme.typography.headlineSmall,
            )

            if (draft.attentionCount > 0) {
                Text(
                    text = "Da verificare: ${draft.attentionCount}. I campi dubbi sono evidenziati, ma puoi correggere anche gli altri.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    text = "I dati principali risultano affidabili. Controlla rapidamente e conferma.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "Dati acquisto",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                item {
                    ConfirmationTextField(
                        label = "Negozio *",
                        value = draft.merchant,
                        onValueChange = {
                            onDraftChange(
                                draft.copy(
                                    merchant = it,
                                    fieldsToReview = draft.fieldsToReview - ConfirmationField.MERCHANT,
                                ),
                            )
                        },
                        requiresReview = ConfirmationField.MERCHANT in draft.fieldsToReview,
                        isError = draft.merchant.isBlank(),
                    )
                }

                item {
                    ConfirmationTextField(
                        label = "Data acquisto *",
                        value = draft.purchaseDate,
                        onValueChange = {
                            onDraftChange(
                                draft.copy(
                                    purchaseDate = it,
                                    fieldsToReview = draft.fieldsToReview - ConfirmationField.PURCHASE_DATE,
                                ),
                            )
                        },
                        requiresReview = ConfirmationField.PURCHASE_DATE in draft.fieldsToReview,
                        isError = !ReceiptConfirmationDraft.isValidDate(draft.purchaseDate),
                        helper = "Formato: gg/mm/aaaa",
                    )
                }

                item {
                    ConfirmationTextField(
                        label = "Ora",
                        value = draft.purchaseTime,
                        onValueChange = {
                            onDraftChange(
                                draft.copy(
                                    purchaseTime = it,
                                    fieldsToReview = draft.fieldsToReview - ConfirmationField.PURCHASE_TIME,
                                ),
                            )
                        },
                        requiresReview = ConfirmationField.PURCHASE_TIME in draft.fieldsToReview,
                    )
                }

                item {
                    ConfirmationTextField(
                        label = "Totale *",
                        value = draft.totalAmount,
                        onValueChange = {
                            onDraftChange(
                                draft.copy(
                                    totalAmount = it,
                                    fieldsToReview = draft.fieldsToReview - ConfirmationField.TOTAL,
                                ),
                            )
                        },
                        requiresReview = ConfirmationField.TOTAL in draft.fieldsToReview,
                        isError = ReceiptConfirmationDraft.parseMoney(draft.totalAmount) == null,
                        helper = "Esempio: 129,90",
                    )
                }

                item {
                    ConfirmationTextField(
                        label = "Valuta",
                        value = draft.currency,
                        onValueChange = {
                            onDraftChange(
                                draft.copy(
                                    currency = it.uppercase(),
                                    fieldsToReview = draft.fieldsToReview - ConfirmationField.CURRENCY,
                                ),
                            )
                        },
                        requiresReview = ConfirmationField.CURRENCY in draft.fieldsToReview,
                    )
                }

                item {
                    ConfirmationTextField(
                        label = "Partita IVA",
                        value = draft.vatNumber,
                        onValueChange = {
                            onDraftChange(
                                draft.copy(
                                    vatNumber = it,
                                    fieldsToReview = draft.fieldsToReview - ConfirmationField.VAT_NUMBER,
                                ),
                            )
                        },
                        requiresReview = ConfirmationField.VAT_NUMBER in draft.fieldsToReview,
                    )
                }

                item {
                    ConfirmationTextField(
                        label = "Numero documento",
                        value = draft.documentNumber,
                        onValueChange = {
                            onDraftChange(
                                draft.copy(
                                    documentNumber = it,
                                    fieldsToReview = draft.fieldsToReview - ConfirmationField.DOCUMENT_NUMBER,
                                ),
                            )
                        },
                        requiresReview = ConfirmationField.DOCUMENT_NUMBER in draft.fieldsToReview,
                    )
                }

                item {
                    ConfirmationTextField(
                        label = "Pagamento",
                        value = draft.paymentMethod,
                        onValueChange = {
                            onDraftChange(
                                draft.copy(
                                    paymentMethod = it,
                                    fieldsToReview = draft.fieldsToReview - ConfirmationField.PAYMENT_METHOD,
                                ),
                            )
                        },
                        requiresReview = ConfirmationField.PAYMENT_METHOD in draft.fieldsToReview,
                    )
                }

                item {
                    HorizontalDivider()
                }

                item {
                    Text(
                        text = "Prodotti (${draft.products.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                if (draft.products.isEmpty()) {
                    item {
                        Text(
                            text = "Nessun prodotto riconosciuto. Puoi aggiungerlo manualmente.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                itemsIndexed(draft.products) { index, product ->
                    ProductConfirmationEditor(
                        index = index,
                        product = product,
                        onChange = { updated ->
                            onDraftChange(
                                draft.copy(
                                    products = draft.products.toMutableList().also {
                                        it[index] = updated.copy(requiresReview = false)
                                    },
                                ),
                            )
                        },
                        onRemove = {
                            onDraftChange(
                                draft.copy(
                                    products = draft.products.toMutableList().also {
                                        it.removeAt(index)
                                    },
                                ),
                            )
                        },
                    )
                }

                item {
                    OutlinedButton(
                        onClick = {
                            onDraftChange(
                                draft.copy(
                                    products = draft.products + ProductConfirmationDraft(
                                        name = "",
                                        quantity = "",
                                        unitPrice = "",
                                        lineTotal = "",
                                        requiresReview = true,
                                        sourceConfidence = null,
                                    ),
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Aggiungi prodotto")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Indietro")
                }

                Button(
                    onClick = onConfirm,
                    enabled = draft.canConfirm,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Conferma scontrino")
                }
            }

            if (!draft.canConfirm) {
                Text(
                    text = "Per confermare servono almeno negozio, data valida, totale valido e nomi prodotto non vuoti.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ConfirmationTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    requiresReview: Boolean,
    isError: Boolean = false,
    helper: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError || requiresReview,
        supportingText = {
            if (requiresReview) {
                Text(
                    if (helper != null) {
                        "Da verificare · $helper"
                    } else {
                        "Da verificare"
                    },
                )
            } else if (helper != null) {
                Text(helper)
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProductConfirmationEditor(
    index: Int,
    product: ProductConfirmationDraft,
    onChange: (ProductConfirmationDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Prodotto ${index + 1}",
            style = MaterialTheme.typography.titleSmall,
        )

        if (product.requiresReview) {
            val confidence = product.sourceConfidence
                ?.let { (it * 100f).toInt() }
                ?.let { " ($it%)" }
                .orEmpty()

            Text(
                text = "Da verificare$confidence",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        OutlinedTextField(
            value = product.name,
            onValueChange = { onChange(product.copy(name = it)) },
            label = { Text("Nome prodotto *") },
            isError = product.name.isBlank(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = product.quantity,
                onValueChange = { onChange(product.copy(quantity = it)) },
                label = { Text("Quantità") },
                isError =
                    product.quantity.isNotBlank() &&
                        ReceiptConfirmationDraft.parseQuantity(product.quantity) == null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )

            OutlinedTextField(
                value = product.unitPrice,
                onValueChange = { onChange(product.copy(unitPrice = it)) },
                label = { Text("Prezzo unit.") },
                isError =
                    product.unitPrice.isNotBlank() &&
                        ReceiptConfirmationDraft.parseMoney(product.unitPrice) == null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = product.lineTotal,
            onValueChange = { onChange(product.copy(lineTotal = it)) },
            label = { Text("Importo riga") },
            isError =
                product.lineTotal.isNotBlank() &&
                    ReceiptConfirmationDraft.parseMoney(product.lineTotal) == null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        TextButton(onClick = onRemove) {
            Text("Rimuovi prodotto")
        }

        HorizontalDivider()
    }
}
