@file:OptIn(ExperimentalMaterial3Api::class)

package store.chekak.balik.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.ui.components.CategoryIcons
import store.chekak.balik.ui.components.EmptyState
import store.chekak.balik.ui.components.parseColor
import store.chekak.balik.ui.navigation.LocalNavigator
import store.chekak.balik.ui.strings

/** Иконки, которые можно выбрать для категории. */
private val CATEGORY_ICON_NAMES = listOf(
    "ShoppingCart", "LocalCafe", "DirectionsBus", "Storefront",
    "Subscriptions", "Home", "MedicalServices", "Movie",
    "SwapHoriz", "AttachMoney", "Payments", "Category",
)

/** Палитра для быстрого выбора цвета категории. */
private val CATEGORY_COLORS = listOf(
    "#FF8A65", "#4CAF50", "#FF7043", "#42A5F5", "#AB47BC", "#26A69A",
    "#8D6E63", "#EC407A", "#5C6BC0", "#78909C", "#66BB6A", "#FFA726",
)

private const val DEFAULT_CATEGORY_COLOR = "#FF8A65"

/**
 * Список категорий: собственные и системные. Системные категории нельзя удалить,
 * но можно переименовать и перекрасить.
 */
@Composable
fun CategoriesScreen(vm: SettingsViewModel) {
    val s = strings
    val navigator = LocalNavigator.current
    val categories by vm.categories.collectAsState()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { SettingsTopBar(title = s.categories, onBack = { navigator.pop() }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editing = null
                    showDialog = true
                },
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = s.add)
            }
        },
    ) { padding ->
        if (categories.isEmpty()) {
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
                items(items = categories, key = { it.id }) { category ->
                    CategoryCard(
                        category = category,
                        onEdit = {
                            editing = category
                            showDialog = true
                        },
                        onDelete = { vm.deleteCategory(category) },
                    )
                }
            }
        }
    }

    if (showDialog) {
        CategoryDialog(
            initial = editing,
            onDismiss = { showDialog = false },
            onSave = { name, nameEn, iconName, colorHex ->
                val existing = editing
                vm.upsertCategory(
                    CategoryEntity(
                        id = existing?.id ?: 0L,
                        key = existing?.key,
                        name = name,
                        nameEn = nameEn,
                        iconName = iconName,
                        colorHex = colorHex,
                        isSystem = existing?.isSystem ?: false,
                        sortOrder = existing?.sortOrder ?: (categories.maxOfOrNull { it.sortOrder } ?: 0) + 10,
                    ),
                )
                showDialog = false
            },
        )
    }
}

/** Экран категорий со своим ViewModel. */
@Composable
fun CategoriesScreen() {
    CategoriesScreen(rememberSettingsViewModel())
}

/** Карточка категории: иконка, названия и кнопки действий. */
@Composable
private fun CategoryCard(
    category: CategoryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = strings
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(parseColor(category.colorHex).copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CategoryIcons.of(category.iconName),
                    contentDescription = null,
                    tint = parseColor(category.colorHex),
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(text = category.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = category.nameEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = s.edit,
                )
            }
            if (!category.isSystem) {
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
}

/** Диалог создания и правки категории. */
@Composable
private fun CategoryDialog(
    initial: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, nameEn: String, iconName: String, colorHex: String) -> Unit,
) {
    val s = strings
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var nameEn by remember { mutableStateOf(initial?.nameEn ?: "") }
    var iconName by remember { mutableStateOf(initial?.iconName ?: CATEGORY_ICON_NAMES.first()) }
    var colorHex by remember { mutableStateOf(initial?.colorHex ?: DEFAULT_CATEGORY_COLOR) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (initial == null) s.add else s.edit) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = "${s.category} (RU)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = nameEn,
                    onValueChange = { nameEn = it },
                    label = { Text(text = "${s.category} (EN)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(parseColor(colorHex), CircleShape),
                    )
                    OutlinedTextField(
                        value = colorHex,
                        onValueChange = { input ->
                            val cleaned = input
                                .removePrefix("#")
                                .uppercase()
                                .filter { it.isDigit() || it in 'A'..'F' }
                                .take(6)
                            colorHex = if (cleaned.isEmpty()) "" else "#$cleaned"
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CATEGORY_COLORS.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(parseColor(hex), CircleShape)
                                .clickable { colorHex = hex },
                        )
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CATEGORY_ICON_NAMES.forEach { name ->
                        val selected = name == iconName
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    CircleShape,
                                )
                                .clickable { iconName = name },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = CategoryIcons.of(name),
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim(),
                        nameEn.trim().ifEmpty { name.trim() },
                        iconName,
                        colorHex.ifEmpty { DEFAULT_CATEGORY_COLOR },
                    )
                },
                enabled = name.isNotBlank(),
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
