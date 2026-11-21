package com.wfsar.pdfextractor

import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class OcrEngine(
    tessDataParentPath: String,
    language: String = "ara"
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val tessBaseApi = TessBaseAPI().apply {
        val ok = init(tessDataParentPath, language)
        require(ok) { "Tesseract init failed at: $tessDataParentPath" }
    }

    suspend fun recognize(
        bitmap: Bitmap,
        singleLine: Boolean = false
    ): String = withContext(Dispatchers.Default) {
        check(!closed.get()) { "OcrEngine is closed" }
        tessBaseApi.pageSegMode = if (singleLine) {
            TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
        } else {
            TessBaseAPI.PageSegMode.PSM_AUTO
        }
        tessBaseApi.setImage(bitmap)
        tessBaseApi.utF8Text ?: ""
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            tessBaseApi.recycle()
        }
    }
}
