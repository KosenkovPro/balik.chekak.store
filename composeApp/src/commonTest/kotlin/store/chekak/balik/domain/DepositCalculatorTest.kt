package store.chekak.balik.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import store.chekak.balik.core.DateUtils
import store.chekak.balik.data.db.DepositEntity
import store.chekak.balik.data.db.DepositPayout

/**
 * Расчёт процентов по вкладам и налога на них.
 *
 * Проверяем на конкретных числах: 1 000 000 ₽ под 16% годовых даёт за год
 * ровно 160 000 ₽ — как раз необлагаемый минимум по НК РФ на 2026 год.
 */
class DepositCalculatorTest {

    private val calculator = DepositCalculator()

    /** 1 января 2026, полночь. */
    private val january2026 = 1_767_225_600_000L

    private fun deposit(
        id: Long = 1L,
        amountMinor: Long = 100_000_000L,
        ratePercent: Double = 16.0,
        startDate: Long = january2026,
        endDate: Long = DateUtils.plusMonths(january2026, 12),
        payout: DepositPayout = DepositPayout.AT_END,
        currency: String = "RUB",
    ) = DepositEntity(
        id = id,
        name = "Вклад $id",
        bankName = "Банк",
        amountMinor = amountMinor,
        annualRatePercent = ratePercent,
        startDate = startDate,
        endDate = endDate,
        payout = payout,
        currency = currency,
    )

    private fun date(year: Int, month: Int, day: Int): Long {
        val local = kotlinx.datetime.LocalDate(year, month, day)
        return DateUtils.toEpochMillis(local)
    }

    @Test
    fun interestAtEndOfTermIsPaidOnce() {
        val result = calculator.project(
            deposits = listOf(deposit(payout = DepositPayout.AT_END)),
            fromDate = january2026,
            toDate = DateUtils.plusMonths(january2026, 12),
        )
        val projection = result.deposits.single()

        // 365 дней × 16% / 365 = 16% от миллиона.
        assertEquals(16_000_000L, projection.interestMinor)
        assertEquals(16_000_000L, projection.receivedMinor)
        assertEquals(100_000_000L, projection.balanceMinor, "Тело вклада не меняется")
        // Выплата в конце срока — 1 января 2027 года.
        assertEquals(2027, projection.receivedByYear.keys.single())
    }

    @Test
    fun interestBeforeClosingIsNotYetPaid() {
        val result = calculator.project(
            deposits = listOf(deposit(payout = DepositPayout.AT_END)),
            fromDate = january2026,
            toDate = date(2026, 7, 1),
        )
        val projection = result.deposits.single()

        // Проценты наросли, но выплаты ещё не было.
        assertTrue(projection.interestMinor in 7_000_000L..8_500_000L)
        assertEquals(0L, projection.receivedMinor)
        assertTrue(projection.receivedByYear.isEmpty())
    }

    @Test
    fun monthlyPayoutKeepsPrincipalAndPaysTwelveTimes() {
        val result = calculator.project(
            deposits = listOf(deposit(payout = DepositPayout.MONTHLY)),
            fromDate = january2026,
            toDate = DateUtils.plusMonths(january2026, 12),
        )
        val projection = result.deposits.single()

        // Тело не растёт, проценты выплачены двенадцать раз.
        assertEquals(100_000_000L, projection.balanceMinor)
        assertEquals(projection.interestMinor, projection.receivedMinor)
        // Выплаты в 2026 и 2027 годах: последняя — 1 января 2027.
        assertEquals(2, projection.receivedByYear.size)
        assertTrue(projection.interestMinor > 15_000_000L)
    }

    @Test
    fun capitalizationGrowsPrincipal() {
        val result = calculator.project(
            deposits = listOf(deposit(payout = DepositPayout.CAPITALIZED)),
            fromDate = january2026,
            toDate = DateUtils.plusMonths(january2026, 12),
        )
        val projection = result.deposits.single()

        // С капитализацией тело растёт: на вкладе больше, чем было.
        assertTrue(projection.balanceMinor > 100_000_000L)
        assertTrue(projection.interestMinor > 15_900_000L, "Проценты считаются и от выросшего тела")
    }

