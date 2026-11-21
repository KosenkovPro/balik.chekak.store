package store.chekak.balik.parser

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import store.chekak.balik.core.DateUtils
import store.chekak.balik.parser.csv.RaiffeisenCsvParser
import store.chekak.balik.parser.pdf.TBankPdfParser

class RaiffeisenCsvParserTest {

    private val parser = RaiffeisenCsvParser()

    private fun csv(): ByteArray {
        val header = "Дата операции;Выполнено банком;Номер документа;Поступления;Расходы;Валюта;" +
            "Детали операции (назначение платежа);Номер карты"
        val rows = listOf(
            "\"11.11.2026 15:46\";\"13.11.2026\";\"202639\";\"\";\"370,00\";\"RUB\";" +
                "\" Оплата покупки по карте. CARD **8872 11NOV RUB 370.00 TEST SHOP MOSCOW\";\"**8872\"",
            "\"09.11.2026 21:47\";\"09.11.2026\";\"875619\";\"\";\"30 000,00\";\"RUB\";" +
                "\" Перевод на номер 0079169773779. Получатель: Иван Иванов Через СБП.\";\"\"",
            "\"05.10.2026 00:00\";\"05.10.2026\";\"928051\";\"19 719,67\";\"\";\"RUB\";" +
                "\" Выплата авторского вознаграждения от АйТи-Альянс\";\"\"",
        )
        return (listOf(header) + rows).joinToString("\r\n").toByteArray(Charsets.UTF_8)
    }

    @Test
    fun detectsBankByHeader() {
        assertTrue(parser.canParse(String(csv()), "Выписка.csv"))
    }

    @Test
    fun parsesOperationsWithSigns() = runTest {
        val statement = parser.parse(csv(), "Выписка.csv")

        assertEquals(3, statement.operations.size)
        assertEquals("raiffeisen", statement.bankId)

        val purchase = statement.operations[0]
        assertEquals(-37_000L, purchase.amountMinor)
        assertEquals("**8872".filter { it.isDigit() }.takeLast(4), statement.cardLast4)

        val transfer = statement.operations[1]
        assertEquals(-3_000_000L, transfer.amountMinor)
        assertTrue(transfer.merchantName.contains("Иван Иванов"), transfer.merchantName)

        val income = statement.operations[2]
        assertEquals(1_971_967L, income.amountMinor)
        assertTrue(income.merchantName.contains("АйТи-Альянс"), income.merchantName)
    }

    @Test
    fun countsUnrecognizedRows() = runTest {
        val broken = "Дата операции;Расходы\n\"не дата\";\"100,00\""
        val statement = parser.parse(broken.toByteArray(), "broken.csv")
        assertEquals(0, statement.operations.size)
        assertEquals(1, statement.unrecognizedRows)
    }
}

class TBankPdfParserTest {

    private val parser = TBankPdfParser()

    /** Текст в том виде, в котором его отдаёт PDFBox (строки по позициям). */
    private val extractedText = """
        Исх. № 46406715
        АКЦИОНЕРНОЕ ОБЩЕСТВО «ТБАНК»
        Справка о движении средств
        18.11.2026
        О продукте
        Номер лицевого счета:   40817810900020627506
        Сумма доступного остатка на   18.11.2026:   14 709.49   ₽
        Движение средств за период с 18.11.2025 по 18.11.2026
        Дата и время
        операции
        Дата
        списания
        Сумма в валюте
        операции
        Сумма операции
        в валюте карты
        Описание
        операции
        Номер
        карты
        14.11.2026
        13:54
        14.11.2026
        13:56
        -786.00   ₽   -786.00   ₽   Оплата в Mobizap.ru   6915
        09.11.2026
        12:54
        09.11.2026
        13:12
        +2 512.00   ₽   +2 512.00   ₽   Возврат средств по оплате
        OP TRC Gagarinskij g
        Moskva RUS
        6915
        09.11.2026
        12:23
        09.11.2026
        12:24
        +3 000.00   ₽   +3 000.00   ₽   Перевод с договора
        5481986502
        6915
        АО «ТБанк» универсальная лицензия Банка России № 2673
    """.trimIndent()

    @Test
    fun detectsBank() {
        assertTrue(parser.canParse(extractedText, "Справка.pdf"))
    }

    @Test
    fun parsesHeaderBalanceAndPeriod() {
        val statement = parser.parseText(extractedText)
        assertEquals(1_470_949L, statement.balanceMinor)
        assertTrue(statement.periodStart != null && statement.periodEnd != null)
        assertEquals("Счёт 7506", statement.accountName)
    }

    @Test
    fun parsesOperationsWithSignsAndDescriptions() {
        val statement = parser.parseText(extractedText)
        assertEquals(3, statement.operations.size)

        val purchase = statement.operations[0]
        assertEquals(-78_600L, purchase.amountMinor)
        assertTrue(purchase.rawDescription.contains("Mobizap"), purchase.rawDescription)
        assertEquals("6915", statement.cardLast4)

        val refund = statement.operations[1]
        assertEquals(251_200L, refund.amountMinor)
        assertTrue(refund.rawDescription.contains("Возврат средств"), refund.rawDescription)

        val transfer = statement.operations[2]
        assertEquals(300_000L, transfer.amountMinor)
    }
}

