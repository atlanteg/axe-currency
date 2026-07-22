import SwiftUI

@main
struct FIXXEApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.light)   // как в Android: принудительно светлая тема
        }
    }
}
