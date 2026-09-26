package com.milanstevic.garanzia

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.milanstevic.garanzia.archive.ArchiveFilterState
import com.milanstevic.garanzia.archive.ReceiptArchiveDetailScreen
import com.milanstevic.garanzia.archive.ReceiptArchiveScreen
import com.milanstevic.garanzia.archive.ReceiptLifecycleManager
import com.milanstevic.garanzia.archive.ReceiptPdfManager
import com.milanstevic.garanzia.archive.ReceiptPdfViewerScreen
import com.milanstevic.garanzia.confirmation.ReceiptConfirmationDraft
import com.milanstevic.garanzia.confirmation.ReceiptConfirmationScreen
import com.milanstevic.garanzia.data.ReceiptArchiveState
import com.milanstevic.garanzia.data.ReceiptRepository
import com.milanstevic.garanzia.diagnostics.DiagnosticsManager
import com.milanstevic.garanzia.diagnostics.DiagnosticsScreen
import com.milanstevic.garanzia.diagnostics.DiagnosticsSnapshot
import com.milanstevic.garanzia.intelligence.ReceiptInterpretation
import com.milanstevic.garanzia.intelligence.ReceiptInterpreter
import com.milanstevic.garanzia.ocr.OcrReceiptResult
import com.milanstevic.garanzia.ocr.OcrResultScreen
import com.milanstevic.garanzia.ocr.ReceiptOcrEngine
import com.milanstevic.garanzia.scanner.DocumentScannerManager
import com.milanstevic.garanzia.scanner.FallbackCameraScreen
import com.milanstevic.garanzia.scanner.ReceiptFileStore
import com.milanstevic.garanzia.scanner.ReceiptReviewScreen
import com.milanstevic.garanzia.storage.BackgroundSyncScheduler
import com.milanstevic.garanzia.storage.ReceiptMirrorManager
import com.milanstevic.garanzia.storage.StorageSettings
import com.milanstevic.garanzia.storage.StorageSettingsScreen
import com.milanstevic.garanzia.storage.StorageTarget
import com.milanstevic.garanzia.ui.home.HomeScreen
import com.milanstevic.garanzia.ui.theme.GaranziaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var receiptFileStore: ReceiptFileStore

    @Inject
    lateinit var receiptOcrEngine: ReceiptOcrEngine

    @Inject
    lateinit var receiptInterpreter: ReceiptInterpreter

    @Inject
    lateinit var receiptRepository: ReceiptRepository

    @Inject
    lateinit var storageSettings: StorageSettings

    @Inject
    lateinit var receiptMirrorManager: ReceiptMirrorManager

    @Inject
    lateinit var receiptPdfManager: ReceiptPdfManager

    @Inject
    lateinit var receiptLifecycleManager: ReceiptLifecycleManager

    @Inject
    lateinit var diagnosticsManager: DiagnosticsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GaranziaTheme {
                GaranziaApp(
                    activity = this,
                    fileStore = receiptFileStore,
                    ocrEngine = receiptOcrEngine,
                    receiptInterpreter = receiptInterpreter,
                    receiptRepository = receiptRepository,
                    storageSettings = storageSettings,
                    receiptMirrorManager = receiptMirrorManager,
                    receiptPdfManager = receiptPdfManager,
                    receiptLifecycleManager = receiptLifecycleManager,
                    diagnosticsManager = diagnosticsManager,
                )
            }
        }
    }
}

private enum class AppScreen {
    HOME,
    CAMERA,
    REVIEW,
    OCR,
    CONFIRM,
    ARCHIVE,
    ARCHIVE_DETAIL,
    PDF_VIEWER,
    STORAGE,
    DIAGNOSTICS,
}

