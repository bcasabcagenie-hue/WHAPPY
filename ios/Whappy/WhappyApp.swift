import FirebaseCore
import FirebaseMessaging
import SwiftUI
import UIKit
import UserNotifications

let wapiPushTokenDidChange = Notification.Name("wapi.push-token-did-change")
let wapiPushDidOpen = Notification.Name("wapi.push-did-open")
let wapiPushDidDeclineCall = Notification.Name("wapi.push-did-decline-call")
let wapiPendingDeclineCallKey = "wapi.pending-decline-call"
private let wapiDirectCallCategory = "WAPI_DIRECT_CALL"
private let wapiAcceptCallAction = "WAPI_ACCEPT_CALL"
private let wapiDeclineCallAction = "WAPI_DECLINE_CALL"

final class WapiAppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        let accept = UNNotificationAction(identifier: wapiAcceptCallAction, title: "Accepter", options: [.foreground])
        let decline = UNNotificationAction(identifier: wapiDeclineCallAction, title: "Refuser", options: [.destructive])
        UNUserNotificationCenter.current().setNotificationCategories([
            UNNotificationCategory(identifier: wapiDirectCallCategory, actions: [accept, decline], intentIdentifiers: [], options: [.customDismissAction])
        ])
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async { application.registerForRemoteNotifications() }
        }
        return true
    }

    func application(_: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func messaging(_: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken, !fcmToken.isEmpty else { return }
        UserDefaults.standard.set(fcmToken, forKey: "wapi.ios.fcm-token")
        NotificationCenter.default.post(name: wapiPushTokenDidChange, object: nil)
    }

    func userNotificationCenter(_: UNUserNotificationCenter, willPresent _: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound, .badge])
    }

    func userNotificationCenter(_: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        if let deepLink = response.notification.request.content.userInfo["deepLink"] as? String {
            if response.actionIdentifier == wapiDeclineCallAction {
                // À froid, WhappyStore peut ne pas exister encore. L'action
                // est conservée jusqu'à la restauration de l'authentification.
                UserDefaults.standard.set(deepLink, forKey: wapiPendingDeclineCallKey)
                NotificationCenter.default.post(name: wapiPushDidDeclineCall, object: deepLink)
            } else {
                // Opening an incoming call only displays its consent screen;
                // microphone/camera remain off until the user taps Accepter.
                NotificationCenter.default.post(name: wapiPushDidOpen, object: deepLink)
            }
        }
        completionHandler()
    }
}

@main
struct WhappyApp: App {
    @UIApplicationDelegateAdaptor(WapiAppDelegate.self) private var appDelegate
    @StateObject private var store = WhappyStore()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            Group {
                if store.firebaseSessionLoading {
                    ProgressView("Connexion sécurisée à WAPI…")
                } else if store.firebaseUserID == nil {
                    WhappyPhoneSignInView()
                } else {
                    ContentView()
                        .onOpenURL { store.handleWhappyURL($0) }
                }
            }
            .environmentObject(store)
            .tint(.whappyBlue)
            .onChange(of: scenePhase) { _, phase in
                switch phase {
                case .active: store.setFirebasePresence(online: true)
                case .background: store.setFirebasePresence(online: false)
                case .inactive: break
                @unknown default: break
                }
            }
        }
    }
}
