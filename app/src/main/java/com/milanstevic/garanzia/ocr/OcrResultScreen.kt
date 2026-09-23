package com.milanstevic.garanzia.ocr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun OcrResultScreen(
    status: String,
    progressPercent: Int?,
    result: OcrReceiptResult?,
    error: String?,
    onDone: () -> Unit,
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
                text = "OCR scontrino",
                style = MaterialTheme.typography.headlineSmall,
            )

            if (result == null && error == null) {
                CircularProgressIndicator()
                Text(status)
                progressPercent?.let { Text("$it%") }
            }

            error?.let {
                Text(
                    text = "OCR non completato: $it",
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "Lo scontrino originale è comunque già stato conservato.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            result?.let { receipt ->
                Text(
                    text = "Testo rilevato — nessuna interpretazione IA in questa fase",
                    style = MaterialTheme.typography.titleMedium,
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    receipt.pages.forEach { page ->
                        item {
                            Text(
                                text = "Pagina ${page.pageIndex + 1}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        items(page.lines) { line ->
                            val confidence = (line.confidence * 100f).roundToInt()
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(line.text)
                                Text(
                                    text = "Affidabilità OCR: $confidence%",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        item {
                            Text(
                                text = "Tempo OCR: ${page.totalTimeMs} ms",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (result != null || error != null) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Torna alla home")
                }
            }
        }
    }
}
