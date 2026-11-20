package com.wfsar.pdfextractor

object WfsarFactory {
    fun create(
        tessDataParentPath: String,
        config: WfsarPdfExtractorConfig = WfsarPdfExtractorConfig()
    ): WfsarPdfExtractor = WfsarPdfExtractor(
        ocrEngine = OcrEngine(tessDataParentPath),
        config = config
    )

    inline fun <T> useWfsarExtractor(
        tessDataParentPath: String,
        config: WfsarPdfExtractorConfig = WfsarPdfExtractorConfig(),
        block: (WfsarPdfExtractor) -> T
    ): T {
        val extractor = create(tessDataParentPath, config)
        return extractor.use(block)
    }
}
