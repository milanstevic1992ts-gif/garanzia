package com.milanstevic.garanzia.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.milanstevic.garanzia.ui.components.GaranziaHeader
import com.milanstevic.garanzia.ui.components.InfoStrip
import com.milanstevic.garanzia.ui.components.PremiumBottomBar
import com.milanstevic.garanzia.ui.components.PremiumDestination
import com.milanstevic.garanzia.ui.components.SectionCard
import com.milanstevic.garanzia.ui.components.StatusPill

@Composable
fun StorageSettingsScreen(
    state: StorageSettingsState,
    syncInProgress: Boolean,
    syncMessage: String?,
    onChoosePhone: () -> Unit,
    onChooseDrive: () -> Unit,
    onClearPhone: () -> Unit,
    onClearDrive: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            PremiumBottomBar(
                selected = PremiumDestination.STORAGE,
                onHome = onOpenHome,
                onArchive = onOpenArchive,
                onStorage = {},
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GaranziaHeader(
                eyebrow = "Backup e copie",
                title = "Archiviazione",
                subtitle = "Scegli dove conservare le copie esterne dei tuoi scontrini.",
            )

            StatusPill(
                text =
                    if (state.bothConfigured) {
                        "Doppia copia attiva"
                    } else {
                        "Configurazione incompleta"
                    },
                positive = state.bothConfigured,
            )

            StorageTargetCard(
                title = "Telefono",
                description = "Una copia fisica in una cartella scelta da te.",
                configured = state.phoneConfigured,
                label = state.phone.label,
                onChoose = onChoosePhone,
                onClear = onClearPhone,
            )

            StorageTargetCard(
                title = "Google Drive",
                description = "Una seconda copia nel tuo spazio Drive.",
                configured = state.driveConfigured,
                label = state.drive.label,
                helper = "Nel selettore Android apri Google Drive dal menu laterale e scegli la cartella.",
                onChoose = onChooseDrive,
                onClear = onClearDrive,
            )

            SectionCard(
                title = "Sincronizzazione",
                subtitle = "Controlla l'intero archivio e completa le copie mancanti.",
            ) {
                Button(
                    onClick = onSyncNow,
                    enabled =
                        !syncInProgress &&
                            (state.phoneConfigured || state.driveConfigured),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (syncInProgress) {
                            "Sincronizzazione…"
                        } else {
                            "Sincronizza archivio adesso"
                        },
                    )
                }

                AnimatedVisibility(
                    visible = syncMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    syncMessage?.let {
                        InfoStrip(
                            text = it,
                            positive =
                                !it.contains("erro", ignoreCase = true) &&
                                    !it.contains("riprov", ignoreCase = true),
                        )
                    }
                }
            }

            Text(
                text = "La copia interna dell'app rimane sempre attiva, anche se telefono o Drive non sono configurati.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageTargetCard(
    title: String,
    description: String,
    configured: Boolean,
    label: String?,
    helper: String? = null,
    onChoose: () -> Unit,
    onClear: () -> Unit,
) {
    SectionCard(
        title = title,
        subtitle = description,
    ) {
        StatusPill(
            text =
                if (configured) {
                    "Configurata"
                } else {
                    "Non configurata"
                },
            positive = configured,
        )

        if (configured) {
            Text(
                text = label ?: "Cartella selezionata",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        helper?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FilledTonalButton(
            onClick = onChoose,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (configured) "Cambia cartella" else "Scegli cartella")
        }

        if (configured) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Rimuovi configurazione")
            }
        }
    }
}
