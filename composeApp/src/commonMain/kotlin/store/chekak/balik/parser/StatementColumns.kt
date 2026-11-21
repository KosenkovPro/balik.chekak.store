package store.chekak.balik.parser

/**
 * Сопоставление колонок выписки по заголовкам. Используется и CSV-, и XLSX-парсером:
 * банки меняют порядок и добавляют столбцы, но смысл колонок остаётся прежним.
 */
class StatementColumns(
    val date: Int?,
    val processed: Int?,
    val document: Int?,
    val income: Int?,
    val expense: Int?,
    val singleAmount: Int?,
    val currency: Int?,
    val description: Int?,
    val balance: Int?,
    val card: Int?,
    val mcc: Int?,
) {
    companion object {
        fun from(header: List<String>): StatementColumns {
            fun find(predicate: (String) -> Boolean): Int? =
                header.indexOfFirst { predicate(it.trim().lowercase()) }.takeIf { it >= 0 }

            return StatementColumns(
                date = find { it.startsWith("дата операции") || it == "дата" || it.startsWith("дата ") || it.contains("дата и время") },
                processed = find {
                    it.contains("выполнено") || it.contains("дата списания") ||
                        it.contains("провод") || it.contains("дата проводки")
                },
                document = find {
                    it.contains("номер документа") || it == "документ" ||
                        it.startsWith("номер операции") || it.contains("идентификатор")
                },
                income = find {
                    it.contains("поступлен") || it.contains("приход") ||
                        it == "кредит" || it.startsWith("поступление") || it.contains("доход")
                },
                expense = find {
                    it.contains("расход") || it == "дебет" || it.contains("списан") || it.contains("платеж")
                },
                singleAmount = find {
                    it == "сумма" || it.startsWith("сумма ") || it == "amount" || it.contains("сумма операции")
                },
                currency = find { it.contains("валюта") },
                description = find {
                    it.contains("детали операции") || it.contains("назначение") ||
                        it.contains("описание") || it.contains("комментарий") ||
                        it.contains("операция") || it.contains("получатель") || it.contains("платеж")
                },
                balance = find { it.contains("остаток") },
                card = find { it.contains("номер карты") || it == "карта" || it.contains("карты") },
                mcc = find { it.contains("mcc") },
            )
        }
    }
}
