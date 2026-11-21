package store.chekak.balik

import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager

/**
 * iOS: файл, положенный Share Extension в общий контейнер App Group.
 * Забираем самый свежий файл с префиксом "import-".
 */
actual fun pendingImportBytes(): Pair<String, ByteArray>? {
    val container = store.chekak.balik.data.db.DatabaseProvider.sharedContainerPath() ?: return null
    val directory = "$container/PendingImports"

    val names = NSFileManager.defaultManager.contentsOfDirectoryAtPath(
        directory,
        error = null,
    ) as? List<*> ?: return null

    val candidates = names.filterIsInstance<String>().filter { it.startsWith("import-") }
    if (candidates.isEmpty()) return null

    val newest = candidates.maxByOrNull { name ->
        NSFileManager.defaultManager.attributesOfItemAtPath("$directory/$name", null)
            ?.let { attributes ->
                (attributes as? Map<*, *>)?.get("NSFileCreationDate") as? Number
            }?.toLong() ?: 0L
    } ?: return null

    val data = NSFileManager.defaultManager.contentsAtPath("$directory/$newest") ?: return null
    val size = data.length.toInt()
    if (size <= 0) return null

    val bytes = ByteArray(size).also { buffer ->
        buffer.usePinned { pinned ->
            data.getBytes(pinned.addressOf(0), length = data.length)
        }
    }
    NSFileManager.defaultManager.removeItemAtPath("$directory/$newest", null)
    return newest to bytes
}
