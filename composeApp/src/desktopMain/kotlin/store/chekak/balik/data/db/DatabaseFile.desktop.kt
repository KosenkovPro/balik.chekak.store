package store.chekak.balik.data.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual object DatabaseFile {

    actual fun path(): String = File("build/databases", DatabaseProvider.DATABASE_NAME).absolutePath

    actual suspend fun readBytes(): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(path())
        if (file.exists()) file.readBytes() else null
    }
}
