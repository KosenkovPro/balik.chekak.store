package store.chekak.balik.ui.cash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import store.chekak.balik.core.DateUtils
import store.chekak.balik.core.formatMoney
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.ui.LocalStrings
import store.chekak.balik.ui.components.EmptyState

/**
 * Ведение наличных: пополнение, снятие и список операций.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashScreen() {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()

    val ledgerRepository = koinInject<LedgerRepository>()
    val vm = remember(scope) { CashViewModel(ledgerRepository, scope) }
    val state by vm.state.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var isTopUp by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text(s.cashTitle) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(s.cashBalance, style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = formatMoney(state.balanceMinor, state.currency),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                isTopUp = true
                                amountText = ""
                                showDialog = true
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(s.cashTopUp)
                        }
                        OutlinedButton(
                            onClick = {
                                isTopUp = false
                                amountText = ""
                                showDialog = true
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(s.cashWithdraw)
                        }
                    }
                }
            }

            if (state.transactions.isEmpty()) {
                EmptyState(title = s.cashNoOperations)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.transactions, key = { it.id }) { transaction ->
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = transaction.rawDescription,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = formatMoney(transaction.amountMinor, transaction.currency),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            Text(
                                text = formatDate(transaction.timestamp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (isTopUp) s.cashTopUp else s.cashWithdraw) },
            text = {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(s.amount) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val minor = amountText.replace(',', '.').toDoubleOrNull()
                        ?.let { Math.round(it * 100.0).toLong() }
                        ?: 0L
                    if (minor > 0) {
                        if (isTopUp) vm.topUp(minor) else vm.withdraw(minor)
                    }
                    showDialog = false
                }) { Text(s.ok) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(s.cancel) }
            },
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val date = DateUtils.toLocalDateTime(timestamp)
    return "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
}
