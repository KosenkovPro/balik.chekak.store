package store.chekak.balik.ui.navigation

import androidx.compose.runtime.Composable

/**
 * Обработка системной кнопки «Назад».
 *
 * На Android кнопка возвращает на предыдущий экран приложения, а не закрывает
 * приложение. На десктопе и iOS системной кнопки «Назад» нет, поэтому там
 * обработчик ничего не делает.
 */
@Composable
expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
