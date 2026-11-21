import SwiftUI
import UIKit

// `ComposeApp` — фреймворк, собираемый модулем `:composeApp`.
// В разных версиях Compose Multiplatform символ `ComposeUIViewController` лежит
// либо внутри него, либо в отдельном одноимённом модуле — поддерживаем оба случая.
#if canImport(ComposeUIViewController)
import ComposeUIViewController
#endif
import ComposeApp

/// Мост между SwiftUI и Compose Multiplatform.
///
/// Kotlin-часть сама поднимает весь интерфейс (тема Material 3, онбординг,
/// дашборд, настройки), поэтому Swift-код не содержит ни одного экрана.
///
/// ВАЖНО: имя `MainViewControllerKt` — это сгенерированный Kotlin/Native фасад
/// top-level функций, и оно зависит от **имени файла** с точкой входа.
/// Kotlin-файл `composeApp/src/commonMain/kotlin/store/chekak/balik/MainViewController.kt`
/// назван именно так, поэтому фасад — `MainViewControllerKt`.
/// Если переименовать файл в `App.kt`, фасад станет `AppKt`
/// и вызов придётся заменить на `AppKt.AppView()`.
struct ComposeRootView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        ComposeUIViewController { MainViewControllerKt.AppView() }
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Состояние полностью хранится в Kotlin (StateFlow), обновлять нечего.
    }
}
