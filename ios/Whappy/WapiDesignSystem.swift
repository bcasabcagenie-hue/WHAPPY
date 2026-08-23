import SwiftUI

/// Adaptateur iOS du fichier design-system/wapi.tokens.json.
enum WapiColor {
    static let blue = Color(red: 0.000, green: 0.580, blue: 0.941)
    static let deepBlue = Color(red: 0.000, green: 0.400, blue: 0.812)
    static let sky = Color(red: 0.000, green: 0.635, blue: 0.902)
    static let ink = Color(red: 0.125, green: 0.125, blue: 0.125)
    static let secondaryText = Color(red: 0.435, green: 0.467, blue: 0.502)
    static let canvas = Color(red: 0.961, green: 0.969, blue: 0.976)
    static let surface = Color.white
    static let secondarySurface = Color(red: 0.945, green: 0.957, blue: 0.969)
    static let line = Color(red: 0.890, green: 0.910, blue: 0.929)
    static let verified = Color(red: 0.522, green: 0.553, blue: 0.588)
    static let live = Color(red: 0.941, green: 0.267, blue: 0.353)
}

enum WapiSpacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
}

enum WapiRadius {
    static let compact: CGFloat = 12
    static let control: CGFloat = 16
    static let panel: CGFloat = 22
    static let hero: CGFloat = 28
}

extension Color {
    static let whappyBlue = WapiColor.blue
    static let whappyInk = WapiColor.ink
    static let whappyBackground = WapiColor.canvas
    static let wapiVerified = WapiColor.verified
    static let wapiLive = WapiColor.live
}
