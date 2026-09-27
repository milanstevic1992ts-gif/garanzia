package com.milanstevic.garanzia.product.attachment

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StoredAttachmentFile(
    val uri: Uri,
    val mimeType: String,
    val originalName: String?,
    val sizeBytes: Long,
)

@Singleton
class ProductAttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val rootDir: File
        get() = File(context.filesDir, "product_attachments").apply { mkdirs() }

    private val pendingDir: File
        get() = File(rootDir, "pending").apply { mkdirs() }

    private val storedDir: File
        get() = File(rootDir, "stored").apply { mkdirs() }

    fun newCameraCaptureFile(): File =
        File(pendingDir, "attachment_${UUID.randomUUID()}.jpg")

    fun importSelectedImage(sourceUri: Uri): StoredAttachmentFile {
        val mimeType = context.contentResolver.getType(sourceUri)
            ?.lowercase()
            ?: mimeFromName(queryDisplayName(sourceUri))
            ?: DEFAULT_IMAGE_MIME

        require(mimeType.startsWith("image/")) {
            "Seleziona un'immagine valida"
        }

        val originalName = queryDisplayName(sourceUri)
        val extension = extensionFor(
            mimeType = mimeType,
            originalName = originalName,
        )
        val target = File(storedDir, "attachment_${UUID.randomUUID()}.$extension")

        return try {
            context.contentResolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "Impossibile aprire l'immagine selezionata" }
                copyValidated(
                    input = input,
                    target = target,
                )
            }

            StoredAttachmentFile(
                uri = Uri.fromFile(target),
                mimeType = mimeType,
                originalName = originalName,
                sizeBytes = target.length(),
            )
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    fun finalizeCameraCapture(file: File): StoredAttachmentFile {
        require(file.parentFile?.canonicalFile == pendingDir.canonicalFile) {
            "File fotocamera non valido"
        }
        require(file.isFile && file.length() in 1..MAX_IMAGE_BYTES) {
            "Foto non valida o troppo grande"
        }

        val target = File(storedDir, "attachment_${UUID.randomUUID()}.jpg")

        try {
            file.inputStream().use { input ->
                copyValidated(
                    input = input,
                    target = target,
                )
            }
            file.delete()

            return StoredAttachmentFile(
                uri = Uri.fromFile(target),
                mimeType = "image/jpeg",
                originalName = "foto_${System.currentTimeMillis()}.jpg",
                sizeBytes = target.length(),
            )
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    fun importRestoredAttachment(
        input: InputStream,
        mimeType: String,
        originalName: String?,
    ): StoredAttachmentFile {
        require(mimeType.startsWith("image/")) {
            "Tipo allegato del backup non valido"
        }

        val extension = extensionFor(
            mimeType = mimeType,
            originalName = originalName,
        )
        val target = File(storedDir, "attachment_${UUID.randomUUID()}.$extension")

        return try {
            copyValidated(
                input = input,
                target = target,
            )
            StoredAttachmentFile(
                uri = Uri.fromFile(target),
                mimeType = mimeType,
                originalName = originalName,
                sizeBytes = target.length(),
            )
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    fun delete(uri: Uri): Boolean {
        val file = uri.path?.let(::File) ?: return false
        val parent = file.parentFile?.canonicalFile ?: return false

        if (parent != storedDir.canonicalFile && parent != pendingDir.canonicalFile) {
            return false
        }

        return !file.exists() || file.delete()
    }

    fun discardPending(file: File?) {
        if (file == null) return
        runCatching {
            if (file.parentFile?.canonicalFile == pendingDir.canonicalFile) {
                file.delete()
            }
        }
    }

    private fun copyValidated(
        input: InputStream,
        target: File,
    ) {
        target.outputStream().use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0L

            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue

                total += read
                require(total <= MAX_IMAGE_BYTES) {
                    "Immagine troppo grande"
                }
                output.write(buffer, 0, read)
            }

            output.flush()
            require(total > 0L) {
                "L'immagine selezionata è vuota"
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }

        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            cursor?.close()
        }
    }

    private fun extensionFor(
        mimeType: String,
        originalName: String?,
    ): String {
        val fromMime = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.lowercase()
            ?.takeIf(::safeExtension)

        if (fromMime != null) return fromMime

        val fromName = originalName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf(::safeExtension)

        return fromName ?: "jpg"
    }

    private fun mimeFromName(name: String?): String? {
        val extension = name
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf(::safeExtension)
            ?: return null

        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension)
    }

    private fun safeExtension(value: String): Boolean =
        value.length in 2..5 && value.all(Char::isLetterOrDigit)

    private companion object {
        const val DEFAULT_IMAGE_MIME = "image/jpeg"
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_IMAGE_BYTES = 30L * 1024L * 1024L
    }
}