/**
 * Реальная раскладка справки Т-Банка (JasperReports): строка операции — две даты,
 * две суммы и описание с номером карты, а время и хвост описания идут следующей строкой.
 */
class TBankPdfStructuredLayoutTest {

    private val parser = TBankPdfParser()

    private val extractedText = """
        Исх. № 46406715 18.11.2026
        Справка о движении средств
        Номер лицевого счета:  40817810900020627506
        Сумма доступного остатка на 18.11.2026: 14 709.49 ₽
        Движение средств за период с 18.11.2025 по 18.11.2026
        Дата и время Дата Сумма в валюте Сумма операции Описание Номер
        операции списания операции в валюте карты операции карты
        14.11.2026 14.11.2026 -786.00 ₽ -786.00 ₽ Оплата в Mobizap.ru 6915
        13:54 13:56
        09.11.2026 09.11.2026 +2 512.00 ₽ +2 512.00 ₽ Возврат средств по оплате 6915
        12:54 13:12 OP TRC Gagarinskij g
        Moskva RUS
        19.10.2026 19.10.2026 -1 000.00 ₽ -1 000.00 ₽ Внешний перевод по 6915
        12:46 12:46 номеру телефона
        +79680778991
        16.10.2026 16.10.2026 +55.00 ₽ +55.00 ₽ Кэшбэк за обычные —
        22:10 22:10 покупки
        АО «ТБанк» универсальная лицензия Банка России № 2673, к/с 30101810145250000974 в ГУ Банка России по ЦФО
        БИК 044525974 ИНН 7710140679 КПП 771301001
        1
        Пополнения: 524 291,28 ₽
        Расходы: 511 252,34 ₽
    """.trimIndent()

    @Test
    fun readsEveryOperationOfTheTable() {
        val statement = parser.parseText(extractedText)
        assertEquals(4, statement.operations.size, statement.operations.toString())
        assertEquals(0, statement.unrecognizedRows)
        assertEquals("6915", statement.cardLast4)
    }

    @Test
    fun readsAmountsWithSigns() {
        val statement = parser.parseText(extractedText)
        assertEquals(-78_600L, statement.operations[0].amountMinor)
        assertEquals(251_200L, statement.operations[1].amountMinor)
        assertEquals(-100_000L, statement.operations[2].amountMinor)
        assertEquals(5_500L, statement.operations[3].amountMinor)
    }

    @Test
    fun readsOperationAndProcessedTimes() {
        val statement = parser.parseText(extractedText)
        val operation = DateUtils.toLocalDateTime(statement.operations[0].timestamp)
        assertEquals(2026, operation.year)
        assertEquals(11, operation.monthNumber)
        assertEquals(14, operation.dayOfMonth)
        assertEquals(13, operation.hour)
        assertEquals(54, operation.minute)

        val processed = DateUtils.toLocalDateTime(statement.operations[0].processedTimestamp!!)
        assertEquals(13, processed.hour)
        assertEquals(56, processed.minute)
    }

    @Test
    fun joinsDescriptionContinuationAndDropsCardNumber() {
        val statement = parser.parseText(extractedText)

        val purchase = statement.operations[0]
        assertEquals("Оплата в Mobizap.ru", purchase.rawDescription)
        assertEquals("Mobizap.ru", purchase.merchantName)

        val refund = statement.operations[1]
        assertTrue(refund.rawDescription.contains("Возврат средств"), refund.rawDescription)
        assertTrue(refund.rawDescription.contains("Gagarinskij"), refund.rawDescription)
        assertTrue(refund.rawDescription.contains("Moskva RUS"), refund.rawDescription)

        // Номер карты уходит из описания, номер телефона — остаётся в тексте назначения.
        val transfer = statement.operations[2]
        assertEquals("Внешний перевод по номеру телефона +79680778991", transfer.rawDescription)
        assertEquals("Внешний перевод по номеру телефона", transfer.merchantName)
    }

    @Test
    fun dropsOperationWithoutCardNumber() {
        val statement = parser.parseText(extractedText)
        val cashback = statement.operations[3]
        assertEquals("Кэшбэк за обычные покупки", cashback.rawDescription)
        assertNull(cashback.documentId)
    }

    @Test
    fun pageFurnitureDoesNotLeakIntoDescriptions() {
        val statement = parser.parseText(extractedText)
        for (operation in statement.operations) {
            assertTrue(!operation.rawDescription.contains("БИК"), operation.rawDescription)
            assertTrue(!operation.rawDescription.contains("Пополнения"), operation.rawDescription)
            assertTrue(!operation.rawDescription.contains("универсальная лицензия"), operation.rawDescription)
        }
    }
}

class MerchantNameExtractorTest {

    @Test
    fun extractsMerchantFromCardPayment() {
        val name = MerchantNameExtractor.extract(
            "Оплата покупки по карте. CARD **8872 11NOV RUB 370.00 TEST SHOP MOSCOW",
        )
        assertTrue(name.contains("TEST SHOP"), name)
    }

    @Test
    fun extractsRecipient() {
        val name = MerchantNameExtractor.extract(
            "Перевод на номер 0079169773779. Получатель: Иван Иванов Через СБП.",
        )
        assertTrue(name.contains("Иван Иванов"), name)
    }
}
