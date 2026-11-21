package store.chekak.balik.parser.xlsx

/**
 * Распаковка DEFLATE (RFC 1951) в чистом Kotlin — нужна, чтобы читать XLSX
 * (это ZIP-архив) на обеих платформах без нативных зависимостей.
 */
object Inflate {

    private const val MAX_BITS = 15

    private val lengthBase = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
    )
    private val lengthExtra = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
    )
    private val distanceBase = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577,
    )
    private val distanceExtra = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
    )
    private val codeLengthOrder = intArrayOf(
        16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15,
    )

    fun inflate(data: ByteArray, expectedSize: Int = 0): ByteArray {
        val reader = BitReader(data)
        val output = ByteArrayBuilder(if (expectedSize > 0) expectedSize else data.size * 4)

        while (true) {
            val isFinal = reader.readBits(1)
            val blockType = reader.readBits(2)
            when (blockType) {
                0 -> inflateStored(reader, output)
                1 -> {
                    val (lit, dist) = fixedTables()
                    inflateBlock(reader, output, lit, dist)
                }
                2 -> {
                    val (lit, dist) = readDynamicTables(reader)
                    inflateBlock(reader, output, lit, dist)
                }
                else -> throw IllegalArgumentException("Неизвестный тип блока DEFLATE: $blockType")
            }
            if (isFinal == 1) break
        }
        return output.toByteArray()
    }

    private fun inflateStored(reader: BitReader, output: ByteArrayBuilder) {
        reader.alignToByte()
        val length = reader.readBits(16)
        val nlength = reader.readBits(16)
        if ((length.inv() and 0xFFFF) != nlength) {
            throw IllegalArgumentException("Повреждённый stored-блок DEFLATE")
        }
        repeat(length) { output.append(reader.readByte().toByte()) }
    }

    private fun inflateBlock(reader: BitReader, output: ByteArrayBuilder, lit: Huffman, dist: Huffman) {
        while (true) {
            val symbol = lit.decode(reader)
            when {
                symbol < 256 -> output.append(symbol.toByte())
                symbol == 256 -> return
                else -> {
                    val lengthIndex = symbol - 257
                    if (lengthIndex >= lengthBase.size) throw IllegalArgumentException("Некорректный код длины: $symbol")
                    val length = lengthBase[lengthIndex] + reader.readBits(lengthExtra[lengthIndex])
                    val distSymbol = dist.decode(reader)
                    if (distSymbol >= distanceBase.size) throw IllegalArgumentException("Некорректный код расстояния: $distSymbol")
                    val distance = distanceBase[distSymbol] + reader.readBits(distanceExtra[distSymbol])
                    output.copyFromDistance(distance, length)
                }
            }
        }
    }

    private fun readDynamicTables(reader: BitReader): Pair<Huffman, Huffman> {
        val hlit = reader.readBits(5) + 257
        val hdist = reader.readBits(5) + 1
        val hclen = reader.readBits(4) + 4

        val codeLengths = IntArray(19)
        for (i in 0 until hclen) {
            codeLengths[codeLengthOrder[i]] = reader.readBits(3)
        }
        val codeLengthTable = Huffman(codeLengths)

        val lengths = IntArray(hlit + hdist)
        var index = 0
        while (index < lengths.size) {
            when (val symbol = codeLengthTable.decode(reader)) {
                in 0..15 -> {
                    lengths[index++] = symbol
                }
                16 -> {
                    if (index == 0) throw IllegalArgumentException("Некорректный повтор в DEFLATE")
                    val previous = lengths[index - 1]
                    var repeat = reader.readBits(2) + 3
                    while (repeat-- > 0 && index < lengths.size) lengths[index++] = previous
                }
                17 -> {
                    var repeat = reader.readBits(3) + 3
                    while (repeat-- > 0 && index < lengths.size) lengths[index++] = 0
                }
                18 -> {
                    var repeat = reader.readBits(7) + 11
                    while (repeat-- > 0 && index < lengths.size) lengths[index++] = 0
                }
                else -> throw IllegalArgumentException("Некорректный код длины: $symbol")
            }
        }

        val literalLengths = lengths.copyOfRange(0, hlit)
        val distanceLengths = lengths.copyOfRange(hlit, lengths.size)
        return Huffman(literalLengths) to Huffman(distanceLengths)
    }

    private fun fixedTables(): Pair<Huffman, Huffman> {
        val literalLengths = IntArray(288)
        for (i in 0..143) literalLengths[i] = 8
        for (i in 144..255) literalLengths[i] = 9
        for (i in 256..279) literalLengths[i] = 7
        for (i in 280..287) literalLengths[i] = 8
        val distanceLengths = IntArray(30) { 5 }
        return Huffman(literalLengths) to Huffman(distanceLengths)
    }

    private class BitReader(private val data: ByteArray) {
        private var bytePos = 0
        private var bitBuffer = 0
        private var bitCount = 0

        fun readBits(count: Int): Int {
            var value = 0
            for (i in 0 until count) {
                if (bitCount == 0) {
                    bitBuffer = if (bytePos < data.size) data[bytePos++].toInt() and 0xFF else 0
                    bitCount = 8
                }
                value = value or ((bitBuffer and 1) shl i)
                bitBuffer = bitBuffer ushr 1
                bitCount--
            }
            return value
        }

        fun readByte(): Int {
            if (bitCount >= 8) {
                val value = bitBuffer and 0xFF
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
                return value
            }
            return if (bytePos < data.size) data[bytePos++].toInt() and 0xFF else 0
        }

        fun alignToByte() {
            bitCount = 0
            bitBuffer = 0
        }
    }

    private class Huffman(lengths: IntArray) {
        private val counts = IntArray(MAX_BITS + 1)
        private val symbols = IntArray(lengths.size)

        init {
            for (length in lengths) {
                if (length in 1..MAX_BITS) counts[length]++
            }
            val offsets = IntArray(MAX_BITS + 1)
            for (bits in 1..MAX_BITS) offsets[bits] = offsets[bits - 1] + counts[bits - 1]
            for (symbol in lengths.indices) {
                val length = lengths[symbol]
                if (length in 1..MAX_BITS) symbols[offsets[length]++] = symbol
            }
        }

        fun decode(reader: BitReader): Int {
            var code = 0
            var first = 0
            var index = 0
            for (length in 1..MAX_BITS) {
                code = code or reader.readBits(1)
                val count = counts[length]
                if (code - first < count) return symbols[index + (code - first)]
                index += count
                first = (first + count) shl 1
                code = code shl 1
            }
            throw IllegalArgumentException("Некорректный код Хаффмана")
        }
    }

    /** Простой растущий буфер с поддержкой копирования из окна. */
    private class ByteArrayBuilder(initialCapacity: Int) {
        private var buffer = ByteArray(maxOf(16, initialCapacity))
        private var size = 0

        fun append(byte: Byte) {
            ensureCapacity(size + 1)
            buffer[size++] = byte
        }

        fun copyFromDistance(distance: Int, length: Int) {
            if (distance > size) throw IllegalArgumentException("Расстояние за пределами окна: $distance")
            ensureCapacity(size + length)
            var from = size - distance
            repeat(length) {
                buffer[size++] = buffer[from++]
            }
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)

        private fun ensureCapacity(capacity: Int) {
            if (capacity <= buffer.size) return
            var newSize = buffer.size * 2
            while (newSize < capacity) newSize *= 2
            buffer = buffer.copyOf(newSize)
        }
    }
}
