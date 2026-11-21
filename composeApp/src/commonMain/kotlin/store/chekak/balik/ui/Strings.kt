package store.chekak.balik.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import store.chekak.balik.data.repository.AppLanguage

/**
 * Все строки интерфейса. Переключение языка мгновенное: root-компонент читает
 * язык из настроек и передаёт нужный набор строк через [LocalStrings].
 */
class Strings(lang: AppLanguage) {

    private val appLanguage: AppLanguage = lang

    val isRussian: Boolean get() = appLanguage == AppLanguage.RUSSIAN

    // Общие
    val appName = if (isRussian) "ЧёКак: Балик" else "CheKak: Buck"
    val ok = if (isRussian) "ОК" else "OK"
    val cancel = if (isRussian) "Отмена" else "Cancel"
    val save = if (isRussian) "Сохранить" else "Save"
    val delete = if (isRussian) "Удалить" else "Delete"
    val edit = if (isRussian) "Изменить" else "Edit"
    val back = if (isRussian) "Назад" else "Back"
    val add = if (isRussian) "Добавить" else "Add"
    val apply = if (isRussian) "Применить" else "Apply"
    val close = if (isRussian) "Закрыть" else "Close"
    val retry = if (isRussian) "Повторить" else "Retry"
    val done = if (isRussian) "Готово" else "Done"
    val loading = if (isRussian) "Загрузка…" else "Loading…"
    val saved = if (isRussian) "Сохранено" else "Saved"
    val all = if (isRussian) "Все" else "All"
    val none = if (isRussian) "Нет" else "None"

    // Онбординг
    val onboardingTitle1 = if (isRussian) "Импортируй выписки" else "Import statements"
    val onboardingText1 = if (isRussian) {
        "CSV, XLSX или PDF из банка — приложение разберёт их сама."
    } else {
        "CSV, XLSX or PDF from your bank — the app parses them for you."
    }
    val onboardingTitle2 = if (isRussian) "Категории и лимиты" else "Categories and limits"
    val onboardingText2 = if (isRussian) {
        "Расходы раскладываются по категориям, для каждой можно задать месячный лимит."
    } else {
        "Expenses are sorted into categories, each with an optional monthly limit."
    }
    val onboardingTitle3 = if (isRussian) "Всё остаётся на устройстве" else "Everything stays on device"
    val onboardingText3 = if (isRussian) {
        "Никаких серверов и интернета: данные лежат только в локальной базе."
    } else {
        "No servers, no internet: your data lives in a local database only."
    }
    val onboardingNext = if (isRussian) "Далее" else "Next"
    val onboardingStart = if (isRussian) "Начать" else "Get started"
    val onboardingSkip = if (isRussian) "Пропустить" else "Skip"

    // Дашборд
    val dashboard = if (isRussian) "Дашборд" else "Dashboard"
    val periodCurrentMonth = if (isRussian) "Текущий месяц" else "Current month"
    val periodLastMonth = if (isRussian) "Прошлый месяц" else "Last month"
    val periodCustom = if (isRussian) "Произвольный интервал" else "Custom range"
    val expenses = if (isRussian) "Расходы" else "Expenses"
    val incomes = if (isRussian) "Доходы" else "Income"
    val balance = if (isRussian) "Остаток" else "Balance"
    val delta = if (isRussian) "Дельта" else "Net"
    val byCategory = if (isRussian) "По категориям" else "By category"
    val importStatement = if (isRussian) "Загрузить выписку" else "Import statement"
    val noData = if (isRussian) "Пока нет операций" else "No transactions yet"
    val noDataHint = if (isRussian) {
        "Загрузите выписку из банка — всё останется на устройстве."
    } else {
        "Import a bank statement — everything stays on your device."
    }
    val allAccounts = if (isRussian) "Все счета" else "All accounts"
    val transactions = if (isRussian) "Операции" else "Transactions"
    val forecast = if (isRussian) "Прогноз" else "Forecast"
    val cash = if (isRussian) "Наличные" else "Cash"
    val settings = if (isRussian) "Настройки" else "Settings"
    val newOperations = if (isRussian) "Новых операций" else "New transactions"
    val noTransactionsInPeriod = if (isRussian) {
        "За выбранный период операций нет"
    } else {
        "No transactions in the selected period"
    }

