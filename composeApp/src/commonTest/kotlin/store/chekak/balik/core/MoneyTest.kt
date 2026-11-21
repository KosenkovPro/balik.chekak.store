package store.chekak.balik.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun formatsRubWithNonBreakingSpace() {
        assertEquals("1\u00A0234,56 ₽", formatMoney(123_456, "RUB"))
    }

    @Test
    fun formatsNegativeAmount() {
        assertEquals("−370,00 ₽", formatMoney(-37_000, "RUB"))
    }

    @Test
    fun formatsZeroDecimalsCurrency() {
        assertEquals("1\u00A0000 ¥", formatMoney(1_000, "JPY"))
    }

    @Test
    fun formatsShortAmount() {
        assertEquals("12 тыс. ₽", formatMoneyShort(1_234_000, "RUB"))
    }

    @Test
    fun percentOfIsSafeForZeroTotal() {
        assertEquals(0f, percentOf(100, 0))
    }

    @Test
    fun moneyArithmeticKeepsCurrency() {
        val sum = Money(100, "RUB") + Money(50, "RUB")
        assertEquals(150L, sum.minorUnits)
    }

    @Test
    fun absoluteRemovesSign() {
        assertEquals(500L, Money(-500).absolute().minorUnits)
    }

    @Test
    fun parsesPlainNumber() {
        assertEquals(100_000_00L, parseMoneyToMinor("100000"))
    }

    @Test
    fun parsesNumberWithCommaAndSpaces() {
        assertEquals(100_000_50L, parseMoneyToMinor("100 000,50"))
        assertEquals(100_000_50L, parseMoneyToMinor("100\u00A0000,50"))
    }

    @Test
    fun parsesNumberWithPoint() {
        assertEquals(100_000_50L, parseMoneyToMinor("100000.50"))
    }

    @Test
    fun parsesNumberWithoutDigitsAsNull() {
        assertEquals(null, parseMoneyToMinor(""))
        assertEquals(null, parseMoneyToMinor("abc"))
    }

    @Test
    fun respectsCurrencyWithoutDecimals() {
        assertEquals(100_000L, parseMoneyToMinor("100000", "JPY"))
    }
}

class Sha256Test {

    @Test
    fun matchesKnownVectors() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex("abc"),
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hex(""),
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex(
                "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq",
            ),
        )
    }

    @Test
    fun isStableForLongInput() {
        val input = "a".repeat(1000)
        val first = Sha256.hex(input)
        val second = Sha256.hex(input)
        assertEquals(first, second)
        assertEquals(64, first.length)
    }
}
