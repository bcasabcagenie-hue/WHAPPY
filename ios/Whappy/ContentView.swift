import AVFoundation
import PhotosUI
import SwiftUI
import UIKit

struct ContentView: View {
    @EnvironmentObject private var store: WhappyStore

    var body: some View {
        TabView(selection: $store.selectedTab) {
            NavigationStack { HomeView() }
                .tabItem { Label("Accueil", systemImage: "house.fill") }.tag(WhappyTab.home)
            NavigationStack { MessagesView() }
                .tabItem { Label("Messages", systemImage: "message.fill") }.badge(store.unreadCount).tag(WhappyTab.messages)
            NavigationStack { CallsView() }
                .tabItem { Label("Appels", systemImage: "phone.fill") }.tag(WhappyTab.calls)
            NavigationStack { MarketView() }
                .tabItem { Label("Marché", systemImage: "storefront.fill") }.badge(store.cartCount).tag(WhappyTab.market)
            NavigationStack { LiveView() }
                .tabItem { Label("Live", systemImage: "video.fill") }.tag(WhappyTab.live)
            NavigationStack { ServicesView() }
                .tabItem { Label("Services", systemImage: "wallet.pass.fill") }.tag(WhappyTab.services)
            NavigationStack { ProfileView() }
                .tabItem { Label("Profil", systemImage: "person.crop.circle.fill") }.tag(WhappyTab.profile)
        }
        .tint(.whappyBlue)
    }
}

private struct BrandHeader: View {
    let subtitle: String
    @State private var showActivity = false

    var body: some View {
        HStack(spacing: 12) {
            Image("WhappyMark").resizable().scaledToFit().frame(width: 46, height: 46).clipShape(RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 1) {
                Text("WHAPPY").font(.title2.bold()).foregroundStyle(Color.whappyInk)
                Text(subtitle).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button { showActivity = true } label: { Image(systemName: "bell.fill") }
                .buttonStyle(.bordered).clipShape(Circle())
        }
        .sheet(isPresented: $showActivity) { ActivityCenterView() }
    }
}

struct HomeView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var composingMoment = false
    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                BrandHeader(subtitle: "Vos opportunités, maintenant")
                ZStack(alignment: .bottomLeading) {
                    LinearGradient(colors: [.whappyBlue, Color(red: 0, green: 0.37, blue: 0.62)], startPoint: .topLeading, endPoint: .bottomTrailing)
                    Circle().fill(.white.opacity(0.12)).frame(width: 170).offset(x: 220, y: -50)
                    VStack(alignment: .leading, spacing: 9) {
                        Label("WHAPPY PULSE", systemImage: "waveform.path.ecg").font(.caption.bold()).foregroundStyle(.white.opacity(0.85))
                        Text("Tout ce qui bouge\nautour de vous.").font(.system(size: 30, weight: .black, design: .rounded)).foregroundStyle(.white)
                        Text("Explorez, échangez, payez et discutez.").font(.subheadline).foregroundStyle(.white.opacity(0.82))
                    }.padding(24)
                }
                .frame(height: 220).clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))

                Text("Explorer").font(.title3.bold()).foregroundStyle(Color.whappyInk)
                LazyVGrid(columns: columns, spacing: 12) {
                    ActionCard(title: "Marché", subtitle: "\(store.listings.count) offres", icon: "storefront.fill", color: .whappyBlue) { store.selectedTab = .market }
                    ActionCard(title: "En direct", subtitle: "\(store.liveRooms.filter(\.live).count) lives", icon: "video.fill", color: .red) { store.selectedTab = .live }
                    ActionCard(title: "Messages", subtitle: "\(store.unreadCount) nouveau", icon: "message.fill", color: .purple) { store.selectedTab = .messages }
                    ActionCard(title: "Services", subtitle: "Wallet et demandes", icon: "wallet.pass.fill", color: .orange) { store.selectedTab = .services }
                }
                HStack { Text("Moments").font(.title3.bold()).foregroundStyle(Color.whappyInk); Spacer(); Button { composingMoment = true } label: { Label("Publier", systemImage: "plus.circle.fill") } }
                ForEach(store.moments) { moment in
                    VStack(alignment: .leading, spacing: 8) { HStack { InitialsAvatar(text: "CB", size: 38); VStack(alignment: .leading) { Text("Vous").font(.headline); Text(moment.createdAt, style: .relative).font(.caption).foregroundStyle(.secondary) } }; Text(moment.title).font(.title3.bold()).foregroundStyle(Color.whappyInk); Text(moment.text).foregroundStyle(.secondary) }.frame(maxWidth: .infinity, alignment: .leading).padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 18))
                }
                Text("Tendances près de vous").font(.title3.bold()).foregroundStyle(Color.whappyInk)
                ForEach(store.listings.prefix(2)) { listing in
                    NavigationLink(value: listing) { ListingRow(listing: listing) }.buttonStyle(.plain)
                }
            }.padding()
        }
        .background(Color.whappyBackground)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: Listing.self) { ListingDetailView(listingID: $0.id) }
        .sheet(isPresented: $composingMoment) { MomentComposerView() }
    }
}

private struct MomentComposerView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WhappyStore
    @State private var title = ""
    @State private var text = ""
    var body: some View {
        NavigationStack {
            Form { Section("Nouveau Moment") { TextField("Titre", text: $title); TextField("Partagez quelque chose", text: $text, axis: .vertical).lineLimit(4...8) }; Section { Text("Votre Moment reste disponible dans l’accueil de l’application.").font(.footnote).foregroundStyle(.secondary) } }
                .navigationTitle("Publier")
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Publier") { store.createMoment(title: title, text: text); dismiss() }.disabled(title.count < 2 || text.count < 3) } }
        }
    }
}

