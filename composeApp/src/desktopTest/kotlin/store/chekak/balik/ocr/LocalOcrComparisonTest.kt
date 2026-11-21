package store.chekak.balik.ocr

import kotlinx.coroutines.test.runTest
import store.chekak.balik.parser.ParsedOperation
import store.chekak.balik.parser.ParsedStatement
import store.chekak.balik.parser.pdf.OcrLayout
import store.chekak.balik.parser.pdf.OcrPage
import store.chekak.balik.parser.pdf.OcrScript
import store.chekak.balik.parser.pdf.OcrWord
import store.chekak.balik.parser.pdf.OcrWordDump
import store.chekak.balik.parser.pdf.TBankPdfParser
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.test.Test

/**
 * Сравнение разборов одной и той же справки Т-Банка разными движками OCR:
 *
 *  1) эталон — текстовый слой PDF (то, что считается правильным ответом);
 *  2) приложение на Android — Tesseract 3.05, только классический движок;
 *  3) локально на компьютере — Tesseract 5.5 с LSTM-моделями;
 *  4) локально на компьютере — RapidOCR (PP-OCR от Baidu), отдельный движок.
 *
 * Все варианты распознавания разбираются **одним и тем же кодом**
 * (`OcrLayout` + `TBankPdfParser`), поэтому разница между колонками — только
 * в движке и моделях. Когда движков несколько, спор решает голосование
 * большинством: кто не совпал с большинством, тот и ошибся.
 */
class LocalOcrComparisonTest {

    @Test
    fun compareEngines() = runTest {
        val source = realFile(".secret/Справка о движении средств.pdf")
        val appText = realFile(".secret/ocr-scan.txt")
        val localDir = realFile(".tmp-ocr/local")
        if (source == null || appText == null || localDir == null) {
            println("OCR COMPARISON: нет данных — проверка пропущена")
            return@runTest
        }

        val parser = TBankPdfParser()
        val reference = parser.parse(source.readBytes(), source.name)

        // Текст приложения собираем заново из рамок слов: так видно, что даёт
        // текущая сборка строк, а не тот текст, что был выгружен раньше.
        val rebuiltAppText = androidTextFromBoxes(realFile(".tmp-ocr/ocr-words.txt"))
            ?: appText.readText()

        val engines = mutableListOf(
            Engine(
                title = "Android: Tesseract 5.5",
                note = "bytedeco, int-квантованные модели tessdata_best, rus+eng, рендер 300 dpi",
                statement = parser.parseText(rebuiltAppText),
            ),
            Engine(
                title = "Tesseract 5.5",
                note = "tessdata_best (LSTM), rus+eng, --psm 3",
                statement = parser.parseText(localText(localDir)),
            ),
        )

        for (rapid in listOf(
            ".tmp-ocr/rapid/words-cyrillic-v5.txt" to "RapidOCR PP-OCRv5",
            ".tmp-ocr/rapid/words-multi-v6.txt" to "RapidOCR PP-OCRv6",
        )) {
            val file = realFile(rapid.first) ?: continue
            if (file.length() < 100) continue
            engines += Engine(
                title = rapid.second,
                note = "PP-OCR, onnxruntime",
                statement = parser.parseText(rapidText(file)),
            )
        }

        val matched = match(reference.operations, engines.map { it.statement.operations })
        val matches = engines.map { it to matched }
        val stats = engines.indices.map { index -> stats(matched, index) }

        val summary = buildSummary(reference, engines, matches, stats)

        val outDir = realFile(".tmp-ocr")?.let { File(it, "comparison") }
        if (outDir != null) {
            if (!outDir.exists()) outDir.mkdirs()
            File(outDir, "operations.csv").writeText(csv(reference.operations, engines, matches))
            File(outDir, "summary.md").writeText(summary)
            File(outDir, "local-text.txt").writeText(localText(localDir))
            File(outDir, "android-text.txt").writeText(rebuiltAppText)
            File(outDir, "reference-ops.csv").writeText(opsCsv(reference.operations))
            for (engine in engines) {
                File(outDir, "ops-${slug(engine.title)}.csv").writeText(opsCsv(engine.statement.operations))
            }
            println("CSV: ${File(outDir, "operations.csv").absolutePath}")
        }
        println(summary)
    }

