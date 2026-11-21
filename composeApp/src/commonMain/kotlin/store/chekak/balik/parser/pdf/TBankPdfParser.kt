package store.chekak.balik.parser.pdf

import store.chekak.balik.core.DateUtils
import store.chekak.balik.parser.text.AmountParser

/**
 * Парсер справки о движении средств Т-Банка (PDF).
 *
 * Шапка справки содержит: номер лицевого счёта, сумму доступного остатка
 * и период движения средств.
 */
class TBankPdfParser : PdfStatementParser() {

    override val bankId: String = "tbank"
    override val bankDisplayName: String = "Т-Банк"

    override fun canParse(sampleText: String, fileName: String): Boolean {
        val sample = sampleText.uppercase()
        return sample.contains("ТБАНК") ||
            sample.contains("СПРАВКА О ДВИЖЕНИИ СРЕДСТВ") ||
            sample.contains("АО «ТБАНК»")
    }

    override fun parseHeader(lines: List<String>): Header {
        val accountNumber = lines.firstOrNull { it.startsWith("Номер лицевого счета", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val balance = lines.firstOrNull { it.contains("доступного остатка", ignoreCase = true) }
            ?.let { line ->
                val afterColon = line.substringAfter(":", missingDelimiterValue = "")
                Regex("""([\d][\d\s]*[.,]\d{2})""").find(afterColon)
                    ?.groupValues?.get(1)
                    ?.let { AmountParser.parseToMinorUnits(it) }
            }

        val periodLine = lines.firstOrNull { it.contains("за период", ignoreCase = true) }
        val periodMatch = periodLine?.let {
            Regex("""с\s+(\d{2}\.\d{2}\.\d{4})\s+по\s+(\d{2}\.\d{2}\.\d{4})""").find(it)
        }
        val periodStart = periodMatch?.groupValues?.get(1)?.let { DateUtils.parseDateTime(it) }
        val periodEnd = periodMatch?.groupValues?.get(2)?.let { DateUtils.parseDateTime(it) }

        return Header(
            accountName = accountNumber?.let { "Счёт ${it.takeLast(4)}" } ?: "Счёт Т-Банка",
            balanceMinor = balance,
            periodStart = periodStart,
            periodEnd = periodEnd,
        )
    }
}
