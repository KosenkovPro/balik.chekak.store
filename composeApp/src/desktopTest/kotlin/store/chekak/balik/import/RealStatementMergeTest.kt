package store.chekak.balik.import

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import store.chekak.balik.data.db.AppDatabase
import store.chekak.balik.data.db.AppDatabaseConstructor
import store.chekak.balik.data.repository.ImportRepository
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.parser.ParserRegistry

/**
 * Проверка на настоящих выписках: повторный импорт того же файла не должен
 * ни добавлять, ни обновлять ничего — только распознать дубли.
 *
 * Файлы лежат в `.secret/` и в репозиторий не попадают, поэтому тест молча
 * пропускается, если их нет (например, на CI).
 */
class RealStatementMergeTest {

    private lateinit var database: AppDatabase
    private lateinit var ledgerRepository: LedgerRepository
    private lateinit var importRepository: ImportRepository
    private var databaseFile: File? = null

    @BeforeTest
    fun setUp() {
        val file = File.createTempFile("chekak-real-test", ".db")
        file.delete()
        databaseFile = file
        database = Room.databaseBuilder<AppDatabase>(
            name = file.absolutePath,
            factory = { AppDatabaseConstructor.initialize() },
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*store.chekak.balik.data.db.ALL_MIGRATIONS)
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

    @Test
    fun raiffeisenCsvIsFullyMergedOnRepeatedImport() = runTest {
        val file = realFile("Выписка по счету_17_11_2026.csv") ?: return@runTest
        val bytes = file.readBytes()

        val first = importRepository.analyze(bytes, file.name)
        assertTrue(first.totalRows > 0, "Выписка должна разбираться")
        assertEquals(first.totalRows, first.newCount, "Первый импорт: всё новое")
        assertEquals(0, first.duplicateCount)
        importRepository.commit(first)

        val stored = ledgerRepository.getTransactions().size
        assertEquals(first.newCount, stored)

        // Повторный импорт того же файла — ноль новых и ноль обновлений.
        val second = importRepository.analyze(bytes, file.name)
        assertEquals(0, second.newCount, "Повторный импорт не должен добавлять операции")
        assertEquals(0, second.updatedCount, "Повторный импорт не должен обновлять операции")
        assertEquals(first.totalRows, second.duplicateCount, "Все строки должны узнаться как дубли")
        importRepository.commit(second)

        assertEquals(stored, ledgerRepository.getTransactions().size, "Лишних операций не появилось")

        // Номер документа из выписки сохранён — по нему выписки и склеиваются.
        val withDocument = ledgerRepository.getTransactions().count { !it.documentId.isNullOrBlank() }
        assertEquals(stored, withDocument, "У каждой операции должен сохраниться номер документа")
    }

    @Test
    fun tbankPdfIsFullyMergedOnRepeatedImport() = runTest {
        val file = realFile("Справка о движении средств.pdf") ?: return@runTest
        val bytes = file.readBytes()

        val first = importRepository.analyze(bytes, file.name)
        assertTrue(first.totalRows > 0, "PDF-выписка должна разбираться")
        assertEquals(
            first.totalRows,
            first.newCount,
            "PDF: totalRows=${first.totalRows} rows=${first.rows.size} " +
                "unrecognized=${first.unrecognizedRows} new=${first.newCount} dup=${first.duplicateCount}",
        )
        importRepository.commit(first)

        val stored = ledgerRepository.getTransactions().size
        assertEquals(first.newCount, stored)

        val second = importRepository.analyze(bytes, file.name)
        assertEquals(0, second.newCount, "Повторный импорт не должен добавлять операции")
        assertEquals(0, second.updatedCount, "Повторный импорт не должен обновлять операции")
        importRepository.commit(second)

        assertEquals(stored, ledgerRepository.getTransactions().size, "Лишних операций не появилось")
    }

    /**
     * Сверка разбора с итогами, которые банк печатает в самой справке.
     *
     * Это самая строгая проверка парсера: если хоть одна операция потерялась
     * или посчиталась дважды, суммы разойдутся.
     */
    @Test
    fun tbankPdfSumsMatchTheStatementTotals() = runTest {
        val file = realFile("Справка о движении средств.pdf") ?: return@runTest
        val text = store.chekak.balik.parser.pdf.PdfTextExtractor.extractText(file.readBytes())
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')

        val declaredIncome = totalFromStatement(text, "Пополнения")
        val declaredExpenses = totalFromStatement(text, "Расходы")
        assertNotNull(declaredIncome, "В справке должны быть итоги пополнений")
        assertNotNull(declaredExpenses, "В справке должны быть итоги расходов")

        val statement = ParserRegistry().parse(file.readBytes(), file.name)
        val income = statement.operations.filter { it.amountMinor > 0 }.sumOf { it.amountMinor }
        val expenses = statement.operations.filter { it.amountMinor < 0 }.sumOf { -it.amountMinor }

        assertEquals(declaredIncome, income, "Итог пополнений в справке не совпал с разбором")
        assertEquals(declaredExpenses, expenses, "Итог расходов в справке не совпал с разбором")
    }

    /** Итог из строки вида «Пополнения: 524 291,28 ₽». */
    private fun totalFromStatement(text: String, label: String): Long? {
        val line = text.split('\n').firstOrNull { it.trim().startsWith(label) } ?: return null
        val amount = Regex("""([\d][\d\s]*[.,]\d{2})""").find(line)?.groupValues?.get(1) ?: return null
        return store.chekak.balik.parser.text.AmountParser.parseToMinorUnits(amount)
    }

    /** Ищет реальный файл в папке `.secret`; null — если её нет. */
    private fun realFile(name: String): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, ".secret/$name")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        return null
    }
}
