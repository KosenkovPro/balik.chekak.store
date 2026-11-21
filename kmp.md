# Системный промпт / ТЗ для разработки кроссплатформенного приложения «ЧёКак: Балик» (CheKak: Buck)

Разработай полноценный MVP кроссплатформенного мобильного приложения (Android + iOS) **«ЧёКак: Балик»** (английское название: **CheKak: Buck**) на стеке **Kotlin Multiplatform (KMP) + Compose Multiplatform**.

---

## 1. Метаданные проекта и окружение
* **Название приложения (RU):** ЧёКак: Балик
* **Название приложения (EN):** CheKak: Buck
* **Локальный путь репозитория:** `D:\Projects\GitHub\KosenkovPro\balik.chekak.store`
* **GitHub репозиторий:** `BalikChocoStore` (`KosenkovPro`)
* **Package Name / Bundle ID:** `store.chekak.balik`
* **Версия Java / Toolchain:** **JDK 21 LTS** (`jvmToolchain(21)` во всех модулях Gradle и в CI/CD).
* **Целевые платформы сборки:** **Android** (APK / AAB) и **iOS** (`.app` / неподписанный `.ipa` для sideloading через AltStore / SideStore / Sideloadly и последующей публикации в App Store).

---

## 2. Главная идея и ограничения (Privacy-First & Offline-First)
* **Строгий 100% Offline:** Приложение **НЕ должно** запрашивать разрешение `INTERNET` в `AndroidManifest.xml` и не должно выполнять никаких сетевых запросов на iOS. Никаких бэкендов, телеметрии, облачной синхронизации и внешних API.
* **Локальное хранилище:** Все финансовые данные, правила категоризации и история выгрузок хранятся строго локально на устройстве в базе **SQLite** через официальный **Room KMP**.
* **Назначение:** Импорт банковских выписок (CSV, PDF, XLSX) из российских банков (начиная с Райффайзенбанка, с возможностью легко подключать Т-Банк, Сбер, Альфу и др.), защита от дублей при пересекающихся периодах выгрузок, отсечка внутренних переводов между своими счетами, автоматическая категоризация расходов и аналитика по периодам.

---

## 3. Архитектура и технический стек (Kotlin Multiplatform)
* **Структура модулей KMP:**
  * `composeApp` (`commonMain`, `androidMain`, `iosMain`) — общая бизнес-логика, парсеры, база данных Room KMP, движок категоризации и весь общий UI на **Compose Multiplatform (Material 3)**.
  * `iosApp` — нативная обёртка Xcode (`iosApp.xcodeproj`), точка входа SwiftUI/UIKit для Compose, конфигурация `Info.plist` и **iOS Share Extension** для приёма файлов выписок из меню «Поделиться».
* **Навигация и состояние:** Kotlin Coroutines, `StateFlow`, MVVM / Clean Architecture, DI через **Koin** (кроссплатформенный).
* **База данных:** **Room KMP** (`androidx.room:room-runtime` + KSP + `androidx.sqlite:sqlite-bundled`) с единой схемой и миграциями для Android и iOS.
* **Чтение и парсинг файлов в `commonMain` / платформенных реализациях (`expect/actual`):**
  * **CSV:** потоковое чтение байтов с автоопределением кодировки (`UTF-8` с BOM/без BOM и `Windows-1251` — критично для российских банков) и разделителя (`;` или `,`).
  * **PDF:** на Android — `com.tom-roush:pdfbox-android`; на iOS — нативный системный фреймворк `PDFKit` (`PDFDocument`) через `expect/actual` интерфейс `PdfTextExtractor`.
* **Системная интеграция (Share Sheet):**
  * **Android:** `intent-filter` в `AndroidManifest.xml` для `ACTION_SEND` и `ACTION_VIEW` (MIME-типы `text/csv`, `text/comma-separated-values`, `application/pdf`, `application/octet-stream`).
  * **iOS:** поддержка открытия файлов из системного меню «Поделиться» / «Открыть в...» (`CFBundleDocumentTypes` / `UTExportedTypeDeclarations` в `Info.plist` + приём URL файла в `SceneDelegate` / SwiftUI `onOpenURL`).

---

