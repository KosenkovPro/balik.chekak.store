@file:OptIn(ExperimentalMaterial3Api::class)

package store.chekak.balik.ui.settings

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
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import store.chekak.balik.core.DateUtils
import store.chekak.balik.data.db.ImportBatchEntity
import store.chekak.balik.ui.components.EmptyState
import store.chekak.balik.ui.compare.ComparisonSource
import store.chekak.balik.ui.navigation.LocalNavigator
import store.chekak.balik.ui.navigation.Screen
import store.chekak.balik.ui.strings

/**
 * Журнал импортов: какие файлы загружались и сколько операций добавилось.
 */
@Composable
fun ImportHistoryScreen(vm: SettingsViewModel) {
    val s = strings
    val navigator = LocalNavigator.current
    val batches by vm.importBatches.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingRollback by remember { mutableStateOf<ImportBatchEntity?>(null) }

    Scaffold(
        topBar = { SettingsTopBar(title = s.importHistory, onBack = { navigator.pop() }) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        if (batches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(title = s.noResults)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = batches, key = { it.id }) { batch ->
                    ImportBatchCard(
                        batch = batch,
                        onRollback = { pendingRollback = batch },
                        onCompare = {
                            navigator.push(Screen.OcrComparison(ComparisonSource.Batch(batch.id)))
                        },
                    )
                }
            }
        }
    }

    val batch = pendingRollback
    if (batch != null) {
        AlertDialog(
            onDismissRequest = { pendingRollback = null },
            title = { Text(text = s.rollback) },
            text = { Text(text = batch.fileName) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.rollbackBatch(batch.id)
                        pendingRollback = null
                        scope.launch {
                            snackbarHostState.showSnackbar(s.rollbackDone)
                        }
                    },
                ) {
                    Text(text = s.ok)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRollback = null }) {
                    Text(text = s.cancel)
                }
            },
        )
    }
}

/** Экран журнала импортов со своим ViewModel. */
@Composable
fun ImportHistoryScreen() {
    ImportHistoryScreen(rememberSettingsViewModel())
}

/** Карточка одного импорта. */
@Composable
private fun ImportBatchCard(
    batch: ImportBatchEntity,
    onRollback: () -> Unit,
    onCompare: () -> Unit,
) {
    val s = strings
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = batch.fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = batch.bankName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatTimestamp(batch.importedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${s.newOperations}: ${batch.newRows} · " +
                    "${s.updatedOperations}: ${batch.updatedRows} · " +
                    "${s.skippedDuplicates}: ${batch.skippedDuplicates} · " +
                    "${s.rows}: ${batch.totalRows}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCompare) {
                    Icon(
                        imageVector = Icons.Filled.CompareArrows,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(text = s.compareWithOriginal)
                }
                TextButton(onClick = onRollback) {
                    Icon(
                        imageVector = Icons.Filled.Undo,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(text = s.rollback)
                }
            }
        }
    }
}

/** Дата и время импорта в формате ДД.ММ.ГГГГ ЧЧ:ММ. */
private fun formatTimestamp(epochMillis: Long): String {
    val dateTime = DateUtils.toLocalDateTime(epochMillis)
    val day = dateTime.dayOfMonth.toString().padStart(2, '0')
    val month = dateTime.monthNumber.toString().padStart(2, '0')
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    return "$day.$month.${dateTime.year} $hour:$minute"
}
