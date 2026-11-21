package store.chekak.balik.data.repository

import store.chekak.balik.data.db.AccountEntity
import store.chekak.balik.data.db.CategoryEntity
import store.chekak.balik.data.db.CategoryRuleEntity
import store.chekak.balik.data.db.ImportBatchEntity
import store.chekak.balik.data.db.ImportRowBackupEntity
import store.chekak.balik.data.db.RuleMatchType
import store.chekak.balik.data.db.TransactionEntity
import store.chekak.balik.domain.CategorizationEngine
import store.chekak.balik.domain.Fingerprint
import store.chekak.balik.domain.InternalTransferDetector
import store.chekak.balik.domain.MergeAction
import store.chekak.balik.domain.MergeCandidate
import store.chekak.balik.domain.MergeEngine
import store.chekak.balik.domain.MergeReason
import store.chekak.balik.parser.ParserRegistry
import store.chekak.balik.parser.ParsedOperation
import store.chekak.balik.parser.ParsedStatement

/**
 * Строка предпросмотра импорта: операция, предполагаемая категория и решение
 * по слиянию с уже импортированными данными.
 */
data class ImportPreviewRow(
    val operation: ParsedOperation,
    val fingerprint: String,
    val categoryId: Long?,
    val isInternalTransfer: Boolean,
    val matchedRuleId: Long? = null,
    val manuallyChanged: Boolean = false,
    /** Что будет сделано с операцией при импорте. */
    val mergeAction: MergeAction = MergeAction.ADD,
    /** Почему принято именно такое решение. */
    val mergeReason: MergeReason = MergeReason.NEW_OPERATION,
    /** Уже сохранённая операция, которую предстоит обновить. */
    val existingTransactionId: Long? = null,
) {
    /** Операция уже была в базе и не изменилась — будет пропущена. */
    val isDuplicate: Boolean get() = mergeAction == MergeAction.SKIP
}

/** Итог предпросмотра перед импортом. */
data class ImportPreview(
    val fileName: String,
    val bankId: String,
    val bankDisplayName: String,
    val accountId: Long?,
    val accountName: String,
    val currency: String,
    val balanceMinor: Long?,
    val rows: List<ImportPreviewRow> = emptyList(),
    val unrecognizedRows: Int = 0,
    val totalRows: Int = 0,
    val fileType: String = "",
    /** Период выписки — по нему решается, обновлять ли остаток счёта. */
    val periodStart: Long? = null,
    val periodEnd: Long? = null,
    /** Последние цифры карты — по ним счёт создаётся при первом импорте. */
    val cardLast4: String? = null,
    /** Текст выписки распознан по картинке (OCR), а не взят из текстового слоя. */
    val ocrUsed: Boolean = false,
) {
    /** Операций, которых в базе ещё не было. */
    val newCount: Int get() = rows.count { it.mergeAction == MergeAction.ADD }

    /** Операций, которые уже были, но банк изменил в них детали. */
    val updatedCount: Int get() = rows.count { it.mergeAction == MergeAction.UPDATE }

    /** Операций, которые уже были и не изменились. */
    val duplicateCount: Int get() = rows.count { it.mergeAction == MergeAction.SKIP }

    /** Операций, из-за которых импорт вообще что-то меняет. */
    val changedCount: Int get() = newCount + updatedCount
}

/**
 * Импорт выписок: разбор файла, сопоставление с уже загруженными данными,
 * предпросмотр, защита от дублей и откат батча.
 *
 * Выписки одного банка за разные периоды пересекаются, поэтому импорт не
 * «добавляет всё подряд», а сначала разбирает каждую операцию: что уже есть
 * (пропустить), что изменилось (обновить) и чего действительно нет (добавить).
 */