private struct ActionCard: View {
    let title: String
    let subtitle: String
    let icon: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 12) {
                Image(systemName: icon).font(.title2).foregroundStyle(color)
                Text(title).font(.headline).foregroundStyle(Color.whappyInk)
                Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(16).background(.white).clipShape(RoundedRectangle(cornerRadius: 18))
        }.buttonStyle(.plain)
    }
}

struct MessagesView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var composing = false
    @State private var search = ""
    private var filtered: [Conversation] { search.isEmpty ? store.conversations : store.conversations.filter { $0.name.localizedCaseInsensitiveContains(search) || $0.lastMessage.localizedCaseInsensitiveContains(search) || $0.phoneNumber.contains(search) } }

    var body: some View {
        List(filtered) { conversation in
            NavigationLink(value: conversation) {
                HStack(spacing: 13) {
                    InitialsAvatar(text: conversation.initials)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(conversation.name).font(.headline)
                        Text(conversation.lastMessage).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Spacer()
                    if conversation.unread { Circle().fill(Color.whappyBlue).frame(width: 10, height: 10) }
                }.padding(.vertical, 5)
            }
            .swipeActions(edge: .leading) { Button { store.toggleUnread(conversation) } label: { Label(conversation.unread ? "Marquer lu" : "Non lu", systemImage: conversation.unread ? "envelope.open" : "envelope.badge") }.tint(.whappyBlue) }
            .swipeActions(edge: .trailing) { Button(role: .destructive) { store.deleteConversation(conversation) } label: { Label("Supprimer", systemImage: "trash") } }
        }
        .listStyle(.plain).navigationTitle("Messages")
        .searchable(text: $search, prompt: "Nom ou contenu récent")
        .navigationDestination(for: Conversation.self) { conversation in ConversationView(conversationID: conversation.id).onAppear { store.markRead(conversation) } }
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { composing = true } label: { Label("Nouveau", systemImage: "square.and.pencil") } } }
        .sheet(isPresented: $composing) { NewConversationView() }
    }
}

private struct NewConversationView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WhappyStore
    @State private var name = ""
    @State private var phone = "+242"

    var body: some View {
        NavigationStack {
            Form {
                Section("Contact") {
                    TextField("Nom", text: $name)
                    TextField("Téléphone", text: $phone).keyboardType(.phonePad)
                }
                Section { Text("Le contact reste enregistré sur cet appareil et la conversation peut être utilisée immédiatement.").font(.footnote).foregroundStyle(.secondary) }
            }
            .navigationTitle("Nouvelle discussion")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Créer") { store.createConversation(name: name, phone: phone); dismiss() }.disabled(name.count < 2 || phone.count < 8) }
            }
        }
    }
}

private struct ConversationView: View {
    @EnvironmentObject private var store: WhappyStore
    let conversationID: UUID
    @State private var draft = ""
    @State private var callMode: CallMode?
    @State private var photoItem: PhotosPickerItem?
    @State private var recorder: AVAudioRecorder?
    @State private var player: AVAudioPlayer?
    @State private var recording = false
    @State private var searchOpen = false
    @State private var search = ""
    @State private var replyTo: Message?
    @State private var editingMessage: Message?
    private var conversation: Conversation? { store.conversations.first { $0.id == conversationID } }
    private var visibleMessages: [Message] {
        let source = conversation?.messages ?? []
        guard !search.isEmpty else { return source }
        return source.filter { $0.text.localizedCaseInsensitiveContains(search) || ($0.replyText?.localizedCaseInsensitiveContains(search) == true) }
    }

    private var draftKey: String { "whappy.draft.\(conversationID.uuidString)" }

