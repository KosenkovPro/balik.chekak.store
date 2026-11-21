package store.chekak.balik.parser.pdf

/**
 * Универсальный разбор PDF-справок: используется, когда банк не распознан,
 * но таблица операций построена по стандартной схеме.
 */
class GenericPdfParser : PdfStatementParser() {

    override val bankId: String = "generic_pdf"
    override val bankDisplayName: String = "PDF-выписка"

    override fun canParse(sampleText: String, fileName: String): Boolean =
        fileName.lowercase().endsWith(".pdf")
}
