package store.chekak.balik.domain

import store.chekak.balik.core.DateUtils
import store.chekak.balik.data.db.TransactionEntity
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Способ расчёта прогноза. */
enum class ForecastMethod { MEDIAN, MEAN, P85 }

/**
 * Быстрые пресеты целевой даты.
 *
 * Это лишь подсказки: дату прогноза можно задать произвольно, хоть на год вперёд.
 */
enum class ForecastHorizon(val days: Int) {
    WEEK(7),
    MONTH(30),
    QUARTER(91),
    YEAR(365),
}

/**
 * Готовые варианты периода истории, по которому считается темп.
 *
 * Это подсказки: период можно задать произвольно — своими датами «с» и «по».
 */
enum class HistoryPreset {
    /** Только прошлый календарный месяц целиком. */
    LAST_MONTH,

    /** Три последних месяца, включая текущий. */
    THREE_MONTHS,

    /** Шесть последних месяцев, включая текущий. */
    SIX_MONTHS,

    /** Всё, что есть в базе. */
    ALL_TIME,

    /** Свои даты «с» и «по». */
    CUSTOM,
}

/**
 * Месяц выбранного периода истории, по которому посчитан дневной темп.
 */
data class ForecastSegment(
    val yearMonth: String,
    val expensesMinor: Long,
    val incomesMinor: Long,
    /** Сколько дней этого месяца попало в выбранный период. */
    val days: Int,
    val dailyExpenseRate: Long,
    val dailyIncomeRate: Long,
)

/**
 * Прогноз: сколько денег будет на выбранную дату, если ничего не менять.
 *
 * @param historyFrom начало периода истории, по которому считается темп
 * @param historyTo конец периода истории
 * @param targetDate дата, на которую считается прогноз
 */
data class ForecastResult(
    val method: ForecastMethod,
    val currency: String,
    val historyFrom: Long,
    val historyTo: Long,
    val targetDate: Long,
    /** Сколько дней от «сегодня» до целевой даты. */
    val daysAhead: Int,
    val dailyExpenseRate: Long = 0,
    val dailyIncomeRate: Long = 0,
    val projectedExpenseMinor: Long = 0,
    val projectedIncomeMinor: Long = 0,
    val currentBalanceMinor: Long = 0,
    val monthsAnalyzed: Int = 0,
    val segments: List<ForecastSegment> = emptyList(),
) {
    /** Есть ли в выбранном периоде операции, по которым вообще можно считать темп. */
    val hasData: Boolean get() = segments.any { it.expensesMinor != 0L || it.incomesMinor != 0L }

    /** Остаток на целевой дату. */
    val projectedBalanceMinor: Long get() = currentBalanceMinor + projectedIncomeMinor - projectedExpenseMinor

    /** Чистый поток за месяц (30 дней) по выбранному темпу. */
    val netPerMonthMinor: Long get() = (dailyIncomeRate - dailyExpenseRate) * 30
}

/**
 * Прогноз остатка на произвольную дату.
 *
 * Логика: по выбранному периоду истории считается средний дневной темп расходов
 * и темп доходов, затем темп умножается на число дней от «сегодня» до целевой
 * даты, и к текущему остатку прибавляется разница.
 *
 * Период истории задаётся произвольно — например, «только прошлый месяц» или
 * «с 1 марта по 30 июня». Внутри периода темп считается по каждому месяцу
 * отдельно (по фактическому числу дней этого месяца в периоде), а затем
 * месяцы сводятся выбранным методом: медиана, среднее или 85-й перцентиль
 * (по умолчанию p85 — «пессимистичный, но честный» сценарий).
 *
 * Переводы самому себе не учитываются: они не меняют состояние и портили бы
 * средний темп.
 */
class ForecastCalculator {

