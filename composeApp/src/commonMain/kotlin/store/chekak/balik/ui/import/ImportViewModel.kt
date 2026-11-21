package store.chekak.balik.ui.import

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.data.repository.ImportPreview
import store.chekak.balik.data.repository.ImportPreviewRow
import store.chekak.balik.data.repository.ImportRepository
import store.chekak.balik.parser.ParseFailureReason
import store.chekak.balik.parser.StatementParseException

/** Состояние экрана импорта. */
sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Analyzing : ImportUiState
    data object Importing : ImportUiState

    @Immutable
    data class Preview(
        val preview: ImportPreview,
        val categories: List<CategoryEntity>,
    ) : ImportUiState

    @Immutable
    data class Done(val imported: Int, val updated: Int, val duplicates: Int) : ImportUiState

    @Immutable
    data class Error(
        val message: String,
        /** Причина ошибки — по ней интерфейс показывает понятную подсказку. */
        val reason: ParseFailureReason? = null,
    ) : ImportUiState
}

/**
 * Импорт выписки: разбор файла → предпросмотр (с дублями и распознанными категориями)
 * → импорт с фиксацией батча для последующего отката.
 *
 * Состояние — именно объект состояния Compose ([MutableState]), а не обычное поле:
 * экран должен реагировать на смену состояния (открыть предпросмотр, показать
 * ошибку, закрыть экран после импорта). С обычным `var` Compose не видит
 * изменений — предпросмотр просто не открывается, а файл «пропадает».
 */
class ImportViewModel(
    private val importRepository: ImportRepository,
    private val scope: CoroutineScope,
) {
    /**
     * Текущее состояние импорта.
     *
     * Наблюдаемое: экран читает его прямо в composable-функции, поэтому любая
     * смена состояния перерисовывает экран и перезапускает `LaunchedEffect`.
     */
    val state: MutableState<ImportUiState> = mutableStateOf<ImportUiState>(ImportUiState.Idle)

    fun analyze(bytes: ByteArray, fileName: String) {
        state.value = ImportUiState.Analyzing
        scope.launch {
            state.value = runCatching {
                val preview = importRepository.analyze(bytes, fileName)
                ImportUiState.Preview(preview, importRepository.categories())
            }.getOrElse { error ->
                ImportUiState.Error(
                    message = error.message ?: "Не удалось разобрать файл",
                    reason = (error as? StatementParseException)?.reason,
                )
            }
        }
    }

    /** Загружает уже разобранный предпросмотр (например, переданный навигацией). */
    fun loadPreview(preview: ImportPreview) {
        scope.launch {
            state.value = ImportUiState.Preview(preview, importRepository.categories())
        }
    }

    fun setCategory(row: ImportPreviewRow, categoryId: Long?) {
        val current = state.value as? ImportUiState.Preview ?: return
        state.value = ImportUiState.Preview(
            preview = current.preview.copy(
                rows = current.preview.rows.map { existing ->
                    if (existing.fingerprint == row.fingerprint) {
                        existing.copy(categoryId = categoryId, manuallyChanged = true)
                    } else {
                        existing
                    }
                },
            ),
            categories = current.categories,
        )
    }

    /** Создаёт правило и пересчитывает категории остальных строк предпросмотра. */
    fun rememberRule(row: ImportPreviewRow, categoryId: Long) {
        val current = state.value as? ImportUiState.Preview ?: return
        scope.launch {
            val updated = importRepository.rememberRule(current.preview, row, categoryId)
            state.value = ImportUiState.Preview(updated, current.categories)
        }
    }

    fun commit() {
        val current = state.value as? ImportUiState.Preview ?: return
        state.value = ImportUiState.Importing
        scope.launch {
            state.value = runCatching {
                val batch = importRepository.commit(current.preview)
                ImportUiState.Done(batch.newRows, batch.updatedRows, batch.skippedDuplicates)
            }.getOrElse { error ->
                ImportUiState.Error(error.message ?: "Не удалось сохранить импорт")
            }
        }
    }

    fun reset() {
        state.value = ImportUiState.Idle
    }
}
