import SwiftUI

/// Точка входа приложения «ЧёКак: Балик».
///
/// Swift-слой намеренно тонкий: весь UI рисует Compose Multiplatform
/// (`ComposeApp.MainViewControllerKt.AppView()`), а нативная часть отвечает только за
/// системную интеграцию — приём файлов выписок и deep links вида `chekak://import?path=...`.
@main
struct iOSApp: App {

    var body: some Scene {
        WindowGroup {
            ComposeRootView()
                // Compose управляет safe area и клавиатурой самостоятельно,
                // поэтому дополнительных модификаторов здесь не требуется.
                .onOpenURL { url in
                    // Срабатывает и для `chekak://import?...` (Share Extension),
                    // и для `file://...` (открытие выписки через «Открыть в…»).
                    ImportRequestHandler.shared.handle(url: url)
                }
        }
    }
}
