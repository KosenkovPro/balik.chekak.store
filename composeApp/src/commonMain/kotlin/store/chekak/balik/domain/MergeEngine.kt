package store.chekak.balik.domain

import store.chekak.balik.core.DateUtils
import store.chekak.balik.data.db.TransactionEntity
import store.chekak.balik.parser.ParsedOperation
import kotlin.math.abs

/**
 * Что делать с операцией из нового файла при слиянии с уже импортированными.
 */
enum class MergeAction {
    /** Операции ещё нет в базе — добавляем. */
    ADD,

    /** Операция уже есть, но банк изменил детали — обновляем. */
    UPDATE,

    /** Операция уже есть и не изменилась — пропускаем. */
    SKIP,
}

/** Почему выбрано именно это действие — показывается в предпросмотре импорта. */
enum class MergeReason {
    /** В базе такой операции нет. */
    NEW_OPERATION,

    /** Совпал отпечаток операции (банк, дата, сумма, валюта, назначение). */
    EXACT_DUPLICATE,

    /** Совпал банковский номер документа: та же операция, но детали могли измениться. */
    SAME_DOCUMENT,

    /** Совпали сумма, время и назначение платежа. */
    SAME_OPERATION,
}

/** Решение по одной операции нового файла. */
data class MergeDecision(
    val action: MergeAction,
    val reason: MergeReason,
    /** Идентификатор уже сохранённой операции — для UPDATE. */
    val existingTransactionId: Long? = null,
)

/** Операция нового файла вместе с её отпечатком. */
data class MergeCandidate(
    val operation: ParsedOperation,
    val fingerprint: String,
)

/** Операция и принятое по ней решение. */
data class MergeResult(
    val candidate: MergeCandidate,
    val decision: MergeDecision,
)

/**
 * Слияние новой выписки с уже импортированными операциями.
 *
 * Выписки одного банка за разные периоды пересекаются: в новом файле часть операций
 * уже есть, часть новых, а часть могла прийти с изменёнными деталями. Разбор ведётся
 * в три уровня — от самого надёжного признака к самому слабому:
 *
 *  1. **Номер документа** — банковский идентификатор операции (в CSV это колонка
 *     «Номер документа»). Самый надёжный признак: он не меняется между выгрузками
 *     даже если текст назначения изменился.
 *  2. **Отпечаток** — банк, дата, сумма, валюта и назначение платежа. Совпадение
 *     означает, что это буквально та же операция.
 *  3. **Сумма + время + назначение** — нечёткое сопоставление на случай, если банк
 *     поправил формулировку или время. Работает только в пределах одного счёта,
 *     с жёстким допуском по времени и порогом сходства текста.
 *
 * Более слабый уровень никогда не «съедает» лишнего: одна уже сохранённая операция
 * сопоставляется не более чем с одной новой, поэтому две одинаковые покупки в один
 * день не склеятся в одну. Если сомнение остаётся — операция считается новой.
 */
