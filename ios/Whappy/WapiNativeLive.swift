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
            errorMessage = error.localizedDescription
        }
    }

    func create(title: String, category: String, visibility: String) async -> String? {
        do {
            let data = try await call("createLiveSession", data: [
                "title": title,
                "category": category,
                "visibility": visibility,
                "hostMode": "personal"
            ])
            await refresh()
            return data["liveId"] as? String
        } catch {
            errorMessage = error.localizedDescription
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
    @Published var errorMessage: String?

    let listing: WapiLiveListing
    private(set) var isHost: Bool
    private lazy var room = Room(delegate: self)
    private let functions = Functions.functions(region: "europe-west1")
    private let firestore = Firestore.firestore()
    private var liveListener: ListenerRegistration?
    private var commentListener: ListenerRegistration?

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
            errorMessage = error.localizedDescription
            await room.disconnect()
        }
    }

    func toggleMicrophone() async {
        guard isHost else { return }
        do {
            let next = !microphoneEnabled
            try await room.localParticipant.setMicrophone(enabled: next)
            microphoneEnabled = next
        } catch { errorMessage = error.localizedDescription }
    }

    func toggleCamera() async {
        guard isHost else { return }
        do {
            let next = !cameraEnabled
            try await room.localParticipant.setCamera(enabled: next)
            cameraEnabled = next
            if !next { videoTrack = nil }
        } catch { errorMessage = error.localizedDescription }
    }

    func switchCamera() async {
        guard isHost else { return }
        do {
            guard let track = room.localParticipant.firstCameraVideoTrack as? LocalVideoTrack,
                  let capturer = track.capturer as? CameraCapturer else { return }
            _ = try await capturer.switchCameraPosition()
        }
        catch { errorMessage = error.localizedDescription }
    }

    func react() async {
        do { _ = try await call("sendLiveReaction", data: ["liveId": listing.id, "reaction": "heart"]) }
        catch { errorMessage = error.localizedDescription }
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
        } catch { errorMessage = error.localizedDescription }
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

private struct WapiVideoSurface: UIViewRepresentable {
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
                if directory.loading { ProgressView().padding(.top, 40) }
                if !directory.loading, directory.lives.isEmpty {
                    ContentUnavailableView("Aucun Live en cours", systemImage: "video.badge.plus", description: Text("Créez le premier direct WAPI."))
                        .padding(.top, 70)
                }
                ForEach(directory.lives) { live in
                    Button { selected = live } label: { WapiLiveCard(live: live) }.buttonStyle(.plain)
                        .disabled(!live.isHost && live.status != "live")
                }
            }.padding(WapiSpacing.lg)
        }
        .background(Color.whappyBackground)
        .navigationTitle("En direct")
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { creating = true } label: { Label("Créer", systemImage: "video.badge.plus") } } }
        .task { await directory.refresh() }
        .refreshable { await directory.refresh() }
        .sheet(isPresented: $creating) { WapiLiveStudio(directory: directory) { selected = $0; creating = false } }
        .fullScreenCover(item: $selected) { WapiNativeLiveRoom(listing: $0) { selected = nil; Task { await directory.refresh() } } }
        .alert("Live WAPI", isPresented: Binding(get: { directory.errorMessage != nil }, set: { if !$0 { directory.errorMessage = nil } })) { Button("Compris") { directory.errorMessage = nil } } message: { Text(directory.errorMessage ?? "") }
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

private struct WapiLiveStudio: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var directory: WapiLiveDirectory
    let onCreated: (WapiLiveListing) -> Void
    @State private var title = ""
    @State private var category = "Discussion"
    @State private var visibility = "public"
    @State private var busy = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Votre direct") { TextField("Titre", text: $title); Picker("Catégorie", selection: $category) { ForEach(["Discussion", "Business", "Culture", "Tech", "Radio"], id: \.self) { Text($0) } } }
                Section("Audience") { Picker("Visibilité", selection: $visibility) { Text("Tout le monde").tag("public"); Text("Mes contacts").tag("contacts"); Text("Privé").tag("private") } }
                Section { Label("Les jetons vidéo sont temporaires et générés côté serveur.", systemImage: "lock.shield.fill") }
            }
            .navigationTitle("Studio Live")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button(busy ? "Préparation…" : "Démarrer") { Task { await create() } }.disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).count < 3 || busy) }
            }
        }
    }

    private func create() async {
        busy = true
        defer { busy = false }
        guard let id = await directory.create(title: title.trimmingCharacters(in: .whitespacesAndNewlines), category: category, visibility: visibility),
              let live = directory.lives.first(where: { $0.id == id }) else { return }
        onCreated(live)
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

    private func close(end: Bool) async { await session.leave(end: end); dismiss(); onClosed() }
}

private struct WapiLiveButton: View {
    let icon: String
    let label: String
    let action: () -> Void
    var body: some View { Button(action: action) { VStack(spacing: 5) { Image(systemName: icon).font(.title3).frame(width: 48, height: 48).background(Color.whappyBlue).clipShape(Circle()); Text(label).font(.caption2) }.foregroundStyle(.white) } }
}
