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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import store.chekak.balik.core.Money
import store.chekak.balik.core.formatMoney
import store.chekak.balik.data.db.BudgetEntity
import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.ui.components.EmptyState
import store.chekak.balik.ui.navigation.LocalNavigator
import store.chekak.balik.ui.strings

/**
 * Месячные лимиты по категориям. Фактически потраченное считается по операциям,
 * здесь показываем сам лимит.
 */
@Composable
fun BudgetsScreen(vm: SettingsViewModel) {
    val s = strings
    val navigator = LocalNavigator.current
    val budgets by vm.budgets.collectAsState()
    val categories by vm.categories.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { SettingsTopBar(title = s.budgets, onBack = { navigator.pop() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = s.add)
            }
        },
    ) { padding ->
        if (budgets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(title = s.budgetsEmpty)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = budgets, key = { it.id }) { budget ->
                    BudgetCard(
                        budget = budget,
                        categoryName = categories.firstOrNull { it.id == budget.categoryId }?.name ?: s.all,
                        onDelete = { vm.deleteBudget(budget) },
                    )
                }
            }
        }
    }

    if (showDialog) {
        BudgetDialog(
            categories = categories,
            onDismiss = { showDialog = false },
            onSave = { categoryId, limitMinor ->
                vm.upsertBudget(
                    BudgetEntity(
                        categoryId = categoryId,
                        limitMinor = limitMinor,
                        currency = "RUB",
                        period = "MONTHLY",
                    ),
                )
                showDialog = false
            },
        )
    }
}

/** Экран лимитов со своим ViewModel. */
@Composable
fun BudgetsScreen() {
    BudgetsScreen(rememberSettingsViewModel())
}

/** Карточка лимита по категории. */
@Composable
private fun BudgetCard(
    budget: BudgetEntity,
    categoryName: String,
    onDelete: () -> Unit,
) {
    val s = strings
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = categoryName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${s.budgetLimit}: ${formatMoney(budget.limitMinor, budget.currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = s.delete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Диалог добавления лимита: категория и сумма в рублях. */
@Composable
private fun BudgetDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (categoryId: Long, limitMinor: Long) -> Unit,
) {
    val s = strings
    var categoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: 0L) }
    var amountText by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }

    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val amount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = s.budgets) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = s.category, style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(onClick = { menuExpanded = true }) {
                        Text(text = selectedCategory?.name ?: s.selectCategory)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(text = category.name) },
                                onClick = {
                                    categoryId = category.id
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        amountText = input.filter { it.isDigit() || it == '.' || it == ',' }
                    },
                    label = { Text(text = s.budgetLimit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (amount > 0.0) {
                    Text(
                        text = formatMoney(Money.ofMajor(amount).minorUnits, "RUB"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(categoryId, Money.ofMajor(amount).minorUnits)
                },
                enabled = amount > 0.0 && categoryId != 0L,
            ) {
                Text(text = s.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = s.cancel)
            }
        },
    )
}
