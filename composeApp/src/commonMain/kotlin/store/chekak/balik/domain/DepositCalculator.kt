package store.chekak.balik.domain

import kotlinx.datetime.LocalDate
import store.chekak.balik.core.DateUtils
import store.chekak.balik.data.db.DepositEntity
import store.chekak.balik.data.db.DepositPayout
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Настройки налога на проценты по вкладам (ст. 217.1 НК РФ).
 *
 * По умолчанию — как предписывает закон: 13% с суммы превышения необлагаемого
 * минимума 160 000 ₽ (1 млн ₽ × максимальная ключевая ставка ЦБ за 2026 год),
 * а с превышения порога в 2,4 млн ₽ — 15%. Любое значение можно поправить:
 * ставки и лимиты меняются каждый год.
 */
data class DepositTaxSettings(
    /** Базовая ставка НДФЛ, %: 13 по умолчанию. */
    val ratePercent: Double = 13.0,
    /** Повышенная ставка, %: 15 по умолчанию. */
    val elevatedRatePercent: Double = 15.0,
    /** Необлагаемый минимум процентов за год, в минорных единицах: 160 000 ₽. */
    val exemptMinor: Long = 16_000_000L,
    /** Порог повышенной ставки, в минорных единицах: 2 400 000 ₽. */
    val thresholdMinor: Long = 240_000_000L,
)

/** Проценты и налог по одному вкладу на выбранную дату. */
data class DepositProjection(
    val depositId: Long,
    val name: String,
    val currency: String,
    /** Тело вклада. */
    val principalMinor: Long,
    /** Начисленные проценты за период (до налога). */
    val interestMinor: Long,
    /** Налог на проценты. */
    val taxMinor: Long,
    /** Проценты после налога. */
    val netInterestMinor: Long,
    /** На вкладе к дате: тело плюс капитализированные проценты. */
    val balanceMinor: Long,
    /** Проценты, уже полученные (выплаченные или капитализированные). */
    val receivedMinor: Long,
    /** Проценты по годам: год → сумма полученных процентов. */
    val receivedByYear: Map<Int, Long> = emptyMap(),
)

/** Итог по всем вкладам на выбранную дату. */
data class DepositProjectionResult(
    val deposits: List<DepositProjection> = emptyList(),
    val totalPrincipalMinor: Long = 0,
    val totalInterestMinor: Long = 0,
    val totalTaxMinor: Long = 0,
    /** Денег на вкладах к дате (тело + капитализированные проценты). */
    val totalBalanceMinor: Long = 0,
    /** Процентов получено на руки. */
    val totalReceivedMinor: Long = 0,
) {
    /** Проценты после налога. */
    val totalNetInterestMinor: Long get() = totalInterestMinor - totalTaxMinor
}

/**
 * Расчёт роста вкладов и налога на проценты.
 *
 * Проценты начисляются ежедневно: `сумма × ставка × дни / 365`. Схема вклада
 * определяет, когда проценты попадают к владельцу:
 *
 *  * **ежемесячно** — выплата в тот же день месяца, что и открытие вклада,
 *    тело не растёт;
 *  * **в конце срока** — одна выплата в день закрытия, проценты считаются
 *    на всё тело за весь срок;
 *  * **с капитализацией** — проценты ежемесячно добавляются к телу, дальше
 *    проценты считаются уже от увеличенной суммы.
 *
 * Налог считается по правилам НК РФ: облагается только процентный доход, и
 * только он, превышающий необлагаемый минимум (по умолчанию 160 000 ₽ за 2026
 * год). Суммы по всем вкладам складываются и минимум применяется один раз на
 * календарный год: с превышения порога в 2,4 млн ₽ ставка повышается до 15%.
 *
 * Упрощения, о которых стоит знать:
 *  * проценты, выплаченные в конце срока, относятся к году выплаты;
 *  * валютные вклады налогом не облагаются — лимит и ставка заданы в рублях.
 */
class DepositCalculator {

