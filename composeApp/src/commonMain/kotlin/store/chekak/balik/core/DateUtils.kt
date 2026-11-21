package store.chekak.balik.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Утилиты дат. Банковские выписки используют локальное время и разные форматы,
 * поэтому парсинг сосредоточен здесь, а не размазан по парсерам.
 */
object DateUtils {

    private val dayMonthYear = Regex("""^(\d{1,2})[.\-/](\d{1,2})[.\-/](\d{4})$""")
    private val dayMonthYearTime =
        Regex("""^(\d{1,2})[.\-/](\d{1,2})[.\-/](\d{4})[ T](\d{1,2}):(\d{2})(?::(\d{2}))?$""")
    private val isoDate = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val isoDateTime = Regex("""^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?$""")

    /** Разбирает дату из выписки в epoch millis (локальная таймзона устройства). */
    fun parseDateTime(text: String, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long? {
        val trimmed = text.trim().trim('\u00A0')
        if (trimmed.isEmpty()) return null

        dayMonthYearTime.matchEntire(trimmed)?.let { m ->
            val (d, mo, y, h, mi, s) = m.destructured
            return toEpoch(d.toInt(), mo.toInt(), y.toInt(), h.toInt(), mi.toInt(), s.toIntOrNull() ?: 0, timeZone)
        }
        dayMonthYear.matchEntire(trimmed)?.let { m ->
            val (d, mo, y) = m.destructured
            return toEpoch(d.toInt(), mo.toInt(), y.toInt(), 0, 0, 0, timeZone)
        }
        isoDateTime.matchEntire(trimmed)?.let { m ->
            val (y, mo, d, h, mi, s) = m.destructured
            return toEpoch(d.toInt(), mo.toInt(), y.toInt(), h.toInt(), mi.toInt(), s.toIntOrNull() ?: 0, timeZone)
        }
        isoDate.matchEntire(trimmed)?.let { m ->
            val (y, mo, d) = m.destructured
            return toEpoch(d.toInt(), mo.toInt(), y.toInt(), 0, 0, 0, timeZone)
        }
        return null
    }

    private fun toEpoch(
        day: Int,
        month: Int,
        year: Int,
        hour: Int,
        minute: Int,
        second: Int,
        timeZone: TimeZone,
    ): Long? = try {
        LocalDateTime(year, month, day, hour, minute, second)
            .toInstant(timeZone)
            .toEpochMilliseconds()
    } catch (_: IllegalArgumentException) {
        null
    }

    fun toLocalDateTime(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
        kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)

    fun toLocalDate(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
        toLocalDateTime(epochMillis, timeZone).date

    /** Начало локального дня в epoch millis. */
    fun startOfDay(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val date = toLocalDate(epochMillis, timeZone)
        return LocalDateTime(date.year, date.month, date.dayOfMonth, 0, 0).toInstant(timeZone).toEpochMilliseconds()
    }

    fun endOfDay(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val date = toLocalDate(epochMillis, timeZone)
        return LocalDateTime(date.year, date.month, date.dayOfMonth, 23, 59, 59)
            .toInstant(timeZone).toEpochMilliseconds() + 999L
    }

    fun startOfMonth(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val date = toLocalDate(epochMillis, timeZone)
        return LocalDateTime(date.year, date.month, 1, 0, 0).toInstant(timeZone).toEpochMilliseconds()
    }

    fun endOfMonth(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val date = toLocalDate(epochMillis, timeZone)
        val nextMonth = if (date.month == kotlinx.datetime.Month.DECEMBER) 1 else date.monthNumber + 1
        val nextYear = if (date.month == kotlinx.datetime.Month.DECEMBER) date.year + 1 else date.year
        val firstOfNext = LocalDateTime(nextYear, nextMonth, 1, 0, 0).toInstant(timeZone)
        return firstOfNext.toEpochMilliseconds() - 1
    }

    fun startOfYear(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val date = toLocalDate(epochMillis, timeZone)
        return LocalDateTime(date.year, 1, 1, 0, 0).toInstant(timeZone).toEpochMilliseconds()
    }

    fun endOfYear(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val date = toLocalDate(epochMillis, timeZone)
        return LocalDateTime(date.year, 12, 31, 23, 59, 59)
            .toInstant(timeZone).toEpochMilliseconds() + 999L
    }

    /** Добавляет месяцы к дате, сохраняя день (с обрезкой до конца месяца). */
    fun plusMonths(epochMillis: Long, months: Int, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val date = toLocalDate(epochMillis, timeZone)
        val totalMonths = date.year * 12 + (date.monthNumber - 1) + months
        val newYear = totalMonths / 12
        val newMonth = totalMonths % 12 + 1
        val maxDay = daysInMonth(newYear, newMonth)
        val newDay = if (date.dayOfMonth > maxDay) maxDay else date.dayOfMonth
        return LocalDateTime(newYear, newMonth, newDay, 0, 0).toInstant(timeZone).toEpochMilliseconds()
    }

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if (isLeapYear(year)) 29 else 28
    }

    /** Локальная дата в epoch millis (полночь). */
    fun toEpochMillis(date: LocalDate, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long =
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 0, 0)
            .toInstant(timeZone)
            .toEpochMilliseconds()

    /** Дата в формате «дд.мм.гггг» — так выписки и интерфейс показывают даты. */
    fun formatDate(epochMillis: Long): String {
        val date = toLocalDate(epochMillis)
        return "${date.dayOfMonth.toString().padStart(2, '0')}." +
            "${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
    }

    fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    fun daysBetween(fromMillis: Long, toMillis: Long): Int {
        val from = toLocalDate(fromMillis)
        val to = toLocalDate(toMillis)
        return (to.toEpochDays() - from.toEpochDays()).toInt()
    }

    /** Сколько полных дней прошло с начала периода. */
    fun dayIndex(epochMillis: Long, periodStart: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): Int =
        daysBetween(periodStart, epochMillis)

    /**
     * Номер столбца календаря для 1-го числа месяца: 0 — понедельник.
     *
     * Считаем от эпохи, а не через `DayOfWeek`: на JVM и на Kotlin/Native у дня
     * недели разная нумерация (там `ordinal` начинается с нуля, здесь — с
     * единицы), и календарь получался бы сдвинутым на день.
     */
    fun mondayFirstOffset(year: Int, month: Int): Int {
        val epochDays = LocalDate(year, month, 1).toEpochDays()
        // 1 января 1970 года — четверг, значит до понедельника три дня.
        return ((epochDays + 3) % 7 + 7) % 7
    }
}
