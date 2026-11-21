package store.chekak.balik.ui.import

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import store.chekak.balik.core.DateUtils
import store.chekak.balik.core.formatMoney
import store.chekak.balik.data.repository.ImportPreview
import store.chekak.balik.data.repository.ImportPreviewRow
import store.chekak.balik.domain.MergeAction
import store.chekak.balik.parser.StatementComparison.toComparable
import store.chekak.balik.ui.compare.ComparisonSource
import store.chekak.balik.ui.navigation.LocalNavigator
import store.chekak.balik.ui.navigation.Screen
import store.chekak.balik.ui.LocalStrings
import store.chekak.balik.ui.components.CategoryIcons
import store.chekak.balik.ui.components.parseColor

/**
 * Предпросмотр импорта: что будет загружено, сколько дублей пропущено,
 * можно поправить категорию и запомнить правило для похожих операций.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewScreen(initialPreview: ImportPreview) {
    val s = LocalStrings.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    val importRepository = koinInject<store.chekak.balik.data.repository.ImportRepository>()
    val vm = remember(scope) { ImportViewModel(importRepository, scope) }
    // Наблюдаемое состояние: без него экран не закрылся бы после импорта
    // и не показывал бы правку категории.
    val vmState by vm.state

    var preview by remember { mutableStateOf(initialPreview) }
    var categories by remember { mutableStateOf(emptyList<store.chekak.balik.data.db.CategoryEntity>()) }
    var categoryMenuRow by remember { mutableStateOf<ImportPreviewRow?>(null) }
    var rememberFlag by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.loadPreview(initialPreview)
        categories = importRepository.categories()
    }

    LaunchedEffect(vmState) {
        val current = vmState
        if (current is ImportUiState.Preview) {
            preview = current.preview
            categories = current.categories
        }
        if (current is ImportUiState.Done) {
            navigator.pop()
        }
        if (current is ImportUiState.Error) {
            navigator.pop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.importPreview) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { vm.commit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = preview.changedCount > 0,
            ) {
                Text("${s.importNow} (${preview.changedCount})")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${s.detectedBank}: ${preview.bankDisplayName}",
                    style = MaterialTheme.typography.titleMedium,
                )
                preview.periodStart?.let { from ->
                    val to = preview.periodEnd
                    Text(
                        text = "${s.statementPeriod}: ${formatTimestamp(from)}" +
                            (to?.let { " — ${formatTimestamp(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${s.newOperations}: ${preview.newCount} · ${s.updatedOperations}: ${preview.updatedCount} · " +
                        "${s.duplicates}: ${preview.duplicateCount} · ${s.unrecognized}: ${preview.unrecognizedRows}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (preview.ocrUsed) {
                    Text(
                        text = s.ocrRecognized,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    // Сверка с оригиналом: видно, насколько хорошо распознан скан,
                    // ещё до того, как операции попадут в базу.
                    OutlinedButton(
                        onClick = {
                            navigator.push(
                                Screen.OcrComparison(
                                    ComparisonSource.Preview(
                                        preview.rows.map { it.operation.toComparable() },
                                    ),
                                ),
                            )
                        },
                    ) {
                        Text(s.compareWithOriginal)
                    }
                }
                if (preview.changedCount == 0 && preview.rows.isNotEmpty()) {
                    Text(
                        text = s.importNothingToDo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(preview.rows, key = { it.fingerprint }) { row ->
                    val category = categories.firstOrNull { it.id == row.categoryId }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { categoryMenuRow = row }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.operation.merchantName.ifBlank { row.operation.rawDescription },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (row.isDuplicate) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    text = buildString {
                                        append(formatTimestamp(row.operation.timestamp))
                                        if (category != null) {
                                            append(" · ")
                                            append(category.name)
                                        }
                                        append(" · ")
                                        append(
                                            when (row.mergeAction) {
                                                MergeAction.ADD -> s.newBadge
                                                MergeAction.UPDATE -> s.updatedBadge
                                                MergeAction.SKIP -> s.duplicateBadge
                                            },
                                        )
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            category?.let {
                                Icon(
                                    imageVector = CategoryIcons.of(it.iconName),
                                    contentDescription = null,
                                    tint = parseColor(it.colorHex),
                                )
                            }
                            Text(
                                text = formatMoney(row.operation.amountMinor, row.operation.currency),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        if (categoryMenuRow?.fingerprint == row.fingerprint) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Switch(checked = rememberFlag, onCheckedChange = { rememberFlag = it })
                                Text(
                                    text = s.rememberRule,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    categoryMenuRow?.let { row ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { categoryMenuRow = null },
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        vm.setCategory(row, category.id)
                        if (rememberFlag) {
                            vm.rememberRule(row, category.id)
                        }
                        rememberFlag = false
                        categoryMenuRow = null
                    },
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = DateUtils.toLocalDateTime(timestamp)
    return "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
}
