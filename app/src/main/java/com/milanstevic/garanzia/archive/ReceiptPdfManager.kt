package com.milanstevic.garanzia.archive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.milanstevic.garanzia.data.local.ReceiptWithDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

@Singleton
class ReceiptPdfManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun createOrReplacePdf(
        details: ReceiptWithDetails,
    ): File = withContext(Dispatchers.IO) {
        require(details.pages.isNotEmpty()) {
            "Lo scontrino non contiene pagine originali"
        }

        val directory = File(context.cacheDir, "receipt_pdfs").apply { mkdirs() }
        val output = File(directory, "${details.receipt.id}.pdf")
        val temporary = File(directory, "${details.receipt.id}.pdf.tmp")

        temporary.delete()

        val document = PdfDocument()
        var writtenPages = 0

        try {
            details.pages
                .sortedBy { it.pageIndex }
                .forEachIndexed { index, page ->
                    val bitmap = decodeForPdf(Uri.parse(page.originalUri))
                        ?: return@forEachIndexed

                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(
                            PDF_WIDTH,
                            PDF_HEIGHT,
                            index + 1,
                        ).create()

                        val pdfPage = document.startPage(pageInfo)
                        try {
                            val canvas = pdfPage.canvas
                            canvas.drawColor(Color.WHITE)

                            val target = fitCenter(
                                sourceWidth = bitmap.width,
                                sourceHeight = bitmap.height,
                                left = PAGE_MARGIN.toFloat(),
                                top = PAGE_MARGIN.toFloat(),
                                right = (PDF_WIDTH - PAGE_MARGIN).toFloat(),
                                bottom = (PDF_HEIGHT - PAGE_MARGIN).toFloat(),
                            )

                            canvas.drawBitmap(bitmap, null, target, null)
                        } finally {
                            document.finishPage(pdfPage)
                        }

                        writtenPages++
                    } finally {
                        bitmap.recycle()
                    }
                }

            require(writtenPages > 0) {
                "Impossibile leggere le immagini originali dello scontrino"
            }

            temporary.outputStream().buffered().use { stream ->
                document.writeTo(stream)
            }
        } finally {
            document.close()
        }

        if (output.exists()) output.delete()

        if (!temporary.renameTo(output)) {
            temporary.copyTo(output, overwrite = true)
            temporary.delete()
        }

        output
    }

    private fun decodeForPdf(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        openSource(uri).use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        var sampleSize = 1
        while (
            max(
                bounds.outWidth / sampleSize,
                bounds.outHeight / sampleSize,
            ) > MAX_DECODE_SIDE
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return openSource(uri).use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun openSource(uri: Uri): InputStream =
        when (uri.scheme) {
            null, "file" -> {
                val path = requireNotNull(uri.path) {
                    "Percorso immagine originale non valido"
                }
                FileInputStream(File(path))
            }

            else -> requireNotNull(
                context.contentResolver.openInputStream(uri),
            ) {
                "Impossibile aprire l'immagine originale"
            }
        }

    private fun fitCenter(
        sourceWidth: Int,
        sourceHeight: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): RectF {
        val targetWidth = right - left
        val targetHeight = bottom - top
        val sourceRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
        val targetRatio = targetWidth / targetHeight

        val width: Float
        val height: Float

        if (sourceRatio > targetRatio) {
            width = targetWidth
            height = width / sourceRatio
        } else {
            height = targetHeight
            width = height * sourceRatio
        }

        val x = left + (targetWidth - width) / 2f
        val y = top + (targetHeight - height) / 2f

        return RectF(
            x,
            y,
            x + width,
            y + height,
        )
    }

    private companion object {
        const val PDF_WIDTH = 1240
        const val PDF_HEIGHT = 1754
        const val PAGE_MARGIN = 48
        const val MAX_DECODE_SIDE = 2400
    }
}
