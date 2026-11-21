package store.chekak.balik.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import store.chekak.balik.core.DateUtils
import store.chekak.balik.core.formatMoney
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.parser.ComparisonRow
import store.chekak.balik.parser.ComparisonStatus
import store.chekak.balik.parser.ParserRegistry
import store.chekak.balik.ui.LocalStrings
import store.chekak.balik.ui.Strings
import store.chekak.balik.ui.components.EmptyState
import store.chekak.balik.ui.files.FilePicker
import store.chekak.balik.ui.files.FilePickerMode
import store.chekak.balik.ui.files.FilePickerRequest
import store.chekak.balik.ui.navigation.LocalNavigator

/**
 * Сверка распознанного текста с оригиналом выписки.
 *
 * Пользователь выбирает оригинал (ту же выписку, но с текстовым слоем), а
 * приложение показывает, насколько совпало распознанное: сколько описаний
 * совпало дословно, а где и как отличается.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrComparisonScreen(source: ComparisonSource) {
    val s = LocalStrings.current
    val navigator = LocalNavigator.current

    val parserRegistry = koinInject<ParserRegistry>()
    val ledgerRepository = koinInject<LedgerRepository>()
    val scope = rememberCoroutineScope()
    val vm = remember(source, scope) {
        OcrComparisonViewModel(parserRegistry, ledgerRepository, scope, source)
    }
    val state by vm.state

    var pickerRequest by remember { mutableStateOf<FilePickerRequest?>(null) }
    var onlyMismatches by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.ocrComparison) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val current = state) {
                is ComparisonUiState.Idle -> IdleContent(
                    onPick = {
                        pickerRequest = FilePickerRequest(
                            id = System.currentTimeMillis(),
                            mode = FilePickerMode.Open,
                            mimeTypes = listOf(
                                "application/pdf",
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            ),
                        )
                    },
                )

                is ComparisonUiState.Analyzing -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(s.comparingWithOriginal, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is ComparisonUiState.Error -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = {
                        pickerRequest = FilePickerRequest(
                            id = System.currentTimeMillis(),
                            mode = FilePickerMode.Open,
                            mimeTypes = listOf(
                                "application/pdf",
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            ),
                        )
                    }) {
                        Text(s.pickOriginalFile)
                    }
                }

                is ComparisonUiState.Report -> ReportContent(
                    state = current,
                    onlyMismatches = onlyMismatches,
                    onToggleMismatches = { onlyMismatches = it },
                    onPickAnother = {
                        pickerRequest = FilePickerRequest(
                            id = System.currentTimeMillis(),
                            mode = FilePickerMode.Open,
                            mimeTypes = listOf(
                                "application/pdf",
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            ),
                        )
                    },
                )
            }
        }
    }

    FilePicker(
        request = pickerRequest,
        onResult = { picked ->
            pickerRequest = null
            if (picked != null) {
                vm.compareWith(picked.bytes, picked.name)
            }
        },
    )
}

/** Объяснение и кнопка выбора оригинала. */
@Composable
private fun IdleContent(onPick: () -> Unit) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = s.comparisonIntro,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onPick) {
            Text(s.pickOriginalFile)
        }
    }
}

/** Отчёт: сводка и список расхождений. */
@Composable
private fun ReportContent(
    state: ComparisonUiState.Report,
    onlyMismatches: Boolean,
    onToggleMismatches: (Boolean) -> Unit,
    onPickAnother: () -> Unit,
) {
    val s = LocalStrings.current
    val report = state.report
    val rows = if (onlyMismatches) report.mismatches else report.rows

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${s.originalFile}: ${state.referenceFileName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${s.operationsInOriginal}: ${report.referenceCount} · " +
                            "${s.recognizedOperations}: ${report.actualCount}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${s.exactMatches}: ${report.exactCount} " +
                            "(${percentText(report.exactShare, 1)})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${s.averageSimilarity}: ${percentText(report.averageSimilarity, 1)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${s.mismatches}: ${report.mismatchCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (report.mismatchCount == 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    if (report.mismatchCount == 0) {
                        Text(
                            text = s.noMismatches,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = onPickAnother) {
                            Text(s.pickAnotherFile)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = onlyMismatches,
                    onClick = { onToggleMismatches(true) },
                    label = { Text(s.onlyMismatches) },
                )
                FilterChip(
                    selected = !onlyMismatches,
                    onClick = { onToggleMismatches(false) },
                    label = { Text(s.showAllRows) },
                )
            }
        }

        if (rows.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(title = s.noResults)
                }
            }
        } else {
            items(rows) { row ->
                ComparisonRowCard(row)
            }
        }
    }
}

/** Одна операция сверки: что ожидалось и что получилось. */
@Composable
private fun ComparisonRowCard(row: ComparisonRow) {
    val s = LocalStrings.current
    val operation = row.reference ?: row.actual
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = DateUtils.formatDate(operation?.timestamp ?: 0L),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatMoney(operation?.amountMinor ?: 0L, operation?.currency ?: "RUB"),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = s.statusOf(row.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (row.status) {
                        ComparisonStatus.EXACT -> MaterialTheme.colorScheme.primary
                        ComparisonStatus.SIMILAR -> MaterialTheme.colorScheme.tertiary
                        ComparisonStatus.DIFFERENT,
                        ComparisonStatus.ONLY_REFERENCE,
                        ComparisonStatus.ONLY_ACTUAL,
                        -> MaterialTheme.colorScheme.error
                    },
                )
            }

            val referenceText = row.reference?.description.orEmpty()
            val actualText = row.actual?.description.orEmpty()
            if (referenceText.isNotEmpty()) {
                Text(
                    text = "${s.inOriginal}: $referenceText",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (actualText.isNotEmpty() && actualText != referenceText) {
                Text(
                    text = "${s.inRecognized}: $actualText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.reference == null) {
                Text(
                    text = s.notInOriginal,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.actual == null) {
                Text(
                    text = s.notRecognized,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (row.status != ComparisonStatus.EXACT && row.reference != null && row.actual != null) {
                Text(
                    text = "${s.similarity}: ${percentText(row.similarity)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Подпись результата сверки одной операции. */
private fun Strings.statusOf(status: ComparisonStatus): String = when (status) {
    ComparisonStatus.EXACT -> statusExact
    ComparisonStatus.SIMILAR -> statusSimilar
    ComparisonStatus.DIFFERENT -> statusDifferent
    ComparisonStatus.ONLY_REFERENCE -> statusOnlyReference
    ComparisonStatus.ONLY_ACTUAL -> statusOnlyActual
}

/**
 * Процент в виде «96 %» или «99.8 %».
 *
 * Разделитель всегда точка: в русской локали `format` даёт запятую, а числа в
 * отчёте о распознавании удобнее сравнивать между устройствами.
 */
private fun percentText(value: Float, decimals: Int = 0): String {
    val text = if (decimals == 0) {
        "%.0f".format(value * 100)
    } else {
        "%.${decimals}f".format(value * 100)
    }
    return "$text %".replace(',', '.')
}
