package store.chekak.balik.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

/**
 * Миграция 1 → 2.
 *
 * Добавляется:
 *  * `import_batches.updatedRows` — сколько операций обновилось при импорте;
 *  * `transactions.documentId` — банковский номер операции, по которому выписки
 *    одного банка склеиваются между собой;
 *  * `categories.key` — стабильный ключ системной категории (по нему их находят
 *    правила, лимиты и движок категоризации);
 *  * таблица `import_row_backups` — снимки обновлённых операций, чтобы откат
 *    импорта возвращал их прежний вид.
 *
 * Ключи уже существующих системных категорий восстанавливаются по названию:
 * без этого после обновления движок категоризации перестал бы узнавать
 * системные категории, а импорт — проставлять категории.
 *
 * SQL повторяет схему, которую Room ожидает для версии 2 (см. schemas/…/2.json).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execute(
            "ALTER TABLE `import_batches` ADD COLUMN `updatedRows` INTEGER NOT NULL DEFAULT 0",
        )
        connection.execute("ALTER TABLE `transactions` ADD COLUMN `documentId` TEXT")
        connection.execute("ALTER TABLE `categories` ADD COLUMN `key` TEXT")
        restoreCategoryKeys(connection)
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS `import_row_backups` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `batchId` INTEGER NOT NULL,
                `transactionId` INTEGER NOT NULL,
                `fingerprint` TEXT NOT NULL,
                `rawDescription` TEXT NOT NULL,
                `merchantName` TEXT NOT NULL,
                `mcc` TEXT,
                `categoryId` INTEGER,
                `isInternalTransfer` INTEGER NOT NULL,
                FOREIGN KEY(`batchId`) REFERENCES `import_batches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        connection.execute(
            "CREATE INDEX IF NOT EXISTS `index_import_row_backups_batchId` " +
                "ON `import_row_backups` (`batchId`)",
        )
    }
}

/**
 * Проставляет ключи системным категориям, у которых после миграции ключа нет.
 *
 * Узнаём категорию по названию (русскому или английскому) — в версии 1 ключа
 * в базе не было, но название осталось прежним.
 */
private fun restoreCategoryKeys(connection: SQLiteConnection) {
    val statement = connection.prepare(
        "UPDATE `categories` SET `key` = ? WHERE `key` IS NULL AND (`name` = ? OR `nameEn` = ?)",
    )
    try {
        for (category in SeedData.categories) {
            val key = category.key ?: continue
            statement.reset()
            statement.clearBindings()
            statement.bindText(1, key)
            statement.bindText(2, category.name)
            statement.bindText(3, category.nameEn)
            statement.step()
        }
    } finally {
        statement.close()
    }
}

/**
 * Миграция 2 → 3.
 *
 * Добавляется таблица `deposits` — банковские вклады (депозиты): сумма, ставка,
 * срок и схема начисления процентов. По ним считается рост вклада и налог
 * на проценты по правилам НК РФ.
 *
 * SQL повторяет схему, которую Room ожидает для версии 3 (см. schemas/…/3.json).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS `deposits` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `bankName` TEXT NOT NULL,
                `amountMinor` INTEGER NOT NULL,
                `annualRatePercent` REAL NOT NULL,
                `startDate` INTEGER NOT NULL,
                `endDate` INTEGER NOT NULL,
                `payout` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

/** Все миграции базы — подключаются к каждому провайдеру. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

/** Выполняет SQL-команду, не возвращающую строк. */
private fun SQLiteConnection.execute(sql: String) {
    val statement = prepare(sql)
    try {
        statement.step()
    } finally {
        statement.close()
    }
}
