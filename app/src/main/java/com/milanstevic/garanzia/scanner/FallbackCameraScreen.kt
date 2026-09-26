package com.milanstevic.garanzia.scanner

import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File

@Composable
fun FallbackCameraScreen(
    outputFile: File,
    onCaptured: (Uri) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)

        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            }.onFailure {
                errorMessage = "Fotocamera non disponibile"
            }
        }, executor)

        onDispose {
            if (providerFuture.isDone) {
                runCatching {
                    providerFuture.get().unbindAll()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.Black.copy(alpha = 0.62f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Inquadra lo scontrino",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Tieni visibili tutti i bordi e cerca di evitare riflessi.",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp, vertical = 120.dp)
                .fillMaxSize()
                .sizeIn(maxWidth = 520.dp, maxHeight = 720.dp)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(24.dp),
                ),
        )

        errorMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.72f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                ) {
                    Text("Annulla")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        outputFile.parentFile?.mkdirs()
                        val options =
                            ImageCapture.OutputFileOptions
                                .Builder(outputFile)
                                .build()

                        imageCapture.takePicture(
                            options,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(
                                    outputFileResults: ImageCapture.OutputFileResults,
                                ) {
                                    onCaptured(Uri.fromFile(outputFile))
                                }

                                override fun onError(
                                    exception: ImageCaptureException,
                                ) {
                                    errorMessage =
                                        "Impossibile salvare la foto"
                                }
                            },
                        )
                    },
                ) {
                    Text("Scatta")
                }
            }
        }
    }
}
