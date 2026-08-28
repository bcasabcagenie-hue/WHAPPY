import AVFoundation
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions
import LiveKit
import SwiftUI

struct WapiLiveListing: Identifiable, Hashable {
    let id: String
    let hostID: String
    let hostName: String
    let hostPhotoURL: String
    let title: String
    let category: String
    let status: String
    let viewerCount: Int
    let reactionCount: Int

    var isHost: Bool { hostID == Auth.auth().currentUser?.uid }
}

private struct WapiLiveCredentials {
    let serverURL: String
    let participantToken: String
    let isHost: Bool
}

struct WapiLiveComment: Identifiable {
    let id: String
    let author: String
    let text: String
}

private enum WapiLiveError: LocalizedError {
    case invalidResponse
    case permissions

    var errorDescription: String? {
        switch self {
        case .invalidResponse: "Le serveur Live WAPI a renvoyé une réponse invalide."
        case .permissions: "Autorisez la caméra et le microphone pour démarrer le direct."
        }
    }
}

@MainActor
final class WapiLiveDirectory: ObservableObject {
    @Published private(set) var lives: [WapiLiveListing] = []
    @Published private(set) var loading = false
    @Published var errorMessage: String?

    private let functions = Functions.functions(region: "europe-west1")

    func refresh() async {
        guard Auth.auth().currentUser != nil else { return }
        loading = true
        defer { loading = false }
        do {
            let data = try await call("listVisibleLiveSessions", data: [:])
            let values = data["lives"] as? [[String: Any]] ?? []
            lives = values.compactMap { value in
                guard let id = value["id"] as? String,
                      let hostID = value["hostId"] as? String,
                      let title = value["title"] as? String else { return nil }
                return WapiLiveListing(
                    id: id,
                    hostID: hostID,
                    hostName: value["hostName"] as? String ?? "Créateur WAPI",
                    hostPhotoURL: value["hostPhotoUrl"] as? String ?? "",
                    title: title,
                    category: value["category"] as? String ?? "Discussion",
                    status: value["status"] as? String ?? "scheduled",
                    viewerCount: (value["viewerCount"] as? NSNumber)?.intValue ?? 0,
                    reactionCount: (value["reactionCount"] as? NSNumber)?.intValue ?? 0
                )
            }
        } catch {
            errorMessage = wapiUserFacingError(error, action: "Le chargement des directs")
        }
    }

    func create(title: String, category: String, visibility: String) async -> String? {
        do {
            let data = try await call("createLiveSession", data: [
                "title": title,
                "category": category,
                "visibility": visibility,
                "hostMode": "personal",
                "startNow": true
            ])
            await refresh()
            return data["liveId"] as? String
        } catch {
            errorMessage = wapiUserFacingError(error, action: "La création du direct")
            return nil
        }
    }

    private func call(_ name: String, data: [String: Any]) async throws -> [String: Any] {
        try await withCheckedThrowingContinuation { continuation in
            functions.httpsCallable(name).call(data) { result, error in
                if let error { continuation.resume(throwing: error); return }
                guard let value = result?.data as? [String: Any] else {
                    continuation.resume(throwing: WapiLiveError.invalidResponse)
                    return
                }
                continuation.resume(returning: value)
            }
        }
    }
}

@MainActor
final class WapiLiveSession: NSObject, ObservableObject, @preconcurrency RoomDelegate {
    @Published private(set) var videoTrack: VideoTrack?
    @Published private(set) var connecting = true
    @Published private(set) var connectionLabel = "Connexion sécurisée…"
    @Published private(set) var microphoneEnabled = false
    @Published private(set) var cameraEnabled = false
    @Published private(set) var viewerCount = 0
    @Published private(set) var reactionCount = 0
    @Published private(set) var comments: [WapiLiveComment] = []
    @Published private(set) var kingQiRoom: [String: Any] = [:]
    @Published var errorMessage: String?

