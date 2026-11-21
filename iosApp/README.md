# iOS-часть «ЧёКак: Балик» (`store.chekak.balik`)

Нативная iOS-обёртка над общим Kotlin-кодом. Весь UI рисует Compose Multiplatform
(`composeApp/src/commonMain/kotlin/store/chekak/balik/App.kt` → `ComposeApp.MainViewControllerKt.AppView()`),
Swift-слой отвечает только за системную интеграцию: файлы выписок, Share Extension, виджет и deep links.

## Требования

| Что | Версия |
|---|---|
| macOS | 13+ (Ventura) |
| Xcode | 15+ (проверено на 15.x, `compatibilityVersion = "Xcode 15.0"`) |
| Deployment target | iOS 15.0 |
| JDK | 21 LTS — нужен Gradle для сборки `ComposeApp.framework` |
| Kotlin | 2.2.10 (совпадает с `gradle/libs.versions.toml`) |
| Compose Multiplatform | 1.9.0 |

Сборка iOS возможна **только на macOS** — на других ОС Xcode недоступен.
Приложение полностью офлайн: сетевых разрешений, ATS-исключений и сетевых вызовов нет.

## Состав

```
iosApp/
├── Configuration/Config.xcconfig      # base configuration для всех таргетов (генерируется Gradle)
├── build.gradle.kts                   # модуль Gradle: iOS-цели + задача xcodeConfig
├── iosApp.xcodeproj/                  # Xcode-проект с тремя таргетами
├── iosApp/                            # приложение (SwiftUI + мост к Compose)
│   ├── iOSApp.swift                   # @main App, обработка onOpenURL
│   ├── MainViewController.swift       # UIViewControllerRepresentable -> ComposeUIViewController
│   ├── ImportRequestHandler.swift     # deep links chekak:// и открытые файлы
│   ├── Info.plist                     # документы (csv/xlsx/pdf), URL-схема chekak
│   ├── iOSApp.entitlements           # App Group group.store.chekak.balik
│   └── Assets.xcassets/               # AppIcon + AccentColor
├── ShareExtension/                    # приём выписок из меню «Поделиться»
└── CheKakWidget/                      # WidgetKit: итоги месяца
```

Таргеты Xcode-проекта:

| Таргет | Bundle ID | Назначение |
|---|---|---|
| `iosApp` | `store.chekak.balik` | приложение (SwiftUI → Compose) |
| `ShareExtension` | `store.chekak.balik.ShareExtension` | приём файла выписки из системного меню «Поделиться» |
| `CheKakWidget` | `store.chekak.balik.Widget` | виджет с итогами месяца |

Все три таргета используют общий контейнер App Group `group.store.chekak.balik`,
где лежит база Room KMP (`chekak.sqlite`) и файлы, ожидающие импорта.

## Подготовка

1. **Включить модуль в Gradle.** В `settings.gradle.kts` корня репозитория добавьте
   `include(":iosApp")` — без этого задача `xcodeConfig` не будет доступна.
   Версии плагинов уже зафиксированы в `iosApp/build.gradle.kts`
   (`kotlin("multiplatform") version "2.2.10"`, `org.jetbrains.kotlin.plugin.compose` 2.2.10).

2. **Сгенерировать xcconfig** (опционально, файл уже лежит в репозитории):

   ```bash
   ./gradlew :iosApp:xcodeConfig
   ```

   Задача пересобирает `iosApp/Configuration/Config.xcconfig` из Gradle-модели:
   идентификаторы приложения, `FRAMEWORK_SEARCH_PATHS` (путь к `ComposeApp.framework`),
   `$(CONFIGURATION)`/`$(SDK_NAME)`/`$(ARCHS)` и пути к Kotlin/Native toolchain.
   Абсолютные пути к toolchain машинозависимы, поэтому в закоммиченном файле они пустые —
   после запуска задачи они заполнятся. Если не хотите получать «грязный» diff, добавьте
   `git update-index --skip-worktree iosApp/Configuration/Config.xcconfig`.

3. **Открыть проект:**

   ```bash
   open iosApp/iosApp.xcodeproj
   ```

   В Xcode выберите схему `iosApp` и запустите на симуляторе или устройстве.
   При первом build phase «Compile Kotlin Framework» сам вызовет
   `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` — отдельные шаги не нужны.

