package store.chekak.balik.domain

import store.chekak.balik.core.percentOf
import store.chekak.balik.data.db.BudgetEntity
import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.data.db.TransactionEntity

/** Состояние лимита по категории. */
data class BudgetStatus(
    val categoryId: Long,
    val categoryName: String,
    val categoryKey: String?,
    val colorHex: String,
    val iconName: String,
    val limitMinor: Long,
    val spentMinor: Long,
    val remainingMinor: Long,
    val currency: String,
) {
    val progress: Float get() = percentOf(spentMinor, limitMinor)
    val isExceeded: Boolean get() = spentMinor > limitMinor
    val isAlmostExceeded: Boolean get() = !isExceeded && progress >= 0.8f
}

/** Оценка лимитов за период. */
object BudgetEvaluator {

    fun evaluate(
        budgets: List<BudgetEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        from: Long,
        to: Long,
    ): List<BudgetStatus> {
        val categoriesById = categories.associateBy { it.id }
        return budgets.mapNotNull { budget ->
            val category = categoriesById[budget.categoryId] ?: return@mapNotNull null
            val spent = Analytics.spentByCategory(transactions, budget.categoryId, from, to)
            BudgetStatus(
                categoryId = budget.categoryId,
                categoryName = category.name,
                categoryKey = category.key,
                colorHex = category.colorHex,
                iconName = category.iconName,
                limitMinor = budget.limitMinor,
                spentMinor = spent,
                remainingMinor = budget.limitMinor - spent,
                currency = budget.currency,
            )
        }.sortedByDescending { it.progress }
    }
}
