package store.chekak.balik.ui.transaction

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.ui.navigation.LocalNavigator
import store.chekak.balik.ui.LocalStrings
import store.chekak.balik.ui.components.CategoryIcons
import store.chekak.balik.ui.components.EmptyState
import store.chekak.balik.ui.components.parseColor
import store.chekak.balik.ui.navigation.Screen

/**
 * Лента операций: поиск, фильтр по категории и по статусу «перевод себе».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen() {
    val s = LocalStrings.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    val ledgerRepository = koinInject<LedgerRepository>()
    val vm = remember(scope) { TransactionsViewModel(ledgerRepository, scope) }
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.transactions) },
                actions = {
                    IconButton(onClick = { vm.toggleOnlyInternalTransfers() }) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = s.filterInternal,
                            tint = if (state.filters.onlyInternalTransfers) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navigator.push(Screen.ManualTransaction) }) {
                Icon(Icons.Filled.Add, contentDescription = s.addManual)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.filters.query,
                onValueChange = vm::setQuery,
                label = { Text(s.search) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                FilterChip(
                    selected = state.filters.categoryId == null,
                    onClick = { vm.setCategory(null) },
                    label = { Text(s.allCategories) },
                )
                FilterChip(
                    selected = state.filters.onlyInternalTransfers,
                    onClick = { vm.toggleOnlyInternalTransfers() },
                    label = { Text(s.filterInternal) },
                )
            }

            if (state.items.isEmpty()) {
                EmptyState(title = s.noResults, subtitle = s.noDataHint)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { transaction ->
                        val category = state.categories.firstOrNull { it.id == transaction.categoryId }
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = transaction.merchantName.ifBlank { transaction.rawDescription },
                                    maxLines = 1,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = buildString {
                                        append(formatDate(transaction.timestamp))
                                        if (category != null) {
                                            append(" · ")
                                            append(category.name)
                                        }
                                    },
                                )
                            },
                            leadingContent = {
                                category?.let {
                                    Icon(
                                        imageVector = CategoryIcons.of(it.iconName),
                                        contentDescription = null,
                                        tint = parseColor(it.colorHex),
                                    )
                                }
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatMoney(transaction.amountMinor, transaction.currency),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (transaction.amountMinor < 0) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                    )
                                    if (transaction.isInternalTransfer) {
                                        Text(
                                            text = s.internalTransferBadge,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                navigator.push(Screen.TransactionDetail(transaction.id))
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val date = DateUtils.toLocalDateTime(timestamp)
    return "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
}
