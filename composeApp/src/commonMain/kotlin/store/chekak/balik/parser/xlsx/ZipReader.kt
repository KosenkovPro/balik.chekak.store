package store.chekak.balik.parser.xlsx

/**
 * Минимальный ZIP-ридер: читает End of Central Directory, список центральных записей
 * и распаковывает содержимое (stored + deflate). Нужен для чтения XLSX.
 */
object ZipReader {

    private const val EOCD_SIGNATURE = 0x06054b50
    private const val CENTRAL_SIGNATURE = 0x02014b50
    private const val LOCAL_SIGNATURE = 0x04034b50

    class Entry(val name: String, val bytes: ByteArray)

    fun read(bytes: ByteArray): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        for (entry in readEntries(bytes)) {
            result[entry.name] = entry.bytes
        }
        return result
    }

    fun readEntries(bytes: ByteArray): List<Entry> {
        val eocd = findEocd(bytes) ?: throw IllegalArgumentException("Файл не похож на ZIP-архив")
        val entryCount = read16(bytes, eocd + 10)
        var offset = read32(bytes, eocd + 16)
        val entries = mutableListOf<Entry>()

        repeat(entryCount) {
            if (offset + 46 > bytes.size) return@repeat
            if (read32(bytes, offset) != CENTRAL_SIGNATURE) return@repeat

            val method = read16(bytes, offset + 10)
            val compressedSize = read32(bytes, offset + 20)
            val nameLength = read16(bytes, offset + 28)
            val extraLength = read16(bytes, offset + 30)
            val commentLength = read16(bytes, offset + 32)
            val localOffset = read32(bytes, offset + 42)
            val name = String(bytes, offset + 46, nameLength, Charsets.UTF_8)

            val data = readLocalEntry(bytes, localOffset, method, compressedSize)
            if (data != null) entries.add(Entry(name, data))

            offset += 46 + nameLength + extraLength + commentLength
        }
        return entries
    }

    private fun readLocalEntry(
        bytes: ByteArray,
        localOffset: Int,
        method: Int,
        compressedSize: Int,
    ): ByteArray? {
        if (localOffset + 30 > bytes.size) return null
        if (read32(bytes, localOffset) != LOCAL_SIGNATURE) return null

        val nameLength = read16(bytes, localOffset + 26)
        val extraLength = read16(bytes, localOffset + 28)
        val dataStart = localOffset + 30 + nameLength + extraLength
        val size = if (compressedSize > 0) compressedSize else (bytes.size - dataStart)
        if (dataStart + size > bytes.size) return null

        val raw = bytes.copyOfRange(dataStart, dataStart + size)
        return when (method) {
            0 -> raw
            8 -> Inflate.inflate(raw)
            else -> null
        }
    }

    private fun findEocd(bytes: ByteArray): Int? {
        val minSize = 22
        if (bytes.size < minSize) return null
        val start = maxOf(0, bytes.size - 66_000)
        for (i in bytes.size - minSize downTo start) {
            if (read32(bytes, i) == EOCD_SIGNATURE) return i
        }
        return null
    }

    private fun read16(bytes: ByteArray, offset: Int): Int {
        if (offset + 1 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun read32(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
