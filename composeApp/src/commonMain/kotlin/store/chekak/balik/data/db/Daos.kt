package store.chekak.balik.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY isCash ASC, bankName COLLATE NOCASE ASC, accountName ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY isCash ASC, bankName COLLATE NOCASE ASC, accountName ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY isCash ASC, bankName COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("UPDATE accounts SET balanceMinor = :balance, lastUpdated = :updatedAt WHERE id = :id")
    suspend fun updateBalance(id: Long, balance: Long?, updatedAt: Long)

    @Query("UPDATE accounts SET accountName = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun observeInPeriod(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    suspend fun getInPeriod(from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
    suspend fun getByAccount(accountId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT fingerprint FROM transactions")
    suspend fun getFingerprints(): List<String>

    @Query("SELECT COUNT(*) FROM transactions WHERE fingerprint = :fingerprint")
    suspend fun countByFingerprint(fingerprint: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE importBatchId = :batchId")
    suspend fun countByBatch(batchId: Long): Int

    /** Операции, загруженные конкретным импортом, — с ними сверяют распознавание. */
    @Query("SELECT * FROM transactions WHERE importBatchId = :batchId ORDER BY timestamp DESC")
    suspend fun getByBatch(batchId: Long): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE importBatchId = :batchId")
    suspend fun deleteByBatch(batchId: Long)

    /** Обновляет банковские детали операции (текст, продавец, MCC, отпечаток). */
    @Query(
        """
        UPDATE transactions
        SET fingerprint = :fingerprint, rawDescription = :rawDescription,
            merchantName = :merchantName, mcc = :mcc, documentId = :documentId
        WHERE id = :id
        """,
    )
    suspend fun updateBankDetails(
        id: Long,
        fingerprint: String,
        rawDescription: String,
        merchantName: String,
        mcc: String?,
        documentId: String?,
    )

    /** Возвращает операцию к состоянию до импорта (откат). */
    @Query(
        """
        UPDATE transactions
        SET fingerprint = :fingerprint, rawDescription = :rawDescription,
            merchantName = :merchantName, mcc = :mcc, categoryId = :categoryId,
            isInternalTransfer = :isInternalTransfer
        WHERE id = :id
        """,
    )
    suspend fun restoreFromBackup(
        id: Long,
        fingerprint: String,
        rawDescription: String,
        merchantName: String,
        mcc: String?,
        categoryId: Long?,
        isInternalTransfer: Boolean,
    )

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>
}

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules ORDER BY priority DESC, id ASC")
    fun observeAll(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules ORDER BY priority DESC, id ASC")
    suspend fun getAll(): List<CategoryRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: CategoryRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<CategoryRuleEntity>)

    @Update
    suspend fun update(rule: CategoryRuleEntity)

    @Delete
    suspend fun delete(rule: CategoryRuleEntity)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ImportBatchDao {
    @Query("SELECT * FROM import_batches ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batches ORDER BY importedAt DESC")
    suspend fun getAll(): List<ImportBatchEntity>

    @Query("SELECT * FROM import_batches WHERE id = :id")
    suspend fun getById(id: Long): ImportBatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(batch: ImportBatchEntity): Long

    /** Поправляет счётчики батча после фактического применения импорта. */
    @Query(
        """
        UPDATE import_batches
        SET newRows = :newRows, updatedRows = :updatedRows, skippedDuplicates = :skippedDuplicates
        WHERE id = :id
        """,
    )
    suspend fun updateCounts(id: Long, newRows: Int, updatedRows: Int, skippedDuplicates: Int)

    @Delete
    suspend fun delete(batch: ImportBatchEntity)
}

/** Снимки операций, изменённых при импорте — нужны для отката. */
@Dao
interface ImportRowBackupDao {
    @Query("SELECT * FROM import_row_backups WHERE batchId = :batchId")
    suspend fun getByBatch(batchId: Long): List<ImportRowBackupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(backups: List<ImportRowBackupEntity>)

    @Query("DELETE FROM import_row_backups WHERE batchId = :batchId")
    suspend fun deleteByBatch(batchId: Long)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY limitMinor DESC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY limitMinor DESC")
    suspend fun getAll(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId LIMIT 1")
    suspend fun getByCategory(categoryId: Long): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)
}

@Dao
interface OwnAccountDao {
    @Query("SELECT * FROM own_accounts WHERE isArchived = 0 ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<OwnAccountEntity>>

    @Query("SELECT * FROM own_accounts WHERE isArchived = 0")
    suspend fun getAll(): List<OwnAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: OwnAccountEntity): Long

    @Delete
    suspend fun delete(account: OwnAccountEntity)
}

@Dao
interface DepositDao {
    @Query("SELECT * FROM deposits ORDER BY endDate ASC")
    fun observeAll(): Flow<List<DepositEntity>>

    @Query("SELECT * FROM deposits ORDER BY endDate ASC")
    suspend fun getAll(): List<DepositEntity>

    @Query("SELECT * FROM deposits WHERE id = :id")
    suspend fun getById(id: Long): DepositEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deposit: DepositEntity): Long

    @Update
    suspend fun update(deposit: DepositEntity)

    @Delete
    suspend fun delete(deposit: DepositEntity)
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<AppSettingEntity>>

    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: AppSettingEntity)

    @Query("DELETE FROM app_settings WHERE key = :key")
    suspend fun remove(key: String)
}
