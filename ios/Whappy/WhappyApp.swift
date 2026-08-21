import SwiftUI

@main
struct WhappyApp: App {
    @StateObject private var store = WhappyStore()

    var body: some Scene {
        WindowGroup {
            Group {
                if store.firebaseSessionLoading {
                    ProgressView("Connexion sécurisée à WHAPPY…")
                } else if store.firebaseUserID == nil {
                    WhappyPhoneSignInView()
                } else {
                    ContentView()
                        .onOpenURL { store.handleWhappyURL($0) }
                }
            }
            .environmentObject(store)
            .tint(.whappyBlue)
        }
    }
}
