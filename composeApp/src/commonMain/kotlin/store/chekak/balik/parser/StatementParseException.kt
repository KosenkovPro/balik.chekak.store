package store.chekak.balik.parser

/**
 * Причина, по которой файл выписки не удалось разобрать.
 *
 * Нужна, чтобы интерфейс показал понятный текст вместо технического сообщения
 * об ошибке.
 */
enum class ParseFailureReason {
    /**
     * В PDF нет текстового слоя (это скан), и распознать текст не удалось.
     */
    SCAN_NOT_RECOGNIZED,

    /**
     * Файл разобран, но операций в нём не нашлось.
     */
    NO_OPERATIONS,

    /**
     * В файле-оригинале нет текстового слоя: это скан, а сверять распознанное
     * с распознанным бессмысленно.
     */
    REFERENCE_WITHOUT_TEXT,
}

/**
 * Ошибка разбора выписки с известной причиной.
 *
 * @param reason причина — по ней интерфейс выбирает подсказку для пользователя.
 * @param message техническое описание (в лог и в историю импорта).
 */
class StatementParseException(
    val reason: ParseFailureReason,
    message: String,
) : Exception(message)
