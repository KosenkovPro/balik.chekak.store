package store.chekak.balik.domain

import store.chekak.balik.core.DateUtils
import store.chekak.balik.data.db.TransactionEntity
import store.chekak.balik.parser.MerchantNameExtractor
import store.chekak.balik.parser.ParsedOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BANK = "raiffeisen"

/** Момент времени из выписки — читаемый вид «дд.ММ.гггг чч:мм». */
private fun moment(text: String): Long =
    requireNotNull(DateUtils.parseDateTime(text)) { "Не разобралось время: $text" }

private fun operation(
    timestamp: Long,
    amountMinor: Long,
    description: String,
    currency: String = "RUB",
    documentId: String? = null,
): ParsedOperation = ParsedOperation(
    timestamp = timestamp,
    amountMinor = amountMinor,
    currency = currency,
    rawDescription = description,
    merchantName = MerchantNameExtractor.extract(description),
    documentId = documentId,
)

private fun candidate(operation: ParsedOperation): MergeCandidate = MergeCandidate(
    operation = operation,
    fingerprint = Fingerprint.of(
        bankId = BANK,
        timestamp = operation.timestamp,
        amountMinor = operation.amountMinor,
        currency = operation.currency,
        rawDescription = operation.rawDescription,
    ),
)

private fun stored(
    id: Long,
    timestamp: Long,
    amountMinor: Long,
    description: String,
    currency: String = "RUB",
    documentId: String? = null,
): TransactionEntity = TransactionEntity(
    id = id,
    fingerprint = Fingerprint.of(BANK, timestamp, amountMinor, currency, description),
    accountId = 1L,
    timestamp = timestamp,
    amountMinor = amountMinor,
    currency = currency,
    rawDescription = description,
    merchantName = MerchantNameExtractor.extract(description),
    documentId = documentId,
)

class MergeEngineTest {

    private val engine = MergeEngine()

    // --- Новые операции ---

    @Test
    fun newOperationIsAdded() {
        val op = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN 6915")

        val results = engine.plan(listOf(candidate(op)), emptyList())

        assertEquals(1, results.size)
        assertEquals(MergeAction.ADD, results[0].decision.action)
        assertEquals(MergeReason.NEW_OPERATION, results[0].decision.reason)
        assertNull(results[0].decision.existingTransactionId)
    }

    // --- Точный дубль ---

    @Test
    fun exactDuplicateIsSkipped() {
        val op = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN 6915")
        val existing = stored(7L, op.timestamp, op.amountMinor, op.rawDescription)

        val results = engine.plan(listOf(candidate(op)), listOf(existing))

        assertEquals(MergeAction.SKIP, results[0].decision.action)
        assertEquals(MergeReason.EXACT_DUPLICATE, results[0].decision.reason)
        assertEquals(7L, results[0].decision.existingTransactionId)
    }

    @Test
    fun duplicateInsideTheSameFileIsSkipped() {
        val op = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN 6915")

        val results = engine.plan(listOf(candidate(op), candidate(op)), emptyList())

        assertEquals(MergeAction.ADD, results[0].decision.action)
        assertEquals(MergeAction.SKIP, results[1].decision.action)
    }

    @Test
    fun sameDocumentInsideTheSameFileIsSkipped() {
        val first = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN 6915", documentId = "974845")
        val second = operation(moment("09.11.2026 20:29"), -35_000, "Оплата в AUCHAN 6915", documentId = "974845")

        val results = engine.plan(listOf(candidate(first), candidate(second)), emptyList())

        assertEquals(MergeAction.ADD, results[0].decision.action)
        assertEquals(MergeAction.SKIP, results[1].decision.action)
    }

    // --- Совпадение по номеру документа ---

