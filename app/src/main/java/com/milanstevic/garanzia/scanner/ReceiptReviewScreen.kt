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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.milanstevic.garanzia.ui.components.GaranziaHeader
import com.milanstevic.garanzia.ui.components.SectionCard
import com.milanstevic.garanzia.ui.components.StatusPill

@Composable
fun ReceiptReviewScreen(
    pages: List<Uri>,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
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
                eyebrow = "Prima del riconoscimento",
                title = "Controlla lo scontrino",
                subtitle = "Verifica che tutte le pagine siano leggibili prima di avviare l'OCR.",
            )

            StatusPill(
                text = "${pages.size} pagina/e pronte",
                positive = pages.isNotEmpty(),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(pages) { index, uri ->
                    SectionCard(
                        title = "Pagina ${index + 1}",
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Pagina ${index + 1} dello scontrino",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    enabled = pages.isNotEmpty(),
                ) {
                    Text("Conserva e leggi")
                }
            }
        }
    }
}
