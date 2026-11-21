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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import store.chekak.balik.core.DateUtils
import store.chekak.balik.data.db.TransactionEntity
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.domain.Fingerprint
import store.chekak.balik.ui.navigation.LocalNavigator
import store.chekak.balik.ui.LocalStrings

/**
 * Ручное добавление операции — в том числе наличных (выбор счёта «Наличные»).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTransactionScreen() {
    val s = LocalStrings.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    val ledgerRepository = koinInject<LedgerRepository>()

    val accounts = remember { mutableStateOf(emptyList<store.chekak.balik.data.db.AccountEntity>()) }
    val categories = remember { mutableStateOf(emptyList<store.chekak.balik.data.db.CategoryEntity>()) }

    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var accountMenuOpen by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val allAccounts = ledgerRepository.getAccounts()
        val cash = ledgerRepository.ensureCashAccount()
        accounts.value = allAccounts + listOf(cash).filter { candidate -> allAccounts.none { it.id == candidate.id } }
        categories.value = ledgerRepository.getCategories()
        accountId = accounts.value.firstOrNull()?.id
        categoryId = categories.value.firstOrNull()?.id
        val now = DateUtils.toLocalDateTime(System.currentTimeMillis())
        dateText = "${now.dayOfMonth.toString().padStart(2, '0')}.${now.monthNumber.toString().padStart(2, '0')}.${now.year} " +
            "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.newOperation) },
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isExpense,
                    onClick = { isExpense = true },
                    label = { Text(s.expense) },
                )
                FilterChip(
                    selected = !isExpense,
                    onClick = { isExpense = false },
                    label = { Text(s.income) },
                )
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text(s.amount) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                label = { Text(s.date) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(s.description) },
                modifier = Modifier.fillMaxWidth(),
            )

            androidx.compose.material3.OutlinedButton(
                onClick = { accountMenuOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val account = accounts.value.firstOrNull { it.id == accountId }
                Text(account?.accountName ?: s.account)
            }
            DropdownMenu(expanded = accountMenuOpen, onDismissRequest = { accountMenuOpen = false }) {
                accounts.value.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.accountName) },
                        onClick = {
                            accountId = account.id
                            accountMenuOpen = false
                        },
                    )
                }
            }

            androidx.compose.material3.OutlinedButton(
                onClick = { categoryMenuOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val category = categories.value.firstOrNull { it.id == categoryId }
                Text(category?.name ?: s.selectCategory)
            }
            DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                categories.value.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            categoryId = category.id
                            categoryMenuOpen = false
                        },
                    )
                }
            }

            Button(
                onClick = {
                    val minor = amountText.replace(',', '.').toDoubleOrNull()?.let {
                        Math.round(it * 100.0).toLong()
                    } ?: 0L
                    val signed = if (isExpense) -kotlin.math.abs(minor) else kotlin.math.abs(minor)
                    val timestamp = DateUtils.parseDateTime(dateText) ?: System.currentTimeMillis()
                    val targetAccount = accountId ?: accounts.value.firstOrNull()?.id ?: return@Button
                    scope.launch {
                        ledgerRepository.upsertTransaction(
                            TransactionEntity(
                                fingerprint = Fingerprint.of("manual", timestamp, signed, "RUB", description),
                                accountId = targetAccount,
                                timestamp = timestamp,
                                amountMinor = signed,
                                currency = "RUB",
                                rawDescription = description,
                                merchantName = description,
                                categoryId = categoryId,
                                isInternalTransfer = false,
                                isManual = true,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                        navigator.pop()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.save)
            }
        }
    }
}
