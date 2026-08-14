import Foundation

@MainActor
final class WhappyStore: ObservableObject {
    @Published var selectedTab: WhappyTab = .home
    @Published var conversations: [Conversation]
    @Published var listings: [Listing]
    @Published var liveRooms: [LiveRoom]

    init() {
        let now = Date()
        conversations = [
            Conversation(
                id: UUID(), name: "Amina M.", initials: "AM",
                phoneNumber: "+242065550101",
                lastMessage: "Le troc est accepté pour le canapé ?", unread: true,
                messages: [
                    Message(id: UUID(), text: "Bonjour, le canapé est toujours disponible.", mine: false, sentAt: now.addingTimeInterval(-180)),
                    Message(id: UUID(), text: "Bonjour Amina. Le troc est accepté ?", mine: true, sentAt: now.addingTimeInterval(-120)),
                    Message(id: UUID(), text: "Oui, envoyez-moi votre proposition.", mine: false, sentAt: now.addingTimeInterval(-60))
                ]
            ),
            Conversation(id: UUID(), name: "Junior K.", initials: "JK", phoneNumber: "+242058842160", lastMessage: "Je peux livrer cet après-midi.", unread: false, messages: []),
            Conversation(id: UUID(), name: "Mokabi Studio", initials: "MS", phoneNumber: "+242065550101", lastMessage: "Votre commande est prête ✦", unread: false, messages: [])
        ]
        listings = [
            Listing(id: UUID(), title: "MacBook Air M3 · Comme neuf", price: "750 000 FCFA", place: "Poto-Poto", seller: "Junior K.", icon: "laptopcomputer", acceptsTrade: false),
            Listing(id: UUID(), title: "Canapé modulable en velours", price: "Échange accepté", place: "Bacongo", seller: "Maison Noki", icon: "sofa.fill", acceptsTrade: true),
            Listing(id: UUID(), title: "Sneakers édition limitée", price: "85 000 FCFA", place: "Centre-ville", seller: "Mokabi Store", icon: "shoe.fill", acceptsTrade: false)
        ]
        liveRooms = [
            LiveRoom(id: UUID(), host: "Mokabi Studio", title: "Nouvelle collection N’Tela", category: "Mode", viewers: 1_284, icon: "tshirt.fill"),
            LiveRoom(id: UUID(), host: "Junior Tech", title: "Les bonnes affaires smartphones", category: "Tech", viewers: 438, icon: "iphone.gen3")
        ]
    }

    var unreadCount: Int { conversations.filter(\.unread).count }

    func markRead(_ conversation: Conversation) {
        guard let index = conversations.firstIndex(where: { $0.id == conversation.id }) else { return }
        conversations[index].unread = false
    }

    func send(_ text: String, to conversationID: UUID) {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, let index = conversations.firstIndex(where: { $0.id == conversationID }) else { return }
        conversations[index].messages.append(Message(id: UUID(), text: value, mine: true, sentAt: Date()))
        conversations[index].lastMessage = value
    }
}
