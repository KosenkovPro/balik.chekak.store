package store.chekak.balik.data.db

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
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
import store.chekak.balik.data.repository.ImportRepository
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.parser.ParserRegistry

/**
 * Проверка обновления базы с версии 1 до версии 2 на настоящем SQLite.
 *
 * База версии 1 создаётся вручную (тем же SQL, что Room генерировал для неё),
 * в неё кладутся данные — как у пользователя, который давно пользуется
 * приложением. Затем открываем базу обычным Room: он обязан сам выполнить
 * миграцию, проверить схему и не потерять данные.
 */
class MigrationTest {

    private lateinit var databaseFile: File
    private var database: AppDatabase? = null

    @BeforeTest
    fun setUp() {
        databaseFile = File.createTempFile("chekak-migration-test", ".db")
        databaseFile.delete()
        createVersion1Database(databaseFile)
    }

    @AfterTest
    fun tearDown() {
        database?.close()
        databaseFile.delete()
    }

    @Test
    fun migratesFromVersion1AndKeepsAllData() = runTest {
        val db = openDatabase()

        // Данные на месте.
        val transactions = db.transactionDao().getAll()
        assertEquals(2, transactions.size, "Операции не должны потеряться при миграции")
        val expense = transactions.first { it.amountMinor < 0 }
        assertEquals(-37000L, expense.amountMinor)
        assertEquals("Оплата в AUCHAN 6915", expense.rawDescription)
        assertEquals("AUCHAN", expense.merchantName)
        assertEquals("auchan-1", expense.fingerprint)

        // Новые колонки появились и заполнены осмысленно.
        assertNull(expense.documentId, "У старых операций номера документа нет")
        assertEquals(0, db.importBatchDao().getById(1)?.updatedRows)

        // Ключи системных категорий восстановлены — иначе категоризация сломалась бы.
        val categories = db.categoryDao().getAll()
        assertEquals(12, categories.size)
        assertTrue(
            categories.all { !it.key.isNullOrBlank() },
            "У всех системных категорий должен быть ключ после миграции",
        )
        val groceries = categories.first { it.name == "Продукты" }
        assertEquals(SeedData.CategoryKeys.GROCERY, groceries.key)

        // Таблица снимков для отката создана.
        assertTrue(tableExists(db, "import_row_backups"))
        assertTrue(db.importRowBackupDao().getByBatch(1).isEmpty())

        // Таблица вкладов создана, но у пользователя вкладов не было.
        assertTrue(tableExists(db, "deposits"))
        assertTrue(db.depositDao().getAll().isEmpty())

        // Room принял схему и переписал отметку версии.
        assertEquals(V3_IDENTITY_HASH, identityHash(db))
    }

    /**
     * Обновление с версии 2 до версии 3: появляются вклады.
     *
     * База версии 2 готовится из базы версии 1 настоящей миграцией 1 → 2 —
     * тем же кодом, что работает в приложении. Дальше Room обязан сам выполнить
     * миграцию 2 → 3, создать таблицу вкладов и не потерять данные.
     */
    @Test
    fun migratesFromVersion2AndCreatesDepositsTable() = runTest {
        prepareVersion2Database(databaseFile)

        val db = openDatabase()

        assertTrue(tableExists(db, "deposits"), "После миграции должна появиться таблица вкладов")
        assertTrue(db.depositDao().getAll().isEmpty(), "У пользователя вкладов ещё не было")

        // Старые данные на месте: миграция ничего не потеряла.
        val transactions = db.transactionDao().getAll()
        assertEquals(2, transactions.size, "Операции не должны потеряться при миграции")
        assertEquals(-37000L, transactions.first { it.amountMinor < 0 }.amountMinor)
        assertEquals(12, db.categoryDao().getAll().size)

        // Вкладами можно пользоваться сразу после миграции.
        val depositId = db.depositDao().insert(
            DepositEntity(
                name = "Накопительный",
                bankName = "Райффайзенбанк",
                amountMinor = 500_000_00L,
                annualRatePercent = 14.5,
                startDate = 1_767_225_600_000L,
                endDate = 1_769_769_600_000L,
                payout = DepositPayout.CAPITALIZED,
            ),
        )
        assertTrue(depositId > 0)
        assertEquals(1, db.depositDao().getAll().size)

        assertEquals(V3_IDENTITY_HASH, identityHash(db))
    }

