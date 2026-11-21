package store.chekak.balik.parser.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Проверки устранения омоглифов.
 *
 * Кириллические буквы неотличимы от латинских, поэтому слово «PYATEROCHKA»
 * движок читает как «РУАТЕВОСНКА», а «ТОО» — как «TOO». Оба варианта
 * движок отдаёт двумя прочтениями — латиницей и кириллицей, — а какое из
 * них верное, решает окружение слова в строке операции.
 */
class OcrScriptTest {

    @Test
    fun wordOfLookalikeLettersIsAmbiguous() {
        assertTrue(OcrScript.isAmbiguous("РУАТЕВОСНКА"))
        assertTrue(OcrScript.isAmbiguous("СН77031"))
        assertTrue(OcrScript.isAmbiguous("МОСКВА"))
        assertTrue(OcrScript.isAmbiguous("Отмена"))
    }

    @Test
    fun latinWordOfLookalikeLettersIsAmbiguousToo() {
        // Начертание у «PYATEROCHKA» и «РУАТЕВОСНКА» одно — значит, слово
        // омоглифно, какой бы алфавит движок ни выбрал.
        assertTrue(OcrScript.isAmbiguous("PYATEROCHKA"))
        assertTrue(OcrScript.isAmbiguous("CH77031"))
    }

    @Test
    fun wordWithOtherLettersIsNotAmbiguous() {
        assertFalse(OcrScript.isAmbiguous("MOSCOW"))
        assertFalse(OcrScript.isAmbiguous("МОРЕМАНИЯ"))
        assertFalse(OcrScript.isAmbiguous("Mobizap.ru"))
    }

    @Test
    fun singleLetterIsNotAmbiguous() {
        // «Р» — это знак рубля, а «в» и «с» — предлоги: переписывать их нельзя.
        assertFalse(OcrScript.isAmbiguous("Р"))
        assertFalse(OcrScript.isAmbiguous("в"))
        assertFalse(OcrScript.isAmbiguous("с"))
    }

    @Test
    fun twoLetterWordIsAmbiguous() {
        // «РР» в названии компании — омоглиф, и окружение строки скажет, какой
        // там алфавит.
        assertTrue(OcrScript.isAmbiguous("РР"))
        assertTrue(OcrScript.isAmbiguous("PP"))
    }

    @Test
    fun latinReadingReplacesCyrillicInLatinPart() {
        val text = OcrScript.toText(listOf(page(operationWords())))

        assertTrue(text.contains("PYATEROCHKA"), "ожидалось латинское чтение: $text")
        assertFalse(text.contains("РУАТЕВОСНКА"), "кириллическое чтение должно уйти: $text")
    }

    @Test
    fun cyrillicRowKeepsCyrillicReading() {
        val words = operationWords().map { word ->
            when (word.text) {
                "MOSCOW" -> word.copy(text = "МОСКВА")
                "RUS" -> word.copy(text = "РОССИЯ")
                else -> word
            }
        }
        val text = OcrScript.toText(listOf(page(words)))

        assertTrue(text.contains("РУАТЕВОСНКА"), "кириллица должна остаться: $text")
        assertFalse(text.contains("PYATEROCHKA"), "латиница не должна появляться: $text")
    }

    /**
     * Главный случай: кириллическое слово в строке, где есть латиница.
     *
     * Раньше правило смотрело только на всю строку: если в строке есть
     * латинское слово, латинское чтение подставлялось во все омоглифы — и
     * «Отмена» превращалась в «OTMeHa». Теперь смотрим на соседей: сразу
     * после «Отмена» идёт кириллица, значит и слово кириллическое.
     */
    @Test
    fun cyrillicWordBeforeCyrillicNeighbourKeepsCyrillicReading() {
        val words = listOf(
            word("05.06.2026", left = 40, top = 100),
            word("Отмена", left = 520, top = 100, latin = "OTMeHa", latinConfidence = 90f),
            word("операции", left = 620, top = 100),
            word("оплаты", left = 720, top = 100),
            word("DIKSI", left = 820, top = 100),
            word("Podolsk", left = 900, top = 100),
            word("RUS", left = 1000, top = 100),
        )
        val text = OcrScript.toText(listOf(page(words)))

        assertTrue(text.contains("Отмена"), "кириллица должна остаться: $text")
        assertFalse(text.contains("OTMeHa"), "латинское чтение не должно появляться: $text")
    }

    /**
     * Латинское слово в конце латинской части описания.
     *
     * Движок прочитал кириллицей («СНЕ»), а окружение — латинское:
     * рядом с «Kowloon, Hong» стоит «CHE».
     */
    @Test
    fun cyrillicWordBeforeLatinNeighbourTakesLatinReading() {
        val words = listOf(
            word("05.06.2026", left = 40, top = 100),
            word("Оплата", left = 520, top = 100),
            word("в", left = 620, top = 100),
            word("Kowloon,", left = 700, top = 100),
            word("Hong", left = 820, top = 100),
            word("СНЕ", left = 920, top = 100, latin = "CHE", latinConfidence = 96f),
        )
        val text = OcrScript.toText(listOf(page(words)))

        assertTrue(text.contains("CHE"), "ожидалось латинское чтение: $text")
        assertFalse(text.contains("СНЕ"), "кириллическое чтение должно уйти: $text")
    }

