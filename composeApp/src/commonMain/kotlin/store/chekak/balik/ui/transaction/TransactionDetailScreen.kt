package store.chekak.balik.ui.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import store.chekak.balik.core.DateUtils
import store.chekak.balik.core.formatMoney
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.ui.navigation.LocalNavigator
import store.chekak.balik.ui.LocalStrings
import store.chekak.balik.ui.components.CategoryIcons
import store.chekak.balik.ui.components.parseColor

/**
 * Экран операции: полное редактирование и удаление.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(transactionId: Long) {
    val s = LocalStrings.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    val ledgerRepository = koinInject<LedgerRepository>()
    val settingsRepository = koinInject<SettingsRepository>()
    val vm = remember(scope) { TransactionDetailViewModel(ledgerRepository, scope, transactionId) }
    val state = vm.state

    var showDeleteDialog by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) navigator.pop()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.editOperation) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = s.deleteOperation)
                    }
                },
            )
        },
    ) { padding ->
        val transaction = state.transaction
        if (transaction == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.loading)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = amountToText(transaction.amountMinor),
                onValueChange = { text ->
                    val parsed = textToMinor(text)
                    if (parsed != null) vm.setAmount(parsed)
                },
                label = { Text(s.amount) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = dateToText(transaction.timestamp),
                onValueChange = { text ->
                    val parsed = DateUtils.parseDateTime(text)
                    if (parsed != null) vm.setTimestamp(parsed)
                },
                label = { Text(s.date) },
                singleLine = true,
                supportingText = { Text("ДД.ММ.ГГГГ ЧЧ:ММ") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = transaction.rawDescription,
                onValueChange = vm::setDescription,
                label = { Text(s.description) },
                modifier = Modifier.fillMaxWidth(),
            )

            androidx.compose.material3.OutlinedButton(
                onClick = { categoryMenuOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val category = state.categories.firstOrNull { it.id == transaction.categoryId }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    category?.let {
                        Icon(
                            imageVector = CategoryIcons.of(it.iconName),
                            contentDescription = null,
                            tint = parseColor(it.colorHex),
                        )
                        androidx.compose.foundation.layout.Spacer(
                            Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    Text(category?.name ?: s.selectCategory)
                }
            }
            DropdownMenu(
                expanded = categoryMenuOpen,
                onDismissRequest = { categoryMenuOpen = false },
            ) {
                state.categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            vm.setCategory(category.id)
                            categoryMenuOpen = false
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.isInternalTransfer, modifier = Modifier.weight(1f))
                Switch(
                    checked = transaction.isInternalTransfer,
                    onCheckedChange = vm::setInternalTransfer,
                )
            }

            Text(
                text = formatMoney(transaction.amountMinor, transaction.currency),
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            )

            Button(
                onClick = {
                    vm.save()
                    scope.launch { navigator.pop() }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.save)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(s.deleteConfirm) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    vm.delete()
                }) { Text(s.delete) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(s.cancel) }
            },
        )
    }
}

private fun amountToText(minorUnits: Long): String {
    val negative = minorUnits < 0
    val abs = if (negative) -minorUnits else minorUnits
    val whole = abs / 100
    val fraction = abs % 100
    return (if (negative) "-" else "") + "$whole.${fraction.toString().padStart(2, '0')}"
}

private fun textToMinor(text: String): Long? {
    val cleaned = text.replace(',', '.').replace(" ", "").replace("\u00A0", "")
    if (cleaned.isEmpty()) return null
    val value = cleaned.toDoubleOrNull() ?: return null
    return Math.round(value * 100.0).toLong()
}

private fun dateToText(timestamp: Long): String {
    val date = DateUtils.toLocalDateTime(timestamp)
    val day = date.dayOfMonth.toString().padStart(2, '0')
    val month = date.monthNumber.toString().padStart(2, '0')
    val hour = date.hour.toString().padStart(2, '0')
    val minute = date.minute.toString().padStart(2, '0')
    return "$day.$month.${date.year} $hour:$minute"
}