    @Test
    fun sameDocumentWithChangedDetailsIsUpdated() {
        val old = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN", documentId = "974845")
        val new = operation(
            moment("09.11.2026 20:28"),
            -35_000,
            "Оплата в AUCHAN MOSCOW RUS",
            documentId = "974845",
        )
        val existing = stored(7L, old.timestamp, old.amountMinor, old.rawDescription, documentId = "974845")

        val results = engine.plan(listOf(candidate(new)), listOf(existing))

        assertEquals(MergeAction.UPDATE, results[0].decision.action)
        assertEquals(MergeReason.SAME_DOCUMENT, results[0].decision.reason)
        assertEquals(7L, results[0].decision.existingTransactionId)
    }

    @Test
    fun sameDocumentWithSameDetailsIsSkipped() {
        val op = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN", documentId = "974845")
        val existing = stored(7L, op.timestamp, op.amountMinor, op.rawDescription, documentId = "974845")

        val results = engine.plan(listOf(candidate(op)), listOf(existing))

        // Ничего не поменялось — операция пропускается.
        assertEquals(MergeAction.SKIP, results[0].decision.action)
        assertEquals(7L, results[0].decision.existingTransactionId)
    }

    @Test
    fun operationWithoutDocumentIdIsNotMatchedByDocument() {
        val op = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN MOSCOW")
        val existing = stored(7L, op.timestamp, op.amountMinor, "Оплата в AUCHAN", documentId = "974845")

        val results = engine.plan(listOf(candidate(op)), listOf(existing))

        // Оба текста похожи, время и сумма совпадают — это нечёткое совпадение.
        assertEquals(MergeAction.UPDATE, results[0].decision.action)
        assertEquals(MergeReason.SAME_OPERATION, results[0].decision.reason)
    }

    // --- Нечёткое совпадение ---

    @Test
    fun sameAmountTimeAndSimilarTextIsUpdated() {
        val old = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN 6915")
        val new = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN MOSCOW RUS 6915")
        val existing = stored(7L, old.timestamp, old.amountMinor, old.rawDescription)

        val results = engine.plan(listOf(candidate(new)), listOf(existing))

        assertEquals(MergeAction.UPDATE, results[0].decision.action)
        assertEquals(MergeReason.SAME_OPERATION, results[0].decision.reason)
        assertEquals(7L, results[0].decision.existingTransactionId)
    }

    @Test
    fun sameAmountAndTimeButDifferentTextIsAdded() {
        val old = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN 6915")
        val new = operation(moment("09.11.2026 20:28"), -35_000, "Перевод на номер 0079169773779. Марина П.")
        val existing = stored(7L, old.timestamp, old.amountMinor, old.rawDescription)

        val results = engine.plan(listOf(candidate(new)), listOf(existing))

        assertEquals(MergeAction.ADD, results[0].decision.action)
    }

    @Test
    fun sameAmountOnAnotherDayIsAdded() {
        val old = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN 6915")
        val new = operation(moment("10.11.2026 20:28"), -35_000, "Оплата в AUCHAN 6915")
        val existing = stored(7L, old.timestamp, old.amountMinor, old.rawDescription)

        val results = engine.plan(listOf(candidate(new)), listOf(existing))

        assertEquals(MergeAction.ADD, results[0].decision.action)
    }

    @Test
    fun sameTextButDifferentAmountIsAdded() {
        val old = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN 6915")
        val new = operation(moment("09.11.2026 20:28"), -36_000, "Оплата в AUCHAN 6915")
        val existing = stored(7L, old.timestamp, old.amountMinor, old.rawDescription)

        val results = engine.plan(listOf(candidate(new)), listOf(existing))

        assertEquals(MergeAction.ADD, results[0].decision.action)
    }

    @Test
    fun sameAmountAndTimeButDifferentCurrencyIsAdded() {
        val old = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN 6915")
        val new = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN 6915", currency = "USD")
        val existing = stored(7L, old.timestamp, old.amountMinor, old.rawDescription)

        val results = engine.plan(listOf(candidate(new)), listOf(existing))

        assertEquals(MergeAction.ADD, results[0].decision.action)
    }

