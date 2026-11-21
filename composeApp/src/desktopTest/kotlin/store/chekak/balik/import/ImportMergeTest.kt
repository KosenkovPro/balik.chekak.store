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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import store.chekak.balik.data.db.ALL_MIGRATIONS
import store.chekak.balik.data.db.AppDatabase
import store.chekak.balik.data.db.AppDatabaseConstructor
import store.chekak.balik.data.repository.ImportRepository
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.domain.MergeAction
import store.chekak.balik.parser.ParserRegistry

/**
 * Сквозная проверка слияния выписок на настоящей базе данных:
 * импорт → предпросмотр → фиксация → откат.
 *
 * Формат CSV — как у Райффайзенбанка (разделитель `;`, колонки «Номер документа»,
 * «Детали операции», «Номер карты»).
 */
class ImportMergeTest {

    private lateinit var database: AppDatabase
    private lateinit var ledgerRepository: LedgerRepository
    private lateinit var importRepository: ImportRepository
    private var databaseFile: File? = null

    @BeforeTest
    fun setUp() {
        val file = File.createTempFile("chekak-merge-test", ".db")
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

    // --- Повторный импорт того же файла ---

    @Test
    fun reimportingTheSameFileAddsNothing() = runTest {
        val csv = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915"),
            row("09.11.2026 21:47", "875619", expense = "30 000,00", description = "Перевод на номер 0079169773779. Марина П."),
        )

        val first = importRepository.analyze(csv, "выписка.csv")
        assertEquals(2, first.newCount)
        assertEquals(0, first.duplicateCount)
        assertEquals(0, first.updatedCount)
        importRepository.commit(first)
        assertEquals(2, ledgerRepository.getTransactions().size)

        val second = importRepository.analyze(csv, "выписка.csv")
        assertEquals(0, second.newCount)
        assertEquals(2, second.duplicateCount)
        assertEquals(0, second.updatedCount)

        importRepository.commit(second)
        assertEquals(2, ledgerRepository.getTransactions().size)
    }

    // --- Пересекающиеся выписки за разные периоды ---

    @Test
    fun overlappingStatementsOnlyAddTheNewPart() = runTest {
        val october = statement(
            row("15.10.2026 12:00", "1001", expense = "10 000,00", description = "Оплата в PYATEROCHKA 6915"),
            row("20.10.2026 13:00", "1002", expense = "20 000,00", description = "Оплата в AUCHAN 6915"),
        )
        val november = statement(
            row("20.10.2026 13:00", "1002", expense = "20 000,00", description = "Оплата в AUCHAN 6915"),
            row("03.11.2026 09:00", "1003", expense = "30 000,00", description = "Оплата в AUCHAN 6915"),
        )

        importRepository.commit(importRepository.analyze(october, "октябрь.csv"))
        assertEquals(2, ledgerRepository.getTransactions().size)

        val preview = importRepository.analyze(november, "ноябрь.csv")
        assertEquals(1, preview.newCount)
        assertEquals(1, preview.duplicateCount)
        assertEquals(0, preview.updatedCount)

        importRepository.commit(preview)
        val transactions = ledgerRepository.getTransactions()
        assertEquals(3, transactions.size)
        assertEquals(1, transactions.count { it.documentId == "1003" })
    }

    // --- Банк изменил текст операции ---