@Composable
private fun GaranziaApp(
    activity: Activity,
    fileStore: ReceiptFileStore,
    ocrEngine: ReceiptOcrEngine,
    receiptInterpreter: ReceiptInterpreter,
    receiptRepository: ReceiptRepository,
    storageSettings: StorageSettings,
    receiptMirrorManager: ReceiptMirrorManager,
    receiptPdfManager: ReceiptPdfManager,
    receiptLifecycleManager: ReceiptLifecycleManager,
    diagnosticsManager: DiagnosticsManager,
) {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var cameraOutput by remember { mutableStateOf<File?>(null) }
    var lastSavedPages by remember { mutableIntStateOf(0) }
    val stagedUris = remember { mutableStateListOf<Uri>() }
    var currentOriginalUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val archiveStateFlow = remember(receiptRepository) {
        receiptRepository.observeArchiveState()
    }
    val archiveState by archiveStateFlow.collectAsState(
        initial = ReceiptArchiveState(),
    )
    val archiveReceipts = archiveState.receipts
    val savedReceiptCount = archiveReceipts.size
    val databaseError = archiveState.error
    var selectedArchiveReceiptId by remember { mutableStateOf<String?>(null) }
    var archiveFilters by remember { mutableStateOf(ArchiveFilterState()) }
    var archiveSearchIds by remember { mutableStateOf<Set<String>?>(null) }
    val storageState by storageSettings.state.collectAsState()
    var storageSyncInProgress by remember { mutableStateOf(false) }
    var storageMessage by remember { mutableStateOf<String?>(null) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var pdfLoading by remember { mutableStateOf(false) }
    var pdfError by remember { mutableStateOf<String?>(null) }
    var diagnosticsSnapshot by remember { mutableStateOf<DiagnosticsSnapshot?>(null) }
    var diagnosticsLoading by remember { mutableStateOf(false) }
    var editingReceiptId by remember { mutableStateOf<String?>(null) }
    var isDeletingReceipt by remember { mutableStateOf(false) }
    var deleteReceiptError by remember { mutableStateOf<String?>(null) }

    var ocrStatus by remember { mutableStateOf("Preparazione OCR") }
    var ocrProgress by remember { mutableStateOf<Int?>(null) }
    var ocrResult by remember { mutableStateOf<OcrReceiptResult?>(null) }
    var interpretation by remember { mutableStateOf<ReceiptInterpretation?>(null) }
    var confirmationDraft by remember { mutableStateOf<ReceiptConfirmationDraft?>(null) }
    var lastConfirmedProducts by remember { mutableStateOf<Int?>(null) }
    var isSavingReceipt by remember { mutableStateOf(false) }
    var saveReceiptError by remember { mutableStateOf<String?>(null) }
    var ocrError by remember { mutableStateOf<String?>(null) }

    fun showReview(uris: List<Uri>) {
        stagedUris.clear()
        stagedUris.addAll(uris)
        screen = AppScreen.REVIEW
    }

    fun launchFallbackCamera() {
        cameraOutput = fileStore.newCameraCaptureFile()
        screen = AppScreen.CAMERA
    }

    fun startOcr(savedUris: List<Uri>) {
        ocrStatus = "Preparazione PP-OCRv6"
        ocrProgress = null
        ocrResult = null
        interpretation = null
        confirmationDraft = null
        editingReceiptId = null
        currentOriginalUris = savedUris
        saveReceiptError = null
        ocrError = null
        screen = AppScreen.OCR

        scope.launch {
            try {
                val result = ocrEngine.recognize(savedUris) { progress ->
                    withContext(Dispatchers.Main) {
                        ocrStatus =
                            "Modello ${progress.modelIndex}/${progress.modelCount}: ${progress.label}"
                        ocrProgress = progress.percent
                    }
                }
                ocrResult = result
                interpretation = runCatching {
                    receiptInterpreter.interpret(result)
                }.getOrNull()
                ocrStatus = "OCR e interpretazione completati"
                ocrProgress = 100
            } catch (t: Throwable) {
                ocrError = t.message ?: t::class.java.simpleName
                ocrStatus = "OCR non completato"
            }
        }
    }


    fun openReceiptPdf(receiptId: String) {
        selectedArchiveReceiptId = receiptId
        pdfFile = null
        pdfError = null
        pdfLoading = true
        screen = AppScreen.PDF_VIEWER

        val details = archiveReceipts.firstOrNull {
            it.receipt.id == receiptId
        }

        if (details == null) {
            pdfLoading = false
            pdfError = "Scontrino non trovato nell'archivio"
            return
        }

        scope.launch {
            try {
                pdfFile = receiptPdfManager.createOrReplacePdf(details)
            } catch (t: Throwable) {
                pdfError =
                    t.message ?: "Impossibile preparare il PDF dello scontrino"
            } finally {
                pdfLoading = false
            }
        }
    }


    fun openDiagnostics() {
        diagnosticsLoading = true
        screen = AppScreen.DIAGNOSTICS
        scope.launch {
            try {
                diagnosticsSnapshot = diagnosticsManager.runChecks()
            } finally {
                diagnosticsLoading = false
            }
        }
    }


    fun syncArchiveCopies() {
        if (storageSyncInProgress) return
        if (!storageState.phoneConfigured && !storageState.driveConfigured) {
            storageMessage = "Configura almeno una cartella esterna."
            return
        }

        storageSyncInProgress = true
        scope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    receiptMirrorManager.mirrorArchive(archiveReceipts)
                }
                storageMessage =
                    if (summary.failedCopies == 0) {
                        "Sincronizzazione completata: ${summary.successfulCopies} copie verificate."
                    } else {
                        "Sincronizzazione parziale: ${summary.successfulCopies} copie OK, ${summary.failedCopies} da riprovare."
                    }
            } finally {
                storageSyncInProgress = false
            }
        }
    }

    fun saveStorageFolder(
        target: StorageTarget,
        uri: Uri,
    ) {
        val flags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        val persisted = runCatching {
            activity.contentResolver.takePersistableUriPermission(uri, flags)
        }.isSuccess

        if (!persisted) {
            storageMessage = "Impossibile mantenere il permesso permanente per questa cartella."
            return
        }

        val label =
            runCatching {
                DocumentFile.fromTreeUri(activity, uri)?.name
            }.getOrNull()
                ?: uri.authority
                ?: "Cartella selezionata"

        storageSettings.saveTarget(
            target = target,
            uri = uri,
            label = label,
        )
        BackgroundSyncScheduler.enqueueNow(activity)
        storageMessage = "Cartella salvata. Avvio sincronizzazione automatica."
    }

    val phoneFolderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { saveStorageFolder(StorageTarget.PHONE, it) }
        }

    val driveFolderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { saveStorageFolder(StorageTarget.DRIVE, it) }
        }

    LaunchedEffect(
        archiveReceipts.size,
        storageState.phone.uri,
        storageState.drive.uri,
    ) {
        if (
            archiveReceipts.isNotEmpty() &&
            (storageState.phoneConfigured || storageState.driveConfigured) &&
            !storageSyncInProgress
        ) {
            storageSyncInProgress = true
            try {
                val summary = withContext(Dispatchers.IO) {
                    receiptMirrorManager.mirrorArchive(archiveReceipts)
                }
                storageMessage =
                    if (summary.failedCopies == 0) {
                        "Copie esterne aggiornate."
                    } else {
                        "${summary.failedCopies} copie esterne da riprovare."
                    }
            } finally {
                storageSyncInProgress = false
            }
        }
    }

    LaunchedEffect(
        archiveFilters.query,
        archiveReceipts.hashCode(),
    ) {
        val query = archiveFilters.query.trim()

        archiveSearchIds =
            if (query.isBlank()) {
                null
            } else {
                delay(180)
                runCatching {
                    withContext(Dispatchers.IO) {
                        receiptRepository.searchReceiptIds(query)
                    }
                }.getOrNull()
            }
    }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchFallbackCamera()
        }

    val documentScannerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val resultUris = DocumentScannerManager.imageUrisFromResult(result.data)
                scope.launch {
                    val staged = withContext(Dispatchers.IO) {
                        fileStore.stageContentUris(resultUris)
                    }
                    if (staged.isNotEmpty()) showReview(staged)
                }
            }
        }

    fun requestFallbackCamera() {
        if (
            ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchFallbackCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun startScan() {
        if (DocumentScannerManager.isScannerAvailable(activity)) {
            DocumentScannerManager
                .getStartScanIntent(activity)
                .addOnSuccessListener { sender ->
                    documentScannerLauncher.launch(
                        IntentSenderRequest.Builder(sender).build(),
                    )
                }
                .addOnFailureListener {
                    requestFallbackCamera()
                }
        } else {
            requestFallbackCamera()
        }
    }

    Crossfade(
        targetState = screen,
        animationSpec = tween(durationMillis = 220),
        label = "garanziaScreenTransition",
    ) { currentScreen ->
        when (currentScreen) {
        AppScreen.HOME -> HomeScreen(
            onScanReceipt = ::startScan,
            onOpenArchive = { screen = AppScreen.ARCHIVE },
            onOpenStorage = { screen = AppScreen.STORAGE },
            lastSavedPages = lastSavedPages,
            lastConfirmedProducts = lastConfirmedProducts,
            savedReceiptCount = savedReceiptCount,
            dualCopyConfigured = storageState.bothConfigured,
            storageMessage = storageMessage,
            databaseError = databaseError,
        )

        AppScreen.CAMERA -> {
            val output = cameraOutput
            if (output != null) {
                FallbackCameraScreen(
                    outputFile = output,
                    onCaptured = { uri -> showReview(listOf(uri)) },
                    onCancel = {
                        output.delete()
                        screen = AppScreen.HOME
                    },
                )
            }
        }

        AppScreen.REVIEW -> ReceiptReviewScreen(
            pages = stagedUris,
            onAccept = {
                val toFinalize = stagedUris.toList()
                scope.launch {
                    val finalized = withContext(Dispatchers.IO) {
                        fileStore.finalizeStaged(toFinalize)
                    }
                    lastSavedPages = finalized.size
                    stagedUris.clear()

                    if (finalized.isNotEmpty()) {
                        startOcr(finalized)
                    } else {
                        ocrError = "Impossibile conservare lo scontrino"
                        screen = AppScreen.OCR
                    }
                }
            },
            onDiscard = {
                val toDiscard = stagedUris.toList()
                scope.launch(Dispatchers.IO) {
                    fileStore.discardStaged(toDiscard)
                }
                stagedUris.clear()
                screen = AppScreen.HOME
            },
        )

        AppScreen.OCR -> OcrResultScreen(
            status = ocrStatus,
            progressPercent = ocrProgress,
            result = ocrResult,
            interpretation = interpretation,
            error = ocrError,
            onReview = {
                val interpreted = interpretation
                if (interpreted != null) {
                    confirmationDraft = ReceiptConfirmationDraft.from(interpreted)
                    screen = AppScreen.CONFIRM
                }
            },
            onManualSave = {
                confirmationDraft = ReceiptConfirmationDraft.manualFallback()
                saveReceiptError = null
                screen = AppScreen.CONFIRM
            },
            onDone = {
                ocrResult = null
                interpretation = null
                confirmationDraft = null
                currentOriginalUris = emptyList()
                ocrError = null
                ocrProgress = null
                screen = AppScreen.HOME
            },
        )

        AppScreen.CONFIRM -> {
            val draft = confirmationDraft
            if (draft != null) {
                ReceiptConfirmationScreen(
                    draft = draft,
                    isSaving = isSavingReceipt,
                    saveError = saveReceiptError,
                    editing = editingReceiptId != null,
                    onDraftChange = {
                        confirmationDraft = it
                        saveReceiptError = null
                    },
                    onConfirm = {
                        if (!isSavingReceipt) {
                            isSavingReceipt = true
                            saveReceiptError = null
                            scope.launch {
                                try {
                                    val editId = editingReceiptId

                                    if (editId != null) {
                                        val result = receiptLifecycleManager.updateReceipt(
                                            receiptId = editId,
                                            draft = draft,
                                        )

                                        BackgroundSyncScheduler.enqueueNow(activity)
                                        storageMessage =
                                            result.warning
                                                ?: "Scontrino aggiornato. Copie esterne in aggiornamento."

                                        editingReceiptId = null
                                        confirmationDraft = null
                                        selectedArchiveReceiptId = editId
                                        screen = AppScreen.ARCHIVE_DETAIL
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            receiptRepository.saveConfirmedReceipt(
                                                draft = draft,
                                                originalUris = currentOriginalUris,
                                                rawOcrText = ocrResult?.rawText,
                                            )
                                        }

                                        BackgroundSyncScheduler.enqueueNow(activity)

                                        storageMessage =
                                            if (
                                                storageState.phoneConfigured ||
                                                storageState.driveConfigured
                                            ) {
                                                "Scontrino salvato. Sincronizzazione copie esterne in corso."
                                            } else {
                                                "Scontrino salvato nel database interno. Configura Archiviazione per le copie esterne."
                                            }

                                        lastConfirmedProducts = draft.products.size
                                        ocrResult = null
                                        interpretation = null
                                        confirmationDraft = null
                                        currentOriginalUris = emptyList()
                                        ocrError = null
                                        ocrProgress = null
                                        screen = AppScreen.HOME
                                    }
                                } catch (t: Throwable) {
                                    saveReceiptError =
                                        t.message ?: "Impossibile salvare lo scontrino"
                                } finally {
                                    isSavingReceipt = false
                                }
                            }
                        }
                    },
                    onBack = {
                        saveReceiptError = null
                        if (editingReceiptId != null) {
                            editingReceiptId = null
                            confirmationDraft = null
                            screen = AppScreen.ARCHIVE_DETAIL
                        } else {
                            screen = AppScreen.OCR
                        }
                    },
                )
            }
        }

        AppScreen.ARCHIVE -> ReceiptArchiveScreen(
            receipts = archiveReceipts,
            filters = archiveFilters,
            matchingIds = archiveSearchIds,
            onFiltersChange = { archiveFilters = it },
            onOpenReceipt = { receiptId ->
                selectedArchiveReceiptId = receiptId
                deleteReceiptError = null
                screen = AppScreen.ARCHIVE_DETAIL
            },
            onOpenHome = {
                selectedArchiveReceiptId = null
                screen = AppScreen.HOME
            },
            onOpenStorage = {
                screen = AppScreen.STORAGE
            },
            databaseError = databaseError,
        )

        AppScreen.ARCHIVE_DETAIL -> {
            val details = archiveReceipts.firstOrNull {
                it.receipt.id == selectedArchiveReceiptId
            }

            if (details != null) {
                ReceiptArchiveDetailScreen(
                    details = details,
                    onOpenPdf = {
                        openReceiptPdf(details.receipt.id)
                    },
                    onEdit = {
                        editingReceiptId = details.receipt.id
                        confirmationDraft = ReceiptConfirmationDraft.fromStored(details)
                        saveReceiptError = null
                        screen = AppScreen.CONFIRM
                    },
                    onDelete = {
                        if (!isDeletingReceipt) {
                            isDeletingReceipt = true
                            deleteReceiptError = null

                            scope.launch {
                                try {
                                    val result = receiptLifecycleManager.deleteReceipt(
                                        details.receipt.id,
                                    )

                                    BackgroundSyncScheduler.enqueueNow(activity)
                                    storageMessage =
                                        result.warning
                                            ?: "Scontrino eliminato e copie ripulite."
                                    selectedArchiveReceiptId = null
                                    screen = AppScreen.ARCHIVE
                                } catch (t: Throwable) {
                                    deleteReceiptError =
                                        t.message ?: "Impossibile eliminare lo scontrino"
                                } finally {
                                    isDeletingReceipt = false
                                }
                            }
                        }
                    },
                    isDeleting = isDeletingReceipt,
                    deleteError = deleteReceiptError,
                    onBack = {
                        deleteReceiptError = null
                        selectedArchiveReceiptId = null
                        screen = AppScreen.ARCHIVE
                    },
                )
            } else {
                ReceiptArchiveScreen(
                    receipts = archiveReceipts,
                    filters = archiveFilters,
                    matchingIds = archiveSearchIds,
                    onFiltersChange = { archiveFilters = it },
                    onOpenReceipt = { receiptId ->
                        selectedArchiveReceiptId = receiptId
                    },
                    onOpenHome = {
                        selectedArchiveReceiptId = null
                        screen = AppScreen.HOME
                    },
                    onOpenStorage = {
                        screen = AppScreen.STORAGE
                    },
                    databaseError = databaseError,
                )
            }
        }

        AppScreen.PDF_VIEWER -> ReceiptPdfViewerScreen(
            pdfFile = pdfFile,
            loading = pdfLoading,
            error = pdfError,
            onRetry = {
                selectedArchiveReceiptId?.let(::openReceiptPdf)
            },
            onBack = {
                pdfError = null
                pdfLoading = false
                screen =
                    if (selectedArchiveReceiptId != null) {
                        AppScreen.ARCHIVE_DETAIL
                    } else {
                        AppScreen.ARCHIVE
                    }
            },
        )

        AppScreen.STORAGE -> StorageSettingsScreen(
            state = storageState,
            syncInProgress = storageSyncInProgress,
            syncMessage = storageMessage,
            onChoosePhone = { phoneFolderLauncher.launch(null) },
            onChooseDrive = { driveFolderLauncher.launch(null) },
            onClearPhone = {
                storageSettings.clearTarget(StorageTarget.PHONE)
                storageMessage = "Cartella telefono rimossa."
            },
            onClearDrive = {
                storageSettings.clearTarget(StorageTarget.DRIVE)
                storageMessage = "Cartella Google Drive rimossa."
            },
            onSyncNow = ::syncArchiveCopies,
            onOpenDiagnostics = ::openDiagnostics,
            onOpenHome = { screen = AppScreen.HOME },
            onOpenArchive = { screen = AppScreen.ARCHIVE },
        )

        AppScreen.DIAGNOSTICS -> DiagnosticsScreen(
            snapshot = diagnosticsSnapshot,
            loading = diagnosticsLoading,
            onRunChecks = ::openDiagnostics,
            onBack = { screen = AppScreen.STORAGE },
        )
        }
    }
}
