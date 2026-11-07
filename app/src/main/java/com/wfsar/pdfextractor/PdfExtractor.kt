package com.wfsar.pdfextractor

import com.tomroush.pdfbox.pdmodel.PDDocument
import com.tomroush.pdfbox.text.PDFTextStripper
import java.io.File

class PdfExtractor {

    private val diacriticsRegex = Regex("[\u064B-\u065F\u0640]")
    private val extraSpacesRegex = Regex("\\s+")
    private val numberOrEnglishRegex = Regex("[0-9A-Za-z]+")

    private val abdRegex = Regex("عبد\\s+")
    private val alDeenRegex = Regex("\\s+الدين")
    private val allahRegex = Regex("\\s+الله")

    fun processOcrText(ocrText: String): String {
        if (ocrText.isBlank()) return ""

        val processedLines = ocrText.lines().map { line ->
            var cleanedLine = line.replace(diacriticsRegex, "")
            cleanedLine = normalizeArabicText(cleanedLine)
            cleanedLine = cleanedLine.replace(extraSpacesRegex, " ").trim()

            cleanedLine = cleanedLine.replace(abdRegex, "عبد")
            cleanedLine = cleanedLine.replace(alDeenRegex, "الدين")
            cleanedLine = cleanedLine.replace(allahRegex, "الله")
            cleanedLine = cleanedLine.replace("زين العابدين", "زين_العابدين")

            cleanedLine
        }

        return processedLines.joinToString("\n")
    }

    fun extractTextFromPdfBox(pdfFile: File): String {
        if (!pdfFile.exists()) return ""

        return try {
            val document = PDDocument.load(pdfFile)
            val stripper = PDFTextStripper()
            val rawText = stripper.getText(document)
            document.close()
            
            processPdfBoxArabicText(rawText)
        } catch (e: Exception) {
            e.message ?: "Error reading file"
        }
    }

    private fun processPdfBoxArabicText(rawText: String): String {
        if (rawText.isBlank()) return ""

        val processedLines = rawText.lines().map { line ->
            var cleanedLine = line.replace(diacriticsRegex, "")
            var correctedLine = cleanedLine.reversed()
            
            correctedLine = numberOrEnglishRegex.replace(correctedLine) { matchResult ->
                matchResult.value.reversed()
            }
            
            correctedLine = normalizeArabicText(correctedLine)
            correctedLine = correctedLine.replace(extraSpacesRegex, " ").trim()
            
            correctedLine = correctedLine.replace(abdRegex, "عبد")
            correctedLine = correctedLine.replace(alDeenRegex, "الدين")
            correctedLine = correctedLine.replace(allahRegex, "الله")
            
            correctedLine
        }
        return processedLines.joinToString("\n")
    }

    private fun normalizeArabicText(text: String): String {
        return text
            .replace(Regex("[أإآ]"), "ا")
            .replace("ى", "ي")
    }
}