    let listing: WapiLiveListing
    private(set) var isHost: Bool
    private lazy var room = Room(delegate: self)
    private let functions = Functions.functions(region: "europe-west1")
    private let firestore = Firestore.firestore()
    private var liveListener: ListenerRegistration?
    private var commentListener: ListenerRegistration?
    private var kingQiListener: ListenerRegistration?
    private var kingQiRoomID = ""

    init(listing: WapiLiveListing) {
        self.listing = listing
        self.isHost = listing.isHost
        self.viewerCount = listing.viewerCount
        self.reactionCount = listing.reactionCount
        super.init()
    }

    func connect() async {
        do {
            let credentials = try await credentials()
            isHost = credentials.isHost
            if isHost {
                async let camera = AVCaptureDevice.requestAccess(for: .video)
                async let microphone = AVCaptureDevice.requestAccess(for: .audio)
                guard await camera, await microphone else { throw WapiLiveError.permissions }
            }
            observeMetadata()
            try await room.connect(url: credentials.serverURL, token: credentials.participantToken)
            if isHost {
                try await room.localParticipant.setMicrophone(enabled: true)
                try await room.localParticipant.setCamera(enabled: true)
                microphoneEnabled = true
                cameraEnabled = true
                _ = try await call("setLiveSessionState", data: ["liveId": listing.id, "action": "start"])
            } else {
                _ = try await call("setLivePresence", data: ["liveId": listing.id, "action": "connected"])
            }
            connecting = false
            connectionLabel = "En direct"
        } catch {
            connecting = false
            errorMessage = wapiUserFacingError(error, action: "Le direct")
            await room.disconnect()
        }
    }

    func toggleMicrophone() async {
        guard isHost else { return }
        do {
            let next = !microphoneEnabled
            try await room.localParticipant.setMicrophone(enabled: next)
            microphoneEnabled = next
        } catch { errorMessage = wapiUserFacingError(error, action: "Le microphone du direct") }
    }

    func toggleCamera() async {
        guard isHost else { return }
        do {
            let next = !cameraEnabled
            try await room.localParticipant.setCamera(enabled: next)
            cameraEnabled = next
            if !next { videoTrack = nil }
        } catch { errorMessage = wapiUserFacingError(error, action: "La caméra du direct") }
    }

    func switchCamera() async {
        guard isHost else { return }
        do {
            guard let track = room.localParticipant.firstCameraVideoTrack as? LocalVideoTrack,
                  let capturer = track.capturer as? CameraCapturer else { return }
            _ = try await capturer.switchCameraPosition()
        }
        catch { errorMessage = wapiUserFacingError(error, action: "Le changement de caméra") }
    }

    func react() async {
        do { _ = try await call("sendLiveReaction", data: ["liveId": listing.id, "reaction": "heart"]) }
        catch { errorMessage = wapiUserFacingError(error, action: "La réaction") }
    }

    func sendComment(_ rawText: String) async {
        let text = String(rawText.trimmingCharacters(in: .whitespacesAndNewlines).prefix(280))
        guard !text.isEmpty, let user = Auth.auth().currentUser else { return }
        do {
            try await firestore.collection("liveSessions").document(listing.id).collection("comments").addDocument(data: [
                "authorId": user.uid,
                "authorName": user.displayName ?? user.phoneNumber ?? "Membre WAPI",
                "text": text,
                "createdAt": FieldValue.serverTimestamp()
            ])
        } catch { errorMessage = wapiUserFacingError(error, action: "L’envoi du commentaire") }
    }

    func answerKingQi(_ option: Int) async {
        guard !kingQiRoomID.isEmpty else { return }
        do { _ = try await call("kingQiSubmitAnswer", data: ["roomId": kingQiRoomID, "optionIndex": option]) }
        catch { errorMessage = wapiUserFacingError(error, action: "La réponse King QI") }
    }

    func advanceKingQi() async {
        guard !kingQiRoomID.isEmpty else { return }
        do { _ = try await call("kingQiAdvanceTournament", data: ["roomId": kingQiRoomID]) }
        catch { errorMessage = wapiUserFacingError(error, action: "Le tournoi King QI") }
    }

