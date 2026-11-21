package store.chekak.balik.parser.pdf

import kotlin.math.abs

/**
 * Сборка распознанных слов обратно в строки текста.
 *
 * Это аналог `PDFTextStripper.sortByPosition` для OCR: движок отдаёт слова
 * с рамками, а парсеру выписки нужен текст, разбитый на строки, — как если бы
 * страница имела текстовый слой. Тогда один и тот же разбор (`PdfStatementParser`)
 * работает и для обычного PDF, и для скана.
 */
object OcrLayout {

    /**
     * Разделитель страниц в тексте, собранном из распознанных слов.
     *
     * Нужен, чтобы операция не склеилась из строк разных страниц: в справке
     * таблица идёт постранично, и хвост описания операции внизу страницы не
     * должен приклеиться к шапке следующей.
     */
    const val PAGE_SEPARATOR = "\u001E"

    /**
     * Собирает слова страницы в строки.
     *
     * Слова одной строки таблицы стоят на общей базовой линии (нижняя граница
     * букв), поэтому группируем их по вертикали, а внутри строки сортируем
     * слева направо.
     *
     * Наклон страницы не учитывается: мы работаем с выгрузками банков, они
     * формируются программно и всегда выровнены, а поправка на наклон давала
     * больше вреда, чем пользы (лишние строки на ровных страницах).
     */
    fun toLines(page: OcrPage): List<String> =
        toLineGroups(page)
            .map { joinLine(it) }
            .filter { it.isNotEmpty() }

    /**
     * Раскладывает слова страницы по строкам — теми же словами, из которых
     * собраны строки, но сгруппированными.
     *
     * Нужно не только для текста: по строкам определяется, к какой операции
     * относится слово (см. [OcrScript]).
     */
    fun toLineGroups(page: OcrPage): List<List<OcrWord>> {
        val words = page.words.filter { it.text.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val tolerance = verticalTolerance(words)
        return clusterByBaseline(words, tolerance)
    }

    /**
     * Текст строки из её слов.
     *
     * Отдельная функция для тех, кто работает со словами строки напрямую:
     * текст собирается ровно так же, как в [toLines].
     */
    fun joinWords(words: List<OcrWord>): String = joinLine(words)

    /** Текст всех страниц — строки страниц подряд, страницы разделены знаком. */
    fun toText(pages: List<OcrPage>): String =
        pages.joinToString(PAGE_SEPARATOR) { page ->
            toLines(page).joinToString("\n")
        }

    /**
     * Раскладывает слова по строкам.
     *
     * Ключ строки — положение базовой линии: у слов одной строки оно отличается
     * на пару пикселей, а у соседних строк — на десятки.
     */
    private fun clusterByBaseline(words: List<OcrWord>, tolerance: Int): List<List<OcrWord>> {
        val clusters = mutableListOf<MutableList<OcrWord>>()
        val baselines = mutableListOf<Double>()

        for (word in words.sortedBy { baselineOf(it) }) {
            val baseline = baselineOf(word)
            var target = -1
            var bestDelta = Double.MAX_VALUE
            for (index in baselines.indices) {
                val delta = abs(baseline - baselines[index])
                if (delta <= tolerance && delta < bestDelta) {
                    bestDelta = delta
                    target = index
                }
            }
            if (target < 0) {
                baselines += baseline
                clusters += mutableListOf(word)
            } else {
                clusters[target].add(word)
            }
        }
        return clusters
    }

    /** Положение слова по вертикали — середина его рамки. */
    private fun baselineOf(word: OcrWord): Double = (word.top + word.bottom) / 2.0

    /**
     * Склеивает слова строки слева направо.
     *
     * Между словами ставим пробел, а при большом горизонтальном разрыве —
     * несколько пробелов: так колонки таблицы остаются различимыми.
     *
     * Пробел ставим не всегда: движок иногда делит одно слово на два
     * («Mos.» и «Transport»), и пробел между ними портил описание. Настоящий
     * пробел в строке заметно шире: его ширина пропорциональна кеглю, поэтому
     * порог считаем от высоты строки.
     */
    private fun joinLine(words: List<OcrWord>): String {
        val sorted = words.sortedBy { it.left }
        val spaceGap = spaceGapOf(sorted)
        val builder = StringBuilder()
        var previous: OcrWord? = null
        var previousRight: Int? = null
        for (word in sorted) {
            val gap = previousRight?.let { word.left - it } ?: 0
            if (builder.isNotEmpty()) {
                val spaces = when {
                    gap > BIG_GAP -> BIG_GAP_SPACES
                    // Движок разорвал один числовой токен на два слова с
                    // перекрытыми рамками: «220220******» и «3715». Пробела
                    // между ними быть не должно.
                    gap <= 0 && continuesNumber(previous, word) -> 0
                    // Рамки перекрываются: движок ошибся с границей слова,
                    // склеивать нельзя — иначе слова слипнутся.
                    gap <= 0 -> 1
                    gap > spaceGap -> 1
                    else -> 0
                }
                builder.append(" ".repeat(spaces))
            }
            builder.append(word.text)
            previous = word
            previousRight = maxOf(previousRight ?: word.right, word.right)
        }
        return builder.toString().trim()
    }

    /**
     * Продолжение ли это одного числового токена.
     *
     * Номер карты «220220******3715» движок иногда отдаёт двумя словами с
     * перекрытыми рамками: между ними пробела нет.
     */
    private fun continuesNumber(previous: OcrWord?, current: OcrWord): Boolean {
        val last = previous?.text?.lastOrNull() ?: return false
        val first = current.text.firstOrNull() ?: return false
        return (last.isDigit() || last == '*') && first.isDigit()
    }

    /**
     * Зазор, за который слова считаются разными.
     *
     * Зазор внутри слова — это кернинг, единицы пикселей. Пробел между
     * словами — примерно четверть кегля, то есть заметно больше. Порог
     * считаем от медианной высоты слов строки, чтобы правило не зависело от
     * разрешения рендера.
     */
    private fun spaceGapOf(words: List<OcrWord>): Int {
        val heights = words.map { (it.bottom - it.top).coerceAtLeast(1) }.sorted()
        val median = heights[heights.size / 2]
        return maxOf(MIN_SPACE_GAP, (median * SPACE_GAP_RATIO).toInt())
    }

    /**
     * Допуск по вертикали для объединения слов в строку.
     *
     * Берём половину от медианной высоты слова: у строки таблицы все слова
     * одной строки имеют близкий кегль, а разные строки отстоят друг от друга
     * заметно дальше.
     */
    private fun verticalTolerance(words: List<OcrWord>): Int {
        val heights = words.map { (it.bottom - it.top).coerceAtLeast(1) }.sorted()
        val median = heights[heights.size / 2]
        return maxOf(MIN_TOLERANCE, median / 2)
    }

    /** Расстояние между словами, за которым начинается новая колонка. */
    private const val BIG_GAP = 40

    /** Пробелов между колонками таблицы. */
    private const val BIG_GAP_SPACES = 3

    /** Доля высоты строки, за которой зазор считается пробелом. */
    private const val SPACE_GAP_RATIO = 0.12f

    /** Минимальный зазор, за которым слова считаются разными, px. */
    private const val MIN_SPACE_GAP = 3

    /** Минимальный допуск по вертикали, px. */
    private const val MIN_TOLERANCE = 4
}