class MergeEngine(
    /** Допустимое расхождение времени при нечётком сопоставлении. */
    private val timeToleranceMillis: Long = 120_000L,
    /** Минимальное сходство назначений платежа, чтобы считать операции одинаковыми. */
    private val similarityThreshold: Double = 0.5,
) {

    /**
     * Разбирает операции нового файла по уже сохранённым.
     *
     * @param candidates операции нового файла с посчитанными отпечатками
     * @param existing операции того же счёта, уже лежащие в базе
     */
    fun plan(
        candidates: List<MergeCandidate>,
        existing: List<TransactionEntity>,
    ): List<MergeResult> {
        val byFingerprint = HashMap<String, TransactionEntity>(existing.size)
        val byDocument = HashMap<String, TransactionEntity>(existing.size)
        val byDayAndAmount = HashMap<DayAmountKey, MutableList<TransactionEntity>>()

        for (transaction in existing) {
            byFingerprint[transaction.fingerprint] = transaction
            transaction.documentId?.trim()?.takeIf { it.isNotEmpty() }?.let { byDocument[it] = transaction }
            val key = DayAmountKey.of(transaction.timestamp, transaction.amountMinor, transaction.currency)
            byDayAndAmount.getOrPut(key) { mutableListOf() }.add(transaction)
        }

        val claimed = HashSet<Long>()
        val seenFingerprints = HashSet<String>()
        val seenDocuments = HashSet<String>()
        val results = ArrayList<MergeResult>(candidates.size)

        for (candidate in candidates) {
            val operation = candidate.operation

            // 1. Такая же операция уже встречалась в этом же файле.
            if (!seenFingerprints.add(candidate.fingerprint)) {
                results += MergeResult(
                    candidate,
                    MergeDecision(MergeAction.SKIP, MergeReason.EXACT_DUPLICATE),
                )
                continue
            }
            val documentId = operation.documentId?.trim()?.takeIf { it.isNotEmpty() }
            if (documentId != null && !seenDocuments.add(documentId)) {
                results += MergeResult(
                    candidate,
                    MergeDecision(MergeAction.SKIP, MergeReason.EXACT_DUPLICATE),
                )
                continue
            }

            // 2. Совпал отпечаток — та же операция, ничего не поменялось.
            val exact = byFingerprint[candidate.fingerprint]
            if (exact != null) {
                claimed.add(exact.id)
                results += MergeResult(
                    candidate,
                    MergeDecision(MergeAction.SKIP, MergeReason.EXACT_DUPLICATE, exact.id),
                )
                continue
            }

            // 3. Совпал номер документа — та же операция, но банк мог поменять детали.
            if (documentId != null) {
                val byDoc = byDocument[documentId]
                if (byDoc != null) {
                    claimed.add(byDoc.id)
                    val changed = detailsDiffer(operation, byDoc)
                    results += MergeResult(
                        candidate,
                        MergeDecision(
                            action = if (changed) MergeAction.UPDATE else MergeAction.SKIP,
                            reason = MergeReason.SAME_DOCUMENT,
                            existingTransactionId = byDoc.id,
                        ),
                    )
                    continue
                }
            }

            // 4. Нечёткое сопоставление: та же сумма примерно в то же время.
            val fuzzy = findByAmountAndTime(operation, byDayAndAmount, claimed)
            if (fuzzy != null && similarity(operation, fuzzy) >= similarityThreshold) {
                claimed.add(fuzzy.id)
                val changed = detailsDiffer(operation, fuzzy)
                results += MergeResult(
                    candidate,
                    MergeDecision(
                        action = if (changed) MergeAction.UPDATE else MergeAction.SKIP,
                        reason = MergeReason.SAME_OPERATION,
                        existingTransactionId = fuzzy.id,
                    ),
                )
                continue
            }

            // 5. Операции у нас ещё не было.
            results += MergeResult(
                candidate,
                MergeDecision(MergeAction.ADD, MergeReason.NEW_OPERATION),
            )
        }

        return results
    }

    /**
     * Ищет уже сохранённую операцию с той же суммой, отстоящую от входящей
     * не дальше [timeToleranceMillis]. Среди кандидатов выбирается ближайший по
     * времени; уже сопоставленные операции повторно не используются.
     */
    private fun findByAmountAndTime(
        operation: ParsedOperation,
        byDayAndAmount: Map<DayAmountKey, MutableList<TransactionEntity>>,
        claimed: Set<Long>,
    ): TransactionEntity? {
        val key = DayAmountKey.of(operation.timestamp, operation.amountMinor, operation.currency)
        var best: TransactionEntity? = null
        var bestDistance = Long.MAX_VALUE

        // Соседние дни нужны для операций, попавших на полночь.
        for (dayOffset in -1..1) {
            val candidates = byDayAndAmount[
                DayAmountKey(key.day + dayOffset, key.amountMinor, key.currency)
            ] ?: continue
            for (candidate in candidates) {
                if (candidate.id in claimed) continue
                val distance = abs(candidate.timestamp - operation.timestamp)
                if (distance > timeToleranceMillis) continue
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = candidate
                }
            }
        }
        return best
    }

    /** Различаются ли банковские детали операции. */
    private fun detailsDiffer(operation: ParsedOperation, existing: TransactionEntity): Boolean {
        if (normalize(operation.rawDescription) != normalize(existing.rawDescription)) return true
        if (normalize(operation.merchantName) != normalize(existing.merchantName)) return true
        val mcc = operation.mcc?.trim()
        if (!mcc.isNullOrEmpty() && mcc != existing.mcc?.trim()) return true
        return false
    }

    /** Сходство назначений платежа по коэффициенту Dice (пересечение мультимножеств слов). */
    private fun similarity(operation: ParsedOperation, existing: TransactionEntity): Double {
        val left = tokens(operation.rawDescription)
        val right = tokens(existing.rawDescription)
        if (left.isEmpty() || right.isEmpty()) return 0.0

        val remaining = right.toMutableList()
        var common = 0
        for (token in left) {
            val index = remaining.indexOf(token)
            if (index >= 0) {
                remaining.removeAt(index)
                common++
            }
        }
        return 2.0 * common / (left.size + right.size)
    }

    private fun normalize(text: String): String =
        text.trim().replace(Regex("""\s+"""), " ").lowercase()

    private fun tokens(text: String): List<String> =
        text.lowercase()
            .split(NON_LETTER)
            .filter { it.isNotEmpty() }

    /** Ключ «день + сумма + валюта» — им bucketing-ятся операции для нечёткого поиска. */
    private data class DayAmountKey(
        val day: Int,
        val amountMinor: Long,
        val currency: String,
    ) {
        companion object {
            fun of(timestamp: Long, amountMinor: Long, currency: String): DayAmountKey =
                DayAmountKey(
                    day = DateUtils.toLocalDate(timestamp).toEpochDays(),
                    amountMinor = amountMinor,
                    currency = currency,
                )
        }
    }

    private companion object {
        val NON_LETTER = Regex("""[^\p{L}\p{N}]+""")
    }
}