    func leave(end: Bool) async {
        do {
            if isHost && end {
                _ = try await call("setLiveSessionState", data: ["liveId": listing.id, "action": "end"])
            } else if !isHost {
                _ = try await call("setLivePresence", data: ["liveId": listing.id, "action": "disconnected"])
            }
        } catch { /* Le webhook réconcilie aussi les départs. */ }
        liveListener?.remove()
        commentListener?.remove()
        kingQiListener?.remove()
        await room.disconnect()
    }

    private func credentials() async throws -> WapiLiveCredentials {
        let data = try await call("joinLiveSession", data: ["liveId": listing.id])
        guard let serverURL = data["serverUrl"] as? String,
              serverURL.hasPrefix("wss://"),
              let token = data["participantToken"] as? String,
              !token.isEmpty else { throw WapiLiveError.invalidResponse }
        return WapiLiveCredentials(serverURL: serverURL, participantToken: token, isHost: data["role"] as? String == "host")
    }

    private func call(_ name: String, data: [String: Any]) async throws -> [String: Any] {
        try await withCheckedThrowingContinuation { continuation in
            functions.httpsCallable(name).call(data) { result, error in
                if let error { continuation.resume(throwing: error); return }
                guard let value = result?.data as? [String: Any] else {
                    continuation.resume(throwing: WapiLiveError.invalidResponse)
                    return
                }
                continuation.resume(returning: value)
            }
        }
    }

    private func observeMetadata() {
        liveListener = firestore.collection("liveSessions").document(listing.id).addSnapshotListener { [weak self] snapshot, _ in
            guard let self, let data = snapshot?.data() else { return }
            Task { @MainActor in
                self.viewerCount = (data["viewerCount"] as? NSNumber)?.intValue ?? 0
                self.reactionCount = (data["reactionCount"] as? NSNumber)?.intValue ?? 0
                let linkedRoomID = data["kingQiRoomId"] as? String ?? ""
                if !linkedRoomID.isEmpty, linkedRoomID != self.kingQiRoomID { self.observeKingQi(linkedRoomID) }
                if !self.isHost, data["status"] as? String == "ended" { self.errorMessage = "Ce direct est terminé." }
            }
        }
        commentListener = firestore.collection("liveSessions").document(listing.id).collection("comments")
            .order(by: "createdAt", descending: false).limit(toLast: 40)
            .addSnapshotListener { [weak self] snapshot, _ in
                let values = snapshot?.documents.map { document in
                    WapiLiveComment(
                        id: document.documentID,
                        author: document.get("authorName") as? String ?? "Membre WAPI",
                        text: document.get("text") as? String ?? ""
                    )
                } ?? []
                Task { @MainActor in self?.comments = values }
            }
    }

    private func observeKingQi(_ roomID: String) {
        kingQiListener?.remove(); kingQiRoomID = roomID
        kingQiListener = firestore.collection("kingQiRooms").document(roomID).addSnapshotListener { [weak self] snapshot, _ in
            let value = snapshot?.data() ?? [:]
            Task { @MainActor in self?.kingQiRoom = value }
        }
    }

    func room(_: Room, participant _: LocalParticipant, didPublishTrack publication: LocalTrackPublication) {
        guard let track = publication.track as? VideoTrack else { return }
        videoTrack = track
    }

    func room(_: Room, participant _: RemoteParticipant, didSubscribeTrack publication: RemoteTrackPublication) {
        guard let track = publication.track as? VideoTrack else { return }
        videoTrack = track
    }

    func room(_: Room, didUpdateConnectionState connectionState: ConnectionState, from _: ConnectionState) {
        connectionLabel = switch connectionState {
        case .reconnecting: "Reconnexion…"
        case .connected: "En direct"
        case .disconnected: "Direct interrompu"
        default: "Connexion…"
        }
    }
}

