package store.chekak.balik.parser.text

/**
 * Разбор денежных сумм из выписок. Российские банки пишут «1 234,56», зарубежные — «1,234.56»,
 * в PDF встречается «-786.00 ₽» и «+2 512.00 ₽».
 */
object AmountParser {

    /**
     * Всё, что не цифра и не разделитель, — убираем.
     *
     * Тире разных видов оставляем: OCR часто читает знак минуса как длинное
     * тире, и без него сумма потеряла бы знак.
     */
    private val nonDigits = Regex("[^0-9,.\u2212\u2013\u2014-]")

    private val currencyNoise = Regex("[\\u00A0\\u202F\\u2009\\s]")

    /** Знаки, которые читаются как минус. */
    private const val MINUS_SIGNS = "-\u2212\u2013\u2014"

    /** Возвращает сумму в минорных единицах или null, если разобрать не удалось. */
    fun parseToMinorUnits(raw: String, minorDigits: Int = 2): Long? {
        val cleaned = raw.replace(currencyNoise, "").replace(nonDigits, "")
        if (cleaned.isEmpty()) return null

        val negative = cleaned.firstOrNull()?.let { it in MINUS_SIGNS } ?: false
        val unsigned = cleaned.trimStart('-', '+', '\u2212', '\u2013', '\u2014')

        val lastComma = unsigned.lastIndexOf(',')
        val lastDot = unsigned.lastIndexOf('.')
        val decimalSeparator = when {
            lastComma >= 0 && lastDot >= 0 -> if (lastComma > lastDot) ',' else '.'
            lastComma >= 0 -> {
                val digitsAfter = unsigned.length - lastComma - 1
                if (digitsAfter in 1..2) ',' else null
            }
            lastDot >= 0 -> {
                val digitsAfter = unsigned.length - lastDot - 1
                if (digitsAfter in 1..2) '.' else null
            }
            else -> null
        }

        val integerPart: String
        val fractionPart: String
        if (decimalSeparator != null) {
            val idx = unsigned.lastIndexOf(decimalSeparator)
            integerPart = unsigned.substring(0, idx).replace(",", "").replace(".", "")
            fractionPart = unsigned.substring(idx + 1).replace(",", "").replace(".", "")
        } else {
            integerPart = unsigned.replace(",", "").replace(".", "")
            fractionPart = ""
        }

        val whole = integerPart.toLongOrNull() ?: return null
        val fraction = if (fractionPart.isEmpty()) 0L else fractionPart.take(minorDigits).toLongOrNull() ?: 0L

        var minor = whole * pow10(minorDigits)
        for (i in 0 until minorDigits) {
            minor += when {
                i < fractionPart.length -> (fractionPart[i] - '0').toLong() * pow10(minorDigits - 1 - i)
                else -> 0L
            }
        }

        return if (negative) -minor else minor
    }

    /** Достаёт из текста первую сумму со знаком (для PDF-выписок Т-Банка). */
    fun findSignedAmount(text: String, minorDigits: Int = 2): Long? {
        val regex = Regex("""([+-]?\d{1,3}(?:[ \u00A0]\d{3})*(?:[.,]\d{1,2})?|[+-]?\d+(?:[.,]\d{1,2})?)""")
        for (match in regex.findAll(text)) {
            val value = parseToMinorUnits(match.groupValues[1], minorDigits) ?: continue
            if (match.groupValues[1].startsWith("+") || match.groupValues[1].startsWith("-")) return value
        }
        return null
    }

    private fun pow10(digits: Int): Long {
        var result = 1L
        repeat(digits) { result *= 10 }
        return result
    }
}
