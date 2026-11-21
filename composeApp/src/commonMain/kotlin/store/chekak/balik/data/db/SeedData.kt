package store.chekak.balik.data.db

/**
 * Первичное наполнение базы: системные категории, справочник MCC и настройки по умолчанию.
 */
object SeedData {

    /** Ключи системных категорий — по ним правила и лимиты ссылаются на категории. */
    object CategoryKeys {
        const val GROCERY = "groceries"
        const val CAFE = "cafe"
        const val TRANSPORT = "transport"
        const val MARKETPLACE = "marketplace"
        const val SUBSCRIPTIONS = "subscriptions"
        const val HOUSING = "housing"
        const val HEALTH = "health"
        const val ENTERTAINMENT = "entertainment"
        const val INTERNAL = "internal_transfers"
        const val INCOME = "income"
        const val OTHER = "other"
        const val CASH = "cash"
    }

    val categories: List<CategoryEntity> = listOf(
        category(CategoryKeys.GROCERY, "Продукты", "Groceries", "ShoppingCart", "#4CAF50", 10),
        category(CategoryKeys.CAFE, "Кафе и рестораны", "Cafes & Restaurants", "LocalCafe", "#FF7043", 20),
        category(CategoryKeys.TRANSPORT, "Транспорт и такси", "Transport & Taxi", "DirectionsBus", "#42A5F5", 30),
        category(CategoryKeys.MARKETPLACE, "Маркетплейсы", "Marketplaces", "Storefront", "#AB47BC", 40),
        category(CategoryKeys.SUBSCRIPTIONS, "Подписки и связь", "Subscriptions & Telecom", "Subscriptions", "#26A69A", 50),
        category(CategoryKeys.HOUSING, "ЖКХ и дом", "Housing & Utilities", "Home", "#8D6E63", 60),
        category(CategoryKeys.HEALTH, "Здоровье", "Health", "MedicalServices", "#EC407A", 70),
        category(CategoryKeys.ENTERTAINMENT, "Развлечения", "Entertainment", "Movie", "#5C6BC0", 80),
        category(CategoryKeys.INTERNAL, "Переводы себе", "Transfers to Self", "SwapHoriz", "#78909C", 90),
        category(CategoryKeys.INCOME, "Доходы", "Income", "AttachMoney", "#66BB6A", 100),
        category(CategoryKeys.CASH, "Наличные", "Cash", "Payments", "#FFA726", 110),
        category(CategoryKeys.OTHER, "Прочее", "Other", "Category", "#9E9E9E", 120),
    )

    /** Стандартный справочник MCC → системная категория. */
    val mccRules: Map<String, String> = mapOf(
        "5411" to CategoryKeys.GROCERY,
        "5412" to CategoryKeys.GROCERY,
        "5422" to CategoryKeys.GROCERY,
        "5441" to CategoryKeys.GROCERY,
        "5451" to CategoryKeys.GROCERY,
        "5499" to CategoryKeys.GROCERY,
        "5712" to CategoryKeys.TRANSPORT,
        "4111" to CategoryKeys.TRANSPORT,
        "4121" to CategoryKeys.TRANSPORT,
        "4131" to CategoryKeys.TRANSPORT,
        "4511" to CategoryKeys.TRANSPORT,
        "4722" to CategoryKeys.TRANSPORT,
        "4789" to CategoryKeys.TRANSPORT,
        "5541" to CategoryKeys.SUBSCRIPTIONS,
        "5542" to CategoryKeys.SUBSCRIPTIONS,
        "4814" to CategoryKeys.SUBSCRIPTIONS,
        "4816" to CategoryKeys.SUBSCRIPTIONS,
        "4900" to CategoryKeys.HOUSING,
        "7011" to CategoryKeys.HOUSING,
        "8931" to CategoryKeys.HOUSING,
        "6010" to CategoryKeys.CASH,
        "6011" to CategoryKeys.CASH,
        "9641" to CategoryKeys.INTERNAL,
        "4829" to CategoryKeys.MARKETPLACE,
        "4821" to CategoryKeys.MARKETPLACE,
        "5960" to CategoryKeys.MARKETPLACE,
        "5732" to CategoryKeys.ENTERTAINMENT,
        "5734" to CategoryKeys.ENTERTAINMENT,
        "5811" to CategoryKeys.CAFE,
        "5812" to CategoryKeys.CAFE,
        "5813" to CategoryKeys.CAFE,
        "5814" to CategoryKeys.CAFE,
        "5911" to CategoryKeys.HEALTH,
        "5912" to CategoryKeys.HEALTH,
        "7333" to CategoryKeys.HEALTH,
        "7997" to CategoryKeys.ENTERTAINMENT,
        "7832" to CategoryKeys.ENTERTAINMENT,
        "5945" to CategoryKeys.ENTERTAINMENT,
        "5399" to CategoryKeys.OTHER,
        "5651" to CategoryKeys.OTHER,
        "5691" to CategoryKeys.OTHER,
    )

    private fun category(
        key: String,
        name: String,
        nameEn: String,
        iconName: String,
        colorHex: String,
        sortOrder: Int,
    ) = CategoryEntity(
        key = key,
        name = name,
        nameEn = nameEn,
        iconName = iconName,
        colorHex = colorHex,
        isSystem = true,
        sortOrder = sortOrder,
    )

    /** Ключ настройки для выбранного языка. */
    const val SETTING_LANGUAGE = "language"
    const val SETTING_ONBOARDING_DONE = "onboarding_done"
    const val SETTING_FORECAST_METHOD = "forecast_method"

    /** Выбор пользователя на экране прогноза: период истории и целевая дата. */
    const val SETTING_FORECAST_PRESET = "forecast_preset"
    const val SETTING_FORECAST_FROM = "forecast_from"
    const val SETTING_FORECAST_TO = "forecast_to"
    const val SETTING_FORECAST_TARGET = "forecast_target"
    const val SETTING_DEFAULT_CURRENCY = "default_currency"
    const val SETTING_CASH_ACCOUNT_ID = "cash_account_id"
    const val SETTING_LAST_ACCOUNT_ID = "last_account_id"

    // Налог на проценты по вкладам (ст. 217.1 НК РФ). По умолчанию — 13% с суммы
    // превышения необлагаемого минимума 160 000 ₽ (1 млн ₽ × максимальная ключевая
    // ставка ЦБ за 2026 год); ставку и лимиты можно поправить вручную.
    const val SETTING_DEPOSIT_TAX_RATE = "deposit_tax_rate"
    const val SETTING_DEPOSIT_TAX_ELEVATED = "deposit_tax_elevated"
    const val SETTING_DEPOSIT_TAX_EXEMPT = "deposit_tax_exempt"
    const val SETTING_DEPOSIT_TAX_THRESHOLD = "deposit_tax_threshold"
}
