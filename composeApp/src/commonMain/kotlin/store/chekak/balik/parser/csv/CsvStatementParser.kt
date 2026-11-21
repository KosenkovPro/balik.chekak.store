package store.chekak.balik.parser.csv

import store.chekak.balik.core.DateUtils
import store.chekak.balik.parser.BankStatementParser
import store.chekak.balik.parser.MerchantNameExtractor
import store.chekak.balik.parser.ParsedOperation
import store.chekak.balik.parser.ParsedStatement
import store.chekak.balik.parser.StatementColumns
import store.chekak.balik.parser.text.AmountParser
import store.chekak.balik.parser.text.CharsetDecoder
import store.chekak.balik.parser.text.CsvReader

/**
 * Базовая реализация разбора CSV-выписок: кодировка, разделитель, сопоставление колонок
 * по заголовкам. Конкретные банки наследуются и описывают только [canParse] и название.
 */
abstract class CsvStatementParser : BankStatementParser {

    override suspend fun parse(bytes: ByteArray, fileName: String): ParsedStatement {
        val decoded = CharsetDecoder.decode(bytes)
        val table = CsvReader.parse(decoded.text)
        if (table.header.isEmpty()) {
            return ParsedStatement(bankId, bankDisplayName, fileType = "CSV")
        }

        val columns = StatementColumns.from(table.header)
        val operations = mutableListOf<ParsedOperation>()
        var unrecognized = 0
        var currency = "RUB"
        var cardLast4: String? = null
        var balance: Long? = null

        for (row in table.rows) {
            val rawDescription = columns.description?.let { row.getOrNull(it)?.trim() }.orEmpty()
            val timestamp = columns.date?.let { DateUtils.parseDateTime(row.getOrNull(it).orEmpty()) }
            val income = columns.income?.let { AmountParser.parseToMinorUnits(row.getOrNull(it).orEmpty()) }
            val expense = columns.expense?.let { AmountParser.parseToMinorUnits(row.getOrNull(it).orEmpty()) }
            // Расходы храним со знаком минус, доходы — плюс.
            var amount = income ?: expense?.let { -it }

            if (amount == null) {
                // Формат с одной колонкой суммы: знак задаёт расход/доход.
                columns.singleAmount?.let { index ->
                    val minor = AmountParser.parseToMinorUnits(row.getOrNull(index).orEmpty())
                    if (minor != null) amount = if (minor < 0) minor else -minor
                }
            }

            if (timestamp == null || amount == null) {
                if (row.any { it.isNotBlank() }) unrecognized++
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
            bankDisplayName = bankDisplayName,
            fileType = "CSV",
            accountName = cardLast4?.let { "Карта $it" } ?: bankDisplayName,
            cardLast4 = cardLast4,
            currency = currency,
            balanceMinor = balance,
            operations = operations,
            totalRows = table.rows.size,
            unrecognizedRows = unrecognized,
        )
    }
}