    @Test
    fun changedDetailsAreUpdatedWithoutCreatingADuplicate() = runTest {
        val original = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915"),
        )
        val corrected = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN MOSCOW RUS 6915"),
        )

        importRepository.commit(importRepository.analyze(original, "выписка.csv"))
        val before = ledgerRepository.getTransactions().single()

        val preview = importRepository.analyze(corrected, "выписка-2.csv")
        assertEquals(0, preview.newCount)
        assertEquals(1, preview.updatedCount)
        assertEquals(0, preview.duplicateCount)

        importRepository.commit(preview)

        val after = ledgerRepository.getTransactions().single()
        assertEquals(before.id, after.id)
        assertTrue(after.rawDescription.contains("MOSCOW"), after.rawDescription)
        assertEquals("202639", after.documentId)
    }

    @Test
    fun updateKeepsTheCategoryChosenByTheUser() = runTest {
        val csv = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915"),
        )
        importRepository.commit(importRepository.analyze(csv, "выписка.csv"))

        val transaction = ledgerRepository.getTransactions().single()
        val cafe = ledgerRepository.getCategories().first { it.name == "Кафе и рестораны" }
        ledgerRepository.upsertTransaction(transaction.copy(categoryId = cafe.id))

        val corrected = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN MOSCOW RUS 6915"),
        )
        importRepository.commit(importRepository.analyze(corrected, "выписка-2.csv"))

        assertEquals(cafe.id, ledgerRepository.getTransactions().single().categoryId)
    }

    // --- Откат ---

    @Test
    fun rollbackRemovesAddedRows() = runTest {
        val october = statement(
            row("15.10.2026 12:00", "1001", expense = "10 000,00", description = "Оплата в PYATEROCHKA 6915"),
        )
        val november = statement(
            row("15.10.2026 12:00", "1001", expense = "10 000,00", description = "Оплата в PYATEROCHKA 6915"),
            row("03.11.2026 09:00", "1003", expense = "30 000,00", description = "Оплата в AUCHAN 6915"),
        )

        importRepository.commit(importRepository.analyze(october, "октябрь.csv"))
        val batch = importRepository.commit(importRepository.analyze(november, "ноябрь.csv"))
        assertEquals(2, ledgerRepository.getTransactions().size)

        importRepository.rollback(batch.id)
        assertEquals(1, ledgerRepository.getTransactions().size)
    }

    @Test
    fun rollbackRestoresUpdatedRows() = runTest {
        val original = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915"),
        )
        val corrected = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN MOSCOW RUS 6915"),
        )

        importRepository.commit(importRepository.analyze(original, "выписка.csv"))
        val batch = importRepository.commit(importRepository.analyze(corrected, "выписка-2.csv"))
        assertTrue(ledgerRepository.getTransactions().single().rawDescription.contains("MOSCOW"))

        importRepository.rollback(batch.id)

        val restored = ledgerRepository.getTransactions().single()
        assertEquals("Оплата в AUCHAN 6915", restored.rawDescription)
    }

    // --- Счета ---

    @Test
    fun differentCardsGetDifferentAccounts() = runTest {
        val first = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915", card = "**8872"),
        )
        val second = statement(
            row("12.11.2026 15:46", "202640", expense = "370,00", description = "Оплата в AUCHAN 6915", card = "**1234"),
        )

        importRepository.commit(importRepository.analyze(first, "выписка.csv"))
        importRepository.commit(importRepository.analyze(second, "выписка-2.csv"))

        val accounts = ledgerRepository.getAccounts()
        assertEquals(2, accounts.size)
        assertEquals(setOf("8872", "1234"), accounts.mapNotNull { it.cardLast4 }.toSet())
    }

    @Test
    fun sameCardReusesTheSameAccount() = runTest {
        val first = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915", card = "**8872"),
        )
        val second = statement(
            row("12.11.2026 15:46", "202640", expense = "370,00", description = "Оплата в AUCHAN 6915", card = "**8872"),
        )

        importRepository.commit(importRepository.analyze(first, "выписка.csv"))
        importRepository.commit(importRepository.analyze(second, "выписка-2.csv"))

        assertEquals(1, ledgerRepository.getAccounts().size)
        assertEquals(2, ledgerRepository.getTransactions().size)
    }

    // --- Остаток ---

    @Test
    fun olderStatementDoesNotOverwriteAFresherBalance() = runTest {
        val newer = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915", balance = "5 000,00"),
        )
        val older = statement(
            row("10.10.2026 15:46", "1001", expense = "370,00", description = "Оплата в AUCHAN 6915", balance = "1 000,00"),
        )

        importRepository.commit(importRepository.analyze(newer, "новый.csv"))
        assertEquals(500_000L, ledgerRepository.getAccounts().single().balanceMinor)

        importRepository.commit(importRepository.analyze(older, "старый.csv"))
        assertEquals(
            500_000L,
            ledgerRepository.getAccounts().single().balanceMinor,
            "Старая выписка не должна откатывать остаток",
        )
    }

    @Test
    fun newerStatementUpdatesTheBalance() = runTest {
        val older = statement(
            row("10.10.2026 15:46", "1001", expense = "370,00", description = "Оплата в AUCHAN 6915", balance = "1 000,00"),
        )
        val newer = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915", balance = "5 000,00"),
        )

        importRepository.commit(importRepository.analyze(older, "старый.csv"))
        importRepository.commit(importRepository.analyze(newer, "новый.csv"))

        assertEquals(500_000L, ledgerRepository.getAccounts().single().balanceMinor)
    }

    // --- Прочее ---

    @Test
    fun twoIdenticalOperationsInOneFileAreBothImported() = runTest {
        // Так бывает в справке Т-Банка: два возврата на одну сумму в одну минуту.
        val csv = statement(
            row("21.07.2026 18:11", "7001", income = "50,00", description = "Возврат средств по операции оплаты"),
            row("21.07.2026 18:11", "7002", income = "50,00", description = "Возврат средств по операции оплаты"),
        )

        val preview = importRepository.analyze(csv, "выписка.csv")
        assertEquals(2, preview.newCount, "Обе одинаковые операции должны добавиться")
        assertEquals(0, preview.duplicateCount)

        importRepository.commit(preview)
        val transactions = ledgerRepository.getTransactions()
        assertEquals(2, transactions.size, "Обе операции должны сохраниться")
        assertEquals(10_000L, transactions.sumOf { it.amountMinor }, "Сумма операций должна совпасть с выпиской")
        assertEquals(2, transactions.map { it.fingerprint }.toSet().size, "Отпечатки должны различаться")
    }

    @Test
    fun repeatedImportOfIdenticalOperationsDoesNotDuplicateThem() = runTest {
        val csv = statement(
            row("21.07.2026 18:11", "7001", income = "50,00", description = "Возврат средств по операции оплаты"),
            row("21.07.2026 18:11", "7002", income = "50,00", description = "Возврат средств по операции оплаты"),
        )

        importRepository.commit(importRepository.analyze(csv, "выписка.csv"))
        val second = importRepository.analyze(csv, "выписка.csv")
        assertEquals(0, second.newCount)
        assertEquals(0, second.updatedCount)
        assertEquals(2, second.duplicateCount)

        importRepository.commit(second)
        assertEquals(2, ledgerRepository.getTransactions().size)
    }

    @Test
    fun importBatchKeepsCounters() = runTest {
        val csv = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915"),
            row("11.11.2026 16:46", "202640", expense = "370,00", description = "Оплата в AUCHAN 6915"),
        )

        val batch = importRepository.commit(importRepository.analyze(csv, "выписка.csv"))

        assertEquals(2, batch.newRows)
        assertEquals(0, batch.updatedRows)
        assertEquals(0, batch.skippedDuplicates)
        assertEquals(2, batch.totalRows)
        assertEquals("CSV", batch.fileType)
    }

    @Test
    fun documentIdIsStoredForFutureMerges() = runTest {
        val csv = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915"),
        )
        importRepository.commit(importRepository.analyze(csv, "выписка.csv"))

        assertEquals("202639", ledgerRepository.getTransactions().single().documentId)
    }

    @Test
    fun previewMarksEveryRowWithItsDecision() = runTest {
        val first = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915"),
        )
        importRepository.commit(importRepository.analyze(first, "выписка.csv"))

        val second = statement(
            row("11.11.2026 15:46", "202639", expense = "370,00", description = "Оплата в AUCHAN 6915"),
            row("12.11.2026 15:46", "202640", expense = "400,00", description = "Оплата в AUCHAN 6915"),
        )
        val preview = importRepository.analyze(second, "выписка-2.csv")

        assertEquals(2, preview.rows.size)
        assertEquals(MergeAction.SKIP, preview.rows[0].mergeAction)
        assertEquals(MergeAction.ADD, preview.rows[1].mergeAction)
        assertNull(preview.rows[1].existingTransactionId)
        assertNotNull(preview.rows[0].existingTransactionId)
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