    // --- Две одинаковые покупки в один день ---

    @Test
    fun twoIdenticalPurchasesOnTheSameDayStaySeparate() {
        // Два одинаковых платежа по 350 ₽ в один день, но в разное время.
        val first = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN 6915")
        val second = operation(moment("09.11.2026 21:14"), -35_000, "Оплата в AUCHAN 6915")
        val existing = stored(7L, first.timestamp, first.amountMinor, first.rawDescription)

        val results = engine.plan(listOf(candidate(first), candidate(second)), listOf(existing))

        assertEquals(MergeAction.SKIP, results[0].decision.action)
        assertEquals(MergeAction.ADD, results[1].decision.action)
    }

    @Test
    fun oneStoredOperationIsNotClaimedTwice() {
        // Одна сохранённая операция и две входящие с тем же отпечатком:
        // вторая входящая не должна «съесть» уже использованную запись.
        val op = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN 6915")
        val existing = stored(7L, op.timestamp, op.amountMinor, op.rawDescription)

        val results = engine.plan(listOf(candidate(op), candidate(op)), listOf(existing))

        assertEquals(MergeAction.SKIP, results[0].decision.action)
        assertEquals(MergeAction.SKIP, results[1].decision.action)
    }

    // --- Пересекающиеся выписки ---

    @Test
    fun overlappingFilesSplitIntoNewAndDuplicate() {
        val october = listOf(
            operation(moment("15.10.2026 12:00"), -10_000, "Оплата в PYATEROCHKA 6915", documentId = "1"),
            operation(moment("20.10.2026 13:00"), -20_000, "Оплата в AUCHAN 6915", documentId = "2"),
        )
        val november = listOf(
            // Первая операция есть в базе — дубль.
            operation(moment("20.10.2026 13:00"), -20_000, "Оплата в AUCHAN 6915", documentId = "2"),
            // Вторая — новая.
            operation(moment("03.11.2026 09:00"), -30_000, "Оплата в AUCHAN 6915", documentId = "3"),
        )
        val existing = october.mapIndexed { index, op ->
            stored(index + 1L, op.timestamp, op.amountMinor, op.rawDescription, documentId = op.documentId)
        }

        val results = engine.plan(november.map(::candidate), existing)

        assertEquals(MergeAction.SKIP, results[0].decision.action)
        assertEquals(MergeAction.ADD, results[1].decision.action)
    }

    @Test
    fun everyOperationGetsExactlyOneDecision() {
        val incoming = (1..25).map {
            operation(moment("09.11.2026 20:28") + it * 60_000L, -1000L * it, "Оплата #$it")
        }
        val existing = incoming.take(10).mapIndexed { index, op ->
            stored(index + 1L, op.timestamp, op.amountMinor, op.rawDescription)
        }

        val results = engine.plan(incoming.map(::candidate), existing)

        assertEquals(incoming.size, results.size)
        assertEquals(10, results.count { it.decision.action == MergeAction.SKIP })
        assertEquals(15, results.count { it.decision.action == MergeAction.ADD })
        assertTrue(results.all { it.decision.existingTransactionId == null || it.decision.action != MergeAction.ADD })
    }

    @Test
    fun decisionsAlwaysCarryAnExistingIdForUpdates() {
        val old = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN", documentId = "42")
        val new = operation(moment("09.11.2026 20:28"), -35_000, "Оплата в AUCHAN MOSCOW", documentId = "42")
        val existing = stored(99L, old.timestamp, old.amountMinor, old.rawDescription, documentId = "42")

        val results = engine.plan(listOf(candidate(new)), listOf(existing))

        assertEquals(MergeAction.UPDATE, results[0].decision.action)
        assertNotNull(results[0].decision.existingTransactionId)
        assertEquals(99L, results[0].decision.existingTransactionId)
    }
}
