package com.wfsar.pdfextractor

/** Factory and scoped resource helper for the WFSAR PDF extractor. */
object WfsarFactory {
    fun create(
        config: WfsarPdfExtractorConfig = WfsarPdfExtractorConfig()
    ): WfsarPdfExtractor = WfsarPdfExtractor(
        ocrEngine = OcrEngine(),
        config = config
    )

    /**
     * Creates an extractor and always closes it after [block] returns or throws.
     * Use from a coroutine when collecting [WfsarPdfExtractor.extract].
     */
    inline fun <T> useWfsarExtractor(
        config: WfsarPdfExtractorConfig = WfsarPdfExtractorConfig(),
        block: (WfsarPdfExtractor) -> T
    ): T {
        val extractor = create(config)
        return extractor.use(block)
    }
}