    /**
     * Считает рост вкладов и налог на проценты на дату [toDate].
     *
     * @param fromDate «сегодня»: с этой даты считаются новые проценты
     * @param toDate дата, на которую считаем прогноз
     */
    fun project(
        deposits: List<DepositEntity>,
        fromDate: Long,
        toDate: Long,
        tax: DepositTaxSettings = DepositTaxSettings(),
    ): DepositProjectionResult {
        val projections = deposits.map { projectOne(it, fromDate, toDate) }
        val taxes = distributeTax(projections, tax)

        val enriched = projections.map { projection ->
            val taxMinor = taxes[projection.depositId] ?: 0L
            projection.copy(
                taxMinor = taxMinor,
                netInterestMinor = projection.interestMinor - taxMinor,
            )
        }

        return DepositProjectionResult(
            deposits = enriched,
            totalPrincipalMinor = enriched.sumOf { it.principalMinor },
            totalInterestMinor = enriched.sumOf { it.interestMinor },
            totalTaxMinor = enriched.sumOf { it.taxMinor },
            totalBalanceMinor = enriched.sumOf { it.balanceMinor },
            totalReceivedMinor = enriched.sumOf { it.receivedMinor },
        )
    }

    /** Проценты и налог по одному вкладу. */
    private fun projectOne(deposit: DepositEntity, fromDate: Long, toDate: Long): DepositProjection {
        val start = DateUtils.toLocalDate(deposit.startDate)
        val end = DateUtils.toLocalDate(deposit.endDate)
        val today = DateUtils.toLocalDate(fromDate)
        val target = DateUtils.toLocalDate(toDate)

        if (target <= today || end < today) {
            return DepositProjection(
                depositId = deposit.id,
                name = deposit.name,
                currency = deposit.currency,
                principalMinor = deposit.amountMinor,
                interestMinor = 0L,
                taxMinor = 0L,
                netInterestMinor = 0L,
                balanceMinor = deposit.amountMinor,
                receivedMinor = 0L,
            )
        }

        return when (deposit.payout) {
            DepositPayout.AT_END -> projectAtEnd(deposit, start, end, today, target)
            DepositPayout.MONTHLY -> projectMonthly(deposit, start, today, target)
            DepositPayout.CAPITALIZED -> projectCapitalized(deposit, start, today, target)
        }
    }

    /** Выплата в конце срока: проценты начисляются на всё тело за весь срок. */
    private fun projectAtEnd(
        deposit: DepositEntity,
        start: LocalDate,
        end: LocalDate,
        today: LocalDate,
        target: LocalDate,
    ): DepositProjection {
        val termDays = daysBetween(start, end).toDouble()
        val fullInterest = interestOn(deposit.amountMinor, deposit.annualRatePercent, termDays)

        val closed = target >= end
        val interest = if (closed) {
            fullInterest
        } else {
            interestOn(deposit.amountMinor, deposit.annualRatePercent, daysBetween(today, target).toDouble())
        }

        // Проценты получены в момент выплаты — в конце срока.
        val received = if (closed) interest else 0L
        val receivedByYear = if (closed) mapOf(end.year to received) else emptyMap()

        return DepositProjection(
            depositId = deposit.id,
            name = deposit.name,
            currency = deposit.currency,
            principalMinor = deposit.amountMinor,
            interestMinor = interest,
            taxMinor = 0L,
            netInterestMinor = interest,
            balanceMinor = deposit.amountMinor,
            receivedMinor = received,
            receivedByYear = receivedByYear,
        )
    }

    /** Ежемесячная выплата: тело не растёт, проценты приходят по датам месяца. */
    private fun projectMonthly(
        deposit: DepositEntity,
        start: LocalDate,
        today: LocalDate,
        target: LocalDate,
    ): DepositProjection {
        var interest = 0L
        var received = 0L
        val receivedByYear = mutableMapOf<Int, Long>()

        var previous = lastAnniversaryBefore(start, today) ?: start
        for (payoutDate in anniversariesAfter(start, today, target)) {
            val days = daysBetween(previous, payoutDate).toDouble()
            val payout = interestOn(deposit.amountMinor, deposit.annualRatePercent, days)
            interest += payout
            received += payout
            receivedByYear[payoutDate.year] = (receivedByYear[payoutDate.year] ?: 0L) + payout
            previous = payoutDate
        }

        return DepositProjection(
            depositId = deposit.id,
            name = deposit.name,
            currency = deposit.currency,
            principalMinor = deposit.amountMinor,
            interestMinor = interest,
            taxMinor = 0L,
            netInterestMinor = interest,
            balanceMinor = deposit.amountMinor,
            receivedMinor = received,
            receivedByYear = receivedByYear,
        )
    }

