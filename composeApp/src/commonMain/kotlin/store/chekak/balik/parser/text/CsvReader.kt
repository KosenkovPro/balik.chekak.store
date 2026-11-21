package store.chekak.balik.parser.text

/**
 * Минимальный CSV-ридер: поддерживает разделитель `;` и `,`, кавычки, экранированные
 * кавычки (`""`), переводы строк внутри полей и BOM.
 */
object CsvReader {

    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val delimiter: Char,
    )

    /**
     * Разбирает CSV-текст. Если [delimiter] не задан — определяется по первой строке
     * (счётчик `;` против `,` вне кавычек).
     */
    fun parse(text: String, delimiter: Char? = null): Table {
        val clean = text.removePrefix("\uFEFF")
        val lines = splitLines(clean)
        if (lines.isEmpty()) return Table(emptyList(), emptyList(), delimiter ?: ';')

        val usedDelimiter = delimiter ?: detectDelimiter(lines.first())
        val header = splitFields(lines.first(), usedDelimiter).map { it.trim() }
        val body = lines.drop(1)
            .filter { line -> line.isNotBlank() }
            .map { line -> splitFields(line, usedDelimiter) }
        return Table(header, body, usedDelimiter)
    }

    fun detectDelimiter(sampleLine: String): Char {
        var semicolons = 0
        var commas = 0
        var inQuotes = false
        for (ch in sampleLine) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                !inQuotes && ch == ';' -> semicolons++
                !inQuotes && ch == ',' -> commas++
            }
        }
        return if (commas > semicolons) ',' else ';'
    }

    /** Делит текст на строки, не разрывая строки внутри кавычек. */
    private fun splitLines(text: String): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    current.append('"').append('"')
                    i++
                }
                ch == '"' -> {
                    inQuotes = !inQuotes
                    current.append(ch)
                }
                !inQuotes && (ch == '\n' || ch == '\r') -> {
                    lines.add(current.toString())
                    current.clear()
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> current.append(ch)
            }
            i++
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    /** Делит строку на поля по разделителю, учитывая кавычки. */
    private fun splitFields(line: String, delimiter: Char): List<String> {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val ch = line[i]
            when {
                inQuotes && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                !inQuotes && ch == delimiter -> {
                    fields.add(field.toString())
                    field.clear()
                }
                else -> field.append(ch)
            }
            i++
        }
        fields.add(field.toString())
        return fields
    }
}
