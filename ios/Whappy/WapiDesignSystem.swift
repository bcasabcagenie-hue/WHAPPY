import SwiftUI
import UIKit

/// Adaptateur iOS du fichier design-system/wapi.tokens.json.
enum WapiColor {
    static let blue = Color(red: 0.078, green: 0.541, blue: 0.949)
    static let bluePressed = Color(red: 0.043, green: 0.443, blue: 0.800)
    static let deepBlue = Color(red: 0.043, green: 0.435, blue: 0.847)
    static let sky = Color(red: 0.314, green: 0.788, blue: 0.918)
    static let violet = Color(red: 0.400, green: 0.459, blue: 0.961)
    static let ink = Color(red: 0.067, green: 0.141, blue: 0.239)
    static let secondaryText = Color(red: 0.392, green: 0.455, blue: 0.545)
    static let canvas = Color(red: 0.945, green: 0.961, blue: 0.980)
    static let surface = Color.white
    static let secondarySurface = Color(red: 0.918, green: 0.945, blue: 0.973)
    static let blueMist = Color(red: 0.882, green: 0.945, blue: 1.000)
    static let unreadSurface = Color(red: 0.929, green: 0.969, blue: 1.000)
    static let line = Color(red: 0.843, green: 0.882, blue: 0.925)
    static let verified = Color(red: 0.522, green: 0.553, blue: 0.588)
    static let live = Color(red: 0.941, green: 0.267, blue: 0.353)
}

enum WapiSpacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let screen: CGFloat = 16
}

enum WapiRadius {
    static let compact: CGFloat = 12
    static let control: CGFloat = 16
    static let panel: CGFloat = 22
    static let hero: CGFloat = 28
}

enum WapiShadow {
    static let color = Color(red: 0.05, green: 0.20, blue: 0.36).opacity(0.10)
    static let radius: CGFloat = 16
    static let y: CGFloat = 7
}

/// Chrome natif commun à tous les écrans. WAPI conserve les composants iOS,
/// mais leur donne une identité propre au lieu d'imiter une autre application.
enum WapiChrome {
    static func install() {
        let blue = UIColor(red: 0.078, green: 0.541, blue: 0.949, alpha: 1)
        let muted = UIColor(red: 0.392, green: 0.455, blue: 0.545, alpha: 1)
        let canvas = UIColor(red: 0.945, green: 0.961, blue: 0.980, alpha: 0.98)

        let tab = UITabBarAppearance()
        tab.configureWithOpaqueBackground()
        tab.backgroundColor = canvas
        tab.shadowColor = UIColor(red: 0.84, green: 0.88, blue: 0.93, alpha: 0.65)
        [tab.stackedLayoutAppearance, tab.inlineLayoutAppearance, tab.compactInlineLayoutAppearance].forEach { item in
            item.selected.iconColor = blue
            item.selected.titleTextAttributes = [.foregroundColor: blue, .font: UIFont.systemFont(ofSize: 10, weight: .semibold)]
            item.normal.iconColor = muted
            item.normal.titleTextAttributes = [.foregroundColor: muted, .font: UIFont.systemFont(ofSize: 10, weight: .medium)]
        }
        UITabBar.appearance().standardAppearance = tab
        UITabBar.appearance().scrollEdgeAppearance = tab

        let navigation = UINavigationBarAppearance()
        navigation.configureWithOpaqueBackground()
        navigation.backgroundColor = canvas
        navigation.shadowColor = .clear
        navigation.titleTextAttributes = [.foregroundColor: UIColor(red: 0.067, green: 0.141, blue: 0.239, alpha: 1)]
        UINavigationBar.appearance().standardAppearance = navigation
        UINavigationBar.appearance().scrollEdgeAppearance = navigation
    }
}

extension View {
    func wapiPanel(radius: CGFloat = WapiRadius.panel) -> some View {
        self
            .background(WapiColor.surface)
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).stroke(WapiColor.line.opacity(0.72), lineWidth: 0.8))
            .shadow(color: WapiShadow.color, radius: WapiShadow.radius, y: WapiShadow.y)
    }

    func wapiFlowSurface(radius: CGFloat = WapiRadius.panel) -> some View {
        self
            .background(
                LinearGradient(
                    colors: [Color.white, WapiColor.blueMist.opacity(0.48), Color.white],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).stroke(WapiColor.sky.opacity(0.22), lineWidth: 0.9))
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
