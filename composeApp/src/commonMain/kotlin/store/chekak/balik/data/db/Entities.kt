package store.chekak.balik.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Счёт или карта. Наличные тоже хранятся здесь (isCash = true), чтобы аналитика
 * и графики работали с ними одинаково.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankName: String,
    val accountName: String,
    val currency: String = "RUB",
    /** Последние 4 цифры карты — для понятного отображения. */
    val cardLast4: String? = null,
    /** Остаток из последней выписки (в минорных единицах). */
    val balanceMinor: Long? = null,
    val isCash: Boolean = false,
    val isArchived: Boolean = false,
    /**
     * Дата, на которую актуальны данные по счёту: конец периода последней
     * импортированной выписки. По ней сравниваются выписки — старая не должна
     * откатывать остаток, пришедший из более свежей.
     */
    val lastUpdated: Long = 0L,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Стабильный ключ системной категории (см. SeedData.CategoryKeys). */
    val key: String? = null,
    val name: String,
    val nameEn: String = name,
    val iconName: String = "category",
    val colorHex: String = "#FF8A65",
    val isSystem: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["timestamp"]),
        Index(value = ["importBatchId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["importBatchId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SHA-256 от "$bankId|$date|$amount|$currency|$rawDescription" — защита от дублей. */
    val fingerprint: String,
    val accountId: Long,
    val timestamp: Long,
    /** Минорные единицы: отрицательные — расход, положительные — доход. */
    val amountMinor: Long,
    val currency: String = "RUB",
    val rawDescription: String,
    val merchantName: String = "",
    val mcc: String? = null,
    /**
     * Банковский номер операции («Номер документа» в выписке).
     * Устойчив к правкам текста назначения — по нему операции надёжно склеиваются
     * при импорте пересекающихся выписок.
     */
    val documentId: String? = null,
    val categoryId: Long? = null,
    val isInternalTransfer: Boolean = false,
    /** Операция создана вручную, а не при импорте. */
    val isManual: Boolean = false,
    val importBatchId: Long? = null,
    val note: String? = null,
    val createdAt: Long = 0L,
)

/** Тип сопоставления правила категоризации. */
enum class RuleMatchType { MCC, CONTAINS, REGEX }

@Entity(
    tableName = "category_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val matchType: RuleMatchType = RuleMatchType.CONTAINS,
    val categoryId: Long,
    val setInternalTransfer: Boolean = false,
    val priority: Int = 0,
)

@Entity(tableName = "import_batches")
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val bankName: String,
    val importedAt: Long = 0L,
    val totalRows: Int = 0,
    val newRows: Int = 0,
    /** Сколько уже сохранённых операций обновилось новой выпиской. */
    val updatedRows: Int = 0,
    val skippedDuplicates: Int = 0,
    val accountId: Long? = null,
    val fileType: String = "",
)

/**
 * Снимок операции до обновления при импорте.
 *
 * Нужен, чтобы откат импорта вернул не только удалённые новые операции,
 * но и прежние детали обновлённых.
 */
@Entity(
    tableName = "import_row_backups",
    indices = [Index(value = ["batchId"])],
    foreignKeys = [
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ImportRowBackupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val transactionId: Long,
    val fingerprint: String,
    val rawDescription: String,
    val merchantName: String,
    val mcc: String?,
    val categoryId: Long?,
    val isInternalTransfer: Boolean,
)

/** Месячный лимит по категории. */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val limitMinor: Long,
    val currency: String = "RUB",
    /** MONTHLY — лимит на календарный месяц. */
    val period: String = "MONTHLY",
    val updatedAt: Long = 0L,
)

/**
 * Мои собственные карты и счета. Нужны, чтобы отличать перевод самому себе
 * от перевода другу или на внешний номер телефона.
 */
@Entity(tableName = "own_accounts")
data class OwnAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    /** Только цифры, без пробелов и маски — по ним ищем в описании операции. */
    val normalized: String,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
)

/** Произвольные настройки приложения (язык, онбординг, параметры прогноза). */
@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** Схема начисления процентов по вкладу. */
enum class DepositPayout {
    /** Проценты выплачиваются ежемесячно, тело вклада не растёт. */
    MONTHLY,

    /** Все проценты выплачиваются в конце срока. */
    AT_END,

    /** Проценты ежемесячно капитализируются — тело вклада растёт. */
    CAPITALIZED,
}

/**
 * Банковский вклад (депозит).
 *
 * Считается отдельно от счёта: это актив, который растёт сам. Проценты и налог
 * с них считаются по правилам НК РФ — см. `domain/DepositCalculator`.
 */
@Entity(tableName = "deposits")
data class DepositEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Название вклада — например, «Накопительный». */
    val name: String,
    val bankName: String = "",
    /** Тело вклада в минорных единицах. */
    val amountMinor: Long,
    /** Годовая ставка в процентах: 14.5 — это 14,5% годовых. */
    val annualRatePercent: Double,
    /** Начало срока (epoch millis). */
    val startDate: Long,
    /** Конец срока (epoch millis). */
    val endDate: Long,
    val payout: DepositPayout = DepositPayout.AT_END,
    val currency: String = "RUB",
    val createdAt: Long = 0L,
)
