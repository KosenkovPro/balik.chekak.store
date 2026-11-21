import Foundation
import UIKit

/// Общий контейнер приложения, общий для `iosApp`, `ShareExtension` и `CheKakWidget`.
///
/// Kotlin-часть читает базу Room KMP из этого же контейнера
/// (`NSFileManager.containerURL(forSecurityApplicationGroupIdentifier:)`).
enum AppGroup {

    /// Идентификатор App Group. Должен совпадать с `com.apple.security.application-groups`
    /// во всех entitlements и с `APP_GROUP_ID` в `composeApp/src/iosMain/.../DatabaseProvider.ios.kt`.
    static let identifier = "group.store.chekak.balik"

    /// Ключ в `UserDefaults` группы: очередь файлов, ожидающих импорта.
    ///
    /// Значение — массив строк с именами файлов **внутри контейнера App Group**.
    /// Kotlin-часть читает его так:
    /// ```kotlin
    /// val defaults = NSUserDefaults(suiteName = "group.store.chekak.balik")
    /// val pending = defaults?.stringArrayForKey("chekak.pendingImports") ?: emptyList()
    /// ```
    enum Defaults {
        static let pendingImportsKey = "chekak.pendingImports"
    }

    /// Корневая папка контейнера App Group (`nil`, если entitlement не выдан).
    static var containerURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier)
    }

    /// `UserDefaults` в общем контейнере (`nil`, если entitlement не выдан).
    static var defaults: UserDefaults? {
        UserDefaults(suiteName: identifier)
    }
}

/// Обработчик системной интеграции: файлы выписок, открытые из «Файлов»/«Поделиться»,
/// и deep links `chekak://import?path=<имя файла>` от Share Extension.
///
/// Файлы всегда копируются в контейнер App Group под именем `import-<UUID>.<ext>`,
/// чтобы Kotlin-часть читала их по обычному пути без security-scoped URL.
final class ImportRequestHandler {

    static let shared = ImportRequestHandler()

    private init() {}

    // MARK: - Точка входа

    /// Разбирает входящий URL: deep link `chekak://` либо открытый файл `file://`.
    func handle(url: URL) {
        switch url.scheme?.lowercased() {
        case "chekak":
            handleDeepLink(url)
        case "file":
            handleOpenedFile(url)
        default:
            NSLog("ЧёКак: игнорируем URL со схемой %@", url.scheme ?? "")
        }
    }

    // MARK: - Deep link

    /// `chekak://import?path=import-<UUID>.csv` — файл уже лежит в контейнере App Group,
    /// его нужно только поставить в очередь импорта.
    private func handleDeepLink(_ url: URL) {
        guard url.host?.lowercased() == "import" else {
            NSLog("ЧёКак: неизвестная команда %@", url.host ?? "")
            return
        }
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        let fileName = components?.queryItems?.first { $0.name == "path" }?.value
        guard let fileName, !fileName.isEmpty else {
            NSLog("ЧёКак: в ссылке нет параметра path")
            return
        }
        enqueue(fileName: fileName)
    }

    // MARK: - Открытый файл

    /// Копирует открытый пользователем файл в контейнер App Group и ставит его в очередь.
    private func handleOpenedFile(_ url: URL) {
        guard url.isFileURL else { return }

        let isSecurityScoped = url.startAccessingSecurityScopedResource()
        defer {
            if isSecurityScoped {
                url.stopAccessingSecurityScopedResource()
            }
        }

        guard let container = AppGroup.containerURL else {
            NSLog("ЧёКак: нет доступа к контейнеру App Group %@", AppGroup.identifier)
            return
        }

        let fileName = makeImportFileName(extension: url.pathExtension)
        let destination = container.appendingPathComponent(fileName)

        do {
            if FileManager.default.fileExists(atPath: destination.path) {
                try FileManager.default.removeItem(at: destination)
            }
            try FileManager.default.copyItem(at: url, to: destination)
            enqueue(fileName: fileName)
        } catch {
            NSLog("ЧёКак: не удалось скопировать файл в контейнер App Group: %@", error.localizedDescription)
        }
    }

    // MARK: - Очередь импорта

    /// Кладёт файл в очередь импорта в общем `UserDefaults`.
    /// Kotlin-часть забирает очередь и запускает импорт выписки.
    func enqueue(fileName: String) {
        guard let defaults = AppGroup.defaults else {
            NSLog("ЧёКак: нет доступа к UserDefaults группы %@", AppGroup.identifier)
            return
        }
        var pending = defaults.stringArray(forKey: AppGroup.Defaults.pendingImportsKey) ?? []
        guard !pending.contains(fileName) else { return }
        pending.append(fileName)
        defaults.set(pending, forKey: AppGroup.Defaults.pendingImportsKey)
    }

    // MARK: - Вспомогательное

    /// Имя файла в контейнере: `import-<UUID>.<ext>`.
    func makeImportFileName(extension fileExtension: String) -> String {
        let ext = fileExtension.isEmpty ? "bin" : fileExtension
        return "import-\(UUID().uuidString).\(ext)"
    }
}
