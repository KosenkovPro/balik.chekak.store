package store.chekak.balik.parser.pdf

/**
 * Устранение омоглифов в описаниях операций.
 *
 * Кириллические буквы АВЕКМНОРСТУХ неотличимы от латинских, поэтому движок,
 * читая страницу целиком, выбирает алфавит по контексту: название магазина
 * «PYATEROCHKA» превращается в «РУАТЕВОСНКА», а «ТОО» — в «TOO».
 *
 * Переписывать буквы по таблице нельзя: «МОСКВА» — настоящее кириллическое
 * слово, и такая замена испортила бы описание. Поэтому оба прочтения берём
 * у самого движка: тот же фрагмент перечитывается отдельно моделью с
 * латиницей ([OcrWord.latinText]) и отдельно моделью с кириллицей
 * ([OcrWord.cyrillicText]).
 *
 * Какой из двух вариантов верный, решает окружение слова. В описании
 * операции кириллическая часть («Оплата в», «Возврат средств») стоит в
 * начале, а латинская (название магазина, город, страна) — в конце. Ближайшее
 * слово, у которого алфавит известен однозначно, и говорит, в какой части
 * строки находится омоглиф:
 *
 *  * «Отмена операции оплаты DIKSI Podolsk RUS» — после «Отмена» идёт
 *    кириллица, значит слово кириллическое;
 *  * «Оплата в РУАТЕВОСНКА MOSCOW RUS» — после «РУАТЕВОСНКА» идёт латиница,
 *    значит слово латинское.
 */
object OcrScript {

    /**
     * Кириллические буквы, неотличимые от латинских.
     */
    private val CYRILLIC_LOOKALIKE = setOf(
        'А', 'В', 'Е', 'К', 'М', 'Н', 'О', 'Р', 'С', 'Т', 'У', 'Х',
        'а', 'в', 'е', 'к', 'м', 'н', 'о', 'р', 'с', 'т', 'у', 'х',
    )

    /**
     * Латинские буквы, у которых есть кириллический двойник.
     *
     * Учитываются вместе с [CYRILLIC_LOOKALIKE]: «PYATEROCHKA» и
     * «РУАТЕВОСНКА» — одно и то же начертание, поэтому слово омоглифно,
     * какой бы алфавит движок ни выбрал.
     */
    private val LATIN_LOOKALIKE = setOf(
        'A', 'B', 'C', 'E', 'H', 'K', 'M', 'O', 'P', 'R', 'T', 'X', 'Y',
        'a', 'b', 'c', 'e', 'h', 'k', 'm', 'o', 'p', 'r', 't', 'x', 'y',
    )

    /**
     * Буквы кириллицы, которых нет в латинице.
     *
     * Слово с такой буквой однозначно кириллическое.
     */
    private val CYRILLIC_ONLY = setOf(
        'Б', 'Г', 'Д', 'Ж', 'З', 'И', 'Й', 'Л', 'П', 'Ф', 'Ц', 'Ч', 'Ш', 'Щ',
        'Ъ', 'Ы', 'Ь', 'Э', 'Ю', 'Я', 'Ё',
        'б', 'г', 'д', 'ж', 'з', 'и', 'й', 'л', 'п', 'ф', 'ц', 'ч', 'ш', 'щ',
        'ъ', 'ы', 'ь', 'э', 'ю', 'я', 'ё',
    )

    /**
     * Буквы латиницы, которых нет в кириллице.
     *
     * Слово с такой буквой однозначно латинское.
     */
    private val LATIN_ONLY = setOf(
        'D', 'F', 'G', 'I', 'J', 'L', 'N', 'Q', 'R', 'S', 'U', 'V', 'W', 'Z',
        'd', 'f', 'g', 'i', 'j', 'l', 'n', 'q', 'r', 's', 'u', 'v', 'w', 'z',
    )

    /**
     * Минимальная уверенность, при которой прочтению можно верить, %.
     *
     * Порог отделяет уверенное прочтение от случайного: модель с латиницей
     * читает «Отмена» как «OTMeHa» и без уверенности, а «PYATEROCHKA» —
     * уверенно.
     */
    const val MIN_CONFIDENCE = 40f

    /**
     * Минимальное число букв, для которого омоглифы разбираются.
     *
     * Одна буква — это знак рубля или предлог («в», «с»), переписывать их
     * нельзя. Двухбуквенные слова («РР» в названии компании) разбирать
     * можно: окружение строки надёжно говорит, какой там алфавит.
     */
    private const val MIN_AMBIGUOUS_LENGTH = 2

    /** Начало строки операции — дата. */
    private val DATE_START = Regex("""^\d{2}\.\d{2}\.\d{4}""")

    /** Текст распознанных страниц с устранёнными омоглифами. */
    fun toText(pages: List<OcrPage>): String = OcrLayout.toText(disambiguate(pages))