    var body: some View {
        VStack(spacing: 0) {
            if searchOpen { HStack { Image(systemName: "magnifyingglass").foregroundStyle(.secondary); TextField("Rechercher dans la discussion", text: $search); Button { search = ""; searchOpen = false } label: { Image(systemName: "xmark.circle.fill") } }.padding(10).background(.bar) }
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 8) {
                        if visibleMessages.isEmpty && !search.isEmpty { ContentUnavailableView("Aucun résultat", systemImage: "magnifyingglass", description: Text("Aucun message ne contient « \(search) ».")) }
                        ForEach(Array(visibleMessages.enumerated()), id: \.element.id) { index, message in
                            VStack(spacing: 7) {
                                if index == 0 || !Calendar.current.isDate(message.sentAt, inSameDayAs: visibleMessages[index - 1].sentAt) {
                                    Text(messageDayLabel(message.sentAt)).font(.caption2.bold()).foregroundStyle(.secondary).padding(.horizontal, 10).padding(.vertical, 5).background(Color(.tertiarySystemFill)).clipShape(Capsule())
                                }
                                HStack {
                                if message.mine { Spacer(minLength: 65) }
                                VStack(alignment: message.mine ? .trailing : .leading, spacing: 5) {
                                    if let replied = message.replyText, !replied.isEmpty { Text("↩ \(replied)").font(.caption).lineLimit(2).padding(7).frame(maxWidth: .infinity, alignment: .leading).background(.white.opacity(message.mine ? 0.16 : 0.55)).clipShape(RoundedRectangle(cornerRadius: 9)) }
                                    if message.deleted { Text("Message supprimé").italic().opacity(0.7) }
                                    else if message.kind == "image", let path = message.mediaPath, let image = UIImage(contentsOfFile: path) {
                                        Image(uiImage: image).resizable().scaledToFill().frame(width: 190, height: 150).clipShape(RoundedRectangle(cornerRadius: 12))
                                    } else if message.kind == "audio", let path = message.mediaPath {
                                        Button { playAudio(path) } label: { Label(player?.isPlaying == true ? "Lecture…" : "Lire la note vocale", systemImage: "waveform.circle.fill") }.buttonStyle(.plain)
                                    } else { Text(message.text) }
                                    if !message.reactions.isEmpty { HStack(spacing: 4) { ForEach(Array(Dictionary(grouping: message.reactions.values, by: { $0 })).sorted(by: { $0.key < $1.key }), id: \.key) { group in Text(group.key + (group.value.count > 1 ? " \(group.value.count)" : "")).font(.caption).padding(.horizontal, 6).padding(.vertical, 3).background(.white.opacity(message.mine ? 0.18 : 0.7)).clipShape(Capsule()) } } }
                                    HStack(spacing: 4) { if message.edited { Text("modifié ·").font(.caption2).opacity(0.6) }; Text(message.sentAt, style: .time).font(.caption2).opacity(0.65); if message.mine { Image(systemName: "checkmark.circle.fill").font(.caption2).opacity(0.8) } }
                                }
                                .padding(.horizontal, 14).padding(.vertical, 9)
                                .background(message.mine ? Color.whappyBlue : Color(.secondarySystemBackground))
                                .foregroundStyle(message.mine ? .white : Color.whappyInk).clipShape(RoundedRectangle(cornerRadius: 17))
                                .contextMenu {
                                    if !message.deleted {
                                        Button { replyTo = message } label: { Label("Répondre", systemImage: "arrowshape.turn.up.left") }
                                        Menu("Réagir") { ForEach(["❤️", "👍", "😂", "😮", "🙏"], id: \.self) { emoji in Button(emoji) { store.react(to: message.id, in: conversationID, emoji: emoji) } } }
                                        if !message.text.isEmpty { Button { UIPasteboard.general.string = message.text } label: { Label("Copier", systemImage: "doc.on.doc") } }
                                        if message.mine && message.kind == "text" { Button { editingMessage = message; replyTo = nil; draft = message.text } label: { Label("Modifier", systemImage: "pencil") } }
                                        if message.mine { Button(role: .destructive) { store.deleteMessage(message.id, in: conversationID) } label: { Label("Supprimer pour tous", systemImage: "trash") } }
                                    }
                                }
                                if !message.mine { Spacer(minLength: 65) }
                                }.id(message.id)
                            }
                        }
                    }.padding()
                }
                .onChange(of: conversation?.messages.count) { _, _ in if let id = conversation?.messages.last?.id { withAnimation { proxy.scrollTo(id) } } }
            }
            if let replyTo { HStack { Image(systemName: "arrowshape.turn.up.left.fill").foregroundStyle(Color.whappyBlue); VStack(alignment: .leading) { Text("Répondre").font(.caption.bold()).foregroundStyle(Color.whappyBlue); Text(replyTo.text).font(.caption).lineLimit(1) }; Spacer(); Button { self.replyTo = nil } label: { Image(systemName: "xmark.circle.fill") } }.padding(.horizontal).padding(.vertical, 8).background(Color.whappyBlue.opacity(0.08)) }
            if let editingMessage { HStack { Image(systemName: "pencil.circle.fill").foregroundStyle(.orange); VStack(alignment: .leading) { Text("Modifier le message").font(.caption.bold()).foregroundStyle(.orange); Text(editingMessage.text).font(.caption).lineLimit(1) }; Spacer(); Button { self.editingMessage = nil; draft = "" } label: { Image(systemName: "xmark.circle.fill") } }.padding(.horizontal).padding(.vertical, 8).background(Color.orange.opacity(0.08)) }
            HStack(spacing: 10) {
                PhotosPicker(selection: $photoItem, matching: .images) { Image(systemName: "photo.circle.fill").font(.title2) }.disabled(recording)
                Button { toggleRecording() } label: { Image(systemName: recording ? "stop.circle.fill" : "mic.circle.fill").font(.title2).foregroundStyle(recording ? .red : Color.whappyBlue) }
                TextField("Votre message", text: $draft, axis: .vertical).textFieldStyle(.roundedBorder)
                Button { submitDraft() } label: { Image(systemName: editingMessage == nil ? "arrow.up.circle.fill" : "checkmark.circle.fill").font(.system(size: 34)) }
                    .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }.padding().background(.bar)
        }
        .navigationTitle(conversation?.name ?? "Discussion").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItemGroup(placement: .topBarTrailing) { Button { searchOpen.toggle(); if !searchOpen { search = "" } } label: { Image(systemName: "magnifyingglass") }; Button { callMode = .audio } label: { Image(systemName: "phone.fill") }; Button { callMode = .video } label: { Image(systemName: "video.fill") } } }
        .sheet(item: $callMode) { mode in if let conversation { SystemCallView(name: conversation.name, phone: conversation.phoneNumber, mode: mode) } }
        .onChange(of: photoItem) { _, item in guard let item else { return }; Task { await attachPhoto(item) } }
        .onAppear { if draft.isEmpty { draft = UserDefaults.standard.string(forKey: draftKey) ?? "" } }
        .onChange(of: draft) { _, value in if editingMessage == nil { UserDefaults.standard.set(value, forKey: draftKey) } }
    }

    private func submitDraft() {
        let value = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }
        if let editingMessage { store.editMessage(editingMessage.id, in: conversationID, text: value); self.editingMessage = nil }
        else { store.send(value, to: conversationID, replyTo: replyTo) }
        draft = ""; replyTo = nil; UserDefaults.standard.removeObject(forKey: draftKey)
    }

    private func messageDayLabel(_ date: Date) -> String {
        if Calendar.current.isDateInToday(date) { return "Aujourd’hui" }
        if Calendar.current.isDateInYesterday(date) { return "Hier" }
        return date.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(Locale(identifier: "fr_FR"))).capitalized
    }

    private func attachPhoto(_ item: PhotosPickerItem) async {
        guard let data = try? await item.loadTransferable(type: Data.self), let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        let url = directory.appendingPathComponent("whappy-photo-\(UUID().uuidString).jpg")
        guard (try? data.write(to: url, options: .atomic)) != nil else { return }
        store.sendMedia(kind: "image", path: url.path, to: conversationID)
        photoItem = nil
    }

    private func toggleRecording() {
        if let recorder {
            recorder.stop(); self.recorder = nil; recording = false
            store.sendMedia(kind: "audio", path: recorder.url.path, to: conversationID)
            return
        }
        AVAudioApplication.requestRecordPermission { granted in
            guard granted else { return }
            DispatchQueue.main.async {
                guard let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
                let url = directory.appendingPathComponent("whappy-voice-\(UUID().uuidString).m4a")
                do {
                    try AVAudioSession.sharedInstance().setCategory(.playAndRecord, mode: .spokenAudio, options: [.defaultToSpeaker])
                    try AVAudioSession.sharedInstance().setActive(true)
                    let audio = try AVAudioRecorder(url: url, settings: [AVFormatIDKey: Int(kAudioFormatMPEG4AAC), AVSampleRateKey: 44_100, AVNumberOfChannelsKey: 1, AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue])
                    audio.record(); recorder = audio; recording = true
                } catch { recording = false }
            }
        }
    }

    private func playAudio(_ path: String) {
        if player?.isPlaying == true { player?.stop(); player = nil; return }
        guard let audio = try? AVAudioPlayer(contentsOf: URL(fileURLWithPath: path)) else { return }
        audio.play(); player = audio
    }
}

