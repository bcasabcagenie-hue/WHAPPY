import FirebaseAuth
import FirebaseFunctions
import SwiftUI

private struct WiaChatMessage: Identifiable, Equatable {
    let id: String
    let text: String
    let fromUser: Bool
    let failed: Bool
}

@MainActor
private final class WiaAssistantViewModel: ObservableObject {
    @Published var messages: [WiaChatMessage] = []
    @Published var loadingHistory = false
    @Published var waitingForReply = false
    @Published var memoryAvailable = true

    private let functions = Functions.functions(region: "europe-west1")
    private var didLoad = false

    func loadHistory() async {
        guard !didLoad else { return }
        didLoad = true
        loadingHistory = true
        defer { loadingHistory = false }
        do {
            let data = try await call("getWepiHistory", data: ["threadId": "main"])
            let values = data["messages"] as? [[String: Any]] ?? []
            messages = values.compactMap { value in
                let text = (value["content"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                guard !text.isEmpty else { return nil }
                return WiaChatMessage(
                    id: value["id"] as? String ?? UUID().uuidString,
                    text: text,
                    fromUser: value["role"] as? String == "user",
                    failed: false
                )
            }
            if messages.isEmpty { messages = [welcomeMessage()] }
        } catch {
            memoryAvailable = false
            if messages.isEmpty { messages = [welcomeMessage()] }
        }
    }

    func send(_ value: String) async {
        let prompt = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !prompt.isEmpty, !waitingForReply else { return }
        let messageID = "ios-\(UUID().uuidString.lowercased())"
        let history = messages
            .filter { !$0.failed && !$0.id.hasPrefix("welcome-") }
            .suffix(20)
            .map { ["fromUser": $0.fromUser, "text": $0.text] as [String: Any] }
        messages.append(WiaChatMessage(id: messageID, text: prompt, fromUser: true, failed: false))
        waitingForReply = true
        defer { waitingForReply = false }
        do {
            let data = try await call("askWepi", data: [
                "prompt": String(prompt.prefix(4_000)),
                "history": history,
                "threadId": "main",
                "messageId": messageID,
            ])
            let answer = (data["text"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !answer.isEmpty else { throw WiaAssistantError.emptyResponse }
            messages.append(WiaChatMessage(id: "assistant-\(messageID)", text: answer, fromUser: false, failed: false))
            memoryAvailable = true
        } catch {
            messages.append(WiaChatMessage(
                id: "failed-\(messageID)",
                text: wapiUserFacingError(error, action: "La réponse de WIA"),
                fromUser: false,
                failed: true
            ))
        }
    }

    private func call(_ name: String, data: [String: Any]) async throws -> [String: Any] {
        try await withCheckedThrowingContinuation { continuation in
            functions.httpsCallable(name).call(data) { result, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: result?.data as? [String: Any] ?? [:])
                }
            }
        }
    }

    private func welcomeMessage() -> WiaChatMessage {
        let rawName = Auth.auth().currentUser?.displayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let firstName = rawName.split(separator: " ").first.map(String.init) ?? ""
        let greeting = firstName.isEmpty ? "Bonjour." : "Bonjour \(firstName)."
        return WiaChatMessage(
            id: "welcome-main",
            text: "\(greeting) Je suis WIA, le même moteur conversationnel que dans Pilotis, intégré à WAPI. Votre mémoire est privée et aucune action n’est exécutée sans votre confirmation.",
            fromUser: false,
            failed: false
        )
    }
}

private enum WiaAssistantError: LocalizedError {
    case emptyResponse

    var errorDescription: String? { "WIA n’a renvoyé aucune réponse." }
}

struct WiaAssistantView: View {
    @StateObject private var model = WiaAssistantViewModel()
    @State private var prompt = ""
    @FocusState private var composerFocused: Bool

    private let suggestions = [
        "Explique-moi un sujet",
        "Prépare une réponse client",
        "Analyse une idée Business",
        "Aide-moi à organiser un projet",
    ]

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider()
            conversation
        }
        .background(Color(.systemGroupedBackground))
        .safeAreaInset(edge: .bottom, spacing: 0) {
            composer
        }
        .navigationBarHidden(true)
        .task { await model.loadHistory() }
    }

