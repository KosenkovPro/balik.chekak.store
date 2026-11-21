package store.chekak.balik.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS-реализация. Файл БД лежит в общем контейнере App Group — так его видят
 * Share Extension и виджет, работающие в отдельных процессах.
 */
actual object DatabaseProvider {

    actual fun create(): AppDatabase {
        return Room.databaseBuilder<AppDatabase>(
            name = databaseFilePath(),
            factory = { AppDatabaseConstructor.initialize() },
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigration(false)
            .build()
    }

    fun databaseFilePath(): String {
        val directory = sharedContainerPath() ?: documentsPath()
        return "$directory/$DATABASE_NAME"
    }

    /** Контейнер App Group, если entitlement выдан и он доступен. */
    fun sharedContainerPath(): String? = runCatching {
        val container = NSFileManager.defaultManager
            .containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_ID)
        container?.path
    }.getOrNull()

    private fun documentsPath(): String {
        val urls = NSSearchPathForDirectoriesInDomains(
            NSSearchPathDirectory.NSDocumentDirectory,
            NSSearchPathDirectory.NSUserDomainMask,
            true,
        )
        val path = urls.firstOrNull() as? String ?: "."
        return "$path/Database"
    }

    const val DATABASE_NAME = "chekak.sqlite"
    const val APP_GROUP_ID = "group.store.chekak.balik"
}
