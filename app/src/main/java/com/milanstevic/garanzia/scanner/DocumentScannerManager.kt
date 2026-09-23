/*
 * Adapted and modified for Garanzia from Scanly:
 * https://github.com/Azyrn/Scanly
 * Scanly is licensed under Apache License 2.0.
 */
package com.milanstevic.garanzia.scanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

object DocumentScannerManager {
    private const val PAGE_LIMIT = 20

    fun isScannerAvailable(context: Context): Boolean =
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    fun getStartScanIntent(activity: Activity): Task<IntentSender> {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(PAGE_LIMIT)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        return GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
    }

    fun imageUrisFromResult(data: Intent?): List<Uri> =
        GmsDocumentScanningResult
            .fromActivityResultIntent(data)
            ?.pages
            ?.map { it.imageUri }
            .orEmpty()
}
