package com.milanstevic.garanzia.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.milanstevic.garanzia.ui.components.GaranziaHeader
import com.milanstevic.garanzia.ui.components.InfoStrip
import com.milanstevic.garanzia.ui.components.MetricCard
import com.milanstevic.garanzia.ui.components.PremiumBottomBar
import com.milanstevic.garanzia.ui.components.PremiumDestination
import com.milanstevic.garanzia.ui.components.SectionCard
import com.milanstevic.garanzia.ui.components.StatusPill

@Composable
fun HomeScreen(
    onScanReceipt: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenStorage: () -> Unit,
    lastSavedPages: Int,
    lastConfirmedProducts: Int?,
    savedReceiptCount: Int,
    dualCopyConfigured: Boolean,
    storageMessage: String?,
    databaseError: String?,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            PremiumBottomBar(
                selected = PremiumDestination.HOME,
                onHome = {},
                onArchive = onOpenArchive,
                onStorage = onOpenStorage,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            GaranziaHeader(
                eyebrow = "Archivio personale",
                title = "Garanzia",
                subtitle = "Scansiona, conserva e ritrova ogni scontrino senza perderlo.",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(
                    label = "Scontrini salvati",
                    value = savedReceiptCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "Ultima scansione",
                    value =
                        if (lastSavedPages > 0) {
                            "$lastSavedPages pag."
                        } else {
                            "—"
                        },
                    modifier = Modifier.weight(1f),
                )
            }

            databaseError?.let {
                InfoStrip(
                    text = "Problema archivio: $it · nuovo tentativo automatico in corso.",
                    positive = false,
                )
            }

            SectionCard(
                title = "Nuovo scontrino",
                subtitle = "Fotografa o importa lo scontrino. L'originale resta conservato.",
            ) {
                Button(
                    onClick = onScanReceipt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Scansiona scontrino")
                }
            }

            SectionCard(
                title = "Sicurezza copie",
                subtitle = "Mantieni una copia sul telefono e una seconda su Google Drive.",
            ) {
                StatusPill(
                    text =
                        if (dualCopyConfigured) {
                            "Doppia copia attiva"
                        } else {
                            "Da configurare"
                        },
                    positive = dualCopyConfigured,
                )

                AnimatedVisibility(
                    visible = storageMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    storageMessage?.let {
                        InfoStrip(
                            text = it,
                            positive =
                                !it.contains("erro", ignoreCase = true) &&
                                    !it.contains("riprov", ignoreCase = true),
                        )
                    }
                }
            }

            lastConfirmedProducts?.let { productCount ->
                InfoStrip(
                    text = "Ultimo scontrino confermato: $productCount prodotto/i riconosciuti.",
                    positive = true,
                )
            }

            Text(
                text = "I tuoi documenti restano disponibili anche senza connessione.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
