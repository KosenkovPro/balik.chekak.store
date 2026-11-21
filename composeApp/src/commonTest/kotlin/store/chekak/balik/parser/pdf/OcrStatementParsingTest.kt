package store.chekak.balik.parser.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Разбор выписки, распознанной по картинке.
 *
 * OCR читает знак рубля как «Р» (кириллическая Пе), а строки таблицы собирает
 * из слов по базовой линии. Здесь проверяется, что такой текст разбирается
 * так же, как текстовый слой PDF.
 */
class OcrStatementParsingTest {

    private val parser = TBankPdfParser()

    /** Кириллическая «Р» — так знак рубля читает OCR. */
    private val ruble = "\u0420"

    @Test
    fun recognizedRowWithLetterRubleIsParsed() {
        val text = "14.11.2026 14.11.2026 -786.00 $ruble -786.00 $ruble " +
            "Оплата в МоЬігар.гц 6915\n13:54 13:56"

        val statement = parser.parseText(text)

        assertEquals(1, statement.operations.size)
        val operation = statement.operations.first()
        assertEquals(-78600L, operation.amountMinor)
        assertEquals("RUB", operation.currency)
        assertEquals("6915", statement.cardLast4)
    }

    @Test
    fun recognizedRowsWithStandardRubleSignAreParsed() {
        val text = "14.11.2026 14.11.2026 -786.00 ₽ -786.00 ₽ " +
            "Оплата в PEREKRESTOK 6915\n13:54 13:56"

        val statement = parser.parseText(text)

        assertEquals(1, statement.operations.size)
        assertEquals(-78600L, statement.operations.first().amountMinor)
    }

    /**
     * Страницы разбираются по отдельности.
     *
     * Хвост описания операции внизу страницы не должен приклеиться к шапке
     * следующей страницы.
     */
    @Test
    fun pageSeparatorKeepsRowsOfDifferentPagesApart() {
        val text = "14.11.2026 14.11.2026 -786.00 $ruble -786.00 $ruble " +
            "Оплата 6915\n13:54 13:56" +
            OcrLayout.PAGE_SEPARATOR +
            "15.11.2026 15.11.2026 +100.00 $ruble +100.00 $ruble " +
            "Пополнение 6915\n10:00 10:01"

        val statement = parser.parseText(text)

        assertEquals(2, statement.operations.size)
        assertEquals(-78600L, statement.operations[0].amountMinor)
        assertEquals(10000L, statement.operations[1].amountMinor)
    }

    /**
     * Сквозной путь: слова со страницы → строки → операции.
     *
     * Слова расставлены как на настоящей странице выписки: две даты, две
     * суммы со знаком рубля, описание и номер карты, а ниже — время.
     */
    @Test
    fun wordsOfPageAreAssembledIntoParsedOperations() {
        val page = OcrPage(
            width = 1652,
            height = 2338,
            words = listOf(
                word("14.11.2026", left = 157, top = 137),
                word("14.11.2026", left = 351, top = 137),
                word("-786.00", left = 554, top = 137),
                word(ruble, left = 630, top = 137),
                word("-786.00", left = 818, top = 137),
                word(ruble, left = 894, top = 137),
                word("Оплата", left = 1083, top = 137),
                word("6915", left = 1388, top = 137),
                word("13:54", left = 157, top = 168),
                word("13:56", left = 351, top = 168),
            ),
        )

        val statement = parser.parseText(OcrLayout.toText(listOf(page)))

        assertEquals(1, statement.operations.size)
        val operation = statement.operations.first()
        assertEquals(-78600L, operation.amountMinor)
        assertEquals("RUB", operation.currency)
        assertEquals("6915", statement.cardLast4)
        assertTrue(operation.rawDescription.contains("Оплата"))
    }

    /**
     * OCR читает знак минуса как длинное тире.
     *
     * Если такое не распознать, операция теряется целиком, а расходы
     * считаются неверно.
     */
    @Test
    fun minusSignReadAsEmDashKeepsOperation() {
        val text = "16.10.2026 16.10.2026 \u2014500.00 Р -500.00 Р " +
            "Внешний перевод по 6915\n12:18 12:19"

        val statement = parser.parseText(text)

        assertEquals(1, statement.operations.size)
        assertEquals(-50000L, statement.operations.first().amountMinor)
    }

    /** OCR путает кириллическую «О» с нулём в сумме. */
    @Test
    fun letterOInsteadOfZeroInAmountIsFixed() {
        val text = "04.06.2026 04.06.2026 -1 924.4${ruble} -1 924.40${ruble} " +
            "Оплата в РУАТЕКОСНКА 6915\n20:10 20:11"

        val statement = parser.parseText(text)

        assertEquals(1, statement.operations.size)
        assertEquals(-192440L, statement.operations.first().amountMinor)
    }

    /**
     * Движок OCR иногда не отдаёт знак рубля после первой суммы.
     *
     * Колонка выглядит как «+10 000.00» без знака валюты. Раньше такая строка
     * не разбиралась вовсе, и операция терялась вместе с деньгами.
     */
    @Test
    fun rowWithoutCurrencySignAfterFirstAmountIsParsed() {
        val text = "09.10.2026 09.10.2026 +10 000.00 +10 000.00 $ruble " +
            "Пополнение. Система быстрых платежей 6915\n12:15 12:16"

        val statement = parser.parseText(text)

        assertEquals(1, statement.operations.size)
        val operation = statement.operations.first()
        assertEquals(1_000_000L, operation.amountMinor)
        assertEquals("RUB", operation.currency)
        assertEquals("6915", statement.cardLast4)
    }

    /**
     * Две такие строки подряд — две разные операции.
     *
     * Знак валюты у первой суммы не распознан, поэтому строка операции
     * определяется по двум датам и двум суммам.
     */
    @Test
    fun twoRowsWithoutFirstCurrencySignStaySeparateOperations() {
        val text = "09.10.2026 09.10.2026 +10 000.00 +10 000.00 $ruble " +
            "Пополнение 6915\n12:15 12:16\n" +
            "08.10.2026 08.10.2026 -500.00 -500.00 $ruble " +
            "Оплата в AUCHAN 6915\n11:00 11:01"

        val statement = parser.parseText(text)

        assertEquals(2, statement.operations.size)
        assertEquals(1_000_000L, statement.operations[0].amountMinor)
        assertEquals(-50_000L, statement.operations[1].amountMinor)
    }

    /**
     * Движок иногда читает время неверно: «00:09» превращается в «60:00».
     *
     * Раньше такая строка не разбиралась вовсе: операция терялась вместе с
     * деньгами, хотя сумма и описание были прочитаны верно.
     */
    @Test
    fun brokenTimeDoesNotLoseOperation() {
        val text = "23.04.2026 23.04.2026 -289.00 $ruble -289.00 $ruble " +
            "Оплата в GGS 6915\n60:00 03:42"

        val statement = parser.parseText(text)

        assertEquals(1, statement.operations.size)
        val operation = statement.operations.first()
        assertEquals(-28900L, operation.amountMinor)
        assertEquals("6915", statement.cardLast4)
        assertTrue(operation.rawDescription.contains("GGS"))
    }

    private fun word(text: String, left: Int, top: Int, width: Int = 90, height: Int = 17) =
        OcrWord(text = text, left = left, top = top, right = left + width, bottom = top + height)
}
