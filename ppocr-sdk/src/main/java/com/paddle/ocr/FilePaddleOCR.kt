// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// Garanzia adaptation:
// - keeps PaddleOCR detection/recognition pipeline unchanged
// - loads verified ONNX models from app-private files instead of APK assets

package com.paddle.ocr

import android.content.Context
import android.graphics.Bitmap
import com.paddle.ocr.engine.DetectionEngine
import com.paddle.ocr.engine.ORTSessionManager
import com.paddle.ocr.engine.RecognitionEngine
import com.paddle.ocr.model.OCRError
import com.paddle.ocr.model.OCRResult
import com.paddle.ocr.model.OCRRunResult
import com.paddle.ocr.postprocess.BoxSorter
import com.paddle.ocr.postprocess.QuadTextCrop
import com.paddle.ocr.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FilePaddleOCR private constructor(
    private val manager: ORTSessionManager,
    private val config: PaddleOCRConfig,
    private val detectionEngine: DetectionEngine,
    private val recognitionEngine: RecognitionEngine,
) {
    companion object {
        suspend fun create(
            context: Context,
            detModelFile: File,
            recModelFile: File,
            characterList: List<String>,
            config: PaddleOCRConfig = PaddleOCRConfig(),
            engineConfig: EngineConfig = EngineConfig(),
        ): FilePaddleOCR = withContext(Dispatchers.IO) {
            val manager = ORTSessionManager(context.applicationContext, engineConfig)
            try {
                manager.loadModelFiles(detModelFile, recModelFile)
                FilePaddleOCR(
                    manager = manager,
                    config = config,
                    detectionEngine = DetectionEngine(manager, config),
                    recognitionEngine = RecognitionEngine(manager, characterList),
                )
            } catch (t: Throwable) {
                manager.release()
                throw t
            }
        }
    }

    suspend fun recognize(bitmap: Bitmap): OCRRunResult {
        if (bitmap.width == 0 || bitmap.height == 0) {
            throw OCRError.InvalidImage()
        }
        return withContext(Dispatchers.IO) { run(bitmap) }
    }

    private fun run(bitmap: Bitmap): OCRRunResult {
        val srcMat = BitmapUtils.bitmapToBGRMat(bitmap)
        val totalStart = System.currentTimeMillis()

        try {
            val detResult = detectionEngine.detect(srcMat)
            val boxes = BoxSorter.sortInReadingOrder(detResult.boxes)

            if (boxes.isEmpty()) {
                val total = System.currentTimeMillis() - totalStart
                return OCRRunResult(
                    results = emptyList(),
                    detectionTimeMs = detResult.timeMs,
                    recognitionTimeMs = 0,
                    totalTimeMs = total,
                    lineCount = 0,
                    detPreprocessMs = detResult.preprocessMs,
                    detInferenceMs = detResult.inferenceMs,
                    detPostprocessMs = detResult.postprocessMs,
                    coldLoadTimeMs = manager.coldLoadTimeMs,
                    detInputShape = detResult.inputShape,
                )
            }

            var totalRecPreMs = 0L
            var totalRecInfMs = 0L
            var totalRecPostMs = 0L
            var totalRecMs = 0L
            val results = mutableListOf<OCRResult>()
            val recInputShapes = mutableListOf<List<Int>>()
            val perLineRecMs = mutableListOf<Long>()
            val batchSize = config.recBatchSize.coerceAtLeast(1)

            var i = 0
            while (i < boxes.size) {
                val crops = mutableListOf<org.opencv.core.Mat>()
                val boxIndices = mutableListOf<Int>()
                var next = i

                while (next < boxes.size && crops.size < batchSize) {
                    val crop = QuadTextCrop.crop(srcMat, boxes[next])
                    if (crop.rows() > 0 && crop.cols() > 0) {
                        crops.add(crop)
                        boxIndices.add(next)
                    } else {
                        crop.release()
                    }
                    next++
                }

                try {
                    if (crops.isNotEmpty()) {
                        val rec = recognitionEngine.recognize(crops)
                        totalRecPreMs += rec.preprocessMs
                        totalRecInfMs += rec.inferenceMs
                        totalRecPostMs += rec.postprocessMs
                        totalRecMs += rec.timeMs
                        recInputShapes.add(rec.inputShape)
                        if (batchSize == 1) perLineRecMs.add(rec.timeMs)

                        for (j in rec.texts.indices) {
                            val (text, confidence) = rec.texts[j]
                            if (confidence >= config.recScoreThresh) {
                                results.add(
                                    OCRResult(
                                        box = boxes[boxIndices[j]],
                                        text = text,
                                        confidence = confidence,
                                    )
                                )
                            }
                        }
                    }
                } finally {
                    crops.forEach { it.release() }
                }

                i = next
            }

            val total = System.currentTimeMillis() - totalStart
            return OCRRunResult(
                results = results,
                detectionTimeMs = detResult.timeMs,
                recognitionTimeMs = totalRecMs,
                totalTimeMs = total,
                lineCount = results.size,
                detPreprocessMs = detResult.preprocessMs,
                detInferenceMs = detResult.inferenceMs,
                detPostprocessMs = detResult.postprocessMs,
                recPreprocessMs = totalRecPreMs,
                recInferenceMs = totalRecInfMs,
                recPostprocessMs = totalRecPostMs,
                pipelineOverheadMs = total - detResult.timeMs - totalRecMs,
                coldLoadTimeMs = manager.coldLoadTimeMs,
                detInputShape = detResult.inputShape,
                recInputShapes = recInputShapes,
                perLineRecMs = perLineRecMs,
            )
        } finally {
            srcMat.release()
        }
    }

    suspend fun release() {
        withContext(Dispatchers.IO) { manager.release() }
    }
}
