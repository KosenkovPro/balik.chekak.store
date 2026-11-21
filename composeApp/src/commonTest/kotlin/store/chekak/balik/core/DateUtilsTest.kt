package store.chekak.balik.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Раскладка календаря: номер столбца для первого числа месяца.
 *
 * Проверяем на известных датах — понедельник всегда в первом столбце.
 */
class DateUtilsTest {

    @Test
    fun mondayIsTheFirstColumn() {
        // 1 февраля 2027 года — понедельник.
        assertEquals(0, DateUtils.mondayFirstOffset(2027, 2))
    }

    @Test
    fun tuesdayIsTheSecondColumn() {
        // 1 декабря 2026 года — вторник.
        assertEquals(1, DateUtils.mondayFirstOffset(2026, 12))
    }

    @Test
    fun otherWeekdaysLandInTheRightColumn() {
        // 1 января 2026 года — четверг.
        assertEquals(3, DateUtils.mondayFirstOffset(2026, 1))
        // 1 ноября 2026 года — воскресенье, последний столбец.
        assertEquals(6, DateUtils.mondayFirstOffset(2026, 11))
        // 1 сентября 2026 года — вторник.
        assertEquals(1, DateUtils.mondayFirstOffset(2026, 9))
    }

    @Test
    fun offsetIsAlwaysWithinWeek() {
        var year = 2020
        while (year < 2030) {
            for (month in 1..12) {
                val offset = DateUtils.mondayFirstOffset(year, month)
                assertTrue(offset in 0..6, "Смещение вне диапазона: $year-$month → $offset")
            }
            year++
        }
    }

    @Test
    fun februaryOfLeapYearStartsOnSaturday() {
        // 1 февраля 2024 года — четверг (2024 год — високосный).
        assertEquals(3, DateUtils.mondayFirstOffset(2024, 2))
    }

    @Test
    fun daysInMonthKnowsAboutLeapYears() {
        assertEquals(29, DateUtils.daysInMonth(2024, 2))
        assertEquals(28, DateUtils.daysInMonth(2026, 2))
        assertEquals(31, DateUtils.daysInMonth(2026, 12))
        assertEquals(30, DateUtils.daysInMonth(2026, 11))
    }
}
