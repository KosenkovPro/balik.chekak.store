package store.chekak.balik.ui.forecast

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import store.chekak.balik.core.DateUtils
import store.chekak.balik.data.db.DepositEntity
import store.chekak.balik.data.repository.DepositRepository
import store.chekak.balik.data.repository.LedgerRepository
import store.chekak.balik.data.repository.SettingsRepository
import store.chekak.balik.domain.DepositCalculator
import store.chekak.balik.domain.DepositProjectionResult
import store.chekak.balik.domain.DepositTaxSettings
import store.chekak.balik.domain.ForecastCalculator
import store.chekak.balik.domain.ForecastHorizon
import store.chekak.balik.domain.ForecastMethod
import store.chekak.balik.domain.ForecastPeriod
import store.chekak.balik.domain.ForecastResult
import store.chekak.balik.domain.ForecastSelection
import store.chekak.balik.domain.HistoryPreset

/** Состояние экрана прогноза. */
@Immutable
data class ForecastState(
    val isLoading: Boolean = true,
    val method: ForecastMethod = ForecastMethod.P85,
    val preset: HistoryPreset = HistoryPreset.THREE_MONTHS,
    val historyFrom: Long = 0L,
    val historyTo: Long = 0L,
    val targetDate: Long = 0L,
    val currency: String = "RUB",
    val result: ForecastResult? = null,
    val balances: Map<String, Long> = emptyMap(),
    val deposits: List<DepositEntity> = emptyList(),
    val depositProjection: DepositProjectionResult = DepositProjectionResult(),
    val taxSettings: DepositTaxSettings = DepositTaxSettings(),
) {
    /**
     * Дата, соответствующая быстрому пресету горизонта.
     *
     * Нужна, чтобы подсветить чип, когда выбранная дата совпадает с пресетом.
     */
    fun horizonDate(horizon: ForecastHorizon): Long {
        val today = System.currentTimeMillis()
        return DateUtils.startOfDay(today + horizon.days * MILLIS_PER_DAY)
    }
}

/** Выбор пользователя: метод, период истории и целевая дата. */
private data class ForecastSelectionState(
    /** Загружен ли выбор из настроек — до этого показываем «загрузка». */
    val loaded: Boolean = false,
    val method: ForecastMethod = ForecastMethod.P85,
    val preset: HistoryPreset = HistoryPreset.THREE_MONTHS,
    val customFrom: Long? = null,
    val customTo: Long? = null,
    val targetDate: Long? = null,
) {
    /** Выбор для сохранения в настройках. */
    fun toStored(): ForecastSelection = ForecastSelection(
        preset = preset,
        customFrom = customFrom,
        customTo = customTo,
        targetDate = targetDate,
    )
}

/**
 * Прогноз: сколько денег будет на выбранную дату, если ничего не менять.
 *
 * Темп расходов и доходов берётся из выбранного периода истории (произвольного),
 * а прогноз считается до выбранной целевой даты (тоже произвольной).
 * Метод расчёта — медиана, среднее или 85-й перцентиль.
 *
 * Выбор пользователя сохраняется в настройках: период и целевая дата не слетают
 * при переходе на другой экран и переживают перезапуск приложения.
 */
class ForecastViewModel(
    private val ledgerRepository: LedgerRepository,
    private val settingsRepository: SettingsRepository,
    private val depositRepository: DepositRepository,
    private val forecastCalculator: ForecastCalculator,
    private val depositCalculator: DepositCalculator,
    private val scope: CoroutineScope,
) {
    private val selection = MutableStateFlow(ForecastSelectionState())

    init {
        scope.launch {
            val saved = settingsRepository.getForecastSelection()
            selection.value = selection.value.copy(
                loaded = true,
                preset = saved.preset,
                customFrom = saved.customFrom,
                customTo = saved.customTo,
                targetDate = saved.targetDate,
            )
        }
    }

    val state: StateFlow<ForecastState> = combine(
        ledgerRepository.observeTransactions(),
        ledgerRepository.observeAccounts(),
        depositRepository.observeAll(),
        selection,
    ) { transactions, accounts, deposits, sel ->
        val balances = accounts
            .filter { !it.isCash && it.balanceMinor != null }
            .groupBy { it.currency }
            .mapValues { (_, list) -> list.sumOf { it.balanceMinor ?: 0L } }

        val currency = balances.keys.firstOrNull()
            ?: transactions.maxByOrNull { it.timestamp }?.currency
            ?: "RUB"

        val today = System.currentTimeMillis()
        val earliest = transactions.minOfOrNull { it.timestamp } ?: today
        val period = ForecastPeriod.resolve(
            preset = sel.preset,
            earliest = earliest,
            today = today,
            customFrom = sel.customFrom,
            customTo = sel.customTo,
        )
        val target = sel.targetDate ?: ForecastPeriod.defaultTargetDate(today)

        val result = forecastCalculator.calculate(
            transactions = transactions,
            currentBalanceMinor = balances[currency] ?: 0L,
            currency = currency,
            historyFrom = period.first,
            historyTo = period.last,
            targetDate = target,
            today = today,
            method = sel.method,
        )

        val taxSettings = settingsRepository.getDepositTaxSettings()
        val depositProjection = depositCalculator.project(
            deposits = deposits,
            fromDate = today,
            toDate = target,
            tax = taxSettings,
        )

        ForecastState(
            isLoading = !sel.loaded,
            method = sel.method,
            preset = sel.preset,
            historyFrom = period.first,
            historyTo = period.last,
            targetDate = target,
            currency = currency,
            result = result,
            balances = balances,
            deposits = deposits,
            depositProjection = depositProjection,
            taxSettings = taxSettings,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), ForecastState())

    fun setMethod(value: ForecastMethod) {
        selection.value = selection.value.copy(method = value)
        scope.launch { settingsRepository.setForecastMethod(value.name) }
    }

    /** Пресет периода истории: сбрасывает свои даты, если они были заданы. */
    fun setPreset(value: HistoryPreset) {
        selection.value = selection.value.copy(preset = value, customFrom = null, customTo = null)
        persist()
    }

    /** Свои границы периода истории. */
    fun setHistoryRange(from: Long, to: Long) {
        selection.value = selection.value.copy(
            preset = HistoryPreset.CUSTOM,
            customFrom = minOf(from, to),
            customTo = maxOf(from, to),
        )
        persist()
    }

    /** Произвольная целевая дата. */
    fun setTargetDate(date: Long) {
        selection.value = selection.value.copy(targetDate = DateUtils.startOfDay(date))
        persist()
    }

    /**
     * Быстрый пресет целевой даты: столько дней от «сегодня».
     *
     * Та же дата, что и раньше — неделя, месяц, 3 месяца, год — только теперь
     * любую дату можно задать и вручную.
     */
    fun setTargetHorizon(horizon: ForecastHorizon) {
        val today = System.currentTimeMillis()
        val date = DateUtils.startOfDay(today + horizon.days * MILLIS_PER_DAY)
        selection.value = selection.value.copy(targetDate = date)
        persist()
    }

    /** Сохраняет выбор в настройках. */
    private fun persist() {
        scope.launch { settingsRepository.setForecastSelection(selection.value.toStored()) }
    }
}

/** Миллисекунд в сутках. */
private const val MILLIS_PER_DAY = 86_400_000L
