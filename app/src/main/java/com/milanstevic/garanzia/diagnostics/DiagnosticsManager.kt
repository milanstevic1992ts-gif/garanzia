package com.milanstevic.garanzia.diagnostics

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import com.milanstevic.garanzia.data.ReceiptRepository
import com.milanstevic.garanzia.ocr.PaddleModelRepository
import com.milanstevic.garanzia.storage.PendingMirrorDeletionStore
import com.milanstevic.garanzia.storage.StorageSettings
import com.milanstevic.garanzia.storage.StorageTargetState
import com.milanstevic.garanzia.storage.SyncStatusStore
import com.paddle.ocr.util.OpenCVUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DiagnosticLevel {
    OK,
    WARNING,
    ERROR,
}

data class DiagnosticItem(
    val title: String,
    val detail: String,
    val level: DiagnosticLevel,
)

data class DiagnosticsSnapshot(
    val items: List<DiagnosticItem>,
    val generatedAtEpochMs: Long = System.currentTimeMillis(),
) {
    val errors: Int get() = items.count { it.level == DiagnosticLevel.ERROR }
    val warnings: Int get() = items.count { it.level == DiagnosticLevel.WARNING }
    val healthy: Boolean get() = errors == 0
}

@Singleton
class DiagnosticsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: PaddleModelRepository,
    private val repository: ReceiptRepository,
    private val storageSettings: StorageSettings,
    private val syncStatusStore: SyncStatusStore,
    private val pendingDeletions: PendingMirrorDeletionStore,
) {
    suspend fun runChecks(): DiagnosticsSnapshot = withContext(Dispatchers.IO) {
        val items = mutableListOf<DiagnosticItem>()

        val openCvOk = runCatching {
            OpenCVUtils.init(context)
        }.getOrDefault(false)
        items += DiagnosticItem(
            title = "OpenCV",
            detail =
                if (openCvOk) {
                    "Libreria nativa caricata correttamente"
                } else {
                    OpenCVUtils.errorMessage()
                        ?.let { "Errore: $it" }
                        ?: "Libreria nativa non disponibile"
                },
            level =
                if (openCvOk) {
                    DiagnosticLevel.OK
                } else {
                    DiagnosticLevel.ERROR
                },
        )

        val modelsInstalled = runCatching { models.areInstalled() }.getOrDefault(false)
        items += DiagnosticItem(
            title = "Modelli OCR",
            detail =
                if (modelsInstalled) {
                    "PP-OCRv6 installato e hash verificati"
                } else {
                    "Modelli mancanti o da riscaricare al prossimo OCR"
                },
            level =
                if (modelsInstalled) {
                    DiagnosticLevel.OK
                } else {
                    DiagnosticLevel.WARNING
                },
        )

        val receiptsResult = runCatching { repository.getAllReceipts() }
        val receipts = receiptsResult.getOrNull().orEmpty()
        items += DiagnosticItem(
            title = "Database Room",
            detail =
                receiptsResult.fold(
                    onSuccess = { "${it.size} scontrino/i leggibili" },
                    onFailure = { it.message ?: "Errore lettura database" },
                ),
            level =
                if (receiptsResult.isSuccess) {
                    DiagnosticLevel.OK
                } else {
                    DiagnosticLevel.ERROR
                },
        )

        if (receiptsResult.isSuccess) {
            val missingOriginals = receipts
                .flatMap { it.pages }
                .count { page ->
                    !sourceExists(Uri.parse(page.originalUri))
                }

            items += DiagnosticItem(
                title = "Originali scontrini",
                detail =
                    if (missingOriginals == 0) {
                        "Tutte le pagine originali risultano disponibili"
                    } else {
                        "$missingOriginals pagina/e originale/i non trovate"
                    },
                level =
                    if (missingOriginals == 0) {
                        DiagnosticLevel.OK
                    } else {
                        DiagnosticLevel.ERROR
                    },
            )
        }

        val storage = storageSettings.state.value
        items += targetCheck(
            title = "Cartella telefono",
            target = storage.phone,
        )
        items += targetCheck(
            title = "Google Drive",
            target = storage.drive,
        )

        val freeBytes = StatFs(context.filesDir.absolutePath).availableBytes
        items += DiagnosticItem(
            title = "Spazio libero",
            detail = formatBytes(freeBytes),
            level = when {
                freeBytes >= ONE_GB -> DiagnosticLevel.OK
                freeBytes >= MIN_FREE_BYTES -> DiagnosticLevel.WARNING
                else -> DiagnosticLevel.ERROR
            },
        )

        val pendingDeleteCount = pendingDeletions.all().size
        items += DiagnosticItem(
            title = "Pulizia copie esterne",
            detail =
                if (pendingDeleteCount == 0) {
                    "Nessuna cancellazione esterna in attesa"
                } else {
                    "$pendingDeleteCount cancellazione/i da riprovare"
                },
            level =
                if (pendingDeleteCount == 0) {
                    DiagnosticLevel.OK
                } else {
                    DiagnosticLevel.WARNING
                },
        )

        val sync = syncStatusStore.state.value
        items += DiagnosticItem(
            title = "Sincronizzazione background",
            detail = when {
                sync.running ->
                    "Sincronizzazione in corso"
                sync.lastAttemptEpochMs == null ->
                    "Non ancora eseguita"
                else ->
                    buildString {
                        append(sync.lastMessage ?: "Ultimo tentativo registrato")
                        append(" · ")
                        append(formatDate(sync.lastAttemptEpochMs))
                        if (sync.failedCopies > 0) {
                            append(" · ")
                            append(sync.failedCopies)
                            append(" copie da riprovare")
                        }
                    }
            },
            level = when {
                sync.running -> DiagnosticLevel.WARNING
                sync.failedCopies > 0 -> DiagnosticLevel.WARNING
                sync.lastAttemptEpochMs == null -> DiagnosticLevel.WARNING
                else -> DiagnosticLevel.OK
            },
        )

        DiagnosticsSnapshot(items)
    }

    private fun targetCheck(
        title: String,
        target: StorageTargetState,
    ): DiagnosticItem {
        val uri = target.uri
            ?: return DiagnosticItem(
                title = title,
                detail = "Non configurata",
                level = DiagnosticLevel.WARNING,
            )

        val permissionPersisted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }

        val document = runCatching {
            DocumentFile.fromTreeUri(context, uri)
        }.getOrNull()

        val ready =
            permissionPersisted &&
                document != null &&
                document.isDirectory &&
                document.canRead() &&
                document.canWrite()

        return DiagnosticItem(
            title = title,
            detail =
                if (ready) {
                    target.label ?: "Cartella accessibile"
                } else {
                    "Permesso o accesso alla cartella non valido"
                },
            level =
                if (ready) {
                    DiagnosticLevel.OK
                } else {
                    DiagnosticLevel.ERROR
                },
        )
    }

    private fun sourceExists(uri: Uri): Boolean =
        when (uri.scheme) {
            null, "file" ->
                uri.path
                    ?.let(::File)
                    ?.let { it.isFile && it.length() > 0L }
                    ?: false

            else ->
                runCatching {
                    context.contentResolver.openInputStream(uri).use { input ->
                        input != null && input.read() >= 0
                    }
                }.getOrDefault(false)
        }

    private fun formatDate(epochMs: Long): String =
        DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
        ).format(Date(epochMs))

    private fun formatBytes(bytes: Long): String {
        val gb = bytes.toDouble() / ONE_GB.toDouble()
        return String.format("%.1f GB disponibili", gb)
    }

    private companion object {
        const val ONE_GB = 1024L * 1024L * 1024L
        const val MIN_FREE_BYTES = 250L * 1024L * 1024L
    }
}