struct WapiVideoSurface: UIViewRepresentable {
    let track: VideoTrack?

    func makeUIView(context _: Context) -> VideoView {
        let view = VideoView()
        view.layoutMode = .fill
        return view
    }

    func updateUIView(_ view: VideoView, context _: Context) { view.track = track }
}

struct LiveView: View {
    @StateObject private var directory = WapiLiveDirectory()
    @State private var creating = false
    @State private var selected: WapiLiveListing?

    var body: some View {
        ScrollView {
            LazyVStack(spacing: WapiSpacing.md) {
                Label("Les directs publics sont visibles dans tout WAPI, sans être dans les contacts.", systemImage: "globe.europe.africa.fill").font(.footnote).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 4)
                if directory.loading { ProgressView().padding(.top, 40) }
                if !directory.loading, directory.lives.isEmpty {
                    ContentUnavailableView("Aucun Live en cours", systemImage: "video.badge.plus", description: Text("Créez le premier direct WAPI."))
                        .padding(.top, 70)
                }
                ForEach(directory.lives) { live in
                    Button { selected = live } label: { WapiLiveCard(live: live) }.buttonStyle(WapiPressableButtonStyle())
                        .disabled(!live.isHost && live.status != "live")
                }
            }.padding(WapiSpacing.lg)
        }
        .background(Color.whappyBackground)
        .navigationTitle("En direct")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { Task { await startDirect() } } label: {
                    if creating { ProgressView() } else { Label("Direct", systemImage: "video.badge.plus") }
                }
                .disabled(creating)
            }
        }
        .task { await directory.refresh() }
        .refreshable { await directory.refresh() }
        .fullScreenCover(item: $selected) { WapiNativeLiveRoom(listing: $0) { selected = nil; Task { await directory.refresh() } } }
        .alert("Live WAPI", isPresented: Binding(get: { directory.errorMessage != nil }, set: { if !$0 { directory.errorMessage = nil } })) { Button("Compris") { directory.errorMessage = nil } } message: { Text(directory.errorMessage ?? "") }
    }

    private func startDirect() async {
        creating = true
        defer { creating = false }
        guard let id = await directory.create(title: "", category: "Discussion", visibility: "public"),
              let live = directory.lives.first(where: { $0.id == id }) else { return }
        selected = live
    }
}

private struct WapiLiveCard: View {
    let live: WapiLiveListing
    var body: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(colors: [WapiColor.deepBlue, WapiColor.ink], startPoint: .topLeading, endPoint: .bottomTrailing)
            Image(systemName: live.status == "live" ? "play.rectangle.fill" : "calendar.badge.clock").font(.system(size: 72)).foregroundStyle(.white.opacity(0.15)).frame(maxWidth: .infinity, maxHeight: .infinity)
            VStack(alignment: .leading, spacing: 7) {
                HStack { Text(live.status == "live" ? "● LIVE" : "PROGRAMMÉ").font(.caption2.bold()).padding(.horizontal, 8).padding(.vertical, 5).background(live.status == "live" ? WapiColor.live : WapiColor.verified).clipShape(Capsule()); Spacer(); Label(live.viewerCount.formatted(), systemImage: "eye.fill").font(.caption) }
                Text(live.title).font(.title3.bold())
                Text("\(live.hostName) · \(live.category)").font(.caption).foregroundStyle(.white.opacity(0.75))
            }.foregroundStyle(.white).padding(WapiSpacing.lg)
        }.frame(height: 210).clipShape(RoundedRectangle(cornerRadius: WapiRadius.panel))
    }
}

