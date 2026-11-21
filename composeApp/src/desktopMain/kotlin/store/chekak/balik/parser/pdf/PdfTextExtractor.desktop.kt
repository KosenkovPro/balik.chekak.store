package store.chekak.balik.parser.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

/**
 * JVM-реализация на Apache PDFBox. Используется только для тестов
 * (в приложении PDF разбирается через PDFBox-Android и PDFKit).
 */
actual object PdfTextExtractor {

    actual fun extractText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        return runCatching {
            Loader.loadPDF(bytes).use { document ->
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                stripper.getText(document)
            }
        }.getOrElse { "" }
    }
}
