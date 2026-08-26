import AVFoundation
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions
import LiveKitWebRTC
import SwiftUI

private enum WapiDirectCallError: LocalizedError {
    case invalidRoute, invalidResponse, permissions

    var errorDescription: String? {
        switch self {
        case .invalidRoute: return "Le contact WAPI de cet appel est introuvable."
        case .invalidResponse: return "La signalisation sécurisée de l’appel est invalide."
        case .permissions: return "Autorisez le microphone et la caméra pour cet appel WAPI."
        }
    }
}

private struct WapiIceServer {
    let urls: [String]
    let username: String?
    let credential: String?
}

/// Direct calls use the same WAPI-owned WebRTC flow on iOS and Android:
/// Firebase carries only offer, answer and ICE candidates; audio/video uses
/// peer-to-peer WebRTC or WAPI's TURN relay for restrictive mobile networks.
@MainActor
private final class WapiDirectCallSession: NSObject, ObservableObject, @preconcurrency LKRTCPeerConnectionDelegate {
    @Published private(set) var videoTrack: LKRTCVideoTrack?
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
    private let functions = Functions.functions(region: "europe-west1")
    private let firestore = Firestore.firestore()
    private let factory = LKRTCPeerConnectionFactory()
    private let constraints = LKRTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
    private var peerConnection: LKRTCPeerConnection?
    private var audioSource: LKRTCAudioSource?
    private var audioTrack: LKRTCAudioTrack?
    private var videoSource: LKRTCVideoSource?
    private var localVideoTrack: LKRTCVideoTrack?
    private var cameraCapturer: LKRTCCameraVideoCapturer?
    private var callListener: ListenerRegistration?
    private var candidateListener: ListenerRegistration?
    private var callReference: DocumentReference?
    private var queuedCandidates: [LKRTCIceCandidate] = []
    private var localCandidates: [LKRTCIceCandidate] = []
    private var remoteDescriptionReady = false
    private var answerApplied = false
    private var connectInFlight = false
    private var callDocumentReady = false
    private static var cachedIceServers: [WapiIceServer]?
    private static var cachedIceServersAt = Date.distantPast

    init(route: WapiDirectCallRoute) {
        self.route = route
        peerName = route.peerName
        peerPhotoURL = route.peerPhotoURL
        videoEnabled = route.video
        outgoing = route.callID == nil
        invitationReady = route.callID == nil
        super.init()
    }

    func loadInvitation() async {
        guard let id = route.callID else { return }
        do {
            let snapshot = try await firestore.collection("calls").document(id).getDocument()
            let data = snapshot.data() ?? [:]
            guard snapshot.exists,
                  data["status"] as? String == "ringing",
                  let userID = Auth.auth().currentUser?.uid,
                  data["calleeId"] as? String == userID else { throw WapiDirectCallError.invalidRoute }
            callID = id
            callReference = snapshot.reference
            peerName = data["callerName"] as? String ?? peerName
            peerPhotoURL = data["callerPhotoUrl"] as? String ?? peerPhotoURL
            videoEnabled = data["video"] as? Bool ?? videoEnabled
            invitationReady = true
            connecting = false
            connectionLabel = "Appel entrant"
            observeCall(snapshot.reference, caller: false)
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
            async let microphoneGranted = AVCaptureDevice.requestAccess(for: .audio)
            async let cameraGranted = videoEnabled ? AVCaptureDevice.requestAccess(for: .video) : true
            guard await microphoneGranted, await cameraGranted else { throw WapiDirectCallError.permissions }
            try configureAudioSession()
            if outgoing { try await beginOutgoing() } else { try await acceptIncoming() }
        } catch {
            connecting = false
            errorMessage = wapiUserFacingError(error, action: "L’appel")
            closeMedia()
        }
    }

    func toggleMicrophone() async {
        let next = !microphoneEnabled
        audioTrack?.isEnabled = next
        microphoneEnabled = next
    }

    func toggleCamera() async {
        guard videoEnabled else { return }
        let next = !cameraEnabled
        localVideoTrack?.isEnabled = next
        cameraEnabled = next
    }

    func switchCamera() async {
        guard videoEnabled, let capturer = cameraCapturer else { return }
        let current = capturer.captureSession.inputs.compactMap { ($0 as? AVCaptureDeviceInput)?.device }.first
        guard let next = LKRTCCameraVideoCapturer.captureDevices().first(where: { $0.position != current?.position }),
              let format = preferredFormat(for: next) else { return }
        await withCheckedContinuation { continuation in
            capturer.stopCapture { capturer.startCapture(with: next, format: format, fps: 30) { _ in continuation.resume() } }
        }
    }

