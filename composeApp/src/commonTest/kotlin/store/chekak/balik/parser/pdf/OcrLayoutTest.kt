package store.chekak.balik.parser.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Проверка сборки распознанных слов в строки.
 *
 * Движок OCR отдаёт слова с рамками; парсеру выписки нужен текст, разбитый на
 * строки, — как если бы страница имела текстовый слой. Здесь проверяется именно
 * эта «склейка»: слова одной строки таблицы должны попасть в одну строку, а
 * соседние строки — в разные.
 */
class OcrLayoutTest {

    private fun word(text: String, left: Int, top: Int = 100, width: Int = 60, height: Int = 20) =
        OcrWord(text = text, left = left, top = top, right = left + width, bottom = top + height)

    @Test
    fun emptyPageProducesNoLines() {
        assertEquals(emptyList(), OcrLayout.toLines(OcrPage(100, 100, emptyList())))
    }

    @Test
    fun wordsOnSameBaselineJoinIntoOneLine() {
        val page = OcrPage(
            width = 800,
            height = 600,
            words = listOf(
                word("14.10.2026", left = 40),
                word("14.10.2026", left = 140),
                word("-1", left = 240),
                word("364.72", left = 310),
            ),
        )

        val lines = OcrLayout.toLines(page)

        assertEquals(1, lines.size)
        assertEquals("14.10.2026 14.10.2026 -1 364.72", lines.first())
    }

    @Test
    fun wordsOnDifferentRowsBecomeDifferentLines() {
        val page = OcrPage(
            width = 800,
            height = 600,
            words = listOf(
                word("первая", left = 40, top = 100),
                word("вторая", left = 40, top = 140),
                word("третья", left = 40, top = 180),
            ),
        )

        val lines = OcrLayout.toLines(page)

        assertEquals(3, lines.size)
        assertEquals(listOf("первая", "вторая", "третья"), lines)
    }

    /**
     * Страница выписки: много строк таблицы.
     *
     * Раньше полоса строки расширялась по мере добавления слов и в итоге
     * «съедала» всю страницу: вместо десятков строк получалась одна.
     */
    @Test
    fun manyRowsOfTableStaySeparateLines() {
        val words = buildList {
            for (row in 0 until 10) {
                val top = 100 + row * 40
                add(word("$row", left = 40, top = top))
                add(word("сумма", left = 300, top = top))
                add(word("6915", left = 600, top = top))
            }
        }

        val lines = OcrLayout.toLines(OcrPage(800, 600, words))

        assertEquals(10, lines.size)
        // Колонки таблицы разнесены пробелами — так разбор их различает.
        assertEquals("0   сумма   6915", lines.first())
        assertEquals("9   сумма   6915", lines.last())
    }

    @Test
    fun bigHorizontalGapKeepsTableColumnsApart() {
        val page = OcrPage(
            width = 800,
            height = 600,
            words = listOf(
                word("дата", left = 40),
                word("сумма", left = 400),
            ),
        )

        val line = OcrLayout.toLines(page).single()

        // Колонки разделяются несколькими пробелами — так их видно в разборе.
        assertTrue(line.contains("   "), "ожидался широкий разрыв между колонками, было: $line")
        assertTrue(line.startsWith("дата"))
        assertTrue(line.endsWith("сумма"))
    }

    @Test
    fun wordsAreSortedLeftToRightInsideLine() {
        val page = OcrPage(
            width = 800,
            height = 600,
            words = listOf(
                word("третий", left = 240),
                word("первый", left = 40),
                word("второй", left = 140),
            ),
        )

        val line = OcrLayout.toLines(page).single()

        assertEquals("первый второй третий", line)
    }

    @Test
    fun blankWordsAreIgnored() {
        val page = OcrPage(
            width = 800,
            height = 600,
            words = listOf(
                word("оплата", left = 40),
                word("   ", left = 140),
            ),
        )

        assertEquals(listOf("оплата"), OcrLayout.toLines(page))
    }

