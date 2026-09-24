import SwiftUI

extension Color {
    init(hex: String) {
        var v: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&v)
        self.init(red: Double((v >> 16) & 0xFF) / 255,
                  green: Double((v >> 8) & 0xFF) / 255,
                  blue: Double(v & 0xFF) / 255)
    }

    static let brandBlue = Color(hex: "1565C0")
    static let brandGreen = Color(hex: "2E7D32")
    static let bgGray = Color(hex: "F2F3F5")
}

enum AppInfo {
    static var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
    }
}

/// Аргументы запуска для автоматической съёмки скриншотов магазина.
/// Только в DEBUG — в релизной сборке всегда «пусто».
enum ScreenshotArgs {
#if DEBUG
    private static var args: [String] { ProcessInfo.processInfo.arguments }
    static var openAdd: Bool { args.contains("-screen-add") }
    static var openSettings: Bool { args.contains("-screen-settings") }
    static var openInfo: Bool { args.contains("-screen-info") }
    static var initialQuery: String {
        guard let i = args.firstIndex(of: "-screen-query"), i + 1 < args.count else { return "" }
        return args[i + 1]
    }
    /// индекс источника для чипа-фильтра, -1 = «все»
    static var initialFilter: Int {
        guard let i = args.firstIndex(of: "-screen-filter"), i + 1 < args.count else { return -1 }
        return Int(args[i + 1]) ?? -1
    }
#else
    static var openAdd: Bool { false }
    static var openSettings: Bool { false }
    static var openInfo: Bool { false }
    static var initialQuery: String { "" }
    static var initialFilter: Int { -1 }
#endif
}

extension View {
    /// Ограничивает содержимое читаемой шириной и центрирует его.
    /// На iPhone ничего не меняет (экран уже уже лимита), на iPad не даёт
    /// строкам растянуться через весь 13" экран.
    func readableWidth(_ limit: CGFloat = 640) -> some View {
        frame(maxWidth: limit).frame(maxWidth: .infinity)
    }
}

extension View {
    /// scrollContentBackground(.hidden) появился в iOS 16; на iOS 15 фон
    /// List чистится через UITableView.appearance() (см. FIXXEApp.init)
    @ViewBuilder func compatHideListBackground() -> some View {
        if #available(iOS 16.0, *) {
            self.scrollContentBackground(.hidden)
        } else {
            self
        }
    }
}
