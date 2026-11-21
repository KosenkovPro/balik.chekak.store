package store.chekak.balik.parser.xlsx

import store.chekak.balik.core.DateUtils
import store.chekak.balik.parser.BankStatementParser
import store.chekak.balik.parser.MerchantNameExtractor
import store.chekak.balik.parser.ParsedOperation
import store.chekak.balik.parser.ParsedStatement
import store.chekak.balik.parser.StatementColumns
import store.chekak.balik.parser.text.AmountParser

/**
 * Универсальный парсер банковских выгрузок в формате XLSX.
 * Определяет банк по названию в шапке файла, колонки — по заголовкам.
 */
class XlsxStatementParser : BankStatementParser {

    override val bankId: String = "xlsx"
    override val bankDisplayName: String = "XLSX-выгрузка"

    override fun canParse(sampleText: String, fileName: String): Boolean =
        fileName.lowercase().endsWith(".xlsx") || sampleText.contains("sheetData", ignoreCase = true)

    override suspend fun parse(bytes: ByteArray, fileName: String): ParsedStatement {
        val rows = runCatching { XlsxReader.readRows(bytes) }.getOrElse { emptyList() }
        if (rows.isEmpty()) {
            return ParsedStatement(bankId, bankDisplayName, fileType = "XLSX")
        }

        val headerIndex = findHeaderRow(rows)
        if (headerIndex < 0) {
            return ParsedStatement(bankId, bankDisplayName, fileType = "XLSX", totalRows = rows.size)
        }

        val columns = StatementColumns.from(rows[headerIndex])
        val bankName = detectBankName(rows, headerIndex)
        val operations = mutableListOf<ParsedOperation>()
        var unrecognized = 0
        var currency = "RUB"
        var cardLast4: String? = null
        var balance: Long? = null

        for (row in rows.drop(headerIndex + 1)) {
            if (row.all { it.isBlank() }) continue

            val rawDescription = columns.description?.let { row.getOrNull(it)?.trim() }.orEmpty()
            val timestamp = columns.date?.let { DateUtils.parseDateTime(row.getOrNull(it).orEmpty()) }
            val income = columns.income?.let { AmountParser.parseToMinorUnits(row.getOrNull(it).orEmpty()) }
            val expense = columns.expense?.let { AmountParser.parseToMinorUnits(row.getOrNull(it).orEmpty()) }
            var amount = income ?: expense
            if (amount == null) {
                columns.singleAmount?.let { index ->
                    val minor = AmountParser.parseToMinorUnits(row.getOrNull(index).orEmpty())
                    if (minor != null) amount = if (minor < 0) minor else -minor
                }
            }

            if (timestamp == null || amount == null) {
                unrecognized++
                continue
            }

            columns.currency?.let { index ->
                row.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }?.let { currency = it }
            }
            columns.card?.let { index ->
                row.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }?.let { card ->
                    cardLast4 = card.filter { ch -> ch.isDigit() }.takeLast(4).takeIf { it.isNotEmpty() }
                }
            }
            columns.balance?.let { index ->
                AmountParser.parseToMinorUnits(row.getOrNull(index).orEmpty())?.let { balance = it }
            }

            operations += ParsedOperation(
                timestamp = timestamp,
                processedTimestamp = columns.processed?.let { DateUtils.parseDateTime(row.getOrNull(it).orEmpty()) },
                amountMinor = amount,
                currency = currency,
                rawDescription = rawDescription,
                merchantName = MerchantNameExtractor.extract(rawDescription),
                mcc = columns.mcc?.let { row.getOrNull(it)?.trim()?.takeIf { m -> m.isNotEmpty() } },
                documentId = columns.document?.let { row.getOrNull(it)?.trim()?.takeIf { d -> d.isNotEmpty() } },
            )
        }

        return ParsedStatement(
            bankId = bankId,
            bankDisplayName = bankName,
            fileType = "XLSX",
            accountName = cardLast4?.let { "Карта $it" } ?: "Выгрузка XLSX",
            cardLast4 = cardLast4,
            currency = currency,
            balanceMinor = balance,
            operations = operations,
            totalRows = rows.size - headerIndex - 1,
            unrecognizedRows = unrecognized,
        )
    }

    /** Ищет строку заголовков: в ней должны быть дата и описание/назначение. */
    private fun findHeaderRow(rows: List<List<String>>): Int {
        rows.forEachIndexed { index, row ->
            if (index > 25) return@forEachIndexed
            val joined = row.joinToString(" ").lowercase()
            val hasDate = joined.contains("дата") || joined.contains("date")
            val hasDescription = joined.contains("назначение") || joined.contains("описание") ||
                joined.contains("детали") || joined.contains("операц") || joined.contains("комментарий")
            val hasAmount = joined.contains("сумм") || joined.contains("расход") ||
                joined.contains("поступлен") || joined.contains("дебет") || joined.contains("кредит")
            if (hasDate && hasDescription && hasAmount) return index
        }
        return -1
    }

    /** Банк определяем по шапке файла (строки выше заголовка). */
    private fun detectBankName(rows: List<List<String>>, headerIndex: Int): String {
        val header = rows.take(headerIndex.coerceAtLeast(0))
            .joinToString(" ") { it.joinToString(" ") }
        return when {
            header.contains("Райффайзен", ignoreCase = true) -> "Райффайзенбанк"
            header.contains("ТБАНК", ignoreCase = true) || header.contains("Т-Банк", ignoreCase = true) -> "Т-Банк"
            header.contains("Сбер", ignoreCase = true) -> "Сбербанк"
            header.contains("Альфа", ignoreCase = true) -> "Альфа-Банк"
            header.contains("ВТБ", ignoreCase = true) -> "ВТБ"
            else -> bankDisplayName
        }
    }
}