    // ---------------------------------------------------------------- сводка

    private fun buildSummary(
        reference: ParsedStatement,
        engines: List<Engine>,
        matches: List<Pair<Engine, List<Matched>>>,
        stats: List<Stats>,
    ): String {
        val matched = matches.firstOrNull()?.second ?: emptyList()
        val out = StringBuilder()
        out.appendLine("# Сравнение распознавания: эталон и разные движки OCR")
        out.appendLine()
        out.appendLine("Источник: справка Т-Банка, 20 страниц; эталон — текстовый слой PDF.")
        out.appendLine()
        out.appendLine("Как мерили:")
        out.appendLine()
        out.appendLine("- **Эталон** — текстовый слой того же PDF: то, что считается правильным ответом.")
        for (engine in engines) {
            out.appendLine("- **${engine.title}** — ${engine.note}.")
        }
        out.appendLine()
        out.appendLine("Все варианты разбираются **одним и тем же кодом** (`OcrLayout` + `TBankPdfParser`),")
        out.appendLine("поэтому разница между колонками — только в движке и моделях.")
        out.appendLine()

        // --- сводная таблица ---
        out.appendLine("| Показатель | Текстовый слой | ${engines.joinToString(" | ") { it.title }} |")
        out.appendLine("|---|---|${engines.joinToString("|") { "---" }}|")
        out.appendLine(
            row(
                "Операций",
                reference.operations.size,
                engines.map { it.statement.operations.size },
            ),
        )
        out.appendLine(
            row(
                "Нераспознанных строк",
                reference.unrecognizedRows,
                engines.map { it.statement.unrecognizedRows },
            ),
        )
        out.appendLine(row("Доходы, ₽", money(reference), engines.map { money(it.statement) }))
        out.appendLine(row("Расходы, ₽", money(reference, false), engines.map { money(it.statement, false) }))
        out.appendLine(
            row(
                "Остаток, ₽",
                reference.balanceMinor?.let { it / 100.0 },
                engines.map { it.statement.balanceMinor?.let { value -> value / 100.0 } },
            ),
        )
        out.appendLine(row("Карта", reference.cardLast4, engines.map { it.statement.cardLast4 }))
        out.appendLine(row("Период", period(reference), engines.map { period(it.statement) }))
        out.appendLine(
            row(
                "Описаний совпало дословно",
                "—",
                stats.map { "${it.exact} из ${matched.size}" },
            ),
        )
        out.appendLine(
            row(
                "Похожесть описаний (Левенштейн)",
                "—",
                stats.map { "${"%.1f".format(it.similarity * 100)}%" },
            ),
        )
        out.appendLine(
            row(
                "Операций без пары в разборе",
                "—",
                stats.map { it.withoutPair },
            ),
        )
        out.appendLine(
            row(
                "Суммы разошлись",
                "—",
                stats.map { it.sumDiff },
            ),
        )
        out.appendLine(
            row(
                "Средняя длина описания, символов",
                "—",
                stats.map { "%.1f".format(it.avgLength) },
            ),
        )
        out.appendLine(
            row(
                "Описаний с затесавшимся временем",
                "—",
                stats.map { it.withTime },
            ),
        )
        out.appendLine(
            row(
                "Операций без времени (00:00)",
                "—",
                stats.map { it.withoutTime },
            ),
        )

        // --- кто ошибся: голосование большинством ---
        out.appendLine()
        out.appendLine("## Кто ошибся: голосование большинством")
        out.appendLine()
        out.appendLine("Эталон — истина. Ошибкой считается расхождение, которое подтверждают")
        out.appendLine("другие движки: если движок один против остальных, ошибается он.")
        out.appendLine()
        out.appendLine("| Движок | Операций расходится с большинством | Из них сумма | Из них описание |")
        out.appendLine("|---|---|---|---|")
        for (index in matches.indices) {
            val engine = matches[index].first
            var amountDiff = 0
            var descriptionDiff = 0
            for (position in matched.indices) {
                val referenceOp = matched[position].reference
                val mine = matched[position].variants.getOrNull(index)
                val votesAmount = matches.indices.count { other ->
                    matched[position].variants.getOrNull(other)?.amountMinor == referenceOp.amountMinor
                }
                val votesDescription = matches.indices.count { other ->
                    matched[position].variants.getOrNull(other)?.rawDescription?.trim() ==
                        referenceOp.rawDescription.trim()
                }
                if (mine == null) {
                    amountDiff++
                    descriptionDiff++
                    continue
                }
                if (mine.amountMinor != referenceOp.amountMinor && votesAmount >= 2) amountDiff++
                if (mine.rawDescription.trim() != referenceOp.rawDescription.trim() && votesDescription >= 2) {
                    descriptionDiff++
                }
            }
            out.appendLine("| ${engine.title} | $amountDiff | $amountDiff | $descriptionDiff |")
        }

        // --- подробные расхождения по операциям ---
        out.appendLine()
        out.appendLine("## Расхождения по операциям")
        out.appendLine()
        for (index in matches.indices) {
            val engine = matches[index].first
            val problems = mutableListOf<String>()
            matches[index].second.forEachIndexed { position, pair ->
                val referenceOp = pair.reference
                val mine = pair.variants.getOrNull(index)
                val notes = buildList {
                    if (mine == null) add("операция не распознана")
                    else {
                        if (mine.amountMinor != referenceOp.amountMinor) {
                            add("сумма ${moneyOf(mine.amountMinor)} вместо ${moneyOf(referenceOp.amountMinor)}")
                        }
                        if (mine.rawDescription.trim() != referenceOp.rawDescription.trim()) {
                            add("описание «${mine.rawDescription.trim()}»")
                        }
                    }
                }
                if (notes.isNotEmpty()) {
                    problems += "| ${position + 1} | ${date(referenceOp)} | ${moneyOf(referenceOp.amountMinor)} | ${notes.joinToString("; ")} |"
                }
            }
            if (problems.isEmpty()) {
                out.appendLine("### ${engine.title}")
                out.appendLine()
                out.appendLine("Расхождений с эталоном нет.")
                out.appendLine()
            } else {
                out.appendLine("### ${engine.title}")
                out.appendLine()
                out.appendLine("| № | Дата | Сумма (эталон) | Что разошлось |")
                out.appendLine("|---|---|---|---|")
                problems.forEach { out.appendLine(it) }
                out.appendLine()
            }
        }

        // --- попарное сравнение движков ---
        out.appendLine("## Попарное сравнение движков")
        out.appendLine()
        out.appendLine("| Пара | Описаний совпало | Средняя похожесть |")
        out.appendLine("|---|---|---|")
        for (i in engines.indices) {
            for (j in i + 1 until engines.size) {
                var same = 0
                var total = 0.0
                var count = 0
                for (position in matches[i].second.indices) {
                    val left = matches[i].second[position].variants.getOrNull(i)?.rawDescription?.trim() ?: continue
                    val right = matches[j].second[position].variants.getOrNull(j)?.rawDescription?.trim() ?: continue
                    if (left.isEmpty() || right.isEmpty()) continue
                    if (left == right) same++
                    val longest = max(left.length, right.length)
                    total += 1.0 - levenshtein(left, right).toDouble() / longest
                    count++
                }
                val percent = if (count == 0) "—" else "%.1f".format(total / count * 100)
                out.appendLine("| ${engines[i].title} ↔ ${engines[j].title} | $same | $percent % |")
            }
        }

        // --- полная таблица ---
        out.appendLine()
        out.appendLine("## Полная таблица по операциям")
        out.appendLine()
        val headers = engines.joinToString(" | ") { it.title }
        val flags = engines.joinToString(" | ") { "совпал: ${it.title}" }
        out.appendLine("| № | Дата | Время | Сумма | Текстовый слой | $headers | $flags |")
        out.appendLine("|---|---|---|---|---|${engines.joinToString("|") { "---" }}|${engines.joinToString("|") { "---" }}|")
        for (position in matches[0].second.indices) {
            val pair = matches[0].second[position]
            val referenceOp = pair.reference
            val cells = engines.indices.joinToString(" | ") { index ->
                cell(matches[index].second.getOrNull(position)?.variants?.getOrNull(index)?.rawDescription?.trim() ?: "— НЕТ ПАРЫ —")
            }
            val marks = engines.indices.joinToString(" | ") { index ->
                val mine = matches[index].second.getOrNull(position)?.variants?.getOrNull(index)
                if (mine?.rawDescription?.trim() == referenceOp.rawDescription.trim()) "да" else "нет"
            }
            out.appendLine(
                "| ${position + 1} | ${date(referenceOp)} | ${time(referenceOp)} | ${moneyOf(referenceOp.amountMinor)} | " +
                    "${cell(referenceOp.rawDescription.trim())} | $cells | $marks |",
            )
        }
        return out.toString()
    }