    @Test
    fun migratedDatabaseStillCategorizesNewImports() = runTest {
        val db = openDatabase()
        val ledger = LedgerRepository(
            accountDao = db.accountDao(),
            categoryDao = db.categoryDao(),
            transactionDao = db.transactionDao(),
            ruleDao = db.categoryRuleDao(),
            budgetDao = db.budgetDao(),
            importBatchDao = db.importBatchDao(),
            importRowBackupDao = db.importRowBackupDao(),
        )
        val importRepository = ImportRepository(
            ledgerRepository = ledger,
            settingsRepository = SettingsRepository(db.appSettingDao(), db.ownAccountDao()),
            parserRegistry = ParserRegistry(),
        )

        val csv = buildString {
            append(
                "Дата операции;Выполнено банком;Номер документа;Поступления;Расходы;Валюта;" +
                    "Детали операции (назначение платежа);Номер карты;Остаток\r\n",
            )
            append(
                "\"11.11.2026 15:46\";\"11.11.2026 15:46\";\"202639\";\"\";\"370,00\";\"RUB\";" +
                    "\"Оплата в AUCHAN 6915\";\"**8872\";\"1 000,00\"\r\n",
            )
        }.toByteArray(Charsets.UTF_8)

        val preview = importRepository.analyze(csv, "выписка.csv")
        // Категория определилась по ключу системной категории — значит, ключи на месте.
        assertNotNull(preview.rows.single().categoryId, "После миграции импорт должен проставлять категорию")
        assertEquals(1, preview.newCount, "Операция из выписки должна добавиться как новая")
    }

