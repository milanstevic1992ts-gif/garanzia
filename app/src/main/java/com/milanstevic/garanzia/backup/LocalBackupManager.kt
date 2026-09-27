package com.milanstevic.garanzia.backup

import android.content.Context
import android.net.Uri
import com.milanstevic.garanzia.archive.ReceiptPdfManager
import com.milanstevic.garanzia.data.ReceiptRepository
import com.milanstevic.garanzia.data.local.GARANZIA_SCHEMA_VERSION
import com.milanstevic.garanzia.data.local.ReceiptPageEntity
import com.milanstevic.garanzia.data.local.ReceiptWithDetails
import com.milanstevic.garanzia.scanner.ReceiptFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
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
                context.contentResolver.openOutputStream(targetUri, "w").use { output ->
                    requireNotNull(output) { "Impossibile scrivere il backup" }
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
                )
            }
        }
    }

    suspend fun restoreBackup(sourceUri: Uri): RestoreSummary = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply {
            require(mkdirs()) { "Impossibile preparare il ripristino" }
        }

        val importedUris = mutableListOf<Uri>()

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
                        importedUris += newUri

                        ReceiptPageEntity(
                            id = page.id,
                            receiptId = record.receipt.id,
                            pageIndex = page.pageIndex,
                            originalUri = newUri.toString(),
                        )
                    }

                ReceiptWithDetails(
                    receipt = record.receipt,
                    products = record.products.sortedBy { it.position },
                    pages = restoredPages,
                )
            }

            try {
                repository.replaceArchive(restored)
            } catch (t: Throwable) {
                fileStore.deleteOriginals(importedUris)
                throw t
            }

            fileStore.deleteOriginals(
                before.flatMap { details ->
                    details.pages.map { Uri.parse(it.originalUri) }
                },
            )

            (before.map { it.receipt.id } + restored.map { it.receipt.id })
                .distinct()
                .forEach { receiptId ->
                    pdfManager.deleteCachedPdf(receiptId)
                }

            RestoreSummary(
                receiptCount = restored.size,
                productCount = restored.sumOf { it.products.size },
                pageCount = restored.sumOf { it.pages.size },
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
                    val digest = digestSource(Uri.parse(page.originalUri))

                    require(digest.sizeBytes in 1..MAX_PAGE_BYTES) {
                        "Una pagina originale supera il limite consentito"
                    }

                    BackupPageRecord(
                        id = page.id,
                        receiptId = details.receipt.id,
                        pageIndex = page.pageIndex,
                        entryName = entryName,
                        sizeBytes = digest.sizeBytes,
                        sha256 = digest.sha256,
                    )
                }

            BackupReceiptRecord(
                receipt = details.receipt,
                products = details.products.sortedBy { it.position },
                pages = pages,
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

                    zip.putNextEntry(ZipEntry(page.entryName))
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
                            require(written <= MAX_PAGE_BYTES) {
                                "Pagina originale troppo grande"
                            }
                        }
                    }
                    zip.closeEntry()

                    require(written == page.sizeBytes) {
                        "Un originale è cambiato durante il backup"
                    }
                    require(digest.digest().toHex() == page.sha256) {
                        "Un originale è cambiato durante il backup"
                    }
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

                val expected = manifest.receipts
                    .flatMap { it.pages }
                    .associateBy { it.entryName }
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

                    val page = requireNotNull(expected[entry.name]) {
                        "Il backup contiene file inattesi"
                    }
                    require(!entry.isDirectory) {
                        "Pagina backup non valida"
                    }

                    val target = File(staging, "page_${extracted.size}.jpg")
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
                            require(written <= page.sizeBytes && written <= MAX_PAGE_BYTES) {
                                "Dimensione pagina backup non valida"
                            }
                            require(totalBytes <= MAX_TOTAL_RESTORE_BYTES) {
                                "Il backup supera il limite di sicurezza"
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }

                    require(written == page.sizeBytes && written > 0L) {
                        "Pagina backup incompleta"
                    }
                    require(digest.digest().toHex() == page.sha256) {
                        "Checksum pagina non valido"
                    }

                    extracted[entry.name] = target
                }

                require(complete) {
                    "Backup incompleto: marcatore finale mancante"
                }
                require(extracted.keys == expected.keys) {
                    "Backup incompleto: mancano pagine originali"
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
        require(manifest.formatVersion == BACKUP_FORMAT_VERSION) {
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

        val receiptIds = mutableSetOf<String>()
        val productIds = mutableSetOf<Long>()
        val pageIds = mutableSetOf<Long>()
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

            val positions = mutableSetOf<Int>()
            record.products.forEach { product ->
                require(product.id > 0L && productIds.add(product.id)) {
                    "ID prodotto non valido o duplicato"
                }
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
                require(isSafeEntryName(page.entryName) && entryNames.add(page.entryName)) {
                    "Percorso pagina non valido o duplicato"
                }
                require(page.entryName.startsWith("originals/")) {
                    "Percorso originale non valido"
                }
                require(page.sizeBytes in 1..MAX_PAGE_BYTES) {
                    "Dimensione pagina non valida"
                }
                require(page.sha256.matches(Regex("[0-9a-f]{64}"))) {
                    "Checksum pagina non valido"
                }
                declaredBytes += page.sizeBytes
                require(declaredBytes <= MAX_TOTAL_RESTORE_BYTES) {
                    "Il backup supera il limite di sicurezza"
                }
            }
        }
    }

    private fun digestSource(uri: Uri): SourceDigest =
        openSource(uri).use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                size += read
                require(size <= MAX_PAGE_BYTES) {
                    "Pagina originale troppo grande"
                }
                digest.update(buffer, 0, read)
            }

            SourceDigest(
                sizeBytes = size,
                sha256 = digest.digest().toHex(),
            )
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

    private data class ExtractedBackup(
        val manifest: BackupManifest,
        val files: Map<String, File>,
    )

    private companion object {
        const val BACKUP_FORMAT = "garanzia-backup"
        const val BACKUP_FORMAT_VERSION = 1
        const val MANIFEST_ENTRY = "manifest.json"
        const val COMPLETE_ENTRY = "backup_complete.txt"
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_RECEIPTS = 10_000
        const val MAX_PAGES = 50_000
        const val MAX_MANIFEST_BYTES = 8L * 1024L * 1024L
        const val MAX_MARKER_BYTES = 4L * 1024L
        const val MAX_PAGE_BYTES = 50L * 1024L * 1024L
        const val MAX_TOTAL_RESTORE_BYTES = 2L * 1024L * 1024L * 1024L
    }
}