private struct WapiNativeLiveRoom: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var session: WapiLiveSession
    let onClosed: () -> Void
    @State private var comment = ""

    init(listing: WapiLiveListing, onClosed: @escaping () -> Void) {
        _session = StateObject(wrappedValue: WapiLiveSession(listing: listing))
        self.onClosed = onClosed
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            WapiVideoSurface(track: session.videoTrack).ignoresSafeArea()
            LinearGradient(colors: [.black.opacity(0.72), .clear, .black.opacity(0.9)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    Button { Task { await close(end: false) } } label: { Image(systemName: "xmark").frame(width: 40, height: 40).background(.black.opacity(0.35)).clipShape(Circle()) }
                    VStack(alignment: .leading) { Text("● LIVE  \(session.listing.title)").font(.headline); Text("\(session.connectionLabel) · \(session.viewerCount) spectateur(s) · \(session.reactionCount) réaction(s)").font(.caption).foregroundStyle(.white.opacity(0.7)) }.frame(maxWidth: .infinity, alignment: .leading)
                    if session.isHost { Button("Terminer") { Task { await close(end: true) } }.buttonStyle(.borderedProminent).tint(WapiColor.live) }
                }.foregroundStyle(.white).padding()
                if !session.kingQiRoom.isEmpty { WapiKingQiLiveOverlay(session: session) }
                Spacer()
                ScrollView { LazyVStack(alignment: .leading, spacing: 6) { ForEach(session.comments) { value in Text("\(value.author)  \(value.text)").font(.caption).foregroundStyle(.white).padding(.horizontal, 10).padding(.vertical, 7).background(.black.opacity(0.42)).clipShape(RoundedRectangle(cornerRadius: 13)) } } }.frame(height: 150).padding(.horizontal)
                HStack { TextField("Écrire dans le direct…", text: $comment).textFieldStyle(.roundedBorder); Button { let value = comment; comment = ""; Task { await session.sendComment(value) } } label: { Image(systemName: "paperplane.fill") }.buttonStyle(.borderedProminent).disabled(comment.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }.padding()
                HStack(spacing: 28) {
                    if session.isHost {
                        WapiLiveButton(icon: session.microphoneEnabled ? "mic.fill" : "mic.slash.fill", label: "Micro") { Task { await session.toggleMicrophone() } }
                        WapiLiveButton(icon: session.cameraEnabled ? "video.fill" : "video.slash.fill", label: "Caméra") { Task { await session.toggleCamera() } }
                        WapiLiveButton(icon: "arrow.triangle.2.circlepath.camera.fill", label: "Retourner") { Task { await session.switchCamera() } }
                    } else { WapiLiveButton(icon: "heart.fill", label: "Réagir") { Task { await session.react() } } }
                }.padding(.bottom)
            }
            if session.connecting { ProgressView("Connexion au Live WAPI…").tint(.white).foregroundStyle(.white) }
        }
        .task { await session.connect() }
        .alert("Direct indisponible", isPresented: Binding(get: { session.errorMessage != nil }, set: { if !$0 { session.errorMessage = nil } })) { Button("Fermer") { Task { await close(end: false) } } } message: { Text(session.errorMessage ?? "") }
    }

    private func close(end: Bool) async { WapiSounds.callEnded(); await session.leave(end: end); dismiss(); onClosed() }
}

private struct WapiKingQiLiveOverlay: View {
    @ObservedObject var session: WapiLiveSession
    private var room: [String: Any] { session.kingQiRoom }
    private var currentUserID: String { Auth.auth().currentUser?.uid ?? "" }

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack { Text("♛ KING QI").font(.headline.bold()).foregroundStyle(.yellow); Spacer(); Text("\((room["potCredits"] as? NSNumber)?.intValue ?? 0) CRÉDITS").font(.caption2.bold()).foregroundStyle(.white.opacity(0.7)) }
            if room["status"] as? String == "waiting" { Text("Le tournoi va commencer · \((room["playerIds"] as? [String])?.count ?? 0) joueur(s)").font(.subheadline.bold()).foregroundStyle(.white) }
            else if room["status"] as? String == "finished" { Text(((room["winners"] as? [String])?.contains(currentUserID) == true) ? "🏆 GRAND CHAMPION" : "Tournoi terminé").font(.title3.bold()).foregroundStyle(.white) }
            else if let question = room["currentQuestion"] as? [String: Any], let options = question["options"] as? [String] {
                Text(question["text"] as? String ?? "Question King QI").font(.headline).foregroundStyle(.white).lineLimit(2)
                let players = room["playerIds"] as? [String] ?? []
                let answered = room["answeredIds"] as? [String] ?? []
                let canAnswer = players.contains(currentUserID) && !answered.contains(currentUserID)
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 6) {
                    ForEach(options.indices, id: \.self) { option in Button(options[option]) { Task { await session.answerKingQi(option) } }.font(.caption.bold()).buttonStyle(.borderedProminent).tint(Color.whappyBlue).disabled(!canAnswer) }
                }
                if session.isHost { Button("Manche suivante") { Task { await session.advanceKingQi() } }.font(.caption.bold()).buttonStyle(.bordered) }
            }
        }
        .padding(12).background(Color(red: 0.03, green: 0.11, blue: 0.25).opacity(0.92)).overlay(RoundedRectangle(cornerRadius: 18).stroke(.yellow.opacity(0.55))).clipShape(RoundedRectangle(cornerRadius: 18)).padding(.horizontal)
    }
}

