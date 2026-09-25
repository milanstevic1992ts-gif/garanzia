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
import com.milanstevic.garanzia.archive.ReceiptPdfManager
import com.milanstevic.garanzia.archive.ReceiptPdfViewerScreen
import com.milanstevic.garanzia.confirmation.ReceiptConfirmationDraft
import com.milanstevic.garanzia.confirmation.ReceiptConfirmationScreen
import com.milanstevic.garanzia.data.ReceiptRepository
import com.milanstevic.garanzia.intelligence.ReceiptInterpretation
import com.milanstevic.garanzia.intelligence.ReceiptInterpreter
import com.milanstevic.garanzia.ocr.OcrReceiptResult
import com.milanstevic.garanzia.ocr.OcrResultScreen
import com.milanstevic.garanzia.ocr.ReceiptOcrEngine
import com.milanstevic.garanzia.scanner.DocumentScannerManager
import com.milanstevic.garanzia.scanner.FallbackCameraScreen
import com.milanstevic.garanzia.scanner.ReceiptFileStore
import com.milanstevic.garanzia.scanner.ReceiptReviewScreen
import com.milanstevic.garanzia.storage.ReceiptMirrorManager
import com.milanstevic.garanzia.storage.StorageSettings
import com.milanstevic.garanzia.storage.StorageSettingsScreen
import com.milanstevic.garanzia.storage.StorageTarget
import com.milanstevic.garanzia.ui.home.HomeScreen
import com.milanstevic.garanzia.ui.theme.GaranziaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
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
) {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var cameraOutput by remember { mutableStateOf<File?>(null) }
    var lastSavedPages by remember { mutableIntStateOf(0) }
    val stagedUris = remember { mutableStateListOf<Uri>() }
    var currentOriginalUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val receiptCountFlow = remember(receiptRepository) { receiptRepository.observeReceiptCount() }
    val savedReceiptCount by receiptCountFlow.collectAsState(initial = 0)
    val archiveReceiptsFlow = remember(receiptRepository) { receiptRepository.observeReceipts() }
    val archiveReceipts by archiveReceiptsFlow.collectAsState(initial = emptyList())
    var selectedArchiveReceiptId by remember { mutableStateOf<String?>(null) }
    var archiveFilters by remember { mutableStateOf(ArchiveFilterState()) }
    val storageState by storageSettings.state.collectAsState()
    var storageSyncInProgress by remember { mutableStateOf(false) }
    var storageMessage by remember { mutableStateOf<String?>(null) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var pdfLoading by remember { mutableStateOf(false) }
    var pdfError by remember { mutableStateOf<String?>(null) }

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

    when (screen) {
        AppScreen.HOME -> HomeScreen(
            onScanReceipt = ::startScan,
            onOpenArchive = { screen = AppScreen.ARCHIVE },
            onOpenStorage = { screen = AppScreen.STORAGE },
            lastSavedPages = lastSavedPages,
            lastConfirmedProducts = lastConfirmedProducts,
            savedReceiptCount = savedReceiptCount,
            dualCopyConfigured = storageState.bothConfigured,
            storageMessage = storageMessage,
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
                                    withContext(Dispatchers.IO) {
                                        receiptRepository.saveConfirmedReceipt(
                                            draft = draft,
                                            originalUris = currentOriginalUris,
                                            rawOcrText = ocrResult?.rawText,
                                        )
                                    }

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
                        screen = AppScreen.OCR
                    },
                )
            }
        }

        AppScreen.ARCHIVE -> ReceiptArchiveScreen(
            receipts = archiveReceipts,
            filters = archiveFilters,
            onFiltersChange = { archiveFilters = it },
            onOpenReceipt = { receiptId ->
                selectedArchiveReceiptId = receiptId
                screen = AppScreen.ARCHIVE_DETAIL
            },
            onBack = {
                selectedArchiveReceiptId = null
                screen = AppScreen.HOME
            },
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
                    onBack = {
                        selectedArchiveReceiptId = null
                        screen = AppScreen.ARCHIVE
                    },
                )
            } else {
                ReceiptArchiveScreen(
                    receipts = archiveReceipts,
                    filters = archiveFilters,
                    onFiltersChange = { archiveFilters = it },
                    onOpenReceipt = { receiptId ->
                        selectedArchiveReceiptId = receiptId
                    },
                    onBack = {
                        selectedArchiveReceiptId = null
                        screen = AppScreen.HOME
                    },
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
            onBack = { screen = AppScreen.HOME },
        )
    }
}
