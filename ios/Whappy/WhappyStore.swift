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
    @Published private(set) var recentSpaces: [WhappyTab] = []
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
    /// Story uploads are deliberately optimistic: the circle appears as soon
    /// as its author publishes, then the media is synchronised in background.
    /// Keeping these identifiers separate prevents a transient Firestore
    /// refresh from making a fresh Story disappear from the rail.
    @Published private(set) var pendingStoryIDs: Set<String> = []
    @Published var business: WhappyBusiness? { didSet { save() } }
    @Published var activeBusinessMode: Bool { didSet { save() } }
    @Published var activeBusinessRemoteID: String { didSet { save() } }
    @Published var notificationsEnabled: Bool { didSet { save() } }
    @Published var privacyMode: String { didSet { save() } }
    @Published var dataSaverEnabled: Bool { didSet { save() } }
    @Published var interfaceLanguage: WapiInterfaceLanguage { didSet { save() } }
    @Published var pendingContactPhone: String?
    @Published var pendingChannelID: UUID?
    @Published var pendingConversationID: UUID?
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

    /// Conversations are scoped to the selected account context. A Business
    /// conversation must never appear in the personal inbox (or the reverse),
    /// even when both contexts use the same authenticated phone number.
    var accountConversations: [Conversation] {
        conversations.filter { conversation in
            if activeBusinessMode {
                return conversation.profileType == "business"
                    && (activeBusinessRemoteID.isEmpty || conversation.businessPageID == activeBusinessRemoteID)
            }
            return conversation.profileType != "business"
        }
    }

    var unreadCount: Int { accountConversations.filter(\.unread).count }
    var cartCount: Int { cart.reduce(0) { $0 + $1.quantity } }

    /// The pull-down drawer in Messages mirrors a super-app's recent spaces.
    /// Core communication tabs stay out of this list so it remains useful.
    func rememberRecentSpace(_ tab: WhappyTab) {
        let discoverable: Set<WhappyTab> = [.home, .actus, .wia, .market, .live, .games, .services, .profile]
        guard discoverable.contains(tab) else { return }
        recentSpaces.removeAll { $0 == tab }
        recentSpaces.insert(tab, at: 0)
        recentSpaces = Array(recentSpaces.prefix(6))
        defaults.set(recentSpaces.map(\.rawValue), forKey: "recentSpaces")
    }

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
        // Direct calls are native WebRTC calls. The invitation is the signed
        // Firestore document, so declining works even while the app is waking
        // from a notification and does not depend on a separate media room.
        Firestore.firestore().collection("calls").document(callID).updateData([
            "status": "declined",
            "updatedAt": FieldValue.serverTimestamp(),
        ]) { error in
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
                let remoteStories = rawStories
                    .compactMap { self.decodeStory($0, fallbackDate: now) }
                    .filter { $0.expiresAt > now }
                    .sorted { $0.createdAt > $1.createdAt }
                let pendingStories = self.stories.filter { self.pendingStoryIDs.contains($0.id) && $0.expiresAt > now }
                self.stories = (pendingStories + remoteStories)
                    .reduce(into: [String: WapiStory]()) { current, story in
                        // A remote record replaces its local placeholder as
                        // soon as the callable has confirmed publication.
                        current[story.id] = story
                    }
                    .values
                    .sorted { $0.createdAt > $1.createdAt }
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
        guard mediaData == nil || ["image", "video", "audio"].contains(mediaType) else { throw NSError(domain: "WAPI", code: 400, userInfo: [NSLocalizedDescriptionKey: "Type de média non pris en charge."]) }
        let maximumBytes = mediaType == "video" ? 50 * 1_024 * 1_024 : mediaType == "audio" ? 25 * 1_024 * 1_024 : 12 * 1_024 * 1_024
        guard mediaData == nil || (!mediaData!.isEmpty && mediaData!.count <= maximumBytes) else {
            throw NSError(domain: "WAPI", code: 413, userInfo: [NSLocalizedDescriptionKey: "Ce média dépasse la taille autorisée pour une Story WAPI."])
        }
        let pendingID = "pending-story-\(UUID().uuidString)"
        let now = Date()
        let localMediaURL = try localStoryMediaURL(for: mediaData, mediaType: mediaType, pendingID: pendingID)
        let localStory = WapiStory(
            id: pendingID,
            authorID: userID,
            authorName: Auth.auth().currentUser?.displayName ?? Auth.auth().currentUser?.phoneNumber ?? "Membre WAPI",
            authorPhotoURL: Auth.auth().currentUser?.photoURL?.absoluteString ?? "",
            caption: cleanCaption,
            mediaURL: localMediaURL,
            mediaType: mediaType,
            createdAt: now,
            expiresAt: now.addingTimeInterval(24 * 60 * 60),
            viewCount: 0,
            viewed: true,
        )
        pendingStoryIDs.insert(pendingID)
        stories.removeAll { $0.id == pendingID }
        stories.insert(localStory, at: 0)

        // The composer may now close immediately, exactly as a native mobile
        // Story flow.  Upload, callable publication and Firestore convergence
        // continue without blocking the user's next action.
        Task { [weak self] in
            guard let self else { return }
            do {
                let published = try await self.uploadStory(
                    userID: userID,
                    caption: cleanCaption,
                    mediaData: mediaData,
                    mediaType: mediaType,
                    contentType: contentType,
                )
                self.pendingStoryIDs.remove(pendingID)
                self.stories.removeAll { $0.id == pendingID || $0.id == published.id }
                self.stories.insert(published, at: 0)
                self.removeLocalStoryMedia(at: localMediaURL)
                self.refreshStories()
            } catch {
                // Do not remove the user's work on a temporary Firebase or
                // network failure.  The local Story remains visible instead
                // of looking as if publishing silently failed.
                self.pendingStoryIDs.remove(pendingID)
            }
        }
    }

    private func uploadStory(userID: String, caption cleanCaption: String, mediaData: Data?, mediaType: String, contentType: String) async throws -> WapiStory {
        var mediaURL = ""
        var storagePath = ""
        var serverImageData: Data?
        if let mediaData {
            if mediaType == "image" {
                serverImageData = try normalizedStoryImage(mediaData)
            } else {
                let ext = mediaType == "video" ? "mp4" : "m4a"
                storagePath = "stories/\(userID)/\(UUID().uuidString).\(ext)"
                let reference = Storage.storage().reference().child(storagePath)
                let metadata = StorageMetadata(); metadata.contentType = contentType.isEmpty ? (mediaType == "video" ? "video/mp4" : "audio/mp4") : contentType
                try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                    reference.putData(mediaData, metadata: metadata) { _, error in if let error { continuation.resume(throwing: error) } else { continuation.resume() } }
                }
                mediaURL = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
                    reference.downloadURL { url, error in if let error { continuation.resume(throwing: error) } else if let url { continuation.resume(returning: url.absoluteString) } else { continuation.resume(throwing: NSError(domain: "WAPI", code: 500)) } }
                }
            }
        }
        var payload: [String: Any] = ["caption": cleanCaption, "mediaType": mediaType, "mediaUrl": mediaURL, "storagePath": storagePath]
        if let serverImageData { payload["mediaDataBase64"] = serverImageData.base64EncodedString() }
        let result = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[String: Any], Error>) in
            Functions.functions(region: "europe-west1").httpsCallable("publishStory").call(payload) { result, error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume(returning: result?.data as? [String: Any] ?? [:]) }
            }
        }
        guard let id = result["id"] as? String else { throw NSError(domain: "WAPI", code: 500, userInfo: [NSLocalizedDescriptionKey: "La Story n’a pas reçu d’identifiant."]) }
        mediaURL = result["mediaUrl"] as? String ?? mediaURL
        let now = Date()
        let created = (result["createdAtMillis"] as? NSNumber).map { Date(timeIntervalSince1970: $0.doubleValue / 1_000) } ?? now
        let expiry = (result["expiresAtMillis"] as? NSNumber).map { Date(timeIntervalSince1970: $0.doubleValue / 1_000) } ?? created.addingTimeInterval(24 * 60 * 60)
        return WapiStory(id: id, authorID: userID, authorName: result["authorName"] as? String ?? "Membre WAPI", authorPhotoURL: result["authorPhotoUrl"] as? String ?? "", caption: cleanCaption, mediaURL: mediaURL, mediaType: mediaType, createdAt: created, expiresAt: expiry, viewCount: 0, viewed: true)
    }

    private func normalizedStoryImage(_ data: Data) throws -> Data {
        guard let image = UIImage(data: data) else { throw NSError(domain: "WAPI", code: 400, userInfo: [NSLocalizedDescriptionKey: "Cette image ne peut pas être lue."]) }
        let longest = max(image.size.width, image.size.height)
        let scale = min(1, 2_048 / max(longest, 1))
        let size = CGSize(width: max(1, image.size.width * scale), height: max(1, image.size.height * scale))
        let rendered = UIGraphicsImageRenderer(size: size).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        for quality in [0.88, 0.78, 0.68] {
            if let encoded = rendered.jpegData(compressionQuality: quality), encoded.count <= 5 * 1_024 * 1_024 { return encoded }
        }
        throw NSError(domain: "WAPI", code: 413, userInfo: [NSLocalizedDescriptionKey: "Cette image reste trop volumineuse après optimisation."])
    }

    private func localStoryMediaURL(for data: Data?, mediaType: String, pendingID: String) throws -> String {
        guard let data else { return "" }
        let ext = mediaType == "image" ? "jpg" : mediaType == "video" ? "mp4" : "m4a"
        let directory = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("WAPI/story-outbox", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent("\(pendingID).\(ext)")
        try data.write(to: file, options: .atomic)
        return file.absoluteString
    }

    private func removeLocalStoryMedia(at value: String) {
        guard let url = URL(string: value), url.isFileURL else { return }
        try? FileManager.default.removeItem(at: url)
    }

    private func decodeStory(_ value: [String: Any], fallbackDate: Date) -> WapiStory? {
        guard let id = value["id"] as? String, !id.isEmpty else { return nil }
        let createdMillis = (value["createdAtMillis"] as? NSNumber)?.doubleValue
        let created = createdMillis.map { Date(timeIntervalSince1970: $0 / 1_000) }
            ?? Self.storyDate(value["createdAt"] as? String)
            ?? fallbackDate
        let expiryMillis = (value["expiresAtMillis"] as? NSNumber)?.doubleValue
        let expiry = expiryMillis.map { Date(timeIntervalSince1970: $0 / 1_000) }
            ?? created.addingTimeInterval(24 * 60 * 60)
        let views = (value["viewCount"] as? NSNumber)?.intValue ?? (value["viewCount"] as? Int ?? 0)
        return WapiStory(
            id: id,
            authorID: value["authorId"] as? String ?? "",
            authorName: value["authorName"] as? String ?? "Contact WAPI",
            authorPhotoURL: value["authorPhotoUrl"] as? String ?? "",
            caption: value["caption"] as? String ?? "",
            mediaURL: value["mediaUrl"] as? String ?? "",
            mediaType: value["mediaType"] as? String ?? "text",
            createdAt: created,
            expiresAt: expiry,
            viewCount: views,
            viewed: value["viewedByCurrentUser"] as? Bool ?? false,
        )
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
        let next = WhappyBusiness(id: business?.id ?? UUID(), remoteID: remoteID, name: cleanName, category: category.trimmingCharacters(in: .whitespacesAndNewlines), bio: bio.trimmingCharacters(in: .whitespacesAndNewlines), city: city.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Brazzaville" : city.trimmingCharacters(in: .whitespacesAndNewlines), phone: phone.trimmingCharacters(in: .whitespacesAndNewlines), website: website.trimmingCharacters(in: .whitespacesAndNewlines), logoURL: business?.logoURL ?? "")
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
        if !next.logoURL.isEmpty { payload["logoUrl"] = next.logoURL }
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

    /// Uploads a normalized logo to the business-owned Storage path.  The
    /// active profile updates immediately after Firestore accepts the URL, so
    /// a Business account never falls back to the personal avatar.
    func uploadBusinessLogo(_ data: Data) {
        guard let userID = firebaseUserID,
              var currentBusiness = business,
              !currentBusiness.remoteID.isEmpty,
              !data.isEmpty,
              data.count <= 5 * 1_024 * 1_024 else {
            firebaseMessage = "Créez d’abord le compte Business puis choisissez une image de moins de 5 Mo."
            return
        }
        let reference = Storage.storage().reference().child("business/\(userID)/\(currentBusiness.remoteID)/logo-\(UUID().uuidString).jpg")
        let metadata = StorageMetadata(); metadata.contentType = "image/jpeg"
        firebaseBusy = true
        reference.putData(data, metadata: metadata) { [weak self] _, error in
            guard let self else { return }
            if let error {
                Task { @MainActor in self.firebaseBusy = false; self.firebaseMessage = self.friendlyFirebaseError(error) }
                return
            }
            reference.downloadURL { url, error in
                guard let url, error == nil else {
                    Task { @MainActor in self.firebaseBusy = false; self.firebaseMessage = error.map(self.friendlyFirebaseError) ?? "Le logo Business n’a pas pu être envoyé." }
                    return
                }
                Firestore.firestore().collection("businessPages").document(currentBusiness.remoteID).setData([
                    "logoUrl": url.absoluteString,
                    "updatedAt": FieldValue.serverTimestamp(),
                ], merge: true) { writeError in
                    Task { @MainActor in
                        self.firebaseBusy = false
                        guard writeError == nil else {
                            self.firebaseMessage = self.friendlyFirebaseError(writeError!)
                            return
                        }
                        currentBusiness.logoURL = url.absoluteString
                        self.business = currentBusiness
                        self.firebaseMessage = "Logo Business enregistré dans WAPI."
                    }
                }
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
        recentSpaces = (defaults.stringArray(forKey: "recentSpaces") ?? [])
            .compactMap(WhappyTab.init(rawValue:))
            .filter { $0 != .messages && $0 != .calls }
            .prefix(6)
            .map { $0 }
        conversations = decode("conversations") ?? conversations
        channels = decode("channels") ?? channels
        listings = decode("listings") ?? listings
        liveRooms = decode("liveRooms") ?? liveRooms
        calls = decode("calls") ?? calls
        cart = decode("cart") ?? cart
        orders = decode("orders") ?? orders
        walletTransactions = decode("walletTransactions") ?? walletTransactions
        // Remove legacy local-only wallet samples. A visible WAPI balance must
        // now come exclusively from confirmed provider transactions.
        walletTransactions.removeAll { transaction in
            let label = transaction.label.lowercased()
            return label.contains("démonstration") || label.contains("paiement test")
        }
        serviceRequests = decode("serviceRequests") ?? serviceRequests
        moments = decode("moments") ?? moments
        stories = decode("stories") ?? stories
        business = decode("business") ?? business
        activeBusinessMode = defaults.object(forKey: "activeBusinessMode") as? Bool ?? activeBusinessMode
        activeBusinessRemoteID = defaults.string(forKey: "activeBusinessRemoteID") ?? activeBusinessRemoteID
        walletBalance = defaults.object(forKey: "walletBalance") as? Int ?? walletBalance
        if walletTransactions.isEmpty { walletBalance = 0 }
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
