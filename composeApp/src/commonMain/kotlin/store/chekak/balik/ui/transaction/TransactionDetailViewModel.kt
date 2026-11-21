package store.chekak.balik.ui.transaction

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.data.db.TransactionEntity
import store.chekak.balik.data.repository.LedgerRepository

/** Состояние экрана операции (просмотр и редактирование). */
@Immutable
data class TransactionDetailState(
    val isLoading: Boolean = true,
    val transaction: TransactionEntity? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val isDeleted: Boolean = false,
)

/**
 * Экран операции: полное редактирование (сумма, дата, описание, категория,
 * флаг перевода себе) и удаление.
 */
class TransactionDetailViewModel(
    private val ledgerRepository: LedgerRepository,
    private val scope: CoroutineScope,
    private val transactionId: Long,
) {
    var state: TransactionDetailState = TransactionDetailState()
        private set

    init {
        scope.launch {
            val transaction = ledgerRepository.getTransaction(transactionId)
            val categories = ledgerRepository.getCategories()
            state = TransactionDetailState(
                isLoading = false,
                transaction = transaction,
                categories = categories,
            )
        }
    }

    fun setAmount(amountMinor: Long) = update { it.copy(amountMinor = amountMinor) }

    fun setTimestamp(timestamp: Long) = update { it.copy(timestamp = timestamp) }

    fun setDescription(description: String) = update { it.copy(rawDescription = description) }

    fun setCategory(categoryId: Long?) = update { it.copy(categoryId = categoryId) }

    fun setInternalTransfer(isInternal: Boolean) = update { it.copy(isInternalTransfer = isInternal) }

    fun save() {
        val transaction = state.transaction ?: return
        scope.launch {
            ledgerRepository.upsertTransaction(transaction)
        }
    }

    fun delete() {
        val transaction = state.transaction ?: return
        scope.launch {
            ledgerRepository.deleteTransaction(transaction)
            state = state.copy(isDeleted = true)
        }
    }

    private fun update(block: (TransactionEntity) -> TransactionEntity) {
        val current = state.transaction ?: return
        state = state.copy(transaction = block(current))
    }
}
