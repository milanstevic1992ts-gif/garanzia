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

    fun stageContentUris(sourceUris: List<Uri>): List<Uri> =
        sourceUris.mapNotNull { source ->
            runCatching {
                val target = File(stagingDir, "scan_${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(source).use { input ->
                    requireNotNull(input) { "Unable to open scanned image" }
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                Uri.fromFile(target)
            }.getOrNull()
        }

    fun finalizeStaged(stagedUris: List<Uri>): List<Uri> =
        stagedUris.mapNotNull { uri ->
            runCatching {
                val source = requireNotNull(uri.path).let(::File)
                require(source.exists()) { "Staged receipt does not exist" }

                val target = File(originalsDir, "receipt_${UUID.randomUUID()}.jpg")
                if (!source.renameTo(target)) {
                    source.copyTo(target, overwrite = false)
                    source.delete()
                }
                Uri.fromFile(target)
            }.getOrNull()
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