    func toggleSpeaker() {
        let next = !speakerEnabled
        do {
            try AVAudioSession.sharedInstance().overrideOutputAudioPort(next ? .speaker : .none)
            speakerEnabled = next
        } catch { errorMessage = wapiUserFacingError(error, action: "Le haut-parleur") }
    }

    func close(decline: Bool = false) async {
        let reference = callReference ?? (callID.isEmpty ? nil : firestore.collection("calls").document(callID))
        closeMedia()
        guard let reference else { return }
        try? await reference.updateData(["status": decline ? "declined" : "ended", "updatedAt": FieldValue.serverTimestamp()])
    }

    private func beginOutgoing() async throws {
        guard let user = Auth.auth().currentUser,
              let peerID = route.peerID, !peerID.isEmpty else { throw WapiDirectCallError.invalidRoute }
        try preparePeer(iceServers: await loadIceServers())
        let offer = try await makeOffer()
        try await setLocalDescription(offer)
        let profile = try? await firestore.collection("users").document(user.uid).getDocument()
        let reference = firestore.collection("calls").document()
        callID = reference.documentID
        callReference = reference
        let profileData = profile?.data() ?? [:]
        let callData: [String: Any] = [
            "callerId": user.uid,
            "calleeId": peerID,
            "callerName": profileData["displayName"] as? String ?? user.displayName ?? "Membre WAPI",
            "callerPhotoUrl": profileData["photoUrl"] as? String ?? user.photoURL?.absoluteString ?? "",
            "calleeName": peerName,
            "video": videoEnabled,
            "status": "ringing",
            "offer": ["type": "offer", "sdp": offer.sdp],
            "createdAt": FieldValue.serverTimestamp(),
            "updatedAt": FieldValue.serverTimestamp(),
        ]
        try await reference.setData(callData)
        callDocumentReady = true
        for candidate in localCandidates { try? await add(candidate, to: "callerCandidates", reference: reference) }
        localCandidates.removeAll()
        observeCall(reference, caller: true)
        observeCandidates(reference, collection: "calleeCandidates")
        connecting = false
        connectionLabel = "Sonnerie…"
    }

    private func acceptIncoming() async throws {
        guard let reference = callReference ?? route.callID.map({ firestore.collection("calls").document($0) }) else { throw WapiDirectCallError.invalidRoute }
        let snapshot = try await reference.getDocument()
        let data = snapshot.data() ?? [:]
        guard snapshot.exists, data["status"] as? String == "ringing",
              let offerData = snapshot.get("offer") as? [String: Any],
              let sdp = offerData["sdp"] as? String, !sdp.isEmpty else { throw WapiDirectCallError.invalidResponse }
        callID = reference.documentID
        callReference = reference
        peerName = data["callerName"] as? String ?? peerName
        peerPhotoURL = data["callerPhotoUrl"] as? String ?? peerPhotoURL
        videoEnabled = data["video"] as? Bool ?? videoEnabled
        try preparePeer(iceServers: await loadIceServers())
        try await setRemoteDescription(LKRTCSessionDescription(type: .offer, sdp: sdp))
        remoteDescriptionReady = true
        flushQueuedCandidates()
        observeCandidates(reference, collection: "callerCandidates")
        let answer = try await makeAnswer()
        try await setLocalDescription(answer)
        try await reference.updateData([
            "answer": ["type": "answer", "sdp": answer.sdp],
            "status": "accepted",
            "updatedAt": FieldValue.serverTimestamp(),
        ])
        callDocumentReady = true
        for candidate in localCandidates { try? await add(candidate, to: "calleeCandidates", reference: reference) }
        localCandidates.removeAll()
        connecting = false
        connectionLabel = "Connexion du média…"
    }

