package store.chekak.balik.parser.pdf

import store.chekak.balik.core.DateUtils
import store.chekak.balik.parser.BankStatementParser
import store.chekak.balik.parser.MerchantNameExtractor
import store.chekak.balik.parser.ParsedOperation
import store.chekak.balik.parser.ParsedStatement
import store.chekak.balik.parser.text.AmountParser

/**
 * Базовая реализация разбора банковских справок в PDF.
 *
 * Поддерживаются две раскладки таблицы операций:
 *
 *  1. **Структурированная** (Т-Банк, JasperReports). Строка операции начинается с двух дат
 *     (дата операции и дата списания) и двух сумм с символом валюты, а время операции и
 *     хвост описания приходят следующей строкой:
 *     ```
 *     14.10.2026 14.10.2026 -1 364.72 ₽ -1 364.72 ₽ Оплата в PEREKRESTOK 6915
 *     19:11 19:26 PAPERNIKA MOSCOW RUS
 *     ```
 *     Номер карты — последний токен строки операции.
 *
 *  2. **Свободная**. Строка начинается с даты и времени (либо дата и время стоят на двух
 *     строках подряд), описание может переноситься на несколько строк.
 */
abstract class PdfStatementParser : BankStatementParser {

    protected open val defaultCurrency: String = "RUB"

    override suspend fun parse(bytes: ByteArray, fileName: String): ParsedStatement {
        val fromTextLayer = parseText(PdfTextExtractor.extractText(bytes))
        if (fromTextLayer.operations.isNotEmpty()) return fromTextLayer

        // Текстового слоя нет (или он нечитаемый) — значит это скан.
        // Распознаём картинку и разбираем получившийся текст тем же кодом.
        val pages = PdfOcrEngine.recognizePages(bytes)
        if (pages.isEmpty()) return fromTextLayer

        val fromOcr = parseText(OcrScript.toText(pages)).copy(ocrUsed = true)
        return if (fromOcr.operations.isNotEmpty()) fromOcr else fromTextLayer
    }

    /**
     * Разбор PDF с уже распознанным текстом.
     *
     * Нужен реестру парсеров: для скана текст распознаётся один раз, и он же
     * служит признаком банка при выборе парсера. Повторный запуск OCR занял бы
     * минуты на многостраничной выписке.
     *
     * @param ocrText текст, распознанный по картинкам страниц.
     */
    suspend fun parseScanned(bytes: ByteArray, fileName: String, ocrText: String): ParsedStatement {
        val fromOcr = parseText(ocrText).copy(ocrUsed = true)
        if (fromOcr.operations.isNotEmpty()) return fromOcr
        // Распознанный текст ничего не дал — пробуем текстовый слой (он может
        // быть, но в нечитаемом виде).
        return parseText(PdfTextExtractor.extractText(bytes))
    }

    /** Разбор уже извлечённого текста — так парсер тестируется без PDF. */
    fun parseText(rawText: String): ParsedStatement {
        val text = rawText.replace('\u00A0', ' ').replace('\u202F', ' ')
        // Страницы разбираются по отдельности: строка операции не должна
        // собираться из кусков разных страниц. Для OCR между страницами стоит
        // разделитель, для обычного текстового слоя страница одна.
        val pages = text.split(OcrLayout.PAGE_SEPARATOR)
        val pageLines = pages.map { page -> page.toLines() }
        val lines = pageLines.flatten()

        val header = parseHeader(lines)
        val operations = mutableListOf<ParsedOperation>()
        val cardNumbers = mutableListOf<String>()
        var unrecognized = 0

        for (linesInPage in pageLines) {
            for (row in splitIntoRows(linesInPage)) {
                val parsed = parseRow(row)
                if (parsed == null) {
                    unrecognized++
                } else {
                    operations += parsed.first
                    parsed.second?.let { cardNumbers += it }
                }
            }
        }

        return ParsedStatement(
            bankId = bankId,
            bankDisplayName = bankDisplayName,
            fileType = "PDF",
            accountName = header.accountName,
            cardLast4 = cardNumbers.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
            currency = defaultCurrency,
            balanceMinor = header.balanceMinor,
            periodStart = header.periodStart,
            periodEnd = header.periodEnd,
            operations = operations,
            totalRows = operations.size + unrecognized,
            unrecognizedRows = unrecognized,
        )
    }

