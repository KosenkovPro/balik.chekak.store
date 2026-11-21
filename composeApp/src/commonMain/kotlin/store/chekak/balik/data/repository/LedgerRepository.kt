package store.chekak.balik.data.repository

import store.chekak.balik.data.db.AccountDao
import store.chekak.balik.data.db.AccountEntity
import store.chekak.balik.data.db.BudgetDao
import store.chekak.balik.data.db.BudgetEntity
import store.chekak.balik.data.db.CategoryDao
import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.data.db.CategoryRuleDao
import store.chekak.balik.data.db.CategoryRuleEntity
import store.chekak.balik.data.db.CategorySeeder
import store.chekak.balik.data.db.ImportBatchDao
import store.chekak.balik.data.db.ImportBatchEntity
import store.chekak.balik.data.db.ImportRowBackupDao
import store.chekak.balik.data.db.ImportRowBackupEntity
import store.chekak.balik.data.db.TransactionDao
import store.chekak.balik.data.db.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** Операция вместе с разобранными сущностями — то, что показывает UI. */
data class TransactionItem(
    val transaction: TransactionEntity,
    val category: CategoryEntity?,
    val account: AccountEntity?,
)

/** Основные операции над «ledger»: счета, категории, операции, правила, лимиты. */
class LedgerRepository(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val ruleDao: CategoryRuleDao,
    private val budgetDao: BudgetDao,
    private val importBatchDao: ImportBatchDao,
    private val importRowBackupDao: ImportRowBackupDao,
) {
    /** Засев категорией; экземпляр общий, чтобы блокировка была одна на репозиторий. */
    private val categorySeeder = CategorySeeder(categoryDao)

    fun observeAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll()

    fun observeActiveAccounts(): Flow<List<AccountEntity>> = accountDao.observeActive()

    suspend fun getAccounts(): List<AccountEntity> = accountDao.getAll()

    suspend fun getAccount(id: Long): AccountEntity? = accountDao.getById(id)

    suspend fun upsertAccount(account: AccountEntity): Long =
        if (account.id == 0L) accountDao.insert(account) else {
            accountDao.update(account)
            account.id
        }

    /**
     * Записывает остаток счёта.
     *
     * [updatedAt] — дата, на которую остаток актуален (конец периода выписки).
     * Именно по ней сравниваются выписки: старая выписка не должна откатывать
     * остаток, который пришёл из более свежей.
     */
    suspend fun updateBalance(accountId: Long, balanceMinor: Long?, updatedAt: Long) {
        accountDao.updateBalance(accountId, balanceMinor, updatedAt)
    }

    suspend fun deleteAccount(account: AccountEntity) = accountDao.delete(account)

    /** Возвращает счёт наличных, создавая его при необходимости. */
    suspend fun ensureCashAccount(currency: String = "RUB"): AccountEntity {
        val existing = accountDao.getAll().firstOrNull { it.isCash }
        if (existing != null) return existing
        val id = accountDao.insert(
            AccountEntity(
                bankName = "",
                accountName = "Наличные",
                currency = currency,
                isCash = true,
                lastUpdated = System.currentTimeMillis(),
            ),
        )
        return accountDao.getById(id) ?: AccountEntity(id = id, bankName = "", accountName = "Наличные", isCash = true)
    }

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun getCategories(): List<CategoryEntity> = categoryDao.getAll()

    /**
     * Первичное наполнение базы системными категориями.
     *
     * Без категорий в базе не работает ни категоризация, ни аналитика, ни лимиты.
     * Вызывается при старте приложения; повторные вызовы безопасны.
     */
    suspend fun ensureSeeded() = categorySeeder.ensureSeeded()

    suspend fun getCategory(id: Long): CategoryEntity? = categoryDao.getById(id)

    suspend fun upsertCategory(category: CategoryEntity): Long =
        if (category.id == 0L) categoryDao.insert(category) else {
            categoryDao.update(category)
            category.id
        }

    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    fun observeTransactions(): Flow<List<TransactionEntity>> = transactionDao.observeAll()

    fun observeTransactionsInPeriod(from: Long, to: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeInPeriod(from, to)

    suspend fun getTransactions(): List<TransactionEntity> = transactionDao.getAll()

    /** Операции одного счёта — по ним идёт сопоставление при слиянии выписок. */
    suspend fun getTransactionsByAccount(accountId: Long): List<TransactionEntity> =
        transactionDao.getByAccount(accountId)

    suspend fun getTransactionsInPeriod(from: Long, to: Long): List<TransactionEntity> =
        transactionDao.getInPeriod(from, to)

    /** Операции одного импорта — с ними сверяют результат распознавания. */
    suspend fun getTransactionsByBatch(batchId: Long): List<TransactionEntity> =
        transactionDao.getByBatch(batchId)

    suspend fun getTransaction(id: Long): TransactionEntity? = transactionDao.getById(id)

    suspend fun upsertTransaction(transaction: TransactionEntity): Long =
        if (transaction.id == 0L) {
            val id = transactionDao.insert(transaction)
            if (id == -1L) transactionDao.getAll().first { it.fingerprint == transaction.fingerprint }.id else id
        } else {
            transactionDao.update(transaction)
            transaction.id
        }

    suspend fun deleteTransaction(transaction: TransactionEntity) = transactionDao.delete(transaction)

    /** Массовое сохранение операций (используется при импорте). */
    suspend fun upsertTransactions(transactions: List<TransactionEntity>) {
        if (transactions.isEmpty()) return
        transactionDao.insertAll(transactions)
    }

    /** Обновляет банковские детали операции — текст, продавца, MCC и отпечаток. */
    suspend fun updateTransactionBankDetails(
        id: Long,
        fingerprint: String,
        rawDescription: String,
        merchantName: String,
        mcc: String?,
        documentId: String?,
    ) {
        transactionDao.updateBankDetails(id, fingerprint, rawDescription, merchantName, mcc, documentId)
    }

    fun observeRules(): Flow<List<CategoryRuleEntity>> = ruleDao.observeAll()

    suspend fun getRules(): List<CategoryRuleEntity> = ruleDao.getAll()

    suspend fun upsertRule(rule: CategoryRuleEntity): Long =
        if (rule.id == 0L) ruleDao.insert(rule) else {
            ruleDao.update(rule)
            rule.id
        }

    suspend fun deleteRule(rule: CategoryRuleEntity) = ruleDao.delete(rule)

    fun observeBudgets(): Flow<List<BudgetEntity>> = budgetDao.observeAll()

    suspend fun getBudgets(): List<BudgetEntity> = budgetDao.getAll()

    suspend fun upsertBudget(budget: BudgetEntity): Long =
        if (budget.id == 0L) budgetDao.insert(budget) else {
            budgetDao.update(budget)
            budget.id
        }

    suspend fun deleteBudget(budget: BudgetEntity) = budgetDao.delete(budget)

    fun observeImportBatches(): Flow<List<ImportBatchEntity>> = importBatchDao.observeAll()

    suspend fun getImportBatches(): List<ImportBatchEntity> = importBatchDao.getAll()

    suspend fun insertImportBatch(batch: ImportBatchEntity): Long = importBatchDao.insert(batch)

    /** Поправляет счётчики батча после фактического применения импорта. */
    suspend fun updateImportBatchCounts(
        id: Long,
        newRows: Int,
        updatedRows: Int,
        skippedDuplicates: Int,
    ) {
        importBatchDao.updateCounts(id, newRows, updatedRows, skippedDuplicates)
    }

    suspend fun deleteImportBatch(batch: ImportBatchEntity) = importBatchDao.delete(batch)

    /** Снимки операций, изменённых при импорте. */
    suspend fun insertImportBackups(backups: List<ImportRowBackupEntity>) {
        if (backups.isEmpty()) return
        importRowBackupDao.insertAll(backups)
    }

    suspend fun getImportBackups(batchId: Long): List<ImportRowBackupEntity> =
        importRowBackupDao.getByBatch(batchId)

    /**
     * Откат импорта: удаляет добавленные операции и возвращает обновлённым
     * прежний вид (снимки лежат в [importRowBackupDao]).
     */
    suspend fun rollbackImportBatch(batchId: Long) {
        for (backup in importRowBackupDao.getByBatch(batchId)) {
            transactionDao.restoreFromBackup(
                id = backup.transactionId,
                fingerprint = backup.fingerprint,
                rawDescription = backup.rawDescription,
                merchantName = backup.merchantName,
                mcc = backup.mcc,
                categoryId = backup.categoryId,
                isInternalTransfer = backup.isInternalTransfer,
            )
        }
        importRowBackupDao.deleteByBatch(batchId)
        transactionDao.deleteByBatch(batchId)
    }

    suspend fun countTransactionsInBatch(batchId: Long): Int = transactionDao.countByBatch(batchId)
}
