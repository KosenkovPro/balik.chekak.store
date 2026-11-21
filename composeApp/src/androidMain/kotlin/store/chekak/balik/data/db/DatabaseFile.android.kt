package store.chekak.balik.data.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual object DatabaseFile {

    actual fun path(): String {
        val context = appContext ?: error("DatabaseProvider.init(context) не вызван")
        return context.getDatabasePath(DatabaseProvider.DATABASE_NAME).absolutePath
    }

    actual suspend fun readBytes(): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(path())
        if (!file.exists()) null else file.readBytes()
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
