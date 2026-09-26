package com.milanstevic.garanzia.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.milanstevic.garanzia.archive.ReceiptPdfManager
import com.milanstevic.garanzia.data.local.ReceiptWithDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

data class MirrorTargetResult(
    val target: StorageTarget,
    val configured: Boolean,
    val success: Boolean,
    val message: String,
)

data class ReceiptMirrorResult(
    val phone: MirrorTargetResult,
    val drive: MirrorTargetResult,
) {
    val configuredFailures: List<MirrorTargetResult>
        get() = listOf(phone, drive).filter { it.configured && !it.success }

    val configuredSuccesses: Int
        get() = listOf(phone, drive).count { it.configured && it.success }
}

data class ArchiveMirrorSummary(
    val receiptCount: Int,
    val successfulCopies: Int,
    val failedCopies: Int,
    val pendingDeletionFailures: Int = 0,
)

@Singleton
class ReceiptMirrorManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: StorageSettings,
    private val pendingDeletions: PendingMirrorDeletionStore,
    private val pdfManager: ReceiptPdfManager,
) {
    @Synchronized
    fun mirrorReceipt(details: ReceiptWithDetails): ReceiptMirrorResult {
        val state = settings.state.value

        return ReceiptMirrorResult(
            phone = mirrorTarget(
                target = StorageTarget.PHONE,
                treeUri = state.phone.uri,
                details = details,
            ),
            drive = mirrorTarget(
                target = StorageTarget.DRIVE,
                treeUri = state.drive.uri,
                details = details,
            ),
        )
    }

    @Synchronized
    fun mirrorArchive(receipts: List<ReceiptWithDetails>): ArchiveMirrorSummary {
        val pendingDeletionFailures = processPendingDeletions()
        var successes = 0
        var failures = 0

        receipts.forEach { details ->
            val result = mirrorReceipt(details)
            listOf(result.phone, result.drive)
                .filter { it.configured }
                .forEach { targetResult ->
                    if (targetResult.success) {
                        successes++
                    } else {
                        failures++
                    }
                }
        }

        return ArchiveMirrorSummary(
            receiptCount = receipts.size,
            successfulCopies = successes,
            failedCopies = failures,
            pendingDeletionFailures = pendingDeletionFailures,
        )
    }

    @Synchronized
    fun deleteReceiptCopies(details: ReceiptWithDetails): ReceiptMirrorResult {
        val state = settings.state.value
        val directoryName = receiptDirectoryName(details)

        return ReceiptMirrorResult(
            phone = deleteTargetDirectory(
                target = StorageTarget.PHONE,
                treeUri = state.phone.uri,
                directoryName = directoryName,
            ),
            drive = deleteTargetDirectory(
                target = StorageTarget.DRIVE,
                treeUri = state.drive.uri,
                directoryName = directoryName,
            ),
        )
    }

    private fun processPendingDeletions(): Int {
        val state = settings.state.value
        var failures = 0

        pendingDeletions.all().forEach { pending ->
            val treeUri =
                when (pending.target) {
                    StorageTarget.PHONE -> state.phone.uri
                    StorageTarget.DRIVE -> state.drive.uri
                }

            if (treeUri == null) {
                failures++
                return@forEach
            }

            val result = deleteTargetDirectory(
                target = pending.target,
                treeUri = treeUri,
                directoryName = pending.directoryName,
            )

            if (!result.success) {
                failures++
            }
        }

        return failures
    }

    private fun deleteTargetDirectory(
        target: StorageTarget,
        treeUri: Uri?,
        directoryName: String,
    ): MirrorTargetResult {
        if (treeUri == null) {
            return MirrorTargetResult(
                target = target,
                configured = false,
                success = true,
                message = "Nessuna cartella configurata",
            )
        }

        return runCatching {
            val root = requireNotNull(
                DocumentFile.fromTreeUri(context, treeUri),
            ) { "Cartella non disponibile" }

            require(root.canWrite()) {
                "La cartella selezionata non è scrivibile"
            }

            val directory = root.findFile(directoryName)
            if (directory != null) {
                require(directory.delete()) {
                    "Impossibile eliminare la vecchia copia esterna"
                }
            }

            pendingDeletions.remove(target, directoryName)

            MirrorTargetResult(
                target = target,
                configured = true,
                success = true,
                message = "Copia esterna eliminata",
            )
        }.getOrElse { error ->
            pendingDeletions.add(target, directoryName)

            MirrorTargetResult(
                target = target,
                configured = true,
                success = false,
                message = error.message ?: "Eliminazione esterna da riprovare",
            )
        }
    }

    private fun mirrorTarget(
        target: StorageTarget,
        treeUri: Uri?,
        details: ReceiptWithDetails,
    ): MirrorTargetResult {
        if (treeUri == null) {
            return MirrorTargetResult(
                target = target,
                configured = false,
                success = false,
                message = "Cartella non configurata",
            )
        }

        return runCatching {
            val root = requireNotNull(
                DocumentFile.fromTreeUri(context, treeUri),
            ) { "Cartella non disponibile" }

            require(root.canWrite()) { "La cartella selezionata non è scrivibile" }

            val directoryName = receiptDirectoryName(details)
            val receiptDirectory =
                root.findFile(directoryName)
                    ?.takeIf { it.isDirectory }
                    ?: requireNotNull(root.createDirectory(directoryName)) {
                        "Impossibile creare la cartella dello scontrino"
                    }

            val expectedPages = details.pages
                .sortedBy { it.pageIndex }
                .map { page ->
                    page to "pagina_${(page.pageIndex + 1).toString().padStart(2, '0')}.jpg"
                }

            val complete =
                receiptDirectory.findFile(COMPLETE_FILE)?.isFile == true &&
                    receiptDirectory.findFile(SUMMARY_FILE)?.isFile == true &&
                    receiptDirectory.findFile(PDF_FILE)?.isFile == true &&
                    expectedPages.all { (_, fileName) ->
                        receiptDirectory.findFile(fileName)?.isFile == true
                    } &&
                    (
                        details.receipt.rawOcrText.isNullOrBlank() ||
                            receiptDirectory.findFile(OCR_FILE)?.isFile == true
                        )

            if (!complete) {
                cleanManagedFiles(receiptDirectory)

                expectedPages.forEach { (page, fileName) ->
                    copyUriIntoDirectory(
                        sourceUri = Uri.parse(page.originalUri),
                        directory = receiptDirectory,
                        displayName = fileName,
                        mimeType = "image/jpeg",
                    )
                }

                writeTextFile(
                    directory = receiptDirectory,
                    displayName = SUMMARY_FILE,
                    text = buildSummary(details),
                )

                details.receipt.rawOcrText
                    ?.takeIf { it.isNotBlank() }
                    ?.let { rawOcr ->
                        writeTextFile(
                            directory = receiptDirectory,
                            displayName = OCR_FILE,
                            text = rawOcr,
                        )
                    }

                val pdfFile = pdfManager.createOrReplacePdfBlocking(details)
                copyFileIntoDirectory(
                    sourceFile = pdfFile,
                    directory = receiptDirectory,
                    displayName = PDF_FILE,
                    mimeType = "application/pdf",
                )

                writeTextFile(
                    directory = receiptDirectory,
                    displayName = COMPLETE_FILE,
                    text =
                        "receiptId=${details.receipt.id}\n" +
                            "pages=${expectedPages.size}\n" +
                            "completed=true\n",
                )
            }

            MirrorTargetResult(
                target = target,
                configured = true,
                success = true,
                message = "Sincronizzato",
            )
        }.getOrElse { error ->
            MirrorTargetResult(
                target = target,
                configured = true,
                success = false,
                message = error.message ?: "Copia non riuscita",
            )
        }
    }

    private fun copyUriIntoDirectory(
        sourceUri: Uri,
        directory: DocumentFile,
        displayName: String,
        mimeType: String,
    ) {
        val target = requireNotNull(directory.createFile(mimeType, displayName)) {
            "Impossibile creare $displayName"
        }

        try {
            val copiedBytes = openSource(sourceUri).use { input ->
                context.contentResolver.openOutputStream(target.uri, "w").use { output ->
                    requireNotNull(output) { "Impossibile scrivere $displayName" }
                    val copied = input.copyTo(output)
                    output.flush()
                    copied
                }
            }

            require(copiedBytes > 0L) {
                "$displayName non contiene dati"
            }
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    private fun copyFileIntoDirectory(
        sourceFile: File,
        directory: DocumentFile,
        displayName: String,
        mimeType: String,
    ) {
        require(sourceFile.isFile && sourceFile.length() > 0L) {
            "PDF locale non disponibile"
        }

        val target = requireNotNull(directory.createFile(mimeType, displayName)) {
            "Impossibile creare $displayName"
        }

        try {
            val copiedBytes = sourceFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(target.uri, "w").use { output ->
                    requireNotNull(output) { "Impossibile scrivere $displayName" }
                    val copied = input.copyTo(output)
                    output.flush()
                    copied
                }
            }

            require(copiedBytes > 0L) {
                "$displayName non contiene dati"
            }
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    private fun openSource(uri: Uri): InputStream =
        when (uri.scheme) {
            "file" -> {
                val path = requireNotNull(uri.path) { "Percorso originale non valido" }
                FileInputStream(File(path))
            }

            else -> requireNotNull(context.contentResolver.openInputStream(uri)) {
                "Impossibile leggere lo scontrino originale"
            }
        }

    private fun writeTextFile(
        directory: DocumentFile,
        displayName: String,
        text: String,
    ) {
        val file = requireNotNull(directory.createFile("text/plain", displayName)) {
            "Impossibile creare $displayName"
        }

        try {
            context.contentResolver.openOutputStream(file.uri, "w").use { output ->
                requireNotNull(output) { "Impossibile scrivere $displayName" }
                output.writer(Charsets.UTF_8).use { writer ->
                    writer.write(text)
                }
            }

        } catch (t: Throwable) {
            file.delete()
            throw t
        }
    }

    private fun cleanManagedFiles(directory: DocumentFile) {
        directory.listFiles().forEach { file ->
            val name = file.name.orEmpty()
            if (
                name.startsWith(PAGE_PREFIX) ||
                name == SUMMARY_FILE ||
                name == OCR_FILE ||
                name == PDF_FILE ||
                name == COMPLETE_FILE
            ) {
                file.delete()
            }
        }
    }

    private fun receiptDirectoryName(details: ReceiptWithDetails): String {
        val date = details.receipt.purchaseDate.ifBlank { "data_sconosciuta" }
        val merchant =
            sanitizeName(details.receipt.merchant)
                .take(48)
                .ifBlank { "negozio" }

        return "${date}_${merchant}_${details.receipt.id.take(8)}"
    }

    private fun sanitizeName(value: String): String =
        value
            .trim()
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), "_")
            .trim('_')

    private fun buildSummary(details: ReceiptWithDetails): String =
        buildString {
            appendLine("Negozio: ${details.receipt.merchant}")
            appendLine("Data: ${details.receipt.purchaseDate}")
            details.receipt.purchaseTime?.let {
                appendLine("Ora: $it")
            }
            appendLine(
                "Totale: ${details.receipt.totalAmount} ${details.receipt.currency.orEmpty()}",
            )
            details.receipt.documentNumber?.let {
                appendLine("Documento: $it")
            }
            details.receipt.vatNumber?.let {
                appendLine("P.IVA: $it")
            }
            details.receipt.paymentMethod?.let {
                appendLine("Pagamento: $it")
            }
            appendLine()
            appendLine("Prodotti:")

            details.products
                .sortedBy { it.position }
                .forEachIndexed { index, product ->
                    append("${index + 1}. ${product.name}")
                    product.quantity?.let {
                        append(" · q.tà $it")
                    }
                    product.lineTotal?.let {
                        append(" · $it")
                    }
                    appendLine()
                }
        }

    private companion object {
        const val PAGE_PREFIX = "pagina_"
        const val SUMMARY_FILE = "dati_scontrino.txt"
        const val OCR_FILE = "ocr_originale.txt"
        const val PDF_FILE = "scontrino.pdf"
        const val COMPLETE_FILE = "sincronizzazione_completa.txt"
    }
}
