package store.chekak.balik.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Android-реализация: файл БД в приватном хранилище приложения.
 * Контекст инициализируется в Application, чтобы провайдер оставался
 * единственным источником экземпляра БД для всех экранов и виджета.
 */
actual object DatabaseProvider {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun create(): AppDatabase {
        val context = appContext
            ?: error("DatabaseProvider.init(context) не вызван: нет Application-контекста")
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        return Room.databaseBuilder<AppDatabase>(
            context = context,
            name = dbFile.absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigration(false)
            .build()
    }

    const val DATABASE_NAME = "chekak.db"
}
