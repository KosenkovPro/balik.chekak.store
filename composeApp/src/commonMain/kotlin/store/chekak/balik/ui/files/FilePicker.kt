package store.chekak.balik.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/** Режим системного диалога файлов. */
enum class FilePickerMode { Open, Save }

/** Запрос на открытие или сохранение файла. */
@Immutable
data class FilePickerRequest(
    /** Меняется при каждом новом запросе — по нему запускается диалог. */
    val id: Long,
    val mode: FilePickerMode,
    val mimeTypes: List<String> = emptyList(),
    val suggestedName: String? = null,
)

/** Выбранный файл с содержимым. */
class PickedFile(
    val name: String,
    val bytes: ByteArray,
    val mimeType: String? = null,
)

/**
 * Системный диалог выбора/сохранения файла: на Android — Storage Access Framework,
 * на iOS — UIDocumentPickerViewController.
 */
@Composable
expect fun FilePicker(
    request: FilePickerRequest?,
    onResult: (PickedFile?) -> Unit,
)
