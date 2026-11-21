package store.chekak.balik.ocr

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import store.chekak.balik.parser.pdf.OcrLayout
import store.chekak.balik.parser.pdf.TBankPdfParser

/**
 * Проверка качества OCR на настоящей справке.
 *
 * Сравнивается текст, распознанный по картинке страниц, с текстовым слоем того
 * же PDF: операций должно быть столько же, а суммы — совпадать до копейки.
 *
 * Файлы лежат в `.secret/` и в репозиторий не попадают, поэтому тест молча
 * пропускается, если их нет (например, на CI).
 */
class OcrStatementAccuracyTest {

    @Test
    fun recognizedScanMatchesTextLayer() = runTest {
        val source = realFile(".secret/Справка о движении средств.pdf")
        val recognized = realFile(".secret/ocr-scan.txt")
        if (source == null || recognized == null) {
            println("OCR ACCURACY: нет файлов в .secret — проверка пропущена")
            return@runTest
        }

        val parser = TBankPdfParser()
        val reference = parser.parse(source.readBytes(), source.name)
        val fromScan = parser.parseText(recognized.readText())

        val referenceIncome = reference.operations.filter { it.amountMinor > 0 }.sumOf { it.amountMinor }
        val referenceExpenses = reference.operations.filter { it.amountMinor < 0 }.sumOf { -it.amountMinor }
        val scanIncome = fromScan.operations.filter { it.amountMinor > 0 }.sumOf { it.amountMinor }
        val scanExpenses = fromScan.operations.filter { it.amountMinor < 0 }.sumOf { -it.amountMinor }

        println(
            "OCR ACCURACY: операций ${fromScan.operations.size} против ${reference.operations.size}, " +
                "доход $scanIncome против $referenceIncome, расход $scanExpenses против $referenceExpenses",
        )

        assertEquals(reference.operations.size, fromScan.operations.size)
        assertEquals(referenceIncome, scanIncome)
        assertEquals(referenceExpenses, scanExpenses)
        assertEquals(reference.cardLast4, fromScan.cardLast4)
        assertEquals(reference.periodStart, fromScan.periodStart)
        assertEquals(reference.periodEnd, fromScan.periodEnd)
    }

    /**
     * Распознанный текст должен собираться в строки таблицы: одна строка — одна
     * операция, а не смесь колонок.
     */
    @Test
    fun recognizedTextKeepsTableRows() = runTest {
        val source = realFile(".secret/Справка о движении средств.pdf")
        val recognized = realFile(".secret/ocr-scan.txt")
        if (source == null || recognized == null) {
            println("OCR ACCURACY: нет файлов в .secret — проверка пропущена")
            return@runTest
        }
        val pages = recognized.readText().split(OcrLayout.PAGE_SEPARATOR)

        val operationLines = pages.sumOf { page ->
            page.split('\n').count { line ->
                Regex("""^\d{2}\.\d{2}\.\d{4}\s+\d{2}\.\d{2}\.\d{4}\s+[-+—–−]?[\d]""").containsMatchIn(line.trim())
            }
        }
        val expected = TBankPdfParser().parse(source.readBytes(), source.name).operations.size

        println("OCR ACCURACY: строк операций в распознанном тексте — $operationLines, в эталоне — $expected")
        assertEquals(expected, operationLines)
    }

    private fun realFile(name: String): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, name)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        return null
    }
}
