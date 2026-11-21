import UIKit
import UniformTypeIdentifiers

/// Share Extension «ЧёКак: Балик».
///
/// Принимает файл выписки (CSV / XLSX / PDF) из системного меню «Поделиться» или
/// «Открыть в…», копирует его в общий контейнер App Group под именем
/// `import-<UUID>.<ext>` и открывает основное приложение ссылкой
/// `chekak://import?path=<имя файла>`.
///
/// Расширение работает полностью офлайн: ни сети, ни телеметрии.
final class ShareViewController: UIViewController {

    /// Должен совпадать с `com.apple.security.application-groups` во всех targets
    /// и с `APP_GROUP_ID` в Kotlin-части.
    private static let appGroupIdentifier = "group.store.chekak.balik"

    private let statusLabel: UILabel = {
        let label = UILabel()
        label.text = "Подготовка импорта…"
        label.textAlignment = .center
        label.numberOfLines = 0
        label.textColor = .secondaryLabel
        label.font = .preferredFont(forTextStyle: .body)
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()

    private var isFinished = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        view.addSubview(statusLabel)
        NSLayoutConstraint.activate([
            statusLabel.leadingAnchor.constraint(equalTo: view.layoutMarginsGuide.leadingAnchor),
            statusLabel.trailingAnchor.constraint(equalTo: view.layoutMarginsGuide.trailingAnchor),
            statusLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])

        importSharedFile()
    }

    // MARK: - Импорт

    private func importSharedFile() {
        let providers = (extensionContext?.inputItems as? [NSExtensionItem] ?? [])
            .flatMap { $0.attachments ?? [] }

        guard !providers.isEmpty else {
            finish(withError: "Нет данных для импорта")
            return
        }

        // 1) Файл передан ссылкой на файл (public.file-url).
        if let provider = providers.first(where: {
            $0.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier)
        }) {
            provider.loadFileRepresentation(forTypeIdentifier: UTType.fileURL.identifier) { [weak self] url, error in
                guard let self else { return }
                guard let url else {
                    self.finish(withError: error?.localizedDescription ?? "Не удалось получить файл")
                    return
                }
                // URL действует только внутри этого блока, поэтому копируем сразу.
                self.store(sourceURL: url, suggestedName: provider.suggestedName)
            }
            return
        }

        // 2) Данные без файловой ссылки (public.data и производные типы).
        if let provider = providers.first(where: { self.isDataProvider($0) }) {
            let typeIdentifier = provider.registeredTypeIdentifiers.first { identifier in
                UTType(identifier)?.conforms(to: .data) == true
            } ?? UTType.data.identifier

            provider.loadDataRepresentation(forTypeIdentifier: typeIdentifier) { [weak self] data, error in
                guard let self else { return }
                guard let data else {
                    self.finish(withError: error?.localizedDescription ?? "Не удалось получить данные")
                    return
                }
                self.store(data: data, suggestedName: provider.suggestedName, typeIdentifier: typeIdentifier)
            }
            return
        }

        finish(withError: "Поддерживаются только файлы выписок")
    }

    private func isDataProvider(_ provider: NSItemProvider) -> Bool {
        provider.registeredTypeIdentifiers.contains { identifier in
            UTType(identifier)?.conforms(to: .data) == true
        }
    }

    // MARK: - Контейнер App Group

    private func store(sourceURL: URL, suggestedName: String?) {
        guard let container = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: Self.appGroupIdentifier
        ) else {
            finish(withError: "Нет доступа к контейнеру App Group")
            return
        }

        let fileExtension = self.fileExtension(suggestedName: suggestedName, fallback: sourceURL.pathExtension)
        let fileName = makeImportFileName(extension: fileExtension)
        let destination = container.appendingPathComponent(fileName)

        do {
            if FileManager.default.fileExists(atPath: destination.path) {
                try FileManager.default.removeItem(at: destination)
            }
            try FileManager.default.copyItem(at: sourceURL, to: destination)
            openMainApp(with: fileName)
        } catch {
            finish(withError: "Не удалось сохранить файл: \(error.localizedDescription)")
        }
    }

    private func store(data: Data, suggestedName: String?, typeIdentifier: String) {
        guard let container = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: Self.appGroupIdentifier
        ) else {
            finish(withError: "Нет доступа к контейнеру App Group")
            return
        }

        let fileExtension = self.fileExtension(
            suggestedName: suggestedName,
            fallback: UTType(typeIdentifier)?.preferredFilenameExtension ?? ""
        )
        let fileName = makeImportFileName(extension: fileExtension)
        let destination = container.appendingPathComponent(fileName)

        do {
            try data.write(to: destination, options: .atomic)
            openMainApp(with: fileName)
        } catch {
            finish(withError: "Не удалось сохранить файл: \(error.localizedDescription)")
        }
    }

    /// Имя файла в контейнере: `import-<UUID>.<ext>` — так же, как это делает основное приложение.
    private func makeImportFileName(extension fileExtension: String) -> String {
        let ext = fileExtension.isEmpty ? "bin" : fileExtension
        return "import-\(UUID().uuidString).\(ext)"
    }

    private func fileExtension(suggestedName: String?, fallback: String) -> String {
        if let suggestedName, !suggestedName.isEmpty {
            let ext = URL(fileURLWithPath: suggestedName).pathExtension
            if !ext.isEmpty {
                return ext
            }
        }
        return fallback.isEmpty ? "bin" : fallback
    }

    // MARK: - Открытие основного приложения

    private func openMainApp(with fileName: String) {
        var components = URLComponents()
        components.scheme = "chekak"
        components.host = "import"
        components.queryItems = [URLQueryItem(name: "path", value: fileName)]

        guard let url = components.url else {
            finish(withError: "Не удалось сформировать ссылку")
            return
        }

        statusLabel.text = "Открываем «ЧёКак: Балик»…"
        extensionContext?.open(url) { [weak self] _ in
            self?.finish()
        }
    }

    // MARK: - Завершение

    private func finish(withError message: String? = nil) {
        guard !isFinished else { return }
        isFinished = true

        guard let message else {
            extensionContext?.completeRequest(returningItems: nil)
            return
        }

        statusLabel.text = message
        // Даём пользователю увидеть причину, прежде чем расширение закроется.
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
            self?.extensionContext?.completeRequest(returningItems: nil)
        }
    }
}