    /** Данные из шапки справки. */
    protected data class Header(
        val accountName: String = "",
        val balanceMinor: Long? = null,
        val periodStart: Long? = null,
        val periodEnd: Long? = null,
    )

    protected open fun parseHeader(lines: List<String>): Header = Header()

    /** Одна операция: первая строка + строки-продолжения описания. */
    protected class PdfRow(val head: String) {
        val continuations: MutableList<String> = mutableListOf()

        /** Вся строка целиком, склеенная в одну. */
        val fullText: String get() = (listOf(head) + continuations).joinToString(" ")
    }

    /**
     * Группирует строки в операции. Начало операции — строка со структурированной
     * раскладкой либо строка с датой и временем. Строки-продолжения относятся к операции,
     * пока не встретится «служебный» текст (колонтитул страницы, итоги, подпись).
     */
    protected open fun splitIntoRows(lines: List<String>): List<PdfRow> {
        val rows = mutableListOf<PdfRow>()
        var current: PdfRow? = null
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            when {
                isOperationRow(line) -> {
                    current?.let { rows.add(it) }
                    current = PdfRow(line)
                    i++
                }

                dateTimeStart.containsMatchIn(line) -> {
                    current?.let { rows.add(it) }
                    current = PdfRow(line)
                    i++
                }

                dateOnly.matches(line) && i + 1 < lines.size && timeOnly.matches(lines[i + 1]) -> {
                    current?.let { rows.add(it) }
                    current = PdfRow("$line ${lines[i + 1]}")
                    i += 2
                }

                else -> {
                    val row = current
                    if (row == null) {
                        i++
                        continue
                    }
                    if (isPageFurniture(line)) {
                        rows.add(row)
                        current = null
                    } else {
                        row.continuations.add(line)
                    }
                    i++
                }
            }
        }
        current?.let { rows.add(it) }
        return rows
    }

    /** Разбор одной операции: структурированная строка либо свободный текст. */
    protected open fun parseRow(row: PdfRow): Pair<ParsedOperation, String?>? {
        structuredRow.find(row.head)?.let { match -> return parseStructuredRow(row, match) }
        structuredRowNoFirstCurrency.find(row.head)?.let { match -> return parseStructuredRow(row, match) }
        parseStructuredByColumns(row)?.let { return it }
        return parseFreeRow(row)
    }

    /**
     * Строка операции, разобранная по колонкам таблицы.
     *
     * Если движок не отдал знак валюты у одной из сумм, строку не разобрать
     * регуляркой: без знака неясно, где кончается сумма и начинается описание.
     * Но колонки таблицы разделены широкими промежутками — по ним границы и
     * восстанавливаются.
     *
     * Колонки выписки Т-Банка: дата операции, дата списания, сумма операции,
     * сумма в валюте карты, описание, номер карты.
     */
    private fun parseStructuredByColumns(row: PdfRow): Pair<ParsedOperation, String?>? {
        val columns = columnsOf(row.head) ?: return null
        val operationDate = columns[0]
        val processedDate = columns[1]
        val amount = AmountParser.parseToMinorUnits(fixOcrDigits(columns[2]))
            ?: AmountParser.parseToMinorUnits(fixOcrDigits(columns[3]))
            ?: return null
        val currency = currencyIn(columns[2]) ?: currencyIn(columns[3]) ?: defaultCurrency

        val tail = columns.drop(4).joinToString(" ")
        val (headDescription, cardNumber) = splitCard(tail)
        val (operationTime, processedTime, continuation) = extractTimes(row.continuations)
        val timestamp = parseOperationTime(operationDate, operationTime) ?: return null
        val processedTimestamp = DateUtils.parseDateTime("$processedDate $processedTime")

        val rawDescription = listOf(headDescription, continuation)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        val operation = ParsedOperation(
            timestamp = timestamp,
            processedTimestamp = processedTimestamp,
            amountMinor = amount,
            currency = currency,
            rawDescription = rawDescription,
            merchantName = MerchantNameExtractor.extract(rawDescription),
            documentId = null,
        )
        return operation to cardNumber
    }

    /** Похоже ли строка на строку операции: две даты, суммы, описание. */
    private fun isOperationRow(line: String): Boolean {
        if (structuredRow.containsMatchIn(line)) return true
        if (structuredRowNoFirstCurrency.containsMatchIn(line)) return true
        return columnsOf(line) != null
    }

    /**
     * Колонки строки операции.
     *
     * Колонки разделены широкими промежутками, а начинаются две даты — по ним
     * строка и опознаётся как строка операции.
     */
    private fun columnsOf(line: String): List<String>? {
        val columns = line.split(columnGap).map { it.trim() }.filter { it.isNotEmpty() }
        if (columns.size < 4) return null
        if (!datePattern.matches(columns[0]) || !datePattern.matches(columns[1])) return null
        return columns
    }

    /** Знак валюты в тексте колонки суммы. */
    private fun currencyIn(text: String): String? =
        currencySymbols.firstOrNull { text.contains(it) }?.let { currencyOf(it) }

    /**
     * Время операции.
     *
     * Движок иногда читает время неверно («00:09» → «60:00»), и тогда дата со
     * временем не разбирается. Сумма и описание при этом прочитаны верно,
     * поэтому операцию не теряем — ставим начало дня.
     */
    private fun parseOperationTime(operationDate: String, operationTime: String): Long? =
        DateUtils.parseDateTime("$operationDate $operationTime")
            ?: DateUtils.parseDateTime("$operationDate 00:00")

    /** Операция в структурированной раскладке: даты и суммы в первой строке, время — в следующей. */
    private fun parseStructuredRow(
        row: PdfRow,
        match: MatchResult,
    ): Pair<ParsedOperation, String?>? {
        val operationDate = match.groupValues[1]
        val processedDate = match.groupValues[2]
        // Знак валюты стоит у второй суммы; у первой он может отсутствовать —
        // тогда берём знак от второй.
        val currency = currencyOf(match.groupValues[4].ifBlank { match.groupValues[6] })

        // Сумма операции и сумма в валюте карты в справке совпадают. Если первую
        // не удалось разобрать (движок искал цифру), берём вторую.
        val amount = AmountParser.parseToMinorUnits(fixOcrDigits(match.groupValues[3]))
            ?: AmountParser.parseToMinorUnits(fixOcrDigits(match.groupValues[5]))
            ?: return null
        val (headDescription, cardNumber) = splitCard(match.groupValues[7])

        val (operationTime, processedTime, continuation) = extractTimes(row.continuations)
        val timestamp = parseOperationTime(operationDate, operationTime) ?: return null
        val processedTimestamp = DateUtils.parseDateTime("$processedDate $processedTime")

        val rawDescription = listOf(headDescription, continuation)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        val operation = ParsedOperation(
            timestamp = timestamp,
            processedTimestamp = processedTimestamp,
            amountMinor = amount,
            currency = currency,
            rawDescription = rawDescription,
            merchantName = MerchantNameExtractor.extract(rawDescription),
            documentId = null,
        )
        return operation to cardNumber
    }

    /** Операция в свободной раскладке: дата, время, суммы и описание в общем тексте. */
    private fun parseFreeRow(row: PdfRow): Pair<ParsedOperation, String?>? {
        val block = row.fullText
        val start = dateTimeStart.find(block) ?: return null

        val operationDate = start.groupValues[1]
        val operationTime = start.groupValues[2]
        val timestamp = parseOperationTime(operationDate, operationTime) ?: return null

        // Все даты блока: первая — дата операции, вторая (если есть) — дата списания.
        val allDates = datePattern.findAll(block).map { it.value }.toList()
        val processedDate = allDates.getOrNull(1)?.let { DateUtils.parseDateTime(it) }

        // Убираем даты и время, чтобы они не путались с суммами.
        val withoutDates = block.replace(datePattern, " ")
        val withoutTimes = withoutDates.replace(timePattern, " ")

        // Суммы: первая — в валюте операции, вторая — в валюте карты (обычно совпадают).
        val amounts = amountPattern.findAll(withoutTimes).mapNotNull { match ->
            AmountParser.parseToMinorUnits(match.groupValues[1])
        }.toList()

        if (amounts.isEmpty()) return null
        val amount = amounts.first()

        // Описание: всё, что осталось после сумм, минус номер карты в конце.
        var description = currencyNoise.replace(withoutTimes, " ")
            .replace(attachedCurrency, " ")
            .replace(amountPattern, " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        var cardNumber: String? = null
        val cardMatch = Regex("""(?:^|\s)(\d{4}|—)\s*$""").find(description)
        if (cardMatch != null) {
            val candidate = cardMatch.groupValues[1]
            if (candidate != "—") {
                cardNumber = candidate
                description = description.removeRange(
                    description.lastIndexOf(candidate).coerceAtLeast(0),
                    description.length,
                ).trim()
            }
        }
        description = description.trim().trimEnd('.').trim()

        val operation = ParsedOperation(
            timestamp = timestamp,
            processedTimestamp = processedDate,
            amountMinor = amount,
            currency = defaultCurrency,
            rawDescription = description,
            merchantName = MerchantNameExtractor.extract(description),
            documentId = null,
        )
        return operation to cardNumber
    }

    /**
     * Достаёт время операции и время списания из первой строки-продолжения.
     * Возвращает также остаток этой строки и остальных продолжений — это хвост описания.
     */
    private fun extractTimes(continuations: List<String>): Triple<String, String, String> {
        val first = continuations.firstOrNull()
            ?: return Triple("00:00", "00:00", "")
        val tokens = first.split(Regex("""\s+"""))
        val times = tokens.takeWhile { timeToken.matches(it) }
        val operationTime = times.getOrNull(0) ?: "00:00"
        val processedTime = times.getOrNull(1) ?: operationTime
        val rest = tokens.drop(times.size).joinToString(" ")
        val tail = (listOf(rest) + continuations.drop(1))
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return Triple(operationTime, processedTime, tail)
    }

    /** Отделяет номер карты (последний токен строки) от описания операции. */
    private fun splitCard(tail: String): Pair<String, String?> {
        val trimmed = tail.trim()
        if (trimmed.isEmpty()) return "" to null
        val tokens = trimmed.split(Regex("""\s+"""))
        val last = tokens.last()
        return when {
            cardNumber.matches(last) -> tokens.dropLast(1).joinToString(" ") to last
            last == "—" || last == "-" -> tokens.dropLast(1).joinToString(" ") to null
            else -> trimmed to null
        }
    }

    private fun currencyOf(symbol: String): String = when (symbol) {
        "$" -> "USD"
        "€" -> "EUR"
        "₸" -> "KZT"
        "₽", "\u0420", "P", "p" -> "RUB"
        else -> defaultCurrency
    }
    /** Служебные строки PDF (колонтитулы, шапка таблицы, итоги, подпись) — в операцию не входят. */
    private fun isPageFurniture(line: String): Boolean = PageFurniture.isFurniture(line)

    /** Строки страницы без пустых. */
    private fun String.toLines(): List<String> = split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Кириллическая «О» вместо нуля.
     *
     * Когда страницу распознают по картинке, движок регулярно выдаёт «О» или
     * «о» вместо нуля — в суммах это единственное место, где буква может
     * стоять вместо цифры.
     */
    private fun fixOcrDigits(raw: String): String =
        raw.replace('\u041E', '0').replace('\u043E', '0')

    protected companion object {
        /** Начало строки операции: дата и время (в одной строке). */
        val dateTimeStart = Regex(
            """^(\d{2}\.\d{2}\.\d{4})\s+(\d{2}:\d{2})""",
        )
        val dateOnly = Regex("""^\d{2}\.\d{2}\.\d{4}$""")
        val timeOnly = Regex("""^\d{2}:\d{2}$""")
        val timeToken = Regex("""^\d{1,2}:\d{2}$""")
        val datePattern = Regex("""\d{2}\.\d{2}\.\d{4}""")
        val timePattern = Regex("""\b\d{1,2}:\d{2}\b""")
        val amountPattern = Regex("""([+-]?\d{1,3}(?:[ ]\d{3})*(?:[.,]\d{2})|[+-]?\d+(?:[.,]\d{2}))""")

        /**
         * Знак валюты, стоящий отдельным словом: «-500.00 ₽ Оплата».
         *
         * Рубль при распознавании часто читается как «Р» (кириллическая Пе),
         * поэтому в наборе есть и этот вариант.
         */
        val currencyNoise = Regex(
            """\s(₽|\$|€|₸|\u0420|RUB|USD|EUR|KZT|BYN)\s""",
            RegexOption.IGNORE_CASE,
        )

        /** Знак валюты, прилипший к числу: «-500.00Р». */
        val attachedCurrency = Regex("""(?<=[\d.,])[₽€₸\u0420](?!\p{L})""")

        val cardNumber = Regex("""^\d{4}$""")

        /** Большой промежуток между колонками таблицы. */
        val columnGap = Regex("""\s{3,}""")

        /** Знаки валют, которые встречаются в выписках. */
        val currencySymbols = listOf("₽", "\u0420", "€", "$", "₸")

        /**
         * Структурированная строка операции: дата операции, дата списания, сумма в валюте
         * операции, сумма в валюте карты, описание и номер карты.
         *
         * В наборе знаков — варианты, которые даёт OCR вместо картинки:
         * `\u0420` — кириллическая «Р» вместо знака рубля, `\u041E`/`\u043E` —
         * кириллическая «О» вместо нуля в сумме, а в знаке минуса — тире
         * всех видов, которые движок читает вместо «-».
         */
        val structuredRow = Regex(
            """^(\d{2}\.\d{2}\.\d{4})\s+(\d{2}\.\d{2}\.\d{4})\s+([-+\u2014\u2013\u2212]?[\d][\d\s.,\u041E\u043E]*?)\s*([₽$€₸\u0420Pp])\s+([-+\u2014\u2013\u2212]?[\d][\d\s.,\u041E\u043E]*?)\s*([₽$€₸\u0420Pp])\s*(.*)$""",
        )

        /**
         * Та же строка операции, но без знака валюты после первой суммы.
         *
         * Движок OCR иногда не отдаёт знак рубля после первой суммы: в
         * распознанном тексте колонка выглядит как «+10 000.00» без «Р», и
         * строка операции целиком терялась, хотя вторая сумма разобрана.
         *
         * Знак у обеих сумм обязателен: он отделяет суммы от описания, и без
         * него строку невозможно разобрать однозначно. А вот знак у первой
         * суммы — можно: вторая сумма с ним разбирается всегда.
         */
        val structuredRowNoFirstCurrency = Regex(
            """^(\d{2}\.\d{2}\.\d{4})\s+(\d{2}\.\d{2}\.\d{4})\s+([-+\u2014\u2013\u2212][\d][\d\s.,\u041E\u043E]*?)(?:\s*([₽$€₸\u0420Pp]))?\s+([-+\u2014\u2013\u2212][\d][\d\s.,\u041E\u043E]*?)\s*([₽$€₸\u0420Pp])\s*(.*)$""",
        )
    }
}
