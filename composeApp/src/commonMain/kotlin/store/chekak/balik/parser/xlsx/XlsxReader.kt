package store.chekak.balik.parser.xlsx

/**
 * Чтение таблиц XLSX: ZIP → sharedStrings.xml + worksheets/sheet1.xml.
 * Возвращает строки как списки строк (пустые ячейки сохраняются по позиции колонки).
 */
object XlsxReader {

    fun readRows(bytes: ByteArray): List<List<String>> {
        val entries = ZipReader.read(bytes)
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it.decodeToString()) } ?: emptyList()
        val sheetBytes = findFirstSheet(entries) ?: return emptyList()
        return parseSheet(sheetBytes.decodeToString(), sharedStrings)
    }

    private fun findFirstSheet(entries: Map<String, ByteArray>): ByteArray? {
        entries["xl/worksheets/sheet1.xml"]?.let { return it }
        return entries.entries
            .filter { it.key.startsWith("xl/worksheets/") && it.key.endsWith(".xml") }
            .minByOrNull { it.key }
            ?.value
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val root = XmlParser.parse(xml)
        // Корневой элемент — <sst>, строки лежат в его детях; ищем в глубину,
        // чтобы не зависеть от наличия обёртки.
        val container = root.findDeep("sst") ?: root
        val strings = mutableListOf<String>()
        for (si in container.childrenNamed("si")) {
            // В одном <si> текст может быть разбит на несколько <t> (например, с форматированием).
            val text = si.childrenNamed("t").joinToString("") { it.text }
            strings.add(text.ifEmpty { si.text })
        }
        return strings
    }

    private fun parseSheet(xml: String, sharedStrings: List<String>): List<List<String>> {
        val root = XmlParser.parse(xml)
        // <sheetData> лежит внутри <worksheet> — ищем в глубину.
        val sheetData = root.findDeep("sheetData") ?: return emptyList()
        val rows = mutableListOf<List<String>>()

        for (row in sheetData.childrenNamed("row")) {
            val cells = row.childrenNamed("c")
            if (cells.isEmpty()) continue

            var maxIndex = 0
            val values = mutableMapOf<Int, String>()
            for (cell in cells) {
                val index = columnIndex(cell.attribute("r"))
                val value = cellValue(cell, sharedStrings)
                if (index >= 0) {
                    values[index] = value
                    if (index > maxIndex) maxIndex = index
                }
            }
            rows.add(List(maxIndex + 1) { values[it] ?: "" })
        }
        return rows
    }

    private fun cellValue(cell: XmlParser.Node, sharedStrings: List<String>): String {
        val type = cell.attribute("t")
        return when (type) {
            "s" -> {
                val index = cell.child("v")?.text?.trim()?.toIntOrNull() ?: -1
                if (index in sharedStrings.indices) sharedStrings[index] else ""
            }
            "inlineStr" -> cell.child("is")?.childrenNamed("t")?.joinToString("") { it.text } ?: cell.text
            "str" -> cell.child("v")?.text ?: cell.text
            else -> cell.child("v")?.text ?: cell.text
        }
    }

    /** "BC12" → 54 (индекс колонки с нуля). */
    private fun columnIndex(reference: String?): Int {
        if (reference.isNullOrEmpty()) return -1
        var index = 0
        for (ch in reference) {
            if (!ch.isLetter()) break
            index = index * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return index - 1
    }
}
