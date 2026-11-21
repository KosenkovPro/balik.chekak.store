package store.chekak.balik.ui.cash

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import store.chekak.balik.data.db.AccountEntity
import store.chekak.balik.data.db.TransactionEntity
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.domain.Fingerprint

/** Состояние экрана наличных. */
@Immutable
data class CashState(
    val isLoading: Boolean = true,
    val account: AccountEntity? = null,
    val balanceMinor: Long = 0L,
    val transactions: List<TransactionEntity> = emptyList(),
    val currency: String = "RUB",
)

/**
 * Ведение наличных отдельным «счётом»: пополнение, снятие и список операций.
 */
class CashViewModel(
    private val ledgerRepository: LedgerRepository,
    private val scope: CoroutineScope,
) {
    private val refreshTrigger = MutableStateFlow(0)

    val state: StateFlow<CashState> = combine(
        ledgerRepository.observeAccounts(),
        ledgerRepository.observeTransactions(),
        refreshTrigger,
    ) { accounts, transactions, _ ->
        val cash = accounts.firstOrNull { it.isCash }
        val cashTransactions = cash?.let { account ->
            transactions.filter { it.accountId == account.id }
        } ?: emptyList()

        CashState(
            isLoading = false,
            account = cash,
            balanceMinor = cashTransactions.sumOf { it.amountMinor },
            transactions = cashTransactions.sortedByDescending { it.timestamp },
            currency = cash?.currency ?: "RUB",
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), CashState())

    init {
        scope.launch {
            val cash = ledgerRepository.ensureCashAccount()
            // Остаток наличных меняется вручную, поэтому «дата актуальности» — текущая.
            ledgerRepository.updateBalance(cash.id, cash.balanceMinor, System.currentTimeMillis())
        }
    }

    /** Пополнение наличных (положительная сумма). */
    fun topUp(amountMinor: Long) = changeBalance(amountMinor, "Пополнение наличных", "Cash top-up")

    /** Снятие наличных (отрицательная сумма). */
    fun withdraw(amountMinor: Long) = changeBalance(-amountMinor, "Снятие наличных", "Cash withdrawal")

    private fun changeBalance(amountMinor: Long, ruTitle: String, enTitle: String) {
        if (amountMinor == 0L) return
        scope.launch {
            val cash = ledgerRepository.ensureCashAccount()
            val timestamp = System.currentTimeMillis()
            val title = if (cash.accountName == ruTitle) enTitle else ruTitle
            val transaction = TransactionEntity(
                fingerprint = Fingerprint.of("cash", timestamp, amountMinor, cash.currency, title),
                accountId = cash.id,
                timestamp = timestamp,
                amountMinor = amountMinor,
                currency = cash.currency,
                rawDescription = title,
                merchantName = title,
                isManual = true,
                createdAt = timestamp,
            )
            ledgerRepository.upsertTransaction(transaction)
            refreshTrigger.value += 1
        }
    }
}
