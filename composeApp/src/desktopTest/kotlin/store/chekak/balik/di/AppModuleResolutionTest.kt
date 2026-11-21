package store.chekak.balik.di

import org.koin.dsl.koinApplication
import store.chekak.balik.data.db.AccountDao
import store.chekak.balik.data.db.AppDatabase
import store.chekak.balik.data.db.AppSettingDao
import store.chekak.balik.data.db.BudgetDao
import store.chekak.balik.data.db.CategoryDao
import store.chekak.balik.data.db.CategoryRuleDao
import store.chekak.balik.data.db.ImportBatchDao
import store.chekak.balik.data.db.ImportRowBackupDao
import store.chekak.balik.data.db.OwnAccountDao
import store.chekak.balik.data.db.TransactionDao
import store.chekak.balik.data.repository.BackupRepository
import store.chekak.balik.data.repository.ImportRepository
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.domain.ForecastCalculator
import store.chekak.balik.parser.ParserRegistry
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Проверка графа зависимостей: каждая запись модуля должна разрешаться.
 *
 * Без такого теста забытое определение в Koin выдаёт ошибку только на устройстве:
 * приложение падает на старте с `NoDefinitionFoundException`, и в тестах,
 * которые собирают репозитории вручную, это не видно.
 */
class AppModuleResolutionTest {

    @Test
    fun everyDefinitionResolves() {
        val koin = koinApplication { modules(appModule) }.koin

        assertNotNull(koin.get<AppDatabase>())
        assertNotNull(koin.get<AccountDao>())
        assertNotNull(koin.get<CategoryDao>())
        assertNotNull(koin.get<TransactionDao>())
        assertNotNull(koin.get<CategoryRuleDao>())
        assertNotNull(koin.get<ImportBatchDao>())
        assertNotNull(koin.get<ImportRowBackupDao>())
        assertNotNull(koin.get<BudgetDao>())
        assertNotNull(koin.get<OwnAccountDao>())
        assertNotNull(koin.get<AppSettingDao>())
        assertNotNull(koin.get<LedgerRepository>())
        assertNotNull(koin.get<SettingsRepository>())
        assertNotNull(koin.get<ImportRepository>())
        assertNotNull(koin.get<BackupRepository>())
        assertNotNull(koin.get<ParserRegistry>())
        assertNotNull(koin.get<ForecastCalculator>())
    }
}
