package store.chekak.balik.parser

/**
 * Чистит назначение платежа и вытаскивает из него имя точки/получателя.
 *
 * Примеры из реальных выписок:
 *  * «Оплата покупки по карте. CARD **8872 11NOV RUB 370.00 LLC Stolle Capital Moskva»
 *    → «LLC Stolle Capital Moskva»
 *  * «Перевод с номера 0079169773779. Получатель: Марина Валериевна Через СБП.»
 *    → «Марина Валериевна Через СБП»
 */
object MerchantNameExtractor {

    private val cardPaymentPrefix = Regex(
        """^\s*(Оплата покупки по карте|Оплата товаров и услуг|Оплата в|Приём наличных)[.,]?\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val cardToken = Regex("""CARD\s+\*{0,2}\d{4}\s+\d{1,2}[A-Z]{3}\s+[A-Z]{3}\s+[\d\s.,]+""", RegexOption.IGNORE_CASE)
    private val recipientMarker = Regex("""Получатель\s*:\s*""", RegexOption.IGNORE_CASE)
    private val transferNoise = Regex(
        """(Перевод с номера|Перевод на номер|Пополнение с карты|Пополнение\. Система быстрых платежей|СБП|Система быстрых платежей|Внешний перевод|Внутренний перевод|Внутрибанковский перевод|Перевод с договора|Перевод собственных средств)""",
        RegexOption.IGNORE_CASE,
    )

    fun extract(rawDescription: String): String {
        var text = rawDescription.trim().replace('\u00A0', ' ')
        if (text.isEmpty()) return ""

        // Явный получатель — самый надёжный источник.
        recipientMarker.find(text)?.let { match ->
            val value = text.substring(match.range.last + 1).trim().trimEnd('.', ',')
            if (value.isNotEmpty()) return cleanTail(value)
        }

        // Оплата по карте: отбрасываем префикс и техническую часть с суммой.
        if (cardPaymentPrefix.containsMatchIn(text)) {
            text = cardPaymentPrefix.replace(text, "")
            text = cardToken.replace(text, "")
            text = text.trim().trimStart('.').trim()
            if (text.isNotEmpty()) return cleanTail(text)
        }

        // Перевод: убираем технический хвост про номер телефона/счёта.
        if (transferNoise.containsMatchIn(text)) {
            val cleaned = text
                .replace(Regex("""\+?\d[\d\s\-()]{8,}\d"""), "")
                .replace(Regex("""\b\d{10,16}\b"""), "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
                .trimEnd('.', ',')
            return cleanTail(cleaned)
        }

        return cleanTail(text)
    }

    private fun cleanTail(text: String): String =
        text.replace(Regex("""\s{2,}"""), " ")
            .replace(Regex("""[.,;+]+$"""), "")
            .trim()
}
