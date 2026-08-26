import Foundation
import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import FirebaseFunctions
import FirebaseStorage
import UserNotifications
import UIKit

@MainActor
final class WhappyStore: ObservableObject {
    @Published var selectedTab: WhappyTab = .messages
    @Published var conversations: [Conversation] { didSet { save() } }
    @Published var channels: [WhappyChannel] { didSet { save() } }
    @Published var listings: [Listing] { didSet { save() } }
    @Published var liveRooms: [LiveRoom] { didSet { save() } }
    @Published var calls: [CallRecord] { didSet { save() } }
    @Published var cart: [CartLine] { didSet { save() } }
    @Published var orders: [WhappyOrder] { didSet { save() } }
    @Published var walletBalance: Int { didSet { save() } }
    @Published var walletTransactions: [WalletTransaction] { didSet { save() } }
    @Published var serviceRequests: [WhappyServiceRequest] { didSet { save() } }
    @Published var moments: [WhappyMoment] { didSet { save() } }
    @Published var stories: [WapiStory] { didSet { save() } }
    @Published var business: WhappyBusiness? { didSet { save() } }
    @Published var activeBusinessMode: Bool { didSet { save() } }
    @Published var activeBusinessRemoteID: String { didSet { save() } }
    @Published var notificationsEnabled: Bool { didSet { save() } }
    @Published var privacyMode: String { didSet { save() } }
    @Published var dataSaverEnabled: Bool { didSet { save() } }
    @Published var interfaceLanguage: WapiInterfaceLanguage { didSet { save() } }
    @Published var pendingContactPhone: String?
    @Published var pendingChannelID: UUID?
    @Published var pendingSearch: String?
    @Published var pendingGroupCall: WapiGroupCallRoute?
    @Published var pendingDirectCall: WapiDirectCallRoute?
    @Published var firebaseUserID: String?
    @Published var firebaseProfileVerified = false
    @Published var firebaseSessionLoading = true
    @Published var firebaseBusy = false
    @Published var firebaseMessage: String?
    @Published var firebaseCodeSent = false

    var firebaseVerificationID: String?
    var firebaseAuthHandle: AuthStateDidChangeListenerHandle?
    var firebaseConversationListeners: [ListenerRegistration] = []
    var firebasePresenceListeners: [ListenerRegistration] = []
    var firebasePresencePeerIDs: Set<String> = []
    var firebaseMessageListener: ListenerRegistration?
    var firebaseMessageListenerHasDeliveredSnapshot = false
    var firebasePresenceTimer: Timer?
    var firebaseDirectConversations: [Conversation] = []
    var firebaseGroupConversations: [Conversation] = []
    var pushTokenObserver: NSObjectProtocol?
    var pushOpenObserver: NSObjectProtocol?
    var pushDeclineCallObserver: NSObjectProtocol?

    private let defaults = UserDefaults.standard
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private var restoring = true

