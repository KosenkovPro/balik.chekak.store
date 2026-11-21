package store.chekak.balik.ui.dashboard

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import store.chekak.balik.core.DateUtils
import store.chekak.balik.data.db.AccountEntity
import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.data.db.TransactionEntity
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.domain.Analytics
import store.chekak.balik.domain.PeriodSummary

/** Период на дашборде. */
enum class DashboardPeriod { CURRENT_MONTH, LAST_MONTH, CUSTOM, ALL_TIME }

/** Баланс счёта для карточек. */
@Immutable
data class AccountBalance(
    val account: AccountEntity,
    val balanceMinor: Long?,
)

/** Состояние дашборда. */
@Immutable
data class DashboardState(
    val isLoading: Boolean = true,
    val period: DashboardPeriod = DashboardPeriod.CURRENT_MONTH,
    val customFrom: Long = 0L,
    val customTo: Long = 0L,
    val accounts: List<AccountEntity> = emptyList(),
    val selectedAccountIds: Set<Long> = emptySet(),
    val summary: PeriodSummary = PeriodSummary(0L, 0L),
    val balances: List<AccountBalance> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
) {
    val periodStart: Long get() = summary.from
    val periodEnd: Long get() = summary.to
}

/**
 * Дашборд: итоги за период, разбивка по категориям, балансы счетов.
 */
class DashboardViewModel(
    private val ledgerRepository: LedgerRepository,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val selectedPeriod = MutableStateFlow(DashboardPeriod.CURRENT_MONTH)
    private val customRange = MutableStateFlow(0L to 0L)
    private val accountFilter = MutableStateFlow<Set<Long>>(emptySet())

    private val accountsFlow = ledgerRepository.observeAccounts()
    private val categoriesFlow = ledgerRepository.observeCategories()

    val state: StateFlow<DashboardState> = combine(
        accountsFlow,
        categoriesFlow,
        ledgerRepository.observeTransactions(),
        combine(selectedPeriod, customRange, accountFilter) { period, range, accounts ->
            Triple(period, range, accounts)
        },
    ) { accounts, categories, transactions, selection ->
        val (period, range, selectedAccounts) = selection
        val now = System.currentTimeMillis()
        val (from, to) = resolveRange(period, range, now)
        val filtered = if (selectedAccounts.isEmpty()) {
            transactions
        } else {
            transactions.filter { it.accountId in selectedAccounts }
        }
        val summary = Analytics.summarize(filtered, categories, from, to)
        val balances = accounts.map { AccountBalance(it, it.balanceMinor) }

        DashboardState(
            isLoading = false,
            period = period,
            customFrom = range.first,
            customTo = range.second,
            accounts = accounts,
            selectedAccountIds = selectedAccounts,
            summary = summary,
            balances = balances,
            categories = categories,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    fun setPeriod(period: DashboardPeriod) {
        selectedPeriod.value = period
    }

    fun setCustomRange(from: Long, to: Long) {
        customRange.value = from to to
        selectedPeriod.value = DashboardPeriod.CUSTOM
    }

    fun toggleAccount(accountId: Long) {
        val current = accountFilter.value
        accountFilter.value = if (accountId in current) current - accountId else current + accountId
    }

    fun resetAccountFilter() {
        accountFilter.value = emptySet()
    }

    private fun resolveRange(
        period: DashboardPeriod,
        range: Pair<Long, Long>,
        now: Long,
    ): Pair<Long, Long> = when (period) {
        DashboardPeriod.CURRENT_MONTH -> DateUtils.startOfMonth(now) to DateUtils.endOfMonth(now)
        DashboardPeriod.LAST_MONTH -> DateUtils.startOfMonth(DateUtils.plusMonths(now, -1)) to
            DateUtils.endOfMonth(DateUtils.plusMonths(now, -1))
        DashboardPeriod.CUSTOM -> range.first to range.second
        DashboardPeriod.ALL_TIME -> 0L to Long.MAX_VALUE
    }
}
