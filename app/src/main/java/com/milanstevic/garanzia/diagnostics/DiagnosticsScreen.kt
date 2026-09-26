package com.milanstevic.garanzia.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.milanstevic.garanzia.ui.components.GaranziaHeader
import com.milanstevic.garanzia.ui.components.InfoStrip
import com.milanstevic.garanzia.ui.components.PremiumLoadingCard
import com.milanstevic.garanzia.ui.components.SectionCard
import com.milanstevic.garanzia.ui.components.StatusPill

@Composable
fun DiagnosticsScreen(
    snapshot: DiagnosticsSnapshot?,
    loading: Boolean,
    onRunChecks: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onBack) {
                    Text("Indietro")
                }

                GaranziaHeader(
                    eyebrow = "Stato sistema",
                    title = "Diagnostica",
                    subtitle = "Controllo rapido di OCR, archivio e copie esterne.",
                    modifier = Modifier.weight(1f),
                )
            }

            if (loading) {
                PremiumLoadingCard(
                    label = "Controllo componenti",
                )
            } else {
                val current = snapshot

                if (current == null) {
                    InfoStrip(
                        text = "Premi Avvia controllo per verificare il sistema.",
                        positive = true,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusPill(
                            text =
                                if (current.healthy) {
                                    "Sistema operativo"
                                } else {
                                    "${current.errors} errori"
                                },
                            positive = current.healthy,
                        )

                        if (current.warnings > 0) {
                            StatusPill(
                                text = "${current.warnings} avvisi",
                                positive = false,
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(current.items) { item ->
                            DiagnosticCard(item)
                        }
                    }
                }
            }

            Button(
                onClick = onRunChecks,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (loading) "Controllo…" else "Avvia controllo")
            }
        }
    }
}

@Composable
private fun DiagnosticCard(item: DiagnosticItem) {
    SectionCard(
        title = item.title,
    ) {
        StatusPill(
            text = when (item.level) {
                DiagnosticLevel.OK -> "OK"
                DiagnosticLevel.WARNING -> "Attenzione"
                DiagnosticLevel.ERROR -> "Errore"
            },
            positive = item.level == DiagnosticLevel.OK,
        )

        Text(
            text = item.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