    init() {
        conversations = []
        channels = []
        listings = []
        liveRooms = []
        calls = []
        cart = []
        orders = []
        walletBalance = 0
        walletTransactions = []
        serviceRequests = []
        moments = []
        stories = []
        business = nil
        activeBusinessMode = false
        activeBusinessRemoteID = ""
        notificationsEnabled = true
        privacyMode = "contacts"
        dataSaverEnabled = false
        interfaceLanguage = .automatic
        pendingContactPhone = nil
        pendingChannelID = nil
        pendingSearch = nil
        pendingGroupCall = nil
        pendingDirectCall = nil
        restore()
        restoring = false
        pushTokenObserver = NotificationCenter.default.addObserver(forName: wapiPushTokenDidChange, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.registerFirebasePushDevice() }
        }
        pushOpenObserver = NotificationCenter.default.addObserver(forName: wapiPushDidOpen, object: nil, queue: .main) { [weak self] notification in
            guard let value = notification.object as? String, let url = URL(string: value) else { return }
            Task { @MainActor in self?.handleWhappyURL(url) }
        }
        pushDeclineCallObserver = NotificationCenter.default.addObserver(forName: wapiPushDidDeclineCall, object: nil, queue: .main) { [weak self] notification in
            guard let value = notification.object as? String, let url = URL(string: value) else { return }
            Task { @MainActor in self?.declineDirectCall(from: url) }
        }
        configureFirebaseMessaging()
    }

    var unreadCount: Int { conversations.filter(\.unread).count }
    var cartCount: Int { cart.reduce(0) { $0 + $1.quantity } }

    func handleWhappyURL(_ url: URL) {
        guard let link = WhappyDeepLink.parse(url) else { return }
        selectedTab = .messages
        switch link {
        case .contact(let phone): pendingContactPhone = phone
        case .channel(let id): pendingChannelID = id
        case .groupCall(let id): pendingGroupCall = WapiGroupCallRoute(callID: id, groupID: nil, groupName: "Appel de groupe", video: false)
        case .directCall(let id): pendingDirectCall = WapiDirectCallRoute(callID: id, peerID: nil, peerName: "Appel WAPI", peerPhotoURL: "", video: false)
        case .search(let query): pendingSearch = query
        }
    }

    private func declineDirectCall(from url: URL) {
        guard case .directCall(let callID) = WhappyDeepLink.parse(url) else { return }
        guard firebaseUserID != nil else { return }
        Functions.functions(region: "europe-west1").httpsCallable("closeDirectCallSession").call(["callId": callID, "action": "decline"]) { _, error in
            guard error == nil else { return }
            UserDefaults.standard.removeObject(forKey: wapiPendingDeclineCallKey)
        }
    }

    func drainPendingDirectCallDecline() {
        guard let value = UserDefaults.standard.string(forKey: wapiPendingDeclineCallKey),
              let url = URL(string: value) else { return }
        declineDirectCall(from: url)
    }

    func markRead(_ conversation: Conversation) {
        guard let index = conversations.firstIndex(where: { $0.id == conversation.id }) else { return }
        conversations[index].unread = false
        conversations[index].readAt = Date()
        updateApplicationBadge()
    }

    func updateApplicationBadge() {
        UNUserNotificationCenter.current().setBadgeCount(unreadCount)
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
        if conversations[index].remoteID != nil {
            sendFirebaseMessage(value, conversation: conversations[index], replyTo: replyTo)
            return
        }
        let messageID = UUID()
        let now = Date()
        conversations[index].messages.append(Message(id: messageID, text: value, mine: true, sentAt: now, replyToID: replyTo?.id, replyText: replyTo?.text, status: "sending"))
        conversations[index].lastMessage = value
        Task {
            try? await Task.sleep(nanoseconds: 650_000_000)
            guard let conversationIndex = conversations.firstIndex(where: { $0.id == conversationID }),
                  let messageIndex = conversations[conversationIndex].messages.firstIndex(where: { $0.id == messageID })
            else { return }
            let currentStatus = conversations[conversationIndex].messages[messageIndex].status
            guard currentStatus == "sending" else { return }
            conversations[conversationIndex].messages[messageIndex].status = "sent"
        }
    }

    func react(to messageID: UUID, in conversationID: UUID, emoji: String) {
        guard let conversation = conversations.firstIndex(where: { $0.id == conversationID }), let message = conversations[conversation].messages.firstIndex(where: { $0.id == messageID }) else { return }
        if conversations[conversation].remoteID != nil, conversations[conversation].messages[message].remoteID != nil {
            reactFirebase(conversation: conversations[conversation], message: conversations[conversation].messages[message], emoji: emoji)
            return
        }
        conversations[conversation].messages[message].reactions["me"] = emoji
    }

    func deleteMessage(_ messageID: UUID, in conversationID: UUID) {
        guard let conversation = conversations.firstIndex(where: { $0.id == conversationID }), let message = conversations[conversation].messages.firstIndex(where: { $0.id == messageID }), conversations[conversation].messages[message].mine else { return }
        if conversations[conversation].remoteID != nil, conversations[conversation].messages[message].remoteID != nil {
            deleteFirebaseMessage(conversation: conversations[conversation], message: conversations[conversation].messages[message])
            return
        }
        conversations[conversation].messages[message].deleted = true
        conversations[conversation].messages[message].kind = "deleted"
        conversations[conversation].messages[message].mediaPath = nil
    }

    func editMessage(_ messageID: UUID, in conversationID: UUID, text: String) {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, let conversation = conversations.firstIndex(where: { $0.id == conversationID }), let message = conversations[conversation].messages.firstIndex(where: { $0.id == messageID }), conversations[conversation].messages[message].mine, conversations[conversation].messages[message].kind == "text" else { return }
        if conversations[conversation].remoteID != nil, conversations[conversation].messages[message].remoteID != nil {
            editFirebaseMessage(conversation: conversations[conversation], message: conversations[conversation].messages[message], text: value)
            return
        }
        let previous = conversations[conversation].messages[message]
        let status = previous.status == "sending" ? "sending" : "sent"
        conversations[conversation].messages[message] = Message(id: previous.id, text: value, mine: true, sentAt: previous.sentAt, kind: previous.kind, mediaPath: previous.mediaPath, replyToID: previous.replyToID, replyText: previous.replyText, reactions: previous.reactions, deleted: false, edited: true, status: status)
        if message == conversations[conversation].messages.count - 1 { conversations[conversation].lastMessage = value }
    }

    func sendMedia(kind: String, path: String, to conversationID: UUID, mediaName: String? = nil, viewOnce: Bool = false) {
        guard let index = conversations.firstIndex(where: { $0.id == conversationID }) else { return }
        if conversations[index].remoteID != nil {
            sendFirebaseMedia(kind: kind, path: path, mediaName: mediaName, viewOnce: viewOnce, conversation: conversations[index])
            return
        }
        let label = switch kind {
        case "audio": "🎤 Note vocale"
        case "video": "🎬 Vidéo"
        case "document": "Waphsare · Document"
        default: "📷 Photo"
        }
        let messageID = UUID()
        let now = Date()
        conversations[index].messages.append(Message(id: messageID, text: label, mine: true, sentAt: now, kind: kind, mediaPath: path, mediaName: mediaName, viewOnce: viewOnce, status: "sending"))
        conversations[index].lastMessage = label
        Task {
            try? await Task.sleep(nanoseconds: 650_000_000)
            guard let conversationIndex = conversations.firstIndex(where: { $0.id == conversationID }),
                  let messageIndex = conversations[conversationIndex].messages.firstIndex(where: { $0.id == messageID })
            else { return }
            let currentStatus = conversations[conversationIndex].messages[messageIndex].status
            guard currentStatus == "sending" else { return }
            conversations[conversationIndex].messages[messageIndex].status = "sent"
        }
    }

    func consumeViewOnce(_ messageID: UUID, in conversationID: UUID) {
        guard let conversationIndex = conversations.firstIndex(where: { $0.id == conversationID }),
              let messageIndex = conversations[conversationIndex].messages.firstIndex(where: { $0.id == messageID }) else { return }
        let viewerID = firebaseUserID ?? "local"
        guard conversations[conversationIndex].messages[messageIndex].viewOnce,
              !conversations[conversationIndex].messages[messageIndex].mine,
              !conversations[conversationIndex].messages[messageIndex].viewedByIDs.contains(viewerID) else { return }
        conversations[conversationIndex].messages[messageIndex].viewedByIDs.append(viewerID)
        if conversations[conversationIndex].remoteID != nil,
           conversations[conversationIndex].messages[messageIndex].remoteID != nil {
            markFirebaseViewOnce(conversation: conversations[conversationIndex], message: conversations[conversationIndex].messages[messageIndex])
        }
    }

    func createMoment(title: String, text: String) {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanTitle.count >= 2, cleanText.count >= 3 else { return }
        moments.insert(WhappyMoment(id: UUID(), title: cleanTitle, text: cleanText, createdAt: Date()), at: 0)
    }

    func refreshStories() {
        guard firebaseUserID != nil else { return }
        Functions.functions(region: "europe-west1").httpsCallable("listVisibleStories").call { [weak self] result, error in
            guard let self else { return }
            Task { @MainActor in
                guard error == nil, let root = result?.data as? [String: Any], let rawStories = root["stories"] as? [[String: Any]] else { return }
                let now = Date()
                self.stories = rawStories.compactMap { value in
                    guard let id = value["id"] as? String, !id.isEmpty else { return nil }
                    let created = Self.storyDate(value["createdAt"] as? String) ?? now
                    let expiry = (value["expiresAtMillis"] as? NSNumber).map { Date(timeIntervalSince1970: $0.doubleValue / 1_000) } ?? created.addingTimeInterval(24 * 60 * 60)
                    return WapiStory(id: id, authorID: value["authorId"] as? String ?? "", authorName: value["authorName"] as? String ?? "Contact WAPI", authorPhotoURL: value["authorPhotoUrl"] as? String ?? "", caption: value["caption"] as? String ?? "", mediaURL: value["mediaUrl"] as? String ?? "", mediaType: value["mediaType"] as? String ?? "text", createdAt: created, expiresAt: expiry, viewCount: (value["viewCount"] as? NSNumber)?.intValue ?? 0, viewed: value["viewedByCurrentUser"] as? Bool ?? false)
                }.filter { $0.expiresAt > now }.sorted { $0.createdAt > $1.createdAt }
            }
        }
    }

    func markStoryViewed(_ story: WapiStory) {
        guard !story.viewed, story.authorID != firebaseUserID else { return }
        Functions.functions(region: "europe-west1").httpsCallable("recordStoryView").call(["storyId": story.id]) { [weak self] result, error in
            guard let self, error == nil else { return }
            Task { @MainActor in
                if let index = self.stories.firstIndex(where: { $0.id == story.id }) {
                    self.stories[index].viewed = true
                    let payload = result?.data as? [String: Any]
                    self.stories[index].viewCount = (payload?["viewCount"] as? NSNumber)?.intValue
                        ?? (payload?["viewCount"] as? Int)
                        ?? self.stories[index].viewCount
                }
            }
        }
    }

    func publishStory(caption: String, mediaData: Data? = nil, mediaType: String = "text", contentType: String = "") async throws {
        guard let userID = firebaseUserID else { throw NSError(domain: "WAPI", code: 401, userInfo: [NSLocalizedDescriptionKey: "Connectez-vous à WAPI pour publier."]) }
        let cleanCaption = String(caption.trimmingCharacters(in: .whitespacesAndNewlines).prefix(600))
        guard !cleanCaption.isEmpty || mediaData != nil else { throw NSError(domain: "WAPI", code: 400, userInfo: [NSLocalizedDescriptionKey: "Ajoutez un texte ou un média."]) }
        var mediaURL = ""
        var storagePath = ""
        if let mediaData {
            guard ["image", "video", "audio"].contains(mediaType) else { throw NSError(domain: "WAPI", code: 400, userInfo: [NSLocalizedDescriptionKey: "Type de média non pris en charge."]) }
            let ext = mediaType == "image" ? "jpg" : mediaType == "video" ? "mp4" : "m4a"
            storagePath = "stories/\(userID)/\(UUID().uuidString).\(ext)"
            let reference = Storage.storage().reference().child(storagePath)
            let metadata = StorageMetadata(); metadata.contentType = contentType.isEmpty ? (mediaType == "image" ? "image/jpeg" : mediaType == "video" ? "video/mp4" : "audio/mp4") : contentType
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                reference.putData(mediaData, metadata: metadata) { _, error in if let error { continuation.resume(throwing: error) } else { continuation.resume() } }
            }
            mediaURL = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
                reference.downloadURL { url, error in if let error { continuation.resume(throwing: error) } else if let url { continuation.resume(returning: url.absoluteString) } else { continuation.resume(throwing: NSError(domain: "WAPI", code: 500)) } }
            }
        }
        let result = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[String: Any], Error>) in
            Functions.functions(region: "europe-west1").httpsCallable("publishStory").call(["caption": cleanCaption, "mediaType": mediaType, "mediaUrl": mediaURL, "storagePath": storagePath]) { result, error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume(returning: result?.data as? [String: Any] ?? [:]) }
            }
        }
        guard let id = result["id"] as? String else { throw NSError(domain: "WAPI", code: 500, userInfo: [NSLocalizedDescriptionKey: "La Story n’a pas reçu d’identifiant."]) }
        let now = Date()
        let created = (result["createdAtMillis"] as? NSNumber).map { Date(timeIntervalSince1970: $0.doubleValue / 1_000) } ?? now
        let expiry = (result["expiresAtMillis"] as? NSNumber).map { Date(timeIntervalSince1970: $0.doubleValue / 1_000) } ?? created.addingTimeInterval(24 * 60 * 60)
        stories.insert(WapiStory(id: id, authorID: userID, authorName: result["authorName"] as? String ?? "Membre WAPI", authorPhotoURL: result["authorPhotoUrl"] as? String ?? "", caption: cleanCaption, mediaURL: mediaURL, mediaType: mediaType, createdAt: created, expiresAt: expiry, viewCount: 0, viewed: true), at: 0)
    }

    private static func storyDate(_ value: String?) -> Date? {
        guard let value else { return nil }
        let formatter = ISO8601DateFormatter()
        return formatter.date(from: value)
    }

    func createConversation(name: String, phone: String) {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanName.count >= 2, cleanPhone.count >= 8 else { return }
        if firebaseUserID != nil {
            createFirebaseConversation(name: cleanName, phone: cleanPhone)
            return
        }
        let initials = cleanName.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
        conversations.insert(Conversation(id: UUID(), name: cleanName, initials: initials, phoneNumber: cleanPhone, lastMessage: "Nouvelle conversation", unread: false, messages: []), at: 0)
    }

    func createChannel(name: String, description: String, category: String) {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDescription = description.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanName.count >= 3, cleanName.count <= 80, cleanDescription.count >= 10, cleanDescription.count <= 300 else { return }
        channels.insert(WhappyChannel(id: UUID(), name: cleanName, description: cleanDescription, category: category, ownerName: whappyFounderName, owner: true, subscribed: true, memberCount: 1, verified: false, posts: []), at: 0)
    }

    func toggleChannelSubscription(_ channel: WhappyChannel) {
        guard let index = channels.firstIndex(where: { $0.id == channel.id }), !channels[index].owner else { return }
        channels[index].subscribed.toggle()
        channels[index].memberCount = max(1, channels[index].memberCount + (channels[index].subscribed ? 1 : -1))
    }

    func publish(_ text: String, toChannel channelID: UUID) {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, value.count <= 4_000, let index = channels.firstIndex(where: { $0.id == channelID }), channels[index].owner else { return }
        channels[index].posts.append(WhappyChannelPost(id: UUID(), text: value, authorName: channels[index].ownerName, createdAt: Date()))
    }

    func react(toChannelPost postID: UUID, channelID: UUID, emoji: String) {
        guard let channel = channels.firstIndex(where: { $0.id == channelID }), channels[channel].subscribed, let post = channels[channel].posts.firstIndex(where: { $0.id == postID }) else { return }
        channels[channel].posts[post].reactions["me"] = emoji
    }

    func togglePinnedChannelPost(_ postID: UUID, channelID: UUID) {
        guard let channel = channels.firstIndex(where: { $0.id == channelID }), channels[channel].owner, let post = channels[channel].posts.firstIndex(where: { $0.id == postID }) else { return }
        channels[channel].posts[post].pinned.toggle()
    }

    func deleteChannelPost(_ postID: UUID, channelID: UUID) {
        guard let channel = channels.firstIndex(where: { $0.id == channelID }), channels[channel].owner, let post = channels[channel].posts.firstIndex(where: { $0.id == postID }) else { return }
        channels[channel].posts[post].text = "Publication supprimée"
        channels[channel].posts[post].deleted = true
        channels[channel].posts[post].pinned = false
    }

    func recordCall(name: String, phone: String, mode: CallMode) {
        calls.insert(CallRecord(id: UUID(), name: name, phoneNumber: phone, mode: mode, date: Date(), outgoing: true, missed: false), at: 0)
    }

    func addListing(title: String, price: String, place: String, trade: Bool) {
        let value = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard value.count >= 2 else { return }
        listings.insert(Listing(id: UUID(), title: value, price: price.isEmpty ? "À discuter" : price, place: place.isEmpty ? "Brazzaville" : place, seller: whappyFounderName, icon: trade ? "arrow.triangle.2.circlepath" : "shippingbox.fill", acceptsTrade: trade), at: 0)
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
        // Le Live natif utilise désormais WapiLiveDirectory et le callable
        // createLiveSession. Cette ancienne API locale reste neutre pour la
        // compatibilité de restauration, sans produire de faux direct.
        nil
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

    func saveBusiness(name: String, category: String, bio: String, city: String, phone: String, website: String) {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanName.count >= 2 else { return }
        let existingRemoteID = business?.remoteID ?? ""
        let remoteID = existingRemoteID.isEmpty ? UUID().uuidString.lowercased() : existingRemoteID
        let next = WhappyBusiness(id: business?.id ?? UUID(), remoteID: remoteID, name: cleanName, category: category.trimmingCharacters(in: .whitespacesAndNewlines), bio: bio.trimmingCharacters(in: .whitespacesAndNewlines), city: city.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Brazzaville" : city.trimmingCharacters(in: .whitespacesAndNewlines), phone: phone.trimmingCharacters(in: .whitespacesAndNewlines), website: website.trimmingCharacters(in: .whitespacesAndNewlines))
        business = next
        switchAccount(business: true)
        guard let uid = firebaseUserID else { return }
        let handle = cleanName.lowercased().replacingOccurrences(of: "[^a-z0-9]+", with: "-", options: .regularExpression).trimmingCharacters(in: CharacterSet(charactersIn: "-")) + "-" + String(remoteID.prefix(5))
        firebaseBusy = true
        var payload: [String: Any] = [
            "ownerId": uid,
            "name": next.name,
            "searchName": next.name.lowercased(),
            "type": "business",
            "category": next.category,
            "bio": String(next.bio.prefix(400)),
            "city": next.city,
            "phone": String(next.phone.prefix(30)),
            "website": String(next.website.prefix(180)),
            "onboardingComplete": true,
            "status": "active",
            "updatedAt": FieldValue.serverTimestamp(),
        ]
        // Firestore intentionally keeps the public handle immutable after creation.
        // This lets a business rename itself without breaking existing links.
        if existingRemoteID.isEmpty {
            payload["handle"] = handle
            payload["followers"] = 0
            payload["verified"] = false
            payload["verificationStatus"] = "unverified"
        }
        Firestore.firestore().collection("businessPages").document(remoteID).setData(payload, merge: true) { [weak self] error in
            Task { @MainActor in
                guard let self else { return }
                self.firebaseBusy = false
                self.firebaseMessage = error.map { wapiUserFacingError($0, action: "La synchronisation du compte Business") } ?? "Compte Business synchronisé dans WAPI."
            }
        }
    }

    func switchAccount(business: Bool) {
        guard !business || self.business != nil else { return }
        activeBusinessMode = business
        activeBusinessRemoteID = business ? (self.business?.remoteID ?? "") : ""
        if let uid = firebaseUserID {
            Firestore.firestore().collection("users").document(uid).setData([
                "activeProfileType": business ? "business" : "personal",
                "activeBusinessPageId": business ? activeBusinessRemoteID : "",
                "updatedAt": FieldValue.serverTimestamp(),
            ], merge: true)
        }
    }

    private func restore() {
        conversations = decode("conversations") ?? conversations
        channels = decode("channels") ?? channels
        listings = decode("listings") ?? listings
        liveRooms = decode("liveRooms") ?? liveRooms
        calls = decode("calls") ?? calls
        cart = decode("cart") ?? cart
        orders = decode("orders") ?? orders
        walletTransactions = decode("walletTransactions") ?? walletTransactions
        serviceRequests = decode("serviceRequests") ?? serviceRequests
        moments = decode("moments") ?? moments
        stories = decode("stories") ?? stories
        business = decode("business") ?? business
        activeBusinessMode = defaults.object(forKey: "activeBusinessMode") as? Bool ?? activeBusinessMode
        activeBusinessRemoteID = defaults.string(forKey: "activeBusinessRemoteID") ?? activeBusinessRemoteID
        walletBalance = defaults.object(forKey: "walletBalance") as? Int ?? walletBalance
        notificationsEnabled = defaults.object(forKey: "notificationsEnabled") as? Bool ?? notificationsEnabled
        privacyMode = defaults.string(forKey: "privacyMode") ?? privacyMode
        dataSaverEnabled = defaults.object(forKey: "dataSaverEnabled") as? Bool ?? dataSaverEnabled
        interfaceLanguage = WapiInterfaceLanguage(rawValue: defaults.string(forKey: "interfaceLanguage") ?? "") ?? interfaceLanguage
    }

    private func decode<T: Decodable>(_ key: String) -> T? {
        guard let data = defaults.data(forKey: "whappy.\(key)") else { return nil }
        return try? decoder.decode(T.self, from: data)
    }

    private func save() {
        guard !restoring else { return }
        encode(conversations, "conversations")
        encode(channels, "channels")
        encode(listings, "listings")
        encode(liveRooms, "liveRooms")
        encode(calls, "calls")
        encode(cart, "cart")
        encode(orders, "orders")
        encode(walletTransactions, "walletTransactions")
        encode(serviceRequests, "serviceRequests")
        encode(moments, "moments")
        encode(stories, "stories")
        encode(business, "business")
        defaults.set(activeBusinessMode, forKey: "activeBusinessMode")
        defaults.set(activeBusinessRemoteID, forKey: "activeBusinessRemoteID")
        defaults.set(walletBalance, forKey: "walletBalance")
        defaults.set(notificationsEnabled, forKey: "notificationsEnabled")
        defaults.set(privacyMode, forKey: "privacyMode")
        defaults.set(dataSaverEnabled, forKey: "dataSaverEnabled")
        defaults.set(interfaceLanguage.rawValue, forKey: "interfaceLanguage")
    }

    private func encode<T: Encodable>(_ value: T, _ key: String) {
        if let data = try? encoder.encode(value) { defaults.set(data, forKey: "whappy.\(key)") }
    }
}