struct CallsView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var selected: CallRecord?
    @State private var mode: CallMode = .audio

    var body: some View {
        List {
            Section("Récents") {
                ForEach(store.calls) { call in
                    HStack(spacing: 13) {
                        Image(systemName: call.mode.systemImage).foregroundStyle(call.missed ? .red : Color.whappyBlue).frame(width: 32)
                        VStack(alignment: .leading) { Text(call.name).font(.headline); Text(call.date, style: .relative).font(.caption).foregroundStyle(.secondary) }
                        Spacer()
                        Button { mode = .audio; selected = call } label: { Image(systemName: "phone.circle.fill").font(.title2) }.buttonStyle(.plain)
                        Button { mode = .video; selected = call } label: { Image(systemName: "video.circle.fill").font(.title2) }.buttonStyle(.plain)
                    }.padding(.vertical, 4)
                }
            }
        }
        .navigationTitle("Appels")
        .sheet(item: $selected) { call in SystemCallView(name: call.name, phone: call.phoneNumber, mode: mode) }
    }
}

private struct SystemCallView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WhappyStore
    let name: String
    let phone: String
    let mode: CallMode
    @State private var error: String?

    private var destination: URL? {
        let number = phone.replacingOccurrences(of: " ", with: "")
        return URL(string: (mode == .audio ? "tel://" : "facetime://") + number)
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 22) {
                Spacer()
                ZStack { Circle().fill(Color.whappyBlue.opacity(0.14)).frame(width: 122, height: 122); Image(systemName: mode.systemImage).font(.system(size: 44, weight: .semibold)).foregroundStyle(Color.whappyBlue) }
                Text(name).font(.title.bold()).foregroundStyle(Color.whappyInk)
                Text(phone).font(.callout).foregroundStyle(.secondary)
                Button { startCall() } label: { Label(mode == .audio ? "Appeler" : "Démarrer FaceTime", systemImage: mode.systemImage).frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).tint(mode == .audio ? .green : .whappyBlue).controlSize(.large)
                Text(mode == .audio ? "L’appel s’ouvre dans Téléphone." : "L’appel vidéo s’ouvre dans FaceTime.").font(.footnote).foregroundStyle(.secondary)
                if let error { Text(error).font(.footnote).foregroundStyle(.red).multilineTextAlignment(.center) }
                Spacer()
            }
            .padding(28).background(Color.whappyBackground).navigationTitle(mode.title).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } } }
        }
    }

    private func startCall() {
        store.recordCall(name: name, phone: phone, mode: mode)
        guard let destination, UIApplication.shared.canOpenURL(destination) else { error = "Utilisez un iPhone configuré pour Téléphone ou FaceTime."; return }
        UIApplication.shared.open(destination)
    }
}

