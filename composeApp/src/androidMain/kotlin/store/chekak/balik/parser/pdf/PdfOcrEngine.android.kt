package store.chekak.balik.parser.pdf

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import org.bytedeco.tesseract.ResultIterator
import org.bytedeco.tesseract.TessBaseAPI
import org.bytedeco.tesseract.global.tesseract.PSM_AUTO
import org.bytedeco.tesseract.global.tesseract.RIL_WORD
import java.io.File

/**
 * Android-реализация OCR: Tesseract 5 поверх картинки страницы,
 * отрендеренной PDFBox-Android.
 *
 * Сеть не используется: движок и языковые модели лежат внутри APK.
 *
 * Tesseract требует настоящий путь файловой системы, в котором лежат
 * языковые модели: путь должен указывать прямо в каталог с файлами
 * `rus.traineddata` и `eng.traineddata`, поэтому модели один раз копируются
 * из assets в `filesDir/tessdata`.
 */
actual object PdfOcrEngine {

    /**
     * Языки распознавания.
     *
     * Русский обязателен — сама выписка на русском. Английский нужен, чтобы
     * названия магазинов читались латиницей: в модели только для русского
     * латинских букв почти нет, и движок подставляет на их место кириллицу
     * («PYATEROCHKA» превращается в «РУАТЕКОСНКА»).
     */
    private val languages = listOf("rus", "eng")

    /**
     * Языки второго чтения — только латиница.
     *
     * Слово, записанное латиницей, движок часто читает кириллическими
     * буквами («PYATEROCHKA» → «РУАТЕВОСНКА»). Тот же фрагмент, прочитанный
     * отдельно и только латиницей, даёт второе прочтение — по нему видно,
     * что слово на самом деле латинское.
     */
    private val latinLanguages = listOf("eng")

    /**
     * Языки третьего чтения — только кириллица.
     *
     * Обратный случай: движок читает кириллическое слово латиницей
     * («Отмена» → «OTMeHa», «ТОО» → «TOO»). Модель с латиницей тут не
     * помогает, поэтому фрагмент перечитывается ещё и кириллицей.
     */
    private val cyrillicLanguages = listOf("rus")

    /**
     * Разрешение рендера страницы.
     *
     * 300 dpi: текст выписки набран кеглем 7 pt, и при 200 dpi глифы выходят
     * слишком мелкими — движок чаще путает кириллицу с латиницей. На тех же
     * двадцати страницах справки Т-Банка 300 dpi даёт 439 точных описаний
     * из 473 против 392 при 200 dpi.
     */
    private const val RENDER_DPI = 300f

    /** Больше 40 страниц не распознаём: иначе импорт уходит в минуты. */
    private const val MAX_PAGES = 40

    /** Тег для диагностических сообщений в logcat. */
    private const val TAG = "CheKakOcr"

    /**
     * Режим движка Tesseract.
     *
     * Значения совпадают с enum'ом Tesseract: 3 — движок по умолчанию,
     * 1 — только LSTM, 2 — LSTM и классический движок вместе.
     *
     * В моделях лежит только LSTM, поэтому работаем в режиме 1: классический
     * движок в Tesseract 5 уже не нужен и лишь замедляет работу.
     */
    private const val ENGINE_MODE = 1

    /** Режим разбора одиночного слова — для второго, латинского чтения. */
    private const val WORD_MODE = 8

    /**
     * Режимы разбора, которые перебираем для фрагмента слова.
     *
     * 8 — одиночное слово, 7 — одиночная строка, 13 — «сырая» строка,
     * 6 — однородный блок текста. Первый, на котором движок что-то нашёл,
     * и используется.
     */
    private val WORD_MODES = listOf(WORD_MODE, 7, 13, 6)

    private var appContext: Context? = null

    /** Вызывается из Application: движку нужен контекст для файлов и assets. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual suspend fun recognizePages(bytes: ByteArray): List<OcrPage> {
        if (bytes.isEmpty()) return emptyList()
        val context = appContext ?: return emptyList()
        val dataPath = prepareTessData(context) ?: return emptyList()

        return withContext(Dispatchers.IO) {
            runCatching {
                val api = createApi(dataPath, languages) ?: return@runCatching emptyList()
                val latinApi = createApi(dataPath, latinLanguages)
                val cyrillicApi = createApi(dataPath, cyrillicLanguages)
                try {
                    api.SetPageSegMode(PSM_AUTO)
                    if (isDebuggable()) {
                        File(context.filesDir, "ocr-words.txt").delete()
                        File(context.filesDir, "ocr-text.txt").delete()
                    }

                    PDDocument.load(bytes.inputStream()).use { document ->
                        val renderer = PDFRenderer(document)
                        val pageCount = minOf(document.numberOfPages, MAX_PAGES)
                        val pages = ArrayList<OcrPage>(pageCount)
                        for (index in 0 until pageCount) {
                            val bitmap = renderer.renderImageWithDPI(index, RENDER_DPI)
                                ?: continue
                            try {
                                Log.d(TAG, "page ${index + 1}: image ${bitmap.width}x${bitmap.height}, ${memory()}")
                                setPageImage(api, bitmap)
                                // Распознаём текст: после этого движок отдаёт
                                // итератор по словам с их рамками.
                                api.GetUTF8Text()
                                val words = collectWords(api)
                                // Подозрительные слова перечитываем отдельно:
                                // латиницей и кириллицей. Так видно, каким
                                // алфавитом слово написано на самом деле.
                                val withScripts = words.map { word ->
                                    if (!OcrScript.isAmbiguous(word.text)) {
                                        word
                                    } else {
                                        val latin = readFragment(latinApi, bitmap, word, "латиница")
                                        val cyrillic = readFragment(cyrillicApi, bitmap, word, "кириллица")
                                        word.copy(
                                            latinText = latin?.first,
                                            latinConfidence = latin?.second ?: 0f,
                                            cyrillicText = cyrillic?.first,
                                            cyrillicConfidence = cyrillic?.second ?: 0f,
                                        )
                                    }
                                }
                                Log.d(TAG, "page ${index + 1}: ${withScripts.size} words")
                                dumpText(index + 1, api.GetUTF8Text()?.getString() ?: "")
                                dumpWords(index + 1, withScripts)
                                pages += OcrPage(
                                    width = bitmap.width,
                                    height = bitmap.height,
                                    words = withScripts,
                                )
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        pages
                    }
                } finally {
                    api.End()
                    latinApi?.End()
                    cyrillicApi?.End()
                }
            }.getOrElse { error ->
                Log.w(TAG, "OCR failed", error)
                emptyList()
            }.also { pages ->
                if (isDebuggable() && pages.isNotEmpty()) {
                    dumpText(pages)
                }
            }
        }
    }

    /**
     * Создаёт и инициализирует движок распознавания.
     *
     * @return готовый движок или null, если модель не подошла.
     */
    private fun createApi(dataPath: String, langs: List<String>): TessBaseAPI? {
        val api = TessBaseAPI()
        // Tesseract возвращает из Init 0 при успехе и -1 при ошибке —
        // наоборот, чем у большинства функций. Поэтому проверяем на ноль.
        if (api.Init(dataPath, langs.joinToString("+"), ENGINE_MODE) == 0) {
            Log.d(TAG, "Tesseract initialized, mode=$ENGINE_MODE, lang=${langs.joinToString("+")}")
            return api
        }
        api.End()
        Log.w(TAG, "Tesseract init failed, dataPath=$dataPath, lang=${langs.joinToString("+")}")
        return null
    }

    /**
     * Копирует языковые модели из assets в файловую систему приложения.
     *
     * @return путь к каталогу с моделями (с завершающим разделителем) или null,
     *         если копирование не вышло.
     */
    private fun prepareTessData(context: Context): String? = runCatching {
        val dir = File(context.filesDir, "tessdata")
        if (!dir.exists()) dir.mkdirs()
        for (language in languages) {
            val target = File(dir, "$language.traineddata")
            if (!target.exists() || target.length() == 0L) {
                context.assets.open("tessdata/$language.traineddata").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        dir.absolutePath + File.separator
    }.getOrNull()

    /**
     * Собирает слова страницы с их рамками и уверенностью распознавания.
     */
    private fun collectWords(api: TessBaseAPI): List<OcrWord> {
        val iterator: ResultIterator = api.GetIterator() ?: return emptyList()
        val words = mutableListOf<OcrWord>()
        val left = IntArray(1)
        val top = IntArray(1)
        val right = IntArray(1)
        val bottom = IntArray(1)
        try {
            iterator.Begin()
            while (iterator.Next(RIL_WORD)) {
                val text = iterator.GetUTF8Text(RIL_WORD)?.getString()?.trim()
                if (text.isNullOrEmpty()) continue
                if (!iterator.BoundingBox(RIL_WORD, left, top, right, bottom)) continue
                words += OcrWord(
                    text = text,
                    left = left[0],
                    top = top[0],
                    right = right[0],
                    bottom = bottom[0],
                    confidence = iterator.Confidence(RIL_WORD),
                )
            }
        } finally {
            iterator.deallocate()
        }
        return words
    }

    /**
     * Отдаёт движоку картинку страницы.
     *
     * Пиксели копируются напрямую в буфер: сжатие в PNG ради одного листа
     * дороже, чем само распознавание, а движку всё равно нужен серый образ.
     */
    private fun setPageImage(api: TessBaseAPI, bitmap: Bitmap) {
        val image = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val buffer = ByteBuffer.allocateDirect(image.byteCount)
        image.copyPixelsToBuffer(buffer)
        buffer.rewind()
        api.SetImage(buffer, image.width, image.height, 4, image.width * 4)
    }

    /**
     * Перечитывает фрагмент слова одной моделью.
     *
     * Слово вырезается из картинки страницы и распознаётся отдельно: тогда
     * движок выбирает алфавит по самому фрагменту, а не по всей странице,
     * где кириллицы больше.
     *
     * @return прочтение и уверенность в нём.
     */
    private fun readFragment(
        api: TessBaseAPI?,
        bitmap: Bitmap,
        word: OcrWord,
        script: String,
    ): Pair<String, Float>? {
        if (api == null) {
            debug("$script '${word.text}': нет движка")
            return null
        }
        val margin = 2
        val left = (word.left - margin).coerceAtLeast(0)
        val top = (word.top - margin).coerceAtLeast(0)
        val right = (word.right + margin).coerceAtMost(bitmap.width)
        val bottom = (word.bottom + margin).coerceAtMost(bitmap.height)
        if (right - left < 3 || bottom - top < 3) {
            debug("$script '${word.text}': пустой фрагмент")
            return null
        }

        val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val image = if (crop.config == Bitmap.Config.ARGB_8888) {
            crop
        } else {
            crop.copy(Bitmap.Config.ARGB_8888, false)
        }
        try {
            val buffer = ByteBuffer.allocateDirect(image.byteCount)
            image.copyPixelsToBuffer(buffer)
            buffer.rewind()
            api.SetImage(buffer, image.width, image.height, 4, image.width * 4)

            for (mode in WORD_MODES) {
                api.SetPageSegMode(mode)
                val recognized = api.GetUTF8Text()?.getString()?.trim().orEmpty()
                if (recognized.isEmpty()) continue
                // Уверенность страницы для фрагмента из одного слова — это
                // уверенность самого слова. Итератор на фрагменте пуст:
                // разбор одиночного слова не строит структуру строк.
                val confidence = api.MeanTextConf().toFloat()
                debug("$script '${word.text}' -> '$recognized' ($confidence) режим $mode")
                return recognized to confidence
            }
            debug("$script '${word.text}': ни один режим не дал текста (${image.width}x${image.height})")
            return null
        } finally {
            if (image !== crop) image.recycle()
            crop.recycle()
        }
    }

    /** Диагностика в журнал — только в отладочной сборке. */
    private fun debug(message: String) {
        if (isDebuggable()) Log.d(TAG, message)
    }

    /**
     * Печатает распознанный текст в logcat — только в debug-сборке.
     *
     * Это диагностика: по ней видно, что именно распознал движок, и можно
     * проверить разбор без телефона. В обычной сборке текст выписки в журнал
     * не попадает.
     */
    private fun dumpText(pageIndex: Int, text: String) {
        if (!isDebuggable()) return
        var chunk = StringBuilder()
        for (line in text.split('\n')) {
            if (chunk.length + line.length > 1500) {
                Log.d(TAG, "OCR-TEXT[$pageIndex] $chunk")
                chunk = StringBuilder()
            }
            chunk.append(line).append(" ⏎ ")
        }
        if (chunk.isNotEmpty()) Log.d(TAG, "OCR-TEXT[$pageIndex] $chunk")
    }

    /**
     * Сохраняет распознанные слова с рамками в файл приложения — только в
     * отладочной сборке.
     *
     * Это диагностика: по файлу видно, что именно распознал движок и как
     * из слов собираются строки. В обычной сборке файл не создаётся.
     */
    private fun dumpWords(pageIndex: Int, words: List<OcrWord>) {
        if (!isDebuggable()) return
        runCatching {
            val file = File(appContext!!.filesDir, "ocr-words.txt")
            file.appendText(
                words.joinToString("\n") { word ->
                    OcrWordDump.format(pageIndex, word)
                } + "\n",
            )
        }
    }

    /** Отладочная ли сборка? В ней журналируем распознанный текст. */
    private fun isDebuggable(): Boolean {
        val flags = appContext?.applicationInfo?.flags ?: return false
        return flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    /**
     * Сохраняет собранный из слов текст — только в отладочной сборке.
     *
     * По нему видно, как строки таблицы легли рядом друг с другом: именно
     * этот текст потом разбирает парсер выписки.
     */
    private fun dumpText(pages: List<OcrPage>) {
        runCatching {
            val file = File(appContext!!.filesDir, "ocr-text.txt")
            file.writeText(OcrScript.toText(pages))
        }
    }

    /** Свободная память устройства — помогает понять, хватило ли её движку. */
    private fun memory(): String {
        val manager = appContext?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return "memory: ?"
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return "free=${info.availMem / 1024 / 1024} MB"
    }
}