    /**
     * Можно ли слово прочитать и латиницей, и кириллицей.
     *
     * Проверяются обе стороны: важно не только то, что движок прочитал слово
     * кириллицей («РУАТЕВОСНКА»), но и то, что оно могло быть прочитано
     * латиницей («PYATEROCHKA»): начертание у них одно.
     */
    fun isAmbiguous(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.length < MIN_AMBIGUOUS_LENGTH) return false
        return letters.all { it in CYRILLIC_LOOKALIKE || it in LATIN_LOOKALIKE }
    }

    /**
     * Подставляет то прочтение, которое согласуется с окружением строки.
     *
     * Движок выбирает алфавит по всей странице и часто ошибается: кириллицы
     * в выписке больше, поэтому латинское название магазина он читает
     * кириллицей («РУАТЕВОСНКА»), а русское слово — латиницей («TOO»).
     *
     * Страничное прочтение движок уже сделал, и оно лучше фрагментного:
     * фрагмент читается без окружения. Поэтому правило не заменяет слово
     * само по себе — только если движок выбрал не тот алфавит, который
     * показывает окружение строки.
     */
    fun disambiguate(pages: List<OcrPage>): List<OcrPage> = pages.map { page ->
        val rows = operationRows(page)
        if (rows.isEmpty()) return@map page

        val replacements = HashMap<OcrWord, String>()
        for (words in rows) {
            for ((index, word) in words.withIndex()) {
                if (!isAmbiguous(word.text)) continue
                val zone = zoneOf(words, index) ?: continue
                // Движок выбрал тот же алфавит, что и окружение, — менять нечего.
                if (writtenScript(word.text) == zone) continue
                val replacement = when (zone) {
                    Script.LATIN -> word.latinText
                        ?.takeIf { it.any { it.isLetter() } }
                        ?.takeIf { word.latinConfidence >= MIN_CONFIDENCE }
                    Script.CYRILLIC -> word.cyrillicText
                        ?.takeIf { it.any { it.isLetter() } }
                        ?.takeIf { word.cyrillicConfidence >= MIN_CONFIDENCE }
                    else -> null
                }
                if (replacement != null && replacement != word.text) {
                    replacements[word] = replacement
                }
            }
        }
        if (replacements.isEmpty()) return@map page

        page.copy(
            words = page.words.map { word ->
                replacements[word]?.let { word.copy(text = it) } ?: word
            },
        )
    }

    /**
     * Группирует строки страницы по операциям.
     *
     * Операция начинается со строки с датой; дальше идут строки-продолжения
     * (время, хвост описания). Так же строка собирается в парсере выписки.
     */
    private fun operationRows(page: OcrPage): List<List<OcrWord>> {
        val rows = mutableListOf<List<OcrWord>>()
        var current: MutableList<OcrWord>? = null
        for (group in OcrLayout.toLineGroups(page)) {
            val line = group.sortedBy { it.left }
            val first = line.firstOrNull()?.text.orEmpty()
            if (DATE_START.containsMatchIn(first)) {
                current = mutableListOf()
                current.addAll(line)
                rows.add(current)
            } else if (current != null) {
                // Служебные строки (подвал страницы) к операции не относятся:
                // их слова тянулись бы в описание и сбивали выбор алфавита у
                // названия магазина.
                if (!PageFurniture.isFurniture(OcrLayout.joinWords(line))) {
                    current.addAll(line)
                }
            }
        }
        return rows
    }

    /** Алфавит слова. */
    private enum class Script { CYRILLIC, LATIN, MIXED, NONE }

    /**
     * Какой алфавит у окружения слова.
     *
     * Смотрим на ближайшее справа слово с однозначным алфавитом: в описании
     * операции латинская часть стоит в конце, поэтому следующее слово обычно
     * из той же части, что и омоглиф. Слова из цифр и знаков препинания
     * алфавита не дают — их пропускаем. Если справа ничего подходящего нет,
     * берём ближайшее слева.
     */
    private fun zoneOf(words: List<OcrWord>, index: Int): Script? {
        for (i in index + 1 until words.size) {
            val script = visibleScript(words[i].text) ?: continue
            return script
        }
        for (i in index - 1 downTo 0) {
            val script = visibleScript(words[i].text) ?: continue
            return script
        }
        return null
    }

    /**
     * Алфавит, которым слово набрано в распознавании.
     *
     * Отличается от [visibleScript]: там важно, какие буквы видно, здесь —
     * какие знаки выдал движок. «РУАТЕВОСНКА» набрана кириллицей, но выглядит
     * как латинское слово.
     */
    private fun writtenScript(text: String): Script {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return Script.NONE
        val cyrillic = letters.any { it.code in CYRILLIC_BLOCK }
        val latin = letters.any { it.code in LATIN_BLOCK }
        return when {
            cyrillic && latin -> Script.MIXED
            cyrillic -> Script.CYRILLIC
            latin -> Script.LATIN
            else -> Script.NONE
        }
    }

    /**
     * Алфавит, который видно по буквам.
     *
     * Слово, у которого нет ни одной буквы без двойника, может быть и
     * русским, и латинским: такие слова алфавита не дают.
     */
    private fun visibleScript(text: String): Script? {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return null
        if (letters.any { it in CYRILLIC_ONLY }) return Script.CYRILLIC
        if (letters.any { it in LATIN_ONLY }) return Script.LATIN
        return null
    }

    /** Диапазон кодов кириллических букв. */
    private val CYRILLIC_BLOCK = 0x0400..0x04FF

    /** Диапазон кодов латинских букв. */
    private val LATIN_BLOCK = 0x0041..0x007A
}
