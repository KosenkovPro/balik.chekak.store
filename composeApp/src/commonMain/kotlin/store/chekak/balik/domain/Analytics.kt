package store.chekak.balik.domain

import store.chekak.balik.core.percentOf
import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.data.db.TransactionEntity

/** Доля одной категории в расходах периода. */
data class CategorySlice(
    val categoryId: Long?,
    val categoryKey: String?,
    val name: String,
    val colorHex: String,
    val iconName: String,
    val amountMinor: Long,
    val share: Float,
)

/** Итоги по одной валюте за период. */
data class CurrencySummary(
    val currency: String,
    val expensesMinor: Long = 0,
    val incomesMinor: Long = 0,
    val byCategory: List<CategorySlice> = emptyList(),
) {
    val netMinor: Long get() = incomesMinor - expensesMinor
}

/** Итоги за период по всем валютам. */
data class PeriodSummary(
    val from: Long,
    val to: Long,
    val currencies: List<CurrencySummary> = emptyList(),
) {
    val totalExpensesMinor: Long get() = currencies.sumOf { it.expensesMinor }
    val totalIncomesMinor: Long get() = currencies.sumOf { it.incomesMinor }
    val isEmpty: Boolean get() = currencies.all { it.expensesMinor == 0L && it.incomesMinor == 0L }
}

/**
 * Агрегация операций за период. Переводы между своими счетами исключены
 * из расходов и доходов — иначе они бы «раздували» обе стороны.
 */
object Analytics {

    fun summarize(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        from: Long,
        to: Long,
    ): PeriodSummary {
        val categoriesById = categories.associateBy { it.id }
        val inPeriod = transactions.filter { it.timestamp in from..to && !it.isInternalTransfer }

        val byCurrency = inPeriod.groupBy { it.currency }
        val summaries = byCurrency.map { (currency, items) ->
            val expenses = items.filter { it.amountMinor < 0 }
            val incomes = items.filter { it.amountMinor > 0 }

            val grouped = expenses
                .groupBy { it.categoryId }
                .map { (categoryId, txns) ->
                    val category = categoriesById[categoryId]
                    val amount = txns.sumOf { -it.amountMinor }
                    CategorySlice(
                        categoryId = categoryId,
                        categoryKey = category?.key,
                        name = category?.name ?: "Без категории",
                        colorHex = category?.colorHex ?: "#9E9E9E",
                        iconName = category?.iconName ?: "Category",
                        amountMinor = amount,
                        share = percentOf(amount, expenses.sumOf { -it.amountMinor }),
                    )
                }
                .sortedByDescending { it.amountMinor }

            CurrencySummary(
                currency = currency,
                expensesMinor = expenses.sumOf { -it.amountMinor },
                incomesMinor = incomes.sumOf { it.amountMinor },
                byCategory = grouped,
            )
        }.sortedByDescending { it.expensesMinor }

        return PeriodSummary(from = from, to = to, currencies = summaries)
    }

    /** Сумма расходов по категории за период (для лимитов). */
    fun spentByCategory(
        transactions: List<TransactionEntity>,
        categoryId: Long,
        from: Long,
        to: Long,
    ): Long = transactions
        .filter { it.categoryId == categoryId && !it.isInternalTransfer && it.amountMinor < 0 }
        .filter { it.timestamp in from..to }
        .sumOf { -it.amountMinor }
}
