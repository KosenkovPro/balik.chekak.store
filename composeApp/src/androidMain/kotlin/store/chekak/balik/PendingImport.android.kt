package store.chekak.balik

import java.io.File

/** Android: файл, сохранённый ShareReceiverActivity. */
actual fun pendingImportBytes(): Pair<String, ByteArray>? {
    val file = PendingImport.file ?: return null
    PendingImport.file = null
    if (!file.exists()) return null
    return file.name to file.readBytes()
}
