package store.chekak.balik

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext
import store.chekak.balik.data.repository.AppLanguage
import store.chekak.balik.data.repository.BackupRepository
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.di.initKoin
import store.chekak.balik.ui.LocalStrings
import store.chekak.balik.ui.Strings
import store.chekak.balik.ui.cash.CashScreen
import store.chekak.balik.ui.compare.OcrComparisonScreen
import store.chekak.balik.ui.dashboard.DashboardScreen
import store.chekak.balik.ui.deposits.DepositTaxSettingsScreen
import store.chekak.balik.ui.deposits.DepositsScreen
import store.chekak.balik.ui.forecast.ForecastScreen
import store.chekak.balik.ui.import.ImportPreviewScreen
import store.chekak.balik.ui.navigation.AppNavigator
import store.chekak.balik.ui.navigation.LocalNavigator
import store.chekak.balik.ui.navigation.Screen
import store.chekak.balik.ui.navigation.SystemBackHandler
import store.chekak.balik.ui.onboarding.OnboardingScreen
import store.chekak.balik.ui.settings.BackupScreen
import store.chekak.balik.ui.settings.BudgetsScreen
import store.chekak.balik.ui.settings.CategoriesScreen
import store.chekak.balik.ui.settings.ImportHistoryScreen
import store.chekak.balik.ui.settings.OwnAccountsScreen
import store.chekak.balik.ui.settings.RulesScreen
import store.chekak.balik.ui.settings.SettingsScreen
import store.chekak.balik.ui.settings.SettingsViewModel
import store.chekak.balik.ui.theme.CheKakTheme
import store.chekak.balik.ui.transaction.ManualTransactionScreen
import store.chekak.balik.ui.transaction.TransactionDetailScreen
import store.chekak.balik.ui.transaction.TransactionsScreen

/**
 * Корневой экран приложения. Здесь же живёт навигация и переключение языка.
 *
 * ВАЖНО для iOS: имя файла задаёт имя сгенерированного Kotlin/Native-фасада
 * top-level функций. Swift вызывает `ComposeApp.MainViewControllerKt.AppView()`,
 * поэтому файл должен называться `MainViewController.kt` (иначе фасад будет `AppKt`).
 */
@Composable
fun AppView() {
    ensureKoinStarted()

    val settingsRepository = koinInject<SettingsRepository>()
    val ledgerRepository = koinInject<LedgerRepository>()
    val language by settingsRepository.observeLanguage().collectAsState(AppLanguage.RUSSIAN)
    val onboardingDone by settingsRepository.observeOnboardingCompleted().collectAsState(false)
    val scope = rememberCoroutineScope()
    val navigator = remember { AppNavigator(Screen.Dashboard) }

    // Первичное наполнение базы: системные категории нужны и категоризации,
    // и аналитике, и лимитам. Экраны рисуются только после его завершения.
    val ready by produceState(initialValue = false) {
        ledgerRepository.ensureSeeded()
        value = true
    }

    CompositionLocalProvider(
        LocalStrings provides Strings(language),
        LocalNavigator provides navigator,
    ) {
        CheKakTheme {
            when {
                !ready -> PreparingScreen()
                !onboardingDone -> OnboardingScreen(
                    onFinish = { scope.launch { settingsRepository.setOnboardingCompleted(true) } },
                )
                else -> AppNavigation()
            }
        }
    }
}

/** Заглушка на время первичного наполнения базы. */
@Composable
private fun PreparingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** Основная навигация с нижней панелью. */
@Composable
private fun AppNavigation() {
    val navigator = LocalNavigator.current
    val stack by navigator.stack.collectAsState()
    val screen = stack.last()
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()

    // Системная кнопка «Назад» возвращает на предыдущий экран приложения.
    // На корневом экране (когда стек состоит из одного экрана) остаётся выход.
    SystemBackHandler(enabled = navigator.canGoBack) {
        navigator.pop()
    }

    val ledgerRepository = koinInject<LedgerRepository>()
    val settingsRepository = koinInject<SettingsRepository>()
    val backupRepository = koinInject<BackupRepository>()
    val settingsViewModel = remember(scope) {
        SettingsViewModel(ledgerRepository, settingsRepository, backupRepository, scope)
    }

    val bottomItems = listOf(
        BottomItem(Screen.Dashboard, s.dashboard, Icons.Filled.Home),
        BottomItem(Screen.Transactions, s.transactions, Icons.Filled.List),
        BottomItem(Screen.Forecast, s.forecast, Icons.Filled.TrendingUp),
        BottomItem(Screen.Cash, s.cash, Icons.Filled.Payments),
        BottomItem(Screen.Settings, s.settings, Icons.Filled.Settings),
    )

    val showBottomBar = bottomItems.any { it.screen == screen }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = screen == item.screen,
                            onClick = {
                                if (screen != item.screen) navigator.resetTo(item.screen)
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            AppScreenContent(screen, settingsViewModel)
        }
    }
}

/** Содержимое текущего экрана. */
@Composable
private fun AppScreenContent(screen: Screen, settingsViewModel: SettingsViewModel) {
    when (screen) {
        is Screen.Dashboard -> DashboardScreen()
        is Screen.Transactions -> TransactionsScreen()
        is Screen.Forecast -> ForecastScreen()
        is Screen.Cash -> CashScreen()
        is Screen.Settings -> SettingsScreen(settingsViewModel)
        is Screen.Onboarding -> OnboardingScreen(onFinish = {})
        is Screen.TransactionDetail -> TransactionDetailScreen(screen.transactionId)
        is Screen.ManualTransaction -> ManualTransactionScreen()
        is Screen.ImportPreview -> ImportPreviewScreen(screen.preview)
        is Screen.OcrComparison -> OcrComparisonScreen(screen.source)
        is Screen.Categories -> CategoriesScreen(settingsViewModel)
        is Screen.Rules -> RulesScreen(settingsViewModel)
        is Screen.Budgets -> BudgetsScreen(settingsViewModel)
        is Screen.Backup -> BackupScreen(settingsViewModel)
        is Screen.ImportHistory -> ImportHistoryScreen(settingsViewModel)
        is Screen.OwnAccounts -> OwnAccountsScreen(settingsViewModel)
        is Screen.Deposits -> DepositsScreen()
        is Screen.DepositTax -> DepositTaxSettingsScreen()
    }
}

private data class BottomItem(
    val screen: Screen,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

/** Koin должен быть запущен до первого обращения к зависимостям. */
fun ensureKoinStarted() {
    if (GlobalContext.getOrNull() == null) {
        initKoin()
    }
}
