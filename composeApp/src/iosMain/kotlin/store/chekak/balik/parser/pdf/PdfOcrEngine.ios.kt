package store.chekak.balik.parser.pdf

import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGImage
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PDFKit.PDFDisplayBoxCropBox
import platform.PDFKit.PDFDocument
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextObservation
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRequestTextRecognitionLevelAccurate

/**
 * iOS-реализация OCR: системный Vision поверх снимка страницы из PDFKit.
 *
 * Модель распознавания встроена в iOS, сеть не нужна. Vision отдаёт
 * распознанные фрагменты с рамками в нормализованных координатах
 * (начало — левый нижний угол), поэтому рамки переводятся в пиксели страницы.
 */
actual object PdfOcrEngine {

    /** Масштаб снимка страницы относительно её размера в пунктах. */
    private const val SCALE = 2.0

    /** Больше 40 страниц не распознаём: иначе импорт уходит в минуты. */
    private const val MAX_PAGES = 40

    /**
     * Минимальная высота текста, которую Vision считает значимой.
     *
     * По умолчанию Vision ищет строки не короче 1/32 высоты картинки — в
     * банковской таблице с мелким шрифтом это отсекает часть строк.
     */
    private const val MIN_TEXT_HEIGHT = 0.005f

    actual suspend fun recognizePages(bytes: ByteArray): List<OcrPage> {
        if (bytes.isEmpty()) return emptyList()
        return withContext(Dispatchers.Default) {
            runCatching {
                val data = bytes.usePinned { pinned ->
                    NSData(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                }
                val document = PDFDocument(data = data) ?: return@runCatching emptyList()
                val pages = mutableListOf<OcrPage>()
                val pageCount = minOf(document.pageCount, MAX_PAGES)
                for (index in 0 until pageCount) {
                    val page = document.pageAtIndex(index) ?: continue
                    val bounds = page.boundsForBox(PDFDisplayBoxCropBox)
                    val size = CGSizeMake(bounds.width * SCALE, bounds.height * SCALE)
                    val image = page.thumbnailOfSize(size, PDFDisplayBoxCropBox) ?: continue
                    val cgImage = image.CGImage ?: continue
                    pages += OcrPage(
                        width = cgImage.width.toInt(),
                        height = cgImage.height.toInt(),
                        words = recognize(cgImage),
                    )
                }
                pages
            }.getOrElse { emptyList() }
        }
    }

    /** Распознаёт текст на картинке и возвращает слова с рамками в пикселях. */
    private fun recognize(cgImage: CGImage): List<OcrWord> {
        val request = VNRecognizeTextRequest().apply {
            recognitionLevel = VNRequestTextRecognitionLevelAccurate
            recognitionLanguages = listOf("ru-RU", "en-US")
            // Без коррекции языка Vision не «додумывает» суммы и номера карт.
            usesLanguageCorrection = false
            minimumTextHeight = MIN_TEXT_HEIGHT
        }
        val handler = VNImageRequestHandler(cgImage, emptyMap<Any?, Any?>())
        handler.performRequests(listOf(request))

        val width = cgImage.width.toInt().toDouble()
        val height = cgImage.height.toInt().toDouble()
        val words = mutableListOf<OcrWord>()
        for (result in request.results.orEmpty()) {
            val observation = result as? VNRecognizedTextObservation ?: continue
            val candidate = observation.topCandidates(1uL).firstOrNull() ?: continue
            val text = candidate.string.trim()
            if (text.isEmpty()) continue
            val box = observation.boundingBox
            // Vision: начало координат — левый нижний угол, значения нормализованы.
            val left = (box.origin.x * width).toInt()
            val top = ((1.0 - box.origin.y - box.size.height) * height).toInt()
            val right = ((box.origin.x + box.size.width) * width).toInt()
            val bottom = ((1.0 - box.origin.y) * height).toInt()
            words += OcrWord(
                text = text,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                confidence = candidate.confidence,
            )
        }
        return words
    }
}
