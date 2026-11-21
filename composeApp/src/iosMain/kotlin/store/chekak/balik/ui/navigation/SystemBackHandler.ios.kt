package store.chekak.balik.ui.navigation

import androidx.compose.runtime.Composable

/** На iOS системной кнопки «Назад» нет. */
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Ничего не делаем.
}
