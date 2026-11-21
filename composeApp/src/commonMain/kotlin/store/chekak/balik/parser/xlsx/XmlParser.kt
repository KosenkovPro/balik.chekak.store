package store.chekak.balik.parser.xlsx

/**
 * Крошечный XML-парсер: достаточно для чтения XLSX (worksheet, sharedStrings).
 * Поддерживает объявления, комментарии, CDATA, самозакрывающиеся теги и сущности.
 */
object XmlParser {

    data class Node(
        val name: String,
        val attributes: Map<String, String>,
        val children: List<Node>,
        val text: String,
    ) {
        fun child(name: String): Node? = children.firstOrNull { it.name == name }

        fun childrenNamed(name: String): List<Node> = children.filter { it.name == name }

        fun attribute(name: String): String? = attributes[name]

        /**
         * Ищет узел по имени на любой глубине (первый совпавший в порядке обхода).
         * Нужно для XLSX, где данные лежат внутри обёрток (<sst>, <worksheet>).
         */
        fun findDeep(name: String): Node? {
            if (this.name == name) return this
            for (child in children) {
                child.findDeep(name)?.let { return it }
            }
            return null
        }
    }

    private class Builder(val name: String, val attributes: Map<String, String>) {
        val children = mutableListOf<Builder>()
        val text = StringBuilder()
        var parent: Builder? = null

        fun build(): Node = Node(name, attributes, children.map { it.build() }, text.toString())
    }

    fun parse(xml: String): Node {
        val root = Builder("root", emptyMap())
        var current = root
        var i = 0

        while (i < xml.length) {
            val open = xml.indexOf('<', i)
            if (open < 0) break

            if (open > i) {
                current.text.append(decodeEntities(xml.substring(i, open)))
            }

            when {
                xml.startsWith("<!--", open) -> {
                    val end = xml.indexOf("-->", open)
                    i = if (end < 0) xml.length else end + 3
                }
                xml.startsWith("<![CDATA[", open) -> {
                    val end = xml.indexOf("]]>", open)
                    if (end >= 0) current.text.append(xml.substring(open + 9, end))
                    i = if (end < 0) xml.length else end + 3
                }
                xml.startsWith("<?", open) || xml.startsWith("<!", open) -> {
                    val end = xml.indexOf('>', open)
                    i = if (end < 0) xml.length else end + 1
                }
                xml.startsWith("</", open) -> {
                    val end = xml.indexOf('>', open)
                    if (current !== root) current = current.parent ?: root
                    i = if (end < 0) xml.length else end + 1
                }
                else -> {
                    val end = findTagEnd(xml, open)
                    val raw = xml.substring(open + 1, end)
                    val selfClosing = raw.endsWith("/")
                    val body = if (selfClosing) raw.dropLast(1) else raw
                    val node = parseTag(body)
                    current.children.add(node)
                    if (!selfClosing) {
                        node.parent = current
                        current = node
                    }
                    i = end + 1
                }
            }
        }
        return root.build()
    }

    private fun findTagEnd(xml: String, start: Int): Int {
        var i = start + 1
        var inQuote: Char? = null
        while (i < xml.length) {
            val ch = xml[i]
            when {
                inQuote != null && ch == inQuote -> inQuote = null
                inQuote == null && (ch == '"' || ch == '\'') -> inQuote = ch
                inQuote == null && ch == '>' -> return i
            }
            i++
        }
        return xml.length
    }

    private fun parseTag(body: String): Builder {
        val trimmed = body.trim()
        val spaceIndex = trimmed.indexOfFirst { it.isWhitespace() }
        val rawName = if (spaceIndex > 0) trimmed.substring(0, spaceIndex) else trimmed
        val name = rawName.substringAfterLast(':')
        val attributes = mutableMapOf<String, String>()

        if (spaceIndex > 0) {
            val attrPart = trimmed.substring(spaceIndex)
            val regex = Regex("""([\w:.\-]+)\s*=\s*"([^"]*)"|([\w:.\-]+)\s*=\s*'([^']*)'""")
            for (match in regex.findAll(attrPart)) {
                val key = (match.groupValues[1].ifEmpty { match.groupValues[3] }).substringAfterLast(':')
                val value = if (match.groupValues[1].isNotEmpty()) match.groupValues[2] else match.groupValues[4]
                attributes[key] = decodeEntities(value)
            }
        }
        return Builder(name, attributes)
    }

    fun decodeEntities(text: String): String {
        if (!text.contains('&')) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '&') {
                val end = text.indexOf(';', i)
                if (end in (i + 1)..(i + 10)) {
                    val entity = text.substring(i + 1, end)
                    val replacement = when (entity) {
                        "amp" -> "&"
                        "lt" -> "<"
                        "gt" -> ">"
                        "quot" -> "\""
                        "apos" -> "'"
                        else -> entity.removePrefix("#").toIntOrNull()?.let { codePointToString(it) }
                    }
                    if (replacement != null) {
                        sb.append(replacement)
                        i = end + 1
                        continue
                    }
                }
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    private fun codePointToString(code: Int): String? = when (code) {
        in 0..0x10FFFF -> if (code < 0x10000) {
            code.toChar().toString()
        } else {
            val offset = code - 0x10000
            charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar())
                .concatToString()
        }
        else -> null
    }
}
