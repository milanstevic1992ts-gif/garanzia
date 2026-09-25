package com.milanstevic.garanzia.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    onBack: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Archiviazione",
                style = MaterialTheme.typography.headlineSmall,
            )

            Text(
                text = "La copia interna dell'app resta sempre attiva. Qui puoi aggiungere una copia fisica sul telefono e una seconda copia su Google Drive.",
                style = MaterialTheme.typography.bodyMedium,
            )

            StorageTargetCard(
                title = "Cartella sul telefono",
                configured = state.phoneConfigured,
                label = state.phone.label,
                onChoose = onChoosePhone,
                onClear = onClearPhone,
            )

            StorageTargetCard(
                title = "Cartella Google Drive",
                configured = state.driveConfigured,
                label = state.drive.label,
                helper = "Nel selettore Android scegli Google Drive dal pannello laterale e poi la cartella desiderata.",
                onChoose = onChooseDrive,
                onClear = onClearDrive,
            )

            Text(
                text =
                    if (state.bothConfigured) {
                        "Doppia copia automatica: ATTIVA"
                    } else {
                        "Doppia copia automatica: configura entrambe le cartelle"
                    },
                style = MaterialTheme.typography.titleMedium,
            )

            Button(
                onClick = onSyncNow,
                enabled = !syncInProgress && (state.phoneConfigured || state.driveConfigured),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (syncInProgress) "Sincronizzazione…" else "Sincronizza archivio adesso")
            }

            syncMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Torna alla home")
            }
        }
    }
}

@Composable
private fun StorageTargetCard(
    title: String,
    configured: Boolean,
    label: String?,
    helper: String? = null,
    onChoose: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text =
                    if (configured) {
                        "Configurata: ${label ?: "cartella selezionata"}"
                    } else {
                        "Non configurata"
                    },
                style = MaterialTheme.typography.bodyMedium,
            )

            helper?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
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
}
