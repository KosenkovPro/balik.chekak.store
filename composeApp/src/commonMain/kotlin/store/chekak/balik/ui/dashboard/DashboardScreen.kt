package store.chekak.balik.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import store.chekak.balik.core.formatMoney
import store.chekak.balik.pendingImportBytes
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.parser.ParseFailureReason
import store.chekak.balik.ui.navigation.LocalNavigator
import store.chekak.balik.ui.LocalStrings
import store.chekak.balik.ui.components.CategoryRow
import store.chekak.balik.ui.components.DonutChart
import store.chekak.balik.ui.components.EmptyState
import store.chekak.balik.ui.components.SummaryCard
import store.chekak.balik.ui.files.FilePicker
import store.chekak.balik.ui.files.FilePickerMode
import store.chekak.balik.ui.files.FilePickerRequest
import store.chekak.balik.ui.import.ImportUiState
import store.chekak.balik.ui.import.ImportViewModel
import store.chekak.balik.ui.navigation.Screen

/**
 * Дашборд: итоги за период, разбивка по категориям, балансы счетов и импорт выписки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val s = LocalStrings.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    val ledgerRepository = koinInject<LedgerRepository>()
    val settingsRepository = koinInject<SettingsRepository>()

    val vm = remember(scope) { DashboardViewModel(ledgerRepository, settingsRepository, scope) }
    val state by vm.state.collectAsState()

    val importRepository = koinInject<store.chekak.balik.data.repository.ImportRepository>()
    val importVm = remember(scope) { ImportViewModel(importRepository, scope) }
    // Состояние импорта — наблюдаемое, поэтому предпросмотр открыется сразу
    // после разбора файла, а не «когда получится».
    val importState by importVm.state
    // Причину ошибки показываем своей строкой: пользователю нужна подсказка,
    // а не техническое сообщение парсера.
    val importError = (importState as? ImportUiState.Error)?.let { error ->
        when (error.reason) {
            ParseFailureReason.SCAN_NOT_RECOGNIZED -> s.scanNotRecognized
            ParseFailureReason.NO_OPERATIONS -> s.noOperationsFound
            ParseFailureReason.REFERENCE_WITHOUT_TEXT -> s.referenceWithoutText
            null -> error.message
        }
    }
    var pickerRequest by remember { mutableStateOf<FilePickerRequest?>(null) }

    LaunchedEffect(importState) {
        val current = importState
        if (current is ImportUiState.Preview) {
            navigator.push(Screen.ImportPreview(current.preview))
        }
    }

    // Файл, переданный системой «Поделиться», импортируем сразу при открытии приложения.
    LaunchedEffect(Unit) {
        pendingImportBytes()?.let { (name, bytes) ->
            if (bytes.isNotEmpty()) {
                importVm.analyze(bytes, name)
            }
        }
    }

    FilePicker(
        request = pickerRequest,
        onResult = { picked ->
            pickerRequest = null
            if (picked != null && picked.bytes.isNotEmpty()) {
                importVm.analyze(picked.bytes, picked.name)
            }
        },
    )

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text(s.appName) },
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (importState is ImportUiState.Analyzing) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (importError != null) {
                item {
                    Text(
                        text = importError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.period == DashboardPeriod.CURRENT_MONTH,
                        onClick = { vm.setPeriod(DashboardPeriod.CURRENT_MONTH) },
                        label = { Text(s.periodCurrentMonth) },
                    )
                    FilterChip(
                        selected = state.period == DashboardPeriod.LAST_MONTH,
                        onClick = { vm.setPeriod(DashboardPeriod.LAST_MONTH) },
                        label = { Text(s.periodLastMonth) },
                    )
                    FilterChip(
                        selected = state.period == DashboardPeriod.ALL_TIME,
                        onClick = { vm.setPeriod(DashboardPeriod.ALL_TIME) },
                        label = { Text(s.all) },
                    )
                }
            }

            if (state.accounts.size > 1) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.accounts.forEach { account ->
                            FilterChip(
                                selected = account.id in state.selectedAccountIds,
                                onClick = { vm.toggleAccount(account.id) },
                                label = {
                                    Text(
                                        account.cardLast4?.let { "••${it}" }
                                            ?: account.accountName.take(12),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            if (state.summary.isEmpty) {
                item {
                    EmptyState(
                        title = s.noData,
                        subtitle = s.noDataHint,
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
            } else {
                state.summary.currencies.forEach { currencySummary ->
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SummaryCard(
                                title = s.expenses,
                                value = formatMoney(currencySummary.expensesMinor, currencySummary.currency),
                                modifier = Modifier.weight(1f),
                                accent = MaterialTheme.colorScheme.error,
                                icon = Icons.Filled.ArrowDownward,
                            )
                            SummaryCard(
                                title = s.incomes,
                                value = formatMoney(currencySummary.incomesMinor, currencySummary.currency),
                                modifier = Modifier.weight(1f),
                                accent = MaterialTheme.colorScheme.primary,
                                icon = Icons.Filled.ArrowUpward,
                            )
                        }
                    }

                    if (currencySummary.byCategory.isNotEmpty()) {
                        item {
                            Card(
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                ) {
                                    DonutChart(
                                        slices = currencySummary.byCategory,
                                        currency = currencySummary.currency,
                                        centerLabel = s.expenses,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    currencySummary.byCategory.forEach { slice ->
                                        CategoryRow(
                                            slice = slice,
                                            currency = currencySummary.currency,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.balances.isNotEmpty()) {
                item {
                    Text(
                        text = s.balance,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(state.balances, key = { it.account.id }) { balance ->
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = balance.account.accountName.ifBlank { balance.account.bankName },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = balance.account.bankName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = balance.balanceMinor?.let {
                                    formatMoney(it, balance.account.currency)
                                } ?: "—",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    ExtendedFloatingActionButton(
        onClick = {
            pickerRequest = FilePickerRequest(
                id = System.currentTimeMillis(),
                mode = FilePickerMode.Open,
                mimeTypes = listOf(
                    "text/csv",
                    "text/comma-separated-values",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/pdf",
                    "text/plain",
                    "*/*",
                ),
            )
        },
        text = { Text(s.importStatement) },
        icon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp),
    )
    }
}
