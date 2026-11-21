package store.chekak.balik.data.repository

import store.chekak.balik.data.db.AccountEntity
import store.chekak.balik.data.db.AppSettingEntity
import store.chekak.balik.data.db.AppSettingDao
import store.chekak.balik.data.db.OwnAccountEntity
import store.chekak.balik.data.db.OwnAccountDao
import store.chekak.balik.data.db.SeedData
import store.chekak.balik.domain.DepositTaxSettings
import store.chekak.balik.domain.ForecastSelection
import store.chekak.balik.domain.HistoryPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Язык интерфейса. */
enum class AppLanguage(val code: String) {
    RUSSIAN("ru"),
    ENGLISH("en");

    companion object {
        fun from(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: RUSSIAN
    }
}

/** Настройки приложения и мои собственные счета. */
class SettingsRepository(
    private val settingsDao: AppSettingDao,
    private val ownAccountDao: OwnAccountDao,
) {
    fun observeLanguage(): Flow<AppLanguage> =
        settingsDao.observeAll().map { settings ->
            AppLanguage.from(settings.firstOrNull { it.key == SeedData.SETTING_LANGUAGE }?.value)
        }

    suspend fun getLanguage(): AppLanguage =
        AppLanguage.from(settingsDao.get(SeedData.SETTING_LANGUAGE))

    suspend fun setLanguage(language: AppLanguage) {
        settingsDao.put(AppSettingEntity(SeedData.SETTING_LANGUAGE, language.code))
    }

    fun observeOnboardingCompleted(): Flow<Boolean> =
        settingsDao.observeAll().map { settings ->
            settings.firstOrNull { it.key == SeedData.SETTING_ONBOARDING_DONE }?.value == "true"
        }

    suspend fun isOnboardingCompleted(): Boolean =
        settingsDao.get(SeedData.SETTING_ONBOARDING_DONE) == "true"

    suspend fun setOnboardingCompleted(completed: Boolean) {
        settingsDao.put(AppSettingEntity(SeedData.SETTING_ONBOARDING_DONE, completed.toString()))
    }

    fun observeForecastMethod(): Flow<String> =
        settingsDao.observeAll().map { settings ->
            settings.firstOrNull { it.key == SeedData.SETTING_FORECAST_METHOD }?.value ?: "P85"
        }

    suspend fun getForecastMethod(): String =
        settingsDao.get(SeedData.SETTING_FORECAST_METHOD) ?: "P85"

    suspend fun setForecastMethod(method: String) {
        settingsDao.put(AppSettingEntity(SeedData.SETTING_FORECAST_METHOD, method))
    }

    // --- Выбор на экране прогноза: период истории и целевая дата ---

    /** Читает сохранённый выбор. Отсутствующие значения — пустые. */
    suspend fun getForecastSelection(): ForecastSelection {
        val preset = settingsDao.get(SeedData.SETTING_FORECAST_PRESET)
            ?.let { value -> runCatching { HistoryPreset.valueOf(value) }.getOrNull() }
            ?: HistoryPreset.THREE_MONTHS
        return ForecastSelection(
            preset = preset,
            customFrom = settingsDao.get(SeedData.SETTING_FORECAST_FROM)?.toLongOrNull(),
            customTo = settingsDao.get(SeedData.SETTING_FORECAST_TO)?.toLongOrNull(),
            targetDate = settingsDao.get(SeedData.SETTING_FORECAST_TARGET)?.toLongOrNull(),
        )
    }

    /** Сохраняет выбор: период истории и целевую дату. */
    suspend fun setForecastSelection(selection: ForecastSelection) {
        settingsDao.put(AppSettingEntity(SeedData.SETTING_FORECAST_PRESET, selection.preset.name))
        selection.customFrom?.let {
            settingsDao.put(AppSettingEntity(SeedData.SETTING_FORECAST_FROM, it.toString()))
        }
        selection.customTo?.let {
            settingsDao.put(AppSettingEntity(SeedData.SETTING_FORECAST_TO, it.toString()))
        }
        selection.targetDate?.let {
            settingsDao.put(AppSettingEntity(SeedData.SETTING_FORECAST_TARGET, it.toString()))
        }
    }

    fun observeOwnAccounts(): Flow<List<OwnAccountEntity>> = ownAccountDao.observeAll()

    suspend fun getOwnAccounts(): List<OwnAccountEntity> = ownAccountDao.getAll()

    suspend fun addOwnAccount(label: String, number: String) {
        val normalized = number.filter { it.isDigit() }
        if (normalized.length < 4) return
        ownAccountDao.insert(
            OwnAccountEntity(
                label = label.trim().ifEmpty { "Счёт $normalized" },
                normalized = normalized,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeOwnAccount(account: OwnAccountEntity) {
        ownAccountDao.delete(account)
    }

    // --- Налог на проценты по вкладам ---

    suspend fun getDepositTaxSettings(): DepositTaxSettings {
        val defaults = DepositTaxSettings()
        val rate = settingsDao.get(SeedData.SETTING_DEPOSIT_TAX_RATE)?.toDoubleOrNull()
        val elevated = settingsDao.get(SeedData.SETTING_DEPOSIT_TAX_ELEVATED)?.toDoubleOrNull()
        val exempt = settingsDao.get(SeedData.SETTING_DEPOSIT_TAX_EXEMPT)?.toLongOrNull()
        val threshold = settingsDao.get(SeedData.SETTING_DEPOSIT_TAX_THRESHOLD)?.toLongOrNull()
        return DepositTaxSettings(
            ratePercent = rate ?: defaults.ratePercent,
            elevatedRatePercent = elevated ?: defaults.elevatedRatePercent,
            exemptMinor = exempt ?: defaults.exemptMinor,
            thresholdMinor = threshold ?: defaults.thresholdMinor,
        )
    }

    suspend fun setDepositTaxSettings(settings: DepositTaxSettings) {
        settingsDao.put(AppSettingEntity(SeedData.SETTING_DEPOSIT_TAX_RATE, settings.ratePercent.toString()))
        settingsDao.put(
            AppSettingEntity(SeedData.SETTING_DEPOSIT_TAX_ELEVATED, settings.elevatedRatePercent.toString()),
        )
        settingsDao.put(AppSettingEntity(SeedData.SETTING_DEPOSIT_TAX_EXEMPT, settings.exemptMinor.toString()))
        settingsDao.put(AppSettingEntity(SeedData.SETTING_DEPOSIT_TAX_THRESHOLD, settings.thresholdMinor.toString()))
    }
}
