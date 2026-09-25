package com.milanstevic.garanzia.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.FilePaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class OcrPoint(
    val x: Float,
    val y: Float,
)

data class OcrLine(
    val text: String,
    val confidence: Float,
    val box: List<OcrPoint>,
)

data class OcrPageResult(
    val pageIndex: Int,
    val lines: List<OcrLine>,
    val rawText: String,
    val totalTimeMs: Long,
)

data class OcrReceiptResult(
    val pages: List<OcrPageResult>,
) {
    val rawText: String
        get() = pages.joinToString("\n\n") { it.rawText }
}

@Singleton
class ReceiptOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: PaddleModelRepository,
) {
    suspend fun recognize(
        pages: List<Uri>,
        modelProgress: suspend (OcrModelProgress) -> Unit = {},
    ): OcrReceiptResult {
        require(pages.isNotEmpty()) { "Nessuna pagina da leggere" }
        check(OpenCVUtils.init(context)) {
            "OpenCV non inizializzato. Chiudi e riapri l'app; se il problema continua aggiorna l'app."
        }

        val files = models.ensureInstalled(modelProgress)
        val characters = loadCharacterList()

        val ocr = FilePaddleOCR.create(
            context = context,
            detModelFile = files.detection,
            recModelFile = files.recognition,
            characterList = characters,
            config = PaddleOCRConfig(
                recScoreThresh = 0.0f,
                recBatchSize = 1,
            ),
            engineConfig = EngineConfig(
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
            ),
        )

        return try {
            val results = mutableListOf<OcrPageResult>()
            pages.forEachIndexed { index, uri ->
                val bitmap = withContext(Dispatchers.IO) { decodeBitmap(uri) }
                try {
                    val result = ocr.recognize(bitmap)
                    val lines = result.results.map { line ->
                        OcrLine(
                            text = line.text,
                            confidence = line.confidence,
                            box = line.box.points.map { point ->
                                OcrPoint(point.x, point.y)
                            },
                        )
                    }
                    results += OcrPageResult(
                        pageIndex = index,
                        lines = lines,
                        rawText = lines.joinToString("\n") { it.text },
                        totalTimeMs = result.totalTimeMs,
                    )
                } finally {
                    bitmap.recycle()
                }
            }
            OcrReceiptResult(results)
        } finally {
            ocr.release()
        }
    }

    private fun loadCharacterList(): List<String> {
        val chars = context.assets.open("ocr/dict_v6.txt")
            .bufferedReader()
            .useLines { lines -> lines.toList() }
            .dropLastWhile { it.isEmpty() }
            .toMutableList()

        if (chars.lastOrNull() != " ") chars += " "
        return chars
    }

    private fun decodeBitmap(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }

        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Immagine scontrino non leggibile"
        }

        val maxPixels = 16_000_000L
        var sample = 1
        while (
            (bounds.outWidth / sample).toLong() *
                (bounds.outHeight / sample).toLong() > maxPixels
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return open(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("Impossibile decodificare lo scontrino")
    }

    private fun open(uri: Uri) =
        when (uri.scheme) {
            null, "file" -> requireNotNull(uri.path).let { java.io.File(it).inputStream() }
            else -> requireNotNull(context.contentResolver.openInputStream(uri))
        }
}