## 4. Схема базы данных (Room KMP Entities)
1. **`AccountEntity`** (Счета и карты):
   * `id: Long` (PK, autoGenerate)
   * `bankName: String` (например, `"Райффайзенбанк"`)
   * `accountName: String` (например, `"Текущий счёт RUB"`)
   * `currency: String` (по умолчанию `"RUB"`)
   * `lastUpdated: Long`
2. **`CategoryEntity`** (Категории):
   * `id: Long` (PK, autoGenerate)
   * `name: String`
   * `iconName: String`
   * `colorHex: String`
   * `isSystem: Boolean`
   * *Предзаполнение при первом создании БД:* Продукты, Кафе и рестораны, Транспорт и такси, Маркетплейсы, Подписки и связь, ЖКХ и дом, Здоровье, Развлечения, Переводы себе, Доходы, Прочее.
3. **`TransactionEntity`** (Операции):
   * `id: Long` (PK, autoGenerate)
   * `fingerprint: String` (`@Index(unique = true)` — SHA-256 хеш от строки `"$bankId|$date|$amount|$currency|$rawDescription"`, исключающий дубли при повторном импорте пересекающихся выписок)
   * `accountId: Long` (FK)
   * `timestamp: Long` (epoch millis)
   * `amount: Long` (в копейках/минорных единицах; отрицательные значения — расход, положительные — доход)
   * `currency: String`
   * `rawDescription: String` (исходное назначение платежа из банка)
   * `merchantName: String` (очищенное название точки/получателя)
   * `mcc: String?` (4-значный MCC-код, если присутствует в выписке)
   * `categoryId: Long` (FK)
   * `isInternalTransfer: Boolean` (перевод между своими счетами; исключается из расчёта расходов и доходов в аналитике)
   * `importBatchId: Long` (FK)
4. **`CategoryRuleEntity`** (Правила автокатегоризации):
   * `id: Long` (PK, autoGenerate)
   * `pattern: String`
   * `matchType: RuleMatchType` (`MCC`, `CONTAINS`, `REGEX`)
   * `categoryId: Long` (FK)
   * `setInternalTransfer: Boolean`
   * `priority: Int`
5. **`ImportBatchEntity`** (История загрузок):
   * `id: Long` (PK, autoGenerate)
   * `fileName: String`
   * `bankName: String`
   * `importedAt: Long`
   * `totalRows: Int`
   * `newRows: Int`
   * `skippedDuplicates: Int`
   * *(Позволяет пользователю откатить ошибочно загруженный файл одним действием).*

---

## 5. Плагинная архитектура банковских парсеров
Реализуй единый интерфейс в `commonMain`:
```kotlin
interface BankStatementParser {
    val bankId: String
    val bankDisplayName: String
    fun canParse(sampleText: String, fileName: String): Boolean
    suspend fun parse(bytes: ByteArray, fileName: String): ParseResult
}
```
* **`RaiffeisenCsvParser` (первый рабочий парсер):**
  * Корректное декодирование `Windows-1251` и `UTF-8`.
  * Парсинг стандартной выписки по счёту из Райффайзен Онлайн (дата операции/проведения, описание операции, сумма в валюте счёта, валюта, остаток; корректная обработка неразрывных пробелов в суммах и запятой как десятичного разделителя).
* **`GenericPdfParser` + `ParserRegistry`:**
  * Реестр парсеров, который автоматически определяет банк по заголовку файла (`canParse`) и позволяет добавлять новые банки (`TBankParser`, `SberParser`, `AlfaParser`) добавлением одного класса без правки UI и БД.

---

## 6. Движок умной категоризации (`CategorizationEngine`)
Каждая новая транзакция при импорте проходит 3-ступенчатый пайплайн:
1. **Детектор переводов самому себе:** поиск паттернов вроде `Перевод СБП`, `Пополнение с карты`, `Перевод между своими счетами` $\rightarrow$ выставляется `isInternalTransfer = true` и категория «Переводы себе».
2. **Пользовательские правила (`CategoryRuleEntity`):** проверка по приоритету (`MCC`, `CONTAINS`, `REGEX`).
3. **Встроенный справочник MCC:** автоматический маппинг стандартных MCC-кодов (`5411` $\rightarrow$ Продукты, `5812`/`5814` $\rightarrow$ Кафе и рестораны, `4121` $\rightarrow$ Транспорт и такси и т.д.). Если совпадений нет — категория «Прочее».

---

