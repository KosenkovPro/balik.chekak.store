package store.chekak.balik.parser.pdf

/**
 * Извлечение текста из PDF. Реализации платформенные:
 *  * Android — PDFBox-Android (`com.tom-roush:pdfbox-android`);
 *  * iOS — системный PDFKit;
 *  * JVM (desktop) — Apache PDFBox, используется только в тестах.
 */
expect object PdfTextExtractor {
    fun extractText(bytes: ByteArray): String
}
