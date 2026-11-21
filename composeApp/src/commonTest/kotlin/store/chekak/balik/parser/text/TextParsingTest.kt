package store.chekak.balik.parser.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import store.chekak.balik.parser.MerchantNameExtractor

class AmountParserTest {

    @Test
    fun parsesRussianFormatWithSpaces() {
        assertEquals(123_456L, AmountParser.parseToMinorUnits("1 234,56"))
    }

    @Test
    fun parsesRussianFormatWithNonBreakingSpace() {
        assertEquals(3_000_000L, AmountParser.parseToMinorUnits("30\u00A0000,00"))
    }

    @Test
    fun parsesPlainInteger() {
        assertEquals(37_000L, AmountParser.parseToMinorUnits("370,00"))
    }

    @Test
    fun parsesNegativeAmount() {
        assertEquals(-78_600L, AmountParser.parseToMinorUnits("-786.00"))
    }

    @Test
    fun parsesAmountWithCurrencySymbol() {
        assertEquals(251_200L, AmountParser.parseToMinorUnits("+2 512.00 ₽"))
    }

    @Test
    fun parsesEnglishFormat() {
        assertEquals(123_456L, AmountParser.parseToMinorUnits("1,234.56"))
    }

    @Test
    fun returnsNullForGarbage() {
        assertNull(AmountParser.parseToMinorUnits("—"))
        assertNull(AmountParser.parseToMinorUnits(""))
    }

    @Test
    fun findsSignedAmountInText() {
        val value = AmountParser.findSignedAmount("Оплата в Mobizap.ru -786.00 ₽")
        assertNotNull(value)
        assertEquals(-78_600L, value)
    }
}

class CharsetDecoderTest {

    @Test
    fun detectsUtf8WithBom() {
        val text = "Дата операции"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)
        val decoded = CharsetDecoder.decode(bytes)
        assertEquals(CharsetDecoder.Encoding.UTF8, decoded.encoding)
        assertEquals(text, decoded.text)
    }

    @Test
    fun fallsBackToCp1251() {
        // «Расходы» в Windows-1251.
        val bytes = byteArrayOf(
            0xD0.toByte(), 0xE0.toByte(), 0xF1.toByte(), 0xF5.toByte(),
            0xEE.toByte(), 0xE4.toByte(), 0xFB.toByte(),
        )
        val decoded = CharsetDecoder.decode(bytes)
        assertEquals(CharsetDecoder.Encoding.CP1251, decoded.encoding)
        assertEquals("Расходы", decoded.text)
    }
}

class CsvReaderTest {

    @Test
    fun parsesQuotedFieldsWithSemicolon() {
        val csv = "Дата;Сумма\n\"01.11.2026 10:00\";\"370,00\""
        val table = CsvReader.parse(csv)
        assertEquals(listOf("Дата", "Сумма"), table.header)
        assertEquals(1, table.rows.size)
        assertEquals("01.11.2026 10:00", table.rows[0][0])
        assertEquals("370,00", table.rows[0][1])
    }

    @Test
    fun handlesEscapedQuotes() {
        val csv = "a;b\n\"x\"\"y\";\"z\""
        val table = CsvReader.parse(csv)
        assertEquals("x\"y", table.rows[0][0])
    }

    @Test
    fun detectsCommaDelimiter() {
        val table = CsvReader.parse("a,b,c\n1,2,3")
        assertEquals(',', table.delimiter)
        assertEquals(3, table.header.size)
    }

    @Test
    fun handlesEmptyLines() {
        val table = CsvReader.parse("a;b\n\n1;2\n")
        assertEquals(1, table.rows.size)
    }
}

class MerchantNameExtractorTest {

    @Test
    fun extractsMerchantFromCardPayment() {
        val name = MerchantNameExtractor.extract(
            "Оплата покупки по карте. CARD **8872 11NOV RUB 370.00 LLC Stolle Capital Moskva",
        )
        assertTrue(name.contains("Stolle Capital"), "Ожидалось имя точки, получено: $name")
    }

    @Test
    fun extractsRecipient() {
        val name = MerchantNameExtractor.extract(
            "Перевод с номера 0079169773779. Получатель: Марина Валериевна Через СБП.",
        )
        assertTrue(name.startsWith("Марина"), "Ожидался получатель, получено: $name")
    }
}
