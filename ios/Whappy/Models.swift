import Foundation
import SwiftUI

extension Color {
    static let whappyBlue = Color(red: 0, green: 0.635, blue: 0.902)
    static let whappyInk = Color(red: 0.082, green: 0.176, blue: 0.216)
    static let whappyBackground = Color(red: 0.961, green: 0.984, blue: 0.988)
}

struct Conversation: Identifiable, Hashable, Codable {
    let id: UUID
    let name: String
    let initials: String
    let phoneNumber: String
    var lastMessage: String
    var unread: Bool
    var messages: [Message]
}

enum CallMode: String, Identifiable, Codable {
    case audio, video
    var id: String { rawValue }
    var title: String { self == .audio ? "Appel audio" : "Appel vidéo" }
    var systemImage: String { self == .audio ? "phone.fill" : "video.fill" }
}

struct CallRecord: Identifiable, Hashable, Codable {
    let id: UUID
    let name: String
    let phoneNumber: String
    let mode: CallMode
    let date: Date
    let outgoing: Bool
    let missed: Bool
}

struct Message: Identifiable, Hashable, Codable {
    let id: UUID
    let text: String
    let mine: Bool
    let sentAt: Date
    var kind: String = "text"
    var mediaPath: String? = nil
    var replyToID: UUID? = nil
    var replyText: String? = nil
    var reactions: [String: String] = [:]
    var deleted: Bool = false
    var edited: Bool = false

    init(id: UUID, text: String, mine: Bool, sentAt: Date, kind: String = "text", mediaPath: String? = nil, replyToID: UUID? = nil, replyText: String? = nil, reactions: [String: String] = [:], deleted: Bool = false, edited: Bool = false) {
        self.id = id; self.text = text; self.mine = mine; self.sentAt = sentAt; self.kind = kind; self.mediaPath = mediaPath; self.replyToID = replyToID; self.replyText = replyText; self.reactions = reactions; self.deleted = deleted; self.edited = edited
    }

    private enum CodingKeys: String, CodingKey { case id, text, mine, sentAt, kind, mediaPath, replyToID, replyText, reactions, deleted, edited }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(UUID.self, forKey: .id)
        text = try values.decode(String.self, forKey: .text)
        mine = try values.decode(Bool.self, forKey: .mine)
        sentAt = try values.decode(Date.self, forKey: .sentAt)
        kind = try values.decodeIfPresent(String.self, forKey: .kind) ?? "text"
        mediaPath = try values.decodeIfPresent(String.self, forKey: .mediaPath)
        replyToID = try values.decodeIfPresent(UUID.self, forKey: .replyToID)
        replyText = try values.decodeIfPresent(String.self, forKey: .replyText)
        reactions = try values.decodeIfPresent([String: String].self, forKey: .reactions) ?? [:]
        deleted = try values.decodeIfPresent(Bool.self, forKey: .deleted) ?? false
        edited = try values.decodeIfPresent(Bool.self, forKey: .edited) ?? false
    }
}

struct WhappyMoment: Identifiable, Hashable, Codable {
    let id: UUID
    let title: String
    let text: String
    let createdAt: Date
}

struct Listing: Identifiable, Hashable, Codable {
    let id: UUID
    let title: String
    let price: String
    let place: String
    let seller: String
    let icon: String
    let acceptsTrade: Bool
    var saved: Bool = false
}

struct CartLine: Identifiable, Hashable, Codable {
    var id: UUID { listing.id }
    let listing: Listing
    var quantity: Int
}

struct WhappyOrder: Identifiable, Hashable, Codable {
    let id: UUID
    let reference: String
    let lines: [CartLine]
    let delivery: String
    let createdAt: Date
    var status: String
}

struct LiveRoom: Identifiable, Hashable, Codable {
    let id: UUID
    let host: String
    let title: String
    let category: String
    var viewers: Int
    let icon: String
    var live: Bool = true
}

struct WalletTransaction: Identifiable, Hashable, Codable {
    let id: UUID
    let label: String
    let amount: Int
    let date: Date
}

struct WhappyServiceRequest: Identifiable, Hashable, Codable {
    let id: UUID
    let type: String
    let details: String
    let createdAt: Date
    var status: String
}

struct WhappyBusiness: Identifiable, Hashable, Codable {
    let id: UUID
    var name: String
    var category: String
    var bio: String
    var city: String
}

enum WhappyTab: Hashable {
    case home, messages, calls, market, live, services, profile
}
