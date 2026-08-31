import SwiftUI
import UIKit

/// Shared native editor controls. Insets are owned by SwiftUI, including the keyboard.
struct WapiEditorActionIOS: View {
    let title: String
    let busy: Bool
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Divider()
            Button(action: action) {
                HStack(spacing: 10) {
                    if busy { ProgressView().tint(.white) }
                    Text(busy ? "Enregistrement en cours…" : title).font(.headline)
                }
                .frame(maxWidth: .infinity).frame(minHeight: 52)
            }
            .buttonStyle(.borderedProminent).tint(WapiColor.blue)
            .buttonBorderShape(.roundedRectangle(radius: 14))
            .disabled(!enabled || busy)
            .padding(.horizontal, 20).padding(.vertical, 12)
            .frame(maxWidth: 640)
        }
        .frame(maxWidth: .infinity).background(.bar)
    }
}

struct WapiEditorTextFieldIOS: View {
    let title: String
    @Binding var text: String
    var limit: Int = 80
    var keyboard: UIKeyboardType = .default
    var multiline = false

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title).font(.subheadline).foregroundStyle(.secondary)
            TextField(title, text: $text, axis: multiline ? .vertical : .horizontal)
                .labelsHidden().font(.body).keyboardType(keyboard)
                .textInputAutocapitalization(keyboard == .URL ? .never : .sentences)
                .autocorrectionDisabled(keyboard == .URL)
                .lineLimit(multiline ? 3...6 : 1...1)
                .onChange(of: text) { _, value in
                    if value.count > limit { text = String(value.prefix(limit)) }
                }
            if multiline { Text("\(text.count)/\(limit)").font(.caption).foregroundStyle(.secondary) }
        }
        .padding(.vertical, 6)
    }
}

/// Adaptateur iOS du fichier design-system/wapi.tokens.json.
enum WapiColor {
    static let blue = Color(red: 0.078, green: 0.435, blue: 0.910)
    static let bluePressed = Color(red: 0.043, green: 0.337, blue: 0.722)
    static let deepBlue = Color(red: 0.031, green: 0.290, blue: 0.608)
    static let sky = Color(red: 0.098, green: 0.749, blue: 0.949)
    static let violet = Color(red: 0.000, green: 0.655, blue: 0.627)
    static let night = Color(red: 0.024, green: 0.165, blue: 0.298)
    static let ink = Color(red: 0.039, green: 0.114, blue: 0.196)
    static let secondaryText = Color(red: 0.392, green: 0.459, blue: 0.533)
    static let canvas = Color(red: 0.969, green: 0.976, blue: 0.988)
    static let surface = Color.white
    static let secondarySurface = Color(red: 0.937, green: 0.953, blue: 0.969)
    static let blueMist = Color(red: 0.882, green: 0.937, blue: 1.000)
    static let unreadSurface = Color(red: 0.918, green: 0.957, blue: 1.000)
    static let line = Color(red: 0.867, green: 0.894, blue: 0.925)
    static let verified = Color(red: 0.522, green: 0.553, blue: 0.588)
    static let live = Color(red: 0.941, green: 0.267, blue: 0.353)
}

enum WapiSpacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let screen: CGFloat = 18
}

enum WapiRadius {
    static let compact: CGFloat = 8
    static let control: CGFloat = 12
    static let panel: CGFloat = 16
    static let hero: CGFloat = 22
}

enum WapiShadow {
    static let color = Color(red: 0.03, green: 0.12, blue: 0.22).opacity(0.055)
    static let radius: CGFloat = 5
    static let y: CGFloat = 2
}

/// Retour tactile visuel commun aux cartes, lignes et icônes personnalisées.
/// Les boutons système conservent leur style iOS natif ; seuls les anciens
/// boutons `.plain` utilisent ce comportement afin de ne plus sembler inertes.
struct WapiPressableButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Rectangle())
            .scaleEffect(configuration.isPressed ? 0.975 : 1)
            .opacity(!isEnabled ? 0.46 : (configuration.isPressed ? 0.80 : 1))
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
            .onChange(of: configuration.isPressed) { _, pressed in
                if pressed && isEnabled { WapiSounds.interfaceTap() }
            }
    }
}

/// Chrome natif commun à tous les écrans. WAPI conserve les composants iOS,
/// mais leur donne une identité propre au lieu d'imiter une autre application.
enum WapiChrome {
    static func install() {
        let blue = UIColor(red: 0.031, green: 0.290, blue: 0.608, alpha: 1)
        let muted = UIColor(red: 0.392, green: 0.459, blue: 0.533, alpha: 1)
        let line = UIColor(red: 0.847, green: 0.882, blue: 0.918, alpha: 0.55)

        let tab = UITabBarAppearance()
        tab.configureWithOpaqueBackground()
        tab.backgroundEffect = nil
        tab.backgroundColor = UIColor.white
        tab.shadowColor = line
        [tab.stackedLayoutAppearance, tab.inlineLayoutAppearance, tab.compactInlineLayoutAppearance].forEach { item in
            item.selected.iconColor = blue
            item.selected.titleTextAttributes = [.foregroundColor: blue, .font: UIFont.systemFont(ofSize: 10, weight: .bold)]
            item.normal.iconColor = muted
            item.normal.titleTextAttributes = [.foregroundColor: muted, .font: UIFont.systemFont(ofSize: 10, weight: .medium)]
        }
        UITabBar.appearance().standardAppearance = tab
        UITabBar.appearance().scrollEdgeAppearance = tab

        let navigation = UINavigationBarAppearance()
        navigation.configureWithDefaultBackground()
        navigation.backgroundEffect = UIBlurEffect(style: .systemUltraThinMaterialLight)
        navigation.backgroundColor = UIColor(red: 0.969, green: 0.976, blue: 0.988, alpha: 0.96)
        navigation.shadowColor = line
        navigation.titleTextAttributes = [
            .foregroundColor: UIColor(red: 0.063, green: 0.114, blue: 0.176, alpha: 1),
            .font: UIFont.systemFont(ofSize: 17, weight: .semibold),
        ]
        UINavigationBar.appearance().standardAppearance = navigation
        UINavigationBar.appearance().scrollEdgeAppearance = navigation
    }
}

extension View {
    func wapiPanel(radius: CGFloat = WapiRadius.panel) -> some View {
        self
            .background(WapiColor.surface)
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).stroke(WapiColor.line.opacity(0.82), lineWidth: 0.8))
            .shadow(color: WapiShadow.color, radius: WapiShadow.radius, y: WapiShadow.y)
    }

    func wapiFlowSurface(radius: CGFloat = WapiRadius.panel) -> some View {
        self
            .background(
                LinearGradient(
                    colors: [Color.white, WapiColor.blueMist.opacity(0.24), Color.white],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).stroke(WapiColor.line.opacity(0.82), lineWidth: 0.8))
            .shadow(color: WapiShadow.color, radius: WapiShadow.radius, y: WapiShadow.y)
    }
}

extension Color {
    static let whappyBlue = WapiColor.blue
    static let whappyInk = WapiColor.ink
    static let whappyBackground = WapiColor.canvas
    static let wapiVerified = WapiColor.verified
    static let wapiLive = WapiColor.live
}
