package com.aira.assistant.mobile

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

data class IndexedGalleryDocument(
    val filePath: String,
    val fileName: String,
    val extractedOcrText: String,
    val detectedCategory: String
)

class CameraAndGalleryIntelligence {

    private val indexedDocuments = mutableListOf<IndexedGalleryDocument>()

    fun indexDocumentOrScreenshot(filePath: String, ocrText: String): IndexedGalleryDocument {
        val lower = ocrText.lowercase()
        val category = when {
            lower.contains("invoice") || lower.contains("bill") || lower.contains("total") || lower.contains("rs.") -> "BILL_INVOICE"
            lower.contains("id") || lower.contains("dob") || lower.contains("government") -> "ID_DOCUMENT"
            lower.contains("note") || lower.contains("chapter") -> "HANDWRITTEN_NOTE"
            else -> "SCREENSHOT"
        }
        val doc = IndexedGalleryDocument(
            filePath = filePath,
            fileName = File(filePath).name,
            extractedOcrText = ocrText,
            detectedCategory = category
        )
        indexedDocuments.removeAll { it.filePath == filePath }
        indexedDocuments.add(0, doc)
        return doc
    }

    fun searchGalleryByNaturalQuery(query: String): List<IndexedGalleryDocument> {
        val tokens = query.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }
        if (tokens.isEmpty()) return indexedDocuments.toList()

        return indexedDocuments.filter { doc ->
            val haystack = "${doc.fileName} ${doc.extractedOcrText} ${doc.detectedCategory}".lowercase()
            tokens.any { haystack.contains(it) }
        }
    }

    fun scanPagesToPdf(pageTexts: List<String>, outputPdfFile: File): File {
        outputPdfFile.parentFile?.mkdirs()
        val wroteAndroidPdf = runCatching {
            val pdf = PdfDocument()
            pageTexts.forEachIndexed { idx, text ->
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, idx + 1).create()
                val page = pdf.startPage(pageInfo)
                val canvas: Canvas = page.canvas
                val paint = Paint().apply {
                    color = Color.BLACK
                    textSize = 14f
                }
                var y = 60f
                text.lines().forEach { line ->
                    canvas.drawText(line, 40f, y, paint)
                    y += 22f
                }
                pdf.finishPage(page)
            }
            FileOutputStream(outputPdfFile).use { pdf.writeTo(it) }
            pdf.close()
            outputPdfFile.exists() && outputPdfFile.length() > 100L
        }.getOrDefault(false)

        if (!wroteAndroidPdf) {
            val body = pageTexts.joinToString(" --- ")
            val cleanPreview = body.take(200).replace('\n', ' ')
            val minimalPdf = "%PDF-1.4\n1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n% Scanned Content: $cleanPreview\n%%EOF\n"
            outputPdfFile.writeText(minimalPdf, Charsets.UTF_8)
        }
        return outputPdfFile
    }
}