    // Импорт
    val importPreview = if (isRussian) "Предпросмотр импорта" else "Import preview"
    val detectedBank = if (isRussian) "Определённый банк" else "Detected bank"
    val duplicates = if (isRussian) "Дублей пропущено" else "Duplicates skipped"
    val unrecognized = if (isRussian) "Нераспознанных строк" else "Unrecognized rows"
    val importNow = if (isRussian) "Импортировать" else "Import"
    val rememberRule = if (isRussian) {
        "Запомнить правило для похожих операций"
    } else {
        "Remember this rule for similar transactions"
    }
    val selectCategory = if (isRussian) "Выберите категорию" else "Select category"
    val importFailed = if (isRussian) {
        "Не удалось разобрать файл. Проверь, что это выписка в CSV, XLSX или PDF."
    } else {
        "Could not parse the file. Make sure it is a CSV, XLSX or PDF statement."
    }
    val importDone = if (isRussian) "Импорт завершён" else "Import complete"
    val skippedDuplicates = if (isRussian) "Дубли будут пропущены" else "Duplicates will be skipped"
    val updatedOperations = if (isRussian) "Обновлено" else "Updated"
    val newBadge = if (isRussian) "Новая" else "New"
    val updatedBadge = if (isRussian) "Обновление" else "Updated"
    val duplicateBadge = if (isRussian) "Уже импортирована" else "Already imported"
    val importNothingToDo = if (isRussian) {
        "Все операции из файла уже есть в базе"
    } else {
        "Every transaction from this file is already imported"
    }
    val statementPeriod = if (isRussian) "Период выписки" else "Statement period"
    val ocrRecognized = if (isRussian) {
        "Текст распознан по картинке (OCR) — проверьте суммы перед импортом"
    } else {
        "Text was recognized from the image (OCR) — check the amounts before importing"
    }

    // Сверка распознавания с оригиналом
    val ocrComparison = if (isRussian) "Сверка OCR" else "OCR verification"
    val compareWithOriginal = if (isRussian) "Сверить с оригиналом" else "Compare with the original"
    val comparisonIntro = if (isRussian) {
        "Выберите оригинал выписки — ту же выписку, но с текстовым слоем (PDF с текстом, " +
            "CSV или XLSX). Приложение сопоставит операции и покажет, где распознанное " +
            "отличается от оригинала."
    } else {
        "Choose the original statement — the same statement with a text layer (a PDF with " +
            "text, CSV or XLSX). The app will match the transactions and show where the " +
            "recognized text differs from the original."
    }
    val pickOriginalFile = if (isRussian) "Выбрать файл оригинала" else "Choose the original file"
    val pickAnotherFile = if (isRussian) "Выбрать другой файл" else "Choose another file"
    val comparingWithOriginal = if (isRussian) {
        "Сравниваем с оригиналом…"
    } else {
        "Comparing with the original…"
    }
    val originalFile = if (isRussian) "Оригинал" else "Original"
    val operationsInOriginal = if (isRussian) "операций в оригинале" else "operations in the original"
    val recognizedOperations = if (isRussian) "распознано" else "recognized"
    val exactMatches = if (isRussian) "Совпали дословно" else "Exact matches"
    val averageSimilarity = if (isRussian) "Средняя сходность" else "Average similarity"
    val mismatches = if (isRussian) "Расхождения" else "Mismatches"
    val noMismatches = if (isRussian) {
        "Расхождений нет — распознано точно как в оригинале"
    } else {
        "No mismatches — recognized exactly as in the original"
    }
    val onlyMismatches = if (isRussian) "Только расхождения" else "Mismatches only"
    val showAllRows = if (isRussian) "Показать все" else "Show all"
    val inOriginal = if (isRussian) "В оригинале" else "In the original"
    val inRecognized = if (isRussian) "Распознано" else "Recognized"
    val notInOriginal = if (isRussian) "В оригинале такой операции нет" else "Not present in the original"
    val notRecognized = if (isRussian) "Не распознано" else "Not recognized"
    val similarity = if (isRussian) "Сходство" else "Similarity"
    val statusExact = if (isRussian) "Совпало" else "Exact"
    val statusSimilar = if (isRussian) "Похоже" else "Similar"
    val statusDifferent = if (isRussian) "Отличается" else "Differs"
    val statusOnlyReference = if (isRussian) "Только в оригинале" else "Only in the original"
    val statusOnlyActual = if (isRussian) "Лишняя операция" else "Extra transaction"
    val scanNotRecognized = if (isRussian) {
        "Не удалось распознать текст в PDF: похоже, это скан плохого качества. " +
            "Попробуйте выписку в CSV или XLSX."
    } else {
        "Could not recognize the text in the PDF — it looks like a low-quality scan. " +
            "Try the CSV or XLSX version of the statement."
    }
    val noOperationsFound = if (isRussian) {
        "В файле не найдено ни одной операции. Проверьте, что это выписка."
    } else {
        "No transactions found in the file. Make sure it is a statement."
    }
    val referenceWithoutText = if (isRussian) {
        "В оригинале нет текстового слоя: нужен PDF с текстом, CSV или XLSX."
    } else {
        "The original has no text layer — a PDF with text, CSV or XLSX is needed."
    }

