package store.chekak.balik.ui.files

import androidx.compose.runtime.Composable

/** На десктопе системный диалог файлов не используется — импорт только на устройствах. */
@Composable
actual fun FilePicker(
    request: FilePickerRequest?,
    onResult: (PickedFile?) -> Unit,
) {
    // Намеренно пусто: desktop-таргет используется только для юнит-тестов.
}
