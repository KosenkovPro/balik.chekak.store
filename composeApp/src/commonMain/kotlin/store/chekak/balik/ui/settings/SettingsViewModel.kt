package store.chekak.balik.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import store.chekak.balik.data.db.BudgetEntity
import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.data.db.CategoryRuleEntity
import store.chekak.balik.data.db.ImportBatchEntity
import store.chekak.balik.data.db.OwnAccountEntity
import store.chekak.balik.data.repository.AppLanguage
import store.chekak.balik.data.repository.BackupRepository
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.data.repository.SettingsRepository

/**
 * Настройки: язык, мои счета, правила категоризации, категории, лимиты,
 * резервные копии и журнал импортов.
 */
class SettingsViewModel(
    private val ledgerRepository: LedgerRepository,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val scope: CoroutineScope,
) {
    val language: StateFlow<AppLanguage> = settingsRepository.observeLanguage()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), AppLanguage.RUSSIAN)

    val rules: StateFlow<List<CategoryRuleEntity>> = ledgerRepository.observeRules()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = ledgerRepository.observeCategories()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val budgets: StateFlow<List<BudgetEntity>> = ledgerRepository.observeBudgets()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ownAccounts: StateFlow<List<OwnAccountEntity>> = settingsRepository.observeOwnAccounts()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val importBatches: StateFlow<List<ImportBatchEntity>> = ledgerRepository.observeImportBatches()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setLanguage(value: AppLanguage) {
        scope.launch { settingsRepository.setLanguage(value) }
    }

    fun upsertRule(rule: CategoryRuleEntity) {
        scope.launch { ledgerRepository.upsertRule(rule) }
    }

    fun deleteRule(rule: CategoryRuleEntity) {
        scope.launch { ledgerRepository.deleteRule(rule) }
    }

    fun upsertCategory(category: CategoryEntity) {
        scope.launch { ledgerRepository.upsertCategory(category) }
    }

    fun deleteCategory(category: CategoryEntity) {
        scope.launch { ledgerRepository.deleteCategory(category) }
    }

    fun upsertBudget(budget: BudgetEntity) {
        scope.launch { ledgerRepository.upsertBudget(budget) }
    }

    fun deleteBudget(budget: BudgetEntity) {
        scope.launch { ledgerRepository.deleteBudget(budget) }
    }

    fun addOwnAccount(label: String, number: String) {
        scope.launch { settingsRepository.addOwnAccount(label, number) }
    }

    fun removeOwnAccount(account: OwnAccountEntity) {
        scope.launch { settingsRepository.removeOwnAccount(account) }
    }

    /** Откат импорта: удаляет операции батча. */
    fun rollbackBatch(batchId: Long) {
        scope.launch { ledgerRepository.rollbackImportBatch(batchId) }
    }

    suspend fun exportJson(): String = backupRepository.exportJson()

    suspend fun restoreJson(content: String): Int = backupRepository.importJson(content)

    suspend fun exportSqliteBytes(): ByteArray? = backupRepository.exportSqliteBytes()
}
