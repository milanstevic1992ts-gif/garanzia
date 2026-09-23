package com.milanstevic.garanzia

import android.Manifest
import android.app.Activity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.milanstevic.garanzia.ocr.OcrReceiptResult
import com.milanstevic.garanzia.ocr.OcrResultScreen
import com.milanstevic.garanzia.ocr.ReceiptOcrEngine
import com.milanstevic.garanzia.scanner.DocumentScannerManager
import com.milanstevic.garanzia.scanner.FallbackCameraScreen
import com.milanstevic.garanzia.scanner.ReceiptFileStore
import com.milanstevic.garanzia.scanner.ReceiptReviewScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GaranziaTheme {
                GaranziaApp(
                    activity = this,
                    fileStore = receiptFileStore,
                    ocrEngine = receiptOcrEngine,
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
}

@Composable
private fun GaranziaApp(
    activity: Activity,
    fileStore: ReceiptFileStore,
    ocrEngine: ReceiptOcrEngine,
) {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var cameraOutput by remember { mutableStateOf<File?>(null) }
    var lastSavedPages by remember { mutableIntStateOf(0) }
    val stagedUris = remember { mutableStateListOf<Uri>() }

    var ocrStatus by remember { mutableStateOf("Preparazione OCR") }
    var ocrProgress by remember { mutableStateOf<Int?>(null) }
    var ocrResult by remember { mutableStateOf<OcrReceiptResult?>(null) }
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
                ocrStatus = "OCR completato"
                ocrProgress = 100
            } catch (t: Throwable) {
                ocrError = t.message ?: t::class.java.simpleName
                ocrStatus = "OCR non completato"
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
            lastSavedPages = lastSavedPages,
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
            error = ocrError,
            onDone = {
                ocrResult = null
                ocrError = null
                ocrProgress = null
                screen = AppScreen.HOME
            },
        )
    }
}
