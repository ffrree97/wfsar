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

class WfsarPdfExtractor(
    private val ocrEngine: OcrEngine,
    private val config: WfsarPdfExtractorConfig = WfsarPdfExtractorConfig()
) : Closeable {
    private val rendererMutex = Mutex()
    private val closed = AtomicBoolean(false)

    fun extract(pdfFile: File): Flow<PdfPageText> = flow {
        check(!closed.get()) { "WfsarPdfExtractor is closed" }
        require(pdfFile.isFile && pdfFile.canRead()) {
            "PDF file not readable: ${pdfFile.absolutePath}"
        }

        rendererMutex.withLock {
            check(!closed.get()) { "WfsarPdfExtractor is closed" }
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    for (pageIndex in 0 until renderer.pageCount) {
                        currentCoroutineContext().ensureActive()
                        val result = renderer.openPage(pageIndex).use { page ->
                            val width = (page.width * config.renderScale).toDouble()
                            val height = (page.height * config.renderScale).toDouble()
                            if (!width.isFinite() || !height.isFinite() || width < 1.0 || height < 1.0) {
                                throw IOException("Invalid page size: ${width}x$height")
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
                                if (config.sliceRows) {
                                    extractByRows(pageIndex, bitmap)
                                } else {
                                    val text = ocrEngine.recognize(bitmap, singleLine = false)
                                    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                    PdfPageText(pageIndex = pageIndex, text = text, rows = lines)
                                }
                            } finally {
                                if (!bitmap.isRecycled) bitmap.recycle()
                            }
                        }
                        emit(result)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun extractByRows(pageIndex: Int, pageBitmap: Bitmap): PdfPageText {
        val rowRects = detectTextRowBands(pageBitmap)
        if (rowRects.isEmpty()) {
            val fallbackText = ocrEngine.recognize(pageBitmap, singleLine = false)
            val lines = fallbackText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            return PdfPageText(pageIndex = pageIndex, text = fallbackText, rows = lines)
        }

        val extractedRows = ArrayList<String>(rowRects.size)
        for (band in rowRects) {
            currentCoroutineContext().ensureActive()
            val stripHeight = (band.second - band.first).coerceAtLeast(1)
            val rowBitmap = Bitmap.createBitmap(pageBitmap, 0, band.first, pageBitmap.width, stripHeight)
            try {
                val rawRow = ocrEngine.recognize(rowBitmap, singleLine = true)
                    .replace('\n', ' ')
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (rawRow.isNotEmpty()) {
                    extractedRows.add(rawRow)
                }
            } finally {
                if (!rowBitmap.isRecycled) rowBitmap.recycle()
            }
        }

        return PdfPageText(
            pageIndex = pageIndex,
            text = extractedRows.joinToString("\n"),
            rows = extractedRows
        )
    }

    private fun detectTextRowBands(bitmap: Bitmap): List<Pair<Int, Int>> {
        val w = bitmap.width
        val h = bitmap.height
        val rowPixels = IntArray(w)
        val hasText = BooleanArray(h)

        // تجاهل الخطوط العمودية الرفيعة للجدول (أقل من 18 بكسل) والخطوط الأفقية الطويلة (أكثر من 55% من العرض)
        val minDarkPixels = (w * 0.008).toInt().coerceAtLeast(15)
        val maxDarkPixels = (w * 0.55).toInt()

        for (y in 0 until h) {
            bitmap.getPixels(rowPixels, 0, w, 0, y, w, 1)
            var darkCount = 0
            for (x in 0 until w) {
                val c = rowPixels[x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r < 200 || g < 200 || b < 200) {
                    darkCount++
                }
            }
            hasText[y] = darkCount in minDarkPixels..maxDarkPixels
        }

        val bands = mutableListOf<Pair<Int, Int>>()
        val padding = (6 * config.renderScale).toInt().coerceAtLeast(6)
        val minRowHeight = (10 * config.renderScale).toInt().coerceAtLeast(10)
        val maxGap = (3 * config.renderScale).toInt().coerceAtLeast(3)

        var startY = -1
        var gapCount = 0

        for (y in 0 until h) {
            if (hasText[y]) {
                if (startY == -1) startY = y
                gapCount = 0
            } else if (startY != -1) {
                gapCount++
                if (gapCount > maxGap) {
                    val endY = y - gapCount
                    if (endY - startY >= minRowHeight) {
                        val top = (startY - padding).coerceAtLeast(0)
                        val bottom = (endY + padding).coerceAtMost(h)
                        bands.add(top to bottom)
                    }
                    startY = -1
                    gapCount = 0
                }
            }
        }

        if (startY != -1 && (h - startY) >= minRowHeight) {
            val top = (startY - padding).coerceAtLeast(0)
            bands.add(top to h)
        }

        return bands
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            ocrEngine.close()
        }
    }
}
