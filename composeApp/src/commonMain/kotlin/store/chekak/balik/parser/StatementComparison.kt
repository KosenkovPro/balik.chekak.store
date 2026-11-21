package store.chekak.balik.parser

import store.chekak.balik.core.DateUtils

/**
 * Операция в виде, пригодном для сверки распознавания с оригиналом.
 *
 * @param timestamp дата операции в epoch millis.
 * @param amountMinor сумма в минорных единицах со знаком.
 * @param description исходный текст назначения платежа из выписки.
 * @param merchant очищенное имя точки — показывается рядом с описанием.
 */
data class ComparableOperation(
    val timestamp: Long,
    val amountMinor: Long,
    val currency: String = "RUB",
    val description: String = "",
    val merchant: String = "",
)

/** Что показала сверка одной операции. */
enum class ComparisonStatus {
    /** Описание совпало с оригиналом дословно. */
    EXACT,

    /** Описание отличается, но тексты похожи: движок перепутал одну букву. */
    SIMILAR,

    /** Описание отличается заметно. */
    DIFFERENT,

    /** Операция есть только в оригинале — при разборе распознанного текста она потерялась. */
    ONLY_REFERENCE,

    /** Операция есть только в распознанном — лишняя. */
    ONLY_ACTUAL,
}

/** Одна строка отчёта о сверке. */
data class ComparisonRow(
    /** Операция из оригинала; null, если её нет в распознанном. */
    val reference: ComparableOperation?,
    /** Операция из распознанного текста; null, если её нет в оригинале. */
    val actual: ComparableOperation?,
    val status: ComparisonStatus,
    /** Сходство описаний: 1 — совпадают, 0 — ничего общего. */
    val similarity: Float,
)

/**
 * Итог сверки: что удалось распознать так же, а где результат отличается.
 */
data class ComparisonReport(
    /** Операций в оригинале. */
    val referenceCount: Int,
    /** Операций в распознанном тексте. */
    val actualCount: Int,
    /** Операций, нашедших пару в другой стороне. */
    val matchedCount: Int,
    /** Операций, чьё описание совпало дословно. */
    val exactCount: Int,
    val rows: List<ComparisonRow>,
) {
    /** Описаний, отличающихся от оригинала. */
    val similarCount: Int get() = rows.count { it.status == ComparisonStatus.SIMILAR }

    val differentCount: Int get() = rows.count { it.status == ComparisonStatus.DIFFERENT }

    val onlyReferenceCount: Int get() = rows.count { it.status == ComparisonStatus.ONLY_REFERENCE }

    val onlyActualCount: Int get() = rows.count { it.status == ComparisonStatus.ONLY_ACTUAL }

    /** Расхождений всего — всё, что не совпало дословно. */
    val mismatchCount: Int get() = rows.count { it.status != ComparisonStatus.EXACT }

    /** Доля операций, описанных дословно как в оригинале. */
    val exactShare: Float
        get() = if (referenceCount == 0) 0f else exactCount.toFloat() / referenceCount

    /** Средняя сходность описаний среди сопоставленных операций. */
    val averageSimilarity: Float
        get() {
            val paired = rows.filter { it.reference != null && it.actual != null }
            if (paired.isEmpty()) return 0f
            return paired.sumOf { it.similarity.toDouble() }.toFloat() / paired.size
        }

    /** Строки с расхождениями — их и показывает интерфейс по умолчанию. */
    val mismatches: List<ComparisonRow> get() = rows.filter { it.status != ComparisonStatus.EXACT }
}

/**
 * Сверка распознанного текста с оригиналом.
 *
 * Нужна, чтобы увидеть, насколько хорошо приложение прочитало скан: операции
 * сопоставляются по дате и сумме, а описания сравниваются посимвольно. Так видно
 * и потерянные строки, и ошибки распознавания отдельных букв.
 */
object StatementComparison {

    /**
     * Порог, с которого отличие считается опечаткой, а не другим описанием.
     *
     * Одна перепутанная буква в длинном названии магазина даёт сходство около
     * 0,95, а полностью другое название — заметно меньше.
     */
    const val SIMILAR_THRESHOLD = 0.8f

    private val SPACES = Regex("""\s+""")