    private fun row(name: String, reference: Any?, values: List<Any?>): String {
        val all = (listOf(reference) + values).joinToString(" | ") { (it ?: "—").toString() }
        return "| $name | $all |"
    }

    private fun cell(value: String): String =
        value.replace("|", "\\|").replace("\n", " ").ifBlank { "—" }

    private fun money(statement: ParsedStatement, income: Boolean = true): String {
        val sum = statement.operations
            .filter { if (income) it.amountMinor > 0 else it.amountMinor < 0 }
            .sumOf { if (income) it.amountMinor else -it.amountMinor }
        return "%,.2f".format(sum / 100.0).replace(',', ' ').replace('.', ',')
    }

    private fun moneyOf(minor: Long): String =
        "%,.2f".format(minor / 100.0).replace(',', ' ').replace('.', ',')

    private fun period(statement: ParsedStatement): String {
        val start = statement.periodStart?.let { LocalDate.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
        val end = statement.periodEnd?.let { LocalDate.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
        return if (start == null || end == null) "—" else "$start — $end"
    }

    // ------------------------------------------------------------ сопоставление

    private class Engine(
        val title: String,
        val note: String,
        val statement: ParsedStatement,
    )

    /** Операция эталона и её пара в каждом движке. */
    private class Matched(
        val reference: ParsedOperation,
        val variants: List<ParsedOperation?>,
    )

    /** Сводные числа по одному движку. */
    private class Stats(
        val exact: Int,
        val similarity: Double,
        val withoutPair: Int,
        val sumDiff: Int,
        val avgLength: Double,
        val withTime: Int,
        val withoutTime: Int,
    )

    /** Считает сводные числа по движку с заданным индексом. */
    private fun stats(pairs: List<Matched>, index: Int): Stats {
        var exact = 0
        var withoutPair = 0
        var sumDiff = 0
        var withTime = 0
        var withoutTime = 0
        var similarity = 0.0
        var compared = 0
        var length = 0L
        for (pair in pairs) {
            val reference = pair.reference.rawDescription.trim()
            val mine = pair.variants.getOrNull(index)
            if (mine == null) {
                withoutPair++
                continue
            }
            val other = mine.rawDescription.trim()
            if (other == reference) exact++
            if (mine.amountMinor != pair.reference.amountMinor) sumDiff++
            if (timeNoise.containsMatchIn(other)) withTime++
            if (mine.timestamp == 0L) withoutTime++
            length += other.length
            if (reference.isNotEmpty() && other.isNotEmpty()) {
                val longest = max(reference.length, other.length)
                similarity += 1.0 - levenshtein(reference, other).toDouble() / longest
                compared++
            }
        }
        return Stats(
            exact = exact,
            similarity = if (compared == 0) 0.0 else similarity / compared,
            withoutPair = withoutPair,
            sumDiff = sumDiff,
            avgLength = if (pairs.isEmpty()) 0.0 else length.toDouble() / pairs.size,
            withTime = withTime,
            withoutTime = withoutTime,
        )
    }

    /**
     * Пара операций по дате и сумме.
     *
     * Одинаковых по дате и сумме операций бывает несколько (три покупки по
     * 200 ₽ в один день), поэтому каждая операция сопоставляется только один
     * раз. Сопоставление идёт по индексу, а не по множеству: две операции с
     * одинаковыми датой, суммой и описанием — равные объекты, и множество
     * схлопнуло бы их в одну.
     */
    private fun match(
        reference: List<ParsedOperation>,
        variants: List<List<ParsedOperation>>,
    ): List<Matched> {
        val pools = variants.map { it.toMutableList() }
        return reference.map { op ->
            val key = "${date(op)}|${op.amountMinor}"
            val twins = pools.map { pool ->
                val index = pool.indexOfFirst { "${date(it)}|${it.amountMinor}" == key }
                if (index >= 0) pool.removeAt(index) else null
            }
            Matched(op, twins)
        }
    }

    /** Средняя похожесть описаний: доля совпавших символов после правок Левенштейна. */
    private fun similarity(pairs: List<Matched>): Double {
        var total = 0.0
        var count = 0
        for (pair in pairs) {
            val reference = pair.reference.rawDescription.trim()
            val other = pair.variants.firstOrNull()?.rawDescription?.trim() ?: continue
            if (reference.isEmpty() || other.isEmpty()) continue
            val longest = max(reference.length, other.length)
            total += 1.0 - levenshtein(reference, other).toDouble() / longest
            count++
        }
        return if (count == 0) 0.0 else total / count
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            System.arraycopy(current, 0, previous, 0, current.size)
        }
        return previous[b.length]
    }

    // ------------------------------------------------------------------ файлы

    /** CSV: по строке на операцию, все варианты распознавания рядом. */
    private fun csv(
        reference: List<ParsedOperation>,
        engines: List<Engine>,
        matches: List<Pair<Engine, List<Matched>>>,
    ): String {
        val builder = StringBuilder()
        val header = mutableListOf("№", "дата", "время", "сумма", "описание (текстовый слой)")
        engines.forEach { header += "описание (${it.title})" }
        engines.forEach { header += "совпал (${it.title})" }
        builder.appendLine(header.joinToString(";"))

        for (position in matches[0].second.indices) {
            val pair = matches[0].second[position]
            val referenceOp = pair.reference
            val row = mutableListOf(
                (position + 1).toString(),
                date(referenceOp),
                time(referenceOp),
                moneyOf(referenceOp.amountMinor),
                quote(referenceOp.rawDescription.trim()),
            )
            for (index in engines.indices) {
                val mine = matches[index].second.getOrNull(position)?.variants?.getOrNull(index)
                row += quote(mine?.rawDescription?.trim() ?: "— НЕТ ПАРЫ —")
            }
            for (index in engines.indices) {
                val mine = matches[index].second.getOrNull(position)?.variants?.getOrNull(index)
                row += if (mine?.rawDescription?.trim() == referenceOp.rawDescription.trim()) "да" else "нет"
            }
            builder.appendLine(row.joinToString(";"))
        }
        return builder.toString()
    }

    /** Список операций одним разбором: дата, время, сумма, описание. */
    private fun opsCsv(operations: List<ParsedOperation>): String {
        val builder = StringBuilder()
        builder.appendLine("дата;время;сумма;описание")
        for (op in operations) {
            builder.appendLine(
                listOf(
                    date(op),
                    time(op),
                    moneyOf(op.amountMinor),
                    quote(op.rawDescription.trim()),
                ).joinToString(";"),
            )
        }
        return builder.toString()
    }

    private fun quote(value: String) = "\"" + value.replace("\"", "\"\"").replace("\n", " ") + "\""

    private fun slug(title: String): String =
        title.lowercase().replace(Regex("[^a-zа-я0-9]+"), "-").trim('-')

    private fun date(op: ParsedOperation): String =
        Instant.ofEpochMilli(op.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun time(op: ParsedOperation): String =
        Instant.ofEpochMilli(op.timestamp).atZone(ZoneId.systemDefault()).toLocalTime().toString()

    /**
     * Рамки слов из файла.
     *
     * Два формата: дамп приложения (`OcrWordDump`) и выгрузка RapidOCR,
     * где после рамки идёт уверенность, а потом текст.
     */
    private fun readBoxes(file: File): Map<Int, List<OcrWord>> {
        val pages = mutableMapOf<Int, MutableList<OcrWord>>()
        for (line in file.readLines()) {
            val parsed = OcrWordDump.parse(line)
            if (parsed != null) {
                val (page, word) = parsed
                pages.getOrPut(page) { mutableListOf() } += word
                continue
            }
            val rapid = rapidWord(line) ?: continue
            pages.getOrPut(rapid.first) { mutableListOf() } += rapid.second
        }
        return pages
    }

    /** Строка RapidOCR: `страница|рамка|уверенность|текст`. */
    private fun rapidWord(line: String): Pair<Int, OcrWord>? {
        val parts = line.split('|', limit = 4)
        if (parts.size < 4) return null
        val pageIndex = parts[0].trim().toIntOrNull() ?: return null
        val box = parts[1].split(',').map { it.trim().toIntOrNull() ?: 0 }
        if (box.size < 4) return null
        val confidence = parts[2].trim().toFloatOrNull() ?: return null
        val text = parts[3].trim()
        if (text.isEmpty()) return null
        return pageIndex to OcrWord(
            text = text,
            left = box[0],
            top = box[1],
            right = box[2],
            bottom = box[3],
            confidence = confidence,
        )
    }

    /**
     * Текст приложения на Android, собранный заново из рамок слов.
     *
     * Файл `ocr-words.txt` — выгрузка отладочного дампа приложения: рамки
     * слов по страницам. Собираем из них строки тем же кодом, что и в
     * приложении, — так видно, что даёт текущая сборка строк.
     */
    private fun androidTextFromBoxes(file: File?): String? {
        if (file == null || !file.isFile) return null
        val pages = readBoxes(file)
        if (pages.isEmpty()) return null
        val ordered = pages.keys.sorted().map { index ->
            OcrPage(width = 1652, height = 2338, words = pages.getValue(index))
        }
        println("Android: пересобрано страниц=${ordered.size}, слов=${ordered.sumOf { it.words.size }}")
        return OcrScript.toText(ordered)
    }

    /** Текст RapidOCR: рамки найденных фрагментов собираются тем же кодом. */
    private fun rapidText(file: File): String {
        val pages = readBoxes(file)
        val ordered = pages.keys.sorted().map { index ->
            OcrPage(width = 1652, height = 2338, words = pages.getValue(index))
        }
        println("RapidOCR: страниц=${ordered.size}, фрагментов=${ordered.sumOf { it.words.size }}")
        return OcrLayout.toText(ordered)
    }

    /** Текст из локального распознавания: слова из TSV собираются тем же кодом. */
    private fun localText(dir: File): String {
        val pages = dir.listFiles { file -> file.name.endsWith(".tsv") }
            ?.sortedBy { it.name }
            ?.map { page ->
                val words = mutableListOf<OcrWord>()
                page.readLines().drop(1).forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size < 12) return@forEach
                    if (parts[0].trim() != "5") return@forEach
                    val text = parts[11].trim()
                    if (text.isEmpty()) return@forEach
                    val left = parts[6].toIntOrNull() ?: return@forEach
                    val top = parts[7].toIntOrNull() ?: return@forEach
                    val width = parts[8].toIntOrNull() ?: return@forEach
                    val height = parts[9].toIntOrNull() ?: return@forEach
                    words += OcrWord(text, left, top, left + width, top + height)
                }
                OcrPage(width = 1652, height = 2338, words = words)
            } ?: emptyList()
        println("локальный OCR: страниц=${pages.size}, слов=${pages.sumOf { it.words.size }}")
        return OcrLayout.toText(pages)
    }

    private fun realFile(name: String): File? {        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, name)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return null
    }

    private companion object {
        /** Время внутри описания: строка операции собралась с временем операции. */
        val timeNoise = Regex("""\d{1,2}:\d{2}""")
    }
}
