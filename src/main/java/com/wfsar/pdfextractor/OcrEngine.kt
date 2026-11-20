package com.wfsar.pdfextractor

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.arabic.ArabicTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** Closeable wrapper around the dedicated Arabic ML Kit text recognizer. */
class OcrEngine : Closeable {
    private val closed = AtomicBoolean(false)

    private val recognizer = TextRecognition.getClient(
        ArabicTextRecognizerOptions.Builder().build()
    )

    /** Recognizes Arabic text from [bitmap]. The caller retains ownership of the bitmap. */
    suspend fun recognize(bitmap: Bitmap): Text {
        check(!closed.get()) { "OcrEngine is closed" }
        return recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            recognizer.close()
        }
    }
}