    private suspend fun openDatabase(): AppDatabase {
        val db = Room.databaseBuilder<AppDatabase>(
            name = databaseFile.absolutePath,
            factory = { AppDatabaseConstructor.initialize() },
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigration(false)
            .build()
        database = db
        // Первое обращение открывает файл и запускает миграцию.
        db.transactionDao().getAll()
        return db
    }

    private fun tableExists(db: AppDatabase, table: String): Boolean {
        val connection = BundledSQLiteDriver().open(databaseFile.absolutePath)
        try {
            val statement = connection.prepare("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?")
            try {
                statement.bindText(1, table)
                statement.step()
                return statement.getLong(0) > 0
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }

    private fun identityHash(db: AppDatabase): String {
        val connection = BundledSQLiteDriver().open(databaseFile.absolutePath)
        try {
            val statement = connection.prepare("SELECT identity_hash FROM room_master_table WHERE id = 42")
            try {
                statement.step()
                return statement.getText(0)
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
        }
    }
}

private const val V2_IDENTITY_HASH = "7cc6a8779df9e402500e44b8ba2fd2e3"
private const val V3_IDENTITY_HASH = "3c032358616b107fc8fcdf6930b2dbf3"

/**
 * Готовит базу данных версии 2 из версии 1.
 *
 * Миграция 1 → 2 выполняется настоящим кодом приложения, поэтому тест
 * проверяет ровно то, что увидит пользователь при обновлении.
 */
private fun prepareVersion2Database(file: File) {
    val connection = BundledSQLiteDriver().open(file.absolutePath)
    try {
        // Схема версии 1 и данные уже созданы в setUp — остаётся её обновить.
        MIGRATION_1_2.migrate(connection)
        connection.exec("PRAGMA user_version = 2")
        connection.exec(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                "VALUES(42, '$V2_IDENTITY_HASH')",
        )
    } finally {
        connection.close()
    }
}

/**
 * Создаёт базу данных версии 1 — той самой схемой, которую Room генерировал
 * для первой версии приложения (см. schemas/…/1.json).
 */
private fun createVersion1Database(file: File) {
    val connection = BundledSQLiteDriver().open(file.absolutePath)
    try {
        createSchema(connection)
        insertUserData(connection)
    } finally {
        connection.close()
    }
}

private fun createSchema(db: SQLiteConnection) {
        db.exec(
            """
            CREATE TABLE IF NOT EXISTS `accounts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `bankName` TEXT NOT NULL, `accountName` TEXT NOT NULL, `currency` TEXT NOT NULL,
                `cardLast4` TEXT, `balanceMinor` INTEGER, `isCash` INTEGER NOT NULL,
                `isArchived` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.exec(
            """
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL, `nameEn` TEXT NOT NULL, `iconName` TEXT NOT NULL,
                `colorHex` TEXT NOT NULL, `isSystem` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.exec(
            """
            CREATE TABLE IF NOT EXISTS `import_batches` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `fileName` TEXT NOT NULL, `bankName` TEXT NOT NULL, `importedAt` INTEGER NOT NULL,
                `totalRows` INTEGER NOT NULL, `newRows` INTEGER NOT NULL,
                `skippedDuplicates` INTEGER NOT NULL, `accountId` INTEGER, `fileType` TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.exec(
            """
            CREATE TABLE IF NOT EXISTS `transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `fingerprint` TEXT NOT NULL, `accountId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL,
                `amountMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `rawDescription` TEXT NOT NULL,
                `merchantName` TEXT NOT NULL, `mcc` TEXT, `categoryId` INTEGER,
                `isInternalTransfer` INTEGER NOT NULL, `isManual` INTEGER NOT NULL,
                `importBatchId` INTEGER, `note` TEXT, `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`importBatchId`) REFERENCES `import_batches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.exec("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_fingerprint` ON `transactions` (`fingerprint`)")
        db.exec("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
        db.exec("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
        db.exec("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)")
        db.exec("CREATE INDEX IF NOT EXISTS `index_transactions_importBatchId` ON `transactions` (`importBatchId`)")
        db.exec(
            """
            CREATE TABLE IF NOT EXISTS `category_rules` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pattern` TEXT NOT NULL,
                `matchType` TEXT NOT NULL, `categoryId` INTEGER NOT NULL,
                `setInternalTransfer` INTEGER NOT NULL, `priority` INTEGER NOT NULL,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.exec(
            """
            CREATE TABLE IF NOT EXISTS `budgets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER NOT NULL,
                `limitMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `period` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.exec(
            """
            CREATE TABLE IF NOT EXISTS `own_accounts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `label` TEXT NOT NULL,
                `normalized` TEXT NOT NULL, `isArchived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.exec(
            """
            CREATE TABLE IF NOT EXISTS `app_settings` (
                `key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`)
            )
            """.trimIndent(),
        )
        db.exec("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.exec(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                "VALUES(42, '15dd6a06fa824fdbc622a2b1154b5a6a')",
        )
        // Версия базы: Room ориентируется на user_version — без неё он счёл бы файл
        // «пре-упакованным» и проверил бы схему вместо миграции.
        db.exec("PRAGMA user_version = 1")
    }

/** Данные пользователя: счёт, системные категории, батч импорта и операции. */
private fun insertUserData(db: SQLiteConnection) {
    db.exec(
        "INSERT INTO `accounts` (`id`,`bankName`,`accountName`,`currency`,`cardLast4`," +
            "`balanceMinor`,`isCash`,`isArchived`,`lastUpdated`) VALUES " +
            "(1,'Райффайзенбанк','Карта **8872','RUB','8872',500000,0,0,1766000000000)",
    )
    SeedData.categories.forEachIndexed { index, category ->
        db.exec(
            "INSERT INTO `categories` (`id`,`name`,`nameEn`,`iconName`,`colorHex`,`isSystem`,`sortOrder`) " +
                "VALUES (${index + 1},'${category.name}','${category.nameEn}','${category.iconName}'," +
                "'${category.colorHex}',${category.isSystem},${category.sortOrder})",
        )
    }
    db.exec(
        "INSERT INTO `import_batches` (`id`,`fileName`,`bankName`,`importedAt`,`totalRows`," +
            "`newRows`,`skippedDuplicates`,`accountId`,`fileType`) VALUES " +
            "(1,'выписка.csv','Райффайзенбанк',1766000000000,2,2,0,1,'CSV')",
    )
    db.exec(
        "INSERT INTO `transactions` (`id`,`fingerprint`,`accountId`,`timestamp`,`amountMinor`," +
            "`currency`,`rawDescription`,`merchantName`,`mcc`,`categoryId`,`isInternalTransfer`," +
            "`isManual`,`importBatchId`,`note`,`createdAt`) VALUES " +
            "(1,'auchan-1',1,1766000000000,-37000,'RUB','Оплата в AUCHAN 6915','AUCHAN',NULL,2,0,0,1,NULL," +
            "1766000000000)",
    )
    db.exec(
        "INSERT INTO `transactions` (`id`,`fingerprint`,`accountId`,`timestamp`,`amountMinor`," +
            "`currency`,`rawDescription`,`merchantName`,`mcc`,`categoryId`,`isInternalTransfer`," +
            "`isManual`,`importBatchId`,`note`,`createdAt`) VALUES " +
            "(2,'salary-1',1,1765500000000,500000,'RUB','Перевод от работодателя','Работодатель',NULL,10,0,0,1,NULL," +
            "1765500000000)",
    )
}

/** Выполняет SQL-команду, не возвращающую строк. */
private fun SQLiteConnection.exec(sql: String) {
    val statement = prepare(sql)
    try {
        statement.step()
    } finally {
        statement.close()
    }
}
