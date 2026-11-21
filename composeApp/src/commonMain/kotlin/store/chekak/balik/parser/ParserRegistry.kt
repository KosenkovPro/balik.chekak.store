package store.chekak.balik.parser

import store.chekak.balik.parser.csv.GenericCsvParser
import store.chekak.balik.parser.csv.RaiffeisenCsvParser
import store.chekak.balik.parser.pdf.GenericPdfParser
import store.chekak.balik.parser.pdf.OcrLayout
import store.chekak.balik.parser.pdf.OcrScript
import store.chekak.balik.parser.pdf.PdfOcrEngine
import store.chekak.balik.parser.pdf.PdfStatementParser
import store.chekak.balik.parser.pdf.TBankPdfParser
import store.chekak.balik.parser.pdf.PdfTextExtractor
import store.chekak.balik.parser.text.CharsetDecoder
import store.chekak.balik.parser.xlsx.XlsxStatementParser

/**
 * Реестр парсеров: определяет тип файла и подбирает подходящий банковский парсер.
 * Чтобы добавить банк, достаточно реализовать [BankStatementParser] и добавить его
 * в список — ни UI, ни база данных при этом не меняются.
 */
class ParserRegistry(
    private val csvParsers: List<BankStatementParser> = listOf(RaiffeisenCsvParser(), GenericCsvParser()),
    private val pdfParsers: List<PdfStatementParser> = listOf(TBankPdfParser(), GenericPdfParser()),
    private val xlsxParsers: List<BankStatementParser> = listOf(XlsxStatementParser()),
) {

    enum class FileType { CSV, XLSX, PDF, UNKNOWN }

    /**
     * Разбирает файл выписки.
     *
     * @param allowOcr можно ли распознавать скан. Для сверки с оригиналом OCR не
     *   нужен: оригинал обязан иметь текстовый слой, иначе сверять нечего.
     */
    suspend fun parse(
        bytes: ByteArray,
        fileName: String,
        allowOcr: Boolean = true,
    ): ParsedStatement {
        return when (detectFileType(bytes, fileName)) {
            FileType.CSV -> parseCsv(bytes, fileName)
            FileType.XLSX -> parseXlsx(bytes, fileName)
            FileType.PDF -> parsePdf(bytes, fileName, allowOcr)
            FileType.UNKNOWN -> throw UnsupportedOperationException(
                "Не удалось определить формат файла: $fileName",
            )
        }
    }

    fun detectFileType(bytes: ByteArray, fileName: String): FileType {
        val name = fileName.lowercase()
        if (bytes.size >= 4 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()
        ) {
            return FileType.PDF
        }
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
        ) {
            return FileType.XLSX
        }
        if (name.endsWith(".pdf")) return FileType.PDF
        if (name.endsWith(".xlsx")) return FileType.XLSX
        if (name.endsWith(".csv") || name.endsWith(".txt")) return FileType.CSV

        val head = CharsetDecoder.decode(bytes.copyOfRange(0, minOf(bytes.size, 4096))).text
        return when {
            head.contains(';') || head.contains(',') -> FileType.CSV
            else -> FileType.UNKNOWN
        }
    }

    private suspend fun parseCsv(bytes: ByteArray, fileName: String): ParsedStatement {
        val text = CharsetDecoder.decode(bytes).text
        val parser = csvParsers.firstOrNull { it.canParse(text, fileName) } ?: csvParsers.last()
        return parser.parse(bytes, fileName)
    }

    private suspend fun parseXlsx(bytes: ByteArray, fileName: String): ParsedStatement {
        val parser = xlsxParsers.firstOrNull { it.canParse("", fileName) } ?: xlsxParsers.last()
        return parser.parse(bytes, fileName)
    }

    private suspend fun parsePdf(bytes: ByteArray, fileName: String, allowOcr: Boolean): ParsedStatement {
        val textLayer = PdfTextExtractor.extractText(bytes)
        val isScan = textLayer.trim().length <= SCAN_TEXT_THRESHOLD

        // Оригинал для сверки должен быть настоящей выпиской с текстовым слоем:
        // распознавать его заново значит сравнивать OCR с OCR.
        if (isScan && !allowOcr) {
            throw StatementParseException(
                reason = ParseFailureReason.REFERENCE_WITHOUT_TEXT,
                message = "В оригинале нет текстового слоя: $fileName",
            )
        }

        // У скана текстового слоя нет, поэтому банк определяем по распознанному
        // тексту. Распознаём один раз: тот же текст идёт и на выбор банка,
        // и на разбор операций — второй прогон занял бы минуты.
        val ocrText = if (isScan) OcrScript.toText(PdfOcrEngine.recognizePages(bytes)) else ""
        val detectionText = ocrText.ifBlank { textLayer }

        val parser = pdfParsers.firstOrNull { it.canParse(detectionText, fileName) } ?: pdfParsers.last()
        val statement = if (isScan) {
            parser.parseScanned(bytes, fileName, ocrText)
        } else {
            parser.parse(bytes, fileName)
        }

        if (statement.operations.isEmpty()) {
            if (isScan) {
                throw StatementParseException(
                    reason = ParseFailureReason.SCAN_NOT_RECOGNIZED,
                    message = "Не удалось распознать текст в PDF: $fileName",
                )
            }
            throw StatementParseException(
                reason = ParseFailureReason.NO_OPERATIONS,
                message = "В PDF не найдено операций: $fileName",
            )
        }
        return statement
    }

    private companion object {
        /**
         * Сколько символов текстового слоя считается «текста нет».
         *
         * У скана текстового слоя обычно нет совсем, но у части PDF остаётся
         * служебная подпись (дата печати, номер счёта). Если символов больше
         * порога, документ не скан и OCR не нужен.
         */
        const val SCAN_TEXT_THRESHOLD = 200
    }
}
