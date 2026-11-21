package store.chekak.balik.core

import kotlin.math.abs
import kotlin.math.pow

/**
 * Денежные суммы хранятся в минорных единицах (копейки для RUB), как того требует
 * целочисленная арифметика: никакой float-ошибки в итогах.
 */
data class Money(
    val minorUnits: Long,
    val currency: String = "RUB",
) {
    val isExpense: Boolean get() = minorUnits < 0
    val isIncome: Boolean get() = minorUnits > 0
    val isZero: Boolean get() = minorUnits == 0L

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Разные валюты: $currency и ${other.currency}" }
        return copy(minorUnits = minorUnits + other.minorUnits)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Разные валюты: $currency и ${other.currency}" }
        return copy(minorUnits = minorUnits - other.minorUnits)
    }

    fun absolute(): Money = copy(minorUnits = abs(minorUnits))

    companion object {
        fun ofMajor(amount: Double, currency: String = "RUB"): Money =
            Money(Math.round(amount * 100.0).toLong(), currency)

        fun zero(currency: String = "RUB") = Money(0L, currency)
    }
}

/** Сколько знаков после запятой у валюты. Валюты без минорных единиц учитываем отдельно. */
fun minorUnitDigits(currency: String): Int = when (currency.uppercase()) {
    "JPY", "KRW", "VND", "CLP", "ISK", "XOF", "XAF" -> 0
    "BHD", "JOD", "KWD", "OMR", "TND" -> 3
    else -> 2
}

private val currencySymbols = mapOf(
    "RUB" to "₽",
    "USD" to "$",
    "EUR" to "€",
    "KZT" to "₸",
    "BYN" to "Br",
    "GBP" to "£",
    "CNY" to "¥",
    "JPY" to "¥",
)

fun currencySymbol(currency: String): String = currencySymbols[currency.uppercase()] ?: currency.uppercase()

/**
 * Форматирует сумму в стиле русской локали: 1 234,56 ₽.
 * Разделитель тысяч — неразрывный пробел, десятичный — запятая.
 */
fun formatMoney(minorUnits: Long, currency: String = "RUB", withSymbol: Boolean = true): String {
    val digits = minorUnitDigits(currency)
    val negative = minorUnits < 0
    val absValue = abs(minorUnits)

    val whole = absValue / pow10(digits)
    val fraction = absValue % pow10(digits)

    val groupedWhole = groupThousands(whole.toString())
    val sign = if (negative) "−" else ""
    val fractionText = if (digits > 0) ",${fraction.toString().padStart(digits, '0')}" else ""
    val symbol = if (withSymbol) " ${currencySymbol(currency)}" else ""
    return "$sign$groupedWhole$fractionText$symbol"
}

fun formatMoney(money: Money, withSymbol: Boolean = true): String =
    formatMoney(money.minorUnits, money.currency, withSymbol)

/** Форматирует сумму компактно для графиков: 12,3 тыс. ₽ */
fun formatMoneyShort(minorUnits: Long, currency: String = "RUB"): String {
    val digits = minorUnitDigits(currency)
    val whole = abs(minorUnits) / pow10(digits)
    val sign = if (minorUnits < 0) "−" else ""
    return when {
        whole >= 1_000_000 -> "$sign${groupThousands((whole / 1_000_000).toString())} млн ${currencySymbol(currency)}"
        whole >= 1_000 -> "$sign${groupThousands((whole / 1_000).toString())} тыс. ${currencySymbol(currency)}"
        else -> "$sign${groupThousands(whole.toString())} ${currencySymbol(currency)}"
    }
}

/** Процент от целого с округлением до одного знака. */
fun percentOf(part: Long, total: Long): Float {
    if (total == 0L) return 0f
    return (part.toDouble() / total.toDouble() * 100.0).toFloat()
}

/**
 * Разбирает сумму, введённую пользователем, в минорные единицы.
 *
 * Понимает и «1000», и «1 000,50», и «1,000.50»: разделителем дробной части
 * может быть запятая или точка, пробелы и неразрывные пробелы отбрасываются.
 * Возвращает `null`, если в строке нет ни одной цифры.
 */
fun parseMoneyToMinor(text: String, currency: String = "RUB"): Long? {
    val cleaned = text.filter { it.isDigit() || it == ',' || it == '.' }
    if (cleaned.none { it.isDigit() }) return null

    val digits = minorUnitDigits(currency)
    val separator = cleaned.lastIndexOfAny(charArrayOf(',', '.'))
    val wholePart = if (separator < 0) cleaned else cleaned.substring(0, separator)
    val fractionPart = if (separator < 0) "" else cleaned.substring(separator + 1)

    val whole = wholePart.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val fraction = fractionPart.filter { it.isDigit() }.take(digits).padEnd(digits, '0')
    val fractionMinor = if (digits == 0) 0L else fraction.toLongOrNull() ?: 0L

    return whole * pow10(digits) + fractionMinor
}

private fun pow10(digits: Int): Long {
    var result = 1L
    repeat(digits) { result *= 10 }
    return result
}

private fun groupThousands(value: String): String {
    if (value.length <= 3) return value
    val sb = StringBuilder()
    val digits = value.length
    for ((i, ch) in value.withIndex()) {
        if (i > 0 && (digits - i) % 3 == 0) sb.append('\u00A0')
        sb.append(ch)
    }
    return sb.toString()
}
