package com.milanstevic.garanzia

import android.app.Application
import android.util.Log
import com.paddle.ocr.util.OpenCVUtils
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GaranziaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val loaded = OpenCVUtils.init(this)
        Log.i("Garanzia", "OpenCV initialized=$loaded")
    }
}
