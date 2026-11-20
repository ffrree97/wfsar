package com.wfsar.pdfextractor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lazily renders and recognizes PDF pages in document order.
 *
 * PdfRenderer access is guarded by a Mutex. One bitmap is allocated per page and always recycled
 * in a finally block before processing the next page.
 */
class WfsarPdfExtractor(
    private val ocrEngine: OcrEngine = OcrEngine(),
    private val config: WfsarPdfExtractorConfig = WfsarPdfExtractorConfig()
) : Closeable {
    private val rendererMutex = Mutex()
    private val closed = AtomicBoolean(false)

    /**
     * Returns a cold Flow: the file is opened and pages are processed when collection starts.
     * The PDF is opened independently for each collection.
     */
    fun extract(pdfFile: File): Flow<PdfPageText> = flow {
        check(!closed.get()) { "WfsarPdfExtractor is closed" }
        require(pdfFile.isFile && pdfFile.canRead()) {
            "PDF file does not exist or is not readable: ${pdfFile.absolutePath}"
        }

        rendererMutex.withLock {
            check(!closed.get()) { "WfsarPdfExtractor is closed" }
            ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            ).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    for (pageIndex in 0 until renderer.pageCount) {
                        currentCoroutineContext().ensureActive()

                        val result = renderer.openPage(pageIndex).use { page ->
                            val width = (page.width * config.renderScale)
                                .toDouble()
                            val height = (page.height * config.renderScale)
                                .toDouble()

                            if (!width.isFinite() || !height.isFinite() ||
                                width < 1.0 || height < 1.0 ||
                                width > Int.MAX_VALUE || height > Int.MAX_VALUE ||
                                width * height > Int.MAX_VALUE / 4.0
                            ) {
                                throw IOException(
                                    "Rendered page dimensions are too large: ${width}x$height"
                                )
                            }

                            val bitmap = Bitmap.createBitmap(
                                width.toInt(),
                                height.toInt(),
                                Bitmap.Config.ARGB_8888
                            )
                            try {
                                bitmap.eraseColor(config.backgroundColor)
                                Canvas(bitmap).drawColor(config.backgroundColor)
                                page.render(
                                    bitmap,
                                    Rect(0, 0, bitmap.width, bitmap.height),
                                    null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                )

                                val recognized = ocrEngine.recognize(bitmap)
                                PdfPageText(
                                    pageIndex = pageIndex,
                                    text = recognized.text
                                )
                            } finally {
                                if (!bitmap.isRecycled) {
                                    bitmap.recycle()
                                }
                            }
                        }

                        emit(result)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            ocrEngine.close()
        }
    }
}