    /** Капитализация: проценты ежемесячно добавляются к телу. */
    private fun projectCapitalized(
        deposit: DepositEntity,
        start: LocalDate,
        today: LocalDate,
        target: LocalDate,
    ): DepositProjection {
        var balance = deposit.amountMinor
        var interest = 0L
        val receivedByYear = mutableMapOf<Int, Long>()

        var previous = lastAnniversaryBefore(start, today) ?: start
        for (payoutDate in anniversariesAfter(start, today, target)) {
            val days = daysBetween(previous, payoutDate).toDouble()
            val payout = interestOn(balance, deposit.annualRatePercent, days)
            balance += payout
            interest += payout
            receivedByYear[payoutDate.year] = (receivedByYear[payoutDate.year] ?: 0L) + payout
            previous = payoutDate
        }

        // Проценты за неполный текущий месяц уже начислены, но ещё не капитализированы.
        val tail = daysBetween(previous, target).toDouble()
        if (tail > 0) {
            interest += interestOn(balance, deposit.annualRatePercent, tail)
        }

        return DepositProjection(
            depositId = deposit.id,
            name = deposit.name,
            currency = deposit.currency,
            principalMinor = deposit.amountMinor,
            interestMinor = interest,
            taxMinor = 0L,
            netInterestMinor = interest,
            balanceMinor = deposit.amountMinor + interest,
            receivedMinor = receivedByYear.values.sum(),
            receivedByYear = receivedByYear,
        )
    }

    /**
     * Распределяет налог по вкладам.
     *
     * Необлагаемый минимум применяется один раз на календарный год ко всем
     * вкладам сразу, поэтому годовой процентный доход сначала складывается,
     * а потом делится между вкладами пропорционально их вкладу в доход.
     */
    private fun distributeTax(
        projections: List<DepositProjection>,
        tax: DepositTaxSettings,
    ): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        val rubProjections = projections.filter { it.currency == "RUB" }
        if (rubProjections.isEmpty()) return result

        val years = rubProjections.flatMap { it.receivedByYear.keys }.distinct().sorted()
        for (year in years) {
            val total = rubProjections.sumOf { it.receivedByYear[year] ?: 0L }
            if (total <= 0L) continue
            for (projection in rubProjections) {
                val share = projection.receivedByYear[year] ?: 0L
                if (share <= 0L) continue
                // Минимум делится между вкладами пропорционально их вкладу в доход.
                val exemptionShare = tax.exemptMinor * share / total
                val base = (share - exemptionShare).coerceAtLeast(0L)
                val normal = min(base, tax.thresholdMinor)
                val elevated = (base - tax.thresholdMinor).coerceAtLeast(0L)
                val depositTax = (normal * tax.ratePercent / 100.0).roundToLong() +
                    (elevated * tax.elevatedRatePercent / 100.0).roundToLong()
                result[projection.depositId] = (result[projection.depositId] ?: 0L) + depositTax
            }
        }
        return result
    }

    /** Проценты за период: сумма × ставка × дни / 365. */
    private fun interestOn(principalMinor: Long, annualRatePercent: Double, days: Double): Long =
        (principalMinor * annualRatePercent / 100.0 * days / 365.0).roundToLong()

    /** Даты ежемесячной выплаты после [today] и не позже [target]. */
    private fun anniversariesAfter(start: LocalDate, today: LocalDate, target: LocalDate): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var year = start.year
        var month = start.monthNumber + 1
        while (true) {
            if (month > 12) {
                month = 1
                year++
            }
            val day = min(start.dayOfMonth, DateUtils.daysInMonth(year, month))
            val date = LocalDate(year, month, day)
            if (date > target) break
            if (date > today) result += date
            month++
        }
        return result
    }

    /** Последняя дата ежемесячной выплаты, что была до [today] (или начало вклада). */
    private fun lastAnniversaryBefore(start: LocalDate, today: LocalDate): LocalDate? {
        if (today <= start) return null
        var year = start.year
        var month = start.monthNumber
        var last: LocalDate? = null
        while (true) {
            val day = min(start.dayOfMonth, DateUtils.daysInMonth(year, month))
            val date = LocalDate(year, month, day)
            if (date > today) break
            last = date
            month++
            if (month > 12) {
                month = 1
                year++
            }
            if (year > today.year + 1) break
        }
        return last
    }

    private fun daysBetween(from: LocalDate, to: LocalDate): Int =
        max(0, (to.toEpochDays() - from.toEpochDays()).toInt())
}
