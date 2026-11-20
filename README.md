# wfsar — Arabic PDF Extractor

Standalone Android library module for page-by-page Arabic OCR from PDF files.

## Requirements
- Android Studio with Android Gradle Plugin 8.7.3 support
- JDK 17
- Android SDK platform 35
- Minimum Android API 23

## Import
Open the extracted `wfsar` directory as a project in Android Studio and sync Gradle. The directory is a standalone Android library project (the project root itself is the library module).

## Usage

```kotlin
import com.wfsar.pdfextractor.WfsarFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// From a coroutine:
WfsarFactory.useWfsarExtractor { extractor ->
    extractor.extract(File("/path/to/input.pdf")).collect { page ->
        println("Page ${page.pageIndex + 1}: ${page.text}")
    }
}
```

The `extract()` Flow is cold and processes pages sequentially. Each page bitmap is recycled in a `finally` block before the next page is rendered. Access to `PdfRenderer` is serialized by a `Mutex`. The extractor and recognizer are `Closeable`; the scoped factory helper closes them automatically.

## API
- `WfsarFactory.create(config)` creates a closeable extractor.
- `WfsarFactory.useWfsarExtractor { extractor -> ... }` closes it after the block completes.
- `WfsarPdfExtractor.extract(file)` emits `PdfPageText` records in page order.
- `WfsarPdfExtractorConfig` configures render scale and background color.

## Notes
- Uses the dedicated Arabic ML Kit dependency `com.google.mlkit:text-recognition-arabic:16.0.1` and initializes `TextRecognition.getClient(ArabicTextRecognizerOptions.Builder().build())`.
- PDF image OCR is used. If a PDF contains an embedded text layer, direct text extraction may be more accurate.
- A 2x bitmap can be large for very high-resolution pages. Dimensions are checked before allocation, but device memory still constrains the maximum practical page size.