    @Test
    fun taxIsNotChargedWithinTheExemptMinimum() {
        // 16% от миллиона за год = 160 000 ₽ — ровно минимум, налога нет.
        val result = calculator.project(
            deposits = listOf(deposit(amountMinor = 100_000_000L, ratePercent = 16.0)),
            fromDate = january2026,
            toDate = DateUtils.plusMonths(january2026, 12),
        )
        assertEquals(0L, result.totalTaxMinor)
        assertEquals(16_000_000L, result.totalInterestMinor)
    }

    @Test
    fun taxIsChargedOnTheExcessAboveTheExemptMinimum() {
        // 2 000 000 ₽ под 16% = 320 000 ₽ процентов, минимум 160 000 ₽.
        // Облагается 160 000 ₽ по ставке 13% = 20 800 ₽.
        val result = calculator.project(
            deposits = listOf(deposit(amountMinor = 200_000_000L, ratePercent = 16.0)),
            fromDate = january2026,
            toDate = DateUtils.plusMonths(january2026, 12),
        )
        assertEquals(32_000_000L, result.totalInterestMinor)
        assertEquals(2_080_000L, result.totalTaxMinor, "13% от 160 000 ₽")
        assertEquals(29_920_000L, result.totalNetInterestMinor)
    }

    @Test
    fun elevatedRateAppliesAboveTheThreshold() {
        // 20 000 000 ₽ под 16% = 3 200 000 ₽ процентов.
        // Необлагаемый минимум 160 000 ₽ — облагается 3 040 000 ₽.
        // Из них 2 400 000 ₽ по ставке 13% = 312 000 ₽,
        // оставшиеся 640 000 ₽ по ставке 15% = 96 000 ₽. Итого 408 000 ₽.
        val result = calculator.project(
            deposits = listOf(deposit(amountMinor = 20_000_000_00L, ratePercent = 16.0)),
            fromDate = january2026,
            toDate = DateUtils.plusMonths(january2026, 12),
        )
        assertEquals(320_000_000L, result.totalInterestMinor)
        assertEquals(40_800_000L, result.totalTaxMinor)
    }

    @Test
    fun exemptMinimumAppliesOnceForAllDeposits() {
        // Два вклада по 1 000 000 ₽: проценты 160 000 + 160 000 = 320 000 ₽.
        // Минимум 160 000 ₽ применяется один раз на год — налог только с 160 000 ₽.
        val result = calculator.project(
            deposits = listOf(
                deposit(id = 1L, amountMinor = 100_000_000L),
                deposit(id = 2L, amountMinor = 100_000_000L),
            ),
            fromDate = january2026,
            toDate = DateUtils.plusMonths(january2026, 12),
        )
        assertEquals(32_000_000L, result.totalInterestMinor)
        assertEquals(2_080_000L, result.totalTaxMinor)
        // Налог распределился между вкладами поровну.
        assertEquals(1_040_000L, result.deposits[0].taxMinor)
        assertEquals(1_040_000L, result.deposits[1].taxMinor)
    }

    @Test
    fun foreignCurrencyDepositsAreNotTaxed() {
        val result = calculator.project(
            deposits = listOf(deposit(amountMinor = 100_000_000L, currency = "USD")),
            fromDate = january2026,
            toDate = DateUtils.plusMonths(january2026, 12),
        )
        assertEquals(16_000_000L, result.totalInterestMinor)
        assertEquals(0L, result.totalTaxMinor, "Валютные вклады российским налогом не облагаются")
    }

    @Test
    fun taxSettingsAreRespected() {
        // Ставка 0% — налога нет, как бы ни велики проценты.
        val result = calculator.project(
            deposits = listOf(deposit(amountMinor = 20_000_000_00L)),
            fromDate = january2026,
            toDate = DateUtils.plusMonths(january2026, 12),
            tax = DepositTaxSettings(ratePercent = 0.0, elevatedRatePercent = 0.0),
        )
        assertEquals(0L, result.totalTaxMinor)

        // Другая ставка — другой налог: 20% от 160 000 ₽ = 32 000 ₽.
        val custom = calculator.project(
            deposits = listOf(deposit(amountMinor = 100_000_000L)),
            fromDate = january2026,
            toDate = DateUtils.plusMonths(january2026, 12),
            tax = DepositTaxSettings(ratePercent = 20.0, exemptMinor = 0L),
        )
        assertEquals(16_000_000L, custom.totalInterestMinor)
        assertEquals(3_200_000L, custom.totalTaxMinor)
    }

