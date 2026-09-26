package com.milanstevic.garanzia.scanner

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val rootDir: File
        get() = File(context.filesDir, "receipts").apply { mkdirs() }

    private val stagingDir: File
        get() = File(rootDir, "staging").apply { mkdirs() }

    private val originalsDir: File
        get() = File(rootDir, "originals").apply { mkdirs() }

    fun newCameraCaptureFile(): File =
        File(stagingDir, "capture_${UUID.randomUUID()}.jpg")

    fun stageContentUris(sourceUris: List<Uri>): List<Uri> {
        if (sourceUris.isEmpty()) return emptyList()

        val created = mutableListOf<File>()

        return try {
            sourceUris.forEach { source ->
                val target = File(stagingDir, "scan_${UUID.randomUUID()}.jpg")
                created += target

                context.contentResolver.openInputStream(source).use { input ->
                    requireNotNull(input) { "Impossibile aprire una pagina acquisita" }

                    target.outputStream().use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }

                require(target.isFile && target.length() > 0L) {
                    "Una pagina acquisita risulta vuota"
                }
            }

            created.map(Uri::fromFile)
        } catch (_: Throwable) {
            created.forEach(File::delete)
            emptyList()
        }
    }

    fun finalizeStaged(stagedUris: List<Uri>): List<Uri> {
        if (stagedUris.isEmpty()) return emptyList()

        val sources = stagedUris.mapNotNull { uri ->
            uri.path?.let(::File)
        }

        if (
            sources.size != stagedUris.size ||
            sources.any { !it.isFile || it.parentFile != stagingDir }
        ) {
            return emptyList()
        }

        val targets = mutableListOf<File>()

        return try {
            sources.forEach { source ->
                val target = File(originalsDir, "receipt_${UUID.randomUUID()}.jpg")
                targets += target

                source.inputStream().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }

                require(target.isFile && target.length() > 0L) {
                    "Una pagina originale risulta vuota"
                }
            }

            sources.forEach(File::delete)
            targets.map(Uri::fromFile)
        } catch (_: Throwable) {
            targets.forEach(File::delete)
            emptyList()
        }
    }

    fun deleteOriginals(originalUris: List<Uri>): Int {
        var failures = 0

        originalUris.forEach { uri ->
            val file = uri.path?.let(::File)
            if (
                file == null ||
                file.parentFile != originalsDir ||
                (file.exists() && !file.delete())
            ) {
                failures++
            }
        }

        return failures
    }

    fun discardStaged(stagedUris: List<Uri>) {
        stagedUris.forEach { uri ->
            uri.path
                ?.let(::File)
                ?.takeIf { it.parentFile == stagingDir }
                ?.delete()
        }
    }
}
