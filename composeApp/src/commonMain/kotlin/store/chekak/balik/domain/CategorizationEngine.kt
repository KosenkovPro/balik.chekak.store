package store.chekak.balik.domain

import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.data.db.CategoryRuleEntity
import store.chekak.balik.data.db.RuleMatchType
import store.chekak.balik.data.db.SeedData
import store.chekak.balik.parser.ParsedOperation

/** Результат категоризации одной операции. */
data class CategorizationResult(
    val categoryId: Long?,
    val isInternalTransfer: Boolean,
    /** Правило, которое сработало (null — сработал встроенный MCC или умолчание). */
    val matchedRuleId: Long? = null,
)

/**
 * Движок умной категоризации: трёхступенчатый пайплайн.
 *
 *  1) Детектор переводов самому себе — если перевод найден, операция всегда попадает
 *     в категорию «Переводы себе» и исключается из расчёта расходов и доходов.
 *  2) Пользовательские правила — по убыванию приоритета, типы MCC / CONTAINS / REGEX.
 *  3) Встроенный справочник MCC — если правил нет, категория берётся из MCC-кода.
 *     Если и MCC не дал ничего: доходы → «Доходы», расходы → «Прочее».
 */
class CategorizationEngine(
    private val rules: List<CategoryRuleEntity>,
    private val categories: List<CategoryEntity>,
    private val internalTransferDetector: InternalTransferDetector = InternalTransferDetector(),
) {
    private val categoriesById: Map<Long, CategoryEntity> = categories.associateBy { it.id }
    private val categoryIdBySystemKey: Map<String, Long> = categories
        .mapNotNull { category -> category.key?.let { key -> key to category.id } }
        .toMap()

    fun categorize(operation: ParsedOperation): CategorizationResult {
        if (internalTransferDetector.detect(operation.rawDescription, operation.amountMinor)) {
            return CategorizationResult(
                categoryId = systemCategoryId(SeedData.CategoryKeys.INTERNAL),
                isInternalTransfer = true,
            )
        }

        for (rule in rules.sortedByDescending { it.priority }) {
            if (matches(rule, operation)) {
                return CategorizationResult(
                    categoryId = rule.categoryId,
                    isInternalTransfer = rule.setInternalTransfer,
                    matchedRuleId = rule.id,
                )
            }
        }

        val mccCategory = operation.mcc?.let { SeedData.mccRules[it] }
            ?.let { systemCategoryId(it) }
        if (mccCategory != null) {
            return CategorizationResult(categoryId = mccCategory, isInternalTransfer = false)
        }

        val fallback = if (operation.amountMinor > 0) {
            systemCategoryId(SeedData.CategoryKeys.INCOME)
        } else {
            systemCategoryId(SeedData.CategoryKeys.OTHER)
        }
        return CategorizationResult(categoryId = fallback, isInternalTransfer = false)
    }

    private fun matches(rule: CategoryRuleEntity, operation: ParsedOperation): Boolean = when (rule.matchType) {
        RuleMatchType.MCC -> operation.mcc != null && operation.mcc.equals(rule.pattern.trim(), ignoreCase = true)
        RuleMatchType.CONTAINS -> operation.rawDescription.contains(rule.pattern.trim(), ignoreCase = true)
        RuleMatchType.REGEX -> runCatching {
            Regex(rule.pattern).containsMatchIn(operation.rawDescription)
        }.getOrDefault(false)
    }

    /** Ищет системную категорию по стабильному ключу (SeedData.CategoryKeys). */
    private fun systemCategoryId(key: String): Long? = categoryIdBySystemKey[key]

    /** Категория по идентификатору (для UI). */
    fun categoryById(id: Long?): CategoryEntity? = id?.let { categoriesById[it] }
}
