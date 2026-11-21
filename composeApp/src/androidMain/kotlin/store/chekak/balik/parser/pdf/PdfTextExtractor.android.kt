package store.chekak.balik.parser.pdf

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * Android-реализация на PDFBox-Android.
 * [PDFBoxResourceLoader.init] вызывается в Application — он подгружает ресурсы PDFBox
 * (шрифты и прочее) из APK.
 */
actual object PdfTextExtractor {

    fun init(context: android.content.Context) {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    actual fun extractText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        return runCatching {
            PDDocument.load(bytes.inputStream()).use { document ->
                val stripper = PDFTextStripper()
                // Читаем по позициям: иначе ячейки таблицы «рассыпаются» по колонкам.
                stripper.sortByPosition = true
                stripper.startPage = 1
                stripper.endPage = document.numberOfPages
                stripper.getText(document)
            }
        }.getOrElse { "" }
    }
}
