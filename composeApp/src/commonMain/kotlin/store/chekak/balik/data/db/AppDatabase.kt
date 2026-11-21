package store.chekak.balik.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        CategoryRuleEntity::class,
        ImportBatchEntity::class,
        ImportRowBackupEntity::class,
        BudgetEntity::class,
        OwnAccountEntity::class,
        AppSettingEntity::class,
        DepositEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun importRowBackupDao(): ImportRowBackupDao
    abstract fun budgetDao(): BudgetDao
    abstract fun ownAccountDao(): OwnAccountDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun depositDao(): DepositDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

class Converters {
    @TypeConverter
    fun ruleMatchTypeToString(value: RuleMatchType): String = value.name

    @TypeConverter
    fun stringToRuleMatchType(value: String): RuleMatchType =
        runCatching { RuleMatchType.valueOf(value) }.getOrDefault(RuleMatchType.CONTAINS)

    @TypeConverter
    fun depositPayoutToString(value: DepositPayout): String = value.name

    @TypeConverter
    fun stringToDepositPayout(value: String): DepositPayout =
        runCatching { DepositPayout.valueOf(value) }.getOrDefault(DepositPayout.AT_END)
}