struct MarketView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var search = ""
    @State private var selling = false
    @State private var showingCart = false
    private var filtered: [Listing] { search.isEmpty ? store.listings : store.listings.filter { $0.title.localizedCaseInsensitiveContains(search) || $0.place.localizedCaseInsensitiveContains(search) } }

    var body: some View {
        ScrollView { LazyVStack(spacing: 12) { ForEach(filtered) { listing in NavigationLink(value: listing) { ListingRow(listing: listing) }.buttonStyle(.plain) } }.padding() }
            .background(Color.whappyBackground).navigationTitle("Marché").searchable(text: $search, prompt: "Produit, service ou quartier")
            .navigationDestination(for: Listing.self) { ListingDetailView(listingID: $0.id) }
            .toolbar { ToolbarItemGroup(placement: .topBarTrailing) { Button { showingCart = true } label: { Label("Panier", systemImage: "cart.fill").badge(store.cartCount) }; Button { selling = true } label: { Label("Vendre", systemImage: "plus") } } }
            .sheet(isPresented: $selling) { SellListingView() }.sheet(isPresented: $showingCart) { CartView() }
    }
}

private struct ListingRow: View {
    let listing: Listing
    var body: some View {
        HStack(spacing: 16) {
            ZStack { RoundedRectangle(cornerRadius: 16).fill(Color.whappyBlue.opacity(0.1)); Image(systemName: listing.icon).font(.system(size: 30)).foregroundStyle(Color.whappyBlue) }.frame(width: 82, height: 82)
            VStack(alignment: .leading, spacing: 5) { Text(listing.title).font(.headline).foregroundStyle(Color.whappyInk).lineLimit(2); Text(listing.price).font(.subheadline.bold()).foregroundStyle(listing.acceptsTrade ? .orange : Color.whappyBlue); Label("\(listing.place) · \(listing.seller)", systemImage: "mappin.and.ellipse").font(.caption).foregroundStyle(.secondary).lineLimit(1) }
            Spacer()
            if listing.saved { Image(systemName: "bookmark.fill").foregroundStyle(Color.whappyBlue) }
        }.padding(12).background(.white).clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

private struct ListingDetailView: View {
    @EnvironmentObject private var store: WhappyStore
    let listingID: UUID
    @State private var added = false
    private var listing: Listing? { store.listings.first { $0.id == listingID } }
    var body: some View {
        ScrollView {
            if let listing {
                VStack(alignment: .leading, spacing: 18) {
                    ZStack { RoundedRectangle(cornerRadius: 28).fill(Color.whappyBlue.opacity(0.12)); Image(systemName: listing.icon).font(.system(size: 80)).foregroundStyle(Color.whappyBlue) }.frame(height: 280)
                    Text(listing.title).font(.title.bold()).foregroundStyle(Color.whappyInk)
                    Text(listing.price).font(.title3.bold()).foregroundStyle(listing.acceptsTrade ? .orange : Color.whappyBlue)
                    Label("\(listing.place) · vendu par \(listing.seller)", systemImage: "mappin.and.ellipse").foregroundStyle(.secondary)
                    Button { store.addToCart(listing); added = true } label: { Label(added ? "Ajouté au panier" : "Ajouter au panier", systemImage: added ? "checkmark.circle.fill" : "cart.badge.plus").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent).controlSize(.large)
                    Button { store.toggleSaved(listing) } label: { Label(listing.saved ? "Retirer des favoris" : "Enregistrer l’annonce", systemImage: listing.saved ? "bookmark.slash" : "bookmark") }.buttonStyle(.bordered).frame(maxWidth: .infinity)
                }.padding()
            }
        }.background(Color.whappyBackground).navigationTitle("Annonce").navigationBarTitleDisplayMode(.inline)
    }
}

private struct SellListingView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WhappyStore
    @State private var title = ""; @State private var price = ""; @State private var place = "Brazzaville"; @State private var trade = false
    var body: some View {
        NavigationStack { Form { Section("Annonce") { TextField("Titre", text: $title); TextField("Prix ou proposition", text: $price); TextField("Quartier / ville", text: $place); Toggle("Accepter le troc", isOn: $trade) } }.navigationTitle("Vendre").toolbar { ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Publier") { store.addListing(title: title, price: price, place: place, trade: trade); dismiss() }.disabled(title.count < 2) } } }
    }
}

private struct CartView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WhappyStore
    @State private var delivery = ""; @State private var reference: String?
    var body: some View {
        NavigationStack {
            Form {
                if store.cart.isEmpty { ContentUnavailableView("Panier vide", systemImage: "cart", description: Text("Ajoutez une annonce depuis le Marché.")) }
                else {
                    Section("Articles") { ForEach(store.cart) { line in HStack { VStack(alignment: .leading) { Text(line.listing.title).font(.headline); Text(line.listing.price).font(.caption).foregroundStyle(.secondary) }; Spacer(); Button { store.changeQuantity(line, delta: -1) } label: { Image(systemName: "minus.circle") }; Text("\(line.quantity)"); Button { store.changeQuantity(line, delta: 1) } label: { Image(systemName: "plus.circle") } } } }
                    Section("Livraison") { TextField("Adresse ou point de rendez-vous", text: $delivery, axis: .vertical) }
                    Section { Button("Confirmer la commande") { reference = store.checkout(delivery: delivery) }.disabled(delivery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty).frame(maxWidth: .infinity) }
                }
                if let reference { Section { Label("Commande \(reference) enregistrée", systemImage: "checkmark.seal.fill").foregroundStyle(.green) } }
            }.navigationTitle("Panier").toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } } }
        }
    }
}

