package com.markflow.app

import android.app.Application
import com.markflow.app.ml.DigitRecognizer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * MarkFlow Application class.
 * Initializes Hilt DI and the TensorFlow Lite model at startup.
 */
@HiltAndroidApp
class MarkFlowApp : Application() {

    @Inject
    lateinit var digitRecognizer: DigitRecognizer

    override fun onCreate() {
        super.onCreate()

        // Initialize TF Lite digit recognizer in background
        Thread {
            try {
                digitRecognizer.initialize()
            } catch (e: Exception) {
                // App can function without TF Lite (falls back to OCR only)
                e.printStackTrace()
            }
        }.start()
    }

    override fun onTerminate() {
        super.onTerminate()
        digitRecognizer.close()
    }
}
