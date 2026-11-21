package store.chekak.balik.import

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import store.chekak.balik.data.db.ALL_MIGRATIONS
import store.chekak.balik.data.db.AppDatabase
import store.chekak.balik.data.db.AppDatabaseConstructor
import store.chekak.balik.data.repository.ImportRepository
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.parser.ParserRegistry
import store.chekak.balik.ui.import.ImportUiState
import store.chekak.balik.ui.import.ImportViewModel

/**
 * Проверки сценария импорта как целого: разбор файла не должен менять базу,
 * а состояние импорта должно быть наблюдаемым — иначе экран предпросмотра
 * просто не открывается (пользователь выбирает файл, и ничего не происходит).
 */
class ImportFlowTest {

    private lateinit var database: AppDatabase
    private lateinit var ledgerRepository: LedgerRepository
    private lateinit var importRepository: ImportRepository
    private var databaseFile: File? = null

    @BeforeTest
    fun setUp() {
        val file = File.createTempFile("chekak-import-flow-test", ".db")
        file.delete()
        databaseFile = file
        database = Room.databaseBuilder<AppDatabase>(
            name = file.absolutePath,
            factory = { AppDatabaseConstructor.initialize() },
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

        ledgerRepository = LedgerRepository(
            accountDao = database.accountDao(),
            categoryDao = database.categoryDao(),
            transactionDao = database.transactionDao(),
            ruleDao = database.categoryRuleDao(),
            budgetDao = database.budgetDao(),
            importBatchDao = database.importBatchDao(),
            importRowBackupDao = database.importRowBackupDao(),
        )
        importRepository = ImportRepository(
            ledgerRepository = ledgerRepository,
            settingsRepository = SettingsRepository(database.appSettingDao(), database.ownAccountDao()),
            parserRegistry = ParserRegistry(),
        )
    }

    @AfterTest
    fun tearDown() {
        database.close()
        databaseFile?.delete()
    }

    // --- Разбор файла ничего не меняет в базе ---

    @Test
    fun analyzeDoesNotTouchTheDatabase() = runTest {
        val csv = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915"),
        )

        val preview = importRepository.analyze(csv, "выписка.csv")

        assertEquals(1, preview.newCount, "Предпросмотр должен видеть новую операцию")
        assertTrue(ledgerRepository.getAccounts().isEmpty(), "Разбор не должен создавать счёт")
        assertTrue(ledgerRepository.getTransactions().isEmpty(), "Разбор не должен сохранять операции")
        assertTrue(ledgerRepository.getImportBatches().isEmpty(), "Разбор не должен создавать батч импорта")
    }

    @Test
    fun commitCreatesTheAccountAndTheTransactions() = runTest {
        val csv = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915", balance = "5 000,00"),
        )

        val preview = importRepository.analyze(csv, "выписка.csv")
        assertTrue(ledgerRepository.getAccounts().isEmpty(), "До подтверждения счёта ещё нет")

        importRepository.commit(preview)

        val account = ledgerRepository.getAccounts().single()
        assertEquals("8872", account.cardLast4)
        assertEquals(500_000L, account.balanceMinor, "Остаток берётся из выписки")
        assertEquals(1, ledgerRepository.getTransactions().size)
    }

    @Test
    fun cancelledPreviewLeavesNoAccountBehind() = runTest {
        val csv = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915"),
        )

        // Пользователь посмотрел предпросмотр и вышел, не подтверждая импорт.
        importRepository.analyze(csv, "выписка.csv")

        assertTrue(
            ledgerRepository.getAccounts().isEmpty(),
            "После отмены предпросмотра в базе не должно остаться пустого счёта",
        )
    }

    // --- Состояние импорта наблюдаемо ---

    @Test
    fun importStateIsObservableByCompose() = runTest {
        val vm = ImportViewModel(importRepository, this)

        val seen = mutableListOf<ImportUiState>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            snapshotFlow { vm.state.value }.collect { seen += it }
        }

        vm.state.value = ImportUiState.Analyzing
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertTrue(
            seen.contains(ImportUiState.Analyzing),
            "Смена состояния импорта должна быть видна Compose, иначе предпросмотр не откроется",
        )
        collector.cancel()
    }

    @Test
    fun analyzeEndsWithAnObservablePreview() = runTest {
        val vm = ImportViewModel(importRepository, this)

        val seen = mutableListOf<ImportUiState>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            snapshotFlow { vm.state.value }.collect { seen += it }
        }

        val csv = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915"),
        )
        vm.analyze(csv, "выписка.csv")

        withTimeout(30_000) {
            while (vm.state.value !is ImportUiState.Preview) {
                Snapshot.sendApplyNotifications()
                delay(10)
            }
        }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertTrue(
            seen.any { it is ImportUiState.Preview },
            "Готовый предпросмотр должен прийти в состояние, наблюдаемое Compose",
        )
        collector.cancel()
    }
}

/** Строка выписки Райффайзена в формате CSV с разделителем `;`. */
private fun row(
    date: String,
    documentId: String,
    expense: String = "",
    income: String = "",
    description: String,
    card: String = "**8872",
    balance: String = "",
): String {
    val cells = listOf(
        date,
        date,
        documentId,
        income,
        expense,
        "RUB",
        description,
        card,
        balance,
    )
    return cells.joinToString(";") { "\"$it\"" }
}

/** Готовая выписка: шапка как у Райффайзена плюс колонка остатка. */
private fun statement(vararg rows: String): ByteArray {
    val header = "Дата операции;Выполнено банком;Номер документа;Поступления;Расходы;Валюта;" +
        "Детали операции (назначение платежа);Номер карты;Остаток"
    return (listOf(header) + rows).joinToString("\r\n").toByteArray(Charsets.UTF_8)
}
