package store.chekak.balik.data.db

import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager

actual object DatabaseFile {

    actual fun path(): String = DatabaseProvider.databaseFilePath()

    actual suspend fun readBytes(): ByteArray? = withContext(Dispatchers.IO) {
        val filePath = path()
        val data = NSFileManager.defaultManager.contentsAtPath(filePath) ?: return@withContext null
        val size = data.length.toInt()
        if (size == 0) return@withContext null
        val bytes = ByteArray(size)
        bytes.usePinned { pinned ->
            data.getBytes(pinned.addressOf(0), length = data.length)
        }
        bytes
    }
}