    /**
     * Считает прогноз остатка на [targetDate] по темпу периода [historyFrom]…[historyTo].
     *
     * @param today «сегодня» — от этой даты считаются дни до целевой даты
     */
    fun calculate(
        transactions: List<TransactionEntity>,
        currentBalanceMinor: Long,
        currency: String,
        historyFrom: Long,
        historyTo: Long,
        targetDate: Long,
        today: Long = System.currentTimeMillis(),
        method: ForecastMethod = ForecastMethod.P85,
    ): ForecastResult {
        val from = minOf(historyFrom, historyTo)
        val to = maxOf(historyFrom, historyTo)

        val relevant = transactions.filter {
            it.currency == currency &&
                !it.isInternalTransfer &&
                it.timestamp in from..to
        }

        val segments = buildSegments(relevant, from, to)
        if (segments.isEmpty()) {
            return ForecastResult(
                method = method,
                currency = currency,
                historyFrom = from,
                historyTo = to,
                targetDate = targetDate,
                daysAhead = daysAhead(today, targetDate),
                currentBalanceMinor = currentBalanceMinor,
            )
        }

        val expenseRate = aggregate(segments.map { it.dailyExpenseRate }, method)
        val incomeRate = aggregate(segments.map { it.dailyIncomeRate }, method)
        val days = daysAhead(today, targetDate)

        return ForecastResult(
            method = method,
            currency = currency,
            historyFrom = from,
            historyTo = to,
            targetDate = targetDate,
            daysAhead = days,
            dailyExpenseRate = expenseRate,
            dailyIncomeRate = incomeRate,
            projectedExpenseMinor = expenseRate * days,
            projectedIncomeMinor = incomeRate * days,
            currentBalanceMinor = currentBalanceMinor,
            monthsAnalyzed = segments.size,
            segments = segments,
        )
    }

    /**
     * Разбивает период по календарным месяцам и считает дневной темп каждого.
     *
     * Месяц учитывается по фактическому числу дней, попавших в период: если
     * выбран только «хвост» месяца, темп считается по этим дням, а не по всем
     * дням месяца.
     */
    private fun buildSegments(
        transactions: List<TransactionEntity>,
        from: Long,
        to: Long,
    ): List<ForecastSegment> {
        if (to < from) return emptyList()
        val segments = mutableListOf<ForecastSegment>()
        var cursor = from
        while (cursor <= to) {
            val segmentEnd = minOf(DateUtils.endOfMonth(cursor), to)
            val days = DateUtils.daysBetween(cursor, segmentEnd) + 1
            val inSegment = transactions.filter { it.timestamp in cursor..segmentEnd }
            val expenses = inSegment.filter { it.amountMinor < 0 }.sumOf { -it.amountMinor }
            val incomes = inSegment.filter { it.amountMinor > 0 }.sumOf { it.amountMinor }
            segments += ForecastSegment(
                yearMonth = yearMonth(cursor),
                expensesMinor = expenses,
                incomesMinor = incomes,
                days = days,
                dailyExpenseRate = if (days > 0) expenses / days else 0L,
                dailyIncomeRate = if (days > 0) incomes / days else 0L,
            )
            cursor = segmentEnd + 1
        }
        return segments
    }

    /** Сколько дней осталось до целевой даты (прошедшие дни не считаются). */
    private fun daysAhead(today: Long, targetDate: Long): Int =
        DateUtils.daysBetween(today, targetDate).coerceAtLeast(0)

    private fun yearMonth(millis: Long): String {
        val date = DateUtils.toLocalDate(millis)
        return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
    }

    /** Усреднение по выбранному методу. */
    fun aggregate(values: List<Long>, method: ForecastMethod): Long {
        if (values.isEmpty()) return 0
        return when (method) {
            ForecastMethod.MEAN -> (values.average()).roundToLong()
            ForecastMethod.MEDIAN -> median(values)
            ForecastMethod.P85 -> percentile(values, 0.85)
        }
    }

    fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2
        }
    }

    /** Перцентиль по методу ближайшего ранга. */
    fun percentile(values: List<Long>, p: Double): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val rank = ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    /** Разница между текущим и ожидаемым уровнем (для «светофа» на графике). */
    fun deviation(current: Long, expected: Long): Long = current - expected

    fun absValue(value: Long): Long = abs(value)
}