    // Лента операций
    val search = if (isRussian) "Поиск" else "Search"
    val filterCategory = if (isRussian) "Категория" else "Category"
    val filterInternal = if (isRussian) "Переводы себе" else "Transfers to self"
    val allCategories = if (isRussian) "Все категории" else "All categories"
    val noResults = if (isRussian) "Ничего не найдено" else "Nothing found"
    val addManual = if (isRussian) "Добавить операцию" else "Add transaction"
    val internalTransferBadge = if (isRussian) "Перевод себе" else "Self transfer"

    // Операция
    val editOperation = if (isRussian) "Операция" else "Transaction"
    val amount = if (isRussian) "Сумма" else "Amount"
    val date = if (isRussian) "Дата" else "Date"
    val description = if (isRussian) "Описание" else "Description"
    val category = if (isRussian) "Категория" else "Category"
    val isInternalTransfer = if (isRussian) "Перевод самому себе" else "Transfer to myself"
    val deleteOperation = if (isRussian) "Удалить операцию" else "Delete transaction"
    val deleteConfirm = if (isRussian) "Удалить операцию?" else "Delete this transaction?"
    val newOperation = if (isRussian) "Новая операция" else "New transaction"
    val income = if (isRussian) "Доход" else "Income"
    val expense = if (isRussian) "Расход" else "Expense"
    val account = if (isRussian) "Счёт" else "Account"
    val operationSaved = if (isRussian) "Операция сохранена" else "Transaction saved"

    // Наличные
    val cashTitle = if (isRussian) "Наличные" else "Cash"
    val cashBalance = if (isRussian) "Остаток наличных" else "Cash balance"
    val cashTopUp = if (isRussian) "Пополнить" else "Top up"
    val cashWithdraw = if (isRussian) "Снять" else "Withdraw"
    val cashNoOperations = if (isRussian) {
        "Операций с наличными пока нет"
    } else {
        "No cash transactions yet"
    }
    val cashAccountCreated = if (isRussian) {
        "Счёт наличных создан"
    } else {
        "Cash account created"
    }

    // Прогноз
    val forecastTitle = if (isRussian) "Прогноз" else "Forecast"
    val forecastMethod = if (isRussian) "Метод расчёта" else "Method"
    val methodMedian = if (isRussian) "Медиана" else "Median"
    val methodMean = if (isRussian) "Среднее" else "Average"
    val methodP85 = if (isRussian) "85-й перцентиль" else "85th percentile"
    val horizon = if (isRussian) "Горизонт" else "Horizon"
    val horizonWeek = if (isRussian) "Неделя" else "Week"
    val horizonMonth = if (isRussian) "Месяц" else "Month"
    val horizonQuarter = if (isRussian) "3 месяца" else "3 months"
    val horizonYear = if (isRussian) "Год" else "Year"
    val dailyRate = if (isRussian) "В среднем в день" else "Daily average"
    val projectedExpenses = if (isRussian) "Прогноз расходов" else "Projected expenses"
    val projectedIncome = if (isRussian) "Прогноз доходов" else "Projected income"
    val projectedBalance = if (isRussian) "Через" else "In"
    val currentBalance = if (isRussian) "Сейчас на счетах" else "Current balance"
    val willSave = if (isRussian) "Накопите" else "You will save"
    val willDebt = if (isRussian) "Будете должны" else "You will owe"
    val forecastNoData = if (isRussian) {
        "Недостаточно данных для прогноза — импортируйте выписки"
    } else {
        "Not enough data for a forecast — import some statements"
    }
    val monthsAnalyzed = if (isRussian) "Месяцев в анализе" else "Months analysed"
    val forecastHistory = if (isRussian) "История по месяцам" else "Monthly history"

