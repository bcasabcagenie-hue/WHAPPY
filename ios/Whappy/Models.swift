import Foundation
import SwiftUI

extension Color {
    static let whappyBlue = Color(red: 0, green: 0.635, blue: 0.902)
    static let whappyInk = Color(red: 0.082, green: 0.176, blue: 0.216)
    static let whappyBackground = Color(red: 0.961, green: 0.984, blue: 0.988)
}

struct Conversation: Identifiable, Hashable {
    let id: UUID
    let name: String
    let initials: String
    let phoneNumber: String
    var lastMessage: String
    var unread: Bool
    var messages: [Message]
}

enum CallMode: String, Identifiable {
    case audio
    case video

    var id: String { rawValue }
    var title: String { self == .audio ? "Appel audio" : "Appel vidéo" }
    var systemImage: String { self == .audio ? "phone.fill" : "video.fill" }
}

struct Message: Identifiable, Hashable {
    let id: UUID
    let text: String
    let mine: Bool
    let sentAt: Date
}

struct Listing: Identifiable, Hashable {
    let id: UUID
    let title: String
    let price: String
    let place: String
    let seller: String
    let icon: String
    let acceptsTrade: Bool
}

struct LiveRoom: Identifiable, Hashable {
    let id: UUID
    let host: String
    let title: String
    let category: String
    let viewers: Int
    let icon: String
}

enum WhappyTab: Hashable {
    case home, messages, market, live, profile
}
