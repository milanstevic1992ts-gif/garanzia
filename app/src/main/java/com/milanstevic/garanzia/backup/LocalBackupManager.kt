package com.milanstevic.garanzia.backup

import android.content.Context
import android.net.Uri
import com.milanstevic.garanzia.archive.ReceiptPdfManager
import com.milanstevic.garanzia.data.ReceiptRepository
import com.milanstevic.garanzia.data.local.GARANZIA_SCHEMA_VERSION
import com.milanstevic.garanzia.data.local.ProductAttachmentEntity
import com.milanstevic.garanzia.data.local.ReceiptPageEntity
import com.milanstevic.garanzia.data.local.ReceiptWithDetails
import com.milanstevic.garanzia.product.attachment.ProductAttachmentCategory
import com.milanstevic.garanzia.product.attachment.ProductAttachmentStore
import com.milanstevic.garanzia.scanner.ReceiptFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LocalBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ReceiptRepository,
    private val fileStore: ReceiptFileStore,
    private val productAttachmentStore: ProductAttachmentStore,
    private val pdfManager: ReceiptPdfManager,
) {
    suspend fun createBackup(targetUri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val receipts = repository.getAllReceipts()
        val manifest = buildManifest(receipts)
        val temp = File.createTempFile("garanzia-backup-", ".zip", context.cacheDir)

        try {
            writeBackupZip(
                target = temp,
                manifest = manifest,
                receipts = receipts,
            )

            val copiedBytes = temp.inputStream().use { input ->
                openTarget(targetUri).use { output ->
                    val copied = input.copyTo(output)
                    output.flush()
                    copied
                }
            }

            require(copiedBytes == temp.length() && copiedBytes > 0L) {
                "Il backup non è stato scritto completamente"
            }

            BackupSummary(
                receiptCount = manifest.receiptCount,
                productCount = manifest.productCount,
                pageCount = manifest.pageCount,
                sizeBytes = copiedBytes,
                attachmentCount = manifest.attachmentCount,
            )
        } finally {
            temp.delete()
        }
    }

    suspend fun inspectBackup(sourceUri: Uri): BackupPreview = withContext(Dispatchers.IO) {
        openSource(sourceUri).use { source ->
            ZipInputStream(BufferedInputStream(source)).use { zip ->
                val first = requireNotNull(zip.nextEntry) {
                    "Il file di backup è vuoto"
                }
                require(!first.isDirectory && first.name == MANIFEST_ENTRY) {
                    "Formato backup non riconosciuto"
                }

                val manifest = BackupManifestCodec.decode(
                    readTextEntry(zip, MAX_MANIFEST_BYTES),
                )
                validateManifest(manifest)

                BackupPreview(
                    createdAtEpochMs = manifest.createdAtEpochMs,
                    receiptCount = manifest.receiptCount,
                    productCount = manifest.productCount,
                    pageCount = manifest.pageCount,
                    attachmentCount = manifest.attachmentCount,
                )
            }
        }
    }

    suspend fun restoreBackup(sourceUri: Uri): RestoreSummary = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply {
            require(mkdirs()) { "Impossibile preparare il ripristino" }
        }

        val importedOriginalUris = mutableListOf<Uri>()
        val importedAttachmentUris = mutableListOf<Uri>()

        try {
            val extracted = extractAndValidate(sourceUri, staging)
            val before = repository.getAllReceipts()

            val restored = extracted.manifest.receipts.map { record ->
                val restoredPages = record.pages
                    .sortedBy { it.pageIndex }
                    .map { page ->
                        val stagedFile = requireNotNull(extracted.files[page.entryName]) {
                            "Pagina mancante nel backup"
                        }
                        val newUri = stagedFile.inputStream().use(fileStore::importRestoredOriginal)
                        importedOriginalUris += newUri

                        ReceiptPageEntity(
                            id = page.id,
                            receiptId = record.receipt.id,
                            pageIndex = page.pageIndex,
                            originalUri = newUri.toString(),
                        )
                    }

                val restoredAttachments = record.attachments
                    .sortedBy { it.createdAtEpochMs }
                    .map { attachment ->
                        val stagedFile = requireNotNull(extracted.files[attachment.entryName]) {
                            "Allegato mancante nel backup"
                        }
                        val stored = stagedFile.inputStream().use { input ->
                            productAttachmentStore.importRestoredAttachment(
                                input = input,
                                mimeType = attachment.mimeType,
                                originalName = attachment.originalName,
                            )
                        }
                        importedAttachmentUris += stored.uri

                        ProductAttachmentEntity(
                            id = attachment.id,
                            receiptId = attachment.receiptId,
                            productId = attachment.productId,
                            category = attachment.category,
                            localUri = stored.uri.toString(),
                            mimeType = attachment.mimeType,
                            originalName = attachment.originalName,
                            note = attachment.note,
                            createdAtEpochMs = attachment.createdAtEpochMs,
                        )
                    }

                ReceiptWithDetails(
                    receipt = record.receipt,
                    products = record.products.sortedBy { it.position },
                    pages = restoredPages,
                    attachments = restoredAttachments,
                )
            }

            try {
                repository.replaceArchive(restored)
            } catch (t: Throwable) {
                fileStore.deleteOriginals(importedOriginalUris)
                importedAttachmentUris.forEach(productAttachmentStore::delete)
                throw t
            }

            fileStore.deleteOriginals(
                before.flatMap { details ->
                    details.pages.map { Uri.parse(it.originalUri) }
                },
            )
            before
                .flatMap { it.attachments }
                .forEach { attachment ->
                    productAttachmentStore.delete(Uri.parse(attachment.localUri))
                }

            (before.map { it.receipt.id } + restored.map { it.receipt.id })
                .distinct()
                .forEach { receiptId ->
                    pdfManager.deleteCachedPdf(receiptId)
                }

            RestoreSummary(
                receiptCount = restored.size,
                productCount = restored.sumOf { it.products.size },
                pageCount = restored.sumOf { it.pages.size },
                attachmentCount = restored.sumOf { it.attachments.size },
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun buildManifest(receipts: List<ReceiptWithDetails>): BackupManifest {
        val records = receipts.map { details ->
            val safeReceiptId = sanitizePathComponent(details.receipt.id)
            val pages = details.pages
                .sortedBy { it.pageIndex }
                .map { page ->
                    val entryName = "originals/$safeReceiptId/page_${page.pageIndex}.jpg"
                    val digest = digestSource(
                        uri = Uri.parse(page.originalUri),
                        maxBytes = MAX_PAGE_BYTES,
                    )

                    BackupPageRecord(
                        id = page.id,
                        receiptId = details.receipt.id,
                        pageIndex = page.pageIndex,
                        entryName = entryName,
                        sizeBytes = digest.sizeBytes,
                        sha256 = digest.sha256,
                    )
                }

            val attachments = details.attachments
                .sortedBy { it.createdAtEpochMs }
                .map { attachment ->
                    val entryName =
                        "attachments/$safeReceiptId/${attachment.productId}/attachment_${attachment.id}.bin"
                    val digest = digestSource(
                        uri = Uri.parse(attachment.localUri),
                        maxBytes = MAX_ATTACHMENT_BYTES,
                    )

                    BackupAttachmentRecord(
                        id = attachment.id,
                        receiptId = attachment.receiptId,
                        productId = attachment.productId,
                        category = attachment.category,
                        mimeType = attachment.mimeType,
                        originalName = attachment.originalName,
                        note = attachment.note,
                        createdAtEpochMs = attachment.createdAtEpochMs,
                        entryName = entryName,
                        sizeBytes = digest.sizeBytes,
                        sha256 = digest.sha256,
                    )
                }

            BackupReceiptRecord(
                receipt = details.receipt,
                products = details.products.sortedBy { it.position },
                pages = pages,
                attachments = attachments,
            )
        }

        val manifest = BackupManifest(
            format = BACKUP_FORMAT,
            formatVersion = BACKUP_FORMAT_VERSION,
            databaseSchemaVersion = GARANZIA_SCHEMA_VERSION,
            createdAtEpochMs = System.currentTimeMillis(),
            receipts = records,
        )
        validateManifest(manifest)
        return manifest
    }

    private fun writeBackupZip(
        target: File,
        manifest: BackupManifest,
        receipts: List<ReceiptWithDetails>,
    ) {
        val pageSources = receipts
            .flatMap { details ->
                details.pages.map { page ->
                    (details.receipt.id to page.pageIndex) to Uri.parse(page.originalUri)
                }
            }
            .toMap()
        val attachmentSources = receipts
            .flatMap { details ->
                details.attachments.map { attachment ->
                    attachment.id to Uri.parse(attachment.localUri)
                }
            }
            .toMap()

        ZipOutputStream(BufferedOutputStream(target.outputStream())).use { zip ->
            writeTextEntry(
                zip = zip,
                name = MANIFEST_ENTRY,
                text = BackupManifestCodec.encode(manifest),
            )

            manifest.receipts.forEach { record ->
                record.pages.forEach { page ->
                    val sourceUri = requireNotNull(
                        pageSources[record.receipt.id to page.pageIndex],
                    ) { "Originale non trovato durante il backup" }

                    writeBinaryEntry(
                        zip = zip,
                        entryName = page.entryName,
                        sourceUri = sourceUri,
                        expectedSize = page.sizeBytes,
                        expectedSha256 = page.sha256,
                        maxBytes = MAX_PAGE_BYTES,
                        changedMessage = "Un originale è cambiato durante il backup",
                    )
                }

                record.attachments.forEach { attachment ->
                    val sourceUri = requireNotNull(
                        attachmentSources[attachment.id],
                    ) { "Allegato non trovato durante il backup" }

                    writeBinaryEntry(
                        zip = zip,
                        entryName = attachment.entryName,
                        sourceUri = sourceUri,
                        expectedSize = attachment.sizeBytes,
                        expectedSha256 = attachment.sha256,
                        maxBytes = MAX_ATTACHMENT_BYTES,
                        changedMessage = "Un allegato è cambiato durante il backup",
                    )
                }
            }

            writeTextEntry(
                zip = zip,
                name = COMPLETE_ENTRY,
                text = "complete=true\nformatVersion=$BACKUP_FORMAT_VERSION\n",
            )
        }

        require(target.isFile && target.length() > 0L) {
            "Il pacchetto di backup è vuoto"
        }
    }

    private fun writeBinaryEntry(
        zip: ZipOutputStream,
        entryName: String,
        sourceUri: Uri,
        expectedSize: Long,
        expectedSha256: String,
        maxBytes: Long,
        changedMessage: String,
    ) {
        zip.putNextEntry(ZipEntry(entryName))
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L

        openSource(sourceUri).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                digest.update(buffer, 0, read)
                zip.write(buffer, 0, read)
                written += read
                require(written <= maxBytes) {
                    "File troppo grande durante il backup"
                }
            }
        }
        zip.closeEntry()

        require(written == expectedSize) {
            changedMessage
        }
        require(digest.digest().toHex() == expectedSha256) {
            changedMessage
        }
    }

    private fun extractAndValidate(
        sourceUri: Uri,
        staging: File,
    ): ExtractedBackup {
        openSource(sourceUri).use { source ->
            ZipInputStream(BufferedInputStream(source)).use { zip ->
                val first = requireNotNull(zip.nextEntry) {
                    "Il file di backup è vuoto"
                }
                require(!first.isDirectory && first.name == MANIFEST_ENTRY) {
                    "Formato backup non riconosciuto"
                }

                val manifest = BackupManifestCodec.decode(
                    readTextEntry(zip, MAX_MANIFEST_BYTES),
                )
                validateManifest(manifest)

                val expected = buildMap {
                    manifest.receipts.forEach { record ->
                        record.pages.forEach { page ->
                            put(
                                page.entryName,
                                ExpectedBackupFile(
                                    sizeBytes = page.sizeBytes,
                                    sha256 = page.sha256,
                                    maxBytes = MAX_PAGE_BYTES,
                                ),
                            )
                        }
                        record.attachments.forEach { attachment ->
                            put(
                                attachment.entryName,
                                ExpectedBackupFile(
                                    sizeBytes = attachment.sizeBytes,
                                    sha256 = attachment.sha256,
                                    maxBytes = MAX_ATTACHMENT_BYTES,
                                ),
                            )
                        }
                    }
                }

                val extracted = mutableMapOf<String, File>()
                val seen = mutableSetOf(MANIFEST_ENTRY)
                var complete = false
                var totalBytes = 0L

                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(seen.add(entry.name)) {
                        "Il backup contiene file duplicati"
                    }
                    require(isSafeEntryName(entry.name)) {
                        "Il backup contiene un percorso non valido"
                    }

                    if (entry.name == COMPLETE_ENTRY) {
                        require(!entry.isDirectory) {
                            "Marcatore backup non valido"
                        }
                        val marker = readTextEntry(zip, MAX_MARKER_BYTES)
                        complete = marker.contains("complete=true")
                        continue
                    }

                    val expectation = requireNotNull(expected[entry.name]) {
                        "Il backup contiene file inattesi"
                    }
                    require(!entry.isDirectory) {
                        "File backup non valido"
                    }

                    val target = File(staging, "item_${extracted.size}.bin")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var written = 0L

                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            written += read
                            totalBytes += read
                            require(
                                written <= expectation.sizeBytes &&
                                    written <= expectation.maxBytes
                            ) {
                                "Dimensione file backup non valida"
                            }
                            require(totalBytes <= MAX_TOTAL_RESTORE_BYTES) {
                                "Il backup supera il limite di sicurezza"
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }

                    require(written == expectation.sizeBytes && written > 0L) {
                        "File backup incompleto"
                    }
                    require(digest.digest().toHex() == expectation.sha256) {
                        "Checksum file backup non valido"
                    }

                    extracted[entry.name] = target
                }

                require(complete) {
                    "Backup incompleto: marcatore finale mancante"
                }
                require(extracted.keys == expected.keys) {
                    "Backup incompleto: mancano file originali o allegati"
                }

                return ExtractedBackup(
                    manifest = manifest,
                    files = extracted,
                )
            }
        }
    }

    private fun validateManifest(manifest: BackupManifest) {
        require(manifest.format == BACKUP_FORMAT) {
            "Formato backup non supportato"
        }
        require(manifest.formatVersion in MIN_BACKUP_FORMAT_VERSION..BACKUP_FORMAT_VERSION) {
            "Versione backup non supportata"
        }
        require(manifest.databaseSchemaVersion <= GARANZIA_SCHEMA_VERSION) {
            "Backup creato da una versione dell'app più recente"
        }
        require(manifest.receiptCount <= MAX_RECEIPTS) {
            "Troppi scontrini nel backup"
        }
        require(manifest.pageCount <= MAX_PAGES) {
            "Troppe pagine nel backup"
        }
        require(manifest.attachmentCount <= MAX_ATTACHMENTS) {
            "Troppi allegati nel backup"
        }

        val receiptIds = mutableSetOf<String>()
        val productIds = mutableSetOf<Long>()
        val pageIds = mutableSetOf<Long>()
        val attachmentIds = mutableSetOf<Long>()
        val entryNames = mutableSetOf<String>()
        var declaredBytes = 0L

        manifest.receipts.forEach { record ->
            require(record.receipt.id.isNotBlank() && receiptIds.add(record.receipt.id)) {
                "ID scontrino non valido o duplicato"
            }
            require(record.receipt.merchant.isNotBlank()) {
                "Negozio mancante nel backup"
            }
            require(record.receipt.purchaseDate.isNotBlank()) {
                "Data acquisto mancante nel backup"
            }
            require(record.receipt.totalAmount.isNotBlank()) {
                "Totale mancante nel backup"
            }

            require(record.products.isNotEmpty()) {
                "Uno scontrino nel backup non contiene prodotti"
            }
            require(record.pages.isNotEmpty()) {
                "Uno scontrino nel backup non contiene pagine originali"
            }

            val recordProductIds = mutableSetOf<Long>()
            val positions = mutableSetOf<Int>()
            record.products.forEach { product ->
                require(product.id > 0L && productIds.add(product.id)) {
                    "ID prodotto non valido o duplicato"
                }
                recordProductIds += product.id
                require(product.receiptId == record.receipt.id) {
                    "Prodotto associato allo scontrino sbagliato"
                }
                require(product.position >= 0 && positions.add(product.position)) {
                    "Posizione prodotto duplicata"
                }
                require(product.name.isNotBlank()) {
                    "Nome prodotto mancante"
                }
                require(product.warrantyReminderDays in 1..365) {
                    "Preavviso garanzia non valido"
                }
                require(
                    product.warrantyMonths == null ||
                        product.warrantyMonths in 1..120,
                ) {
                    "Durata garanzia non valida"
                }
            }

            val pageIndexes = mutableSetOf<Int>()
            record.pages.forEach { page ->
                require(page.id > 0L && pageIds.add(page.id)) {
                    "ID pagina non valido o duplicato"
                }
                require(page.receiptId == record.receipt.id) {
                    "Pagina associata allo scontrino sbagliato"
                }
                require(page.pageIndex >= 0 && pageIndexes.add(page.pageIndex)) {
                    "Indice pagina duplicato"
                }
                validateBinaryRecord(
                    entryName = page.entryName,
                    expectedPrefix = "originals/",
                    sizeBytes = page.sizeBytes,
                    sha256 = page.sha256,
                    maxBytes = MAX_PAGE_BYTES,
                    entryNames = entryNames,
                )
                declaredBytes += page.sizeBytes
                require(declaredBytes <= MAX_TOTAL_RESTORE_BYTES) {
                    "Il backup supera il limite di sicurezza"
                }
            }

            record.attachments.forEach { attachment ->
                require(attachment.id > 0L && attachmentIds.add(attachment.id)) {
                    "ID allegato non valido o duplicato"
                }
                require(attachment.receiptId == record.receipt.id) {
                    "Allegato associato allo scontrino sbagliato"
                }
                require(attachment.productId in recordProductIds) {
                    "Allegato associato a un prodotto non presente"
                }
                require(
                    ProductAttachmentCategory.entries.any {
                        it.storedValue == attachment.category
                    }
                ) {
                    "Categoria allegato non valida"
                }
                require(attachment.mimeType.startsWith("image/")) {
                    "Tipo allegato non supportato"
                }
                require(attachment.note == null || attachment.note.length <= 250) {
                    "Nota allegato troppo lunga"
                }
                validateBinaryRecord(
                    entryName = attachment.entryName,
                    expectedPrefix = "attachments/",
                    sizeBytes = attachment.sizeBytes,
                    sha256 = attachment.sha256,
                    maxBytes = MAX_ATTACHMENT_BYTES,
                    entryNames = entryNames,
                )
                declaredBytes += attachment.sizeBytes
                require(declaredBytes <= MAX_TOTAL_RESTORE_BYTES) {
                    "Il backup supera il limite di sicurezza"
                }
            }
        }
    }

    private fun validateBinaryRecord(
        entryName: String,
        expectedPrefix: String,
        sizeBytes: Long,
        sha256: String,
        maxBytes: Long,
        entryNames: MutableSet<String>,
    ) {
        require(isSafeEntryName(entryName) && entryNames.add(entryName)) {
            "Percorso file non valido o duplicato"
        }
        require(entryName.startsWith(expectedPrefix)) {
            "Percorso file backup non valido"
        }
        require(sizeBytes in 1..maxBytes) {
            "Dimensione file non valida"
        }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) {
            "Checksum file non valido"
        }
    }

    private fun digestSource(
        uri: Uri,
        maxBytes: Long,
    ): SourceDigest =
        openSource(uri).use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                size += read
                require(size <= maxBytes) {
                    "File troppo grande"
                }
                digest.update(buffer, 0, read)
            }

            require(size > 0L) {
                "File vuoto"
            }

            SourceDigest(
                sizeBytes = size,
                sha256 = digest.digest().toHex(),
            )
        }

    private fun openTarget(uri: Uri): OutputStream =
        when (uri.scheme) {
            null, "file" -> {
                val path = requireNotNull(uri.path) { "Percorso file non valido" }
                File(path).outputStream()
            }

            else -> requireNotNull(context.contentResolver.openOutputStream(uri, "w")) {
                "Impossibile scrivere il backup"
            }
        }

    private fun openSource(uri: Uri): InputStream =
        when (uri.scheme) {
            "file" -> {
                val path = requireNotNull(uri.path) { "Percorso file non valido" }
                FileInputStream(File(path))
            }

            else -> requireNotNull(context.contentResolver.openInputStream(uri)) {
                "Impossibile leggere il file selezionato"
            }
        }

    private fun writeTextEntry(
        zip: ZipOutputStream,
        name: String,
        text: String,
    ) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun readTextEntry(
        zip: ZipInputStream,
        maxBytes: Long,
    ): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L

        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            require(total <= maxBytes) {
                "Metadati backup troppo grandi"
            }
            output.write(buffer, 0, read)
        }

        return output.toString(Charsets.UTF_8.name())
    }

    private fun sanitizePathComponent(value: String): String =
        value
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
            .ifBlank { "receipt" }

    private fun isSafeEntryName(name: String): Boolean =
        name.isNotBlank() &&
            !name.startsWith("/") &&
            !name.startsWith("\\") &&
            !name.contains("\\") &&
            name.split('/').none { it == ".." || it.isBlank() }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

    private data class SourceDigest(
        val sizeBytes: Long,
        val sha256: String,
    )

    private data class ExpectedBackupFile(
        val sizeBytes: Long,
        val sha256: String,
        val maxBytes: Long,
    )

    private data class ExtractedBackup(
        val manifest: BackupManifest,
        val files: Map<String, File>,
    )

    private companion object {
        const val BACKUP_FORMAT = "garanzia-backup"
        const val MIN_BACKUP_FORMAT_VERSION = 1
        const val BACKUP_FORMAT_VERSION = 2
        const val MANIFEST_ENTRY = "manifest.json"
        const val COMPLETE_ENTRY = "backup_complete.txt"
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_RECEIPTS = 10_000
        const val MAX_PAGES = 50_000
        const val MAX_ATTACHMENTS = 100_000
        const val MAX_MANIFEST_BYTES = 12L * 1024L * 1024L
        const val MAX_MARKER_BYTES = 4L * 1024L
        const val MAX_PAGE_BYTES = 50L * 1024L * 1024L
        const val MAX_ATTACHMENT_BYTES = 30L * 1024L * 1024L
        const val MAX_TOTAL_RESTORE_BYTES = 2L * 1024L * 1024L * 1024L
    }
}
