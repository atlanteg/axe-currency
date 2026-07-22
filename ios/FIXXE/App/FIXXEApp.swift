import SwiftUI

@main
struct FIXXEApp: App {
    init() {
        // iOS 15: очистка фона List (на iOS 16+ это делает compatHideListBackground)
        if #unavailable(iOS 16.0) {
            UITableView.appearance().backgroundColor = .clear
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.light)   // как в Android: принудительно светлая тема
        }
    }
}