## 7. Экраны и UX (Compose Multiplatform)
1. **Дашборд (Главный экран):**
   * Выбор периода (Текущий месяц, Прошлый месяц, Произвольный интервал) и фильтр по банкам/счетам.
   * Карточка итогов периода: Расходы, Доходы, Дельта (строго без учёта `isInternalTransfer`).
   * Интерактивный пончиковый график (`Donut Chart` на Compose `Canvas`) + список категорий с суммами и долями в %.
   * Кнопка **«+ Загрузить выписку»** (системный выбор файла на Android и iOS).
2. **Экран предпросмотра импорта (`ImportPreviewScreen`):**
   * Открывается после выбора файла или получения его через Share Sheet.
   * Отображает: определённый банк, число новых операций, число отфильтрованных дублей, количество нераспознанных строк.
   * Позволяет нажать на любую операцию, выбрать категорию и включить переключатель **«Запомнить правило для похожих операций»** (сразу сохраняет правило в БД и пересчитывает остальные строки в текущем предпросмотре).
3. **Лента операций (`TransactionsScreen`):**
   * Поиск по тексту, фильтрация по категории и статусу «Перевод себе».
   * Редактирование категории и флага `isInternalTransfer` по нажатию на операцию.
4. **Настройки и управление данными (`SettingsScreen`):**
   * Редактор правил категоризации и категорий.
   * Журнал импортов с кнопкой каскадного удаления батча.
   * **Экспорт / Импорт бэкапа** всей локальной базы (JSON / `.sqlite`) через системный диалог сохранения файлов на Android и iOS.

---

## 8. Сборка проекта под Android и iOS без MacBook (GitHub Actions CI/CD)
Проект **обязательно должен собираться под обе платформы (Android и iOS)** в облачном CI/CD через GitHub Actions с использованием официальных облачных раннеров GitHub (`ubuntu-latest` и `macos-latest`):
* **Документация раннеров GitHub:** https://docs.github.com/en/actions/using-github-hosted-runners/using-github-hosted-runners/about-github-hosted-runners#standard-github-hosted-runners-for-public-repositories
* **Спецификация образов `macos-latest`:** https://github.com/actions/runner-images#available-images

Обязательно включи в генерацию готовый файл `.github/workflows/build.yml` со следующими шагами:
1. **`build-android` (на `ubuntu-latest`):** установка **JDK 21 (`temurin`)**, сборка `:composeApp:assembleRelease` и публикация артефакта `CheKak-Buck-Android` (`.apk`).
2. **`build-ios` (на `macos-latest`):** установка **JDK 21 (`temurin`)**, сборка `iosApp/iosApp.xcodeproj` через `xcodebuild` (`-sdk iphoneos`, `CODE_SIGNING_ALLOWED=NO`), упаковка `iosApp.app` в `Payload/` $\rightarrow$ `CheKak-Buck-unsigned.ipa` и публикация артефакта `CheKak-Buck-iOS`.

---

## 9. Требования к формату выдачи результата
Выдай проект поэтапно, с полным компилируемым кодом без `TODO`-заглушек в бизнес-логике:
1. Полная структура директорий KMP-проекта (`composeApp`, `iosApp`, `.github/workflows/build.yml`) и файлы конфигурации Gradle (`settings.gradle.kts`, `gradle/libs.versions.toml`, `composeApp/build.gradle.kts` с настройкой Room KMP, KSP и `jvmToolchain(21)`).
2. Конфигурация платформ: `AndroidManifest.xml` (без `INTERNET`, с `ACTION_SEND` / `ACTION_VIEW`) и файлы `iosApp` (`Info.plist`, `iOSApp.swift`, конфигурация Xcode-проекта для сборки без подписи на `macos-latest`).
3. Слой данных: Room KMP Entities, DAO, `AppDatabase` (с предзаполнением категорий и базовых MCC-правил), платформенные `expect/actual` билдеры базы данных для Android и iOS.
4. Ядро бизнес-логики: `RaiffeisenCsvParser`, `CategorizationEngine`, кроссплатформенный SHA-256 генератор `fingerprint` и `PdfTextExtractor` (`PDFBox-Android` для Android + `PDFKit` для iOS).
5. UI и ViewModel на Compose Multiplatform: Дашборд с `DonutChart`, экран предпросмотра импорта с созданием правил на лету, лента транзакций и экран настроек/бэкапов.
6. Готовый файл `.github/workflows/build.yml` для автоматической сборки APK и unsigned IPA.