    // Прогноз: произвольные даты
    val historyPeriod = if (isRussian) "Период для расчёта темпа" else "Period for the average"
    val historyFrom = if (isRussian) "С" else "From"
    val historyTo = if (isRussian) "По" else "To"
    val lastMonth = if (isRussian) "Прошлый месяц" else "Last month"
    val threeMonths = if (isRussian) "3 месяца" else "3 months"
    val sixMonths = if (isRussian) "6 месяцев" else "6 months"
    val allTime = if (isRussian) "Всё время" else "All time"
    val targetDateTitle = if (isRussian) "Прогноз на дату" else "Forecast for a date"
    val targetDate = if (isRussian) "Дата" else "Date"
    val balanceOnDate = if (isRussian) "Остаток на дату" else "Balance on that date"
    val daysAhead = if (isRussian) "дней до даты" else "days ahead"
    val expensesUntilDate = if (isRussian) "Расходы до даты" else "Expenses until then"
    val incomesUntilDate = if (isRussian) "Доходы до даты" else "Income until then"
    val dailyExpenses = if (isRussian) "В среднем в день: расходы" else "Daily average: expenses"
    val dailyIncomes = if (isRussian) "В среднем в день: доходы" else "Daily average: income"
    val monthlyBreakdown = if (isRussian) "Разбивка по месяцам" else "Monthly breakdown"
    val forecastHint = if (isRussian) {
        "Темп расходов и доходов берётся из выбранного периода, прогноз — до выбранной даты."
    } else {
        "The pace of expenses and income comes from the chosen period; the forecast runs to the chosen date."
    }

    // Вклады
    val deposits = if (isRussian) "Вклады" else "Deposits"
    val addDeposit = if (isRussian) "Добавить вклад" else "Add deposit"
    val editDeposit = if (isRussian) "Вклад" else "Deposit"
    val depositName = if (isRussian) "Название" else "Name"
    val depositBank = if (isRussian) "Банк" else "Bank"
    val depositAmount = if (isRussian) "Сумма вклада" else "Deposit amount"
    val depositRate = if (isRussian) "Ставка, % годовых" else "Rate, % per year"
    val depositStart = if (isRussian) "Открыт" else "Opened"
    val depositEnd = if (isRussian) "Закрыт" else "Closed"
    val depositPayout = if (isRussian) "Выплата процентов" else "Interest payout"
    val payoutMonthly = if (isRussian) "Ежемесячно" else "Monthly"
    val payoutAtEnd = if (isRussian) "В конце срока" else "At the end of term"
    val payoutCapitalized = if (isRussian) "С капитализацией" else "With capitalization"
    val depositInterest = if (isRussian) "Проценты" else "Interest"
    val depositTax = if (isRussian) "Налог" else "Tax"
    val depositNet = if (isRussian) "Проценты после налога" else "Interest after tax"
    val depositTotal = if (isRussian) "Итого на вкладах" else "Total on deposits"
    val depositsEmpty = if (isRussian) "Вкладов пока нет" else "No deposits yet"
    val depositsHint = if (isRussian) {
        "Вклады учитываются в прогнозе: показывают рост и налог на проценты."
    } else {
        "Deposits are included in the forecast: their growth and the tax on interest."
    }
    val deleteDeposit = if (isRussian) "Удалить вклад" else "Delete deposit"
    val depositSaved = if (isRussian) "Вклад сохранён" else "Deposit saved"
    val manageDeposits = if (isRussian) "Вклады" else "Manage deposits"
    val depositFillName = if (isRussian) "Укажите название вклада" else "Enter the deposit name"
    val depositFillAmount = if (isRussian) "Укажите сумму вклада" else "Enter the deposit amount"
    val depositFillRate = if (isRussian) "Укажите ставку в процентах" else "Enter the rate in percent"
    val depositFillTerm = if (isRussian) {
        "Дата закрытия должна быть позже даты открытия"
    } else {
        "The closing date must be later than the opening date"
    }