struct LiveView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var showingStudio = false
    var body: some View {
        ScrollView { LazyVStack(spacing: 16) { ForEach(store.liveRooms) { room in NavigationLink(value: room) { LiveCard(room: room) }.buttonStyle(.plain) } }.padding() }
            .background(Color.whappyBackground).navigationTitle("En direct")
            .navigationDestination(for: LiveRoom.self) { LiveRoomView(roomID: $0.id) }
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { showingStudio = true } label: { Label("Créer", systemImage: "video.badge.plus") } } }
            .sheet(isPresented: $showingStudio) { LiveStudioView() }
    }
}

private struct LiveCard: View {
    let room: LiveRoom
    var body: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(colors: [.black.opacity(0.25), .black.opacity(0.9)], startPoint: .top, endPoint: .bottom)
            Image(systemName: room.icon).font(.system(size: 74)).foregroundStyle(.white.opacity(0.3)).frame(maxWidth: .infinity, maxHeight: .infinity)
            VStack(alignment: .leading, spacing: 6) { HStack { Text(room.live ? "LIVE" : "TERMINÉ").font(.caption2.bold()).padding(.horizontal, 8).padding(.vertical, 4).background(room.live ? .red : .gray).clipShape(Capsule()); Label(room.viewers.formatted(), systemImage: "eye.fill").font(.caption) }; Text(room.title).font(.title3.bold()); Text("\(room.host) · \(room.category)").font(.caption).foregroundStyle(.white.opacity(0.75)) }.foregroundStyle(.white).padding(18)
        }.frame(height: 230).clipShape(RoundedRectangle(cornerRadius: 24))
    }
}

private struct LiveRoomView: View {
    @EnvironmentObject private var store: WhappyStore
    let roomID: UUID
    @State private var comment = ""; @State private var comments = ["Amina : Très belle sélection !"]
    private var room: LiveRoom? { store.liveRooms.first { $0.id == roomID } }
    var body: some View {
        VStack(spacing: 0) {
            if let room { LiveCard(room: room).padding(); ScrollView { VStack(alignment: .leading, spacing: 10) { ForEach(comments, id: \.self) { Text($0).padding(10).background(Color(.secondarySystemBackground)).clipShape(RoundedRectangle(cornerRadius: 12)) } }.frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal) }; HStack { TextField("Commenter le direct", text: $comment).textFieldStyle(.roundedBorder); Button { let value = comment.trimmingCharacters(in: .whitespacesAndNewlines); if !value.isEmpty { comments.append("Vous : \(value)"); comment = "" } } label: { Image(systemName: "paperplane.fill") }.disabled(comment.isEmpty) }.padding().background(.bar) }
        }
        .navigationTitle(room?.host ?? "Live").navigationBarTitleDisplayMode(.inline)
        .toolbar { if let room, room.host == "Cyril Bokilo", room.live { ToolbarItem(placement: .topBarTrailing) { Button("Terminer", role: .destructive) { store.endLive(room) } } } }
    }
}

private struct LiveStudioView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WhappyStore
    @State private var title = ""; @State private var category = "Business"; @State private var permissionText = "La caméra et le microphone seront demandés au démarrage."; @State private var busy = false
    var body: some View {
        NavigationStack {
            Form { Section("Votre direct") { TextField("Titre", text: $title); Picker("Catégorie", selection: $category) { ForEach(["Business", "Mode", "Tech", "Maison"], id: \.self) { Text($0) } } }; Section { Label(permissionText, systemImage: "lock.shield.fill") } }
                .navigationTitle("Studio Live")
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button(busy ? "Préparation…" : "Démarrer") { start() }.disabled(title.count < 3 || busy) } }
        }
    }
    private func start() {
        busy = true
        Task {
            let camera = await AVCaptureDevice.requestAccess(for: .video)
            let microphone = await AVCaptureDevice.requestAccess(for: .audio)
            if camera && microphone, store.createLive(title: title, category: category) != nil { dismiss() }
            else { permissionText = "Autorisez la caméra et le microphone dans Réglages pour lancer le direct."; busy = false }
        }
    }
}

