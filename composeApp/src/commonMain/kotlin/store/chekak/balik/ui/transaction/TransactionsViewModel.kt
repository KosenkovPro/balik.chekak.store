package store.chekak.balik.ui.transaction

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.data.db.TransactionEntity
import store.chekak.balik.data.repository.LedgerRepository

/** Фильтры ленты операций. */
@Immutable
data class TransactionFilters(
    val query: String = "",
    val categoryId: Long? = null,
    val onlyInternalTransfers: Boolean = false,
)

/** Состояние ленты операций. */
@Immutable
data class TransactionsState(
    val isLoading: Boolean = true,
    val items: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val filters: TransactionFilters = TransactionFilters(),
)

/**
 * Лента операций: поиск по тексту, фильтр по категории и по статусу «перевод себе».
 */
class TransactionsViewModel(
    private val ledgerRepository: LedgerRepository,
    private val scope: CoroutineScope,
) {
    private val filters = MutableStateFlow(TransactionFilters())

    val state: StateFlow<TransactionsState> = combine(
        ledgerRepository.observeTransactions(),
        ledgerRepository.observeCategories(),
        filters,
    ) { transactions, categories, currentFilters ->
        val filtered = transactions.filter { transaction ->
            val matchesQuery = currentFilters.query.isBlank() ||
                transaction.rawDescription.contains(currentFilters.query, ignoreCase = true) ||
                transaction.merchantName.contains(currentFilters.query, ignoreCase = true)
            val matchesCategory = currentFilters.categoryId == null ||
                transaction.categoryId == currentFilters.categoryId
            val matchesInternal = !currentFilters.onlyInternalTransfers || transaction.isInternalTransfer
            matchesQuery && matchesCategory && matchesInternal
        }
        TransactionsState(
            isLoading = false,
            items = filtered,
            categories = categories,
            filters = currentFilters,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), TransactionsState())

    fun setQuery(query: String) {
        filters.value = filters.value.copy(query = query)
    }

    fun setCategory(categoryId: Long?) {
        filters.value = filters.value.copy(categoryId = categoryId)
    }

    fun toggleOnlyInternalTransfers() {
        filters.value = filters.value.copy(
            onlyInternalTransfers = !filters.value.onlyInternalTransfers,
        )
    }

    fun resetFilters() {
        filters.value = TransactionFilters()
    }
}
