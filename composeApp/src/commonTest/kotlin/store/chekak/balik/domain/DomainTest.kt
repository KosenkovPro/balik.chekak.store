package store.chekak.balik.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.data.db.CategoryRuleEntity
import store.chekak.balik.data.db.RuleMatchType
import store.chekak.balik.data.db.SeedData
import store.chekak.balik.data.db.TransactionEntity
import store.chekak.balik.parser.ParsedOperation

private val categories: List<CategoryEntity> =
    SeedData.categories.mapIndexed { index, category -> category.copy(id = index + 1L) }

private fun categoryId(key: String): Long =
    categories.first { it.key == key }.id

class InternalTransferDetectorTest {

    @Test
    fun detectsTransferByOwnNumber() {
        val detector = InternalTransferDetector(listOf("2200000000008872"))
        assertTrue(detector.detect("Перевод с карты 2200000000008872 на счёт"))
    }

    @Test
    fun detectsTransferByOwnCardSuffix() {
        val detector = InternalTransferDetector(listOf("2200000000008872"))
        assertTrue(detector.detect("Перевод с карты **8872 на свой счёт"))
    }

    @Test
    fun ordinaryPurchaseWithOwnCardIsNotInternalTransfer() {
        val detector = InternalTransferDetector(listOf("2200000000008872"))
        assertFalse(
            detector.detect("Оплата покупки по карте. CARD **8872 11NOV RUB 370.00 TEST SHOP"),
        )
    }

    @Test
    fun detectsTransferByTextPattern() {
        val detector = InternalTransferDetector()
        assertTrue(detector.detect("Перевод между своими счетами"))
        assertTrue(detector.detect("Пополнение с карты 1234"))
    }

    @Test
    fun externalTransferIsNotInternal() {
        val detector = InternalTransferDetector(listOf("2200000000008872"))
        assertFalse(detector.detect("Перевод с номера 0079169773779. Получатель: Марина"))
    }

    /**
     * Описание, распознанное по картинке, может потерять пробелы или заменить
     * букву знаком препинания — перевод всё равно должен узнаваться.
     */
    @Test
    fun detectsTransferInNoisyRecognizedText() {
        val detector = InternalTransferDetector()
        assertTrue(detector.detect("Перевод с договора 5164056862"))
        assertTrue(detector.detect("Переводсдоговора 5164056862"))
        assertTrue(detector.detect("Перевод (: договора 5 164056862"))
        assertTrue(detector.detect("Внутрибанковский перевод сдоговора 5233155216"))
    }

    @Test
    fun externalTransferIsNotInternalInNoisyText() {
        val detector = InternalTransferDetector(listOf("2200000000008872"))
        assertFalse(detector.detect("Внешний перевод по номеру телефона +79680778991"))
    }
}

class FingerprintTest {

    @Test
    fun fingerprintIsStable() {
        val first = Fingerprint.of("raiffeisen", 1000L, -37000L, "RUB", "Оплата покупки")
        val second = Fingerprint.of("raiffeisen", 1000L, -37000L, "RUB", "Оплата покупки")
        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun fingerprintDiffersForDifferentAmount() {
        val first = Fingerprint.of("raiffeisen", 1000L, -37000L, "RUB", "Оплата покупки")
        val second = Fingerprint.of("raiffeisen", 1000L, -38000L, "RUB", "Оплата покупки")
        assertNotEquals(first, second)
    }
}

class CategorizationEngineTest {

    private val engine = CategorizationEngine(
        rules = emptyList(),
        categories = categories,
    )

    @Test
    fun detectsInternalTransferFirst() {
        val operation = ParsedOperation(
            timestamp = 0L,
            amountMinor = -100_000L,
            rawDescription = "Перевод между своими счетами",
        )
        val result = engine.categorize(operation)
        assertTrue(result.isInternalTransfer)
        assertEquals(categoryId(SeedData.CategoryKeys.INTERNAL), result.categoryId)
    }

    @Test
    fun mapsMccToCategory() {
        val operation = ParsedOperation(
            timestamp = 0L,
            amountMinor = -100_000L,
            rawDescription = "Оплата",
            mcc = "5411",
        )
        val result = engine.categorize(operation)
        assertEquals(categoryId(SeedData.CategoryKeys.GROCERY), result.categoryId)
    }

    @Test
    fun appliesUserRuleWithPriority() {
        val rule = CategoryRuleEntity(
            id = 1L,
            pattern = "Пятёрочка",
            matchType = RuleMatchType.CONTAINS,
            categoryId = categoryId(SeedData.CategoryKeys.GROCERY),
            priority = 100,
        )
        val engineWithRule = CategorizationEngine(listOf(rule), categories)
        val result = engineWithRule.categorize(
            ParsedOperation(timestamp = 0L, amountMinor = -100_000L, rawDescription = "Пятёрочка 1234"),
        )
        assertEquals(categoryId(SeedData.CategoryKeys.GROCERY), result.categoryId)
        assertEquals(1L, result.matchedRuleId)
    }