struct ServicesView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var paying = false; @State private var requestType: ServiceKind?; @State private var message: String?
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("WHAPPY WALLET").font(.caption.bold()).foregroundStyle(Color.whappyBlue)
                    Text(store.walletTransactions.isEmpty ? "Portefeuille non activé" : "\(store.walletBalance.formatted()) FCFA").font(.largeTitle.bold()).foregroundStyle(.white)
                    Text("Mode démonstration · aucun débit bancaire réel").font(.caption).foregroundStyle(.white.opacity(0.72))
                    if store.walletTransactions.isEmpty { Button("Activer avec 25 000 FCFA test") { store.activateDemoWallet(); message = "Portefeuille de démonstration activé." }.buttonStyle(.borderedProminent) }
                    else { HStack { Button("Payer") { paying = true }.buttonStyle(.borderedProminent); ShareLink(item: URL(string: "https://whappy.chat/pay/cyril-bokilo")!) { Label("Recevoir", systemImage: "qrcode") }.buttonStyle(.bordered) } }
                }.padding(22).frame(maxWidth: .infinity, alignment: .leading).background(Color.whappyInk).clipShape(RoundedRectangle(cornerRadius: 26))
                if let message { Label(message, systemImage: "checkmark.circle.fill").foregroundStyle(.green).padding(12).frame(maxWidth: .infinity, alignment: .leading).background(.white).clipShape(RoundedRectangle(cornerRadius: 14)) }
                Text("Services à la demande").font(.title3.bold())
                HStack { ServiceButton(title: "Transport", icon: "car.fill") { requestType = .transport }; ServiceButton(title: "Livraison", icon: "shippingbox.fill") { requestType = .delivery }; ServiceButton(title: "Assistance", icon: "cross.case.fill") { requestType = .help } }
                NavigationLink { OrdersView() } label: { Label("Mes commandes et livraisons", systemImage: "shippingbox.and.arrow.backward.fill").frame(maxWidth: .infinity, alignment: .leading).padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 16)) }
                NavigationLink { BusinessEditorView() } label: { Label(store.business == nil ? "Créer mon espace Business" : "Gérer \(store.business?.name ?? "mon activité")", systemImage: "briefcase.fill").frame(maxWidth: .infinity, alignment: .leading).padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 16)) }
                if !store.serviceRequests.isEmpty { Text("Demandes récentes").font(.title3.bold()); ForEach(store.serviceRequests.prefix(5)) { request in HStack { Image(systemName: "checkmark.circle.fill").foregroundStyle(.green); VStack(alignment: .leading) { Text(request.type).font(.headline); Text(request.details).font(.caption).foregroundStyle(.secondary).lineLimit(2) }; Spacer(); Text(request.status).font(.caption.bold()).foregroundStyle(.orange) }.padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 16)) } }
                if !store.walletTransactions.isEmpty { Text("Historique").font(.title3.bold()); ForEach(store.walletTransactions) { transaction in HStack { Image(systemName: transaction.amount >= 0 ? "arrow.down.circle.fill" : "arrow.up.circle.fill").foregroundStyle(transaction.amount >= 0 ? .green : Color.whappyBlue); VStack(alignment: .leading) { Text(transaction.label).font(.headline); Text(transaction.date, style: .date).font(.caption).foregroundStyle(.secondary) }; Spacer(); Text("\(transaction.amount > 0 ? "+" : "")\(transaction.amount.formatted()) FCFA").font(.subheadline.bold()) }.padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 16)) } }
            }.padding()
        }
        .background(Color.whappyBackground).navigationTitle("Services")
        .sheet(isPresented: $paying) { PaymentView { message = $0 } }
        .sheet(item: $requestType) { type in ServiceRequestView(type: type.rawValue) { message = $0 } }
    }
}

private enum ServiceKind: String, Identifiable {
    case transport = "Transport"
    case delivery = "Livraison"
    case help = "Assistance"
    var id: String { rawValue }
}

private struct ServiceButton: View {
    let title: String; let icon: String; let action: () -> Void
    var body: some View { Button(action: action) { VStack(spacing: 9) { Image(systemName: icon).font(.title2); Text(title).font(.caption.bold()) }.frame(maxWidth: .infinity).padding(.vertical, 18).background(.white).clipShape(RoundedRectangle(cornerRadius: 16)) }.buttonStyle(.plain) }
}