private struct WapiLiveButton: View {
    let icon: String
    let label: String
    let action: () -> Void
    var body: some View { Button(action: action) { VStack(spacing: 5) { Image(systemName: icon).font(.title3).frame(width: 48, height: 48).background(Color.whappyBlue).clipShape(Circle()); Text(label).font(.caption2) }.foregroundStyle(.white) } }
}

// MARK: - Appels de groupe WAPI (même contrat Firebase/WebRTC que l'application Android)

private struct WapiGroupCallCredentials {
    let callID: String
    let groupName: String
    let video: Bool
    let serverURL: String
    let token: String
}

@MainActor
private final class WapiGroupCallSession: NSObject, ObservableObject, @preconcurrency RoomDelegate {
    @Published private(set) var videoTrack: VideoTrack?
    @Published private(set) var connecting = true
    @Published private(set) var connectionLabel = "Préparation de l’appel…"
    @Published private(set) var callID = ""
    @Published private(set) var groupName: String
    @Published private(set) var participantCount = 0
    @Published private(set) var microphoneEnabled = false
    @Published private(set) var cameraEnabled = false
    @Published private(set) var speakerEnabled = true
    @Published private(set) var videoEnabled: Bool
    @Published private(set) var canEndCall = false
    @Published var errorMessage: String?

    private let route: WapiGroupCallRoute
    private lazy var room = Room(delegate: self)
    private let functions = Functions.functions(region: "europe-west1")
    private let firestore = Firestore.firestore()
    private var listener: ListenerRegistration?

    init(route: WapiGroupCallRoute) {
        self.route = route
        self.groupName = route.groupName
        self.videoEnabled = route.video
        super.init()
    }

    func connect() async {
        do {
            let credentials = try await credentials()
            callID = credentials.callID
            groupName = credentials.groupName
            videoEnabled = credentials.video
            canEndCall = route.callID == nil
            let microphoneGranted = await AVCaptureDevice.requestAccess(for: .audio)
            guard microphoneGranted else { throw WapiLiveError.permissions }
            if credentials.video {
                let cameraGranted = await AVCaptureDevice.requestAccess(for: .video)
                guard cameraGranted else { throw WapiLiveError.permissions }
            }
            try AVAudioSession.sharedInstance().setCategory(.playAndRecord, mode: .videoChat, options: [.defaultToSpeaker, .allowBluetoothHFP])
            try AVAudioSession.sharedInstance().setActive(true)
            try await room.connect(url: credentials.serverURL, token: credentials.token)
            try await room.localParticipant.setMicrophone(enabled: true)
            microphoneEnabled = true
            if credentials.video {
                try await room.localParticipant.setCamera(enabled: true)
                cameraEnabled = true
            }
            _ = try await call("setGroupCallPresence", data: ["callId": credentials.callID, "active": true])
            observeCall()
            connecting = false
            connectionLabel = "Connecté"
        } catch {
            connecting = false
            errorMessage = wapiUserFacingError(error, action: "L’appel de groupe")
            await room.disconnect()
        }
    }