    @Test
    fun closedDepositKeepsOnlyPrincipal() {
        // Вклад закончился до «сегодня» — процентов больше нет.
        val result = calculator.project(
            deposits = listOf(
                deposit(
                    startDate = DateUtils.plusMonths(january2026, -12),
                    endDate = DateUtils.plusMonths(january2026, -1),
                    payout = DepositPayout.AT_END,
                ),
            ),
            fromDate = january2026,
            toDate = DateUtils.plusMonths(january2026, 6),
        )
        val projection = result.deposits.single()
        assertEquals(0L, projection.interestMinor)
        assertEquals(100_000_000L, projection.balanceMinor)
    }
}

/**
 * Период истории: пресеты и произвольные границы.
 */
class ForecastPeriodTest {

    /** 15 марта 2026, полночь. */
    private val march2026 = DateUtils.toEpochMillis(kotlinx.datetime.LocalDate(2026, 3, 15))

    @Test
    fun lastMonthCoversTheWholePreviousMonth() {
        val period = ForecastPeriod.resolve(
            preset = HistoryPreset.LAST_MONTH,
            earliest = march2026,
            today = march2026,
        )
        val start = DateUtils.toLocalDate(period.first)
        val end = DateUtils.toLocalDate(period.last)
        assertEquals(2, start.monthNumber)
        assertEquals(2026, start.year)
        assertEquals(28, end.dayOfMonth)
        assertEquals(2, end.monthNumber)
    }

    @Test
    fun threeMonthsStartsTwoMonthsBack() {
        val period = ForecastPeriod.resolve(
            preset = HistoryPreset.THREE_MONTHS,
            earliest = march2026,
            today = march2026,
        )
        val start = DateUtils.toLocalDate(period.first)
        assertEquals(1, start.monthNumber, "Начало года")
        assertEquals(1, start.dayOfMonth)
    }

    @Test
    fun sixMonthsStartsFiveMonthsBack() {
        val period = ForecastPeriod.resolve(
            preset = HistoryPreset.SIX_MONTHS,
            earliest = march2026,
            today = march2026,
        )
        val start = DateUtils.toLocalDate(period.first)
        assertEquals(10, start.monthNumber, "Октябрь прошлого года")
        assertEquals(2025, start.year)
    }

    @Test
    fun allTimeStartsAtTheEarliestTransaction() {
        val earliest = DateUtils.toEpochMillis(kotlinx.datetime.LocalDate(2024, 2, 10))
        val period = ForecastPeriod.resolve(
            preset = HistoryPreset.ALL_TIME,
            earliest = earliest,
            today = march2026,
        )
        assertEquals(earliest, period.first)
    }

    @Test
    fun customPeriodKeepsGivenBoundsInOrder() {
        val from = DateUtils.toEpochMillis(kotlinx.datetime.LocalDate(2026, 1, 5))
        val to = DateUtils.toEpochMillis(kotlinx.datetime.LocalDate(2026, 2, 20))
        // Границы переданы наоборот — порядок всё равно нормальный.
        val period = ForecastPeriod.resolve(
            preset = HistoryPreset.CUSTOM,
            earliest = march2026,
            today = march2026,
            customFrom = to,
            customTo = from,
        )
        assertEquals(from, period.first)
        assertEquals(to, period.last)
    }

    @Test
    fun defaultTargetDateIsOneMonthAhead() {
        val target = ForecastPeriod.defaultTargetDate(march2026)
        val date = DateUtils.toLocalDate(target)
        assertEquals(4, date.monthNumber)
        assertEquals(15, date.dayOfMonth)
    }
}
