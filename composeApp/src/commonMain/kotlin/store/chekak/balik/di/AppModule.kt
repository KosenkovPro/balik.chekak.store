package store.chekak.balik.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import store.chekak.balik.data.db.AppDatabase
import store.chekak.balik.data.db.DatabaseProvider
import store.chekak.balik.data.repository.BackupRepository
import store.chekak.balik.data.repository.DepositRepository
import store.chekak.balik.data.repository.ImportRepository
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.domain.DepositCalculator
import store.chekak.balik.domain.ForecastCalculator
import store.chekak.balik.parser.ParserRegistry

/** Модуль зависимостей: база, репозитории и движок прогноза. */
val appModule: Module = module {
    single<AppDatabase> { DatabaseProvider.create() }

    single { get<AppDatabase>().accountDao() }
    single { get<AppDatabase>().categoryDao() }
    single { get<AppDatabase>().transactionDao() }
    single { get<AppDatabase>().categoryRuleDao() }
    single { get<AppDatabase>().importBatchDao() }
    single { get<AppDatabase>().importRowBackupDao() }
    single { get<AppDatabase>().budgetDao() }
    single { get<AppDatabase>().ownAccountDao() }
    single { get<AppDatabase>().appSettingDao() }
    single { get<AppDatabase>().depositDao() }

    single { LedgerRepository(get(), get(), get(), get(), get(), get(), get()) }
    single { SettingsRepository(get(), get()) }
    single { ImportRepository(get(), get(), get()) }
    single { BackupRepository(get(), get()) }
    single { DepositRepository(get()) }
    single { ParserRegistry() }
    single { ForecastCalculator() }
    single { DepositCalculator() }
}

/** Запуск Koin. Вызывается один раз при старте приложения. */
fun initKoin(extraModules: List<Module> = emptyList()): KoinApplication =
    startKoin {
        modules(appModule + extraModules)
    }
