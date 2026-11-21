package store.chekak.balik.parser.csv

/**
 * Парсер CSV-выгрузок Райффайзенбанка.
 *
 * Фактический формат выгрузки (проверен на реальном файле):
 * `Дата операции;Выполнено банком;Номер документа;Поступления;Расходы;Валюта;Детали операции (назначение платежа);Номер карты`
 *
 * Колонки сопоставляются по заголовкам, поэтому порядок и лишние столбцы не важны.
 */
class RaiffeisenCsvParser : CsvStatementParser() {

    override val bankId: String = "raiffeisen"
    override val bankDisplayName: String = "Райффайзенбанк"

    override fun canParse(sampleText: String, fileName: String): Boolean {
        val header = sampleText.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
        val lower = header.lowercase()
        val hasDocument = lower.contains("номер документа")
        val hasDetails = lower.contains("детали операции") || lower.contains("назначение платежа")
        val hasAmounts = lower.contains("поступления") || lower.contains("расходы")
        return (hasDocument && hasDetails) || (hasDetails && hasAmounts)
    }
}