    /**
     * Обратный случай: движок прочитал кириллицу латиницей.
     *
     * «ТОО» и «TOO» — одно начертание, но в выписке «ТОО» кириллическое:
     * так пишут организацию в России. Латинское прочтение тут не поможет,
     * поэтому и оно берётся у движка.
     */
    @Test
    fun cyrillicReadingReplacesLatinInCyrillicPart() {
        val words = listOf(
            word("05.06.2026", left = 40, top = 100),
            word("Оплата", left = 520, top = 100),
            word("в", left = 620, top = 100),
            word("TOO", left = 700, top = 100, cyrillic = "ТОО", cyrillicConfidence = 93f),
            word("ОнлиПэй", left = 800, top = 100),
        )
        val text = OcrScript.toText(listOf(page(words)))

        assertTrue(text.contains("ТОО"), "ожидалось кириллическое чтение: $text")
        assertFalse(text.contains("TOO"), "латинское чтение должно уйти: $text")
    }

    /**
     * Латинское прочтение без букв — это не латинское слово.
     *
     * Так выглядит «ООО», прочитанное цифрами: движок не увидел букв, и
     * подставлять такое прочтение нельзя.
     */
    @Test
    fun latinReadingWithoutLettersIsNotUsed() {
        val words = listOf(
            word("05.06.2026", left = 40, top = 100),
            word("Оплата", left = 520, top = 100),
            word("в", left = 620, top = 100),
            word("ООО", left = 700, top = 100, latin = "000", latinConfidence = 81f),
            word("IKORNYY", left = 800, top = 100),
            word("Moskva", left = 900, top = 100),
            word("RUS", left = 1000, top = 100),
        )
        val text = OcrScript.toText(listOf(page(words)))

        assertTrue(text.contains("ООО"), "настоящее кириллическое слово должно остаться: $text")
        assertFalse(text.contains("000"), "прочтение без букв не применяется: $text")
    }

    @Test
    fun unsureReadingIsNotUsed() {
        // Уверенность ниже порога — значит, движок в этом чтении не уверен.
        val words = operationWords().map { word ->
            when (word.text) {
                "РУАТЕВОСНКА" -> word.copy(latinConfidence = 10f)
                else -> word
            }
        }
        val text = OcrScript.toText(listOf(page(words)))

        assertTrue(text.contains("РУАТЕВОСНКА"), "неуверенное чтение не применяется: $text")
    }

    @Test
    fun worseReadingIsNotUsed() {
        // Кириллическое прочтение хуже исходного — оставляем исходное.
        val words = operationWords().map { word ->
            when (word.text) {
                "РУАТЕВОСНКА" -> word.copy(
                    cyrillicText = "РУАТЕВОСНКАХ",
                    cyrillicConfidence = 20f,
                )
                "MOSCOW" -> word.copy(text = "МОСКВА")
                "RUS" -> word.copy(text = "РОССИЯ")
                else -> word
            }
        }
        val text = OcrScript.toText(listOf(page(words)))

        assertTrue(text.contains("РУАТЕВОСНКА"), "худшее чтение не применяется: $text")
        assertFalse(text.contains("РУАТЕВОСНКАХ"), "худшее чтение не применяется: $text")
    }

    /** Слова одной операции: строка с датой и суммами, ниже — время и хвост описания. */
    private fun operationWords(): List<OcrWord> = listOf(
        word("05.06.2026", left = 40, top = 100),
        word("05.06.2026", left = 200, top = 100),
        word("-3 461.34", left = 360, top = 100),
        word("Р", left = 460, top = 100),
        word("Оплата", left = 520, top = 100),
        word("в", left = 600, top = 100),
        word(
            "РУАТЕВОСНКА",
            left = 640,
            top = 100,
            latin = "PYATEROCHKA",
            latinConfidence = 91f,
            cyrillic = "РУАТЕВОСНКА",
            cyrillicConfidence = 90f,
        ),
        word("6915", left = 900, top = 100),
        word("19:47", left = 40, top = 130),
        word("MOSCOW", left = 520, top = 130),
        word("RUS", left = 640, top = 130),
    )

    private fun page(words: List<OcrWord>): OcrPage = OcrPage(1000, 400, words)

    private fun word(
        text: String,
        left: Int,
        top: Int,
        latin: String? = null,
        latinConfidence: Float = 0f,
        cyrillic: String? = null,
        cyrillicConfidence: Float = 0f,
    ): OcrWord = OcrWord(
        text = text,
        left = left,
        top = top,
        right = left + text.length * 8,
        bottom = top + 20,
        confidence = 90f,
        latinText = latin,
        latinConfidence = latinConfidence,
        cyrillicText = cyrillic,
        cyrillicConfidence = cyrillicConfidence,
    )

    @Test
    fun dumpRoundTripKeepsBothReadings() {
        val word = OcrWord(
            text = "РУАТЕВОСНКА",
            left = 10,
            top = 20,
            right = 30,
            bottom = 40,
            confidence = 90.5f,
            latinText = "PYATEROCHKA",
            latinConfidence = 91f,
            cyrillicText = "РУАТЕВОСНКА",
            cyrillicConfidence = 60f,
        )

        val parsed = OcrWordDump.parse(OcrWordDump.format(3, word))

        assertEquals(3, parsed?.first)
        assertEquals(word, parsed?.second)
    }
}
