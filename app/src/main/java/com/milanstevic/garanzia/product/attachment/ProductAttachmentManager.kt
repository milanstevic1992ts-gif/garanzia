package com.milanstevic.garanzia.product.attachment

import android.net.Uri
import com.milanstevic.garanzia.data.ReceiptRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ProductAttachmentManager @Inject constructor(
    private val repository: ReceiptRepository,
    private val store: ProductAttachmentStore,
) {
    suspend fun addFromGallery(
        receiptId: String,
        productId: Long,
        category: ProductAttachmentCategory,
        sourceUri: Uri,
    ): ProductAttachmentItem = withContext(Dispatchers.IO) {
        val stored = store.importSelectedImage(sourceUri)

        try {
            ProductAttachmentItem.from(
                repository.addProductAttachment(
                    receiptId = receiptId,
                    productId = productId,
                    category = category.storedValue,
                    localUri = stored.uri.toString(),
                    mimeType = stored.mimeType,
                    originalName = stored.originalName,
                    note = null,
                ),
            )
        } catch (t: Throwable) {
            store.delete(stored.uri)
            throw t
        }
    }

    suspend fun addFromCamera(
        receiptId: String,
        productId: Long,
        category: ProductAttachmentCategory,
        capturedFile: File,
    ): ProductAttachmentItem = withContext(Dispatchers.IO) {
        val stored = store.finalizeCameraCapture(capturedFile)

        try {
            ProductAttachmentItem.from(
                repository.addProductAttachment(
                    receiptId = receiptId,
                    productId = productId,
                    category = category.storedValue,
                    localUri = stored.uri.toString(),
                    mimeType = stored.mimeType,
                    originalName = stored.originalName,
                    note = null,
                ),
            )
        } catch (t: Throwable) {
            store.delete(stored.uri)
            throw t
        }
    }

    suspend fun updateNote(
        attachment: ProductAttachmentItem,
        note: String?,
    ) = withContext(Dispatchers.IO) {
        repository.updateProductAttachmentNote(
            receiptId = attachment.receiptId,
            productId = attachment.productId,
            attachmentId = attachment.id,
            note = note,
        )
    }

    suspend fun delete(
        attachment: ProductAttachmentItem,
    ): Boolean = withContext(Dispatchers.IO) {
        val deleted = repository.deleteProductAttachment(
            receiptId = attachment.receiptId,
            productId = attachment.productId,
            attachmentId = attachment.id,
        )

        store.delete(Uri.parse(deleted.localUri))
    }

    fun newCameraCaptureFile(): File =
        store.newCameraCaptureFile()

    fun discardPendingCamera(file: File?) {
        store.discardPending(file)
    }
}