    // Налог на проценты по вкладам (ст. 217.1 НК РФ)
    val taxSettings = if (isRussian) "Налог на проценты" else "Tax on interest"
    val taxRate = if (isRussian) "Ставка налога, %" else "Tax rate, %"
    val taxElevatedRate = if (isRussian) "Повышенная ставка, %" else "Elevated rate, %"
    val taxExempt = if (isRussian) "Необлагаемый минимум за год" else "Annual exempt amount"
    val taxThreshold = if (isRussian) "Порог повышенной ставки" else "Elevated rate threshold"
    val taxNote = if (isRussian) {
        "По НК РФ: облагаются проценты свыше 160 000 ₽ в год (1 млн ₽ × максимальная " +
            "ключевая ставка ЦБ), ставка 13%, с превышения 2,4 млн ₽ — 15%."
    } else {
        "Per Russian tax law: interest above 160,000 RUB a year is taxed at 13%, " +
            "above 2.4M RUB at 15%."
    }

    // Настройки
    val settingsTitle = if (isRussian) "Настройки" else "Settings"
    val language = if (isRussian) "Язык" else "Language"
    val russian = "Русский"
    val english = "English"
    val myAccounts = if (isRussian) "Мои счета и карты" else "My accounts & cards"
    val myAccountsHint = if (isRussian) {
        "Номера нужны, чтобы отличать переводы самому себе от переводов друзьям."
    } else {
        "Numbers let the app tell your own transfers apart from friends' transfers."
    }
    val addAccount = if (isRussian) "Добавить счёт" else "Add account"
    val accountLabel = if (isRussian) "Название" else "Label"
    val accountNumber = if (isRussian) "Номер карты или счёта" else "Card or account number"
    val categoryRules = if (isRussian) "Правила категоризации" else "Categorization rules"
    val addRule = if (isRussian) "Добавить правило" else "Add rule"
    val rulePattern = if (isRussian) "Условие" else "Condition"
    val ruleType = if (isRussian) "Тип совпадения" else "Match type"
    val ruleTypeMcc = "MCC"
    val ruleTypeContains = if (isRussian) "Содержит текст" else "Contains text"
    val ruleTypeRegex = if (isRussian) "Регулярное выражение" else "Regular expression"
    val ruleCategory = if (isRussian) "Категория" else "Category"
    val rulesEmpty = if (isRussian) "Правил пока нет" else "No rules yet"
    val categories = if (isRussian) "Категории" else "Categories"
    val budgets = if (isRussian) "Лимиты по категориям" else "Category limits"
    val budgetLimit = if (isRussian) "Лимит в месяц" else "Monthly limit"
    val budgetsEmpty = if (isRussian) "Лимитов пока нет" else "No limits yet"
    val backup = if (isRussian) "Резервная копия" else "Backup"
    val exportJson = if (isRussian) "Экспорт базы (JSON)" else "Export database (JSON)"
    val exportSqlite = if (isRussian) "Экспорт базы (SQLite)" else "Export database (SQLite)"
    val importBackup = if (isRussian) "Импорт из копии" else "Restore from backup"
    val backupDone = if (isRussian) "Резервная копия сохранена" else "Backup saved"
    val backupRestored = if (isRussian) "Данные восстановлены" else "Data restored"
    val importHistory = if (isRussian) "Журнал импортов" else "Import history"
    val rollback = if (isRussian) "Откатить импорт" else "Roll back import"
    val rollbackDone = if (isRussian) "Импорт откатан" else "Import rolled back"
    val about = if (isRussian) "О приложении" else "About"
    val privacyNote = if (isRussian) {
        "Приложение работает полностью офлайн: данные не покидают устройство."
    } else {
        "The app is fully offline: your data never leaves the device."
    }
    val version = if (isRussian) "Версия" else "Version"
    val importBatch = if (isRussian) "Импорт" else "Import"
    val rows = if (isRussian) "строк" else "rows"

    // Форматирование дат
    fun monthName(month: Int): String {
        val ru = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        )
        val en = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )
        return if (isRussian) ru[(month - 1).coerceIn(0, 11)] else en[(month - 1).coerceIn(0, 11)]
    }

    fun monthShort(month: Int): String {
        val ru = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
        val en = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return if (isRussian) ru[(month - 1).coerceIn(0, 11)] else en[(month - 1).coerceIn(0, 11)]
    }

    fun weekdayShort(dayOfWeek: Int): String {
        val ru = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
        val en = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return if (isRussian) ru[(dayOfWeek - 1).coerceIn(0, 6)] else en[(dayOfWeek - 1).coerceIn(0, 6)]
    }
}

val LocalStrings = staticCompositionLocalOf { Strings(AppLanguage.RUSSIAN) }

/** Доступ к строкам текущего языка внутри компонуемой функции. */
val strings: Strings
    @Composable get() = LocalStrings.current
