import FirebaseCore
import FirebaseMessaging
import AVFoundation
import AudioToolbox
import SwiftUI
import UIKit
import UserNotifications

let wapiPushTokenDidChange = Notification.Name("wapi.push-token-did-change")
let wapiPushDidOpen = Notification.Name("wapi.push-did-open")
let wapiPushDidDeclineCall = Notification.Name("wapi.push-did-decline-call")
let wapiPushDidEndCall = Notification.Name("wapi.push-did-end-call")
let wapiPendingDeclineCallKey = "wapi.pending-decline-call"
private let wapiDirectCallCategory = "WAPI_DIRECT_CALL"
private let wapiAcceptCallAction = "WAPI_ACCEPT_CALL"
private let wapiDeclineCallAction = "WAPI_DECLINE_CALL"

@MainActor
enum WapiOrientation {
    static var supported: UIInterfaceOrientationMask = .portrait

    static func request(_ mask: UIInterfaceOrientationMask) {
        supported = mask
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive }) else { return }
        scene.windows.first(where: \.isKeyWindow)?.rootViewController?
            .setNeedsUpdateOfSupportedInterfaceOrientations()
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: mask)) { _ in }
    }
}

enum WapiSounds {
    private static var lastTypingAt = Date.distantPast
    private static var lastInterfaceTapAt = Date.distantPast
    private static var gamePlayers: [String: [AVAudioPlayer]] = [:]

    private static var enabled: Bool {
        UserDefaults.standard.object(forKey: "wapi.sounds.enabled") as? Bool ?? true
    }

    private static var typingEnabled: Bool {
        UserDefaults.standard.object(forKey: "wapi.typing.sounds.enabled") as? Bool ?? false
    }

    private static func play(_ id: SystemSoundID) {
        guard enabled else { return }
        AudioServicesPlaySystemSound(id)
    }

    private static func playGameSample(_ name: String, volume: Float, rate: Float = 1, fallback: SystemSoundID) {
        guard enabled else { return }
        guard let url = Bundle.main.url(forResource: name, withExtension: "wav") else {
            play(fallback)
            return
        }
        do {
            var voices = gamePlayers[name] ?? []
            let player: AVAudioPlayer
            if let idle = voices.first(where: { !$0.isPlaying }) { player = idle }
            else if voices.count < 4 {
                player = try AVAudioPlayer(contentsOf: url)
                player.enableRate = true
                player.prepareToPlay()
                voices.append(player)
            } else { player = voices.removeFirst(); player.stop(); voices.append(player) }
            player.currentTime = 0
            player.volume = volume
            player.enableRate = true
            player.rate = min(max(rate, 0.5), 2)
            gamePlayers[name] = voices
            player.play()
        } catch {
            play(fallback)
        }
    }

    static func typing() {
        guard enabled, typingEnabled else { return }
        let now = Date()
        guard now.timeIntervalSince(lastTypingAt) >= 0.085 else { return }
        lastTypingAt = now
        playGameSample("wapi_piece_select", volume: 0.055, fallback: 1104)
    }

    static func interfaceTap() {
        guard enabled else { return }
        let now = Date()
        guard now.timeIntervalSince(lastInterfaceTapAt) >= 0.045 else { return }
        lastInterfaceTapAt = now
        playGameSample("wapi_piece_select", volume: 0.12, fallback: 1104)
    }

