package store.chekak.balik.ui.compare

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.parser.ComparableOperation
import store.chekak.balik.parser.ComparisonReport
import store.chekak.balik.parser.ParserRegistry
import store.chekak.balik.parser.StatementComparison
import store.chekak.balik.parser.StatementComparison.toComparable
import store.chekak.balik.parser.StatementParseException

/**
 * Откуда берётся распознанная сторона сверки.
 */
sealed interface ComparisonSource {
    /** Операции из предпросмотра импорта — сверка до импорта, с самим сканом. */
    data class Preview(val operations: List<ComparableOperation>) : ComparisonSource

    /** Операции уже импортированного батча — сверка после импорта. */
    data class Batch(val batchId: Long) : ComparisonSource
}

/** Состояние экрана сверки. */
sealed interface ComparisonUiState {
    /** Файл оригинала ещё не выбран. */
    data object Idle : ComparisonUiState

    /** Оригинал разбирается. */
    data object Analyzing : ComparisonUiState

    /** Отчёт готов. */
    @Immutable
    data class Report(
        val report: ComparisonReport,
        val referenceFileName: String,
        val bankDisplayName: String,
    ) : ComparisonUiState

    /** Оригинал разобрать не удалось. */
    @Immutable
    data class Error(val message: String) : ComparisonUiState
}

/**
 * Сверка распознанного текста с оригиналом выписки.
 *
 * Распознанная сторона — это операции, которые приложение уже получило из скана
 * (из предпросмотра или из базы). Оригинал пользователь выбирает сам: обычно это
 * та же выписка, но с текстовым слоем — из неё банковский текст берётся точно.
 */
class OcrComparisonViewModel(
    private val parserRegistry: ParserRegistry,
    private val ledgerRepository: LedgerRepository,
    private val scope: CoroutineScope,
    private val source: ComparisonSource,
) {
    /** Состояние экрана — Compose перерисовывается при его смене. */
    val state: MutableState<ComparisonUiState> = mutableStateOf(ComparisonUiState.Idle)

    private val loadJob = scope.launch {
        actual = when (source) {
            is ComparisonSource.Preview -> source.operations
            is ComparisonSource.Batch -> ledgerRepository.getTransactionsByBatch(source.batchId)
                .map { transaction ->
                    ComparableOperation(
                        timestamp = transaction.timestamp,
                        amountMinor = transaction.amountMinor,
                        currency = transaction.currency,
                        description = transaction.rawDescription,
                        merchant = transaction.merchantName,
                    )
                }
        }
    }

    private var actual: List<ComparableOperation> = emptyList()

    /** Разбирает выбранный оригинал и сравнивает его с распознанными операциями. */
    fun compareWith(bytes: ByteArray, fileName: String) {
        state.value = ComparisonUiState.Analyzing
        scope.launch {
            // Распознанные операции могли ещё грузиться из базы — ждём их.
            loadJob.join()
            state.value = runCatching {
                // Оригинал разбираем без OCR: сверять нужно с настоящим текстом.
                val statement = parserRegistry.parse(bytes, fileName, allowOcr = false)
                val report = StatementComparison.compare(
                    reference = statement.operations.map { it.toComparable() },
                    actual = actual,
                )
                ComparisonUiState.Report(
                    report = report,
                    referenceFileName = fileName,
                    bankDisplayName = statement.bankDisplayName,
                )
            }.getOrElse { error ->
                val message = when (error) {
                    is StatementParseException -> when (error.reason) {
                        store.chekak.balik.parser.ParseFailureReason.REFERENCE_WITHOUT_TEXT ->
                            "В оригинале нет текстового слоя: нужен PDF с текстом, CSV или XLSX"

                        else -> error.message ?: "Не удалось разобрать оригинал"
                    }

                    else -> error.message ?: "Не удалось разобрать оригинал"
                }
                ComparisonUiState.Error(message)
            }
        }
    }
}