    private func configureAudioSession() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: videoEnabled ? .videoChat : .voiceChat, options: [.defaultToSpeaker, .allowBluetoothHFP, .allowBluetoothA2DP])
        try session.setActive(true)
        speakerEnabled = true
    }

    private func fallbackIceServers() -> [WapiIceServer] {
        [
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:stun3.l.google.com:19302",
            "stun:stun4.l.google.com:19302",
        ].map { WapiIceServer(urls: [$0], username: nil, credential: nil) }
    }

    private func loadIceServers() async -> [WapiIceServer] {
        let fallback = fallbackIceServers()
        if let cached = Self.cachedIceServers {
            let includesTurn = cached.contains { server in
                server.urls.contains { url in
                    let normalized = url.lowercased()
                    return normalized.hasPrefix("turn:") || normalized.hasPrefix("turns:")
                }
            }
            let lifetime: TimeInterval = includesTurn ? 45 * 60 : 60
            if Date().timeIntervalSince(Self.cachedIceServersAt) < lifetime { return cached }
        }
        do {
            let result = try await callable("getWebRtcIceServers", data: [:], timeout: 8)
            let raw = result["iceServers"] as? [[String: Any]] ?? []
            let configured = raw.compactMap { item -> WapiIceServer? in
                let urls = (item["urls"] as? [String]) ?? (item["urls"] as? String).map { [$0] } ?? []
                guard !urls.isEmpty else { return nil }
                return WapiIceServer(urls: urls, username: item["username"] as? String, credential: item["credential"] as? String)
            }
            var seen = Set<String>()
            let merged = (configured + fallback).filter { server in
                let key = server.urls.joined(separator: "|")
                return seen.insert(key).inserted
            }
            Self.cachedIceServers = merged
            Self.cachedIceServersAt = Date()
            return merged
        } catch {
            // A cold or unavailable callable must never prevent a direct call.
            Self.cachedIceServers = fallback
            Self.cachedIceServersAt = Date()
            return fallback
        }
    }

    private func preparePeer(iceServers: [WapiIceServer]) throws {
        closeMedia(keepState: true)
        let configuration = LKRTCConfiguration()
        configuration.sdpSemantics = .unifiedPlan
        configuration.bundlePolicy = .maxBundle
        configuration.rtcpMuxPolicy = .require
        configuration.continualGatheringPolicy = .gatherContinually
        configuration.iceCandidatePoolSize = 4
        configuration.iceTransportPolicy = .all
        configuration.tcpCandidatePolicy = .enabled
        configuration.candidateNetworkPolicy = .all
        configuration.enableDscp = true
        configuration.iceServers = iceServers.map { LKRTCIceServer(urlStrings: $0.urls, username: $0.username, credential: $0.credential) }
        guard let connection = factory.peerConnection(with: configuration, constraints: constraints, delegate: self) else { throw WapiDirectCallError.invalidResponse }
        peerConnection = connection
        audioSource = factory.audioSource(with: constraints)
        guard let audioSource else { throw WapiDirectCallError.invalidResponse }
        let track = factory.audioTrack(with: audioSource, trackId: "wapi-audio")
        track.isEnabled = true
        audioTrack = track
        microphoneEnabled = true
        _ = connection.add(track, streamIds: ["wapi-media"])
        if videoEnabled { try prepareCamera(on: connection) }
    }

    private func prepareCamera(on connection: LKRTCPeerConnection) throws {
        let source = factory.videoSource()
        let capturer = LKRTCCameraVideoCapturer(delegate: source)
        guard let device = LKRTCCameraVideoCapturer.captureDevices().first(where: { $0.position == .front }) ?? LKRTCCameraVideoCapturer.captureDevices().first,
              let format = preferredFormat(for: device) else { throw WapiDirectCallError.permissions }
        videoSource = source
        cameraCapturer = capturer
        capturer.startCapture(with: device, format: format, fps: 30)
        let track = factory.videoTrack(with: source, trackId: "wapi-video")
        track.isEnabled = true
        localVideoTrack = track
        cameraEnabled = true
        _ = connection.add(track, streamIds: ["wapi-media"])
    }

    private func preferredFormat(for device: AVCaptureDevice) -> AVCaptureDevice.Format? {
        LKRTCCameraVideoCapturer.supportedFormats(for: device).max { lhs, rhs in
            let left = CMVideoFormatDescriptionGetDimensions(lhs.formatDescription)
            let right = CMVideoFormatDescriptionGetDimensions(rhs.formatDescription)
            return left.width * left.height < right.width * right.height
        }
    }

    private func observeCall(_ reference: DocumentReference, caller: Bool) {
        callListener?.remove()
        callListener = reference.addSnapshotListener { [weak self] snapshot, error in
            guard let self else { return }
            if error != nil { Task { @MainActor in self.fail("La signalisation de l’appel est interrompue.") }; return }
            guard let snapshot, snapshot.exists else { return }
            let status = snapshot.data()?["status"] as? String ?? ""
            Task { @MainActor in
                if status == "declined" || status == "ended" {
                    self.closeMedia()
                    self.connectionLabel = status == "declined" ? "Appel refusé" : "Appel terminé"
                    return
                }
                guard caller, status == "accepted", !self.answerApplied,
                      let answer = snapshot.get("answer") as? [String: Any],
                      let sdp = answer["sdp"] as? String else { return }
                self.answerApplied = true
                do {
                    try await self.setRemoteDescription(LKRTCSessionDescription(type: .answer, sdp: sdp))
                    self.remoteDescriptionReady = true
                    self.flushQueuedCandidates()
                    self.connectionLabel = "Connexion du média…"
                } catch { self.fail("La réponse sécurisée de cet appel est invalide.") }
            }
        }
    }

    private func observeCandidates(_ reference: DocumentReference, collection: String) {
        candidateListener?.remove()
        candidateListener = reference.collection(collection).addSnapshotListener { [weak self] snapshot, error in
            guard let self, error == nil else { return }
            let documents = snapshot?.documentChanges.filter { $0.type == .added }.map(\.document) ?? []
            Task { @MainActor in
                for document in documents {
                    guard let candidate = document.data()["candidate"] as? String, !candidate.isEmpty else { continue }
                    let candidateData = document.data()
                    let index = (candidateData["sdpMLineIndex"] as? NSNumber)?.int32Value ?? 0
                    let ice = LKRTCIceCandidate(sdp: candidate, sdpMLineIndex: index, sdpMid: candidateData["sdpMid"] as? String)
                    if self.remoteDescriptionReady { self.addRemote(ice) } else { self.queuedCandidates.append(ice) }
                }
            }
        }
    }

    private func addRemote(_ candidate: LKRTCIceCandidate) {
        peerConnection?.add(candidate) { [weak self] error in
            if error != nil { Task { @MainActor in self?.fail("Une liaison réseau reçue est invalide.") } }
        }
    }

    private func flushQueuedCandidates() {
        let candidates = queuedCandidates
        queuedCandidates.removeAll()
        candidates.forEach(addRemote)
    }

    private func add(_ candidate: LKRTCIceCandidate, to collection: String, reference: DocumentReference) async throws {
        _ = try await reference.collection(collection).addDocument(data: ["candidate": candidate.sdp, "sdpMid": candidate.sdpMid ?? "0", "sdpMLineIndex": candidate.sdpMLineIndex])
    }

    private func makeOffer() async throws -> LKRTCSessionDescription {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<LKRTCSessionDescription, Error>) in
            peerConnection?.offer(for: constraints) { description, error in
                if let error { continuation.resume(throwing: error) }
                else if let description { continuation.resume(returning: description) }
                else { continuation.resume(throwing: WapiDirectCallError.invalidResponse) }
            }
        }
    }

    private func makeAnswer() async throws -> LKRTCSessionDescription {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<LKRTCSessionDescription, Error>) in
            peerConnection?.answer(for: constraints) { description, error in
                if let error { continuation.resume(throwing: error) }
                else if let description { continuation.resume(returning: description) }
                else { continuation.resume(throwing: WapiDirectCallError.invalidResponse) }
            }
        }
    }

    private func setLocalDescription(_ description: LKRTCSessionDescription) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            peerConnection?.setLocalDescription(description) { error in if let error { continuation.resume(throwing: error) } else { continuation.resume() } }
        }
    }

    private func setRemoteDescription(_ description: LKRTCSessionDescription) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            peerConnection?.setRemoteDescription(description) { error in if let error { continuation.resume(throwing: error) } else { continuation.resume() } }
        }
    }

    private func callable(_ name: String, data: [String: Any], timeout: TimeInterval = 12) async throws -> [String: Any] {
        try await withCheckedThrowingContinuation { continuation in
            let callable = functions.httpsCallable(name)
            callable.timeoutInterval = timeout
            callable.call(data) { result, error in
                if let error { continuation.resume(throwing: error); return }
                guard let value = result?.data as? [String: Any] else { continuation.resume(throwing: WapiDirectCallError.invalidResponse); return }
                continuation.resume(returning: value)
            }
        }
    }

    private func fail(_ message: String) {
        connecting = false
        errorMessage = message
        closeMedia(keepState: true)
    }

    private func closeMedia(keepState: Bool = false) {
        callListener?.remove(); callListener = nil
        candidateListener?.remove(); candidateListener = nil
        cameraCapturer?.stopCapture()
        cameraCapturer = nil
        localVideoTrack = nil
        videoSource = nil
        audioTrack = nil
        audioSource = nil
        peerConnection?.close(); peerConnection = nil
        queuedCandidates.removeAll(); localCandidates.removeAll()
        remoteDescriptionReady = false; answerApplied = false; callDocumentReady = false
        mediaConnected = false
        if !keepState { try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation) }
    }

    func peerConnection(_: LKRTCPeerConnection, didChange _: LKRTCSignalingState) {}
    func peerConnection(_: LKRTCPeerConnection, didAdd _: LKRTCMediaStream) {}
    func peerConnection(_: LKRTCPeerConnection, didRemove _: LKRTCMediaStream) {}
    func peerConnectionShouldNegotiate(_: LKRTCPeerConnection) {}
    func peerConnection(_: LKRTCPeerConnection, didChange newState: LKRTCIceConnectionState) {
        Task { @MainActor in
            switch newState {
            case .connected, .completed: self.mediaConnected = true; self.connecting = false; self.connectionLabel = "Connecté"
            case .checking: if !self.mediaConnected { self.connectionLabel = "Recherche du réseau…" }
            case .disconnected: if self.mediaConnected { self.connectionLabel = "Reconnexion…" }
            case .failed: self.fail("Ce réseau n’a pas pu établir la liaison WebRTC. Essayez une autre connexion Internet puis relancez l’appel.")
            default: break
            }
        }
    }
    func peerConnection(_: LKRTCPeerConnection, didChange _: LKRTCIceGatheringState) {}
    func peerConnection(_: LKRTCPeerConnection, didGenerate candidate: LKRTCIceCandidate) {
        Task { @MainActor in
            guard let reference = self.callReference else { self.localCandidates.append(candidate); return }
            let collection = self.outgoing ? "callerCandidates" : "calleeCandidates"
            guard self.callDocumentReady else { self.localCandidates.append(candidate); return }
            try? await self.add(candidate, to: collection, reference: reference)
        }
    }
    func peerConnection(_: LKRTCPeerConnection, didRemove _: [LKRTCIceCandidate]) {}
    func peerConnection(_: LKRTCPeerConnection, didOpen _: LKRTCDataChannel) {}
    func peerConnection(_: LKRTCPeerConnection, didChange _: LKRTCPeerConnectionState) {}
    func peerConnection(_: LKRTCPeerConnection, didAdd rtpReceiver: LKRTCRtpReceiver, streams _: [LKRTCMediaStream]) {
        if let track = rtpReceiver.track as? LKRTCVideoTrack { Task { @MainActor in self.videoTrack = track } }
    }
    func peerConnection(_: LKRTCPeerConnection, didRemove _: LKRTCRtpReceiver) {}
    func peerConnection(_: LKRTCPeerConnection, didStartReceivingOn _: LKRTCRtpTransceiver) {}
    func peerConnection(_: LKRTCPeerConnection, didChangeLocalCandidate _: LKRTCIceCandidate, remoteCandidate _: LKRTCIceCandidate, lastReceivedMs _: Int32, changeReason _: String) {}
    func peerConnection(_: LKRTCPeerConnection, didFailToGatherIceCandidate _: LKRTCIceCandidateErrorEvent) {}
}

