import Foundation

@MainActor
final class WhappyStore: ObservableObject {
    @Published var selectedTab: WhappyTab = .home
    @Published var conversations: [Conversation] { didSet { save() } }
    @Published var listings: [Listing] { didSet { save() } }
    @Published var liveRooms: [LiveRoom] { didSet { save() } }
    @Published var calls: [CallRecord] { didSet { save() } }
    @Published var cart: [CartLine] { didSet { save() } }
    @Published var orders: [WhappyOrder] { didSet { save() } }
    @Published var walletBalance: Int { didSet { save() } }
    @Published var walletTransactions: [WalletTransaction] { didSet { save() } }
    @Published var serviceRequests: [WhappyServiceRequest] { didSet { save() } }
    @Published var moments: [WhappyMoment] { didSet { save() } }
    @Published var business: WhappyBusiness? { didSet { save() } }
    @Published var notificationsEnabled: Bool { didSet { save() } }
    @Published var privacyMode: String { didSet { save() } }
    @Published var dataSaverEnabled: Bool { didSet { save() } }

    private let defaults = UserDefaults.standard
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private var restoring = true

    init() {
        let now = Date()
        conversations = [
            Conversation(id: UUID(), name: "Amina M.", initials: "AM", phoneNumber: "+242065550101", lastMessage: "Le troc est accepté pour le canapé ?", unread: true, messages: [
                Message(id: UUID(), text: "Bonjour, le canapé est toujours disponible.", mine: false, sentAt: now.addingTimeInterval(-180)),
                Message(id: UUID(), text: "Bonjour Amina. Le troc est accepté ?", mine: true, sentAt: now.addingTimeInterval(-120)),
                Message(id: UUID(), text: "Oui, envoyez-moi votre proposition.", mine: false, sentAt: now.addingTimeInterval(-60))
            ]),
            Conversation(id: UUID(), name: "Junior K.", initials: "JK", phoneNumber: "+242058842160", lastMessage: "Je peux livrer cet après-midi.", unread: false, messages: []),
            Conversation(id: UUID(), name: "Mokabi Studio", initials: "MS", phoneNumber: "+242064420222", lastMessage: "Votre commande est prête ✦", unread: false, messages: [])
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
        calls = [
            CallRecord(id: UUID(), name: "Amina M.", phoneNumber: "+242065550101", mode: .audio, date: now.addingTimeInterval(-3_600), outgoing: false, missed: false),
            CallRecord(id: UUID(), name: "Junior K.", phoneNumber: "+242058842160", mode: .video, date: now.addingTimeInterval(-90_000), outgoing: true, missed: false)
        ]
        cart = []
        orders = []
        walletBalance = 0
        walletTransactions = []
        serviceRequests = []
        moments = []
        business = nil
        notificationsEnabled = true
        privacyMode = "contacts"
        dataSaverEnabled = false
        restore()
        restoring = false
    }

    var unreadCount: Int { conversations.filter(\.unread).count }
    var cartCount: Int { cart.reduce(0) { $0 + $1.quantity } }

    func markRead(_ conversation: Conversation) {
        guard let index = conversations.firstIndex(where: { $0.id == conversation.id }) else { return }
        conversations[index].unread = false
    }

    func toggleUnread(_ conversation: Conversation) {
        guard let index = conversations.firstIndex(where: { $0.id == conversation.id }) else { return }
        conversations[index].unread.toggle()
    }

    func deleteConversation(_ conversation: Conversation) {
        conversations.removeAll { $0.id == conversation.id }
    }

    func send(_ text: String, to conversationID: UUID, replyTo: Message? = nil) {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, let index = conversations.firstIndex(where: { $0.id == conversationID }) else { return }
        conversations[index].messages.append(Message(id: UUID(), text: value, mine: true, sentAt: Date(), replyToID: replyTo?.id, replyText: replyTo?.text))
        conversations[index].lastMessage = value
    }

    func react(to messageID: UUID, in conversationID: UUID, emoji: String) {
        guard let conversation = conversations.firstIndex(where: { $0.id == conversationID }), let message = conversations[conversation].messages.firstIndex(where: { $0.id == messageID }) else { return }
        conversations[conversation].messages[message].reactions["me"] = emoji
    }

    func deleteMessage(_ messageID: UUID, in conversationID: UUID) {
        guard let conversation = conversations.firstIndex(where: { $0.id == conversationID }), let message = conversations[conversation].messages.firstIndex(where: { $0.id == messageID }), conversations[conversation].messages[message].mine else { return }
        conversations[conversation].messages[message].deleted = true
        conversations[conversation].messages[message].kind = "deleted"
        conversations[conversation].messages[message].mediaPath = nil
    }

    func editMessage(_ messageID: UUID, in conversationID: UUID, text: String) {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, let conversation = conversations.firstIndex(where: { $0.id == conversationID }), let message = conversations[conversation].messages.firstIndex(where: { $0.id == messageID }), conversations[conversation].messages[message].mine, conversations[conversation].messages[message].kind == "text" else { return }
        let previous = conversations[conversation].messages[message]
        conversations[conversation].messages[message] = Message(id: previous.id, text: value, mine: true, sentAt: previous.sentAt, kind: previous.kind, mediaPath: previous.mediaPath, replyToID: previous.replyToID, replyText: previous.replyText, reactions: previous.reactions, deleted: false, edited: true)
        if message == conversations[conversation].messages.count - 1 { conversations[conversation].lastMessage = value }
    }

    func sendMedia(kind: String, path: String, to conversationID: UUID) {
        guard let index = conversations.firstIndex(where: { $0.id == conversationID }) else { return }
        let label = kind == "image" ? "📷 Photo" : "🎤 Note vocale"
        conversations[index].messages.append(Message(id: UUID(), text: label, mine: true, sentAt: Date(), kind: kind, mediaPath: path))
        conversations[index].lastMessage = label
    }

    func createMoment(title: String, text: String) {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanTitle.count >= 2, cleanText.count >= 3 else { return }
        moments.insert(WhappyMoment(id: UUID(), title: cleanTitle, text: cleanText, createdAt: Date()), at: 0)
    }

    func createConversation(name: String, phone: String) {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanName.count >= 2, cleanPhone.count >= 8 else { return }
        let initials = cleanName.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
        conversations.insert(Conversation(id: UUID(), name: cleanName, initials: initials, phoneNumber: cleanPhone, lastMessage: "Nouvelle conversation", unread: false, messages: []), at: 0)
    }

    func recordCall(name: String, phone: String, mode: CallMode) {
        calls.insert(CallRecord(id: UUID(), name: name, phoneNumber: phone, mode: mode, date: Date(), outgoing: true, missed: false), at: 0)
    }

    func addListing(title: String, price: String, place: String, trade: Bool) {
        let value = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard value.count >= 2 else { return }
        listings.insert(Listing(id: UUID(), title: value, price: price.isEmpty ? "À discuter" : price, place: place.isEmpty ? "Brazzaville" : place, seller: "Cyril Bokilo", icon: trade ? "arrow.triangle.2.circlepath" : "shippingbox.fill", acceptsTrade: trade), at: 0)
    }

    func toggleSaved(_ listing: Listing) {
        guard let index = listings.firstIndex(where: { $0.id == listing.id }) else { return }
        listings[index].saved.toggle()
    }

    func addToCart(_ listing: Listing) {
        if let index = cart.firstIndex(where: { $0.listing.id == listing.id }) { cart[index].quantity = min(9, cart[index].quantity + 1) }
        else { cart.append(CartLine(listing: listing, quantity: 1)) }
    }

    func changeQuantity(_ line: CartLine, delta: Int) {
        guard let index = cart.firstIndex(where: { $0.id == line.id }) else { return }
        let next = cart[index].quantity + delta
        if next <= 0 { cart.remove(at: index) } else { cart[index].quantity = min(9, next) }
    }

    func checkout(delivery: String) -> String? {
        guard !cart.isEmpty, !delivery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        let reference = "WH-" + String(UUID().uuidString.prefix(6)).uppercased()
        orders.insert(WhappyOrder(id: UUID(), reference: reference, lines: cart, delivery: delivery, createdAt: Date(), status: "À confirmer"), at: 0)
        cart = []
        return reference
    }

    func createLive(title: String, category: String) -> LiveRoom? {
        let value = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard value.count >= 3 else { return nil }
        let room = LiveRoom(id: UUID(), host: "Cyril Bokilo", title: value, category: category, viewers: 1, icon: "video.fill")
        liveRooms.insert(room, at: 0)
        return room
    }

    func endLive(_ room: LiveRoom) {
        guard let index = liveRooms.firstIndex(where: { $0.id == room.id }) else { return }
        liveRooms[index].live = false
    }

    func activateDemoWallet() {
        guard walletBalance == 0 && walletTransactions.isEmpty else { return }
        walletBalance = 25_000
        walletTransactions.insert(WalletTransaction(id: UUID(), label: "Solde de démonstration", amount: 25_000, date: Date()), at: 0)
    }

    func pay(recipient: String, amount: Int) -> Bool {
        let name = recipient.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, amount > 0, amount <= walletBalance else { return false }
        walletBalance -= amount
        walletTransactions.insert(WalletTransaction(id: UUID(), label: "Paiement test · \(name)", amount: -amount, date: Date()), at: 0)
        return true
    }

    func createServiceRequest(type: String, details: String) {
        let value = details.trimmingCharacters(in: .whitespacesAndNewlines)
        guard value.count >= 5 else { return }
        serviceRequests.insert(WhappyServiceRequest(id: UUID(), type: type, details: value, createdAt: Date(), status: "Demandé"), at: 0)
    }

    func saveBusiness(name: String, category: String, bio: String, city: String) {
        business = WhappyBusiness(id: business?.id ?? UUID(), name: name, category: category, bio: bio, city: city)
    }

    private func restore() {
        conversations = decode("conversations") ?? conversations
        listings = decode("listings") ?? listings
        liveRooms = decode("liveRooms") ?? liveRooms
        calls = decode("calls") ?? calls
        cart = decode("cart") ?? cart
        orders = decode("orders") ?? orders
        walletTransactions = decode("walletTransactions") ?? walletTransactions
        serviceRequests = decode("serviceRequests") ?? serviceRequests
        moments = decode("moments") ?? moments
        business = decode("business") ?? business
        walletBalance = defaults.object(forKey: "walletBalance") as? Int ?? walletBalance
        notificationsEnabled = defaults.object(forKey: "notificationsEnabled") as? Bool ?? notificationsEnabled
        privacyMode = defaults.string(forKey: "privacyMode") ?? privacyMode
        dataSaverEnabled = defaults.object(forKey: "dataSaverEnabled") as? Bool ?? dataSaverEnabled
    }

    private func decode<T: Decodable>(_ key: String) -> T? {
        guard let data = defaults.data(forKey: "whappy.\(key)") else { return nil }
        return try? decoder.decode(T.self, from: data)
    }

    private func save() {
        guard !restoring else { return }
        encode(conversations, "conversations")
        encode(listings, "listings")
        encode(liveRooms, "liveRooms")
        encode(calls, "calls")
        encode(cart, "cart")
        encode(orders, "orders")
        encode(walletTransactions, "walletTransactions")
        encode(serviceRequests, "serviceRequests")
        encode(moments, "moments")
        encode(business, "business")
        defaults.set(walletBalance, forKey: "walletBalance")
        defaults.set(notificationsEnabled, forKey: "notificationsEnabled")
        defaults.set(privacyMode, forKey: "privacyMode")
        defaults.set(dataSaverEnabled, forKey: "dataSaverEnabled")
    }

    private func encode<T: Encodable>(_ value: T, _ key: String) {
        if let data = try? encoder.encode(value) { defaults.set(data, forKey: "whappy.\(key)") }
    }
}
