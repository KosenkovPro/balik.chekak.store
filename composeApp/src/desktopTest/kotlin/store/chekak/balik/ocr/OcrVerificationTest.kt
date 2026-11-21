package store.chekak.balik.ocr

import kotlinx.coroutines.test.runTest
import store.chekak.balik.parser.StatementComparison
import store.chekak.balik.parser.StatementComparison.toComparable
import store.chekak.balik.parser.pdf.OcrPage
import store.chekak.balik.parser.pdf.OcrScript
import store.chekak.balik.parser.pdf.OcrWord
import store.chekak.balik.parser.pdf.OcrWordDump
import store.chekak.balik.parser.pdf.TBankPdfParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Сверка распознанного текста с оригиналом на настоящих данных.
 *
 * Тот же расчёт, что приложение делает на телефоне: операции из скана
 * сопоставляются с операциями из выписки с текстовым слоем, а описания
 * сравниваются посимвольно. Проверяется, что движок сверки даёт те же числа,
 * что и настольный стенд, и что омоглифы больше не остаются.
 *
 * Без файлов проверка пропускается: файлы выписки и дампы не лежат в репозитории.
 */
class OcrVerificationTest {

    @Test
    fun recognizedTextMatchesOriginal() = runTest {
        val reference = realFile(".secret/Справка о движении средств.pdf")
        val dump = realFile(".tmp-ocr/ocr-words.txt")
        if (reference == null || dump == null) {
            println("OCR VERIFICATION: нет данных — проверка пропущена")
            return@runTest
        }

        val parser = TBankPdfParser()
        val referenceStatement = parser.parse(reference.readBytes(), reference.name)
        val recognizedStatement = parser.parseText(textFromDump(dump))

        val report = StatementComparison.compare(
            reference = referenceStatement.operations.map { it.toComparable() },
            actual = recognizedStatement.operations.map { it.toComparable() },
        )

        println(
            "OCR VERIFICATION: операций ${report.actualCount}, " +
                "сопоставлено ${report.matchedCount}, дословно ${report.exactCount}, " +
                "сходность ${"%.1f".format(report.averageSimilarity * 100)} %, " +
                "расхождений ${report.mismatchCount}",
        )

        assertEquals(473, report.actualCount, "распознано должно быть 473 операции")
        assertEquals(473, report.matchedCount, "каждая операция должна найти пару")
        assertEquals(457, report.exactCount, "дословно должны совпасть 457 описаний")
        assertEquals(16, report.mismatchCount, "расхождений должно остаться 16")
        assertTrue(report.averageSimilarity > 0.99f, "средняя сходность должна быть выше 99 %")

        // Омоглифы устранены: названия магазинов и городов записаны латиницей,
        // а русские слова — кириллицей. Раньше здесь был «РУАТЕВОСНКА».
        val exactTexts = report.rows
            .filter { it.status == store.chekak.balik.parser.ComparisonStatus.EXACT }
            .mapNotNull { it.reference?.description }
        assertTrue(
            exactTexts.any { it.contains("PYATEROCHKA") },
            "в списке должны быть описания с латинским PYATEROCHKA",
        )
        assertTrue(
            exactTexts.none { it.contains("РУАТЕВОСНКА") },
            "кириллических омоглифов остаться не должно",
        )
        assertTrue(
            exactTexts.any { it.contains("Отмена") },
            "русское слово «Отмена» должно читаться кириллицей",
        )
    }

    /** Текст собирается из дампа распознанных слов тем же кодом, что и в приложении. */
    private fun textFromDump(file: File): String {
        val pages = LinkedHashMap<Int, MutableList<OcrWord>>()
        for (line in file.readLines()) {
            val parsed = OcrWordDump.parse(line) ?: continue
            pages.getOrPut(parsed.first) { mutableListOf() }.add(parsed.second)
        }
        val ordered = pages.keys.sorted().map { index ->
            OcrPage(width = 1652, height = 2338, words = pages.getValue(index))
        }
        return OcrScript.toText(ordered)
    }

    /** Ищет файл от рабочего каталога вверх по дереву. */
    private fun realFile(name: String): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, name)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return null
    }
}
