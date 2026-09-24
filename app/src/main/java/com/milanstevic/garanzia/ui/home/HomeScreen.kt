package com.milanstevic.garanzia.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onScanReceipt: () -> Unit,
    lastSavedPages: Int,
    lastConfirmedProducts: Int?,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Garanzia",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Archivio scontrini e garanzie",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Scansiona uno scontrino. Il documento originale resta sul telefono.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onScanReceipt) {
                Text("Scansiona scontrino")
            }
            if (lastSavedPages > 0) {
                Text(
                    text = "Ultima acquisizione conservata: $lastSavedPages pagina/e.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            lastConfirmedProducts?.let { productCount ->
                Text(
                    text = "Ultimo scontrino confermato in questa sessione: $productCount prodotto/i.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
