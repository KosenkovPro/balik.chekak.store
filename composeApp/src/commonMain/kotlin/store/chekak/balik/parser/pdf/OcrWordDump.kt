package store.chekak.balik.parser.pdf

/**
 * Отладочный дамп распознанных слов.
 *
 * Формат строки:
 * `страница|лево,верх,право,низ|текст|уверенность|L:латиница@уверенность|R:кириллица@уверенность`
 *
 * Файл нужен, чтобы посмотреть, что именно распознал движок, и проверить
 * разбор без телефона: по нему видно и рамки слов, и оба прочтения омоглифов.
 * В обычной сборке файл не создаётся.
 *
 * Формат живёт здесь, а не в движке, чтобы движок и проверки читали его
 * одинаково.
 */
object OcrWordDump {

    /** Метка латинского прочтения. */
    const val LATIN_MARK = "L:"

    /** Метка кириллического прочтения. */
    const val CYRILLIC_MARK = "R:"

    /** Строка дампа для одного слова. */
    fun format(page: Int, word: OcrWord): String = buildString {
        append(page).append('|')
        append(word.left).append(',').append(word.top).append(',')
        append(word.right).append(',').append(word.bottom).append('|')
        append(word.text).append('|')
        append("%.1f".format(word.confidence)).append('|')
        append(reading(word.latinText, word.latinConfidence, LATIN_MARK)).append('|')
        append(reading(word.cyrillicText, word.cyrillicConfidence, CYRILLIC_MARK))
    }

    /**
     * Разбирает строку дампа.
     *
     * @return номер страницы и слово или null, если строка наш формат не имеет.
     */
    fun parse(line: String): Pair<Int, OcrWord>? {
        val parts = line.split('|')
        if (parts.size < 6) return null
        val page = parts[0].trim().toIntOrNull() ?: return null
        val box = parts[1].split(',').map { it.trim().toIntOrNull() ?: 0 }
        if (box.size < 4) return null
        // Текст может содержать вертикальную черту — берём поле между рамкой
        // и уверенностью, а с конца отсчитываем служебные поля.
        val text = parts.subList(2, parts.size - 3).joinToString("|")
        val confidence = parts[parts.size - 3].trim().toFloatOrNull() ?: 0f
        val latin = parseReading(parts[parts.size - 2])
        val cyrillic = parseReading(parts[parts.size - 1])
        return page to OcrWord(
            text = text,
            left = box[0],
            top = box[1],
            right = box[2],
            bottom = box[3],
            confidence = confidence,
            latinText = latin?.first,
            latinConfidence = latin?.second ?: 0f,
            cyrillicText = cyrillic?.first,
            cyrillicConfidence = cyrillic?.second ?: 0f,
        )
    }

    /** Прочтение с меткой: `L:PYATEROCHKA@91.0`. */
    private fun reading(text: String?, confidence: Float, mark: String): String =
        text?.let { "$mark$it@${"%.1f".format(confidence)}" }.orEmpty()

    /** Разбирает поле прочтения. */
    private fun parseReading(field: String): Pair<String, Float>? {
        val value = field.trim()
        if (!value.startsWith(LATIN_MARK) && !value.startsWith(CYRILLIC_MARK)) return null
        val body = value.substringAfter(':')
        if (body.isEmpty()) return null
        val name = body.substringBefore('@')
        val confidence = body.substringAfter('@', "").toFloatOrNull() ?: 0f
        return name to confidence
    }
}
