package com.milanstevic.garanzia.ocr

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class PaddleModelFiles(
    val detection: File,
    val recognition: File,
)

data class OcrModelProgress(
    val label: String,
    val percent: Int?,
    val modelIndex: Int,
    val modelCount: Int,
)

private data class RemoteModel(
    val label: String,
    val fileName: String,
    val url: String,
    val sha256: String,
)

@Singleton
class PaddleModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val modelDir: File
        get() = File(context.filesDir, "ocr/ppocrv6-small").apply { mkdirs() }

    private val detection = RemoteModel(
        label = "Rilevamento testo",
        fileName = "det_v6_small.onnx",
        url = "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx/resolve/main/inference.onnx?download=true",
        sha256 = "d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e",
    )

    private val recognition = RemoteModel(
        label = "Riconoscimento testo",
        fileName = "rec_v6_small.onnx",
        url = "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec_onnx/resolve/main/inference.onnx?download=true",
        sha256 = "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634",
    )

    suspend fun ensureInstalled(
        progress: suspend (OcrModelProgress) -> Unit = {},
    ): PaddleModelFiles = withContext(Dispatchers.IO) {
        val models = listOf(detection, recognition)
        val installed = mutableListOf<File>()

        models.forEachIndexed { index, model ->
            installed += ensureModel(model, index, models.size, progress)
        }

        PaddleModelFiles(
            detection = installed[0],
            recognition = installed[1],
        )
    }

    fun areInstalled(): Boolean =
        listOf(detection, recognition).all { model ->
            val file = File(modelDir, model.fileName)
            file.isFile && runCatching { sha256(file) == model.sha256 }.getOrDefault(false)
        }

    private suspend fun ensureModel(
        model: RemoteModel,
        index: Int,
        count: Int,
        progress: suspend (OcrModelProgress) -> Unit,
    ): File {
        val target = File(modelDir, model.fileName)
        if (target.isFile && sha256(target) == model.sha256) {
            progress(OcrModelProgress(model.label, 100, index + 1, count))
            return target
        }

        target.delete()
        val part = File(modelDir, model.fileName + ".part")
        part.delete()

        progress(OcrModelProgress(model.label, 0, index + 1, count))

        val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 120_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Garanzia-Android/0.1")
        }

        return try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                error("Download modello fallito: HTTP ${connection.responseCode}")
            }

            val total = connection.contentLengthLong.takeIf { it > 0L }
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)

            connection.inputStream.buffered().use { input ->
                FileOutputStream(part).buffered().use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read

                        val percent = total?.let {
                            ((downloaded * 100L) / it).toInt().coerceIn(0, 100)
                        }
                        progress(OcrModelProgress(model.label, percent, index + 1, count))
                    }
                    output.flush()
                }
            }

            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (actualHash != model.sha256) {
                part.delete()
                error("Hash non valido per ${model.label}")
            }

            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }

            progress(OcrModelProgress(model.label, 100, index + 1, count))
            target
        } catch (t: Throwable) {
            part.delete()
            throw t
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
