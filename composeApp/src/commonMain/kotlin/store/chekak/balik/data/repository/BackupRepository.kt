package store.chekak.balik.data.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import store.chekak.balik.data.db.AccountEntity
import store.chekak.balik.data.db.AppSettingEntity
import store.chekak.balik.data.db.BudgetEntity
import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.data.db.CategoryRuleEntity
import store.chekak.balik.data.db.DatabaseFile
import store.chekak.balik.data.db.OwnAccountEntity
import store.chekak.balik.data.db.RuleMatchType
import store.chekak.balik.data.db.TransactionEntity

/** DTO для JSON-бэкапа. */
@Serializable
data class BackupDto(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val accounts: List<AccountDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val transactions: List<TransactionDto> = emptyList(),
    val rules: List<RuleDto> = emptyList(),
    val budgets: List<BudgetDto> = emptyList(),
    val ownAccounts: List<OwnAccountDto> = emptyList(),
    val settings: List<SettingDto> = emptyList(),
)

@Serializable
data class AccountDto(
    val id: Long = 0L,
    val bankName: String = "",
    val accountName: String = "",
    val currency: String = "RUB",
    val cardLast4: String? = null,
    val balanceMinor: Long? = null,
    val isCash: Boolean = false,
    val isArchived: Boolean = false,
    val lastUpdated: Long = 0L,
)