private struct WapiNativeCallVideoSurface: UIViewRepresentable {
    let track: LKRTCVideoTrack?
    final class Coordinator { var renderedTrack: LKRTCVideoTrack? }
    func makeCoordinator() -> Coordinator { Coordinator() }
    func makeUIView(context _: Context) -> LKRTCMTLVideoView { LKRTCMTLVideoView() }
    func updateUIView(_ view: LKRTCMTLVideoView, context: Context) {
        if context.coordinator.renderedTrack !== track {
            context.coordinator.renderedTrack?.remove(view)
            track?.add(view)
            context.coordinator.renderedTrack = track
        }
    }
    static func dismantleUIView(_ view: LKRTCMTLVideoView, coordinator: Coordinator) { coordinator.renderedTrack?.remove(view) }
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
            if session.videoEnabled { WapiNativeCallVideoSurface(track: session.videoTrack).ignoresSafeArea() }
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
                    }.padding(.bottom, 42).foregroundStyle(.white)
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
        let activeSession = session
        WapiSounds.callEnded()
        dismiss()
        onClosed()
        await activeSession.close(decline: decline)
    }
}

private struct IncomingCallAction: View {
    let icon: String; let label: String; let color: Color; let action: () -> Void
    var body: some View { Button(action: action) { VStack(spacing: 8) { Image(systemName: icon).font(.title2.weight(.bold)).frame(width: 64, height: 64).background(color).clipShape(Circle()); Text(label).font(.caption.weight(.semibold)) } }.buttonStyle(.plain) }
}

private struct CallControl: View {
    let icon: String; let label: String; var destructive = false; let action: () -> Void
    var body: some View { Button(action: action) { VStack(spacing: 6) { Image(systemName: icon).font(.title3).frame(width: 49, height: 49).background(destructive ? Color.red : Color.white.opacity(0.18)).clipShape(Circle()); Text(label).font(.caption2) } }.buttonStyle(.plain) }
}
