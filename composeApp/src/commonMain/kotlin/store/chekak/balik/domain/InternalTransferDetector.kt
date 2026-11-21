package store.chekak.balik.domain

import store.chekak.balik.core.Sha256

/**
 * Сколько ошибок в буквах допускается при сравнении с образцом.
 *
 * Двух достаточно: движок распознавания теряет букву или ставит вместо неё
 * знак препинания.
 */
private const val MAX_MISTAKES = 2

/**
 * Определение переводов между своими счетами.
 *
 * Работает по двум признакам:
 *  1) в описании операции встречается номер моей карты/счёта (из настроек);
 *  2) описание содержит типовую формулировку перевода самому себе.
 *
 * Внешние переводы по номеру телефона (например, «Перевод с номера +7968…») такими
 * правилами не считаются: это деньги от другого человека, а не движение между
 * своими счетами.
 */
class InternalTransferDetector(
    ownNumbers: List<String> = emptyList(),
) {
    /** Нормализованные номера моих счетов (только цифры). */
    private val ownNumbers: List<String> = ownNumbers
        .map { it.filter { ch -> ch.isDigit() } }
        .filter { it.length >= 4 }

    private val ownSuffixes: List<String> = ownNumbers.map { it.takeLast(4) }.distinct()

    /**
     * Формулировки, которые прямо означают движение между своими счетами.
     */
    private val selfTransferPatterns = listOf(
        "перевод с карты",
        "пополнение с карты",
        "перевод между своими счетами",
        "перевод самому себе",
        "перевод себе",
        "на свою карту",
        "с карты на карту",
        "перевод со счета",
        "перевод с договора",
        "перевод между счетами",
        "пополнение счета с карты",
        "own transfer",
        "self transfer",
    )

    /** Слова, по которым операция вообще похожа на перевод. */
    private val transferKeywords = listOf(
        "перевод",
        "пополнение",
        "зачисление",
        "с карты",
        "на карту",
        "transfer",
        "top-up",
        "top up",
        "replenishment",
    )

    fun detect(description: String, amountMinor: Long = 0): Boolean {
        val text = description.lowercase().replace('\u00A0', ' ')
        if (text.isBlank()) return false

        if (selfTransferPatterns.any { pattern -> matches(text, pattern) }) return true

        // Перевод + упоминание моего номера карты/счёта.
        if (!transferKeywords.any { keyword -> text.contains(keyword) }) return false
        return mentionsOwnNumber(text)
    }

    /**
     * Совпадает ли образец с текстом.
     *
     * Сначала проверяется точное вхождение. Если оно не нашлось и в образце
     * есть одно-буквенное слово («перевод **с** карты»), сравнение идёт по
     * буквам с допуском на огрехи распознавания: движок склеивает слова
     * («Переводсдоговора» вместо «Перевод с договора») и путает буквы со
     * знаками («Перевод (: договора»).
     */
    private fun matches(text: String, pattern: String): Boolean {
        if (text.contains(pattern)) return true
        if (!pattern.split(' ').any { it.length == 1 }) return false
        return matchesRoughly(text, pattern)
    }

    /**
     * Совпадение по буквам с допуском в [MAX_MISTAKES] ошибок.
     *
     * Пробелы и знаки, которые движок не смог прочитать, отбрасываются: важны
     * только буквы. Допуск в две ошибки покрывает и потерянную букву, и
     * заменённую на знак препинания.
     */
    private fun matchesRoughly(text: String, pattern: String): Boolean {
        val source = text.filter { it.isLetter() }
        val needle = pattern.filter { it.isLetter() }
        if (needle.isEmpty()) return false

        for (start in 0..source.length) {
            val end = minOf(source.length, start + needle.length)
            val slice = source.substring(start, end)
            if (slice.length < needle.length - MAX_MISTAKES) continue
            if (editDistance(slice, needle) <= MAX_MISTAKES) return true
        }
        return false
    }

    /** Расстояние Левенштейна между двумя строками. */
    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        val current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            previous = current.copyOf()
        }
        return previous[b.length]
    }

    /** Упоминание ли моих карт или счетов в описании операции. */
    private fun mentionsOwnNumber(text: String): Boolean {
        val sequences = extractDigitSequences(text)
        for (sequence in sequences) {
            for (own in ownNumbers) {
                if (sequence == own) return true
                if (own.length >= 6 && sequence.contains(own)) return true
            }
            // Маска вида «**8872» совпадает с последними четырьмя цифрами моего счёта.
            if (sequence.length >= 4 && ownSuffixes.contains(sequence)) return true
        }
        return false
    }

    /** Список подряд идущих цифр (номера карт, счетов, телефонов). */
    private fun extractDigitSequences(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in text) {
            if (ch.isDigit()) {
                current.append(ch)
            } else {
                if (current.length >= 4) result.add(current.toString())
                current.clear()
            }
        }
        if (current.length >= 4) result.add(current.toString())
        return result
    }
}

/**
 * Отпечаток операции для защиты от дублей при пересекающихся выгрузках.
 * Состав: банк, дата операции, сумма, валюта и исходное назначение платежа.
 */
object Fingerprint {
    fun of(bankId: String, timestamp: Long, amountMinor: Long, currency: String, rawDescription: String): String {
        val normalized = rawDescription.trim().replace(Regex("""\s+"""), " ").lowercase()
        val source = "$bankId|$timestamp|$amountMinor|$currency|$normalized"
        return Sha256.hex(source)
    }
}
