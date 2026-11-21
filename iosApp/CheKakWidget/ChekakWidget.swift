import Foundation
import SwiftUI
import UIKit
import WidgetKit

/// Сводка по месяцу, которую Kotlin-часть кладёт в контейнер App Group
/// файлом `widget-summary.json` (JSON, UTF-8).
///
/// ```json
/// { "month": "Ноябрь 2026", "expenses": 52340.50, "income": 90000.00 }
/// ```
///
/// Если файла нет или он не читается — виджет показывает прочерк.
struct MonthlySummary: Decodable {
    let month: String?
    let expenses: Decimal?
    let income: Decimal?
}

/// Одна запись таймлайна виджета.
struct CheKakWidgetEntry: TimelineEntry {
    let date: Date
    let month: String
    let expenses: String
    let income: String

    /// Заглушка для превью в галерее виджетов.
    static let placeholder = CheKakWidgetEntry(
        date: Date(),
        month: "Текущий месяц",
        expenses: "—",
        income: "—"
    )
}

/// Поставщик данных виджета: читает сводку из контейнера App Group, сети не использует.
struct CheKakWidgetProvider: TimelineProvider {

    /// Должен совпадать с `com.apple.security.application-groups` во всех targets
    /// и с `APP_GROUP_ID` в Kotlin-части.
    private static let appGroupIdentifier = "group.store.chekak.balik"
    private static let summaryFileName = "widget-summary.json"

    func placeholder(in context: Context) -> CheKakWidgetEntry {
        .placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (CheKakWidgetEntry) -> Void) {
        completion(loadEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<CheKakWidgetEntry>) -> Void) {
        let entry = loadEntry()
        // Данные меняются только после импорта выписки, поэтому обновляемся раз в час.
        let refreshDate = Calendar.current.date(byAdding: .hour, value: 1, to: Date())
            ?? Date().addingTimeInterval(3600)
        completion(Timeline(entries: [entry], policy: .after(refreshDate)))
    }

    // MARK: - Чтение данных

    private func loadEntry() -> CheKakWidgetEntry {
        guard let summary = loadSummary() else {
            return CheKakWidgetEntry(date: Date(), month: "Нет данных", expenses: "—", income: "—")
        }
        return CheKakWidgetEntry(
            date: Date(),
            month: summary.month ?? currentMonthTitle(),
            expenses: Self.format(summary.expenses),
            income: Self.format(summary.income)
        )
    }

    private func loadSummary() -> MonthlySummary? {
        guard let container = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: Self.appGroupIdentifier
        ) else {
            return nil
        }
        let fileURL = container.appendingPathComponent(Self.summaryFileName)
        guard let data = try? Data(contentsOf: fileURL) else {
            return nil
        }
        return try? JSONDecoder().decode(MonthlySummary.self, from: data)
    }

    private func currentMonthTitle() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.dateFormat = "LLLL yyyy"
        return formatter.string(from: Date())
    }

    /// Форматирует сумму в рублях; `nil` превращается в прочерк.
    static func format(_ value: Decimal?) -> String {
        guard let value else { return "—" }
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "ru_RU")
        formatter.numberStyle = .decimal
        formatter.maximumFractionDigits = 2
        formatter.minimumFractionDigits = 0
        let number = formatter.string(from: NSDecimalNumber(decimal: value)) ?? "\(value)"
        return "\(number) ₽"
    }
}

/// Оформление виджета «итоги месяца».
struct CheKakWidgetView: View {

    let entry: CheKakWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(entry.month)
                .font(.caption)
                .foregroundColor(.secondary)
                .lineLimit(1)

            row(title: "Расходы", value: entry.expenses)
            row(title: "Доходы", value: entry.income)
        }
        .padding()
        .widgetBackground()
    }

    private func row(title: String, value: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(title)
                .font(.caption2)
                .foregroundColor(.secondary)
            Spacer(minLength: 0)
            Text(value)
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
    }
}

/// Фон виджета: с iOS 17 — системный `containerBackground`, раньше — обычный цвет.
private struct WidgetBackground: ViewModifier {

    func body(content: Content) -> some View {
        if #available(iOS 17.0, *) {
            content.containerBackground(.fill.tertiary, for: .widget)
        } else {
            content.background(Color(UIColor.secondarySystemBackground))
        }
    }
}

private extension View {
    func widgetBackground() -> some View {
        modifier(WidgetBackground())
    }
}

/// Виджет «ЧёКак: Балик» с итогами месяца.
@main
struct CheKakWidget: Widget {

    static let kind = "CheKakWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: CheKakWidgetProvider()) { entry in
            CheKakWidgetView(entry: entry)
        }
        .configurationDisplayName("ЧёКак: Балик")
        .description("Итоги текущего месяца по импортированным выпискам")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
