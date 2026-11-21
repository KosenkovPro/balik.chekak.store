package store.chekak.balik.parser.csv

/**
 * Универсальный разбор CSV-выписок: подходит для банков, чей формат ещё не описан
 * отдельным парсером, если в выгрузке есть колонки с датой, описанием и суммой.
 */
class GenericCsvParser : CsvStatementParser() {

    override val bankId: String = "generic_csv"
    override val bankDisplayName: String = "CSV-выгрузка"

    override fun canParse(sampleText: String, fileName: String): Boolean {
        if (!fileName.lowercase().endsWith(".csv") && !sampleText.contains(';') && !sampleText.contains(',')) {
            return false
        }
        val header = sampleText.lineSequence().firstOrNull { it.isNotBlank() }?.lowercase() ?: return false
        val hasDate = header.contains("дата") || header.contains("date")
        val hasAmount = header.contains("сумм") || header.contains("расход") ||
            header.contains("поступлен") || header.contains("дебет") || header.contains("кредит") ||
            header.contains("amount")
        val hasDescription = header.contains("назначение") || header.contains("описание") ||
            header.contains("детали") || header.contains("операц") || header.contains("комментарий")
        return hasDate && hasAmount && hasDescription
    }
}
