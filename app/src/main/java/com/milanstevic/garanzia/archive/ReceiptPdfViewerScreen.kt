package com.milanstevic.garanzia.archive

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ReceiptPdfViewerScreen(
    pdfFile: File?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    var session by remember(pdfFile) { mutableStateOf<PdfSession?>(null) }
    var pageIndex by remember(pdfFile) { mutableIntStateOf(0) }
    var pageBitmap by remember(pdfFile) { mutableStateOf<Bitmap?>(null) }
    var viewerError by remember(pdfFile) { mutableStateOf<String?>(null) }
    var pageLoading by remember(pdfFile) { mutableStateOf(false) }

    DisposableEffect(pdfFile) {
        onDispose {
            pageBitmap?.recycle()
            pageBitmap = null
            session?.close()
            session = null
        }
    }

    LaunchedEffect(pdfFile) {
        session?.close()
        session = null
        pageIndex = 0
        pageBitmap?.recycle()
        pageBitmap = null
        viewerError = null

        if (pdfFile != null) {
            session = runCatching {
                withContext(Dispatchers.IO) {
                    PdfSession.open(pdfFile)
                }
            }.getOrElse { throwable ->
                viewerError = throwable.message ?: "Impossibile aprire il PDF"
                null
            }
        }
    }

    LaunchedEffect(session, pageIndex) {
        val current = session ?: return@LaunchedEffect

        pageLoading = true
        viewerError = null

        val rendered = runCatching {
            withContext(Dispatchers.IO) {
                current.renderPage(pageIndex)
            }
        }

        pageBitmap?.recycle()
        pageBitmap = rendered.getOrNull()

        rendered.exceptionOrNull()?.let { throwable ->
            viewerError = throwable.message ?: "Impossibile visualizzare questa pagina"
        }

        pageLoading = false
    }

    val currentSession = session
    val combinedError = error ?: viewerError

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onBack) {
                    Text("Indietro")
                }

                Text(
                    text = "PDF scontrino",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
            }

            when {
                loading -> {
                    CircularProgressIndicator()
                    Text("Preparazione PDF…")
                }

                combinedError != null -> {
                    Text(
                        text = combinedError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Riprova")
                    }
                }

                currentSession == null -> {
                    CircularProgressIndicator()
                    Text("Apertura PDF…")
                }

                else -> {
                    Text(
                        text = "Pagina ${pageIndex + 1} di ${currentSession.pageCount}",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    if (pageLoading) {
                        CircularProgressIndicator()
                    }

                    pageBitmap?.let { bitmap ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp),
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Pagina PDF ${pageIndex + 1}",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (pageIndex > 0) pageIndex--
                            },
                            enabled = pageIndex > 0 && !pageLoading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Pagina prima")
                        }

                        Button(
                            onClick = {
                                if (pageIndex < currentSession.pageCount - 1) {
                                    pageIndex++
                                }
                            },
                            enabled =
                                pageIndex < currentSession.pageCount - 1 &&
                                    !pageLoading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Pagina dopo")
                        }
                    }
                }
            }
        }
    }
}

private class PdfSession private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    val pageCount: Int
        get() = renderer.pageCount

    @Synchronized
    fun renderPage(index: Int): Bitmap {
        require(index in 0 until renderer.pageCount) {
            "Pagina PDF non valida"
        }

        renderer.openPage(index).use { page ->
            val width = MAX_RENDER_WIDTH
            val scale = width.toFloat() / page.width.toFloat()
            val height = (page.height * scale).roundToInt().coerceAtLeast(1)

            return Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888,
            ).also { bitmap ->
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                )
            }
        }
    }

    override fun close() {
        renderer.close()
        descriptor.close()
    }

    companion object {
        fun open(file: File): PdfSession {
            require(file.isFile && file.length() > 0L) {
                "PDF non disponibile"
            }

            val descriptor = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )

            return try {
                PdfSession(
                    descriptor = descriptor,
                    renderer = PdfRenderer(descriptor),
                )
            } catch (t: Throwable) {
                descriptor.close()
                throw t
            }
        }

        private const val MAX_RENDER_WIDTH = 1440
    }
}
