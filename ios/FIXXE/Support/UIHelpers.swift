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