## Сборка из командной строки

```bash
# симулятор
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 15' build

# устройство без подписи (unsigned .app -> .ipa)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphoneos -configuration Release CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build
```

## App Group в Apple Developer аккаунте

1. [Apple Developer → Certificates, Identifiers & Profiles → Identifiers → App Groups](https://developer.apple.com/account/resources/identifiers/list/applicationGroup)
   → создайте группу `group.store.chekak.balik`.
2. Зарегистрируйте три bundle id: `store.chekak.balik`, `store.chekak.balik.ShareExtension`,
   `store.chekak.balik.Widget` (Explicit).
3. В **каждом** из трёх идентификаторов отметьте App Group `group.store.chekak.balik`.
4. Пересоздайте provisioning profiles: без группы в профиле entitlement будет «App Groups
   are not supported by the current provisioning profile», а контейнер вернёт `nil`.
5. В Xcode укажите команду разработчика: `TEAM_ID` в `iosApp/Configuration/Config.xcconfig`
   (или Signing & Capabilities → Team для каждого таргета).

Без App Group приложение запустится, но Share Extension не сможет передать файл,
а виджет не увидит сводку — обе функции деградируют мягко (показывают прочерк / ошибку),
без падений.

## Запуск Share Extension

1. Соберите и установите приложение на устройство (Share Extension нельзя запустить
   отдельно — он появляется только в списке «Поделиться»).
2. Откройте «Файлы» → выписка `.csv`/`.xlsx`/`.pdf` → «Поделиться» → «ЧёКак: Балик».
   Расширение скопирует файл в контейнер App Group как `import-<UUID>.<ext>`
   и откроет приложение ссылкой `chekak://import?path=import-<UUID>.<ext>`.
3. Приложение положит файл в очередь импорта (см. контракт ниже) и откроет предпросмотр импорта.

## Контракт с Kotlin-частью

**Точка входа.** Swift вызывает `ComposeUIViewController { MainViewControllerKt.AppView() }`.
`MainViewControllerKt` — сгенерированный Kotlin/Native фасад top-level функций, его имя
зависит от **имени файла** с точкой входа. Kotlin-файл
`composeApp/src/commonMain/kotlin/store/chekak/balik/MainViewController.kt` назван именно так,
поэтому фасад — `MainViewControllerKt`, и Swift-код менять не нужно.
Если переименовать файл в `App.kt`, фасад станет `AppKt` и вызов в `MainViewController.swift`
нужно заменить на `AppKt.AppView()`.

Swift и Kotlin общаются через контейнер App Group — сетевых каналов нет.

**Очередь импорта.** Swift пишет в `UserDefaults` группы `group.store.chekak.balik`
ключ `chekak.pendingImports` (массив имён файлов **внутри контейнера**).
Kotlin-часть читает очередь и запускает импорт:

```kotlin
val defaults = NSUserDefaults(suiteName = "group.store.chekak.balik")
val pending = defaults?.stringArrayForKey("chekak.pendingImports") ?: emptyList()
```

**Сводка для виджета.** Виджет читает из контейнера файл `widget-summary.json`:

```json
{ "month": "Ноябрь 2026", "expenses": 52340.50, "income": 90000.00 }
```

Если файла нет или он не читается — виджет показывает прочерк.

**Имя файла.** Файлы выписок копируются в контейнер как `import-<UUID>.<ext>`.

## Замечания по сборке

* `ComposeApp.framework` — **статический** фреймворк, его собирает модуль `:composeApp`
  (`binaries.framework { baseName = "ComposeApp"; isStatic = true }`). Xcode линкует его через
  `FRAMEWORK_SEARCH_PATHS`; копирование в Frameworks задано фазой «Embed Frameworks»
  (Copy Files, dstSubfolderSpec = 10) без атрибута `CodeSignOnCopy` — статический фреймворк не подписывается.
* Share Extension и виджет намеренно **не** тянут `ComposeApp.framework`: это чистый Swift,
  а фреймворк появляется только после build phase приложения. Копирование фреймворка
  в расширения ломало бы параллельную сборку.
* `ENABLE_USER_SCRIPT_SANDBOXING = NO` включён на уровне проекта — иначе Xcode 15+
  не пустит build phase в Gradle.
* `objectVersion = 56`, `compatibilityVersion = "Xcode 15.0"`.
