// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");

package com.paddle.ocr.util

import android.content.Context
import android.util.Log
import org.opencv.android.OpenCVLoader

object OpenCVUtils {

    @Volatile
    private var initialized = false

    @Volatile
    private var lastError: String? = null

    @Synchronized
    fun init(context: Context): Boolean {
        if (initialized) return true

        initialized = try {
            OpenCVLoader.initDebug()
        } catch (t: Throwable) {
            lastError = t.message ?: t::class.java.simpleName
            false
        }

        if (!initialized) {
            initialized = try {
                System.loadLibrary("opencv_java4")
                true
            } catch (t: Throwable) {
                lastError = t.message ?: t::class.java.simpleName
                false
            }
        }

        if (!initialized) {
            Log.e(
                "OpenCVUtils",
                "OpenCV initialization failed: ${lastError.orEmpty()}",
            )
        } else {
            lastError = null
            Log.i("OpenCVUtils", "OpenCV initialized successfully")
        }

        return initialized
    }

    fun errorMessage(): String? = lastError
}
