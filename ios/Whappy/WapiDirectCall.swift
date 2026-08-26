import AVFoundation
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions
import LiveKit
import SwiftUI

private enum WapiDirectCallError: LocalizedError {
    case invalidRoute
    case invalidResponse
    case permissions

    var errorDescription: String? {
        switch self {
        case .invalidRoute: return "Le contact WAPI de cet appel est introuvable."
        case .invalidResponse: return "Le serveur d’appel WAPI a renvoyé une réponse invalide."
        case .permissions: return "Autorisez le microphone et la caméra pour cet appel WAPI."
        }
    }
}

private struct WapiDirectCallCredentials {
    let callID: String
    let peerName: String
    let peerPhotoURL: String
    let video: Bool
    let serverURL: String
    let token: String
    let accepted: Bool
}

/// Direct calls are native WAPI rooms. Firebase only supplies the invitation;
/// LiveKit connects media to WAPI's self-hosted WebRTC SFU with a short token.
@MainActor
private final class WapiDirectCallSession: NSObject, ObservableObject, @preconcurrency RoomDelegate {
    @Published private(set) var videoTrack: VideoTrack?
    @Published private(set) var callID = ""
    @Published private(set) var peerName: String
    @Published private(set) var peerPhotoURL: String
    @Published private(set) var videoEnabled: Bool
    @Published private(set) var connectionLabel = "Préparation sécurisée…"
    @Published private(set) var connecting = true
    @Published private(set) var microphoneEnabled = false
    @Published private(set) var cameraEnabled = false
    @Published private(set) var speakerEnabled = true
    @Published private(set) var outgoing = false
    @Published private(set) var invitationReady = false
    @Published private(set) var mediaConnected = false
    @Published var errorMessage: String?

    private let route: WapiDirectCallRoute
    private lazy var room = Room(delegate: self)
    private let functions = Functions.functions(region: "europe-west1")
    private let firestore = Firestore.firestore()
    private var listener: ListenerRegistration?
    private var connectInFlight = false

    init(route: WapiDirectCallRoute) {
        self.route = route
        self.peerName = route.peerName
        self.peerPhotoURL = route.peerPhotoURL
        self.videoEnabled = route.video
        self.outgoing = route.callID == nil
        self.invitationReady = route.callID == nil
        super.init()
    }

    func loadInvitation() async {
        guard let callID = route.callID else { return }
        do {
            let value = try await call("getDirectCallSession", data: ["callId": callID])
            peerName = value["peerName"] as? String ?? peerName
            peerPhotoURL = value["peerPhotoUrl"] as? String ?? peerPhotoURL
            videoEnabled = value["video"] as? Bool ?? videoEnabled
            invitationReady = true
            connecting = false
            connectionLabel = "Appel entrant"
        } catch {
            connecting = false
            errorMessage = wapiUserFacingError(error, action: "Le chargement de l’appel")
        }
    }