    func toggleMicrophone() async {
        do {
            let next = !microphoneEnabled
            try await room.localParticipant.setMicrophone(enabled: next)
            microphoneEnabled = next
        } catch { errorMessage = wapiUserFacingError(error, action: "Le microphone") }
    }

    func toggleCamera() async {
        guard videoEnabled else { return }
        do {
            let next = !cameraEnabled
            try await room.localParticipant.setCamera(enabled: next)
            cameraEnabled = next
            if !next { videoTrack = nil }
        } catch { errorMessage = wapiUserFacingError(error, action: "La caméra") }
    }

    func switchCamera() async {
        guard videoEnabled else { return }
        do {
            guard let track = room.localParticipant.firstCameraVideoTrack as? LocalVideoTrack,
                  let capturer = track.capturer as? CameraCapturer else { return }
            _ = try await capturer.switchCameraPosition()
        } catch { errorMessage = wapiUserFacingError(error, action: "Le changement de caméra") }
    }

    func toggleSpeaker() {
        do {
            let next = !speakerEnabled
            try AVAudioSession.sharedInstance().overrideOutputAudioPort(next ? .speaker : .none)
            speakerEnabled = next
        } catch { errorMessage = wapiUserFacingError(error, action: "Le haut-parleur") }
    }

    func leave(end: Bool) async {
        guard !callID.isEmpty else { await room.disconnect(); return }
        do {
            if end && canEndCall {
                _ = try await call("endGroupCallSession", data: ["callId": callID])
            } else {
                _ = try await call("setGroupCallPresence", data: ["callId": callID, "active": false])
            }
        } catch {
            // Une interruption réseau ne doit pas bloquer la fermeture locale.
        }
        listener?.remove()
        await room.disconnect()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func credentials() async throws -> WapiGroupCallCredentials {
        let result: [String: Any]
        if let callID = route.callID {
            result = try await call("joinGroupCallSession", data: ["callId": callID])
        } else if let groupID = route.groupID {
            result = try await call("createGroupCallSession", data: ["groupId": groupID, "source": route.groupSource, "video": route.video])
        } else {
            throw WapiLiveError.invalidResponse
        }
        guard let callID = result["callId"] as? String,
              let serverURL = result["serverUrl"] as? String, serverURL.hasPrefix("wss://"),
              let token = result["participantToken"] as? String, !token.isEmpty else {
            throw WapiLiveError.invalidResponse
        }
        return WapiGroupCallCredentials(
            callID: callID,
            groupName: result["groupName"] as? String ?? route.groupName,
            video: result["video"] as? Bool ?? route.video,
            serverURL: serverURL,
            token: token
        )
    }

    private func call(_ name: String, data: [String: Any]) async throws -> [String: Any] {
        try await withCheckedThrowingContinuation { continuation in
            functions.httpsCallable(name).call(data) { result, error in
                if let error { continuation.resume(throwing: error); return }
                guard let value = result?.data as? [String: Any] else {
                    continuation.resume(throwing: WapiLiveError.invalidResponse)
                    return
                }
                continuation.resume(returning: value)
            }
        }
    }

    private func observeCall() {
        listener = firestore.collection("groupCallSessions").document(callID).addSnapshotListener { [weak self] snapshot, _ in
            guard let data = snapshot?.data() else { return }
            Task { @MainActor in
                self?.participantCount = (data["participantCount"] as? NSNumber)?.intValue ?? 0
                if data["status"] as? String == "ended", self?.connecting == false {
                    self?.connectionLabel = "Appel terminé"
                }
            }
        }
    }

    func room(_: Room, participant _: LocalParticipant, didPublishTrack publication: LocalTrackPublication) {
        guard let track = publication.track as? VideoTrack else { return }
        videoTrack = track
    }

    func room(_: Room, participant _: RemoteParticipant, didSubscribeTrack publication: RemoteTrackPublication) {
        guard let track = publication.track as? VideoTrack else { return }
        videoTrack = track
    }

    func room(_: Room, didUpdateConnectionState state: ConnectionState, from _: ConnectionState) {
        connectionLabel = switch state {
        case .connected: "Connecté"
        case .reconnecting: "Reconnexion…"
        case .disconnected: "Appel interrompu"
        default: "Connexion…"
        }
    }
}

struct WapiGroupCallRoom: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var session: WapiGroupCallSession
    let onClosed: () -> Void

