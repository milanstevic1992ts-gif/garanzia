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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.milanstevic.garanzia.ui.components.GaranziaHeader
import com.milanstevic.garanzia.ui.components.InfoStrip
import com.milanstevic.garanzia.ui.components.SectionCard
import com.milanstevic.garanzia.ui.components.StatusPill

@Composable
fun ReceiptConfirmationScreen(
    draft: ReceiptConfirmationDraft,
    isSaving: Boolean,
    saveError: String?,
    onDraftChange: (ReceiptConfirmationDraft) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
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
                eyebrow = "Ultimo controllo",
                title = "Conferma scontrino",
                subtitle = "Correggi solo ciò che serve, poi salva nell'archivio.",
            )

            when {
                isSaving -> {
                    SectionCard(
                        title = "Salvataggio in corso",
                        subtitle = "Sto registrando scontrino, prodotti e pagine originali.",
                    ) {
                        CircularProgressIndicator()
                    }
                }

                saveError != null -> {
                    InfoStrip(
                        text = "Salvataggio non riuscito: $saveError",
                        positive = false,
                    )
                }

                draft.attentionCount > 0 -> {
                    InfoStrip(
                        text = "Ci sono ${draft.attentionCount} elementi da verificare prima del salvataggio.",
                        positive = false,
                    )
                }

                else -> {
                    InfoStrip(
                        text = "I dati principali risultano affidabili. Puoi confermare.",
                        positive = true,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    SectionCard(
                        title = "Dati acquisto",
                        subtitle = "I campi obbligatori sono contrassegnati con *.",
                    ) {
                        ConfirmationTextField(
                            label = "Negozio *",
                            value = draft.merchant,
                            onValueChange = {
                                onDraftChange(
                                    draft.copy(
                                        merchant = it,
                                        fieldsToReview =
                                            draft.fieldsToReview - ConfirmationField.MERCHANT,
                                    ),
                                )
                            },
                            requiresReview =
                                ConfirmationField.MERCHANT in draft.fieldsToReview,
                            isError = draft.merchant.isBlank(),
                        )

                        ConfirmationTextField(
                            label = "Data acquisto *",
                            value = draft.purchaseDate,
                            onValueChange = {
                                onDraftChange(
                                    draft.copy(
                                        purchaseDate = it,
                                        fieldsToReview =
                                            draft.fieldsToReview - ConfirmationField.PURCHASE_DATE,
                                    ),
                                )
                            },
                            requiresReview =
                                ConfirmationField.PURCHASE_DATE in draft.fieldsToReview,
                            isError =
                                !ReceiptConfirmationDraft.isValidDate(draft.purchaseDate),
                            helper = "Formato: gg/mm/aaaa",
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ConfirmationTextField(
                                label = "Ora",
                                value = draft.purchaseTime,
                                onValueChange = {
                                    onDraftChange(
                                        draft.copy(
                                            purchaseTime = it,
                                            fieldsToReview =
                                                draft.fieldsToReview -
                                                    ConfirmationField.PURCHASE_TIME,
                                        ),
                                    )
                                },
                                requiresReview =
                                    ConfirmationField.PURCHASE_TIME in draft.fieldsToReview,
                                modifier = Modifier.weight(1f),
                            )

                            ConfirmationTextField(
                                label = "Valuta",
                                value = draft.currency,
                                onValueChange = {
                                    onDraftChange(
                                        draft.copy(
                                            currency = it.uppercase(),
                                            fieldsToReview =
                                                draft.fieldsToReview -
                                                    ConfirmationField.CURRENCY,
                                        ),
                                    )
                                },
                                requiresReview =
                                    ConfirmationField.CURRENCY in draft.fieldsToReview,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        ConfirmationTextField(
                            label = "Totale *",
                            value = draft.totalAmount,
                            onValueChange = {
                                onDraftChange(
                                    draft.copy(
                                        totalAmount = it,
                                        fieldsToReview =
                                            draft.fieldsToReview - ConfirmationField.TOTAL,
                                    ),
                                )
                            },
                            requiresReview =
                                ConfirmationField.TOTAL in draft.fieldsToReview,
                            isError =
                                ReceiptConfirmationDraft.parseMoney(
                                    draft.totalAmount,
                                ) == null,
                            helper = "Esempio: 129,90",
                        )

                        ConfirmationTextField(
                            label = "Partita IVA",
                            value = draft.vatNumber,
                            onValueChange = {
                                onDraftChange(
                                    draft.copy(
                                        vatNumber = it,
                                        fieldsToReview =
                                            draft.fieldsToReview -
                                                ConfirmationField.VAT_NUMBER,
                                    ),
                                )
                            },
                            requiresReview =
                                ConfirmationField.VAT_NUMBER in draft.fieldsToReview,
                        )

                        ConfirmationTextField(
                            label = "Numero documento",
                            value = draft.documentNumber,
                            onValueChange = {
                                onDraftChange(
                                    draft.copy(
                                        documentNumber = it,
                                        fieldsToReview =
                                            draft.fieldsToReview -
                                                ConfirmationField.DOCUMENT_NUMBER,
                                    ),
                                )
                            },
                            requiresReview =
                                ConfirmationField.DOCUMENT_NUMBER in draft.fieldsToReview,
                        )

                        ConfirmationTextField(
                            label = "Pagamento",
                            value = draft.paymentMethod,
                            onValueChange = {
                                onDraftChange(
                                    draft.copy(
                                        paymentMethod = it,
                                        fieldsToReview =
                                            draft.fieldsToReview -
                                                ConfirmationField.PAYMENT_METHOD,
                                    ),
                                )
                            },
                            requiresReview =
                                ConfirmationField.PAYMENT_METHOD in draft.fieldsToReview,
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Prodotti",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        StatusPill(
                            text = "${draft.products.size} rilevati",
                            positive = draft.products.isNotEmpty(),
                        )
                    }
                }

                if (draft.products.isEmpty()) {
                    item {
                        InfoStrip(
                            text = "Nessun prodotto riconosciuto. Aggiungine almeno uno per poter salvare.",
                            positive = false,
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Indietro")
                }

                Button(
                    onClick = onConfirm,
                    enabled = draft.canConfirm && !isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isSaving) "Salvataggio…" else "Conferma e salva")
                }
            }

            if (!draft.canConfirm) {
                Text(
                    text = "Servono negozio, data valida, totale valido e almeno un prodotto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError || requiresReview,
        supportingText = {
            when {
                requiresReview && helper != null ->
                    Text("Da verificare · $helper")
                requiresReview ->
                    Text("Da verificare")
                helper != null ->
                    Text(helper)
            }
        },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun ProductConfirmationEditor(
    index: Int,
    product: ProductConfirmationDraft,
    onChange: (ProductConfirmationDraft) -> Unit,
    onRemove: () -> Unit,
) {
    SectionCard(
        title = "Prodotto ${index + 1}",
        subtitle =
            if (product.requiresReview) {
                "Controlla i dati riconosciuti."
            } else {
                "Dati pronti."
            },
    ) {
        if (product.requiresReview) {
            val confidence = product.sourceConfidence
                ?.let { (it * 100f).toInt() }
                ?.let { " · $it%" }
                .orEmpty()

            StatusPill(
                text = "Da verificare$confidence",
                positive = false,
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
    }
}
