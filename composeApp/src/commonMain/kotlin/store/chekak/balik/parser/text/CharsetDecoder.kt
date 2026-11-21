package store.chekak.balik.parser.text

/**
 * Определение кодировки банковских выписок.
 *
 * Российские банки отдают выгрузки в двух основных вариантах:
 *  * UTF-8 (часто с BOM) — актуальные выгрузки Райффайзена;
 *  * Windows-1251 (CP1251) — классика, в т.ч. Excel-выгрузки «в кодировке 1251».
 *
 * Стратегия: сначала BOM, затем строгая проверка UTF-8, и только если она не прошла —
 * однобайтовая раскодировка CP1251 (она не может «сломать» файл, в отличие от guess).
 */
object CharsetDecoder {

    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /** CP1251: 0x80..0x9F и 0xC0..0xDF — заглавные, 0xA0..0xBF и 0xE0..0xFF — строчные. */
    private val cp1251Upper = "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ"
    private val cp1251Lower = "абвгдежзийклмнопрстуфхцчшщъыьэюя"

    enum class Encoding { UTF8, CP1251 }

    data class Decoded(val text: String, val encoding: Encoding, val hadBom: Boolean)

    fun decode(bytes: ByteArray): Decoded {
        if (bytes.isEmpty()) return Decoded("", Encoding.UTF8, false)

        val hadBom = bytes.size >= 3 &&
            bytes[0] == utf8Bom[0] && bytes[1] == utf8Bom[1] && bytes[2] == utf8Bom[2]

        val payload = if (hadBom) bytes.copyOfRange(3, bytes.size) else bytes

        // Строгая проверка UTF-8: если декодирование без ошибок — это UTF-8.
        val utf8 = runCatching {
            payload.decodeToString(startIndex = 0, endIndex = payload.size, throwOnInvalidSequence = true)
        }.getOrNull()
        if (utf8 != null) return Decoded(utf8, Encoding.UTF8, hadBom)

        return Decoded(decodeCp1251(payload), Encoding.CP1251, false)
    }

    fun decodeCp1251(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(
                when (v) {
                    in 0x00..0x7F -> v.toChar()
                    in 0x80..0x9F -> cp1251Upper[v - 0x80]
                    in 0xA0..0xBF -> cp1251Lower[v - 0xA0]
                    in 0xC0..0xDF -> cp1251Upper[v - 0xC0]
                    in 0xE0..0xFF -> cp1251Lower[v - 0xE0]
                    else -> '?'
                }
            )
        }
        return sb.toString()
    }
}
