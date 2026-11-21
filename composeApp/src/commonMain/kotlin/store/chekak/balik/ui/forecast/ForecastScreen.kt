@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package store.chekak.balik.ui.forecast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import store.chekak.balik.core.DateUtils
import store.chekak.balik.core.formatMoney
import store.chekak.balik.data.db.DepositEntity
import store.chekak.balik.data.repository.DepositRepository
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.domain.DepositCalculator
import store.chekak.balik.domain.DepositProjectionResult
import store.chekak.balik.domain.ForecastCalculator
import store.chekak.balik.domain.ForecastHorizon
import store.chekak.balik.domain.ForecastMethod
import store.chekak.balik.domain.HistoryPreset
import store.chekak.balik.ui.LocalStrings
import store.chekak.balik.ui.components.DateField
import store.chekak.balik.ui.components.EmptyState
import store.chekak.balik.ui.components.SummaryCard
import store.chekak.balik.ui.navigation.LocalNavigator
import store.chekak.balik.ui.navigation.Screen

/**
 * Прогноз: сколько денег будет на выбранную дату, если не менять темп расходов
 * и доходов, взятый из выбранного периода истории.
 */
@Composable
fun ForecastScreen() {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.current

    val ledgerRepository = koinInject<LedgerRepository>()
    val settingsRepository = koinInject<SettingsRepository>()
    val depositRepository = koinInject<DepositRepository>()
    val calculator = koinInject<ForecastCalculator>()
    val depositCalculator = koinInject<DepositCalculator>()

    val vm = remember(scope) {
        ForecastViewModel(
            ledgerRepository = ledgerRepository,
            settingsRepository = settingsRepository,
            depositRepository = depositRepository,
            forecastCalculator = calculator,
            depositCalculator = depositCalculator,
            scope = scope,
        )
    }
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(s.forecastTitle) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                return@Column
            }

            Text(s.forecastHint, style = MaterialTheme.typography.bodyMedium)

            Text(s.forecastMethod, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ForecastMethod.entries.forEach { method ->
                    FilterChip(
                        selected = state.method == method,
                        onClick = { vm.setMethod(method) },
                        label = { Text(methodLabel(s, method)) },
                    )
                }
            }

            Text(s.historyPeriod, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.preset == HistoryPreset.LAST_MONTH,
                    onClick = { vm.setPreset(HistoryPreset.LAST_MONTH) },
                    label = { Text(s.lastMonth) },
                )
                FilterChip(
                    selected = state.preset == HistoryPreset.THREE_MONTHS,
                    onClick = { vm.setPreset(HistoryPreset.THREE_MONTHS) },
                    label = { Text(s.threeMonths) },
                )
                FilterChip(
                    selected = state.preset == HistoryPreset.SIX_MONTHS,
                    onClick = { vm.setPreset(HistoryPreset.SIX_MONTHS) },
                    label = { Text(s.sixMonths) },
                )
                FilterChip(
                    selected = state.preset == HistoryPreset.ALL_TIME,
                    onClick = { vm.setPreset(HistoryPreset.ALL_TIME) },
                    label = { Text(s.allTime) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateField(
                    label = s.historyFrom,
                    dateMillis = state.historyFrom,
                    onDateChange = { vm.setHistoryRange(it, state.historyTo) },
                    modifier = Modifier.weight(1f),
                    isRussian = s.isRussian,
                )
                DateField(
                    label = s.historyTo,
                    dateMillis = state.historyTo,
                    onDateChange = { vm.setHistoryRange(state.historyFrom, it) },
                    modifier = Modifier.weight(1f),
                    isRussian = s.isRussian,
                )
            }

            Text(s.targetDateTitle, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ForecastHorizon.entries.forEach { horizon ->
                    FilterChip(
                        selected = state.targetDate == state.horizonDate(horizon),
                        onClick = { vm.setTargetHorizon(horizon) },
                        label = { Text(horizonLabel(s, horizon)) },
                    )
                }
            }
            DateField(
                label = s.targetDate,
                dateMillis = state.targetDate,
                onDateChange = { vm.setTargetDate(it) },
                isRussian = s.isRussian,
            )

            val result = state.result
            if (result == null || !result.hasData) {
                EmptyState(title = s.forecastNoData)
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    title = s.expensesUntilDate,
                    value = formatMoney(result.projectedExpenseMinor, result.currency),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.error,
                    icon = Icons.Filled.ArrowDownward,
                )
                SummaryCard(
                    title = s.incomesUntilDate,
                    value = formatMoney(result.projectedIncomeMinor, result.currency),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primary,
                    icon = Icons.Filled.ArrowUpward,
                )
            }

            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${s.balanceOnDate} (${DateUtils.formatDate(state.targetDate)})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = formatMoney(result.projectedBalanceMinor, result.currency),
                        style = MaterialTheme.typography.displaySmall,
                        color = if (result.projectedBalanceMinor >= 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        text = if (result.projectedBalanceMinor >= 0) s.willSave else s.willDebt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${s.daysAhead}: ${result.daysAhead}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${s.currentBalance}: ${formatMoney(result.currentBalanceMinor, result.currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${s.dailyExpenses}: ${formatMoney(result.dailyExpenseRate, result.currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${s.dailyIncomes}: ${formatMoney(result.dailyIncomeRate, result.currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${s.monthsAnalyzed}: ${result.monthsAnalyzed}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (result.segments.isNotEmpty()) {
                Text(s.monthlyBreakdown, style = MaterialTheme.typography.titleMedium)
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        result.segments.forEach { segment ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = segment.yearMonth,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = formatMoney(segment.expensesMinor, result.currency),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            DepositsBlock(
                deposits = state.deposits,
                projection = state.depositProjection,
                onManage = { navigator.push(Screen.Deposits) },
                onTaxSettings = { navigator.push(Screen.DepositTax) },
            )
        }
    }
}

/** Блок вкладов: рост, проценты и налог на них. */
@Composable
private fun DepositsBlock(
    deposits: List<DepositEntity>,
    projection: DepositProjectionResult,
    onManage: () -> Unit,
    onTaxSettings: () -> Unit,
) {
    val s = LocalStrings.current

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Savings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = s.deposits,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (deposits.isEmpty()) {
                Text(
                    text = s.depositsEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = s.depositsHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "${s.depositTotal}: ${formatMoney(projection.totalPrincipalMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${s.depositInterest}: ${formatMoney(projection.totalInterestMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${s.depositTax}: ${formatMoney(projection.totalTaxMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${s.depositNet}: ${formatMoney(projection.totalNetInterestMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onTaxSettings) { Text(s.taxSettings) }
                TextButton(onClick = onManage) {
                    Text(s.manageDeposits)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

private fun methodLabel(s: store.chekak.balik.ui.Strings, method: ForecastMethod): String = when (method) {
    ForecastMethod.MEDIAN -> s.methodMedian
    ForecastMethod.MEAN -> s.methodMean
    ForecastMethod.P85 -> s.methodP85
}

private fun horizonLabel(s: store.chekak.balik.ui.Strings, horizon: ForecastHorizon): String = when (horizon) {
    ForecastHorizon.WEEK -> s.horizonWeek
    ForecastHorizon.MONTH -> s.horizonMonth
    ForecastHorizon.QUARTER -> s.horizonQuarter
    ForecastHorizon.YEAR -> s.horizonYear
}
