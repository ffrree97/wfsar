package com.wfsar.pdfextractor

import android.graphics.Color

/** OCR result for one PDF page. Page indices are zero-based. */
data class PdfPageText(
    val pageIndex: Int,
    val text: String
)

/** Rendering configuration for [WfsarPdfExtractor]. */
data class WfsarPdfExtractorConfig(
    val renderScale: Float = 2.0f,
    val backgroundColor: Int = Color.WHITE
) {
    init {
        require(renderScale > 0f && renderScale.isFinite()) {
            "renderScale must be finite and greater than zero"
        }
    }
}
