package store.chekak.balik.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Экраны приложения. */
sealed interface Screen {
    data object Onboarding : Screen
    data object Dashboard : Screen
    data object Transactions : Screen
    data object Forecast : Screen
    data object Cash : Screen
    data object Settings : Screen
    data object Categories : Screen
    data object Rules : Screen
    data object Budgets : Screen
    data object Backup : Screen
    data object ImportHistory : Screen
    data object OwnAccounts : Screen
    data object ManualTransaction : Screen
    data object Deposits : Screen
    data object DepositTax : Screen
    data class TransactionDetail(val transactionId: Long) : Screen
    data class ImportPreview(
        val preview: store.chekak.balik.data.repository.ImportPreview,
    ) : Screen
    data class OcrComparison(
        val source: store.chekak.balik.ui.compare.ComparisonSource,
    ) : Screen
}

/**
 * Простой навигатор со стеком экранов. Собственная реализация вместо внешней
 * библиотеки: экранов немного, а зависимостей меньше.
 */
class AppNavigator(initial: Screen = Screen.Dashboard) {

    private val _stack = MutableStateFlow(listOf(initial))

    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    val current: Screen get() = _stack.value.last()

    val canGoBack: Boolean get() = _stack.value.size > 1

    fun push(screen: Screen) {
        _stack.value = _stack.value + screen
    }

    fun pop(): Boolean {
        if (_stack.value.size <= 1) return false
        _stack.value = _stack.value.dropLast(1)
        return true
    }

    fun replace(screen: Screen) {
        _stack.value = _stack.value.dropLast(1) + screen
    }

    fun resetTo(screen: Screen) {
        _stack.value = listOf(screen)
    }
}

val LocalNavigator = compositionLocalOf<AppNavigator> {
    error("AppNavigator не предоставлен в CompositionLocal")
}

/** Кнопка «назад» с корректным поведением (на корне — выход из приложения). */
@Composable
fun rememberNavigator(initial: Screen = Screen.Dashboard): AppNavigator = remember { AppNavigator(initial) }
