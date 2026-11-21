@file:OptIn(ExperimentalMaterial3Api::class)

package store.chekak.balik.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import store.chekak.balik.data.repository.AppLanguage
import store.chekak.balik.data.repository.BackupRepository
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.ui.navigation.LocalNavigator
import store.chekak.balik.ui.navigation.Screen
import store.chekak.balik.ui.strings

/** Версия приложения — совпадает с versionName в сборке. */
private const val APP_VERSION = "1.0.0"

/**
 * Создаёт ViewModel настроек: репозитории берём через Koin.
 * koinInject — @Composable, поэтому вызовы вынесены выше remember:
 * внутри remember-лямбды composable-вызовы запрещены.
 */
@Composable
fun rememberSettingsViewModel(): SettingsViewModel {
    val scope = rememberCoroutineScope()
    val ledgerRepository = koinInject<LedgerRepository>()
    val settingsRepository = koinInject<SettingsRepository>()
    val backupRepository = koinInject<BackupRepository>()
    return remember(scope) {
        SettingsViewModel(
            ledgerRepository = ledgerRepository,
            settingsRepository = settingsRepository,
            backupRepository = backupRepository,
            scope = scope,
        )
    }
}

/**
 * Главный экран настроек: язык, переходы к остальным разделам и блок «О приложении».
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val s = strings
    val navigator = LocalNavigator.current
    val language by vm.language.collectAsState()

    Scaffold(
        topBar = { SettingsTopBar(title = s.settingsTitle, onBack = { navigator.pop() }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LanguageCard(
                language = language,
                onSelect = { vm.setLanguage(it) },
            )

            SettingsNavItem(
                title = s.myAccounts,
                icon = Icons.Filled.AccountBalanceWallet,
                onClick = { navigator.push(Screen.OwnAccounts) },
            )
            SettingsNavItem(
                title = s.categoryRules,
                icon = Icons.Filled.Rule,
                onClick = { navigator.push(Screen.Rules) },
            )
            SettingsNavItem(
                title = s.categories,
                icon = Icons.Filled.Category,
                onClick = { navigator.push(Screen.Categories) },
            )
            SettingsNavItem(
                title = s.budgets,
                icon = Icons.Filled.Tune,
                onClick = { navigator.push(Screen.Budgets) },
            )
            SettingsNavItem(
                title = s.deposits,
                icon = Icons.Filled.Savings,
                onClick = { navigator.push(Screen.Deposits) },
            )
            SettingsNavItem(
                title = s.taxSettings,
                icon = Icons.Filled.Percent,
                onClick = { navigator.push(Screen.DepositTax) },
            )
            SettingsNavItem(
                title = s.backup,
                icon = Icons.Filled.Backup,
                onClick = { navigator.push(Screen.Backup) },
            )
            SettingsNavItem(
                title = s.importHistory,
                icon = Icons.Filled.History,
                onClick = { navigator.push(Screen.ImportHistory) },
            )

            AboutCard()
        }
    }
}

/** Экран настроек со своим ViewModel. */
@Composable
fun SettingsScreen() {
    SettingsScreen(rememberSettingsViewModel())
}

/** Карточка выбора языка интерфейса. */
@Composable
private fun LanguageCard(
    language: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    val s = strings
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = s.language, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = language == AppLanguage.RUSSIAN,
                    onClick = { onSelect(AppLanguage.RUSSIAN) },
                    label = { Text(text = s.russian) },
                )
                FilterChip(
                    selected = language == AppLanguage.ENGLISH,
                    onClick = { onSelect(AppLanguage.ENGLISH) },
                    label = { Text(text = s.english) },
                )
            }
        }
    }
}

/** Карточка «О приложении»: приватность и версия. */
@Composable
private fun AboutCard() {
    val s = strings
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = s.about, style = MaterialTheme.typography.titleMedium)
            Text(
                text = s.privacyNote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${s.version}: $APP_VERSION",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Верхняя панель раздела настроек с кнопкой «назад».
 * Общая для всех экранов настроек.
 */
@Composable
internal fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.back,
                )
            }
        },
    )
}

/** Строка настроек, которая открывает другой экран. */
@Composable
internal fun SettingsNavItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
