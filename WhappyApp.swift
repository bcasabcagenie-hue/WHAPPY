import SwiftUI

@main
struct WhappyApp: App {
    @StateObject private var store = WhappyStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .tint(.whappyBlue)
                .onOpenURL { store.handleWhappyURL($0) }
        }
    }
}
