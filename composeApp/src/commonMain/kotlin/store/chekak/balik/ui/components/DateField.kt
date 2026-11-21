package store.chekak.balik.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import store.chekak.balik.core.DateUtils

/**
 * Поле с датой и календарём для выбора.
 *
 * Свой календарь вместо системного диалога: он одинаково работает на Android,
 * iOS и десктопе и не тянет за собой платформенные зависимости.
 */
@Composable
fun DateField(
    label: String,
    dateMillis: Long,
    onDateChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isRussian: Boolean = true,
    minDate: Long? = null,
) {
    var showPicker by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        androidx.compose.material3.OutlinedTextField(
            value = DateUtils.formatDate(dateMillis),
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Прозрачная кнопка поверх поля: системное поле нельзя «открыть» по тапу.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showPicker = true },
        )
    }

    if (showPicker) {
        CalendarDialog(
            initialDate = dateMillis,
            isRussian = isRussian,
            minDate = minDate,
            onDismiss = { showPicker = false },
            onDateSelected = { selected ->
                showPicker = false
                onDateChange(selected)
            },
        )
    }
}

/** Диалог с календарём на месяц. */
@Composable
private fun CalendarDialog(
    initialDate: Long,
    isRussian: Boolean,
    minDate: Long?,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit,
) {
    val start = DateUtils.toLocalDate(initialDate)
    var year by remember { mutableStateOf(start.year) }
    var month by remember { mutableStateOf(start.monthNumber) }

    val monthNames = if (isRussian) RUSSIAN_MONTHS else ENGLISH_MONTHS
    val weekdayNames = if (isRussian) RUSSIAN_WEEKDAYS else ENGLISH_WEEKDAYS

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (isRussian) "Закрыть" else "Close") }
        },
        title = { Text(if (isRussian) "Выберите дату" else "Pick a date") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { shiftMonth(year, month, -1) { y, m -> year = y; month = m } }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null,
                        )
                    }
                    Text(
                        text = "${monthNames[month - 1]} $year",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = { shiftMonth(year, month, 1) { y, m -> year = y; month = m } }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdayNames.forEach { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                val days = DateUtils.daysInMonth(year, month)
                // Понедельник — первый столбец календаря.
                val offset = DateUtils.mondayFirstOffset(year, month)
                var cell = 0
                while (cell < 42) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        repeat(7) {
                            val dayNumber = cell - offset + 1
                            val date = if (dayNumber in 1..days) LocalDate(year, month, dayNumber) else null
                            DayCell(
                                date = date,
                                isSelected = date != null &&
                                    date.year == start.year &&
                                    date.monthNumber == start.monthNumber &&
                                    date.dayOfMonth == start.dayOfMonth,
                                isEnabled = date != null && (minDate == null || DateUtils.toEpochMillis(date) >= minDate),
                                onClick = { date?.let { onDateSelected(DateUtils.toEpochMillis(it)) } },
                            )
                            cell++
                        }
                    }
                }
            }
        },
    )
}

/** Квадрат одной даты в календаре. */
@Composable
private fun RowScope.DayCell(
    date: LocalDate?,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .clickable(enabled = isEnabled) { onClick() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/** Сдвиг месяца с переносом года. */
private fun shiftMonth(year: Int, month: Int, delta: Int, apply: (Int, Int) -> Unit) {
    val total = year * 12 + (month - 1) + delta
    apply(total / 12, total % 12 + 1)
}

private val RUSSIAN_MONTHS = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

private val ENGLISH_MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val RUSSIAN_WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val ENGLISH_WEEKDAYS = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