    static func sent() { play(1004) }
    static func received() { play(1003) }
    static func mediaAdded() { play(1057) }
    static func recordingStarted() { play(1103) }
    static func recordingStopped() { play(1057) }
    static func recordingCancelled() { play(1073) }
    static func callStarted() { play(1057) }
    static func callEnded() {
        guard UserDefaults.standard.object(forKey: "wapi.call.end.sounds.enabled") as? Bool ?? true else { return }
        play(1001)
    }
    static func storyPublished() { play(1025) }
    static func gameMove() { playGameSample("wapi_piece_move", volume: 0.66, fallback: 1104) }
    static func gameCapture() { playGameSample("wapi_piece_capture", volume: 0.82, fallback: 1057) }
    static func gameDice() { playGameSample("wapi_dice_roll", volume: 0.76, fallback: 1104) }
    static func gamePool(power: Int = 70) { playGameSample("wapi_pool_cue", volume: 0.30 + Float(min(max(power, 1), 100)) * 0.006, fallback: 1104) }
    static func gamePoolCollision(_ intensity: Float = 0.45) { playGameSample("wapi_pool_collision", volume: min(max(intensity, 0.10), 0.78), rate: 0.96 + intensity * 0.12, fallback: 1104) }
    static func gamePoolRail(_ intensity: Float = 0.36) { playGameSample("wapi_pool_cushion", volume: min(max(intensity, 0.10), 0.60), rate: 0.98, fallback: 1104) }
    static func gamePoolPocket() { playGameSample("wapi_pool_pocket", volume: 0.84, rate: 0.92, fallback: 1057) }
    static func gameCard() { playGameSample("wapi_card_flip", volume: 0.70, fallback: 1104) }
    static func gameReward() { playGameSample("wapi_victory", volume: 0.88, fallback: 1025) }
    static func gameInvalid() { playGameSample("wapi_piece_capture", volume: 0.62, rate: 0.66, fallback: 1073); UINotificationFeedbackGenerator().notificationOccurred(.error) }
    static func gameQuizCorrect() { playGameSample("wapi_victory", volume: 0.90, rate: 1.14, fallback: 1025); UINotificationFeedbackGenerator().notificationOccurred(.success) }
    static func gameQuizWrong() { playGameSample("wapi_piece_capture", volume: 0.78, rate: 0.72, fallback: 1073); UINotificationFeedbackGenerator().notificationOccurred(.error) }
    static func gameQuizTick(urgent: Bool = false) { playGameSample("wapi_piece_select", volume: urgent ? 0.50 : 0.28, rate: urgent ? 1.32 : 1.08, fallback: 1104) }

    static func haptic(_ style: UIImpactFeedbackGenerator.FeedbackStyle = .light) {
        guard enabled else { return }
        UIImpactFeedbackGenerator(style: style).impactOccurred()
    }
}

final class WapiAppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {
    func application(_: UIApplication, supportedInterfaceOrientationsFor _: UIWindow?) -> UIInterfaceOrientationMask {
        WapiOrientation.supported
    }

    func application(_ application: UIApplication, didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
        if !UserDefaults.standard.bool(forKey: "wapi.typing.sound-profile-v2") {
            UserDefaults.standard.set(false, forKey: "wapi.typing.sounds.enabled")
            UserDefaults.standard.set(true, forKey: "wapi.typing.sound-profile-v2")
        }
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        let accept = UNNotificationAction(identifier: wapiAcceptCallAction, title: "Accepter", options: [.foreground])
        let decline = UNNotificationAction(identifier: wapiDeclineCallAction, title: "Refuser", options: [.destructive])
        UNUserNotificationCenter.current().setNotificationCategories([
            UNNotificationCategory(identifier: wapiDirectCallCategory, actions: [accept, decline], intentIdentifiers: [], options: [.customDismissAction])
        ])
        #if DEBUG
        // The isolated graphics harness does not exercise push notifications.
        if ProcessInfo.processInfo.arguments.contains("--wapi-pool-visual-test") { return true }
        #endif
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

    func application(
        _: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        guard ["call_cancel", "call_answered"].contains(userInfo["type"] as? String ?? ""),
              let callID = userInfo["callId"] as? String,
              !callID.isEmpty else {
            completionHandler(.noData)
            return
        }
        removeDeliveredCallNotification(callID: callID)
        // Compatibility with the previous backend: acceptance used to arrive
        // as call_cancel and must not close the newly established media.
        let status = (userInfo["status"] as? String ?? "").lowercased()
        if ["declined", "ended", "cancelled", "canceled", "expired"].contains(status) {
            NotificationCenter.default.post(name: wapiPushDidEndCall, object: callID)
        }
        completionHandler(.newData)
    }

    private func removeDeliveredCallNotification(callID: String) {
        UNUserNotificationCenter.current().getDeliveredNotifications { notifications in
            let identifiers = notifications.compactMap { notification in
                (notification.request.content.userInfo["callId"] as? String) == callID
                    ? notification.request.identifier
                    : nil
            }
            if !identifiers.isEmpty {
                UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: identifiers)
            }
        }
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

    private var poolVisualTest: Bool {
        #if DEBUG
        return ProcessInfo.processInfo.arguments.contains("--wapi-pool-visual-test")
        #else
        return false
        #endif
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if poolVisualTest {
                    WapiIOSPoolArena().onAppear { WapiOrientation.request(.landscapeRight) }
                } else if store.firebaseSessionLoading {
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
                    if poolVisualTest { WapiOrientation.request(.landscapeRight) }
                case .background: store.setFirebasePresence(online: false)
                case .inactive: break
                @unknown default: break
                }
            }
        }
    }
}
