package store.chekak.balik.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import store.chekak.balik.core.DateUtils

/**
 * Проверки сверки распознанного текста с оригиналом.
 *
 * Сверка отвечает на вопрос «насколько хорошо приложение прочитало скан»:
 * операции сопоставляются по дате и сумме, а описания сравниваются посимвольно.
 */
class StatementComparisonTest {

    private fun operation(
        date: String,
        amountMinor: Long,
        description: String,
        merchant: String = "",
    ): ComparableOperation = ComparableOperation(
        timestamp = DateUtils.parseDateTime(date) ?: 0L,
        amountMinor = amountMinor,
        description = description,
        merchant = merchant,
    )

    @Test
    fun identicalDescriptionsAreExact() {
        val report = StatementComparison.compare(
            reference = listOf(operation("09.11.2026", -505400, "Оплата в PYATEROCHKA MOSCOW RUS")),
            actual = listOf(operation("09.11.2026", -505400, "Оплата в PYATEROCHKA MOSCOW RUS")),
        )

        assertEquals(1, report.matchedCount)
        assertEquals(1, report.exactCount)
        assertEquals(0, report.mismatchCount)
        assertEquals(ComparisonStatus.EXACT, report.rows.single().status)
    }

    @Test
    fun extraSpacesDoNotBreakExactMatch() {
        val report = StatementComparison.compare(
            reference = listOf(operation("09.11.2026", -100, "Оплата в  Lenta  MOSCOW RUS")),
            actual = listOf(operation("09.11.2026", -100, "Оплата в Lenta MOSCOW RUS")),
        )

        assertEquals(1, report.exactCount)
    }

    @Test
    fun mistypedLetterIsSimilarNotDifferent() {
        val report = StatementComparison.compare(
            reference = listOf(operation("03.10.2026", -26300, "Оплата в Vkusnoitochka_11036 MOSCOW RUS")),
            actual = listOf(operation("03.10.2026", -26300, "Оплата в VKkusnoitochka_11036 MOSCOW RUS")),
        )

        val row = report.rows.single()
        assertEquals(ComparisonStatus.SIMILAR, row.status)
        assertEquals(0, report.exactCount)
        assertEquals(1, report.similarCount)
        assertTrue(row.similarity > 0.9f)
    }

    @Test
    fun completelyDifferentDescriptionIsDifferent() {
        val report = StatementComparison.compare(
            reference = listOf(operation("09.11.2026", -100, "Оплата в PYATEROCHKA MOSCOW RUS")),
            actual = listOf(operation("09.11.2026", -100, "Возврат средств по операции оплаты LEMANAPRO MOSKVA RUS")),
        )

        assertEquals(ComparisonStatus.DIFFERENT, report.rows.single().status)
        assertEquals(1, report.differentCount)
    }

    @Test
    fun operationMissingInRecognitionIsReported() {
        val report = StatementComparison.compare(
            reference = listOf(
                operation("09.11.2026", -100, "Оплата в A MOSCOW RUS"),
                operation("10.11.2026", -200, "Оплата в B MOSCOW RUS"),
            ),
            actual = listOf(operation("09.11.2026", -100, "Оплата в A MOSCOW RUS")),
        )

        assertEquals(1, report.onlyReferenceCount)
        assertEquals(0, report.onlyActualCount)
        assertEquals(2, report.referenceCount)
        assertEquals(1, report.actualCount)
    }

    @Test
    fun extraOperationInRecognitionIsReported() {
        val report = StatementComparison.compare(
            reference = listOf(operation("09.11.2026", -100, "Оплата в A MOSCOW RUS")),
            actual = listOf(
                operation("09.11.2026", -100, "Оплата в A MOSCOW RUS"),
                operation("10.11.2026", -200, "Оплата в B MOSCOW RUS"),
            ),
        )

        assertEquals(1, report.onlyActualCount)
        assertEquals(0, report.onlyReferenceCount)
    }

