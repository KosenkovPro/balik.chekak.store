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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.data.db.CategoryRuleEntity
import store.chekak.balik.data.db.RuleMatchType
import store.chekak.balik.ui.components.EmptyState
import store.chekak.balik.ui.navigation.LocalNavigator
import store.chekak.balik.ui.strings

/**
 * Правила категоризации: по условию и типу совпадения операция попадает в категорию.
 */
@Composable
fun RulesScreen(vm: SettingsViewModel) {
    val s = strings
    val navigator = LocalNavigator.current
    val rules by vm.rules.collectAsState()
    val categories by vm.categories.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { SettingsTopBar(title = s.categoryRules, onBack = { navigator.pop() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = s.addRule)
            }
        },
    ) { padding ->
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(title = s.rulesEmpty)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        categoryName = categoryNameOf(categories, rule.categoryId),
                        onDelete = { vm.deleteRule(rule) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        RuleDialog(
            categories = categories,
            onDismiss = { showAddDialog = false },
            onSave = { pattern, matchType, categoryId, setInternalTransfer ->
                val priority = (rules.maxOfOrNull { it.priority } ?: 0) + 10
                vm.upsertRule(
                    CategoryRuleEntity(
                        pattern = pattern,
                        matchType = matchType,
                        categoryId = categoryId,
                        setInternalTransfer = setInternalTransfer,
                        priority = priority,
                    ),
                )
                showAddDialog = false
            },
        )
    }
}

/** Экран правил со своим ViewModel. */
@Composable
fun RulesScreen() {
    RulesScreen(rememberSettingsViewModel())
}

/** Карточка одного правила. */
@Composable
private fun RuleCard(
    rule: CategoryRuleEntity,
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
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = rule.pattern,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${matchTypeLabel(rule.matchType)} → $categoryName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rule.setInternalTransfer) {
                    Text(
                        text = s.internalTransferBadge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
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

/** Диалог создания правила. */
@Composable
private fun RuleDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (pattern: String, matchType: RuleMatchType, categoryId: Long, setInternalTransfer: Boolean) -> Unit,
) {
    val s = strings
    var pattern by remember { mutableStateOf("") }
    var matchType by remember { mutableStateOf(RuleMatchType.CONTAINS) }
    var categoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: 0L) }
    var setInternalTransfer by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val selectedCategory = categories.firstOrNull { it.id == categoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = s.addRule) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text(text = s.rulePattern) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(text = s.ruleType, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = matchType == RuleMatchType.MCC,
                        onClick = { matchType = RuleMatchType.MCC },
                        label = { Text(text = s.ruleTypeMcc) },
                    )
                    FilterChip(
                        selected = matchType == RuleMatchType.CONTAINS,
                        onClick = { matchType = RuleMatchType.CONTAINS },
                        label = { Text(text = s.ruleTypeContains) },
                    )
                    FilterChip(
                        selected = matchType == RuleMatchType.REGEX,
                        onClick = { matchType = RuleMatchType.REGEX },
                        label = { Text(text = s.ruleTypeRegex) },
                    )
                }

                Text(text = s.ruleCategory, style = MaterialTheme.typography.labelLarge)
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = setInternalTransfer,
                        onCheckedChange = { setInternalTransfer = it },
                    )
                    Text(text = s.internalTransferBadge)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(pattern.trim(), matchType, categoryId, setInternalTransfer)
                },
                enabled = pattern.isNotBlank() && categoryId != 0L,
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

/** Название категории по id; если категории нет — показываем «Все». */
@Composable
private fun categoryNameOf(categories: List<CategoryEntity>, categoryId: Long): String =
    categories.firstOrNull { it.id == categoryId }?.name ?: strings.all

/** Подпись типа совпадения. */
@Composable
private fun matchTypeLabel(type: RuleMatchType): String {
    val s = strings
    return when (type) {
        RuleMatchType.MCC -> s.ruleTypeMcc
        RuleMatchType.CONTAINS -> s.ruleTypeContains
        RuleMatchType.REGEX -> s.ruleTypeRegex
    }
}
