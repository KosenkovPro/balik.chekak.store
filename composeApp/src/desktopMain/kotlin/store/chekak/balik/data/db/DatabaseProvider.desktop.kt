package store.chekak.balik.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * JVM-реализация: база в рабочей папке проекта. Используется только в тестах
 * и локальных запусках, в приложении база живёт на устройстве.
 */
actual object DatabaseProvider {

    actual fun create(): AppDatabase {
        val directory = File("build/databases").apply { mkdirs() }
        return Room.databaseBuilder<AppDatabase>(
            name = File(directory, DATABASE_NAME).absolutePath,
            factory = { AppDatabaseConstructor.initialize() },
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigration(false)
            .build()
    }

    const val DATABASE_NAME = "chekak.db"
}