    @Test
    fun sameDaySameAmountPairsAreMatchedByDescription() {
        // Две операции на одну сумму в один день: перепутать их местами нельзя.
        val report = StatementComparison.compare(
            reference = listOf(
                operation("09.11.2026", -100, "Оплата в PYATEROCHKA MOSCOW RUS"),
                operation("09.11.2026", -100, "Оплата в DIKSI Podolsk RUS"),
            ),
            actual = listOf(
                operation("09.11.2026", -100, "Оплата в DIKSI Podolsk RUS"),
                operation("09.11.2026", -100, "Оплата в PYATEROCHKA MOSCOW RUS"),
            ),
        )

        assertEquals(2, report.exactCount)
        assertEquals(0, report.mismatchCount)
    }

    @Test
    fun sameAmountOnDifferentDaysAreDifferentOperations() {
        val report = StatementComparison.compare(
            reference = listOf(operation("09.11.2026", -100, "Оплата в A MOSCOW RUS")),
            actual = listOf(operation("10.11.2026", -100, "Оплата в A MOSCOW RUS")),
        )

        assertEquals(0, report.matchedCount)
        assertEquals(1, report.onlyReferenceCount)
        assertEquals(1, report.onlyActualCount)
    }

    @Test
    fun summaryCountsShareAndSimilarity() {
        val report = StatementComparison.compare(
            reference = listOf(
                operation("09.11.2026", -100, "Оплата в A MOSCOW RUS"),
                operation("10.11.2026", -200, "Оплата в B MOSCOW RUS"),
                operation("11.11.2026", -300, "Оплата в C MOSCOW RUS"),
                operation("12.11.2026", -400, "Оплата в D MOSCOW RUS"),
            ),
            actual = listOf(
                operation("09.11.2026", -100, "Оплата в A MOSCOW RUS"),
                operation("10.11.2026", -200, "Оплата в B MOSCOW RUS"),
                operation("11.11.2026", -300, "Оплата в B MOSCOW RUS"),
            ),
        )

        assertEquals(4, report.referenceCount)
        assertEquals(3, report.actualCount)
        assertEquals(3, report.matchedCount)
        assertEquals(2, report.exactCount)
        assertEquals(0.5f, report.exactShare)
        assertEquals(2, report.mismatchCount)
        assertTrue(report.averageSimilarity > 0.9f)
    }

    @Test
    fun mismatchesAreListedSeparately() {
        val report = StatementComparison.compare(
            reference = listOf(
                operation("09.11.2026", -100, "Оплата в A MOSCOW RUS"),
                operation("10.11.2026", -200, "Оплата в B MOSCOW RUS"),
            ),
            actual = listOf(operation("09.11.2026", -100, "Оплата в A MOSCOW RUS")),
        )

        assertEquals(1, report.mismatches.size)
        assertEquals(ComparisonStatus.ONLY_REFERENCE, report.mismatches.single().status)
    }

    @Test
    fun levenshteinCountsChangedCharacters() {
        assertEquals(0, StatementComparison.levenshtein("PYATEROCHKA", "PYATEROCHKA"))
        assertEquals(1, StatementComparison.levenshtein("PYATEROCHKA", "PYATEROCНKA"))
        assertEquals(2, StatementComparison.levenshtein("кот", "дом"))
        assertEquals(3, StatementComparison.levenshtein("", "abc"))
    }

    @Test
    fun similarityIsOneForEqualAndZeroForEmpty() {
        assertEquals(1f, StatementComparison.similarity("Оплата в A", "Оплата в A"))
        assertEquals(0f, StatementComparison.similarity("", "Оплата в A"))
        assertTrue(StatementComparison.similarity("Оплата в PYATEROCHKA MOSCOW RUS", "Оплата в PYATEROCНKA MOSCOW RUS") > 0.9f)
    }

    @Test
    fun homoglyphVariantsAreTextuallyDifferent() {
        // «PYATEROCHKA» и «РУАТЕВОСНКА» выглядят одинаково, но текстово разные:
        // поэтому омоглифы и приходится устранять отдельным правилом выбора
        // алфавита, а не сравнением строк.
        assertTrue(StatementComparison.similarity("PYATEROCHKA", "РУАТЕВОСНКА") < 0.8f)
    }
}