    @Test
    fun fallsBackToOtherForExpenses() {
        val result = engine.categorize(
            ParsedOperation(timestamp = 0L, amountMinor = -100_000L, rawDescription = "Что-то"),
        )
        assertEquals(categoryId(SeedData.CategoryKeys.OTHER), result.categoryId)
    }

    @Test
    fun fallsBackToIncomeForPositiveAmount() {
        val result = engine.categorize(
            ParsedOperation(timestamp = 0L, amountMinor = 100_000L, rawDescription = "Зарплата"),
        )
        assertEquals(categoryId(SeedData.CategoryKeys.INCOME), result.categoryId)
    }
}

class AnalyticsTest {

    private fun transaction(
        id: Long,
        amountMinor: Long,
        categoryId: Long? = null,
        internal: Boolean = false,
        timestamp: Long = 1_000L,
    ) = TransactionEntity(
        id = id,
        fingerprint = "fp$id",
        accountId = 1L,
        timestamp = timestamp,
        amountMinor = amountMinor,
        currency = "RUB",
        rawDescription = "Операция $id",
        categoryId = categoryId,
        isInternalTransfer = internal,
    )

    @Test
    fun excludesInternalTransfersFromTotals() {
        val transactions = listOf(
            transaction(1, -100_000L, categoryId(SeedData.CategoryKeys.GROCERY)),
            transaction(2, -50_000L, categoryId(SeedData.CategoryKeys.GROCERY), internal = true),
            transaction(3, 200_000L, categoryId(SeedData.CategoryKeys.INCOME)),
        )
        val summary = Analytics.summarize(transactions, categories, 0L, 10_000L)
        val rub = summary.currencies.first()
        assertEquals(100_000L, rub.expensesMinor)
        assertEquals(200_000L, rub.incomesMinor)
        assertEquals(100_000L, rub.netMinor)
    }

    @Test
    fun splitsByCategoryWithShares() {
        val transactions = listOf(
            transaction(1, -75_000L, categoryId(SeedData.CategoryKeys.GROCERY)),
            transaction(2, -25_000L, categoryId(SeedData.CategoryKeys.CAFE)),
        )
        val summary = Analytics.summarize(transactions, categories, 0L, 10_000L)
        val slices = summary.currencies.first().byCategory
        assertEquals(2, slices.size)
        assertEquals(75f, slices[0].share)
        assertEquals(25f, slices[1].share)
    }

    @Test
    fun keepsCurrenciesApart() {
        val transactions = listOf(
            transaction(1, -100_000L, categoryId(SeedData.CategoryKeys.GROCERY)),
            transaction(2, -10_000L, categoryId(SeedData.CategoryKeys.CAFE)).copy(currency = "USD"),
        )
        val summary = Analytics.summarize(transactions, categories, 0L, 10_000L)
        assertEquals(2, summary.currencies.size)
    }
}

class ForecastCalculatorTest {

    private val calculator = ForecastCalculator()

    /** 1 января 2026 — начало отсчёта в тестах. */
    private val january = 1_767_225_600_000L

    private fun monthlyExpense(id: Long, monthIndex: Int, amountMinor: Long) = TransactionEntity(
        id = id,
        fingerprint = "fp$id",
        accountId = 1L,
        // 1-е число каждого месяца: 2026-01, 2026-02, 2026-03…
        timestamp = store.chekak.balik.core.DateUtils.plusMonths(
            january,
            monthIndex,
        ),
        amountMinor = amountMinor,
        currency = "RUB",
        rawDescription = "Расход $id",
    )

    private fun month(index: Int): Long = store.chekak.balik.core.DateUtils.plusMonths(january, index)

    /** Конец месяца — чтобы период покрывал месяц целиком. */
    private fun monthEnd(index: Int): Long =
        store.chekak.balik.core.DateUtils.endOfMonth(month(index))

    @Test
    fun computesMedian() {
        assertEquals(300L, calculator.median(listOf(100L, 200L, 300L, 400L, 500L)))
        assertEquals(250L, calculator.median(listOf(100L, 200L, 300L, 400L)))
    }

    @Test
    fun computesPercentile() {
        val values = (1..10).map { it * 100L }
        assertEquals(900L, calculator.percentile(values, 0.85))
        assertEquals(1000L, calculator.percentile(values, 1.0))
    }

    @Test
    fun countsOnlyTransactionsInsideTheChosenPeriod() {
        // Расход в январе и в марте, выбираем только февраль — операций в периоде нет.
        val transactions = listOf(
            monthlyExpense(1, 0, -300_000L),
            monthlyExpense(2, 2, -300_000L),
        )
        val result = calculator.calculate(
            transactions = transactions,
            currentBalanceMinor = 1_000_000L,
            currency = "RUB",
            historyFrom = month(1),
            historyTo = monthEnd(1),
            targetDate = month(3),
            today = month(1),
            method = ForecastMethod.MEAN,
        )
        assertEquals(1, result.monthsAnalyzed, "В выбранном периоде один месяц")
        assertEquals(0L, result.dailyExpenseRate, "Операций внутри периода нет")
        assertFalse(result.hasData, "В выбранном периоде нет операций")
        assertEquals(1_000_000L, result.projectedBalanceMinor)
    }