    private var header: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(LinearGradient(colors: [.whappyBlue, Color(red: 0.04, green: 0.32, blue: 0.91)], startPoint: .topLeading, endPoint: .bottomTrailing))
                Image(systemName: "wand.and.stars").font(.title3.bold()).foregroundStyle(.white)
            }
            .frame(width: 46, height: 46)
            VStack(alignment: .leading, spacing: 3) {
                Text("WIA").font(.title2.bold())
                HStack(spacing: 5) {
                    Circle().fill(model.memoryAvailable ? Color.green : Color.orange).frame(width: 7, height: 7)
                    Text(model.memoryAvailable ? "WIA Chat · mémoire privée synchronisée" : "Mémoire locale · synchronisation à reprendre")
                        .font(.caption2).foregroundStyle(.secondary)
                }
            }
            Spacer()
            if model.loadingHistory { ProgressView().tint(.whappyBlue) }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
        .background(.background)
    }

    private var conversation: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 12) {
                    if model.messages.count <= 1 && !composerFocused {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Que voulez-vous faire ?").font(.headline)
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8) {
                                    ForEach(suggestions, id: \.self) { suggestion in
                                        Button(suggestion) { prompt = suggestion; submit() }
                                            .buttonStyle(.bordered)
                                            .clipShape(Capsule())
                                    }
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    ForEach(model.messages) { message in
                        messageRow(message).id(message.id)
                    }
                    if model.waitingForReply {
                        HStack(spacing: 9) {
                            assistantAvatar
                            HStack(spacing: 8) {
                                ProgressView().controlSize(.small).tint(.whappyBlue)
                                Text("WIA prépare sa réponse…").font(.caption).foregroundStyle(.secondary)
                            }
                            .padding(.horizontal, 14).padding(.vertical, 11)
                            .background(.background).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                            Spacer(minLength: 40)
                        }
                        .id("wia-waiting")
                    }
                    Color.clear
                        .frame(height: 1)
                        .id("wia-bottom")
                }
                .padding(14)
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: model.messages.count) { _, _ in
                withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo("wia-bottom", anchor: .bottom) }
            }
            .onChange(of: model.waitingForReply) { _, waiting in
                if waiting { withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo("wia-bottom", anchor: .bottom) } }
            }
            .onChange(of: composerFocused) { _, focused in
                guard focused else { return }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                    withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo("wia-bottom", anchor: .bottom) }
                }
            }
        }
    }

    private func messageRow(_ message: WiaChatMessage) -> some View {
        HStack(alignment: .bottom, spacing: 8) {
            if message.fromUser { Spacer(minLength: 45) } else { assistantAvatar }
            Text(message.text)
                .font(.body)
                .foregroundStyle(message.fromUser ? .white : .primary)
                .textSelection(.enabled)
                .padding(.horizontal, 14).padding(.vertical, 11)
                .background(message.fromUser ? Color.whappyBlue : message.failed ? Color.red.opacity(0.10) : Color(.systemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 19, style: .continuous))
                .shadow(color: message.fromUser ? .clear : .black.opacity(0.05), radius: 4, y: 2)
            if !message.fromUser { Spacer(minLength: 45) }
        }
        .frame(maxWidth: .infinity)
    }

    private var assistantAvatar: some View {
        ZStack {
            Circle().fill(Color.whappyBlue)
            Image(systemName: "wand.and.stars").font(.caption.bold()).foregroundStyle(.white)
        }
        .frame(width: 30, height: 30)
    }

    private var composer: some View {
        HStack(alignment: .bottom, spacing: 9) {
            TextField("Écrivez à WIA…", text: $prompt, axis: .vertical)
                .lineLimit(1...5)
                .focused($composerFocused)
                .submitLabel(.send)
                .onSubmit(submit)
                .padding(.horizontal, 14).padding(.vertical, 11)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            Button(action: submit) {
                Image(systemName: "arrow.up").font(.headline.bold()).foregroundStyle(.white)
                    .frame(width: 46, height: 46).background(Color.whappyBlue).clipShape(Circle())
            }
            .disabled(prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || model.waitingForReply)
            .opacity(prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || model.waitingForReply ? 0.45 : 1)
        }
        .padding(.horizontal, 12).padding(.vertical, 9)
        .background(.regularMaterial)
        .overlay(alignment: .top) { Divider() }
    }

    private func submit() {
        let value = prompt
        guard !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        prompt = ""
        Task { await model.send(value) }
    }
}
