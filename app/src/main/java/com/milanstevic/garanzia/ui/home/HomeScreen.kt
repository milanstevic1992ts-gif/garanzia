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
fun HomeScreen() {
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
                text = "Fase 1 completata: fondamenta Android offline-first.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = {},
                enabled = false,
            ) {
                Text("Scansiona scontrino — disponibile dalla Fase 2")
            }
        }
    }
}