    @Test
    fun countsOnlyTransactionsOfTheChosenPeriodAndCurrency() {
        val transactions = listOf(
            monthlyExpense(1, 0, -300_000L),
            monthlyExpense(2, 1, -310_000L),
        )
        val result = calculator.calculate(
            transactions = transactions,
            currentBalanceMinor = 0L,
            currency = "RUB",
            historyFrom = month(0),
            historyTo = monthEnd(1),
            targetDate = month(3),
            today = month(2),
            method = ForecastMethod.MEAN,
        )
        assertEquals(2, result.monthsAnalyzed)
        assertEquals(0L, result.dailyIncomeRate)
        assertTrue(result.dailyExpenseRate > 0)
    }

    @Test
    fun projectsBalanceOnTargetDate() {
        // Расход 30 000 ₽ в январе и февраль по 30 000 ₽, зарплата 60 000 ₽ в январе.
        val transactions = listOf(
            monthlyExpense(1, 0, -300_000L),
            monthlyExpense(2, 1, -300_000L),
            monthlyExpense(3, 0, 600_000L),
        )
        val today = month(2)
        val target = store.chekak.balik.core.DateUtils.plusMonths(today, 1)
        val result = calculator.calculate(
            transactions = transactions,
            currentBalanceMinor = 1_000_000L,
            currency = "RUB",
            historyFrom = month(0),
            historyTo = monthEnd(1),
            targetDate = target,
            today = today,
            method = ForecastMethod.MEAN,
        )
        assertEquals(2, result.monthsAnalyzed)
        // С 1 марта до 1 апреля — 31 день.
        assertEquals(31, result.daysAhead)
        assertEquals(1_000_000L + result.projectedIncomeMinor - result.projectedExpenseMinor, result.projectedBalanceMinor)
        assertTrue(result.projectedExpenseMinor > 0)
    }

    @Test
    fun targetDateInThePastMeansNoDaysAhead() {
        val result = calculator.calculate(
            transactions = listOf(monthlyExpense(1, 0, -300_000L)),
            currentBalanceMinor = 500_000L,
            currency = "RUB",
            historyFrom = month(0),
            historyTo = monthEnd(0),
            targetDate = month(0),
            today = month(2),
        )
        assertEquals(0, result.daysAhead)
        assertEquals(500_000L, result.projectedBalanceMinor, "В прошлом остаток не меняется")
    }

    @Test
    fun internalTransfersDoNotAffectTheRate() {
        val transfer = monthlyExpense(2, 0, -300_000L).copy(isInternalTransfer = true)
        val result = calculator.calculate(
            transactions = listOf(monthlyExpense(1, 0, -300_000L), transfer),
            currentBalanceMinor = 0L,
            currency = "RUB",
            historyFrom = month(0),
            historyTo = monthEnd(0),
            targetDate = month(1),
            today = month(0),
            method = ForecastMethod.MEAN,
        )
        // Перевод самому себе не считается ни расходом, ни доходом.
        assertEquals(1, result.monthsAnalyzed)
        assertEquals(300_000L / 31, result.dailyExpenseRate)
    }

    @Test
    fun returnsEmptyResultWithoutData() {
        val result = calculator.calculate(
            transactions = emptyList(),
            currentBalanceMinor = 500_000L,
            currency = "RUB",
            historyFrom = month(0),
            historyTo = monthEnd(0),
            targetDate = month(1),
            today = month(0),
        )
        assertFalse(result.hasData, "Без операций прогноз считать не из чего")
        assertEquals(500_000L, result.projectedBalanceMinor)
    }
}

class BudgetEvaluatorTest {

    @Test
    fun computesSpentAndProgress() {
        val budget = store.chekak.balik.data.db.BudgetEntity(
            id = 1L,
            categoryId = categoryId(SeedData.CategoryKeys.GROCERY),
            limitMinor = 10_000L,
        )
        val transactions = listOf(
            TransactionEntity(
                id = 1L,
                fingerprint = "fp1",
                accountId = 1L,
                timestamp = 1_000L,
                amountMinor = -4_000L,
                currency = "RUB",
                rawDescription = "Продукты",
                categoryId = categoryId(SeedData.CategoryKeys.GROCERY),
            ),
        )
        val statuses = BudgetEvaluator.evaluate(listOf(budget), categories, transactions, 0L, 10_000L)
        assertEquals(1, statuses.size)
        assertEquals(4_000L, statuses[0].spentMinor)
        assertEquals(6_000L, statuses[0].remainingMinor)
        assertEquals(40f, statuses[0].progress)
        assertFalse(statuses[0].isExceeded)
    }
}
