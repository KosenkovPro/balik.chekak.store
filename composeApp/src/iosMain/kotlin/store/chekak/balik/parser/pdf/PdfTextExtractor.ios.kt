package store.chekak.balik.parser.pdf

import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.PDFKit.PDFDocument

/**
 * iOS-реализация на системном PDFKit. Сеть не используется.
 */
actual object PdfTextExtractor {

    actual fun extractText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val data = bytes.usePinned { pinned ->
            NSData(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        val document = PDFDocument(data = data) ?: return ""
        val result = StringBuilder()
        for (index in 0 until document.pageCount) {
            val page = document.pageAtIndex(index) ?: continue
            val text = page.string
            if (!text.isNullOrBlank()) {
                result.append(text)
                result.append("\n")
            }
        }
        return result.toString()
    }
}
