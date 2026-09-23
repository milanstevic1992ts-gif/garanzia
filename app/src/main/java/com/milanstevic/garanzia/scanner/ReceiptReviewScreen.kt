package com.milanstevic.garanzia.scanner

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun ReceiptReviewScreen(
    pages: List<Uri>,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Controlla lo scontrino",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "L'immagine originale viene conservata. L'OCR arriverà nella Fase 3.",
                style = MaterialTheme.typography.bodyMedium,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(pages) { index, uri ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pagina ${index + 1}")
                        AsyncImage(
                            model = uri,
                            contentDescription = "Pagina ${index + 1} dello scontrino",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDiscard,
                ) {
                    Text("Rifai")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onAccept,
                ) {
                    Text("Conserva scontrino")
                }
            }
        }
    }
}