@Serializable
data class CategoryDto(
    val id: Long = 0L,
    val key: String? = null,
    val name: String = "",
    val nameEn: String = "",
    val iconName: String = "category",
    val colorHex: String = "#FF8A65",
    val isSystem: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
data class TransactionDto(
    val id: Long = 0L,
    val fingerprint: String = "",
    val accountId: Long = 0L,
    val timestamp: Long = 0L,
    val amountMinor: Long = 0L,
    val currency: String = "RUB",
    val rawDescription: String = "",
    val merchantName: String = "",
    val mcc: String? = null,
    val categoryId: Long? = null,
    val isInternalTransfer: Boolean = false,
    val isManual: Boolean = false,
    val importBatchId: Long? = null,
    val note: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class RuleDto(
    val id: Long = 0L,
    val pattern: String = "",
    val matchType: String = "CONTAINS",
    val categoryId: Long = 0L,
    val setInternalTransfer: Boolean = false,
    val priority: Int = 0,
)

@Serializable
data class BudgetDto(
    val id: Long = 0L,
    val categoryId: Long = 0L,
    val limitMinor: Long = 0L,
    val currency: String = "RUB",
    val period: String = "MONTHLY",
    val updatedAt: Long = 0L,
)

@Serializable
data class OwnAccountDto(
    val id: Long = 0L,
    val label: String = "",
    val normalized: String = "",
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
)

@Serializable
data class SettingDto(
    val key: String = "",
    val value: String = "",
)

/**
 * Резервные копии: JSON (читаемый, восстанавливается) и .sqlite (точная копия файла БД).
 */
class BackupRepository(
    private val ledgerRepository: LedgerRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportJson(): String {
        val dto = BackupDto(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            accounts = ledgerRepository.getAccounts().map { it.toDto() },
            categories = ledgerRepository.getCategories().map { it.toDto() },
            transactions = ledgerRepository.getTransactions().map { it.toDto() },
            rules = ledgerRepository.getRules().map { it.toDto() },
            budgets = ledgerRepository.getBudgets().map { it.toDto() },
            ownAccounts = settingsRepository.getOwnAccounts().map { it.toDto() },
            settings = emptyList(),
        )
        return json.encodeToString(BackupDto.serializer(), dto)
    }

    /** Восстанавливает данные из JSON-бэкапа. Возвращает количество операций. */
    suspend fun importJson(content: String): Int {
        val dto = json.decodeFromString(BackupDto.serializer(), content)
        var restored = 0

        dto.categories.forEach { category ->
            ledgerRepository.upsertCategory(
                CategoryEntity(
                    id = category.id,
                    key = category.key,
                    name = category.name,
                    nameEn = category.nameEn,
                    iconName = category.iconName,
                    colorHex = category.colorHex,
                    isSystem = category.isSystem,
                    sortOrder = category.sortOrder,
                ),
            )
        }
        dto.accounts.forEach { account ->
            ledgerRepository.upsertAccount(
                AccountEntity(
                    id = account.id,
                    bankName = account.bankName,
                    accountName = account.accountName,
                    currency = account.currency,
                    cardLast4 = account.cardLast4,
                    balanceMinor = account.balanceMinor,
                    isCash = account.isCash,
                    isArchived = account.isArchived,
                    lastUpdated = account.lastUpdated,
                ),
            )
        }
        dto.rules.forEach { rule ->
            ledgerRepository.upsertRule(
                CategoryRuleEntity(
                    id = rule.id,
                    pattern = rule.pattern,
                    matchType = runCatching { RuleMatchType.valueOf(rule.matchType) }
                        .getOrDefault(RuleMatchType.CONTAINS),
                    categoryId = rule.categoryId,
                    setInternalTransfer = rule.setInternalTransfer,
                    priority = rule.priority,
                ),
            )
        }
        dto.budgets.forEach { budget ->
            ledgerRepository.upsertBudget(
                BudgetEntity(
                    id = budget.id,
                    categoryId = budget.categoryId,
                    limitMinor = budget.limitMinor,
                    currency = budget.currency,
                    period = budget.period,
                    updatedAt = budget.updatedAt,
                ),
            )
        }
        dto.ownAccounts.forEach { own ->
            settingsRepository.addOwnAccount(own.label, own.normalized)
        }
        dto.transactions.forEach { transaction ->
            ledgerRepository.upsertTransaction(
                TransactionEntity(
                    id = transaction.id,
                    fingerprint = transaction.fingerprint,
                    accountId = transaction.accountId,
                    timestamp = transaction.timestamp,
                    amountMinor = transaction.amountMinor,
                    currency = transaction.currency,
                    rawDescription = transaction.rawDescription,
                    merchantName = transaction.merchantName,
                    mcc = transaction.mcc,
                    categoryId = transaction.categoryId,
                    isInternalTransfer = transaction.isInternalTransfer,
                    isManual = transaction.isManual,
                    importBatchId = null,
                    note = transaction.note,
                    createdAt = transaction.createdAt,
                ),
            )
            restored++
        }
        dto.settings.forEach { setting ->
            // Настройки восстанавливаются через репозиторий настроек.
            if (setting.key == store.chekak.balik.data.db.SeedData.SETTING_LANGUAGE) {
                settingsRepository.setLanguage(
                    store.chekak.balik.data.repository.AppLanguage.from(setting.value),
                )
            }
        }
        return restored
    }

    /** Точная копия файла базы. */
    suspend fun exportSqliteBytes(): ByteArray? = DatabaseFile.readBytes()

    private fun AccountEntity.toDto() = AccountDto(
        id = id,
        bankName = bankName,
        accountName = accountName,
        currency = currency,
        cardLast4 = cardLast4,
        balanceMinor = balanceMinor,
        isCash = isCash,
        isArchived = isArchived,
        lastUpdated = lastUpdated,
    )

    private fun CategoryEntity.toDto() = CategoryDto(
        id = id,
        key = key,
        name = name,
        nameEn = nameEn,
        iconName = iconName,
        colorHex = colorHex,
        isSystem = isSystem,
        sortOrder = sortOrder,
    )

    private fun TransactionEntity.toDto() = TransactionDto(
        id = id,
        fingerprint = fingerprint,
        accountId = accountId,
        timestamp = timestamp,
        amountMinor = amountMinor,
        currency = currency,
        rawDescription = rawDescription,
        merchantName = merchantName,
        mcc = mcc,
        categoryId = categoryId,
        isInternalTransfer = isInternalTransfer,
        isManual = isManual,
        importBatchId = importBatchId,
        note = note,
        createdAt = createdAt,
    )

    private fun CategoryRuleEntity.toDto() = RuleDto(
        id = id,
        pattern = pattern,
        matchType = matchType.name,
        categoryId = categoryId,
        setInternalTransfer = setInternalTransfer,
        priority = priority,
    )

    private fun BudgetEntity.toDto() = BudgetDto(
        id = id,
        categoryId = categoryId,
        limitMinor = limitMinor,
        currency = currency,
        period = period,
        updatedAt = updatedAt,
    )

    private fun OwnAccountEntity.toDto() = OwnAccountDto(
        id = id,
        label = label,
        normalized = normalized,
        isArchived = isArchived,
        createdAt = createdAt,
    )
}
