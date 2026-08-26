import SwiftUI

/// Adaptateur iOS du fichier design-system/wapi.tokens.json.
enum WapiColor {
    static let blue = Color(red: 0.000, green: 0.580, blue: 0.941)
    static let bluePressed = Color(red: 0.000, green: 0.498, blue: 0.820)
    static let deepBlue = Color(red: 0.000, green: 0.400, blue: 0.812)
    static let sky = Color(red: 0.000, green: 0.635, blue: 0.902)
    static let ink = Color(red: 0.090, green: 0.090, blue: 0.090)
    static let secondaryText = Color(red: 0.451, green: 0.467, blue: 0.490)
    static let canvas = Color(red: 0.961, green: 0.961, blue: 0.961)
    static let surface = Color.white
    static let secondarySurface = Color(red: 0.961, green: 0.961, blue: 0.961)
    static let blueMist = Color(red: 0.945, green: 0.980, blue: 1.000)
    static let unreadSurface = Color(red: 0.957, green: 0.984, blue: 1.000)
    static let line = Color(red: 0.906, green: 0.906, blue: 0.906)
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
    static let compact: CGFloat = 8
    static let control: CGFloat = 10
    static let panel: CGFloat = 14
    static let hero: CGFloat = 18
}

enum WapiShadow {
    static let color = Color.black.opacity(0.025)
    static let radius: CGFloat = 5
    static let y: CGFloat = 1
}

extension View {
    func wapiPanel(radius: CGFloat = WapiRadius.panel) -> some View {
        self
            .background(WapiColor.surface)
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
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
