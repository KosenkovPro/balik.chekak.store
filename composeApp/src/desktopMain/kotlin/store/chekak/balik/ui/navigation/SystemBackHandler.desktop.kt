package store.chekak.balik.ui.navigation

import androidx.compose.runtime.Composable

/** На десктопе системной кнопки «Назад» нет. */
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Ничего не делаем.
}