    func connect() async {
        guard !connectInFlight, !mediaConnected else { return }
        connectInFlight = true
        connecting = true
        defer { connectInFlight = false }
        do {
            let credentials = try await credentials()
            callID = credentials.callID
            peerName = credentials.peerName
            peerPhotoURL = credentials.peerPhotoURL
            videoEnabled = credentials.video
            async let microphoneGranted = AVCaptureDevice.requestAccess(for: .audio)
            async let cameraGranted = credentials.video ? AVCaptureDevice.requestAccess(for: .video) : true
            guard await microphoneGranted, await cameraGranted else { throw WapiDirectCallError.permissions }
            try AVAudioSession.sharedInstance().setCategory(.playAndRecord, mode: credentials.video ? .videoChat : .voiceChat, options: [.defaultToSpeaker, .allowBluetooth])
            try AVAudioSession.sharedInstance().setActive(true)
            try await room.connect(url: credentials.serverURL, token: credentials.token)
            try await room.localParticipant.setMicrophone(enabled: true)
            microphoneEnabled = true
            if credentials.video {
                try await room.localParticipant.setCamera(enabled: true)
                cameraEnabled = true
            }
            observeCall()
            connecting = false
            mediaConnected = true
            connectionLabel = credentials.accepted ? "Connecté" : "Sonnerie…"
        } catch {
            connecting = false
            errorMessage = wapiUserFacingError(error, action: "L’appel")
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
        guard videoEnabled,
              let track = room.localParticipant.firstCameraVideoTrack as? LocalVideoTrack,
              let capturer = track.capturer as? CameraCapturer else { return }
        do { _ = try await capturer.switchCameraPosition() }
        catch { errorMessage = wapiUserFacingError(error, action: "Le changement de caméra") }
    }

    func toggleSpeaker() {
        do {
            let next = !speakerEnabled
            try AVAudioSession.sharedInstance().overrideOutputAudioPort(next ? .speaker : .none)
            speakerEnabled = next
        } catch { errorMessage = wapiUserFacingError(error, action: "Le haut-parleur") }
    }

    func close(decline: Bool = false) async {
        listener?.remove()
        let targetCallID = callID.isEmpty ? (route.callID ?? "") : callID
        guard !targetCallID.isEmpty else {
            await room.disconnect()
            return
        }
        do { _ = try await call("closeDirectCallSession", data: ["callId": targetCallID, "action": decline ? "decline" : "end"]) }
        catch { /* A local close must always work, even if the network is gone. */ }
        await room.disconnect()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func credentials() async throws -> WapiDirectCallCredentials {
        let value: [String: Any]
        if let callID = route.callID {
            value = try await call("joinDirectCallSession", data: ["callId": callID])
        } else {
            guard let peerID = route.peerID, !peerID.isEmpty else { throw WapiDirectCallError.invalidRoute }
            value = try await call("createDirectCallSession", data: ["calleeId": peerID, "video": route.video])
        }
        guard let callID = value["callId"] as? String,
              let serverURL = value["serverUrl"] as? String, serverURL.hasPrefix("wss://"),
              let token = value["participantToken"] as? String, !token.isEmpty else {
            throw WapiDirectCallError.invalidResponse
        }
        return WapiDirectCallCredentials(
            callID: callID,
            peerName: value["peerName"] as? String ?? value["calleeName"] as? String ?? route.peerName,
            peerPhotoURL: value["peerPhotoUrl"] as? String ?? value["calleePhotoUrl"] as? String ?? route.peerPhotoURL,
            video: value["video"] as? Bool ?? route.video,
            serverURL: serverURL,
            token: token,
            accepted: (value["status"] as? String) == "accepted"
        )
    }

    private func call(_ name: String, data: [String: Any]) async throws -> [String: Any] {
        try await withCheckedThrowingContinuation { continuation in
            functions.httpsCallable(name).call(data) { result, error in
                if let error { continuation.resume(throwing: error); return }
                guard let value = result?.data as? [String: Any] else {
                    continuation.resume(throwing: WapiDirectCallError.invalidResponse)
                    return
                }
                continuation.resume(returning: value)
            }
        }
    }

    private func observeCall() {
        listener = firestore.collection("directCallSessions").document(callID).addSnapshotListener { [weak self] snapshot, _ in
            guard let self, let data = snapshot?.data() else { return }
            Task { @MainActor in
                switch data["status"] as? String {
                case "accepted": self.connectionLabel = "Connecté"
                case "declined": self.connectionLabel = "Appel refusé"
                case "ended": self.connectionLabel = "Appel terminé"
                default: break
                }
            }
        }
    }

    func room(_: Room, participant _: LocalParticipant, didPublishTrack publication: LocalTrackPublication) {
        if let track = publication.track as? VideoTrack { videoTrack = track }
    }

    func room(_: Room, participant _: RemoteParticipant, didSubscribeTrack publication: RemoteTrackPublication) {
        if let track = publication.track as? VideoTrack { videoTrack = track }
    }

    func room(_: Room, didUpdateConnectionState state: ConnectionState, from _: ConnectionState) {
        connectionLabel = switch state {
        case .connected: outgoing ? "Sonnerie…" : "Connecté"
        case .reconnecting: "Reconnexion…"
        case .disconnected: "Appel interrompu"
        default: "Connexion…"
        }
    }
}

struct WapiDirectCallRoom: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var session: WapiDirectCallSession
    let onClosed: () -> Void

    init(route: WapiDirectCallRoute, onClosed: @escaping () -> Void) {
        _session = StateObject(wrappedValue: WapiDirectCallSession(route: route))
        self.onClosed = onClosed
    }

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color.whappyBlue, Color(red: 0.02, green: 0.08, blue: 0.16)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            if session.videoEnabled { WapiVideoSurface(track: session.videoTrack).ignoresSafeArea() }
            LinearGradient(colors: [.black.opacity(session.videoTrack == nil ? 0.08 : 0.64), .clear, .black.opacity(0.82)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    Button { Task { await close(decline: !session.outgoing && !session.mediaConnected) } } label: { Image(systemName: "chevron.down").frame(width: 42, height: 42).background(.black.opacity(0.28)).clipShape(Circle()) }
                    Spacer()
                    Text(session.videoEnabled ? "APPEL VIDÉO WAPI" : "APPEL AUDIO WAPI").font(.caption2.weight(.bold)).tracking(1.1).padding(.horizontal, 12).padding(.vertical, 7).background(.black.opacity(0.25)).clipShape(Capsule())
                }.padding().foregroundStyle(.white)
                Spacer()
                if session.videoTrack == nil {
                    VStack(spacing: 14) {
                        AsyncImage(url: URL(string: session.peerPhotoURL)) { image in image.resizable().scaledToFill() } placeholder: { Text(session.peerName.prefix(1).uppercased()).font(.system(size: 45, weight: .bold)) }
                            .frame(width: 128, height: 128).background(.white.opacity(0.16)).clipShape(Circle()).overlay(Circle().stroke(.white.opacity(0.45), lineWidth: 2))
                        Text(session.peerName).font(.title2.bold())
                        Text(session.connectionLabel).font(.callout).foregroundStyle(.white.opacity(0.76))
                    }.foregroundStyle(.white)
                }
                Spacer()
                if !session.outgoing && !session.mediaConnected && session.invitationReady {
                    HStack(spacing: 46) {
                        IncomingCallAction(icon: "phone.down.fill", label: "Refuser", color: .red) { Task { await close(decline: true) } }
                        IncomingCallAction(icon: session.videoEnabled ? "video.fill" : "phone.fill", label: "Accepter", color: .green) { Task { await session.connect() } }
                    }
                    .padding(.bottom, 42).foregroundStyle(.white)
                } else if session.mediaConnected {
                    HStack(spacing: 19) {
                        CallControl(icon: session.microphoneEnabled ? "mic.fill" : "mic.slash.fill", label: "Micro") { Task { await session.toggleMicrophone() } }
                        CallControl(icon: session.speakerEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill", label: "HP") { session.toggleSpeaker() }
                        if session.videoEnabled {
                            CallControl(icon: session.cameraEnabled ? "video.fill" : "video.slash.fill", label: "Caméra") { Task { await session.toggleCamera() } }
                            CallControl(icon: "camera.rotate.fill", label: "Retourner") { Task { await session.switchCamera() } }
                        }
                        CallControl(icon: "phone.down.fill", label: "Terminer", destructive: true) { Task { await close() } }
                    }.padding(.bottom, 34).foregroundStyle(.white)
                }
            }
            if session.connecting { ProgressView("Connexion WAPI sécurisée…").tint(.white).foregroundStyle(.white).padding(18).background(.black.opacity(0.55)).clipShape(RoundedRectangle(cornerRadius: 16)) }
        }
        .task { if session.outgoing { await session.connect() } else { await session.loadInvitation() } }
        .alert("Appel WAPI indisponible", isPresented: Binding(get: { session.errorMessage != nil }, set: { if !$0 { session.errorMessage = nil } })) {
            Button("Fermer") { Task { await close(decline: !session.mediaConnected) } }
        } message: { Text(session.errorMessage ?? "") }
    }

    private func close(decline: Bool = false) async {
        // Closing the sheet is a local action and must never wait for the
        // signalling server on a weak or interrupted mobile network.
        let activeSession = session
        WapiSounds.callEnded()
        dismiss()
        onClosed()
        await activeSession.close(decline: decline)
    }
}

private struct IncomingCallAction: View {
    let icon: String
    let label: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon).font(.title2.weight(.bold)).frame(width: 64, height: 64).background(color).clipShape(Circle())
                Text(label).font(.caption.weight(.semibold))
            }
        }.buttonStyle(.plain)
    }
}

private struct CallControl: View {
    let icon: String
    let label: String
    var destructive = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon).font(.title3).frame(width: 49, height: 49).background(destructive ? Color.red : Color.white.opacity(0.18)).clipShape(Circle())
                Text(label).font(.caption2)
            }
        }.buttonStyle(.plain)
    }
}
