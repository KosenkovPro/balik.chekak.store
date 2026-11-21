package store.chekak.balik.ocr

import kotlinx.coroutines.test.runTest
import store.chekak.balik.parser.ParsedOperation
import store.chekak.balik.parser.ParsedStatement
import store.chekak.balik.parser.pdf.OcrLayout
import store.chekak.balik.parser.pdf.OcrPage
import store.chekak.balik.parser.pdf.OcrWord
import store.chekak.balik.parser.pdf.TBankPdfParser
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.test.Test

/**
 * Оценка перебора конфигураций OCR.
 *
 * Каждый каталог в `.tmp-ocr/sweep/` — один прогон Tesseract: распознанные
 * страницы в виде TSV. Все прогоны разбираются **одним и тем же кодом**
 * (`OcrLayout` + `TBankPdfParser`), поэтому разница между конфигурациями —
 * только в движке, моделях и параметрах.
 *
 * Эталон — текстовый слой того же PDF: то, что считается правильным ответом.
 */
class OcrSweepScoreTest {

    @Test
    fun scoreSweep() = runTest {
        val source = realFile(".secret/Справка о движении средств.pdf")
        val sweepDir = realFile(".tmp-ocr/sweep")
        if (source == null || sweepDir == null) {
            println("SWEEP: нет данных — проверка пропущена")
            return@runTest
        }

        val parser = TBankPdfParser()
        val reference = parser.parse(source.readBytes(), source.name)

        val configs = sweepDir.listFiles { file ->
            file.isDirectory && !file.name.startsWith("_") && !file.name.startsWith(".")
        }?.sortedBy { it.name } ?: emptyList()

        if (configs.isEmpty()) {
            println("SWEEP: нет прогонов")
            return@runTest
        }

        val results = configs.map { dir ->
            val pages = readTsvPages(dir)
            val text = OcrLayout.toText(pages)
            val statement = parser.parseText(text)
            val scored = score(reference.operations, statement.operations)
            println("SWEEP ${dir.name}: операций=${statement.operations.size}, " +
                "дословно=${scored.exact}, сходство=${"%.1f".format(scored.similarity * 100)}%, " +
                "сумм-расхождений=${scored.sumDiff}")
            Scored(dir.name, statement, scored)
        }

        val sorted = results.sortedByDescending { it.scored.exact }
        val out = StringBuilder()
        out.appendLine("# Перебор конфигураций OCR")
        out.appendLine()
        out.appendLine("Источник: справка Т-Банка, 20 страниц. Эталон — текстовый слой PDF.")
        out.appendLine("Все прогоны разобраны одним и тем же кодом (`OcrLayout` + `TBankPdfParser`).")
        out.appendLine()
        out.appendLine("| Конфигурация | Операций | Дословно совпало | Сходство | Сумм-расхождений | Доходы, ₽ | Расходы, ₽ |")
        out.appendLine("|---|---|---|---|---|---|---|")
        for (item in sorted) {
            out.appendLine(
                "| ${item.name} | ${item.statement.operations.size} | ${item.scored.exact} | " +
                    "${"%.1f".format(item.scored.similarity * 100)}% | ${item.scored.sumDiff} | " +
                    "${money(item.statement, true)} | ${money(item.statement, false)} |",
            )
        }
        out.appendLine()
        out.appendLine("Эталон: операций ${reference.operations.size}, доходы ${money(reference, true)}, расходы ${money(reference, false)}.")

        // Подробные расхождения — по каждой конфигурации, где есть что показать.
        for (item in sorted) {
            val problems = item.scored.problems
            out.appendLine()
            out.appendLine("## ${item.name}")
            out.appendLine()
            if (problems.isEmpty()) {
                out.appendLine("Расхождений с эталоном нет.")
                continue
            }
            out.appendLine("| № | Дата | Сумма (эталон) | Что разошлось |")
            out.appendLine("|---|---|---|---|")
            problems.forEach { out.appendLine(it) }
        }

        File(sweepDir, "score.md").writeText(out.toString())
        File(sweepDir, "score.csv").writeText(
            buildString {
                appendLine("конфигурация;операций;дословно;сходство;сумм-расхождений")
                for (item in sorted) {
                    appendLine(
                        listOf(
                            item.name,
                            item.statement.operations.size,
                            item.scored.exact,
                            "%.3f".format(item.scored.similarity),
                            item.scored.sumDiff,
                        ).joinToString(";"),
                    )
                }
            },
        )
        println("SWEEP: отчёт ${File(sweepDir, "score.md").absolutePath}")
        println(out)
    }

    // ------------------------------------------------------------------ оценка

    private class Scored(
        val name: String,
        val statement: ParsedStatement,
        val scored: Score,
    )

    private class Score(
        val exact: Int,
        val similarity: Double,
        val sumDiff: Int,
        val problems: List<String>,
    )

    private fun score(reference: List<ParsedOperation>, variant: List<ParsedOperation>): Score {
        val pool = variant.toMutableList()
        var exact = 0
        var sumDiff = 0
        var similarity = 0.0
        var compared = 0
        val problems = mutableListOf<String>()
        var position = 0
        for (op in reference) {
            position++
            val key = "${date(op)}|${op.amountMinor}"
            val index = pool.indexOfFirst { "${date(it)}|${it.amountMinor}" == key }
            val mine = if (index >= 0) pool.removeAt(index) else null
            if (mine == null) {
                problems += "| $position | ${date(op)} | ${moneyOf(op.amountMinor)} | операция не распознана |"
                continue
            }
            val referenceText = op.rawDescription.trim()
            val myText = mine.rawDescription.trim()
            if (myText == referenceText) exact++
            if (mine.amountMinor != op.amountMinor) sumDiff++
            if (referenceText.isNotEmpty() && myText.isNotEmpty()) {
                val longest = max(referenceText.length, myText.length)
                similarity += 1.0 - levenshtein(referenceText, myText).toDouble() / longest
                compared++
            }
            if (myText != referenceText) {
                problems += "| $position | ${date(op)} | ${moneyOf(op.amountMinor)} | " +
                    "«$referenceText» → «$myText» |"
            }
        }
        return Score(
            exact = exact,
            similarity = if (compared == 0) 0.0 else similarity / compared,
            sumDiff = sumDiff,
            problems = problems,
        )
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

    /** Страницы из TSV Tesseract: слова пятого уровня. */
    private fun readTsvPages(dir: File): List<OcrPage> =
        dir.listFiles { file -> file.name.endsWith(".tsv") }
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

    private fun realFile(name: String): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, name)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return null
    }

    private fun date(op: ParsedOperation): String =
        Instant.ofEpochMilli(op.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun moneyOf(minor: Long): String =
        "%,.2f".format(minor / 100.0).replace(',', ' ').replace('.', ',')

    private fun money(statement: ParsedStatement, income: Boolean): String {
        val sum = statement.operations
            .filter { if (income) it.amountMinor > 0 else it.amountMinor < 0 }
            .sumOf { if (income) it.amountMinor else -it.amountMinor }
        return moneyOf(sum)
    }
}