    init(route: WapiGroupCallRoute, onClosed: @escaping () -> Void) {
        _session = StateObject(wrappedValue: WapiGroupCallSession(route: route))
        self.onClosed = onClosed
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            WapiVideoSurface(track: session.videoTrack).ignoresSafeArea()
            LinearGradient(colors: [.black.opacity(0.74), .clear, .black.opacity(0.90)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    Button { Task { await close(end: false) } } label: { Image(systemName: "chevron.down").frame(width: 42, height: 42).background(.black.opacity(0.35)).clipShape(Circle()) }
                    VStack(alignment: .leading, spacing: 3) {
                        Text(session.groupName).font(.headline)
                        Text("\(session.connectionLabel) · \(session.participantCount) participant(s)").font(.caption).foregroundStyle(.white.opacity(0.72))
                    }.frame(maxWidth: .infinity, alignment: .leading)
                    if !session.callID.isEmpty {
                        ShareLink(item: "Rejoignez l’appel de groupe \(session.groupName) sur WAPI\nwhappy://group-call/\(session.callID)") { Image(systemName: "person.badge.plus") }
                            .frame(width: 42, height: 42).background(.black.opacity(0.35)).clipShape(Circle())
                    }
                    if session.canEndCall { Button("Terminer") { Task { await close(end: true) } }.buttonStyle(.borderedProminent).tint(WapiColor.live) }
                }.foregroundStyle(.white).padding()
                Spacer()
                if session.videoTrack == nil {
                    VStack(spacing: 12) {
                        Image(systemName: session.videoEnabled ? "person.2.fill" : "waveform").font(.system(size: 54)).foregroundStyle(.white.opacity(0.85))
                        Text(session.videoEnabled ? "La vidéo des participants apparaîtra ici." : "Appel audio WAPI sécurisé").font(.callout).foregroundStyle(.white.opacity(0.8))
                    }
                }
                Spacer()
                HStack(spacing: 24) {
                    WapiLiveButton(icon: session.microphoneEnabled ? "mic.fill" : "mic.slash.fill", label: "Micro") { Task { await session.toggleMicrophone() } }
                    WapiLiveButton(icon: session.speakerEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill", label: "HP") { session.toggleSpeaker() }
                    if session.videoEnabled {
                        WapiLiveButton(icon: session.cameraEnabled ? "video.fill" : "video.slash.fill", label: "Caméra") { Task { await session.toggleCamera() } }
                        WapiLiveButton(icon: "arrow.triangle.2.circlepath.camera.fill", label: "Retourner") { Task { await session.switchCamera() } }
                    }
                    WapiLiveButton(icon: "phone.down.fill", label: "Quitter") { Task { await close(end: false) } }
                }.padding(.bottom, 34)
            }
            if session.connecting { ProgressView("Connexion sécurisée…").tint(.white).foregroundStyle(.white).padding(18).background(.black.opacity(0.6)).clipShape(RoundedRectangle(cornerRadius: 16)) }
        }
        .task { await session.connect() }
        .alert("Appel de groupe indisponible", isPresented: Binding(get: { session.errorMessage != nil }, set: { if !$0 { session.errorMessage = nil } })) {
            Button("Fermer") { Task { await close(end: false) } }
        } message: { Text(session.errorMessage ?? "") }
    }

    private func close(end: Bool) async {
        await session.leave(end: end)
        dismiss()
        onClosed()
    }
}
