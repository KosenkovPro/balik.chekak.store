package store.chekak.balik.parser.xlsx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InflateTest {

    @Test
    fun inflatesRawDeflateStream() {
        // DEFLATE-поток, сжатый для строки «Балик: проверка DEFLATE 12345».
        val compressed = byteArrayOf(
            0x01, 0x2A, 0x00, 0xD5.toByte(), 0xFF.toByte(), 0xD0.toByte(), 0x91.toByte(), 0xD0.toByte(),
            0xB0.toByte(), 0xD0.toByte(), 0xBB.toByte(), 0xD0.toByte(), 0xB8.toByte(), 0xD0.toByte(),
            0xBA.toByte(), 0x3A, 0x20, 0xD0.toByte(), 0xBF.toByte(), 0xD1.toByte(), 0x80.toByte(),
            0xD0.toByte(), 0xBE.toByte(), 0xD0.toByte(), 0xB2.toByte(), 0xD0.toByte(), 0xB5.toByte(),
            0xD1.toByte(), 0x80.toByte(), 0xD0.toByte(), 0xBA.toByte(), 0xD0.toByte(), 0xB0.toByte(),
            0x20, 0x44, 0x45, 0x46, 0x4C, 0x41, 0x54, 0x45, 0x20, 0x31, 0x32, 0x33, 0x34, 0x35,
        )
        val result = Inflate.inflate(compressed)
        assertEquals("Балик: проверка DEFLATE 12345", result.decodeToString())
    }
}

class ZipReaderTest {

    @Test
    fun readsStoredEntries() {
        val bytes = buildZip(
            "xl/sharedStrings.xml" to "<sst><si><t>Дата</t></si></sst>".toByteArray(),
            "xl/worksheets/sheet1.xml" to "<worksheet><sheetData/></worksheet>".toByteArray(),
        )
        val entries = ZipReader.read(bytes)
        assertEquals(2, entries.size)
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertEquals(
            "<worksheet><sheetData/></worksheet>",
            entries.getValue("xl/worksheets/sheet1.xml").decodeToString(),
        )
    }
}

class XlsxReaderTest {

    @Test
    fun readsSheetWithSharedStrings() {
        val sharedStrings = """
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="3" uniqueCount="3">
              <si><t>Дата операции</t></si>
              <si><t>Сумма</t></si>
              <si><t>Описание</t></si>
            </sst>
        """.trimIndent()

        val sheet = """
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1">
                  <c r="A1" t="s"><v>0</v></c>
                  <c r="B1" t="s"><v>1</v></c>
                  <c r="C1" t="s"><v>2</v></c>
                </row>
                <row r="2">
                  <c r="A2"><v>01.11.2026</v></c>
                  <c r="B2"><v>370.00</v></c>
                  <c r="C2" t="inlineStr"><is><t>Оплата</t></is></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()

        val bytes = buildZip(
            "xl/sharedStrings.xml" to sharedStrings.toByteArray(),
            "xl/worksheets/sheet1.xml" to sheet.toByteArray(),
        )

        val rows = XlsxReader.readRows(bytes)
        assertEquals(2, rows.size)
        assertEquals(listOf("Дата операции", "Сумма", "Описание"), rows[0])
        assertEquals(listOf("01.11.2026", "370.00", "Оплата"), rows[1])
    }
}

/**
 * Собирает минимальный ZIP-архив с несжатыми (stored) записями —
 * ровно то, что читает [ZipReader]. Реализация на чистом Kotlin,
 * чтобы тест работал на всех платформах.
 */
internal fun buildZip(vararg entries: Pair<String, ByteArray>): ByteArray {
    val output = mutableListOf<Byte>()
    val central = mutableListOf<Byte>()
    var offset = 0

    for ((name, data) in entries) {
        val nameBytes = name.toByteArray(Charsets.UTF_8)

        output += listOf(0x50, 0x4B, 0x03, 0x04)
        writeShort(output, 20)      // версия
        writeShort(output, 0)       // флаги
        writeShort(output, 0)       // метод: stored
        writeShort(output, 0)       // время
        writeShort(output, 0)       // дата
        writeInt(output, 0)         // crc32 (проверяющая сторона его игнорирует)
        writeInt(output, data.size) // размер сжатого
        writeInt(output, data.size) // размер исходного
        writeShort(output, nameBytes.size)
        writeShort(output, 0)       // extra
        output += nameBytes.toList()
        output += data.toList()

        central += listOf(0x50, 0x4B, 0x01, 0x02)
        writeShort(central, 20)
        writeShort(central, 20)
        writeShort(central, 0)
        writeShort(central, 0)
        writeShort(central, 0)
        writeShort(central, 0)
        writeInt(central, 0)
        writeInt(central, data.size)
        writeInt(central, data.size)
        writeShort(central, nameBytes.size)
        writeShort(central, 0)
        writeShort(central, 0)
        writeShort(central, 0)
        writeShort(central, 0)
        writeInt(central, 0)
        writeInt(central, offset)
        central += nameBytes.toList()

        offset = output.size
    }

    val centralBytes = central.toByteArray()
    output += centralBytes.toList()
    output += listOf(0x50, 0x4B, 0x05, 0x06)
    writeShort(output, 0)
    writeShort(output, 0)
    writeShort(output, entries.size)
    writeShort(output, entries.size)
    writeInt(output, centralBytes.size)
    writeInt(output, offset)
    writeShort(output, 0)

    return output.toByteArray()
}

private fun writeShort(stream: MutableList<Byte>, value: Int) {
    stream += (value and 0xFF).toByte()
    stream += ((value shr 8) and 0xFF).toByte()
}

private fun writeInt(stream: MutableList<Byte>, value: Int) {
    stream += (value and 0xFF).toByte()
    stream += ((value shr 8) and 0xFF).toByte()
    stream += ((value shr 16) and 0xFF).toByte()
    stream += ((value shr 24) and 0xFF).toByte()
}