    @Test
    fun textOfSeveralPagesIsJoinedWithPageSeparator() {
        val pages = listOf(
            OcrPage(800, 600, listOf(word("страница", left = 40, top = 100))),
            OcrPage(800, 600, listOf(word("вторая", left = 40, top = 100))),
        )

        assertEquals(
            "страница${OcrLayout.PAGE_SEPARATOR}вторая",
            OcrLayout.toText(pages),
        )
    }

    @Test
    fun slightlyShiftedWordsStillJoinIntoOneLine() {
        // В реальном распознавании слова одной строки немного «плавают» по высоте.
        val page = OcrPage(
            width = 800,
            height = 600,
            words = listOf(
                word("сумма", left = 40, top = 100, height = 18),
                word("1 234,56", left = 200, top = 104, height = 18),
            ),
        )

        assertEquals(1, OcrLayout.toLines(page).size)
    }

    /**
     * Наклон страницы не учитывается: выгрузки банков всегда выровнены.
     *
     * Слова, базовые линии которых расходятся больше вертикального допуска,
     * попадают в разные строки — так и должно быть без поправки на наклон.
     */
    @Test
    fun wordsBeyondVerticalToleranceBecomeDifferentLines() {
        val page = OcrPage(
            width = 800,
            height = 600,
            words = listOf(
                word("дата", left = 40, top = 100, height = 18),
                // Базовая линия ниже на 30 px — это уже другая строка.
                word("сумма", left = 300, top = 130, height = 18),
            ),
        )

        assertEquals(2, OcrLayout.toLines(page).size)
    }

    /**
     * Движок измеряет рамку глифа неточно: у знака рубля рамка получается
     * выше остальных.
     *
     * Раньше такое слово отрывалось от своей строки (его низ уходил ниже),
     * и строка операции разваливалась: время операции уезжало в описание.
     * Середина рамки к такому слову устойчива.
     */
    /**
     * Движок разрывает один номер карты на два слова с перекрытыми рамками.
     *
     * Пробела между ними нет: номер карты пишется слитно — «220220******3715».
     */
    @Test
    fun splitNumberIsJoinedWithoutSpace() {
        val page = OcrPage(
            width = 800,
            height = 600,
            words = listOf(
                word("09.10.2026", left = 40, top = 100),
                word("220220******", left = 110, top = 100, width = 370),
                // Рамка второго слова заходит на первое — движок ошибся.
                word("3715", left = 250, top = 100, width = 80),
            ),
        )

        val line = OcrLayout.toLines(page).single()

        assertEquals("09.10.2026 220220******3715", line)
    }

    /**
     * Знак рубля отстоит от суммы, но рамки у них перекрываются.
     *
     * Пробел здесь нужен: сумма и знак — разные слова.
     */
    @Test
    fun rubleSignKeepsSpaceAfterAmount() {
        val page = OcrPage(
            width = 800,
            height = 600,
            words = listOf(
                word("-300.00", left = 40, top = 100, width = 120),
                word("Р", left = 120, top = 100, width = 20),
            ),
        )

        val line = OcrLayout.toLines(page).single()

        assertEquals("-300.00 Р", line)
    }

    @Test
    fun wordWithOversizedBoxStaysInItsLine() {
        val words = buildList {
            val top = 100
            add(word("09.10.2026", left = 40, top = top, height = 17))
            add(word("+10 000.00", left = 200, top = top, height = 17))
            // Знак рубля: рамка выше в два раза, низ уходит за строку.
            add(word("Р", left = 320, top = top - 4, height = 31))
            add(word("Пополнение", left = 400, top = top, height = 17))
            // Следующая строка — время операции.
            add(word("18:20", left = 40, top = top + 31, height = 17))
            add(word("18:24", left = 200, top = top + 31, height = 17))
        }

        val lines = OcrLayout.toLines(OcrPage(800, 600, words))

        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("Р"), "знак рубля должен остаться в своей строке: ${lines[0]}")
        assertEquals("09.10.2026   +10 000.00   Р Пополнение", lines[0])
        assertEquals("18:20   18:24", lines[1])
    }
}
