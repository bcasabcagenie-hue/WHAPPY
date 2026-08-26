@preconcurrency import FirebaseAuth
import FirebaseCore
@preconcurrency import FirebaseFirestore
import FirebaseStorage
import FirebaseMessaging
import FirebaseFunctions
import SwiftUI
import CryptoKit
import UniformTypeIdentifiers

private enum WapiTranslationError: LocalizedError {
    case invalidText
    case unsupportedLanguage
    case invalidResponse

    var errorDescription: String? {
        switch self {
        case .invalidText: return "Le message à traduire est vide ou trop long."
        case .unsupportedLanguage: return "Cette langue n’est pas encore disponible dans Lingwap."
        case .invalidResponse: return "Lingwap a renvoyé une réponse invalide."
        }
    }
}

extension WhappyStore {
    func configureFirebaseMessaging() {
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
        Auth.auth().languageCode = "fr"
        firebaseAuthHandle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor in
                guard let self else { return }
                if user == nil { self.setFirebasePresence(online: false, userID: self.firebaseUserID) }
                self.firebaseUserID = user?.uid
                self.firebaseSessionLoading = false
                self.firebaseBusy = false
                if let user {
                    self.startFirebaseConversationSync(userID: user.uid)
                    self.syncFirebaseProfile(user: user)
                    self.registerFirebasePushDevice()
                    self.setFirebasePresence(online: true)
                    self.drainPendingDirectCallDecline()
                } else {
                    self.stopFirebaseConversationSync()
                    self.conversations = []
                    self.firebaseProfileVerified = false
                }
            }
        }
    }

    func setFirebasePresence(online: Bool, userID: String? = nil) {
        let targetID = userID ?? firebaseUserID ?? Auth.auth().currentUser?.uid
        guard let targetID, !targetID.isEmpty else { return }
        firebasePresenceTimer?.invalidate()
        firebasePresenceTimer = nil
        var payload: [String: Any] = [
            "presenceState": online ? "online" : "offline",
            "presenceUpdatedAt": FieldValue.serverTimestamp()
        ]
        if !online { payload["lastSeenAt"] = FieldValue.serverTimestamp() }
        Firestore.firestore().collection("users").document(targetID).setData(payload, merge: true)
        guard online else { return }
        firebasePresenceTimer = Timer.scheduledTimer(withTimeInterval: 45, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.setFirebasePresence(online: true) }
        }
    }

    func requestFirebasePhoneCode(phone: String) {
        guard let normalized = WhappyPhoneCountry.normalize(phone) else {
            firebaseMessage = "Vérifiez le pays et le numéro de téléphone."
            return
        }
        firebaseBusy = true
        firebaseMessage = nil
        PhoneAuthProvider.provider().verifyPhoneNumber(normalized, uiDelegate: nil) { [weak self] verificationID, error in
            Task { @MainActor in
                guard let self else { return }
                self.firebaseBusy = false
                if let error {
                    self.firebaseMessage = self.friendlyFirebaseError(error)
                    return
                }
                self.firebaseVerificationID = verificationID
                self.firebaseCodeSent = verificationID != nil
                self.firebaseMessage = verificationID == nil ? "Le code SMS n’a pas pu être envoyé." : "Code SMS envoyé au \(normalized)."
            }
        }
    }

    func confirmFirebasePhoneCode(_ code: String) {
        let value = code.filter(\.isNumber)
        guard value.count == 6, let verificationID = firebaseVerificationID else {
            firebaseMessage = "Entrez le code SMS à 6 chiffres."
            return
        }
        firebaseBusy = true
        firebaseMessage = nil
        let credential = PhoneAuthProvider.provider().credential(withVerificationID: verificationID, verificationCode: value)
        Auth.auth().signIn(with: credential) { [weak self] _, error in
            Task { @MainActor in
                guard let self else { return }
                self.firebaseBusy = false
                if let error { self.firebaseMessage = self.friendlyFirebaseError(error) }
            }
        }
    }

    func signOutFirebase() {
        try? Auth.auth().signOut()
    }

    func registerFirebasePushDevice() {
        guard let userID = firebaseUserID,
              let token = UserDefaults.standard.string(forKey: "wapi.ios.fcm-token"),
              token.count >= 20 else { return }
        let deviceIDKey = "wapi.ios.device-id"
        let deviceID = UserDefaults.standard.string(forKey: deviceIDKey) ?? UUID().uuidString
        UserDefaults.standard.set(deviceID, forKey: deviceIDKey)
        Firestore.firestore().collection("users").document(userID).collection("devices").document(deviceID).setData([
            "token": token,
            "platform": "ios",
            "enabled": true,
            "updatedAt": FieldValue.serverTimestamp()
        ], merge: true)
    }

    func openFirebaseConversation(_ conversation: Conversation) {
        guard let remoteID = conversation.remoteID else { return }
        firebaseMessageListener?.remove()
        let collection = Firestore.firestore().collection(conversation.source == "groups" ? "groups" : "conversations")
        firebaseMessageListener = collection.document(remoteID).collection("messages")
            .order(by: "createdAt", descending: false)
            .limit(toLast: 500)
            .addSnapshotListener { [weak self] snapshot, error in
                Task { @MainActor in
                    guard let self else { return }
                    if let error {
                        self.firebaseMessage = self.friendlyFirebaseError(error)
                        return
                    }
                    guard let index = self.conversations.firstIndex(where: { $0.remoteID == remoteID }) else { return }
                    let userID = self.firebaseUserID.orEmpty
                    self.conversations[index].messages = snapshot?.documents.map { self.firebaseMessage(from: $0, userID: userID) } ?? []
                    if conversation.source != "groups", let userID = self.firebaseUserID {
                        collection.document(remoteID).updateData(["readBy.\(userID)": FieldValue.serverTimestamp()])
                    }
                }
            }
    }

    func closeFirebaseConversation() {
        firebaseMessageListener?.remove()
        firebaseMessageListener = nil
    }

    func sendFirebaseMessage(_ text: String, conversation: Conversation, replyTo: Message?) {
        guard let remoteID = conversation.remoteID, let userID = firebaseUserID else { return }
        let root = Firestore.firestore().collection(conversation.source == "groups" ? "groups" : "conversations").document(remoteID)
        let reference = root.collection("messages").document()
        let now = Date()
        let local = Message(
            id: stableFirebaseUUID(reference.documentID),
            text: text,
            mine: true,
            sentAt: now,
            replyToID: replyTo?.id,
            replyText: replyTo?.text,
            status: "sending",
            remoteID: reference.documentID,
            senderID: userID,
            senderName: "Vous"
        )
        appendOptimisticFirebaseMessage(local, conversationID: conversation.id)
        var data: [String: Any] = [
            "text": text,
            "senderId": userID,
            "createdAt": Timestamp(date: now)
        ]
        if conversation.source == "groups" { data["senderName"] = currentFirebaseSenderName }
        if let replyTo, let replyID = replyTo.remoteID {
            data["replyToId"] = replyID
            data["replyText"] = String(replyTo.text.prefix(240))
        }
        reference.setData(data) { [weak self] error in
            Task { @MainActor [weak self] in
                self?.finishFirebaseSend(messageID: local.id, conversationID: conversation.id, error: error)
                guard error == nil else { return }
                self?.updateFirebaseConversationSummary(root: root, source: conversation.source ?? "conversations", text: text, userID: userID)
            }
        }
    }

    func sendFirebaseMedia(kind: String, path: String, mediaName: String? = nil, viewOnce: Bool = false, conversation: Conversation) {
        guard let remoteID = conversation.remoteID, let userID = firebaseUserID else { return }
        guard ["image", "audio", "video", "document"].contains(kind),
              (!viewOnce || kind != "document") else {
            firebaseMessage = "Ce type de média ne peut pas être envoyé ainsi."
            return
        }
        let fileURL = URL(fileURLWithPath: path)
        guard let fileData = try? Data(contentsOf: fileURL), !fileData.isEmpty else {
            firebaseMessage = "Ce média n’est plus disponible sur cet appareil."
            return
        }
        let maxBytes: Int = switch kind {
        case "image": 20 * 1024 * 1024
        case "audio": 12 * 1024 * 1024
        case "video": 60 * 1024 * 1024
        default: 25 * 1024 * 1024
        }
        guard fileData.count <= maxBytes else {
            firebaseMessage = "Ce média dépasse la taille autorisée par WAPI."
            return
        }
        let label = switch kind {
        case "audio": "🎤 Note vocale"
        case "video": "🎬 Vidéo"
        case "document": "Waphsare · Document"
        default: "📷 Photo"
        }
        let rootName = conversation.source == "groups" ? "groups" : "conversations"
        let originalName = (mediaName ?? fileURL.lastPathComponent).trimmingCharacters(in: .whitespacesAndNewlines)
        let originalExtension = fileURL.pathExtension.isEmpty ? fallbackExtension(for: kind) : fileURL.pathExtension.lowercased()
        let safeName = String((originalName.isEmpty ? "wapi-\(kind).\(originalExtension)" : originalName).prefix(120))
        let fileName = "ios-\(UUID().uuidString).\(originalExtension)"
        let object = Storage.storage().reference().child("\(rootName)/\(remoteID)/\(userID)/\(fileName)")
        let metadata = StorageMetadata()
        metadata.contentType = mediaContentType(for: fileURL, kind: kind)
        firebaseBusy = true
        object.putFile(from: URL(fileURLWithPath: path), metadata: metadata) { [weak self] _, error in
            guard let self else { return }
            if let error {
                Task { @MainActor in self.firebaseBusy = false; self.firebaseMessage = self.friendlyFirebaseError(error) }
                return
            }
            object.downloadURL { url, error in
                Task { @MainActor in
                    self.firebaseBusy = false
                    if let error { self.firebaseMessage = self.friendlyFirebaseError(error); return }
                    guard let url else { self.firebaseMessage = "Le média n’a pas pu être envoyé."; return }
                    self.writeFirebaseMediaMessage(label: label, kind: kind, mediaURL: url.absoluteString, fileName: safeName, mediaSizeBytes: fileData.count, mediaSha256: SHA256.hash(data: fileData).compactMap { String(format: "%02x", $0) }.joined(), viewOnce: viewOnce, conversation: conversation)
                }
            }
        }
    }

    func reactFirebase(conversation: Conversation, message: Message, emoji: String) {
        guard let conversationID = conversation.remoteID, let messageID = message.remoteID, let userID = firebaseUserID else { return }
        firebaseMessageReference(conversation: conversation, conversationID: conversationID, messageID: messageID)
            .updateData(["reactions.\(userID)": emoji])
    }

    func markFirebaseViewOnce(conversation: Conversation, message: Message) {
        guard let conversationID = conversation.remoteID,
              let messageID = message.remoteID,
              let userID = firebaseUserID else { return }
        firebaseMessageReference(conversation: conversation, conversationID: conversationID, messageID: messageID)
            .updateData(["viewedBy.\(userID)": FieldValue.serverTimestamp()])
    }

    func updateFirebaseGroup(conversation: Conversation, name rawName: String, photoData: Data?, removePhoto: Bool) {
        guard let source = conversation.source,
              ["groups", "conversations"].contains(source),
              let groupID = conversation.remoteID,
              let userID = firebaseUserID else { return }
        let name = rawName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard (2...80).contains(name.count) else {
            firebaseMessage = "Le nom du groupe doit contenir entre 2 et 80 caractères."
            return
        }
        guard conversation.groupOwnerID == userID || conversation.groupAdminIDs.contains(userID) else {
            firebaseMessage = "Seuls les administrateurs peuvent modifier ce groupe."
            return
        }
        if let photoData, photoData.count > 20 * 1024 * 1024 {
            firebaseMessage = "La photo de groupe est trop volumineuse."
            return
        }
        firebaseBusy = true
        let save: (String) -> Void = { [weak self] photoURL in
            guard let self else { return }
            let changedName = name != conversation.name
            let changedPhoto = removePhoto || !photoURL.isEmpty
            guard changedName || changedPhoto else { self.firebaseBusy = false; return }
            Functions.functions(region: "europe-west1").httpsCallable("updateGroupIdentity").call([
                "groupId": groupID,
                "source": source,
                "name": name,
                "photoUrl": removePhoto ? "" : photoURL,
                "removePhoto": removePhoto
            ]) { _, error in
                Task { @MainActor in
                    self.firebaseBusy = false
                    if let error { self.firebaseMessage = self.friendlyFirebaseError(error) }
                }
            }
        }
        guard let photoData else { save(""); return }
        let storage = Storage.storage().reference().child("\(source)/\(groupID)/\(userID)/group-\(UUID().uuidString).jpg")
        let metadata = StorageMetadata()
        metadata.contentType = "image/jpeg"
        storage.putData(photoData, metadata: metadata) { [weak self] _, error in
            guard let self else { return }
            if let error {
                Task { @MainActor in self.firebaseBusy = false; self.firebaseMessage = self.friendlyFirebaseError(error) }
                return
            }
            storage.downloadURL { url, error in
                guard let url, error == nil else {
                    Task { @MainActor in self.firebaseBusy = false; self.firebaseMessage = error.map(self.friendlyFirebaseError) ?? "La photo du groupe n’a pas pu être envoyée." }
                    return
                }
                save(url.absoluteString)
            }
        }
    }

    func setFirebaseGroupAdministrator(conversation: Conversation, memberID: String, administrator: Bool) {
        guard conversation.source == "groups",
              let groupID = conversation.remoteID,
              let userID = firebaseUserID,
              conversation.groupOwnerID == userID,
              memberID != userID,
              conversation.groupMembers.contains(where: { $0.uid == memberID }) else { return }
        firebaseBusy = true
        Functions.functions(region: "europe-west1").httpsCallable("manageGroupAdministration").call([
            "groupId": groupID,
            "source": conversation.source ?? "groups",
            "memberId": memberID,
            "administrator": administrator
        ]) { [weak self] _, error in
            Task { @MainActor in
                self?.firebaseBusy = false
                if let error { self?.firebaseMessage = self?.friendlyFirebaseError(error) }
            }
        }
    }

    func uploadFirebaseProfilePhoto(_ data: Data) {
        guard let userID = firebaseUserID, !data.isEmpty else { return }
        let reference = Storage.storage().reference().child("profiles/\(userID)/avatar-\(UUID().uuidString).jpg")
        let metadata = StorageMetadata()
        metadata.contentType = "image/jpeg"
        firebaseBusy = true
        reference.putData(data, metadata: metadata) { [weak self] _, error in
            guard let self else { return }
            if let error {
                Task { @MainActor in self.firebaseBusy = false; self.firebaseMessage = self.friendlyFirebaseError(error) }
                return
            }
            reference.downloadURL { url, error in
                Task { @MainActor in
                    self.firebaseBusy = false
                    guard let url, error == nil else {
                        self.firebaseMessage = error.map(self.friendlyFirebaseError) ?? "La photo n’a pas pu être synchronisée."
                        return
                    }
                    Firestore.firestore().collection("users").document(userID).setData([
                        "photoUrl": url.absoluteString,
                        "updatedAt": FieldValue.serverTimestamp()
                    ], merge: true)
                }
            }
        }
    }

    func deleteFirebaseMessage(conversation: Conversation, message: Message) {
        guard let conversationID = conversation.remoteID, let messageID = message.remoteID else { return }
        firebaseMessageReference(conversation: conversation, conversationID: conversationID, messageID: messageID)
            .updateData(["text": "Message supprimé", "kind": "deleted", "mediaUrl": "", "mediaName": "", "deleted": true])
    }

    func editFirebaseMessage(conversation: Conversation, message: Message, text: String) {
        guard let conversationID = conversation.remoteID, let messageID = message.remoteID else { return }
        firebaseMessageReference(conversation: conversation, conversationID: conversationID, messageID: messageID)
            .updateData(["text": text, "edited": true])
    }

    func createFirebaseConversation(name: String, phone: String) {
        guard let user = Auth.auth().currentUser, let normalized = WhappyPhoneCountry.normalize(phone) else { return }
        firebaseBusy = true
        firebaseMessage = nil
        let database = Firestore.firestore()
        database.collection("users").whereField("phoneLookup", isEqualTo: normalized).limit(to: 1).getDocuments { [weak self] snapshot, error in
            guard let self else { return }
            guard error == nil, let peer = snapshot?.documents.first, peer.documentID != user.uid else {
                Task { @MainActor in self.firebaseBusy = false; self.firebaseMessage = error == nil ? "Ce numéro n’est pas encore inscrit sur WAPI." : self.friendlyFirebaseError(error!) }
                return
            }
            database.collection("conversations").whereField("memberIds", arrayContains: user.uid).getDocuments { existing, existingError in
                if let existingConversation = existing?.documents.first(where: { (($0.data()["memberIds"] as? [String]) ?? []).contains(peer.documentID) }) {
                    Task { @MainActor in
                        self.firebaseBusy = false
                        self.selectedTab = .messages
                        self.firebaseMessage = nil
                        if let conversation = self.conversations.first(where: { $0.remoteID == existingConversation.documentID }) { self.openFirebaseConversation(conversation) }
                    }
                    return
                }
                if let existingError {
                    Task { @MainActor in self.firebaseBusy = false; self.firebaseMessage = self.friendlyFirebaseError(existingError) }
                    return
                }
                let peerData = peer.data()
                let currentPhone = user.phoneNumber.orEmpty
                let directID = "direct-" + [user.uid, peer.documentID].sorted().joined(separator: "-")
                let members: [[String: Any]] = [
                    ["uid": user.uid, "displayName": user.displayName ?? "Membre WAPI", "phoneNumber": currentPhone, "photoUrl": user.photoURL?.absoluteString ?? ""],
                    ["uid": peer.documentID, "displayName": peerData["displayName"] as? String ?? name, "phoneNumber": peerData["phoneNumber"] as? String ?? normalized, "photoUrl": peerData["photoUrl"] as? String ?? ""]
                ]
                database.collection("conversations").document(directID).setData([
                    "ownerId": user.uid,
                    "memberIds": [user.uid, peer.documentID].sorted(),
                    "members": members,
                    "typingBy": [:],
                    "readBy": [:],
                    "contactId": peer.documentID,
                    "contactName": peerData["displayName"] as? String ?? name,
                    "lastMessage": "Nouvelle conversation",
                    "lastSenderId": "",
                    "createdAt": FieldValue.serverTimestamp(),
                    "updatedAt": FieldValue.serverTimestamp()
                ]) { creationError in
                    Task { @MainActor in
                        self.firebaseBusy = false
                        self.firebaseMessage = creationError.map(self.friendlyFirebaseError)
                    }
                }
            }
        }
    }

    private func startFirebaseConversationSync(userID: String) {
        stopFirebaseConversationSync()
        conversations = conversations.filter { $0.remoteID != nil }
        let database = Firestore.firestore()
        let direct = database.collection("conversations").whereField("memberIds", arrayContains: userID)
            .addSnapshotListener { [weak self] snapshot, error in
                Task { @MainActor in
                    guard let self else { return }
                    if let error { self.firebaseMessage = self.friendlyFirebaseError(error); return }
                    self.firebaseDirectConversations = snapshot?.documents.compactMap { self.firebaseConversation(from: $0, userID: userID, source: "conversations") } ?? []
                    self.refreshFirebasePresenceObservers()
                    self.publishFirebaseConversations()
                }
            }
        let groups = database.collection("groups").whereField("memberIds", arrayContains: userID)
            .addSnapshotListener { [weak self] snapshot, error in
                Task { @MainActor in
                    guard let self else { return }
                    if let error { self.firebaseMessage = self.friendlyFirebaseError(error); return }
                    self.firebaseGroupConversations = snapshot?.documents.compactMap { self.firebaseConversation(from: $0, userID: userID, source: "groups") } ?? []
                    self.publishFirebaseConversations()
                }
            }
        firebaseConversationListeners = [direct, groups]
    }

    private func stopFirebaseConversationSync() {
        firebaseConversationListeners.forEach { $0.remove() }
        firebaseConversationListeners.removeAll()
        firebasePresenceListeners.forEach { $0.remove() }
        firebasePresenceListeners.removeAll()
        firebasePresencePeerIDs.removeAll()
        closeFirebaseConversation()
        firebaseDirectConversations = []
        firebaseGroupConversations = []
    }

    private func refreshFirebasePresenceObservers() {
        let peerIDs = Set(firebaseDirectConversations.compactMap(\.peerUID))
        guard peerIDs != firebasePresencePeerIDs else { return }
        firebasePresenceListeners.forEach { $0.remove() }
        firebasePresenceListeners.removeAll()
        firebasePresencePeerIDs = peerIDs
        firebasePresenceListeners = peerIDs.map { peerID in
            Firestore.firestore().collection("users").document(peerID).addSnapshotListener { [weak self] snapshot, _ in
                let data = snapshot?.data() ?? [:]
                let updatedAt = (data["presenceUpdatedAt"] as? Timestamp)?.dateValue()
                let lastSeenAt = (data["lastSeenAt"] as? Timestamp)?.dateValue() ?? updatedAt
                let online = data["presenceState"] as? String == "online"
                    && (updatedAt?.timeIntervalSinceNow ?? -Double.infinity) > -90
                Task { @MainActor in
                    guard let self else { return }
                    self.firebaseDirectConversations = self.firebaseDirectConversations.map { conversation in
                        guard conversation.peerUID == peerID else { return conversation }
                        var updated = conversation
                        updated.peerIsOnline = online
                        updated.peerLastSeenAt = lastSeenAt
                        return updated
                    }
                    self.publishFirebaseConversations()
                }
            }
        }
    }

    private func publishFirebaseConversations() {
        let previous = Dictionary(uniqueKeysWithValues: conversations.compactMap { conversation in conversation.remoteID.map { ($0, conversation) } })
        conversations = (firebaseDirectConversations + firebaseGroupConversations).map { fresh in
            guard let remoteID = fresh.remoteID, let old = previous[remoteID] else { return fresh }
            var resolved = fresh
            resolved.messages = old.messages
            resolved.readAt = old.readAt
            return resolved
        }.sorted { $0.lastMessage > $1.lastMessage }
        UIApplication.shared.applicationIconBadgeNumber = unreadCount
    }

    private func firebaseConversation(from document: QueryDocumentSnapshot, userID: String, source: String) -> Conversation? {
        let data = document.data()
        let memberIDs = data["memberIds"] as? [String] ?? []
        guard memberIDs.contains(userID) else { return nil }
        let isGroup = source == "groups" || memberIDs.count > 2 || data["conversationType"] as? String == "group"
        let members = data["members"] as? [[String: Any]] ?? []
        let groupMembers = members.compactMap { member -> WapiGroupMember? in
            guard let uid = member["uid"] as? String, !uid.isEmpty else { return nil }
            return WapiGroupMember(
                uid: uid,
                displayName: member["displayName"] as? String ?? "Membre WAPI",
                phoneNumber: member["phoneNumber"] as? String ?? "",
                photoURL: member["photoUrl"] as? String ?? ""
            )
        }
        let peer = members.first { ($0["uid"] as? String) != userID }
        let name = isGroup ? (data["name"] as? String ?? data["title"] as? String ?? "Groupe WAPI") : (peer?["displayName"] as? String ?? data["contactName"] as? String ?? "Contact WAPI")
        let phone = isGroup ? "" : (peer?["phoneNumber"] as? String ?? "")
        let photo = isGroup ? (data["photoUrl"] as? String ?? data["groupPhotoUrl"] as? String ?? "") : (peer?["photoUrl"] as? String ?? "")
        let initials = name.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
        let updatedAt = (data["updatedAt"] as? Timestamp)?.dateValue() ?? (data["createdAt"] as? Timestamp)?.dateValue() ?? .distantPast
        let readBy = data["readBy"] as? [String: Any]
        let readAt = (readBy?[userID] as? Timestamp)?.dateValue() ?? .distantPast
        return Conversation(
            id: stableFirebaseUUID("\(source):\(document.documentID)"),
            name: name,
            initials: initials.isEmpty ? "W" : initials,
            phoneNumber: phone,
            lastMessage: data["lastMessage"] as? String ?? "Nouvelle conversation",
            unread: updatedAt > readAt && (data["lastSenderId"] as? String) != userID,
            readAt: readAt,
            messages: [],
            remoteID: document.documentID,
            source: source,
            peerUID: isGroup ? nil : peer?["uid"] as? String,
            photoURL: photo,
            groupOwnerID: isGroup ? data["ownerId"] as? String : nil,
            groupAdminIDs: isGroup ? (data["adminIds"] as? [String] ?? []) : [],
            groupMembers: isGroup ? groupMembers : []
        )
    }

    private func firebaseMessage(from document: QueryDocumentSnapshot, userID: String) -> Message {
        let data = document.data()
        let senderID = data["senderId"] as? String ?? ""
        let replyRemoteID = data["replyToId"] as? String
        return Message(
            id: stableFirebaseUUID(document.documentID),
            text: data["text"] as? String ?? "",
            mine: senderID == userID,
            sentAt: (data["createdAt"] as? Timestamp)?.dateValue() ?? Date(),
            kind: data["kind"] as? String ?? "text",
            mediaPath: data["mediaUrl"] as? String,
            mediaName: data["mediaName"] as? String,
            mediaSizeBytes: (data["mediaSizeBytes"] as? NSNumber)?.int64Value,
            mediaSha256: data["mediaSha256"] as? String,
            viewOnce: data["viewOnce"] as? Bool ?? false,
            viewedByIDs: Array((data["viewedBy"] as? [String: Any] ?? [:]).keys),
            replyToID: replyRemoteID.map(stableFirebaseUUID),
            replyText: data["replyText"] as? String,
            reactions: data["reactions"] as? [String: String] ?? [:],
            deleted: data["deleted"] as? Bool ?? false,
            edited: data["edited"] as? Bool ?? false,
            status: "sent",
            remoteID: document.documentID,
            senderID: senderID,
            senderName: data["senderName"] as? String
        )
    }

    private func writeFirebaseMediaMessage(label: String, kind: String, mediaURL: String, fileName: String, mediaSizeBytes: Int, mediaSha256: String, viewOnce: Bool, conversation: Conversation) {
        guard let remoteID = conversation.remoteID, let userID = firebaseUserID else { return }
        let root = Firestore.firestore().collection(conversation.source == "groups" ? "groups" : "conversations").document(remoteID)
        var data: [String: Any] = [
            "text": label, "senderId": userID, "createdAt": FieldValue.serverTimestamp(),
            "kind": kind, "mediaUrl": mediaURL, "mediaName": fileName, "duration": 0,
            "mediaSizeBytes": mediaSizeBytes, "mediaSha256": mediaSha256,
            "viewOnce": viewOnce, "viewedBy": [:]
        ]
        if conversation.source == "groups" { data["senderName"] = currentFirebaseSenderName }
        root.collection("messages").addDocument(data: data) { [weak self] error in
            Task { @MainActor [weak self] in
                if let error { self?.firebaseMessage = self?.friendlyFirebaseError(error) }
                else { self?.updateFirebaseConversationSummary(root: root, source: conversation.source ?? "conversations", text: label, userID: userID) }
            }
        }
    }

    private var currentFirebaseSenderName: String {
        let value = Auth.auth().currentUser?.displayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return value.isEmpty ? "Membre WAPI" : String(value.prefix(80))
    }

    func translateWithLingwap(_ text: String, targetLanguage: String) async throws -> WapiTranslationResult {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, value.count <= 1_200 else {
            throw WapiTranslationError.invalidText
        }
        guard WapiTranslationLanguage.supported.contains(where: { $0.code == targetLanguage }) else {
            throw WapiTranslationError.unsupportedLanguage
        }
        return try await withCheckedThrowingContinuation { continuation in
            Functions.functions(region: "europe-west1").httpsCallable("lingwapTranslateText").call([
                "text": value,
                "sourceLanguage": "auto",
                "targetLanguage": targetLanguage
            ]) { result, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                guard let payload = result?.data as? [String: Any],
                      let translation = (payload["translation"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
                      !translation.isEmpty else {
                    continuation.resume(throwing: WapiTranslationError.invalidResponse)
                    return
                }
                continuation.resume(returning: WapiTranslationResult(
                    text: translation,
                    detectedLanguage: (payload["detectedLanguage"] as? String) ?? "auto",
                    targetLanguage: (payload["targetLanguage"] as? String) ?? targetLanguage
                ))
            }
        }
    }

    private func updateFirebaseConversationSummary(root: DocumentReference, source: String, text: String, userID: String) {
        if source == "groups" {
            root.updateData(["lastMessage": text, "updatedAt": FieldValue.serverTimestamp()])
        } else {
            root.updateData(["lastMessage": text, "lastSenderId": userID, "updatedAt": FieldValue.serverTimestamp()])
        }
    }

    private func appendOptimisticFirebaseMessage(_ message: Message, conversationID: UUID) {
        guard let index = conversations.firstIndex(where: { $0.id == conversationID }) else { return }
        conversations[index].messages.append(message)
        conversations[index].lastMessage = message.text
    }

    private func finishFirebaseSend(messageID: UUID, conversationID: UUID, error: Error?) {
        Task { @MainActor in
            guard let conversation = conversations.firstIndex(where: { $0.id == conversationID }), let message = conversations[conversation].messages.firstIndex(where: { $0.id == messageID }) else { return }
            conversations[conversation].messages[message].status = error == nil ? "sent" : "failed"
            if let error { firebaseMessage = friendlyFirebaseError(error) }
        }
    }

    private func firebaseMessageReference(conversation: Conversation, conversationID: String, messageID: String) -> DocumentReference {
        Firestore.firestore().collection(conversation.source == "groups" ? "groups" : "conversations").document(conversationID).collection("messages").document(messageID)
    }

    private func syncFirebaseProfile(user: FirebaseAuth.User) {
        let phoneNumber = user.phoneNumber ?? ""
        let reference = Firestore.firestore().collection("users").document(user.uid)
        reference.getDocument { [weak self] snapshot, _ in
            var profile: [String: Any] = [
                "uid": user.uid,
                "phoneNumber": phoneNumber,
                "phoneLookup": WhappyPhoneCountry.normalize(phoneNumber) ?? phoneNumber,
                "phoneDigits": phoneNumber.filter(\.isNumber),
                "updatedAt": FieldValue.serverTimestamp()
            ]
            if snapshot?.exists != true {
                profile["verified"] = false
                profile["verificationStatus"] = "unverified"
                profile["createdAt"] = FieldValue.serverTimestamp()
            }
            let verified = (snapshot?.data()?["verified"] as? Bool == true) || isWhappyFounderPhone(phoneNumber)
            reference.setData(profile, merge: true)
            Task { @MainActor in self?.firebaseProfileVerified = verified }
        }
    }

    private func stableFirebaseUUID(_ value: String) -> UUID {
        var first: UInt64 = 14_695_981_039_346_656_037
        var second: UInt64 = 10_995_116_282_11
        for byte in value.utf8 {
            first = (first ^ UInt64(byte)) &* 1_099_511_628_211
            second = (second ^ UInt64(byte &+ 31)) &* 1_099_511_628_211
        }
        let hex = String(format: "%016llx%016llx", first, second)
        let formatted = "\(hex.prefix(8))-\(hex.dropFirst(8).prefix(4))-4\(hex.dropFirst(13).prefix(3))-a\(hex.dropFirst(17).prefix(3))-\(hex.dropFirst(20).prefix(12))"
        return UUID(uuidString: formatted) ?? UUID()
    }

    private func friendlyFirebaseError(_ error: Error) -> String {
        let code = (error as NSError).code
        switch code {
        case AuthErrorCode.invalidPhoneNumber.rawValue: return "Ce numéro de téléphone n’est pas valide."
        case AuthErrorCode.invalidVerificationCode.rawValue: return "Le code SMS est incorrect."
        case AuthErrorCode.sessionExpired.rawValue: return "Le code a expiré. Demandez un nouveau SMS."
        case AuthErrorCode.tooManyRequests.rawValue: return "Trop de tentatives. Patientez quelques minutes."
        case AuthErrorCode.networkError.rawValue: return "Connexion indisponible. Vérifiez Internet puis réessayez."
        default: return "WAPI n’a pas pu terminer l’opération. Réessayez."
        }
    }

    private func fallbackExtension(for kind: String) -> String {
        switch kind {
        case "audio": "m4a"
        case "video": "mp4"
        case "document": "pdf"
        default: "jpg"
        }
    }

    private func mediaContentType(for url: URL, kind: String) -> String {
        if let type = UTType(filenameExtension: url.pathExtension), let mime = type.preferredMIMEType { return mime }
        switch kind {
        case "audio": return "audio/mp4"
        case "video": return "video/mp4"
        case "document": return "application/pdf"
        default: return "image/jpeg"
        }
    }
}

private extension Optional where Wrapped == String {
    var orEmpty: String { self ?? "" }
}

struct ConversationAvatar: View {
    let conversation: Conversation

    var body: some View {
        Group {
            if let value = conversation.photoURL, let url = URL(string: value), !value.isEmpty {
                AsyncImage(url: url) { phase in
                    if case .success(let image) = phase { image.resizable().scaledToFill() }
                    else { InitialsAvatar(text: conversation.initials) }
                }
            } else {
                InitialsAvatar(text: conversation.initials)
            }
        }
        .frame(width: 46, height: 46)
        .clipShape(Circle())
    }
}

struct WhappyPhoneSignInView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var countryCode = "+242"
    @State private var phone = ""
    @State private var code = ""

    private var normalizedPhone: String? { WhappyPhoneCountry.normalize(phone, selectedCode: countryCode) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 22) {
                    Image("WhappyMark").resizable().scaledToFit().frame(width: 76, height: 76).clipShape(RoundedRectangle(cornerRadius: 22))
                    VStack(spacing: 6) {
                        Text(store.firebaseCodeSent ? "Entrez le code SMS" : "Connexion WAPI").font(.largeTitle.bold()).foregroundStyle(Color.whappyInk)
                        Text(store.firebaseCodeSent ? "Votre compte et vos messages seront synchronisés sur tous vos appareils." : "Utilisez le même numéro que sur Android ou le Web.").multilineTextAlignment(.center).foregroundStyle(.secondary)
                    }
                    if store.firebaseCodeSent {
                        TextField("Code à 6 chiffres", text: $code)
                            .keyboardType(.numberPad).textContentType(.oneTimeCode)
                            .font(.title2.monospacedDigit()).multilineTextAlignment(.center)
                            .padding().background(Color.whappyBlue.opacity(0.06)).clipShape(RoundedRectangle(cornerRadius: 16))
                        Button { store.confirmFirebasePhoneCode(code) } label: { Text("Continuer").frame(maxWidth: .infinity).padding(.vertical, 8) }
                            .buttonStyle(.borderedProminent).disabled(code.filter(\.isNumber).count != 6 || store.firebaseBusy)
                        Button("Changer de numéro") { store.firebaseCodeSent = false; store.firebaseVerificationID = nil; store.firebaseMessage = nil }
                    } else {
                        Picker("Pays", selection: $countryCode) {
                            ForEach(WhappyPhoneCountry.supported) { country in Text("\(country.flag) \(country.name)  \(country.code)").tag(country.code) }
                        }
                        .pickerStyle(.menu)
                        TextField("Numéro de téléphone", text: $phone).keyboardType(.phonePad).textContentType(.telephoneNumber)
                            .padding().background(Color.whappyBlue.opacity(0.06)).clipShape(RoundedRectangle(cornerRadius: 16))
                        if let normalizedPhone { Text(normalizedPhone).font(.footnote.bold()).foregroundStyle(Color.whappyBlue) }
                        Button { if let normalizedPhone { store.requestFirebasePhoneCode(phone: normalizedPhone) } } label: { Text("Recevoir le code SMS").frame(maxWidth: .infinity).padding(.vertical, 8) }
                            .buttonStyle(.borderedProminent).disabled(normalizedPhone == nil || store.firebaseBusy)
                    }
                    if store.firebaseBusy { ProgressView() }
                    if let message = store.firebaseMessage { Text(message).font(.footnote).multilineTextAlignment(.center).foregroundStyle(Color.whappyBlue) }
                    Text("Un numéro = un compte WAPI").font(.caption.bold()).foregroundStyle(.secondary)
                }
                .padding(28)
                .frame(maxWidth: 520)
            }
            .background(Color.whappyBackground)
        }
    }
}