private struct PaymentView: View {
    @Environment(\.dismiss) private var dismiss; @EnvironmentObject private var store: WhappyStore
    @State private var recipient = ""; @State private var amount = ""; @State private var error: String?
    let onResult: (String) -> Void
    var body: some View { NavigationStack { Form { Section("Paiement test") { TextField("Bénéficiaire", text: $recipient); TextField("Montant FCFA", text: $amount).keyboardType(.numberPad) }; if let error { Section { Text(error).foregroundStyle(.red) } } }.navigationTitle("Payer").toolbar { ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Envoyer") { let value = Int(amount) ?? 0; if store.pay(recipient: recipient, amount: value) { onResult("Paiement test envoyé à \(recipient)."); dismiss() } else { error = "Vérifiez le bénéficiaire, le montant et le solde." } } } } } }
}

private struct ServiceRequestView: View {
    @Environment(\.dismiss) private var dismiss; @EnvironmentObject private var store: WhappyStore; let type: String; let onResult: (String) -> Void; @State private var details = ""
    var body: some View { NavigationStack { Form { Section(type) { TextField(type == "Transport" ? "Départ et destination" : "Décrivez votre besoin", text: $details, axis: .vertical).lineLimit(3...6) }; Section { Text("La demande est enregistrée sur l’appareil et reste visible dans Services.").font(.footnote).foregroundStyle(.secondary) } }.navigationTitle("Demande").toolbar { ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Envoyer") { store.createServiceRequest(type: type, details: details); onResult("Demande \(type) enregistrée."); dismiss() }.disabled(details.count < 5) } } } }
}

private struct OrdersView: View {
    @EnvironmentObject private var store: WhappyStore
    var body: some View { List { if store.orders.isEmpty { ContentUnavailableView("Aucune commande", systemImage: "shippingbox", description: Text("Validez un panier dans le Marché.")) } else { ForEach(store.orders) { order in Section(order.reference) { Text(order.lines.map { "\($0.quantity) × \($0.listing.title)" }.joined(separator: "\n")); Label(order.delivery, systemImage: "mappin.and.ellipse"); Label(order.status, systemImage: "clock.fill").foregroundStyle(.orange) } } } }.navigationTitle("Commandes") }
}

private struct BusinessEditorView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var name = ""; @State private var category = "Commerce"; @State private var bio = ""; @State private var city = "Brazzaville"; @State private var saved = false
    var body: some View {
        Form { Section("Identité") { TextField("Nom", text: $name); TextField("Catégorie", text: $category); TextField("Ville", text: $city) }; Section("Présentation") { TextField("Bio", text: $bio, axis: .vertical).lineLimit(3...6) }; Section { Button(saved ? "Enregistré" : "Enregistrer l’espace Business") { store.saveBusiness(name: name, category: category, bio: bio, city: city); saved = true }.disabled(name.count < 2).frame(maxWidth: .infinity) } }.navigationTitle("Business").onAppear { if let business = store.business { name = business.name; category = business.category; bio = business.bio; city = business.city } }
    }
}

struct ProfileView: View {
    @EnvironmentObject private var store: WhappyStore
    var body: some View {
        List {
            Section { HStack(spacing: 16) { InitialsAvatar(text: "CB", size: 62); VStack(alignment: .leading) { Text("Cyril Bokilo").font(.title3.bold()); Text("Compte WHAPPY natif").font(.subheadline).foregroundStyle(.secondary) } }.padding(.vertical, 8) }
            Section("Votre activité") { NavigationLink { BusinessEditorView() } label: { Label("Ma boutique", systemImage: "storefront.fill") }; NavigationLink { OrdersView() } label: { Label("Mes commandes", systemImage: "shippingbox.fill") }; Button { store.selectedTab = .services } label: { Label("Mon portefeuille", systemImage: "wallet.pass.fill") } }
            Section("Réglages") { Toggle(isOn: $store.notificationsEnabled) { Label("Notifications", systemImage: "bell.badge.fill") }; NavigationLink { PrivacySettingsView() } label: { Label("Confidentialité et sécurité", systemImage: "lock.shield.fill") }; NavigationLink { DataSettingsView() } label: { Label("Stockage et données", systemImage: "internaldrive.fill") }; NavigationLink { InfoView(title: "Aide", message: "Utilisez Messages pour discuter, Marché pour acheter ou vendre, Live pour diffuser, et Services pour payer en mode démonstration ou demander une prestation.", icon: "questionmark.circle.fill") } label: { Label("Aide", systemImage: "questionmark.circle.fill") } }
            Section { Text("WHAPPY iOS · application native").foregroundStyle(.secondary) }
        }.navigationTitle("Profil")
    }
}

private struct InfoView: View {
    let title: String; let message: String; let icon: String
    var body: some View { VStack(spacing: 20) { Image(systemName: icon).font(.system(size: 54)).foregroundStyle(Color.whappyBlue); Text(title).font(.title.bold()); Text(message).multilineTextAlignment(.center).foregroundStyle(.secondary); Link("Contacter l’assistance", destination: URL(string: "mailto:support@whappy.chat?subject=Aide%20WHAPPY")!).buttonStyle(.borderedProminent) }.padding(30).frame(maxWidth: .infinity, maxHeight: .infinity).background(Color.whappyBackground).navigationTitle(title).navigationBarTitleDisplayMode(.inline) }
}

private struct PrivacySettingsView: View {
    @EnvironmentObject private var store: WhappyStore
    var body: some View { Form { Section("Qui peut vous contacter ?") { Picker("Contacts autorisés", selection: $store.privacyMode) { Text("Mes contacts").tag("contacts"); Text("Tous les utilisateurs").tag("everyone"); Text("Personne").tag("nobody") }.pickerStyle(.inline) }; Section("Sécurité") { Label("Les médias restent dans le stockage privé de l’application.", systemImage: "lock.fill"); Label("Téléphone et FaceTime demandent une confirmation avant l’appel.", systemImage: "phone.badge.checkmark") } }.navigationTitle("Confidentialité") }
}

private struct DataSettingsView: View {
    @EnvironmentObject private var store: WhappyStore
    var body: some View { Form { Section("Réseau") { Toggle("Économiseur de données", isOn: $store.dataSaverEnabled); Text("Réduit le chargement automatique des médias lorsque votre connexion est limitée.").font(.footnote).foregroundStyle(.secondary) }; Section("Stockage") { Label("Photos et notes vocales conservées dans WHAPPY", systemImage: "folder.fill"); Text("Les contenus sont supprimés avec l’application depuis les réglages iOS.").font(.footnote).foregroundStyle(.secondary) } }.navigationTitle("Stockage et données") }
}

private struct ActivityCenterView: View {
    @Environment(\.dismiss) private var dismiss; @EnvironmentObject private var store: WhappyStore
    var body: some View { NavigationStack { List { if store.unreadCount > 0 { Button { store.selectedTab = .messages; dismiss() } label: { Label("\(store.unreadCount) message(s) non lu(s)", systemImage: "message.badge.fill") } }; if !store.orders.isEmpty { Button { store.selectedTab = .services; dismiss() } label: { Label("\(store.orders.count) commande(s) à suivre", systemImage: "shippingbox.fill") } }; ForEach(store.liveRooms.filter(\.live)) { room in Button { store.selectedTab = .live; dismiss() } label: { Label("En direct : \(room.title)", systemImage: "dot.radiowaves.left.and.right") } }; if store.unreadCount == 0 && store.orders.isEmpty && store.liveRooms.filter(\.live).isEmpty { ContentUnavailableView("Tout est à jour", systemImage: "checkmark.circle", description: Text("Les nouvelles activités apparaîtront ici.")) } }.navigationTitle("Activité").toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fermer") { dismiss() } } } } }
}

private struct InitialsAvatar: View {
    let text: String; var size: CGFloat = 48
    var body: some View { ZStack { Circle().fill(Color.whappyBlue.opacity(0.14)); Text(text).font(.system(size: size * 0.32, weight: .bold)).foregroundStyle(Color.whappyBlue) }.frame(width: size, height: size) }
}