    /**
     * Сопоставляет операции оригинала и распознанного текста.
     *
     * @param reference операции из оригинала (текстовый слой выписки).
     * @param actual операции, разобранные из распознанного текста.
     */
    fun compare(
        reference: List<ComparableOperation>,
        actual: List<ComparableOperation>,
    ): ComparisonReport {
        val referenceGroups = reference.groupBy { keyOf(it) }
        val actualGroups = actual.groupBy { keyOf(it) }

        val rows = mutableListOf<ComparisonRow>()
        val keys = (referenceGroups.keys + actualGroups.keys).sortedWith(
            compareBy({ it.substringBefore('|').toLongOrNull() ?: 0L }, { it }),
        )

        for (key in keys) {
            val referenceOps = referenceGroups[key].orEmpty().sortedBy { it.timestamp }
            val actualOps = actualGroups[key].orEmpty().sortedBy { it.timestamp }
            rows += matchGroup(referenceOps, actualOps)
        }

        val rowsSorted = rows.sortedWith(
            compareBy(
                { it.reference?.timestamp ?: it.actual?.timestamp ?: 0L },
                { it.status.ordinal },
            ),
        )

        val matched = rowsSorted.count { it.reference != null && it.actual != null }
        val exact = rowsSorted.count { it.status == ComparisonStatus.EXACT }
        return ComparisonReport(
            referenceCount = reference.size,
            actualCount = actual.size,
            matchedCount = matched,
            exactCount = exact,
            rows = rowsSorted,
        )
    }

    /** Работает ли операция из выписки как разобранная. */
    fun ParsedOperation.toComparable(): ComparableOperation = ComparableOperation(
        timestamp = timestamp,
        amountMinor = amountMinor,
        currency = currency,
        description = rawDescription,
        merchant = merchantName,
    )

    /**
     * Ключ сопоставления: дата и сумма.
     *
     * Время в распознанном тексте может отличаться от оригинала, а вот дата
     * и сумма — это то, что банк показал в строке операции.
     */
    private fun keyOf(operation: ComparableOperation): String {
        val day = DateUtils.toLocalDate(operation.timestamp).toEpochDays()
        return "$day|${operation.amountMinor}"
    }

    /**
     * Сопоставляет операции одной группы (одна дата и одна сумма).
     *
     * В выписке встречаются одинаковые суммы в один день, поэтому пары ищутся
     * жадно по наибольшему сходству описаний: так «Оплата в X» и «Возврат по X»
     * не перепутаются местами.
     */
    private fun matchGroup(
        reference: List<ComparableOperation>,
        actual: List<ComparableOperation>,
    ): List<ComparisonRow> {
        if (reference.isEmpty() || actual.isEmpty()) {
            return reference.map { ComparisonRow(it, null, ComparisonStatus.ONLY_REFERENCE, 0f) } +
                actual.map { ComparisonRow(null, it, ComparisonStatus.ONLY_ACTUAL, 0f) }
        }

        val candidates = mutableListOf<Triple<Int, Int, Float>>()
        for (i in reference.indices) {
            for (j in actual.indices) {
                candidates += Triple(i, j, similarity(reference[i].description, actual[j].description))
            }
        }
        candidates.sortWith(
            compareByDescending<Triple<Int, Int, Float>> { it.third }
                .thenBy { it.first }
                .thenBy { it.second },
        )

        val usedReference = BooleanArray(reference.size)
        val usedActual = BooleanArray(actual.size)
        val pairs = mutableListOf<Pair<Int, Int>>()
        for ((i, j, _) in candidates) {
            if (usedReference[i] || usedActual[j]) continue
            usedReference[i] = true
            usedActual[j] = true
            pairs += i to j
        }

        val rows = mutableListOf<ComparisonRow>()
        for ((i, j) in pairs) {
            val expected = reference[i]
            val found = actual[j]
            val score = similarity(expected.description, found.description)
            val status = when {
                normalize(expected.description) == normalize(found.description) -> ComparisonStatus.EXACT
                score >= SIMILAR_THRESHOLD -> ComparisonStatus.SIMILAR
                else -> ComparisonStatus.DIFFERENT
            }
            rows += ComparisonRow(expected, found, status, score)
        }
        for (i in reference.indices) {
            if (!usedReference[i]) {
                rows += ComparisonRow(reference[i], null, ComparisonStatus.ONLY_REFERENCE, 0f)
            }
        }
        for (j in actual.indices) {
            if (!usedActual[j]) {
                rows += ComparisonRow(null, actual[j], ComparisonStatus.ONLY_ACTUAL, 0f)
            }
        }
        return rows
    }

    /** Убирает лишние пробелы: в выписках они встречаются из-за разбивки строк. */
    fun normalize(text: String): String = text.trim().replace(SPACES, " ")

    /**
     * Сходство двух описаний: 1 — совпадают, 0 — ничего общего.
     *
     * Считается по расстоянию Левенштейна, делённому на длину самого длинного
     * текста: так «PYATEROCHKA» и «РУАТЕВОСНКА» дают высокое сходство, а разные
     * названия — низкое.
     */
    fun similarity(a: String, b: String): Float {
        val left = normalize(a)
        val right = normalize(b)
        if (left == right) return 1f
        if (left.isEmpty() || right.isEmpty()) return 0f
        val longest = maxOf(left.length, right.length)
        return (1f - levenshtein(left, right).toFloat() / longest).coerceIn(0f, 1f)
    }

    /** Расстояние Левенштейна — сколько символов надо заменить, чтобы строки совпали. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
