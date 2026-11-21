package store.chekak.balik.parser

/** Одна операция, разобранная из выписки. */
data class ParsedOperation(
    /** Дата операции (когда списали/приняли). */
    val timestamp: Long,
    /** Дата обработки банком, если выписка её содержит. */
    val processedTimestamp: Long? = null,
    /** Сумма в минорных единицах со знаком: отрицательная — расход. */
    val amountMinor: Long,
    val currency: String = "RUB",
    /** Исходное назначение платежа из выписки. */
    val rawDescription: String = "",
    /** Очищенное имя точки/получателя. */
    val merchantName: String = "",
    val mcc: String? = null,
    /** Номер документа/операции в банке — помогает различать операции. */
    val documentId: String? = null,
)

/** Результат разбора выписки. */
data class ParsedStatement(
    val bankId: String,
    val bankDisplayName: String,
    val fileType: String,
    val accountName: String = "",
    val cardLast4: String? = null,
    val currency: String = "RUB",
    val balanceMinor: Long? = null,
    val periodStart: Long? = null,
    val periodEnd: Long? = null,
    val operations: List<ParsedOperation> = emptyList(),
    val totalRows: Int = 0,
    val unrecognizedRows: Int = 0,
    /** Текст выписки распознан по картинке (OCR), а не взят из текстового слоя. */
    val ocrUsed: Boolean = false,
)

/**
 * Контракт парсера выписок. Чтобы добавить банк, достаточно реализовать интерфейс
 * и зарегистрировать парсер в [ParserRegistry] — UI и база данных не меняются.
 */
interface BankStatementParser {
    val bankId: String
    val bankDisplayName: String

    /** Может ли этот парсер разобрать файл. */
    fun canParse(sampleText: String, fileName: String): Boolean

    suspend fun parse(bytes: ByteArray, fileName: String): ParsedStatement
}