class ImportRepository(
    private val ledgerRepository: LedgerRepository,
    private val settingsRepository: SettingsRepository,
    private val parserRegistry: ParserRegistry,
) {

    private val mergeEngine = MergeEngine()

    /** Разбирает файл и готовит предпросмотр. */
    suspend fun analyze(bytes: ByteArray, fileName: String): ImportPreview {
        // На случай, если импорт пришёл раньше, чем отработало заполнение базы.
        ledgerRepository.ensureSeeded()
        val statement = parserRegistry.parse(bytes, fileName)
        val categories = ledgerRepository.getCategories()
        val ownAccounts = settingsRepository.getOwnAccounts().map { it.normalized }
        val engine = CategorizationEngine(
            rules = ledgerRepository.getRules(),
            categories = categories,
            internalTransferDetector = InternalTransferDetector(ownAccounts),
        )

        val account = findAccount(statement)

        // Сопоставляем только с операциями этого же счёта: иначе совпадения
        // искались бы среди чужих карт того же банка.
        val existing = account?.let { ledgerRepository.getTransactionsByAccount(it.id) }.orEmpty()

        // Счётчик одинаковых операций внутри выписки: см. [fingerprintOf].
        val occurrences = HashMap<String, Int>()

        val candidates = statement.operations.map { operation ->
            MergeCandidate(
                operation = operation,
                fingerprint = fingerprintOf(statement.bankId, operation, occurrences),
            )
        }
        val plan = mergeEngine.plan(candidates, existing)

        val rows = plan.map { result ->
            val operation = result.candidate.operation
            val categorization = engine.categorize(operation)
            ImportPreviewRow(
                operation = operation,
                fingerprint = result.candidate.fingerprint,
                categoryId = categorization.categoryId,
                isInternalTransfer = categorization.isInternalTransfer,
                matchedRuleId = categorization.matchedRuleId,
                mergeAction = result.decision.action,
                mergeReason = result.decision.reason,
                existingTransactionId = result.decision.existingTransactionId,
            )
        }

        return ImportPreview(
            fileName = fileName,
            bankId = statement.bankId,
            bankDisplayName = statement.bankDisplayName,
            accountId = account?.id,
            accountName = account?.accountName ?: statement.accountName,
            currency = statement.currency,
            balanceMinor = statement.balanceMinor,
            rows = rows,
            unrecognizedRows = statement.unrecognizedRows,
            totalRows = statement.totalRows,
            fileType = statement.fileType,
            periodStart = statement.periodStart,
            periodEnd = statement.periodEnd,
            cardLast4 = statement.cardLast4?.trim()?.takeIf { it.isNotEmpty() },
            ocrUsed = statement.ocrUsed,
        )
    }

    /**
     * Ищет счёт, к которому относится выписка.
     *
     * Сопоставление строго по последним цифрам карты: две разные карты одного банка
     * никогда не склеиваются в один счёт. Если в файле карты нет, счёт ищется по банку
     * и названию — так повторный импорт того же файла попадает в тот же счёт.
     *
     * Разбор файла ничего не меняет в базе: счёт создаётся только при подтверждении
     * импорта, иначе отменённый импорт оставлял бы после себя пустой счёт.
     */
    private suspend fun findAccount(statement: ParsedStatement): AccountEntity? {
        val accounts = ledgerRepository.getAccounts()
        val card = statement.cardLast4?.trim()?.takeIf { it.isNotEmpty() }
        val sameBank = accounts.filter {
            it.bankName.equals(statement.bankDisplayName, ignoreCase = true)
        }

        return when {
            card != null -> sameBank.firstOrNull { it.cardLast4 == card }
            else -> sameBank.firstOrNull {
                it.cardLast4 == null &&
                    it.accountName.equals(statement.accountName, ignoreCase = true)
            } ?: sameBank.singleOrNull { it.cardLast4 == null }
        }
    }

    /**
     * Счёт для импорта: уже известный или создаваемый при первом импорте выписки.
     * Заодно обновляется остаток — если выписка не старше уже загруженной.
     */
    private suspend fun resolveAccount(preview: ImportPreview): AccountEntity {
        val existing = preview.accountId?.let { ledgerRepository.getAccount(it) }
        if (existing != null) {
            applyStatementBalance(existing, preview)
            return existing
        }

        val statementDate = statementDate(preview)
        val id = ledgerRepository.upsertAccount(
            AccountEntity(
                bankName = preview.bankDisplayName,
                accountName = preview.accountName.ifBlank { "Карта" },
                currency = preview.currency,
                cardLast4 = preview.cardLast4,
                balanceMinor = preview.balanceMinor,
                lastUpdated = statementDate ?: 0L,
            ),
        )
        return ledgerRepository.getAccount(id)
            ?: error("Не удалось создать счёт для выписки — импорт отменён")
    }

    /**
     * Обновляет остаток счёта, только если выписка не старше уже загруженной.
     *
     * Иначе импорт старого файла после нового откатил бы остаток назад.
     */
    private suspend fun applyStatementBalance(account: AccountEntity, preview: ImportPreview) {
        val balance = preview.balanceMinor ?: return
        val statementDate = statementDate(preview) ?: return
        if (statementDate < account.lastUpdated) return
        ledgerRepository.updateBalance(account.id, balance, statementDate)
    }

    /** Дата, на которую актуальна выписка: конец периода или последняя операция. */
    private fun statementDate(preview: ImportPreview): Long? =
        preview.periodEnd ?: preview.rows.maxOfOrNull { it.operation.timestamp }

    /**
     * Создаёт правило «запомнить для похожих операций» и пересчитывает категории
     * остальных строк предпросмотра.
     */
    suspend fun rememberRule(
        preview: ImportPreview,
        row: ImportPreviewRow,
        categoryId: Long,
    ): ImportPreview {
        val pattern = row.operation.merchantName.ifBlank { row.operation.rawDescription }.trim()
        if (pattern.isEmpty()) return preview

        val existingRules = ledgerRepository.getRules()
        val maxPriority = existingRules.maxOfOrNull { it.priority } ?: 0
        val rule = CategoryRuleEntity(
            pattern = pattern,
            matchType = RuleMatchType.CONTAINS,
            categoryId = categoryId,
            setInternalTransfer = row.isInternalTransfer,
            priority = maxPriority + 10,
        )
        ledgerRepository.upsertRule(rule)

        return preview.copy(rows = preview.rows.map { current ->
            if (current.operation.merchantName.equals(pattern, ignoreCase = true) ||
                current.operation.rawDescription.contains(pattern, ignoreCase = true)
            ) {
                current.copy(categoryId = categoryId, isInternalTransfer = row.isInternalTransfer, matchedRuleId = rule.id)
            } else {
                current
            }
        })
    }

    /**
     * Фиксирует импорт: добавляет новые операции, обновляет те, у которых банк
     * изменил детали, и сохраняет снимки обновлённых строк — чтобы откат вернул
     * их прежний вид.
     */
    suspend fun commit(preview: ImportPreview): ImportBatchEntity {
        val account = resolveAccount(preview)
        val accountId = account.id
        val now = System.currentTimeMillis()

        val batchId = ledgerRepository.insertImportBatch(
            ImportBatchEntity(
                fileName = preview.fileName,
                bankName = preview.bankDisplayName,
                importedAt = now,
                totalRows = preview.totalRows,
                newRows = preview.newCount,
                updatedRows = preview.updatedCount,
                skippedDuplicates = preview.duplicateCount,
                accountId = accountId,
                fileType = preview.fileType,
            ),
        )

        // 1. Новые операции.
        val newEntities = preview.rows
            .filter { it.mergeAction == MergeAction.ADD }
            .map { row -> row.toEntity(accountId, batchId, now) }
        ledgerRepository.upsertTransactions(newEntities)

        // 2. Обновления: подменяем банковские детали, сохраняя категорию пользователя.
        val backups = mutableListOf<ImportRowBackupEntity>()
        for (row in preview.rows.filter { it.mergeAction == MergeAction.UPDATE }) {
            val transactionId = row.existingTransactionId ?: continue
            val existing = ledgerRepository.getTransaction(transactionId) ?: continue
            val operation = row.operation

            val applied = runCatching {
                ledgerRepository.updateTransactionBankDetails(
                    id = transactionId,
                    // Отпечаток уже посчитан при разборе файла — берём тот же самый,
                    // иначе одинаковые операции начали бы совпадать между собой.
                    fingerprint = row.fingerprint,
                    rawDescription = operation.rawDescription,
                    merchantName = operation.merchantName,
                    mcc = operation.mcc,
                    documentId = operation.documentId,
                )
            }.isSuccess
            if (!applied) continue

            backups += ImportRowBackupEntity(
                batchId = batchId,
                transactionId = transactionId,
                fingerprint = existing.fingerprint,
                rawDescription = existing.rawDescription,
                merchantName = existing.merchantName,
                mcc = existing.mcc,
                categoryId = existing.categoryId,
                isInternalTransfer = existing.isInternalTransfer,
            )
        }
        if (backups.isNotEmpty()) {
            ledgerRepository.insertImportBackups(backups)
        }

        // 3. Счётчики батча — по фактически применённому.
        ledgerRepository.updateImportBatchCounts(
            id = batchId,
            newRows = newEntities.size,
            updatedRows = backups.size,
            skippedDuplicates = preview.duplicateCount,
        )

        return ImportBatchEntity(
            id = batchId,
            fileName = preview.fileName,
            bankName = preview.bankDisplayName,
            importedAt = now,
            totalRows = preview.totalRows,
            newRows = newEntities.size,
            updatedRows = backups.size,
            skippedDuplicates = preview.duplicateCount,
            accountId = accountId,
            fileType = preview.fileType,
        )
    }

    /** Откат импорта: удаляет добавленные операции и возвращает обновлённым прежний вид. */
    suspend fun rollback(batchId: Long) {
        ledgerRepository.rollbackImportBatch(batchId)
    }

    /** Категории для выбора в интерфейсе. */
    suspend fun categories(): List<CategoryEntity> = ledgerRepository.getCategories()

    /**
     * Отпечаток операции с учётом её номера среди одинаковых в этой выписке.
     *
     * В выписке встречаются по-настоящему одинаковые операции: например, два возврата
     * на 50,00 в одну и ту же минуту. Без порядкового номера отпечатки совпали бы,
     * вторая операция потерялась бы (в базе отпечаток уникален, а движок слияния
     * увидел бы внутрифайловый дубль) — и итоги не сошлись бы с банковскими.
     *
     * Нумерация детерминирована (порядок строк в файле), поэтому повторный импорт
     * того же файла даёт те же отпечатки и дублей не возникает.
     */
    private fun fingerprintOf(
        bankId: String,
        operation: ParsedOperation,
        occurrences: MutableMap<String, Int>,
    ): String {
        val base = Fingerprint.of(
            bankId = bankId,
            timestamp = operation.timestamp,
            amountMinor = operation.amountMinor,
            currency = operation.currency,
            rawDescription = operation.rawDescription,
        )
        val occurrence = occurrences.merge(base, 1, Int::plus) ?: 1
        return if (occurrence == 1) base else "$base#$occurrence"
    }

    /** Строка предпросмотра превращается в сохраняемую операцию. */
    private fun ImportPreviewRow.toEntity(
        accountId: Long,
        batchId: Long,
        now: Long,
    ): TransactionEntity = TransactionEntity(
        fingerprint = fingerprint,
        accountId = accountId,
        timestamp = operation.timestamp,
        amountMinor = operation.amountMinor,
        currency = operation.currency,
        rawDescription = operation.rawDescription,
        merchantName = operation.merchantName,
        mcc = operation.mcc,
        documentId = operation.documentId,
        categoryId = categoryId,
        isInternalTransfer = isInternalTransfer,
        isManual = false,
        importBatchId = batchId,
        createdAt = now,
    )
}
