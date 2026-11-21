package store.chekak.balik.domain

import store.chekak.balik.core.DateUtils

/**
 * Сохранённый выбор пользователя на экране прогноза.
 *
 * Живёт в настройках, чтобы выбранный период и целевая дата не слетали
 * при переходе на другой экран и переживали перезапуск приложения.
 */
data class ForecastSelection(
    val preset: HistoryPreset = HistoryPreset.THREE_MONTHS,
    /** Свои границы периода — только для [HistoryPreset.CUSTOM]. */
    val customFrom: Long? = null,
    val customTo: Long? = null,
    /** Целевая дата прогноза. */
    val targetDate: Long? = null,
)

/**
 * Период истории, по которому считается темп расходов и доходов.
 *
 * Пресеты — лишь подсказки; период всегда можно задать своими датами.
 */
object ForecastPeriod {

    /**
     * Границы периода для выбранного [preset].
     *
     * @param earliest самая ранняя операция в базе — начало периода для `ALL_TIME`
     * @param today «сегодня»
     * @param customFrom свои даты: начало и конец периода
     */
    fun resolve(
        preset: HistoryPreset,
        earliest: Long,
        today: Long,
        customFrom: Long? = null,
        customTo: Long? = null,
    ): LongRange {
        val endOfToday = DateUtils.endOfDay(today)
        return when (preset) {
            HistoryPreset.LAST_MONTH -> {
                val previousMonth = DateUtils.plusMonths(today, -1)
                DateUtils.startOfMonth(previousMonth)..DateUtils.endOfMonth(previousMonth)
            }

            HistoryPreset.THREE_MONTHS ->
                DateUtils.startOfMonth(DateUtils.plusMonths(today, -2))..endOfToday

            HistoryPreset.SIX_MONTHS ->
                DateUtils.startOfMonth(DateUtils.plusMonths(today, -5))..endOfToday

            HistoryPreset.ALL_TIME ->
                minOf(earliest, today)..endOfToday

            HistoryPreset.CUSTOM -> {
                val from = customFrom ?: DateUtils.startOfMonth(today)
                val to = customTo ?: endOfToday
                minOf(from, to)..maxOf(from, to)
            }
        }
    }

    /**
     * Целевая дата прогноза по умолчанию — через месяц от «сегодня».
     */
    fun defaultTargetDate(today: Long): Long =
        DateUtils.startOfDay(DateUtils.plusMonths(today, 1))
}
