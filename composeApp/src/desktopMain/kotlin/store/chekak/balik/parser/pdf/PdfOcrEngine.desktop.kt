package store.chekak.balik.parser.pdf

/**
 * JVM-реализация: распознавания нет.
 *
 * На desktop приложение не запускается — этот таргет нужен только для тестов
 * парсера PDF, где текст подаётся напрямую. Распознавание проверяется на
 * Android (Tesseract) и iOS (Vision).
 */
actual object PdfOcrEngine {

    actual suspend fun recognizePages(bytes: ByteArray): List<OcrPage> = emptyList()
}
