@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package store.chekak.balik.ui.deposits

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import store.chekak.balik.core.DateUtils
import store.chekak.balik.core.formatMoney
import store.chekak.balik.core.parseMoneyToMinor
import store.chekak.balik.data.db.DepositEntity
import store.chekak.balik.data.db.DepositPayout
import store.chekak.balik.data.repository.DepositRepository
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.domain.DepositCalculator
import store.chekak.balik.domain.DepositProjection
import store.chekak.balik.domain.DepositTaxSettings
import store.chekak.balik.ui.LocalStrings
import store.chekak.balik.ui.components.DateField
import store.chekak.balik.ui.components.EmptyState
import store.chekak.balik.ui.navigation.LocalNavigator

/**
 * Вклады: список, добавление и правка.
 *
 * Сумма, ставка, срок и схема выплаты процентов. Рост и налог считает
 * `DepositCalculator` — по правилам НК РФ.
 */
@Composable
fun DepositsScreen() {
    val s = LocalStrings.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    val depositRepository = koinInject<DepositRepository>()
    val depositCalculator = koinInject<DepositCalculator>()
    val settingsRepository = koinInject<SettingsRepository>()

    val deposits by depositRepository.observeAll().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<DepositEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val today = remember { System.currentTimeMillis() }
    val taxSettings by produceState(
        initialValue = DepositTaxSettings(),
        deposits,
    ) {
        value = settingsRepository.getDepositTaxSettings()
    }

    val projection = remember(deposits, taxSettings, today) {
        // Считаем до конца срока самого «долгого» вклада — так видно все проценты.
        val toDate = deposits.maxOfOrNull { it.endDate } ?: today
        depositCalculator.project(
            deposits = deposits,
            fromDate = today,
            toDate = maxOf(toDate, today),
            tax = taxSettings,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.deposits) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editing = null
                    showEditor = true
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = s.addDeposit)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(s.depositsHint, style = MaterialTheme.typography.bodyMedium)

            if (deposits.isEmpty()) {
                EmptyState(title = s.depositsEmpty)
                return@Column
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(s.depositTotal, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = formatMoney(projection.totalPrincipalMinor),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "${s.depositInterest}: ${formatMoney(projection.totalInterestMinor)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${s.depositTax}: ${formatMoney(projection.totalTaxMinor)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            deposits.forEach { deposit ->
                DepositCard(
                    deposit = deposit,
                    projection = projection.deposits.firstOrNull { it.depositId == deposit.id },
                    onEdit = {
                        editing = deposit
                        showEditor = true
                    },
                    onDelete = {
                        scope.launch { depositRepository.delete(deposit) }
                    },
                )
            }
        }
    }

    if (showEditor) {
        DepositEditorDialog(
            deposit = editing,
            onDismiss = { showEditor = false },
            onSave = { deposit ->
                scope.launch {
                    depositRepository.upsert(deposit)
                    showEditor = false
                }
            },
        )
    }
}

/** Карточка вклада: условия и результат. */
@Composable
private fun DepositCard(
    deposit: DepositEntity,
    projection: DepositProjection?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Savings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(deposit.name, style = MaterialTheme.typography.titleMedium)
                    if (deposit.bankName.isNotBlank()) {
                        Text(
                            text = deposit.bankName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = s.edit)
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = s.deleteDeposit,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Text(
                text = "${s.depositAmount}: ${formatMoney(deposit.amountMinor, deposit.currency)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${s.depositRate}: ${deposit.annualRatePercent.formatRate()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${s.depositStart} — ${s.depositEnd}: " +
                    "${DateUtils.formatDate(deposit.startDate)} — ${DateUtils.formatDate(deposit.endDate)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${s.depositPayout}: ${payoutLabel(s, deposit.payout)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (projection != null && projection.interestMinor > 0L) {
                Text(
                    text = "${s.depositInterest}: ${formatMoney(projection.interestMinor, deposit.currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${s.depositTax}: ${formatMoney(projection.taxMinor, deposit.currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${s.depositNet}: ${formatMoney(projection.netInterestMinor, deposit.currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Диалог добавления и правки вклада. */
@Composable
private fun DepositEditorDialog(
    deposit: DepositEntity?,
    onDismiss: () -> Unit,
    onSave: (DepositEntity) -> Unit,
) {
    val s = LocalStrings.current
    val today = remember { System.currentTimeMillis() }

    var name by remember { mutableStateOf(deposit?.name ?: "") }
    var bank by remember { mutableStateOf(deposit?.bankName ?: "") }
    var amount by remember {
        mutableStateOf(
            if (deposit == null) "" else (deposit.amountMinor / 100.0).toString().replace('.', ','),
        )
    }
    var rate by remember {
        mutableStateOf(if (deposit == null) "" else deposit.annualRatePercent.toString().replace('.', ','))
    }
    var startDate by remember {
        mutableStateOf(deposit?.startDate ?: DateUtils.startOfDay(today))
    }
    var endDate by remember {
        mutableStateOf(
            deposit?.endDate ?: DateUtils.startOfDay(DateUtils.plusMonths(today, 6)),
        )
    }
    var payout by remember { mutableStateOf(deposit?.payout ?: DepositPayout.AT_END) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (deposit == null) s.addDeposit else s.editDeposit) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s.depositName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bank,
                    onValueChange = { bank = it },
                    label = { Text(s.depositBank) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(s.depositAmount) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text(s.depositRate) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = s.depositStart,
                    dateMillis = startDate,
                    onDateChange = { startDate = it },
                    isRussian = s.isRussian,
                )
                DateField(
                    label = s.depositEnd,
                    dateMillis = endDate,
                    onDateChange = { endDate = it },
                    isRussian = s.isRussian,
                    minDate = startDate,
                )
                Text(s.depositPayout, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DepositPayout.entries.forEach { option ->
                        FilterChip(
                            selected = payout == option,
                            onClick = { payout = option },
                            label = { Text(payoutLabel(s, option)) },
                        )
                    }
                }
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountMinor = parseMoneyToMinor(amount)
                    val ratePercent = rate.replace(',', '.').toDoubleOrNull()
                    when {
                        name.isBlank() -> error = s.depositFillName
                        amountMinor == null || amountMinor <= 0L -> error = s.depositFillAmount
                        ratePercent == null || ratePercent <= 0.0 -> error = s.depositFillRate
                        endDate <= startDate -> error = s.depositFillTerm
                        else -> {
                            error = null
                            onSave(
                                DepositEntity(
                                    id = deposit?.id ?: 0L,
                                    name = name.trim(),
                                    bankName = bank.trim(),
                                    amountMinor = amountMinor,
                                    annualRatePercent = ratePercent,
                                    startDate = startDate,
                                    endDate = endDate,
                                    payout = payout,
                                    currency = deposit?.currency ?: "RUB",
                                    createdAt = deposit?.createdAt ?: System.currentTimeMillis(),
                                ),
                            )
                        }
                    }
                },
            ) {
                Text(s.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        },
    )
}

private fun payoutLabel(s: store.chekak.balik.ui.Strings, payout: DepositPayout): String = when (payout) {
    DepositPayout.MONTHLY -> s.payoutMonthly
    DepositPayout.AT_END -> s.payoutAtEnd
    DepositPayout.CAPITALIZED -> s.payoutCapitalized
}

private fun Double.formatRate(): String =
    if (this == this.toLong().toDouble()) "${this.toLong()}%" else "${this}%".replace('.', ',')
