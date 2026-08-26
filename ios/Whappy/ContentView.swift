import AVFoundation
import AVKit
import AudioToolbox
import CoreImage.CIFilterBuiltins
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions
import PhotosUI
import SceneKit
import Speech
import SwiftUI
import UniformTypeIdentifiers
import UIKit

struct ContentView: View {
    @EnvironmentObject private var store: WhappyStore

    private func ui(_ french: String, _ english: String, _ lingala: String) -> String {
        store.interfaceLanguage.text(french, english, lingala)
    }

    var body: some View {
        TabView(selection: $store.selectedTab) {
            NavigationStack { MessagesView() }
                .tabItem { Label(ui("Messages", "Messages", "Nsango"), systemImage: "message.fill") }.badge(store.unreadCount).tag(WhappyTab.messages)
            NavigationStack { CallsView() }
                .tabItem { Label(ui("Appels", "Calls", "Mabéle"), systemImage: "phone.fill") }.tag(WhappyTab.calls)
            NavigationStack { UpdatesView() }
                .tabItem { Label(ui("Actus", "Updates", "Sango"), systemImage: "sparkles") }.tag(WhappyTab.actus)
            NavigationStack { WiaAssistantView() }
                .tabItem { Label("WIA", systemImage: "wand.and.stars") }.tag(WhappyTab.wia)
            NavigationStack { HomeView() }
                .tabItem { Label(ui("Accueil", "Home", "Ndako"), systemImage: "house.fill") }.tag(WhappyTab.home)
            NavigationStack { MarketView() }
                .tabItem { Label(ui("Marché", "Market", "Zando"), systemImage: "storefront.fill") }.badge(store.cartCount).tag(WhappyTab.market)
            NavigationStack { LiveView() }
                .tabItem { Label("Live", systemImage: "video.fill") }.tag(WhappyTab.live)
            NavigationStack { GamesView() }
                .tabItem { Label(ui("Jeux", "Games", "Masano"), systemImage: "bolt.fill") }.tag(WhappyTab.games)
            NavigationStack { ServicesView() }
                .tabItem { Label(ui("Services", "Services", "Misala"), systemImage: "wallet.pass.fill") }.tag(WhappyTab.services)
            NavigationStack { ProfileView() }
                .tabItem { Label(ui("Profil", "Profile", "Profil"), systemImage: "person.crop.circle.fill") }.tag(WhappyTab.profile)
        }
        .tint(.whappyBlue)
        .onChange(of: store.selectedTab) { _, tab in
            if tab == .actus { store.refreshStories() }
        }
        .fullScreenCover(item: $store.pendingGroupCall) { route in
            WapiGroupCallRoom(route: route) { store.pendingGroupCall = nil }
        }
        .fullScreenCover(item: $store.pendingDirectCall) { route in
            WapiDirectCallRoom(route: route) { store.pendingDirectCall = nil }
        }
    }
}

private struct BrandHeader: View {
    let subtitle: String
    @State private var showActivity = false

    var body: some View {
        VStack(spacing: 0) {
            LinearGradient(colors: [WapiColor.sky, WapiColor.blue, WapiColor.deepBlue], startPoint: .leading, endPoint: .trailing)
                .frame(height: 3)
            HStack(spacing: 12) {
                Image("WhappyMark").resizable().scaledToFit().frame(width: 38, height: 38).clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 7) {
                        Text("WAPI").font(.title2.bold()).foregroundStyle(Color.whappyInk)
                        Text("PRIVÉ").font(.system(size: 8, weight: .black)).tracking(0.6).foregroundStyle(Color.whappyBlue).padding(.horizontal, 7).padding(.vertical, 4).background(WapiColor.blueMist).clipShape(Capsule())
                    }
                    Text(subtitle).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Button { showActivity = true } label: { Image(systemName: "bell.fill") }
                    .buttonStyle(.bordered).clipShape(Circle())
            }
            .padding(.horizontal, WapiSpacing.screen)
            .padding(.vertical, 10)
        }
        .background(WapiColor.canvas)
        .overlay(alignment: .bottom) { Rectangle().fill(WapiColor.line).frame(height: 1) }
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
                    LinearGradient(colors: [.whappyBlue, .whappyBlue], startPoint: .topLeading, endPoint: .bottomTrailing)
                    Circle().fill(.white.opacity(0.12)).frame(width: 170).offset(x: 220, y: -50)
                    VStack(alignment: .leading, spacing: 9) {
                        Label("WAPI PULSE", systemImage: "waveform.path.ecg").font(.caption.bold()).foregroundStyle(.white.opacity(0.85))
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
                    ActionCard(title: "Jeux", subtitle: "Défis et duels", icon: "bolt.fill", color: .yellow) { store.selectedTab = .games }
                }
                HStack { Text("Moments").font(.title3.bold()).foregroundStyle(Color.whappyInk); Spacer(); Button { composingMoment = true } label: { Label("Publier", systemImage: "plus.circle.fill") } }
                ForEach(store.moments) { moment in
                    VStack(alignment: .leading, spacing: 8) { HStack { InitialsAvatar(text: "CB", size: 38); VStack(alignment: .leading) { Text("Vous").font(.headline); Text(moment.createdAt, style: .relative).font(.caption).foregroundStyle(.secondary) } }; Text(moment.title).font(.title3.bold()).foregroundStyle(Color.whappyInk); Text(moment.text).foregroundStyle(.secondary) }.frame(maxWidth: .infinity, alignment: .leading).padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 18))
                }
                Text("Tendances près de vous").font(.title3.bold()).foregroundStyle(Color.whappyInk)
                ForEach(store.listings.prefix(2)) { listing in
                    NavigationLink(value: listing) { ListingRow(listing: listing) }.buttonStyle(.plain)
                }
                Text("Vos activités WAPI").font(.title3.bold()).foregroundStyle(Color.whappyInk)
                VStack(spacing: 9) {
                    ActionCard(title: "Radio & podcasts", subtitle: "Émissions, chaînes et écoute continue", icon: "dot.radiowaves.left.and.right", color: .whappyBlue) { store.selectedTab = .actus }
                    ActionCard(title: "Jumeau numérique", subtitle: "Votre identité, vos consentements et vos créations", icon: "sparkles", color: .purple) { store.selectedTab = .profile }
                    ActionCard(title: "Lives en cours", subtitle: store.liveRooms.filter(\.live).isEmpty ? "Soyez le premier à démarrer" : "\(store.liveRooms.filter(\.live).count) direct(s) à rejoindre", icon: "video.fill", color: .red) { store.selectedTab = .live }
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

private struct UpdatesView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var storyComposerPresented = false
    @State private var selectedStory: WapiStory?

    private var activeLives: [LiveRoom] { store.liveRooms.filter(\.live) }
    private var followedChannels: [WhappyChannel] {
        store.channels.sorted { ($0.subscribed ? 1 : 0, $0.memberCount) > ($1.subscribed ? 1 : 0, $1.memberCount) }
    }
    private var storyGroups: [[WapiStory]] {
        Dictionary(grouping: store.stories, by: \.authorID)
            .values
            .map { $0.sorted { $0.createdAt < $1.createdAt } }
            .sorted { left, right in
                let leftOwn = left.first?.authorID == store.firebaseUserID
                let rightOwn = right.first?.authorID == store.firebaseUserID
                if leftOwn != rightOwn { return leftOwn }
                return (left.last?.createdAt ?? .distantPast) > (right.last?.createdAt ?? .distantPast)
            }
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Actus")
                            .font(.system(size: 30, weight: .black, design: .rounded))
                            .foregroundStyle(Color.whappyInk)
                        Text("Directs, chaînes et découvertes WAPI")
                            .font(.caption)
                            .foregroundStyle(WapiColor.secondaryText)
                    }
                    Spacer()
                    Button { store.selectedTab = .live } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                            .background(Color.whappyBlue)
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Créer un direct")
                }

                updatesSectionTitle("Stories", subtitle: "Photos, vidéos et voix · visibles pendant 24 h")
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 14) {
                        let ownStories = storyGroups.first(where: { $0.first?.authorID == store.firebaseUserID }) ?? []
                        Button {
                            if let latest = ownStories.last { selectedStory = latest }
                            else { storyComposerPresented = true }
                        } label: {
                            WapiStoryCircle(story: ownStories.last, title: "Ma Story", isOwn: true, hasUnseen: false, showAdd: ownStories.isEmpty)
                        }
                        .buttonStyle(.plain)
                        ForEach(Array(storyGroups.filter { $0.first?.authorID != store.firebaseUserID }.enumerated()), id: \.offset) { _, group in
                            if let latest = group.last {
                                Button { selectedStory = latest } label: {
                                    WapiStoryCircle(story: latest, title: latest.authorName, isOwn: false, hasUnseen: group.contains(where: { !$0.viewed }), showAdd: false)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }

                updatesSectionTitle("En direct", subtitle: activeLives.isEmpty ? "Aucun direct pour le moment" : "\(activeLives.count) diffusion\(activeLives.count > 1 ? "s" : "") maintenant")
                if activeLives.isEmpty {
                    Button { store.selectedTab = .live } label: {
                        updatesRow(icon: "video.fill", title: "Démarrer un direct", subtitle: "Entrez en direct en un geste", accent: .red)
                    }.buttonStyle(.plain)
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(activeLives) { room in
                                Button { store.selectedTab = .live } label: {
                                    VStack(alignment: .leading, spacing: 8) {
                                        HStack {
                                            Text("● EN DIRECT").font(.caption2.weight(.black)).foregroundStyle(.red)
                                            Spacer()
                                            Text("\(room.viewers)").font(.caption2.weight(.bold)).foregroundStyle(WapiColor.secondaryText)
                                        }
                                        Image(systemName: room.icon).font(.title2).foregroundStyle(Color.whappyBlue)
                                        Text(room.title).font(.headline).foregroundStyle(Color.whappyInk).lineLimit(2)
                                        Text("\(room.host) · \(room.category)").font(.caption2).foregroundStyle(WapiColor.secondaryText).lineLimit(1)
                                    }
                                    .padding(14)
                                    .frame(width: 214, height: 142, alignment: .leading)
                                    .wapiPanel(radius: 18)
                                }.buttonStyle(.plain)
                            }
                        }.padding(.vertical, 4)
                    }
                }

                updatesSectionTitle("Chaînes", subtitle: "Les publications qui comptent")
                VStack(spacing: 2) {
                    ForEach(followedChannels.prefix(4)) { channel in
                        NavigationLink(value: channel) {
                            updatesRow(
                                icon: channel.subscribed ? "dot.radiowaves.left.and.right" : "megaphone.fill",
                                title: channel.name,
                                subtitle: channel.posts.last?.text ?? channel.description,
                                accent: .whappyBlue
                            )
                        }.buttonStyle(.plain)
                    }
                    if followedChannels.isEmpty {
                        Button { store.selectedTab = .messages; store.pendingSearch = "" } label: {
                            updatesRow(icon: "dot.radiowaves.left.and.right", title: "Découvrir les chaînes", subtitle: "Suivez uniquement ce qui vous intéresse", accent: .whappyBlue)
                        }.buttonStyle(.plain)
                    }
                }
                .padding(5)
                .wapiPanel()

                updatesSectionTitle("Explorer", subtitle: "Ouvrez directement un espace WAPI")
                VStack(spacing: 2) {
                    Button { store.selectedTab = .live } label: { updatesRow(icon: "video.fill", title: "Lives", subtitle: "Voir et créer des directs", accent: .red) }.buttonStyle(.plain)
                    Button { store.selectedTab = .games } label: { updatesRow(icon: "gamecontroller.fill", title: "Jeux", subtitle: "Parties, défis et tournois", accent: .indigo) }.buttonStyle(.plain)
                    Button { store.selectedTab = .market } label: { updatesRow(icon: "storefront.fill", title: "Près de vous", subtitle: "Boutiques et offres locales", accent: .green) }.buttonStyle(.plain)
                }
                .padding(5)
                .wapiPanel()
            }
            .padding(.horizontal, WapiSpacing.screen)
            .padding(.top, 12)
            .padding(.bottom, 28)
        }
        .background(Color.whappyBackground.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: WhappyChannel.self) { channel in ChannelView(channelID: channel.id) }
        .sheet(isPresented: $storyComposerPresented) { WapiStoryComposer() }
        .fullScreenCover(item: $selectedStory) { story in WapiStoryViewer(story: story) }
        .onAppear { store.refreshStories() }
    }

    private func updatesSectionTitle(_ title: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.title3.weight(.bold)).foregroundStyle(Color.whappyInk)
            Text(subtitle).font(.caption2).foregroundStyle(WapiColor.secondaryText)
        }.padding(.top, 4)
    }

    private func updatesRow(icon: String, title: String, subtitle: String, accent: Color) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(accent)
                .frame(width: 43, height: 43)
                .background(accent.opacity(0.11))
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk).lineLimit(1)
                Text(subtitle).font(.caption).foregroundStyle(WapiColor.secondaryText).lineLimit(1)
            }
            Spacer()
            Image(systemName: "chevron.right").font(.caption.weight(.bold)).foregroundStyle(accent.opacity(0.75))
        }
        .padding(.horizontal, 9)
        .padding(.vertical, 8)
        .contentShape(Rectangle())
    }
}

private struct WapiStoryCircle: View {
    let story: WapiStory?
    let title: String
    let isOwn: Bool
    let hasUnseen: Bool
    let showAdd: Bool

    var body: some View {
        VStack(spacing: 6) {
            ZStack(alignment: .bottomTrailing) {
                Circle()
                    .strokeBorder(
                        AngularGradient(
                            colors: hasUnseen ? [Color.whappyBlue, Color(red: 0.00, green: 0.72, blue: 0.96), Color(red: 0.04, green: 0.25, blue: 0.80), Color.whappyBlue] : [Color(.systemGray4), Color(.systemGray4)],
                            center: .center
                        ),
                        lineWidth: hasUnseen ? 4 : 1.5
                    )
                    .frame(width: 74, height: 74)
                    .shadow(color: hasUnseen ? Color.whappyBlue.opacity(0.24) : .clear, radius: 7, y: 3)
                Group {
                    if let url = story.flatMap({ URL(string: $0.authorPhotoURL) }), !url.absoluteString.isEmpty {
                        WapiCachedRemoteImage(url: url) { InitialsAvatar(text: String(title.prefix(2)).uppercased(), size: 62) }
                    } else {
                        InitialsAvatar(text: isOwn ? "MOI" : String(title.prefix(2)).uppercased(), size: 62)
                    }
                }
                .frame(width: 64, height: 64)
                .clipShape(Circle())
                if showAdd {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 23, weight: .bold))
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(.white, Color.whappyBlue)
                        .background(Circle().fill(.white))
                        .offset(x: 3, y: 3)
                }
            }
            Text(title)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(Color.whappyInk)
                .lineLimit(1)
                .frame(width: 76)
        }
    }
}

private struct WapiStoryComposer: View {
    @EnvironmentObject private var store: WhappyStore
    @Environment(\.dismiss) private var dismiss
    @State private var photoItem: PhotosPickerItem?
    @State private var audioImporterPresented = false
    @State private var mediaData: Data?
    @State private var mediaType = "text"
    @State private var contentType = ""
    @State private var caption = ""
    @State private var isPublishing = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("Nouvelle Story")
                        .font(.system(size: 28, weight: .black, design: .rounded))
                        .foregroundStyle(Color.whappyInk)
                    Text("Partagez un moment avec vos contacts. Il disparaîtra automatiquement après 24 heures.")
                        .font(.subheadline)
                        .foregroundStyle(WapiColor.secondaryText)
                    HStack(spacing: 10) {
                        PhotosPicker(selection: $photoItem, matching: .any(of: [.images, .videos]), photoLibrary: .shared()) {
                            Label(mediaData == nil ? "Galerie" : "Remplacer", systemImage: "photo.on.rectangle.angled")
                                .font(.headline)
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 15)
                                .background(Color.whappyBlue)
                                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        Button { audioImporterPresented = true } label: {
                            Label("Audio", systemImage: "waveform")
                                .font(.headline)
                                .foregroundStyle(Color.whappyBlue)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 15)
                                .background(Color.whappyBlue.opacity(0.10))
                                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                    if let mediaData {
                        storyMediaPreview(data: mediaData)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Texte de la Story").font(.headline).foregroundStyle(Color.whappyInk)
                        TextEditor(text: $caption)
                            .frame(minHeight: 110)
                            .padding(8)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .overlay(alignment: .topLeading) {
                                if caption.isEmpty { Text("Écrivez un message…").foregroundStyle(.secondary).padding(.top, 16).padding(.leading, 13).allowsHitTesting(false) }
                            }
                    }
                    if let errorMessage {
                        Label(errorMessage, systemImage: "exclamationmark.triangle.fill")
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                    Button {
                        Task { await publish() }
                    } label: {
                        HStack {
                            if isPublishing { ProgressView().tint(.white) }
                            Text(isPublishing ? "Publication…" : "Publier la Story")
                        }
                        .font(.headline)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 15)
                        .background((caption.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && mediaData == nil) ? Color.gray : Color.whappyBlue)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    .disabled(isPublishing || (caption.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && mediaData == nil))
                }
                .padding(20)
            }
            .background(Color.whappyBackground.ignoresSafeArea())
            .navigationTitle("Stories")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } } }
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task { await load(item) }
        }
        .fileImporter(isPresented: $audioImporterPresented, allowedContentTypes: [.audio]) { result in
            importAudio(result)
        }
    }

    @ViewBuilder
    private func storyMediaPreview(data: Data) -> some View {
        if mediaType == "image", let image = UIImage(data: data) {
            Image(uiImage: image).resizable().scaledToFill().frame(maxWidth: .infinity).frame(height: 220).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        } else {
            Label(mediaType == "video" ? "Vidéo prête à publier" : "Audio prêt à publier", systemImage: mediaType == "video" ? "video.fill" : "waveform")
                .frame(maxWidth: .infinity).padding(34).background(Color.whappyBlue.opacity(0.1)).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
    }

    private func load(_ item: PhotosPickerItem) async {
        guard let data = try? await item.loadTransferable(type: Data.self) else { return }
        let types = item.supportedContentTypes
        mediaType = types.contains(where: { $0.conforms(to: .movie) || $0.conforms(to: .video) }) ? "video" : types.contains(where: { $0.conforms(to: .audio) }) ? "audio" : "image"
        contentType = types.first?.preferredMIMEType ?? ""
        mediaData = data
    }

    private func importAudio(_ result: Result<URL, Error>) {
        guard case .success(let source) = result else { return }
        let accessing = source.startAccessingSecurityScopedResource()
        defer { if accessing { source.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: source) else {
            errorMessage = "WAPI n’a pas pu lire ce fichier audio."
            return
        }
        mediaData = data
        mediaType = "audio"
        contentType = UTType(filenameExtension: source.pathExtension)?.preferredMIMEType ?? "audio/m4a"
    }

    private func publish() async {
        isPublishing = true
        errorMessage = nil
        do {
            try await store.publishStory(caption: caption, mediaData: mediaData, mediaType: mediaType, contentType: contentType)
            WapiSounds.storyPublished()
            WapiSounds.haptic(.medium)
            dismiss()
        } catch {
            errorMessage = wapiUserFacingError(error, action: "La publication de votre story")
        }
        isPublishing = false
    }
}

private struct WapiStoryViewer: View {
    @EnvironmentObject private var store: WhappyStore
    @Environment(\.dismiss) private var dismiss
    let story: WapiStory
    @State private var player: AVPlayer?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if story.mediaType == "image", let url = URL(string: story.mediaURL), !story.mediaURL.isEmpty {
                AsyncImage(url: url) { phase in
                    if let image = phase.image { image.resizable().scaledToFit() }
                    else if phase.error != nil { storyText }
                    else { ProgressView().tint(.white) }
                }
            } else if story.mediaType == "video" || story.mediaType == "audio", let player {
                VideoPlayer(player: player).ignoresSafeArea(edges: .bottom)
            } else {
                storyText
            }
            VStack {
                HStack(spacing: 10) {
                    if let url = URL(string: story.authorPhotoURL), !story.authorPhotoURL.isEmpty {
                        WapiCachedRemoteImage(url: url) { InitialsAvatar(text: String(story.authorName.prefix(2)), size: 38) }.frame(width: 38, height: 38).clipShape(Circle())
                    } else { InitialsAvatar(text: String(story.authorName.prefix(2)), size: 38) }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(story.authorName).font(.headline)
                        Text(story.createdAt, style: .relative).font(.caption).foregroundStyle(.white.opacity(0.75))
                    }
                    Spacer()
                    Button { dismiss() } label: { Image(systemName: "xmark").font(.headline).foregroundStyle(.white).padding(10).background(.black.opacity(0.35)).clipShape(Circle()) }.buttonStyle(.plain)
                }
                .padding(.horizontal, 18).padding(.top, 18)
                Spacer()
                if !story.caption.isEmpty {
                    Text(story.caption).font(.title3.weight(.semibold)).foregroundStyle(.white).frame(maxWidth: .infinity, alignment: .leading).padding(18).background(.black.opacity(0.45))
                }
            }
        }
        .task {
            store.markStoryViewed(story)
            if (story.mediaType == "video" || story.mediaType == "audio"), let url = URL(string: story.mediaURL) {
                let value = AVPlayer(url: url)
                player = value
                value.play()
            }
        }
        .onDisappear { player?.pause() }
    }

    private var storyText: some View {
        LinearGradient(colors: [Color.whappyBlue, WapiColor.deepBlue], startPoint: .topLeading, endPoint: .bottomTrailing)
            .ignoresSafeArea()
            .overlay(Text(story.caption.isEmpty ? "Story WAPI" : story.caption).font(.system(size: 28, weight: .bold, design: .rounded)).foregroundStyle(.white).multilineTextAlignment(.center).padding(32))
    }
}

struct MessagesView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var composing = false
    @State private var creatingChannel = false
    @State private var search = ""
    @State private var section = 0
    @State private var linkedPhone = "+242"
    @State private var linkedChannel: WhappyChannel?
    private var filtered: [Conversation] { store.accountConversations.filter { "\($0.name) \($0.lastMessage) \($0.phoneNumber)".matchesWhappySearch(search) } }
    private var filteredChannels: [WhappyChannel] { store.channels.filter { "\($0.name) \($0.description) \($0.category) \($0.ownerName)".matchesWhappySearch(search) }.sorted { ($0.subscribed ? 1 : 0, $0.memberCount) > ($1.subscribed ? 1 : 0, $1.memberCount) } }

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(section == 0 ? (store.activeBusinessMode ? "Messages Business" : "Messages") : "Chaînes")
                        .font(.system(size: 30, weight: .black))
                        .foregroundStyle(Color.whappyInk)
                    Text(section == 0 ? (store.activeBusinessMode ? "Clients, commandes et équipe · identité séparée" : "Vos échanges, instantanément") : "Les publications que vous choisissez")
                        .font(.caption)
                        .foregroundStyle(WapiColor.secondaryText)
                }
                Spacer()
                Button {
                    if section == 0 { composing = true } else { creatingChannel = true }
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(Color.whappyBlue)
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(section == 0 ? "Nouvelle discussion" : "Nouvelle chaîne")
            }
            .padding(.horizontal, WapiSpacing.screen)
            .padding(.top, 12)
            .padding(.bottom, 14)

            if section == 0, store.activeBusinessMode {
                NavigationLink { BusinessWorkspaceView() } label: {
                    HStack(spacing: 11) {
                        if let business = store.business, let url = URL(string: business.logoURL), !business.logoURL.isEmpty {
                            WapiCachedRemoteImage(url: url) { InitialsAvatar(text: business.name, size: 42) }
                            .frame(width: 42, height: 42)
                            .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                        } else {
                            InitialsAvatar(text: store.business?.name ?? "Business", size: 42)
                                .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 6) {
                                Text(store.business?.name ?? "WAPI Business").font(.subheadline.weight(.bold)).lineLimit(1)
                                Text("BUSINESS").font(.system(size: 8, weight: .black)).foregroundStyle(WapiColor.sky)
                            }
                            Text("Vous répondez au nom de l’entreprise").font(.caption2).foregroundStyle(.white.opacity(0.68))
                        }
                        Spacer()
                        Image(systemName: "briefcase.fill").foregroundStyle(WapiColor.sky)
                        Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(.white.opacity(0.55))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .frame(height: 66)
                    .background(LinearGradient(colors: [Color.whappyInk, WapiColor.deepBlue], startPoint: .leading, endPoint: .trailing))
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, WapiSpacing.screen)
                .padding(.bottom, 10)
            }

            HStack(spacing: 4) {
                messageSectionButton("Discussions", value: 0)
                messageSectionButton("Chaînes", value: 1)
            }
            .padding(4)
            .background(WapiColor.secondarySurface)
            .clipShape(RoundedRectangle(cornerRadius: WapiRadius.control, style: .continuous))
            .padding(.horizontal, WapiSpacing.screen)

            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(WapiColor.secondaryText)
                TextField(section == 0 ? "Rechercher dans les messages" : "Rechercher une chaîne", text: $search)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                if !search.isEmpty {
                    Button { search = "" } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(WapiColor.secondaryText)
                    }.buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 14)
            .frame(height: 50)
            .background(.white)
            .clipShape(RoundedRectangle(cornerRadius: WapiRadius.control, style: .continuous))
            .padding(.horizontal, WapiSpacing.screen)
            .padding(.vertical, 10)

            if section == 0 {
                if filtered.isEmpty {
                    ContentUnavailableView(
                        search.isEmpty ? "Aucune conversation" : "Aucun résultat",
                        systemImage: "message",
                        description: Text(search.isEmpty ? "Ajoutez un contact pour commencer à discuter sur WAPI." : "Essayez un autre nom ou contenu récent.")
                    )
                    .frame(maxHeight: .infinity)
                } else {
                    ScrollView {
                        LazyVStack(spacing: 6) {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Conversations").font(.headline).foregroundStyle(Color.whappyInk)
                                    Text("\(filtered.count) échange\(filtered.count > 1 ? "s" : "")")
                                        .font(.caption2).foregroundStyle(WapiColor.secondaryText)
                                }
                                Spacer()
                                Text("WAPI PRIVÉ")
                                    .font(.caption2.weight(.black))
                                    .foregroundStyle(Color.whappyBlue)
                            }
                            .padding(.horizontal, 3)
                            .padding(.vertical, 5)

                            ForEach(filtered) { conversation in
                                NavigationLink(value: conversation) {
                                    WapiConversationRow(conversation: conversation)
                                }
                                .buttonStyle(.plain)
                                .contextMenu {
                                    Button { store.toggleUnread(conversation) } label: {
                                        Label(conversation.unread ? "Marquer comme lu" : "Marquer non lu", systemImage: conversation.unread ? "envelope.open" : "envelope.badge")
                                    }
                                    Button(role: .destructive) { store.deleteConversation(conversation) } label: {
                                        Label("Supprimer", systemImage: "trash")
                                    }
                                }
                            }
                            WapiMiniAppsShelfIOS()
                                .padding(.top, 14)
                        }
                        .padding(.horizontal, WapiSpacing.screen)
                        .padding(.bottom, 24)
                    }
                    .scrollIndicators(.hidden)
                }
            } else {
                ChannelDirectoryView(channels: filteredChannels)
            }
        }
        .background(Color.whappyBackground.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: Conversation.self) { conversation in ConversationView(conversationID: conversation.id).onAppear { store.markRead(conversation) } }
        .navigationDestination(for: WhappyChannel.self) { channel in ChannelView(channelID: channel.id) }
        .sheet(isPresented: $composing) { NewConversationView(initialPhone: linkedPhone) }
        .sheet(isPresented: $creatingChannel) { NewChannelView() }
        .sheet(item: $linkedChannel) { channel in NavigationStack { ChannelView(channelID: channel.id) } }
        .onAppear { consumePendingLinks() }
        .onChange(of: store.pendingContactPhone) { _, _ in consumePendingLinks() }
        .onChange(of: store.pendingChannelID) { _, _ in consumePendingLinks() }
        .onChange(of: store.pendingSearch) { _, _ in consumePendingLinks() }
    }

    @ViewBuilder
    private func messageSectionButton(_ title: String, value: Int) -> some View {
        Button {
            withAnimation(.easeOut(duration: 0.18)) { section = value; search = "" }
        } label: {
            Text(title)
                .font(.footnote.weight(section == value ? .bold : .semibold))
                .foregroundStyle(section == value ? Color.whappyInk : WapiColor.secondaryText)
                .frame(maxWidth: .infinity)
                .frame(height: 38)
                .background(section == value ? Color.white : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func consumePendingLinks() {
        if let phone = store.pendingContactPhone { linkedPhone = phone; composing = true; section = 0; store.pendingContactPhone = nil }
        if let id = store.pendingChannelID, let channel = store.channels.first(where: { $0.id == id }) { linkedChannel = channel; section = 1; store.pendingChannelID = nil }
        if let query = store.pendingSearch { search = query; section = 1; store.pendingSearch = nil }
    }
}

/// A deliberate pull to the end of the inbox reveals the active WAPI spaces.
/// This keeps Messages calm while providing the integrated-app discovery flow
/// users expect from a super-app.
private struct WapiMiniAppsShelfIOS: View {
    @EnvironmentObject private var store: WhappyStore
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 3)

    private struct Item: Identifiable {
        let id: String
        let title: String
        let icon: String
        let tab: WhappyTab
    }

    private let items = [
        Item(id: "live", title: "Direct", icon: "video.fill", tab: .live),
        Item(id: "games", title: "Jeux", icon: "bolt.fill", tab: .games),
        Item(id: "market", title: "Marché", icon: "storefront.fill", tab: .market),
        Item(id: "stories", title: "Créations", icon: "sparkles", tab: .actus),
        Item(id: "services", title: "Services", icon: "wallet.pass.fill", tab: .services),
        Item(id: "profile", title: "Mon WAPI", icon: "person.crop.circle", tab: .profile),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 13) {
            HStack(spacing: 9) {
                Image(systemName: "square.grid.2x2.fill")
                    .foregroundStyle(Color.whappyBlue)
                    .frame(width: 30, height: 30)
                    .background(Color.whappyBlue.opacity(0.10))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text("Continuer dans WAPI").font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk)
                    Text("Activités, créations et services").font(.caption2).foregroundStyle(WapiColor.secondaryText)
                }
            }
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(items) { item in
                    Button { store.selectedTab = item.tab } label: {
                        VStack(spacing: 7) {
                            Image(systemName: item.icon).font(.system(size: 19, weight: .semibold)).foregroundStyle(Color.whappyBlue)
                            Text(item.title).font(.caption2.weight(.semibold)).foregroundStyle(Color.whappyInk).lineLimit(1)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(WapiColor.secondarySurface)
                        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(15)
        .background(.white)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

private struct WapiConversationRow: View {
    let conversation: Conversation

    private var lastDate: Date? { conversation.messages.last?.sentAt }

    private var moment: String {
        guard let lastDate else { return "" }
        if Calendar.current.isDateInToday(lastDate) {
            return lastDate.formatted(date: .omitted, time: .shortened)
        }
        if Calendar.current.isDateInYesterday(lastDate) { return "Hier" }
        return lastDate.formatted(.dateTime.day().month(.twoDigits))
    }

    var body: some View {
        HStack(spacing: 12) {
            ConversationAvatar(conversation: conversation)
                .overlay(alignment: .bottomTrailing) {
                    if conversation.peerIsOnline == true {
                        Circle().fill(Color.green).frame(width: 12, height: 12).overlay(Circle().stroke(.white, lineWidth: 2))
                    }
                }
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(conversation.name)
                        .font(.headline.weight(conversation.unread ? .bold : .semibold))
                        .foregroundStyle(Color.whappyInk)
                        .lineLimit(1)
                    Spacer()
                    Text(moment)
                        .font(.caption2.weight(conversation.unread ? .bold : .regular))
                        .foregroundStyle(conversation.unread ? Color.whappyBlue : WapiColor.secondaryText)
                }
                HStack(spacing: 8) {
                    Text(conversation.lastMessage)
                        .font(.subheadline.weight(conversation.unread ? .semibold : .regular))
                        .foregroundStyle(conversation.unread ? Color.whappyInk : WapiColor.secondaryText)
                        .lineLimit(1)
                    Spacer()
                    if conversation.unread {
                        Text("N")
                            .font(.system(size: 9, weight: .black))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 4)
                            .background(Color.whappyBlue)
                            .clipShape(Capsule())
                    }
                }
            }
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 12)
        .background(conversation.unread ? WapiColor.unreadSurface : Color.white)
        .overlay(alignment: .bottom) {
            Rectangle().fill(WapiColor.line.opacity(0.72)).frame(height: 1).padding(.leading, 66)
        }
    }
}

private struct ChannelDirectoryView: View {
    @EnvironmentObject private var store: WhappyStore
    let channels: [WhappyChannel]

    var body: some View {
        if channels.isEmpty { ContentUnavailableView("Aucune chaîne", systemImage: "dot.radiowaves.left.and.right", description: Text("Créez la première chaîne WAPI.")) }
        else {
            List {
                Section {
                    HStack(spacing: 13) { Image(systemName: "dot.radiowaves.left.and.right").font(.title2.bold()).foregroundStyle(.white).frame(width: 48, height: 48).background(Color.whappyBlue).clipShape(RoundedRectangle(cornerRadius: 15)); VStack(alignment: .leading) { Text("CHAÎNES WAPI").font(.caption2.bold()).foregroundStyle(Color.whappyBlue); Text("Des publications utiles, sans bruit").font(.headline).foregroundStyle(.white); Text("\(store.channels.count) chaînes à découvrir").font(.caption).foregroundStyle(.white.opacity(0.65)) } }.padding(.vertical, 7).listRowBackground(Color.whappyInk)
                }
                Section("Découvrir") {
                    ForEach(channels) { channel in
                        NavigationLink(value: channel) {
                            HStack(spacing: 12) {
                                Image(systemName: channel.subscribed ? "dot.radiowaves.left.and.right" : "megaphone.fill").foregroundStyle(channel.subscribed ? .white : Color.whappyBlue).frame(width: 48, height: 48).background(channel.subscribed ? Color.whappyBlue : Color.whappyBlue.opacity(0.12)).clipShape(RoundedRectangle(cornerRadius: 15))
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack(spacing: 4) { Text(channel.name).font(.headline); if channel.verified { Image(systemName: "checkmark.seal.fill").foregroundStyle(Color.whappyBlue).font(.caption) } }
                                    Text("\(channel.category) · \(channel.memberCount.formatted(.number.notation(.compactName))) abonnés").font(.caption).foregroundStyle(.secondary)
                                    Text(channel.posts.last?.text ?? channel.description).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                }
                            }.padding(.vertical, 5)
                        }
                        .swipeActions(edge: .leading) { if !channel.owner { Button { store.toggleChannelSubscription(channel) } label: { Label(channel.subscribed ? "Quitter" : "Suivre", systemImage: channel.subscribed ? "bell.slash" : "bell.badge") }.tint(channel.subscribed ? .gray : .whappyBlue) } }
                    }
                }
            }.listStyle(.insetGrouped)
        }
    }
}

private struct NewChannelView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WhappyStore
    @State private var name = ""
    @State private var description = ""
    @State private var category = "Communauté"
    private let categories = ["Communauté", "Actualités", "Créateurs", "Shopping", "Sport", "Tech"]

    var body: some View {
        NavigationStack {
            Form {
                Section("Identité") { TextField("Nom de la chaîne", text: $name); TextField("Description", text: $description, axis: .vertical).lineLimit(3...6); Text("\(description.count)/300").font(.caption).foregroundStyle(.secondary) }
                Section("Catégorie") { Picker("Catégorie", selection: $category) { ForEach(categories, id: \.self) { Text($0) } } }
                Section { Label("Vous seul pourrez publier. Les abonnés pourront suivre et réagir.", systemImage: "shield.checkered").font(.footnote).foregroundStyle(.secondary) }
            }
            .navigationTitle("Nouvelle chaîne").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Créer") { store.createChannel(name: name, description: description, category: category); dismiss() }.disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).count < 3 || description.trimmingCharacters(in: .whitespacesAndNewlines).count < 10 || description.count > 300) } }
        }
    }
}

private struct ChannelView: View {
    @EnvironmentObject private var store: WhappyStore
    let channelID: UUID
    @State private var draft = ""
    @State private var search = ""
    private var channel: WhappyChannel? { store.channels.first { $0.id == channelID } }
    private var posts: [WhappyChannelPost] {
        let source = channel?.posts.filter { $0.text.matchesWhappySearch(search) } ?? []
        return source.sorted { ($0.pinned ? 1 : 0, $0.createdAt) > ($1.pinned ? 1 : 0, $1.createdAt) }
    }

    var body: some View {
        VStack(spacing: 0) {
            if let channel {
                VStack(alignment: .leading, spacing: 9) {
                    HStack { Image(systemName: "dot.radiowaves.left.and.right").font(.title2).foregroundStyle(Color.whappyBlue); VStack(alignment: .leading) { HStack { Text(channel.name).font(.title3.bold()); if channel.verified { Image(systemName: "checkmark.seal.fill").foregroundStyle(Color.whappyBlue) } }; Text("\(channel.memberCount.formatted(.number.notation(.compactName))) abonnés · \(channel.posts.count) publications").font(.caption).foregroundStyle(.secondary) }; Spacer(); ShareLink(item: "Découvrez la chaîne \(channel.name) sur WAPI\nwhappy://channel/\(channel.id.uuidString)\nhttps://whappy.chat/channel/\(channel.id.uuidString)") { Image(systemName: "square.and.arrow.up") } }
                    Text(channel.description).font(.subheadline)
                    HStack { Label("Par \(channel.ownerName)", systemImage: "person.crop.circle").font(.caption).foregroundStyle(.secondary); Spacer(); if !channel.owner { Button(channel.subscribed ? "Abonné ✓" : "S’abonner") { store.toggleChannelSubscription(channel) }.buttonStyle(.borderedProminent).controlSize(.small) } else { Label("Propriétaire", systemImage: "crown.fill").font(.caption.bold()).foregroundStyle(Color.whappyBlue) } }
                }.padding().background(Color(.secondarySystemBackground))
                if posts.isEmpty { ContentUnavailableView(search.isEmpty ? "Aucune publication" : "Aucun résultat", systemImage: "text.bubble", description: Text(channel.owner && search.isEmpty ? "Publiez la première actualité de votre chaîne." : "Les publications apparaîtront ici.")).frame(maxHeight: .infinity) }
                else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(posts) { post in
                                VStack(alignment: .leading, spacing: 9) {
                                    HStack { if post.pinned { Label("ÉPINGLÉ", systemImage: "pin.fill").font(.caption2.bold()).foregroundStyle(Color.whappyBlue) }; Spacer(); Text(post.createdAt, style: .relative).font(.caption2).foregroundStyle(.secondary) }
                                    Text(post.text).foregroundStyle(post.deleted ? Color.secondary : Color.whappyInk).italic(post.deleted)
                                    if !post.reactions.isEmpty { HStack { ForEach(Array(Dictionary(grouping: post.reactions.values, by: { $0 })).sorted(by: { $0.key < $1.key }), id: \.key) { group in Text(group.key + (group.value.count > 1 ? " \(group.value.count)" : "")).font(.caption).padding(.horizontal, 7).padding(.vertical, 4).background(Color(.tertiarySystemFill)).clipShape(Capsule()) } } }
                                    HStack { Text(post.authorName).font(.caption.bold()).foregroundStyle(Color.whappyBlue); Spacer(); Text(channel.subscribed ? "Maintenez pour réagir" : "Abonnez-vous pour réagir").font(.caption2).foregroundStyle(.secondary) }
                                }.padding().background(post.pinned ? Color.whappyBlue.opacity(0.08) : Color(.secondarySystemBackground)).clipShape(RoundedRectangle(cornerRadius: 18))
                                .contextMenu {
                                    if channel.subscribed && !post.deleted { Menu("Réagir") { ForEach(["❤️", "👍", "🔥", "👏", "💡"], id: \.self) { emoji in Button(emoji) { store.react(toChannelPost: post.id, channelID: channelID, emoji: emoji) } } }; Button { UIPasteboard.general.string = post.text } label: { Label("Copier", systemImage: "doc.on.doc") } }
                                    if channel.owner && !post.deleted { Button { store.togglePinnedChannelPost(post.id, channelID: channelID) } label: { Label(post.pinned ? "Désépingler" : "Épingler", systemImage: "pin") }; Button(role: .destructive) { store.deleteChannelPost(post.id, channelID: channelID) } label: { Label("Supprimer", systemImage: "trash") } }
                                }
                            }
                        }.padding()
                    }
                }
                if channel.owner {
                    HStack(alignment: .bottom) { TextField("Nouvelle publication…", text: $draft, axis: .vertical).textFieldStyle(.roundedBorder).lineLimit(1...6); Button { store.publish(draft, toChannel: channelID); draft = "" } label: { Image(systemName: "arrow.up.circle.fill").font(.system(size: 34)) }.disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || draft.count > 4_000) }.padding().background(.bar)
                } else if !channel.subscribed { Button { store.toggleChannelSubscription(channel) } label: { Label("S’abonner à cette chaîne", systemImage: "bell.badge.fill").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent).padding() }
            }
        }
        .navigationTitle(channel?.name ?? "Chaîne").navigationBarTitleDisplayMode(.inline)
        .searchable(text: $search, prompt: "Rechercher une publication")
    }
}

private struct NewConversationView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WhappyStore
    @State private var name = ""
    @State private var phone: String
    @State private var countryCode: String
    @State private var scanning = false
    @State private var scanError: String?

    init(initialPhone: String = "+242") {
        let parts = Self.phoneEditorParts(initialPhone, fallback: "+242")
        _phone = State(initialValue: parts.number)
        _countryCode = State(initialValue: parts.code)
    }

    private var normalizedPhone: String? { WhappyPhoneCountry.normalize(phone, selectedCode: countryCode) }

    var body: some View {
        NavigationStack {
            Form {
                Section("Contact") {
                    TextField("Nom", text: $name)
                    Picker("Pays", selection: $countryCode) { ForEach(WhappyPhoneCountry.supported) { country in Text("\(country.flag) \(country.name)  \(country.code)").tag(country.code) } }
                    TextField("Téléphone ou lien WAPI", text: $phone)
                        .keyboardType(.phonePad)
                        .onChange(of: phone) { _, value in
                            if case .contact(let scannedPhone) = WhappyDeepLink.parse(value) {
                                applyPhoneForEditing(scannedPhone)
                                return
                            }
                            let clean = String(value.filter(\.isNumber).prefix(15))
                            if clean != value { phone = clean }
                        }
                    if let normalizedPhone { Label(normalizedPhone, systemImage: "checkmark.circle.fill").font(.footnote.bold()).foregroundStyle(Color.whappyBlue) }
                    else if !phone.isEmpty { Label("Vérifiez l’indicatif et la longueur du numéro", systemImage: "exclamationmark.circle").font(.footnote).foregroundStyle(.orange) }
                    Button { scanning = true } label: { Label("Numériser un code WAPI", systemImage: "qrcode.viewfinder") }
                }
                Section { Text("Le contact reste enregistré sur cet appareil et la conversation peut être utilisée immédiatement.").font(.footnote).foregroundStyle(.secondary) }
            }
            .navigationTitle("Nouvelle discussion")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Créer") { if let normalizedPhone { store.createConversation(name: name, phone: normalizedPhone); dismiss() } }.disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).count < 2 || normalizedPhone == nil) }
            }
            .sheet(isPresented: $scanning) { WhappyScannerSheet { value in handleScannedValue(value) } }
            .alert("Code WAPI", isPresented: Binding(get: { scanError != nil }, set: { if !$0 { scanError = nil } })) { Button("Compris") { scanError = nil } } message: { Text(scanError ?? "") }
        }
    }

    private func handleScannedValue(_ value: String) {
        scanning = false
        if let directPhone = WhappyPhoneCountry.normalize(value) {
            applyPhoneForEditing(directPhone)
            return
        }
        guard let link = WhappyDeepLink.parse(value) else { scanError = "Ce QR n’est pas un code WAPI valide."; return }
        switch link {
        case .contact(let value): applyPhoneForEditing(value)
        case .channel(let id): store.selectedTab = .messages; store.pendingChannelID = id; dismiss()
        case .groupCall(let id): store.pendingGroupCall = WapiGroupCallRoute(callID: id, groupID: nil, groupName: "Appel de groupe", video: false); dismiss()
        case .directCall(let id): store.pendingDirectCall = WapiDirectCallRoute(callID: id, peerID: nil, peerName: "Appel WAPI", peerPhotoURL: "", video: false); dismiss()
        case .search(let query): store.selectedTab = .messages; store.pendingSearch = query; dismiss()
        }
    }

    private func applyPhoneForEditing(_ value: String) {
        let parts = Self.phoneEditorParts(value, fallback: countryCode)
        countryCode = parts.code
        phone = parts.number
    }

    private static func phoneEditorParts(_ raw: String, fallback: String) -> (code: String, number: String) {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let digits = trimmed.filter(\.isNumber)
        let international: Substring? = trimmed.hasPrefix("+") ? digits[...] : (trimmed.hasPrefix("00") ? digits.dropFirst(2) : nil)
        if let international,
           let country = WhappyPhoneCountry.supported.sorted(by: { $0.code.count > $1.code.count }).first(where: { international.hasPrefix($0.code.dropFirst()) }) {
            return (country.code, String(international.dropFirst(country.code.count - 1).prefix(15)))
        }
        return (fallback, String(digits.prefix(15)))
    }
}

private struct WhappyScannerSheet: View {
    @Environment(\.dismiss) private var dismiss
    let onCode: (String) -> Void
    @State private var authorized: Bool?

    var body: some View {
        NavigationStack {
            Group {
                if authorized == true {
                    WhappyQRCodeScanner { value in onCode(value); dismiss() }
                        .ignoresSafeArea(edges: .bottom)
                        .overlay { WapiScannerOverlay() }
                }
                else if authorized == false { ContentUnavailableView("Caméra indisponible", systemImage: "camera.fill", description: Text("Autorisez la caméra dans Réglages pour numériser un code WAPI.")) }
                else { ProgressView("Ouverture de la caméra…") }
            }
            .navigationTitle("Numériser").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } } }
        }
        .task {
            switch AVCaptureDevice.authorizationStatus(for: .video) {
            case .authorized: authorized = true
            case .notDetermined: authorized = await AVCaptureDevice.requestAccess(for: .video)
            default: authorized = false
            }
        }
    }
}

private struct WapiScannerOverlay: View {
    @State private var scanAtBottom = false

    var body: some View {
        VStack {
            Spacer()
            ZStack {
                RoundedRectangle(cornerRadius: 28).stroke(.white.opacity(0.36), lineWidth: 1).frame(width: 286, height: 286)
                RoundedRectangle(cornerRadius: 28).trim(from: 0.01, to: 0.10).stroke(Color.whappyBlue, style: StrokeStyle(lineWidth: 5, lineCap: .round)).frame(width: 286, height: 286)
                RoundedRectangle(cornerRadius: 28).trim(from: 0.26, to: 0.35).stroke(Color.whappyBlue, style: StrokeStyle(lineWidth: 5, lineCap: .round)).frame(width: 286, height: 286)
                RoundedRectangle(cornerRadius: 28).trim(from: 0.51, to: 0.60).stroke(Color.whappyBlue, style: StrokeStyle(lineWidth: 5, lineCap: .round)).frame(width: 286, height: 286)
                RoundedRectangle(cornerRadius: 28).trim(from: 0.76, to: 0.85).stroke(Color.whappyBlue, style: StrokeStyle(lineWidth: 5, lineCap: .round)).frame(width: 286, height: 286)
                Rectangle().fill(Color.whappyBlue).frame(width: 242, height: 2.5)
                    .shadow(color: Color.whappyBlue.opacity(0.9), radius: 7)
                    .offset(y: scanAtBottom ? 112 : -112)
                    .animation(.easeInOut(duration: 1.55).repeatForever(autoreverses: true), value: scanAtBottom)
            }
            Spacer()
            VStack(spacing: 5) {
                Text("Cadrez le code WAPI").font(.headline)
                Text("Détection locale · aucune photo conservée").font(.caption).opacity(0.72)
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 22).padding(.vertical, 14)
            .background(.black.opacity(0.58), in: Capsule())
            .padding(.bottom, 28)
        }
        .allowsHitTesting(false)
        .onAppear { scanAtBottom = true }
    }
}

private struct WhappyQRCodeScanner: UIViewControllerRepresentable {
    let onCode: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onCode: onCode) }

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = UIViewController()
        controller.view.backgroundColor = .black
        context.coordinator.configure(in: controller)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    static func dismantleUIViewController(_ uiViewController: UIViewController, coordinator: Coordinator) { coordinator.stop() }

    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        private let session = AVCaptureSession()
        private let onCode: (String) -> Void
        private var completed = false
        private var previewLayer: AVCaptureVideoPreviewLayer?

        init(onCode: @escaping (String) -> Void) { self.onCode = onCode }

        func configure(in controller: UIViewController) {
            guard let device = AVCaptureDevice.default(for: .video), let input = try? AVCaptureDeviceInput(device: device), session.canAddInput(input) else { return }
            session.addInput(input)
            let output = AVCaptureMetadataOutput()
            guard session.canAddOutput(output) else { return }
            session.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: .main)
            output.metadataObjectTypes = [.qr]
            let layer = AVCaptureVideoPreviewLayer(session: session)
            layer.videoGravity = .resizeAspectFill
            layer.frame = controller.view.bounds
            controller.view.layer.addSublayer(layer)
            previewLayer = layer
            DispatchQueue.global(qos: .userInitiated).async { [session] in session.startRunning() }
        }

        func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
            guard !completed, let code = (metadataObjects.first as? AVMetadataMachineReadableCodeObject)?.stringValue else { return }
            completed = true
            session.stopRunning()
            onCode(code)
        }

        func stop() { if session.isRunning { DispatchQueue.global(qos: .utility).async { [session] in session.stopRunning() } } }
    }
}

private struct WapiEmojiPicker: View {
    @Environment(\.dismiss) private var dismiss
    let onPick: (String) -> Void
    @State private var category = "🙂"
    @State private var recentEmojis: [String] = []
    private let groups: [(String, [String])] = [
        ("🙂", "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😍 🥰 😘 😎 🤩 🥳 🤔 😮 😢 😭 😡 🤯 😴 🤗 🤭 🫡 🫠 🫣 👀".split(separator: " ").map(String.init)),
        ("👋", "👋 🤚 ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 👏 🙌 👐 🤲 🙏 💪 🫶 🤝".split(separator: " ").map(String.init)),
        ("❤️", "❤️ 🩷 🩵 💙 💚 💛 🧡 💜 🖤 🩶 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💯 🔥 ✨ ⭐ 🌟 🎉 🎊 ✅".split(separator: " ").map(String.init)),
        ("🐾", "🐶 🐱 🐭 🐰 🦊 🐻 🐼 🐯 🦁 🦍 🐧 🐦 🦋 🐝 🐢 🐍 🐙 🐟 🐬 🐳 🐘 🦒 🦓 🐆 🌿 🌺 🌻 🌴 🍀 🌊".split(separator: " ").map(String.init)),
        ("🍜", "🍎 🍌 🍉 🍇 🍓 🥭 🍍 🥑 🍅 🥕 🍞 🧀 🍔 🍟 🍕 🌮 🍜 🍝 🍣 🍤 🍰 🧁 🍫 ☕ 🍵 🥤 🍾".split(separator: " ").map(String.init)),
        ("⚽", "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥊 🥋 🛹 🎿 🏆 🥇 🥈 🥉 🎮 🕹️ 🎲 ♟️ 🃏 🎯 🎳 🎸 🎤 🎧 🎬 📸".split(separator: " ").map(String.init)),
        ("🚘", "🚗 🚕 🚌 🚎 🏎️ 🚓 🚑 🚒 🚚 🚜 🛵 🚲 ✈️ 🚀 🚁 ⛵ 🚤 🚢 🚉 🗺️ 🏠 🏖️ 🏙️ 📍".split(separator: " ").map(String.init)),
        ("💼", "📱 💻 ⌨️ 🖥️ 💼 📞 🎥 📹 🎙️ 💬 📎 📄 📁 🧾 💳 💰 💸 🛍️ 🎁 🔒 🔑 🔔 ⚙️ 📈 📊 💡".split(separator: " ").map(String.init)),
        ("🔣", "✅ ❌ ⭕ ❗ ❓ ‼️ ⁉️ ♻️ ⚠️ 🚫 🔞 🔜 🔝 🔙 🔚 ©️ ®️ ™️ #️⃣ *️⃣ 0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣".split(separator: " ").map(String.init)),
        ("🌍", "🇨🇬 🇨🇩 🇫🇷 🇬🇦 🇨🇲 🇳🇬 🇿🇦 🇺🇸 🇨🇦 🇧🇷 🇦🇪 🇸🇦 🇨🇳 🇯🇵 🇰🇷 🇮🇳 🇹🇷 🇮🇹 🇪🇸 🇵🇹 🇩🇪 🇳🇱 🇬🇧 🇲🇦 🇪🇬 🇰🇪 🇸🇳 🇦🇴".split(separator: " ").map(String.init)),
    ]

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                Text("Le clavier emoji iOS donne accès à tous les emoji Unicode. Cette sélection classe les plus utilisés dans WAPI.")
                    .font(.caption).foregroundStyle(.secondary).padding(.horizontal)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) { ForEach(["🕘"] + groups.map { $0.0 }, id: \.self) { icon in Button(icon) { category = icon }.font(.title2).padding(7).background(category == icon ? Color.whappyBlue.opacity(0.15) : .clear, in: RoundedRectangle(cornerRadius: 10)) } }.padding(.horizontal)
                }
                let emojis = category == "🕘" ? recentEmojis : (groups.first(where: { $0.0 == category })?.1 ?? [])
                ScrollView { LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 7), spacing: 12) { ForEach(emojis, id: \.self) { emoji in Button(emoji) { recentEmojis = [emoji] + recentEmojis.filter { $0 != emoji }.prefix(23); onPick(emoji); UIImpactFeedbackGenerator(style: .light).impactOccurred() }.font(.system(size: 28)) } }.padding() }
            }
            .navigationTitle("Emojis")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Terminé") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

private struct MessageActionChip: View {
    let title: String
    let url: URL
    let mine: Bool
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button { openURL(url) } label: {
            Text(title)
                .font(.caption)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(Color.white.opacity(mine ? 0.17 : 0.08))
                .clipShape(RoundedRectangle(cornerRadius: 999))
        }
    }
}

private struct WapiAudioMessagePlayer: View {
    let path: String
    @State private var player: AVPlayer?
    @State private var currentTime: Double = 0
    @State private var duration: Double = 1
    @State private var rate: Float = 1
    @State private var playing = false

    private var mediaURL: URL? {
        if let remote = URL(string: path), remote.scheme == "http" || remote.scheme == "https" { return remote }
        let local = URL(fileURLWithPath: path)
        return FileManager.default.fileExists(atPath: local.path) ? local : nil
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                Button { togglePlayback() } label: {
                    Image(systemName: playing ? "pause.fill" : "play.fill")
                        .font(.body.weight(.bold))
                        .frame(width: 30, height: 30)
                        .background(Color.white.opacity(0.18), in: Circle())
                }
                Image(systemName: "waveform")
                    .font(.headline)
                Text("Note vocale")
                    .font(.subheadline.weight(.semibold))
                Spacer(minLength: 4)
                Menu {
                    ForEach([Float(1), Float(1.5), Float(2)], id: \.self) { value in
                        Button("\(value == floor(value) ? String(format: "%.0f", value) : String(format: "%.1f", value))×") {
                            rate = value
                            if playing { player?.rate = value }
                        }
                    }
                } label: {
                    Text("\(rate == floor(rate) ? String(format: "%.0f", rate) : String(format: "%.1f", rate))×")
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(Color.white.opacity(0.18), in: Capsule())
                }
            }
            HStack(spacing: 8) {
                Slider(value: $currentTime, in: 0...max(duration, 1), onEditingChanged: { editing in
                    if !editing { seek() }
                })
                Text(timeLabel(currentTime))
                    .font(.caption2.monospacedDigit())
                    .opacity(0.72)
            }
        }
        .onAppear(perform: prepare)
        .onDisappear {
            player?.pause()
            playing = false
        }
        .onReceive(Timer.publish(every: 0.25, on: .main, in: .common).autoconnect()) { _ in
            guard let player, playing else { return }
            let seconds = player.currentTime().seconds
            if seconds.isFinite { currentTime = min(max(seconds, 0), max(duration, 1)) }
        }
    }

    private func prepare() {
        guard player == nil, let mediaURL else { return }
        let item = AVPlayerItem(url: mediaURL)
        let next = AVPlayer(playerItem: item)
        next.actionAtItemEnd = .pause
        player = next
        Task {
            let loadedDuration = try? await item.asset.load(.duration)
            guard let seconds = loadedDuration?.seconds, seconds.isFinite, seconds > 0 else { return }
            await MainActor.run { duration = seconds }
        }
    }

    private func togglePlayback() {
        guard let player else { return }
        if playing {
            player.pause()
            playing = false
        } else {
            if currentTime >= duration - 0.05 { seek(to: 0) }
            player.playImmediately(atRate: rate)
            playing = true
        }
    }

    private func seek(to seconds: Double? = nil) {
        let value = seconds ?? currentTime
        player?.seek(to: CMTime(seconds: value, preferredTimescale: 600))
        currentTime = value
    }

    private func timeLabel(_ seconds: Double) -> String {
        guard seconds.isFinite else { return "0:00" }
        return String(format: "%d:%02d", Int(seconds) / 60, Int(seconds) % 60)
    }
}

private struct ConversationView: View {
    @EnvironmentObject private var store: WhappyStore
    let conversationID: UUID
    @State private var draft = ""
    @State private var directCallRoute: WapiDirectCallRoute?
    @State private var groupCallRoute: WapiGroupCallRoute?
    @State private var profileConversation: Conversation?
    @State private var groupSettingsConversation: Conversation?
    @State private var photoItem: PhotosPickerItem?
    @State private var importingDocument = false
    @State private var nextMediaIsViewOnce = false
    @State private var viewOncePreview: Message?
    @State private var zoomedPhoto: ZoomPhoto?
    @State private var translationMessage: Message?
    @State private var showingEmojiPicker = false
    @State private var recorder: AVAudioRecorder?
    @State private var player: AVAudioPlayer?
    @State private var remotePlayer: AVPlayer?
    @State private var recording = false
    @State private var recordingPaused = false
    @State private var voiceDraftURL: URL?
    @State private var searchOpen = false
    @State private var search = ""
    @State private var replyTo: Message?
    @State private var editingMessage: Message?
    @Environment(\.openURL) private var openURL
    private var conversation: Conversation? { store.conversations.first { $0.id == conversationID } }
    private var visibleMessages: [Message] {
        let source = conversation?.messages ?? []
        guard !search.isEmpty else { return source }
        return source.filter { "\($0.text) \($0.replyText ?? "")".matchesWhappySearch(search) }
    }

    private var draftKey: String { "whappy.draft.\(conversationID.uuidString)" }

    private struct MessageActionLink: Identifiable {
        let id = UUID()
        let title: String
        let systemIcon: String
        let url: URL
        let range: NSRange
    }

    private func detectMessageActions(_ text: String) -> [MessageActionLink] {
        guard let detector = try? NSDataDetector(types: (NSTextCheckingResult.CheckingType.link.rawValue | NSTextCheckingResult.CheckingType.phoneNumber.rawValue)) else { return [] }
        let range = NSRange(location: 0, length: (text as NSString).length)
        var actions: [MessageActionLink] = []
        var covered = [NSRange]()
        detector.enumerateMatches(in: text, options: [], range: range) { match, _, _ in
            guard let match = match else { return }
            guard !covered.contains(where: { NSIntersectionRange($0, match.range).length > 0 }) else { return }
            switch match.resultType {
            case .link:
                guard let matchURL = match.url else { return }
                guard let normalized = normalizedActionURL(matchURL) else { return }
                let title = normalized.host?.isEmpty == false ? "Ouvrir \(normalized.host ?? "le lien")" : "Ouvrir le lien"
                let actionURL = normalized
                guard !actions.contains(where: { $0.url == actionURL }) else { return }
                actions.append(MessageActionLink(title: title, systemIcon: "link", url: actionURL, range: match.range))
                covered.append(match.range)
            case .phoneNumber:
                guard let phone = match.phoneNumber else { return }
                let normalized = normalizeMessagePhone(phone)
                guard let cleaned = normalized?.filter({ $0.isNumber || $0 == "+" }).trimmingCharacters(in: CharacterSet.whitespacesAndNewlines),
                      let phoneURL = URL(string: "tel:\(cleaned)") else { return }
                guard !actions.contains(where: { $0.url == phoneURL }) else { return }
                covered.append(match.range)
                actions.append(MessageActionLink(title: "Appeler \(cleaned)", systemIcon: "phone.fill", url: phoneURL, range: match.range))
            default:
                break
            }
        }
        return actions
    }

    private func normalizeMessagePhone(_ value: String) -> String? {
        let raw = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if let exact = WhappyPhoneCountry.supported.first(where: { raw.hasPrefix($0.code) }) {
            if let candidate = WhappyPhoneCountry.normalize(raw, selectedCode: exact.code) { return candidate }
        }
        for country in WhappyPhoneCountry.supported.sorted(by: { $0.code.count > $1.code.count }) {
            if let candidate = WhappyPhoneCountry.normalize(raw, selectedCode: country.code) { return candidate }
        }
        return nil
    }

    private func normalizedActionURL(_ url: URL) -> URL? {
        let raw = url.absoluteString
        let sanitized = raw.trimmingCharacters(in: CharacterSet(charactersIn: "([<{«»〈〉`'\".,;:!?)]}…"))
        guard let trimmed = URL(string: sanitized) else { return nil }

        if let scheme = trimmed.scheme?.lowercased() {
            if scheme == "https" || scheme == "http", let host = trimmed.host?.lowercased(), host == "whappy.chat" || host == "www.whappy.chat" {
                let normalizedPath = trimmed.path.hasPrefix("/") ? String(trimmed.path.dropFirst()) : trimmed.path
                var components = URLComponents()
                components.scheme = "whappy"
                let segments = normalizedPath.split(separator: "/")
                if segments.isEmpty { return nil }
                components.host = segments.first.map(String.init)
                components.path = segments.dropFirst().isEmpty ? "" : "/" + segments.dropFirst().map(String.init).joined(separator: "/")
                if let query = trimmed.query { components.query = query }
                return components.url
            }
            let allowed = ["http", "https", "mailto", "sms", "tel", "facetime", "whatsapp", "whappy"]
            return allowed.contains(scheme) ? trimmed : nil
        }

        let hostless = sanitized.trimmingCharacters(in: .whitespacesAndNewlines)
        if hostless.hasPrefix("www.") || (hostless.contains(".") && !hostless.contains(" ")) {
            return URL(string: "https://\(hostless)")
        }
        return nil
    }

    private func messageDeliveryLabel(_ message: Message, in conversation: Conversation?) -> String {
        if message.status == "sending" { return " • Envoi..." }
        if message.status == "failed" { return " • Échec" }
        let readTimestamp = conversation?.readAt ?? .distantPast
        return (message.sentAt <= readTimestamp) ? " • Lu" : " • Envoyé"
    }

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
                                if !message.mine {
                                    Group {
                                        if let photoURL = conversation?.photoURL, let url = URL(string: photoURL), !photoURL.isEmpty {
                                            WapiCachedRemoteImage(url: url) { InitialsAvatar(text: message.senderName ?? conversation?.initials ?? "W", size: 30) }
                                        } else {
                                            InitialsAvatar(text: message.senderName ?? conversation?.initials ?? "W", size: 30)
                                        }
                                    }
                                    .frame(width: 30, height: 30)
                                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                                    .frame(maxHeight: .infinity, alignment: .bottom)
                                }
                                VStack(alignment: message.mine ? .trailing : .leading, spacing: 5) {
                                    if let replied = message.replyText, !replied.isEmpty { Text("↩ \(replied)").font(.caption).lineLimit(2).padding(7).frame(maxWidth: .infinity, alignment: .leading).background(.white.opacity(message.mine ? 0.16 : 0.55)).clipShape(RoundedRectangle(cornerRadius: 9)) }
                                    if let replyTarget = message.replyToID, let targetMessage = visibleMessages.first(where: { $0.id == replyTarget }) {
                                        Button { withAnimation { proxy.scrollTo(targetMessage.id, anchor: .center) } } label: {
                                            HStack(spacing: 4) {
                                                Image(systemName: "arrow.up.circle")
                                                Text("Voir le message d’origine")
                                            }
                                        }
                                        .font(.caption2)
                                        .buttonStyle(.plain)
                                        .foregroundStyle(.secondary)
                                    }
                                    if message.deleted { Text("Message supprimé").italic().opacity(0.7) }
                                    else if message.viewOnce, !message.mine, message.viewedByIDs.contains(store.firebaseUserID ?? "local") {
                                        Label("Média à vue unique consulté", systemImage: "eye.slash.fill").font(.callout)
                                    } else if message.viewOnce, !message.mine {
                                        Button { viewOncePreview = message } label: {
                                            Label("Ouvrir le média · 1 vue", systemImage: "eye.fill")
                                                .font(.callout.weight(.semibold))
                                                .padding(.vertical, 8)
                                        }.buttonStyle(.plain)
                                    } else if message.kind == "image", let path = message.mediaPath {
                                        imageMessage(path)
                                    } else if message.kind == "audio", let path = message.mediaPath {
                                        WapiAudioMessagePlayer(path: path)
                                    } else if message.kind == "video", let path = message.mediaPath, let url = mediaURL(path) {
                                        VideoPlayer(player: AVPlayer(url: url)).frame(width: 220, height: 150).clipShape(RoundedRectangle(cornerRadius: 12))
                                    } else if message.kind == "document", let path = message.mediaPath, let url = mediaURL(path) {
                                        Link(destination: url) { Label(message.mediaName ?? "Document Waphsare", systemImage: "doc.richtext.fill") }.buttonStyle(.plain)
                                        if let size = message.mediaSizeBytes { Text("Original préservé · \(ByteCountFormatter.string(fromByteCount: size, countStyle: .file))").font(.caption2).opacity(0.72) }
                                    } else { Text(message.text) }
                                    if message.viewOnce, message.mine { Label("Média à vue unique", systemImage: "eye.fill").font(.caption2).opacity(0.72) }
                                    let actions = detectMessageActions(message.text)
                                    if !actions.isEmpty && !message.deleted {
                                        VStack(alignment: .leading, spacing: 5) {
                                            ForEach(Array(actions.prefix(2))) { action in
                                                MessageActionChip(title: action.title, url: action.url, mine: message.mine)
                                            }
                                        }.padding(.top, 5)
                                    }
                                    if !message.reactions.isEmpty { HStack(spacing: 4) { ForEach(Array(Dictionary(grouping: message.reactions.values, by: { $0 })).sorted(by: { $0.key < $1.key }), id: \.key) { group in Text(group.key + (group.value.count > 1 ? " \(group.value.count)" : "")).font(.caption).padding(.horizontal, 6).padding(.vertical, 3).background(.white.opacity(message.mine ? 0.18 : 0.7)).clipShape(Capsule()) } } }
                                    HStack(spacing: 4) { if message.edited { Text("modifié ·").font(.caption2).opacity(0.6) }; Text(message.sentAt, style: .time).font(.caption2).opacity(0.65); if message.mine { Text(messageDeliveryLabel(message, in: conversation)).font(.caption2).opacity(0.7) } }
                                }
                                .padding(.horizontal, 14).padding(.vertical, 9)
                                .background(message.mine ? Color.whappyBlue : Color(.secondarySystemBackground))
                                .foregroundStyle(message.mine ? .white : Color.whappyInk).clipShape(RoundedRectangle(cornerRadius: 17))
                                .contextMenu {
                                    if !message.deleted {
                                        Button { replyTo = message } label: { Label("Répondre", systemImage: "arrowshape.turn.up.left") }
                                        ForEach(detectMessageActions(message.text).prefix(2)) { action in
                                            Button { openURL(action.url) } label: { Label(action.title, systemImage: action.systemIcon) }
                                        }
                                        Menu("Réagir") { ForEach(["❤️", "👍", "😂", "😮", "🙏"], id: \.self) { emoji in Button(emoji) { store.react(to: message.id, in: conversationID, emoji: emoji) } } }
                                        if !message.text.isEmpty { Button { UIPasteboard.general.string = message.text } label: { Label("Copier", systemImage: "doc.on.doc") } }
                                        if !message.text.isEmpty { Button { translationMessage = message } label: { Label("Traduire avec Lingwap", systemImage: "character.bubble") } }
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
            if let voiceDraftURL {
                HStack(spacing: 12) {
                    Image(systemName: "waveform.circle.fill").font(.title2).foregroundStyle(Color.whappyBlue)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Note vocale prête").font(.subheadline.bold())
                        Text("Écoutez-la avant de l’envoyer").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button { playAudio(voiceDraftURL.path) } label: { Image(systemName: player?.isPlaying == true ? "pause.circle.fill" : "play.circle.fill").font(.title2) }.buttonStyle(.plain)
                    Button(role: .destructive) { try? FileManager.default.removeItem(at: voiceDraftURL); self.voiceDraftURL = nil } label: { Image(systemName: "trash.circle.fill").font(.title2) }.buttonStyle(.plain)
                    Button { sendVoiceDraft() } label: { Image(systemName: "arrow.up.circle.fill").font(.system(size: 34)) }.buttonStyle(.plain)
                }.padding(.horizontal).padding(.vertical, 9).background(Color.whappyBlue.opacity(0.08))
            }
            HStack(spacing: 10) {
                PhotosPicker(selection: $photoItem, matching: .any(of: [.images, .videos])) { Image(systemName: "photo.circle.fill").font(.title2) }.disabled(recording)
                Button { importingDocument = true } label: { Image(systemName: "paperclip.circle.fill").font(.title2) }.disabled(recording)
                Button { showingEmojiPicker = true } label: { Image(systemName: "face.smiling.inverse").font(.title2).foregroundStyle(Color.whappyBlue) }.disabled(recording)
                if recording {
                    Button { toggleRecordingPause() } label: { Image(systemName: recordingPaused ? "play.circle.fill" : "pause.circle.fill").font(.title2).foregroundStyle(Color.whappyBlue) }
                }
                Button { toggleRecording() } label: { Image(systemName: recording ? "stop.circle.fill" : "mic.circle.fill").font(.title2).foregroundStyle(recording ? .red : Color.whappyBlue) }.disabled(voiceDraftURL != nil)
                TextField("Votre message", text: $draft, axis: .vertical)
                    .textFieldStyle(.roundedBorder)
                    .onChange(of: draft) { oldValue, newValue in
                        if newValue.count > oldValue.count { WapiSounds.typing() }
                    }
                Button { submitDraft() } label: { Image(systemName: editingMessage == nil ? "arrow.up.circle.fill" : "checkmark.circle.fill").font(.system(size: 34)) }
                    .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }.padding(.horizontal).padding(.vertical, 10).background(.bar)
            HStack {
                Button { nextMediaIsViewOnce.toggle() } label: {
                    Label(nextMediaIsViewOnce ? "1 vue activée" : "Média", systemImage: nextMediaIsViewOnce ? "eye.fill" : "plus.circle")
                        .font(.caption.weight(.semibold))
                }.buttonStyle(.plain).foregroundStyle(nextMediaIsViewOnce ? Color.whappyBlue : .secondary)
                Spacer()
                if recording { Text(recordingPaused ? "Note vocale en pause" : "Enregistrement… touchez Stop pour écouter").font(.caption).foregroundStyle(recordingPaused ? Color.whappyBlue : .red) }
            }.padding(.horizontal).padding(.bottom, 8).background(.bar)
        }
        .navigationTitle("").navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                if let conversation {
                    WapiConversationToolbarTitle(conversation: conversation) {
                        if conversation.groupOwnerID != nil {
                            groupSettingsConversation = conversation
                        } else {
                            profileConversation = conversation
                        }
                    }
                }
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { searchOpen.toggle(); if !searchOpen { search = "" } } label: { Image(systemName: "magnifyingglass") }
                if let conversation, conversation.groupOwnerID != nil, let groupID = conversation.remoteID {
                    Button { WapiSounds.callStarted(); groupCallRoute = WapiGroupCallRoute(callID: nil, groupID: groupID, groupName: conversation.name, video: false, groupSource: conversation.source ?? "groups") } label: { Image(systemName: "phone.fill") }
                    Button { WapiSounds.callStarted(); groupCallRoute = WapiGroupCallRoute(callID: nil, groupID: groupID, groupName: conversation.name, video: true, groupSource: conversation.source ?? "groups") } label: { Image(systemName: "video.fill") }
                    Button { groupSettingsConversation = conversation } label: { Image(systemName: "info.circle") }
                } else if let conversation {
                    Button {
                        WapiSounds.callStarted()
                        directCallRoute = WapiDirectCallRoute(callID: nil, peerID: conversation.peerUID, peerName: conversation.name, peerPhotoURL: conversation.photoURL ?? "", video: false)
                    } label: { Image(systemName: "phone.fill") }
                    Button {
                        WapiSounds.callStarted()
                        directCallRoute = WapiDirectCallRoute(callID: nil, peerID: conversation.peerUID, peerName: conversation.name, peerPhotoURL: conversation.photoURL ?? "", video: true)
                    } label: { Image(systemName: "video.fill") }
                    Button { profileConversation = conversation } label: { Image(systemName: "person.crop.circle") }
                }
            }
        }
        .fullScreenCover(item: $directCallRoute) { route in WapiDirectCallRoom(route: route) { directCallRoute = nil } }
        .fullScreenCover(item: $groupCallRoute) { route in WapiGroupCallRoom(route: route) { groupCallRoute = nil } }
        .sheet(item: $profileConversation) { WapiContactProfileView(conversation: $0) }
        .sheet(item: $groupSettingsConversation) { WapiGroupSettingsView(conversation: $0) }
        .sheet(item: $translationMessage) { WapiTranslationSheet(message: $0) }
        .sheet(isPresented: $showingEmojiPicker) { WapiEmojiPicker { emoji in draft.append(emoji); WapiSounds.gameMove(); WapiSounds.haptic(.light) } }
        .fullScreenCover(item: $zoomedPhoto) { photo in
            ZoomablePhotoViewer(image: photo.image, onDismiss: { zoomedPhoto = nil })
        }
        .onChange(of: photoItem) { _, item in guard let item else { return }; Task { await attachMedia(item) } }
        .fileImporter(isPresented: $importingDocument, allowedContentTypes: [.pdf, .plainText, .rtf, .data]) { attachDocument($0) }
        .sheet(item: $viewOncePreview) { message in
            WapiViewOncePreview(message: message) { store.consumeViewOnce(message.id, in: conversationID); viewOncePreview = nil }
        }
        .onAppear {
            if draft.isEmpty { draft = UserDefaults.standard.string(forKey: draftKey) ?? "" }
            if let conversation { store.openFirebaseConversation(conversation) }
        }
        .onDisappear { recorder?.stop(); recorder = nil; recording = false; recordingPaused = false; store.closeFirebaseConversation() }
        .onChange(of: draft) { _, value in if editingMessage == nil { UserDefaults.standard.set(value, forKey: draftKey) } }
    }

    private func submitDraft() {
        let value = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }
        if let editingMessage { store.editMessage(editingMessage.id, in: conversationID, text: value); self.editingMessage = nil }
        else { store.send(value, to: conversationID, replyTo: replyTo) }
        WapiSounds.sent()
        draft = ""; replyTo = nil; UserDefaults.standard.removeObject(forKey: draftKey)
    }

    private func messageDayLabel(_ date: Date) -> String {
        if Calendar.current.isDateInToday(date) { return "Aujourd’hui" }
        if Calendar.current.isDateInYesterday(date) { return "Hier" }
        return date.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(Locale(identifier: "fr_FR"))).capitalized
    }

    @ViewBuilder
    private func imageMessage(_ path: String) -> some View {
        if let image = UIImage(contentsOfFile: path) {
            Image(uiImage: image)
                .resizable().scaledToFill().frame(width: 190, height: 150)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .contentShape(RoundedRectangle(cornerRadius: 12))
                .onTapGesture { zoomedPhoto = ZoomPhoto(image: image) }
        } else if let url = mediaURL(path) {
            AsyncImage(url: url) { phase in
                if case .success(let image) = phase { image.resizable().scaledToFill() }
                else if case .failure = phase { Label("Photo indisponible", systemImage: "exclamationmark.triangle") }
                else { ProgressView() }
            }
            .frame(width: 190, height: 150).clipShape(RoundedRectangle(cornerRadius: 12))
        } else {
            Label("Photo indisponible", systemImage: "exclamationmark.triangle")
        }
    }

    private func mediaURL(_ value: String) -> URL? {
        if value.hasPrefix("http://") || value.hasPrefix("https://") { return URL(string: value) }
        return URL(fileURLWithPath: value)
    }

    private func attachMedia(_ item: PhotosPickerItem) async {
        guard let data = try? await item.loadTransferable(type: Data.self), let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        let type = item.supportedContentTypes.first
        let isVideo = item.supportedContentTypes.contains { $0.conforms(to: .movie) || $0.conforms(to: .video) }
        let fallback = isVideo ? "mp4" : "jpg"
        let ext = type?.preferredFilenameExtension ?? fallback
        let url = directory.appendingPathComponent("wapi-media-\(UUID().uuidString).\(ext)")
        guard (try? data.write(to: url, options: .atomic)) != nil else { return }
        store.sendMedia(kind: isVideo ? "video" : "image", path: url.path, to: conversationID, mediaName: "wapi-media.\(ext)", viewOnce: nextMediaIsViewOnce)
        WapiSounds.mediaAdded()
        WapiSounds.haptic(.light)
        nextMediaIsViewOnce = false
        photoItem = nil
    }

    private func attachDocument(_ result: Result<URL, Error>) {
        guard case .success(let source) = result else { return }
        let accessing = source.startAccessingSecurityScopedResource()
        defer { if accessing { source.stopAccessingSecurityScopedResource() } }
        guard let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        let name = source.lastPathComponent.isEmpty ? "document" : source.lastPathComponent
        let target = directory.appendingPathComponent("wapi-document-\(UUID().uuidString)-\(name)")
        do {
            try FileManager.default.copyItem(at: source, to: target)
            store.sendMedia(kind: "document", path: target.path, to: conversationID, mediaName: name)
            WapiSounds.mediaAdded()
        } catch {
            store.firebaseMessage = "WAPI n’a pas pu préparer ce document."
        }
    }

    private func toggleRecording() {
        if let recorder {
            recorder.stop(); self.recorder = nil; recording = false
            recordingPaused = false
            if (try? recorder.url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0 > 0 { voiceDraftURL = recorder.url }
            else { try? FileManager.default.removeItem(at: recorder.url) }
            WapiSounds.recordingStopped()
            WapiSounds.haptic(.medium)
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
                    let audio = try AVAudioRecorder(url: url, settings: [AVFormatIDKey: Int(kAudioFormatMPEG4AAC), AVSampleRateKey: 48_000, AVNumberOfChannelsKey: 1, AVEncoderBitRateKey: 128_000, AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue])
                    audio.record(); recorder = audio; recording = true; recordingPaused = false
                    WapiSounds.recordingStarted()
                    WapiSounds.haptic(.medium)
                } catch { recording = false }
            }
        }
    }

    private func toggleRecordingPause() {
        guard let recorder else { return }
        if recordingPaused {
            recorder.record()
            recordingPaused = false
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } else {
            recorder.pause()
            recordingPaused = true
            UIImpactFeedbackGenerator(style: .soft).impactOccurred()
        }
    }

    private func sendVoiceDraft() {
        guard let voiceDraftURL else { return }
        store.sendMedia(kind: "audio", path: voiceDraftURL.path, to: conversationID, mediaName: voiceDraftURL.lastPathComponent, viewOnce: nextMediaIsViewOnce)
        WapiSounds.sent()
        WapiSounds.haptic(.medium)
        self.voiceDraftURL = nil
        nextMediaIsViewOnce = false
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    private func playAudio(_ path: String) {
        if player?.isPlaying == true { player?.stop(); player = nil; return }
        if let remote = URL(string: path), remote.scheme == "https" || remote.scheme == "http" {
            remotePlayer?.pause()
            let next = AVPlayer(url: remote)
            remotePlayer = next
            next.play()
            return
        }
        guard let audio = try? AVAudioPlayer(contentsOf: URL(fileURLWithPath: path)) else { return }
        audio.play(); player = audio
    }
}

private struct WapiViewOncePreview: View {
    let message: Message
    let onClose: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var player: AVPlayer?

    private var url: URL? {
        guard let path = message.mediaPath else { return nil }
        if path.hasPrefix("https://") || path.hasPrefix("http://") { return URL(string: path) }
        return URL(fileURLWithPath: path)
    }

    var body: some View {
        NavigationStack {
            Group {
                if message.kind == "image", let url {
                    AsyncImage(url: url) { phase in
                        if case .success(let image) = phase { image.resizable().scaledToFit() }
                        else if case .failure = phase { ContentUnavailableView("Média indisponible", systemImage: "exclamationmark.triangle") }
                        else { ProgressView() }
                    }.padding()
                } else if message.kind == "video", let url {
                    VideoPlayer(player: AVPlayer(url: url)).ignoresSafeArea(edges: .bottom)
                } else if message.kind == "audio", let url {
                    VStack(spacing: 18) {
                        Image(systemName: "waveform.circle.fill").font(.system(size: 72)).foregroundStyle(Color.whappyBlue)
                        Button("Lire la note vocale") { let value = AVPlayer(url: url); player = value; value.play() }.buttonStyle(.borderedProminent)
                    }
                } else {
                    ContentUnavailableView("Média indisponible", systemImage: "eye.slash")
                }
            }
            .navigationTitle("Vue unique").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fermer") { player?.pause(); onClose(); dismiss() } } }
            .onDisappear { onClose() }
        }
    }
    }

private struct WapiConversationToolbarTitle: View {
    let conversation: Conversation
    let onOpen: () -> Void

    private var presenceLabel: String {
        if conversation.source == "groups" {
            return "\(conversation.groupMembers.count) membre(s)"
        }
        if conversation.peerIsOnline == true { return "En ligne" }
        if let lastSeen = conversation.peerLastSeenAt {
            let relative = RelativeDateTimeFormatter()
            relative.locale = Locale.autoupdatingCurrent
            relative.unitsStyle = .abbreviated
            return "Vu \(relative.localizedString(for: lastSeen, relativeTo: Date()))"
        }
        return conversation.phoneNumber.isEmpty ? "Contact WAPI" : conversation.phoneNumber
    }

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 7) {
                ConversationAvatar(conversation: conversation)
                    .scaleEffect(0.76)
                    .frame(width: 35, height: 35)
                VStack(alignment: .leading, spacing: 1) {
                    Text(conversation.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.whappyInk)
                        .lineLimit(1)
                    HStack(spacing: 4) {
                        if conversation.peerIsOnline == true {
                            Circle().fill(Color.whappyBlue).frame(width: 6, height: 6)
                        }
                        Text(presenceLabel)
                            .font(.caption2)
                            .foregroundStyle(conversation.peerIsOnline == true ? Color.whappyBlue : .secondary)
                            .lineLimit(1)
                    }
                }
            }
            .frame(maxWidth: 185)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(conversation.name), \(presenceLabel)")
    }
}

private struct WapiTranslationSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WhappyStore
    let message: Message
    @State private var targetLanguage = "en"
    @State private var result: WapiTranslationResult?
    @State private var errorMessage: String?
    @State private var translating = false

    private var selectedLanguage: WapiTranslationLanguage {
        WapiTranslationLanguage.language(for: targetLanguage)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Message d’origine") {
                    Text(message.text)
                        .textSelection(.enabled)
                        .lineLimit(8)
                }
                Section("Traduire vers") {
                    Picker("Langue", selection: $targetLanguage) {
                        ForEach(WapiTranslationLanguage.supported) { language in
                            Text(language.displayName).tag(language.code)
                        }
                    }
                    .pickerStyle(.navigationLink)
                    Text("\(WapiTranslationLanguage.supported.count) langues disponibles")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Section {
                    Button {
                        Task { await translate() }
                    } label: {
                        HStack {
                            Label("Traduire en \(selectedLanguage.nativeName)", systemImage: "character.bubble.fill")
                            Spacer()
                            if translating { ProgressView() }
                        }
                    }
                    .disabled(translating)
                }
                if let result {
                    Section("Traduction") {
                        Text(result.text)
                            .textSelection(.enabled)
                        Text("Langue détectée : \(result.detectedLanguage.uppercased())")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                if let errorMessage {
                    Section {
                        Label(errorMessage, systemImage: "exclamationmark.triangle.fill")
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }
                Section {
                    Text("Le texte est envoyé uniquement au relais Lingwap auto-hébergé configuré par WAPI. Les clés de traduction ne sont jamais présentes dans l’application.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Lingwap")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fermer") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func translate() async {
        translating = true
        errorMessage = nil
        result = nil
        do {
            result = try await store.translateWithLingwap(message.text, targetLanguage: targetLanguage)
        } catch {
            errorMessage = wapiUserFacingError(error, action: "La traduction Lingwap")
        }
        translating = false
    }
}

private struct WapiContactProfileView: View {
    let conversation: Conversation
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 18) {
                ConversationAvatar(conversation: conversation).frame(width: 96, height: 96)
                VStack(spacing: 5) {
                    Text(conversation.name).font(.title2.bold()).foregroundStyle(Color.whappyInk)
                    if !conversation.phoneNumber.isEmpty { Text(conversation.phoneNumber).font(.callout).foregroundStyle(.secondary) }
                    Text("Profil WAPI").font(.caption.weight(.semibold)).foregroundStyle(Color.wapiVerified)
                    if conversation.peerIsOnline == true {
                        Label("En ligne maintenant", systemImage: "circle.fill")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(Color.whappyBlue)
                    } else if let lastSeen = conversation.peerLastSeenAt {
                        Text("Dernière activité : \(lastSeen.formatted(date: .abbreviated, time: .shortened))")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                if let phoneURL = URL(string: "tel:\(conversation.phoneNumber.filter { $0.isNumber || $0 == "+" })"), !conversation.phoneNumber.isEmpty {
                    Link(destination: phoneURL) {
                        Label("Appeler avec l’appareil", systemImage: "phone.fill").frame(maxWidth: .infinity)
                    }.buttonStyle(.borderedProminent).tint(Color.whappyBlue)
                }
                Text("Les informations affichées ici respectent les réglages de confidentialité de ce compte.")
                    .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                Spacer()
            }
            .padding(28).background(Color.whappyBackground)
            .navigationTitle("Profil").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } } }
        }
    }
}

private struct WapiGroupSettingsView: View {
    @EnvironmentObject private var store: WhappyStore
    @Environment(\.dismiss) private var dismiss
    let conversation: Conversation
    @State private var name: String
    @State private var photoItem: PhotosPickerItem?
    @State private var selectedImage: UIImage?
    @State private var removePhoto = false

    init(conversation: Conversation) {
        self.conversation = conversation
        _name = State(initialValue: conversation.name)
    }

    private var currentUserID: String { store.firebaseUserID ?? "" }
    private var canEdit: Bool { conversation.groupOwnerID == currentUserID || conversation.groupAdminIDs.contains(currentUserID) }
    private var canManageAdministrators: Bool { conversation.groupOwnerID == currentUserID }
    private var hasChanges: Bool { name.trimmingCharacters(in: .whitespacesAndNewlines) != conversation.name || selectedImage != nil || removePhoto }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 14) {
                        Group {
                            if let selectedImage { Image(uiImage: selectedImage).resizable().scaledToFill() }
                            else { ConversationAvatar(conversation: conversation) }
                        }.frame(width: 74, height: 74).clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        VStack(alignment: .leading, spacing: 5) {
                            Text(conversation.name).font(.headline)
                            Text("\(conversation.groupMembers.count) membre(s)").font(.caption).foregroundStyle(.secondary)
                            Text(canEdit ? "Administration du groupe" : "Membre du groupe").font(.caption.weight(.semibold)).foregroundStyle(canEdit ? Color.whappyBlue : .secondary)
                        }
                    }.padding(.vertical, 5)
                }
                if canEdit {
                    Section("Identité du groupe") {
                        PhotosPicker(selection: $photoItem, matching: .images) {
                            Label(selectedImage == nil ? "Changer la photo" : "Nouvelle photo sélectionnée", systemImage: "photo.on.rectangle.angled")
                        }
                        Toggle("Retirer la photo", isOn: $removePhoto).disabled((conversation.photoURL ?? "").isEmpty)
                        TextField("Nom du groupe", text: $name).textInputAutocapitalization(.words)
                        Text("Chaque modification est enregistrée dans la conversation pour informer les membres.").font(.footnote).foregroundStyle(.secondary)
                    }
                } else {
                    Section { Label("Seuls les administrateurs peuvent modifier le nom ou la photo du groupe.", systemImage: "lock.fill").font(.footnote) }
                }
                Section("Membres") {
                    if conversation.groupMembers.isEmpty {
                        ContentUnavailableView("Membres en cours de synchronisation", systemImage: "person.2")
                    } else {
                        ForEach(conversation.groupMembers) { member in
                            HStack(spacing: 12) {
                                WapiMemberAvatar(member: member)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(member.displayName).font(.body.weight(.medium))
                                    Text(memberRole(member)).font(.caption).foregroundStyle(member.uid == conversation.groupOwnerID || conversation.groupAdminIDs.contains(member.uid) ? Color.whappyBlue : .secondary)
                                }
                                Spacer()
                                if canManageAdministrators, member.uid != conversation.groupOwnerID {
                                    Button(conversation.groupAdminIDs.contains(member.uid) ? "Retirer" : "Nommer admin") {
                                        store.setFirebaseGroupAdministrator(conversation: conversation, memberID: member.uid, administrator: !conversation.groupAdminIDs.contains(member.uid))
                                    }.font(.caption.weight(.semibold)).disabled(store.firebaseBusy)
                                }
                            }.padding(.vertical, 3)
                        }
                    }
                }
            }
            .background(Color.whappyBackground)
            .navigationTitle("Infos du groupe").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } }
                if canEdit {
                    ToolbarItem(placement: .confirmationAction) {
                        Button(store.firebaseBusy ? "Enregistrement…" : "Enregistrer") { save() }
                            .disabled(!hasChanges || !(2...80).contains(name.trimmingCharacters(in: .whitespacesAndNewlines).count) || store.firebaseBusy)
                    }
                }
            }
            .onChange(of: photoItem) { _, item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                        selectedImage = image
                        removePhoto = false
                    }
                    photoItem = nil
                }
            }
        }
    }

    private func memberRole(_ member: WapiGroupMember) -> String {
        if member.uid == conversation.groupOwnerID { return "Créateur · administrateur permanent" }
        if conversation.groupAdminIDs.contains(member.uid) { return "Administrateur" }
        return member.phoneNumber.isEmpty ? "Membre" : member.phoneNumber
    }

    private func save() {
        let photoData = selectedImage.flatMap(makeWapiGroupPhotoData)
        if selectedImage != nil && photoData == nil {
            store.firebaseMessage = "La photo n’a pas pu être préparée. Choisissez une autre image."
            return
        }
        store.updateFirebaseGroup(
            conversation: conversation,
            name: name,
            photoData: photoData,
            removePhoto: removePhoto
        )
    }
}

/// Normalizes a picked group image before it reaches Firebase.
///
/// Photos from modern phones can be 20–60 MB and can keep their original
/// orientation. Rendering a centered square makes the upload predictable,
/// keeps Storage rules fast on mobile networks, and gives Android/iOS the same
/// group-avatar geometry.
private func makeWapiGroupPhotoData(_ image: UIImage) -> Data? {
    let canvasSide: CGFloat = 1024
    guard image.size.width > 0, image.size.height > 0 else { return nil }
    let scale = max(canvasSide / image.size.width, canvasSide / image.size.height)
    let drawSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
    let origin = CGPoint(x: (canvasSide - drawSize.width) / 2, y: (canvasSide - drawSize.height) / 2)
    let format = UIGraphicsImageRendererFormat()
    format.scale = 1
    format.opaque = true
    return UIGraphicsImageRenderer(size: CGSize(width: canvasSide, height: canvasSide), format: format)
        .jpegData(withCompressionQuality: 0.86) { _ in
            UIColor.white.setFill()
            UIRectFill(CGRect(x: 0, y: 0, width: canvasSide, height: canvasSide))
            image.draw(in: CGRect(origin: origin, size: drawSize))
        }
}

private struct WapiMemberAvatar: View {
    let member: WapiGroupMember

    var body: some View {
        Group {
            if let url = URL(string: member.photoURL), !member.photoURL.isEmpty {
                WapiCachedRemoteImage(url: url) { InitialsAvatar(text: member.displayName, size: 42) }
            } else {
                InitialsAvatar(text: member.displayName, size: 42)
            }
        }.frame(width: 42, height: 42).clipShape(Circle())
    }
}

private struct ZoomPhoto: Identifiable {
    let id = UUID()
    let image: UIImage
}

private struct ZoomablePhotoViewer: View {
    let image: UIImage
    let onDismiss: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .topTrailing) {
                Color.black.ignoresSafeArea()

                let maxOffsetX = max(0, (geometry.size.width * (scale - 1)) / 2)
                let maxOffsetY = max(0, (geometry.size.height * (scale - 1)) / 2)
                let clampedOffset = CGSize(
                    width: min(maxOffsetX, max(-maxOffsetX, offset.width)),
                    height: min(maxOffsetY, max(-maxOffsetY, offset.height))
                )

                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .scaleEffect(scale)
                    .offset(clampedOffset)
                    .gesture(
                        MagnificationGesture()
                            .onChanged { value in
                                let nextScale = lastScale * value
                                scale = min(max(nextScale, 1), 4)
                            }
                            .onEnded { _ in
                                if scale < 1.06 {
                                    withAnimation(.spring(response: 0.25, dampingFraction: 0.95)) {
                                        scale = 1
                                        lastScale = 1
                                        offset = .zero
                                        lastOffset = .zero
                                    }
                                } else {
                                    lastScale = scale
                                }
                            }
                    )
                    .simultaneousGesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                if scale > 1 { offset = CGSize(width: lastOffset.width + value.translation.width, height: lastOffset.height + value.translation.height) }
                            }
                            .onEnded { _ in
                                lastOffset = offset
                            }
                    )
                    .simultaneousGesture(
                        TapGesture(count: 2)
                            .onEnded {
                                withAnimation(.spring(response: 0.25, dampingFraction: 0.9)) {
                                    if scale > 1.06 {
                                        scale = 1
                                        lastScale = 1
                                        offset = .zero
                                        lastOffset = .zero
                                    } else {
                                        scale = 2
                                        lastScale = 2
                                    }
                                }
                            }
                    )

                Button {
                    onDismiss()
                    dismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 34, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(20)
                        .contentShape(Circle())
                }
            }
        }
    }
}

struct CallsView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var directCallRoute: WapiDirectCallRoute?
    @State private var unavailableMessage: String?

    var body: some View {
        List {
            Section("Récents") {
                ForEach(store.calls) { call in
                    HStack(spacing: 13) {
                        Image(systemName: call.mode.systemImage).foregroundStyle(call.missed ? .red : Color.whappyBlue).frame(width: 32)
                        VStack(alignment: .leading) { Text(call.name).font(.headline); Text(call.date, style: .relative).font(.caption).foregroundStyle(.secondary) }
                        Spacer()
                        Button { start(call, video: false) } label: { Image(systemName: "phone.circle.fill").font(.title2) }.buttonStyle(.plain)
                        Button { start(call, video: true) } label: { Image(systemName: "video.circle.fill").font(.title2) }.buttonStyle(.plain)
                    }.padding(.vertical, 4)
                }
            }
        }
        .navigationTitle(store.activeBusinessMode ? "Appels Business" : "Appels")
        .fullScreenCover(item: $directCallRoute) { route in WapiDirectCallRoom(route: route) { directCallRoute = nil } }
        .alert("Appel WAPI indisponible", isPresented: Binding(get: { unavailableMessage != nil }, set: { if !$0 { unavailableMessage = nil } })) { Button("Fermer", role: .cancel) {} } message: { Text(unavailableMessage ?? "") }
    }

    private func start(_ call: CallRecord, video: Bool) {
        guard let conversation = store.conversations.first(where: { $0.phoneNumber == call.phoneNumber && $0.peerUID != nil }) else {
            unavailableMessage = "Ce contact doit avoir un compte WAPI actif pour un appel WAPI."
            return
        }
        WapiSounds.callStarted()
        directCallRoute = WapiDirectCallRoute(callID: nil, peerID: conversation.peerUID, peerName: conversation.name, peerPhotoURL: conversation.photoURL ?? "", video: video)
    }
}

struct MarketView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var search = ""
    @State private var selling = false
    @State private var showingCart = false
    private var filtered: [Listing] { store.listings.filter { "\($0.title) \($0.place) \($0.seller)".matchesWhappySearch(search) } }

    var body: some View {
        ScrollView { LazyVStack(spacing: 12) { BusinessSaleRoomsIOSRail(); ForEach(filtered) { listing in NavigationLink(value: listing) { ListingRow(listing: listing) }.buttonStyle(.plain) } }.padding() }
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

private struct LegacyLiveView: View {
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
        .toolbar { if let room, room.host == whappyFounderName, room.live { ToolbarItem(placement: .topBarTrailing) { Button("Terminer", role: .destructive) { store.endLive(room) } } } }
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

private struct WapiIOSGameLaunch: Identifiable {
    let name: String
    let subtitle: String
    var id: String { name }
}

struct GamesView: View {
    @State private var score = 0
    @State private var streak = 1
    @State private var launchedGame: WapiIOSGameLaunch?
    private let games = [("King QI", "Quiz vocal · duels · trophées · direct", "crown.fill"), ("Ludo WAPI", "Plateau 3D · dés animés · amis", "dice.fill"), ("Billard WAPI", "Table 3D · visée tactile · tournoi", "circle.grid.cross.fill"), ("Échecs WAPI", "Échiquier 3D · IA et duels", "checkerboard.rectangle"), ("Jeu de dames", "Pions 3D · dames couronnées · IA", "circle.hexagongrid.fill"), ("Cartes WAPI", "Tables privées · amis · tournoi", "suit.club.fill"), ("Poker WAPI", "Salon privé · jetons non monétaires", "suit.spade.fill"), ("Défi du jour", "Quiz rapide · 60 secondes", "bolt.fill"), ("Mots & idées", "Trouvez la solution ensemble", "sparkles")]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 9) {
                    Label("WHAPPY PLAY", systemImage: "bolt.fill").font(.caption.bold()).foregroundStyle(Color.whappyBlue)
                    Text("Jouez. Progressez.\nRestez connecté.").font(.system(size: 30, weight: .black, design: .rounded)).foregroundStyle(.white)
                    Text("Des mini-jeux à lancer seul ou avec votre communauté.").foregroundStyle(.white.opacity(0.8))
                    HStack { StatPill(title: "Série", value: "\(streak) jour\(streak > 1 ? "s" : "")"); StatPill(title: "Score", value: "\(score) XP") }
                }.padding(22).frame(maxWidth: .infinity, alignment: .leading).background(LinearGradient(colors: [.whappyInk, .whappyBlue.opacity(0.75)], startPoint: .topLeading, endPoint: .bottomTrailing)).clipShape(RoundedRectangle(cornerRadius: 26))
                Text("Choisir un jeu").font(.title3.bold()).foregroundStyle(Color.whappyInk)
                ForEach(games, id: \.0) { game in
                    Button { launchedGame = WapiIOSGameLaunch(name: game.0, subtitle: game.1); WapiSounds.gameMove(); WapiSounds.haptic(.medium) } label: {
                        HStack(spacing: 13) { Image(systemName: game.2).font(.title2).foregroundStyle(Color.whappyBlue).frame(width: 48, height: 48).background(Color.whappyBlue.opacity(0.1)).clipShape(RoundedRectangle(cornerRadius: 14)); VStack(alignment: .leading) { Text(game.0).font(.headline).foregroundStyle(Color.whappyInk); Text(game.1).font(.caption).foregroundStyle(.secondary) }; Spacer(); Text("JOUER  ›").font(.caption.bold()).foregroundStyle(Color.whappyBlue) }.padding(14).background(.white).clipShape(RoundedRectangle(cornerRadius: 18))
                    }.buttonStyle(.plain)
                }
            }.padding()
        }.background(Color.whappyBackground).navigationTitle("Jeux")
            .fullScreenCover(item: $launchedGame) { game in
                if game.name == "King QI" { KingQiIOSView() }
                else if ["Ludo WAPI", "Billard WAPI", "Échecs WAPI", "Jeu de dames", "Cartes WAPI", "Poker WAPI"].contains(game.name) { WapiIOSTabletopGame(game: game, score: $score, streak: $streak) }
                else { WapiIOSArcadeGame(game: game, score: $score, streak: $streak) }
            }
    }
}

private struct WapiIOSTabletopGame: View {
    @Environment(\.dismiss) private var dismiss
    let game: WapiIOSGameLaunch
    @Binding var score: Int
    @Binding var streak: Int
    @State private var turn = 1
    @State private var dieValue = 1
    @State private var status = "Touchez la table pour déplacer la caméra."

    private var primaryAction: String {
        switch game.name {
        case "Ludo WAPI": return "Lancer le dé"
        case "Billard WAPI": return "Frapper"
        case "Échecs WAPI", "Jeu de dames": return "Jouer le coup"
        default: return "Distribuer"
        }
    }

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color.whappyInk, Color.whappyBlue.opacity(0.78), Color.black], startPoint: .topLeading, endPoint: .bottomTrailing).ignoresSafeArea()
            VStack(spacing: 14) {
                HStack {
                    Button { dismiss() } label: { Label("Quitter", systemImage: "chevron.left") }.buttonStyle(.bordered).tint(.white)
                    Spacer()
                    VStack(spacing: 1) { Text(game.name.uppercased()).font(.caption.bold()).foregroundStyle(.cyan); Text("MANCHE \(turn)").font(.headline.bold()).foregroundStyle(.white) }
                    Spacer()
                    Text("\(score) XP").font(.headline.bold()).foregroundStyle(.white)
                }.padding(.horizontal)
                WapiIOS3DTabletop(scene: game.name, dieValue: dieValue).frame(maxWidth: .infinity).frame(height: 380).clipShape(RoundedRectangle(cornerRadius: 26)).padding(.horizontal)
                Text(status).font(.footnote).foregroundStyle(.white.opacity(0.75)).multilineTextAlignment(.center).padding(.horizontal)
                HStack(spacing: 12) {
                    Button { status = "Mode entraînement avec IA sélectionné."; WapiSounds.gameMove(); WapiSounds.haptic(.medium) } label: { Label("IA", systemImage: "brain.head.profile") }.buttonStyle(.bordered).tint(.white)
                    Button {
                        turn += 1; dieValue = Int.random(in: 1...6); score += 10; streak += 1
                        status = game.name == "Ludo WAPI" ? "Dé : \(dieValue). Choisissez un pion à déplacer." : game.name == "Billard WAPI" ? "Coup joué : ajustez la visée avec un glissement sur la table." : "Coup validé. À l’adversaire."
                        UINotificationFeedbackGenerator().notificationOccurred(.success); WapiSounds.gameMove()
                    } label: { Label(primaryAction, systemImage: game.name == "Ludo WAPI" ? "dice.fill" : "play.fill").frame(minWidth: 150) }.buttonStyle(.borderedProminent).tint(Color.whappyBlue)
                    Button { status = "Salon en ligne prêt : invitez vos contacts WAPI avec le bouton Partager." } label: { Image(systemName: "person.2.fill") }.buttonStyle(.bordered).tint(.white)
                }
                Text("Rendu 3D natif · contrôles tactiles · sons et vibrations du système. Les tournois et classements se synchronisent avec le service King QI/WAPI Play.").font(.caption2).foregroundStyle(.white.opacity(0.55)).multilineTextAlignment(.center).padding(.horizontal)
                Spacer(minLength: 8)
            }.padding(.top, 10)
        }
    }
}

private struct WapiIOS3DTabletop: UIViewRepresentable {
    let scene: String
    let dieValue: Int

    func makeUIView(context: Context) -> SCNView {
        let view = SCNView()
        view.scene = makeScene()
        view.allowsCameraControl = true
        view.autoenablesDefaultLighting = true
        view.backgroundColor = UIColor(red: 0.015, green: 0.05, blue: 0.11, alpha: 1)
        view.antialiasingMode = .multisampling4X
        return view
    }

    func updateUIView(_ view: SCNView, context: Context) {
        view.scene = makeScene()
    }

    private func makeScene() -> SCNScene {
        let sceneGraph = SCNScene()
        let camera = SCNNode(); camera.camera = SCNCamera(); camera.camera?.fieldOfView = 48; camera.position = SCNVector3(0, 6.7, 8.7); camera.eulerAngles = SCNVector3(-0.58, 0, 0); sceneGraph.rootNode.addChildNode(camera)
        let light = SCNNode(); light.light = SCNLight(); light.light?.type = .omni; light.light?.intensity = 1_250; light.position = SCNVector3(0, 6, 3); sceneGraph.rootNode.addChildNode(light)
        let floor = SCNFloor(); floor.reflectivity = 0.2; floor.firstMaterial?.diffuse.contents = UIColor.black; let floorNode = SCNNode(geometry: floor); floorNode.position.y = -0.42; sceneGraph.rootNode.addChildNode(floorNode)
        let board = SCNBox(width: 6.4, height: 0.35, length: 6.4, chamferRadius: 0.18); board.firstMaterial?.diffuse.contents = UIColor(red: 0.30, green: 0.16, blue: 0.06, alpha: 1); let boardNode = SCNNode(geometry: board); boardNode.position.y = -0.15; sceneGraph.rootNode.addChildNode(boardNode)
        if scene == "Billard WAPI" { addPool(to: sceneGraph); return sceneGraph }
        if scene == "Ludo WAPI" { addLudo(to: sceneGraph); return sceneGraph }
        if scene == "Échecs WAPI" || scene == "Jeu de dames" { addCheckerboard(to: sceneGraph, chess: scene == "Échecs WAPI"); return sceneGraph }
        addCards(to: sceneGraph)
        return sceneGraph
    }

    private func addCheckerboard(to sceneGraph: SCNScene, chess: Bool) {
        for row in 0..<8 { for column in 0..<8 {
            let tile = SCNBox(width: 0.72, height: 0.08, length: 0.72, chamferRadius: 0.02); tile.firstMaterial?.diffuse.contents = (row + column).isMultiple(of: 2) ? UIColor(red: 0.89, green: 0.72, blue: 0.48, alpha: 1) : UIColor(red: 0.20, green: 0.10, blue: 0.06, alpha: 1); let node = SCNNode(geometry: tile); node.position = SCNVector3(Float(column - 3) * 0.72, 0.08, Float(row - 3) * 0.72); sceneGraph.rootNode.addChildNode(node) } }
        for index in 0..<16 { let piece = SCNCylinder(radius: chess ? 0.20 : 0.24, height: chess ? 0.54 : 0.18); piece.firstMaterial?.diffuse.contents = index < 8 ? UIColor(white: 0.08, alpha: 1) : UIColor(white: 0.92, alpha: 1); let node = SCNNode(geometry: piece); let row = index < 8 ? index / 4 : 6 + index / 4; node.position = SCNVector3(Float((index % 4) * 2 - 3) * 0.72, chess ? 0.39 : 0.21, Float(row - 3) * 0.72); sceneGraph.rootNode.addChildNode(node) }
    }

    private func addLudo(to sceneGraph: SCNScene) {
        let colors: [UIColor] = [.systemRed, .systemGreen, .systemBlue, .systemYellow]
        for index in 0..<16 { let pawn = SCNCapsule(capRadius: 0.16, height: 0.52); pawn.firstMaterial?.diffuse.contents = colors[index / 4]; let node = SCNNode(geometry: pawn); let x: Float = (index % 4 < 2 ? -1.65 : 1.65) + Float(index % 2) * 0.45; let z: Float = index / 4 < 2 ? -1.65 : 1.65; node.position = SCNVector3(x, 0.35, z); sceneGraph.rootNode.addChildNode(node) }
        let die = SCNBox(width: 0.72, height: 0.72, length: 0.72, chamferRadius: 0.10); die.firstMaterial?.diffuse.contents = UIColor.white; let dieNode = SCNNode(geometry: die); dieNode.position = SCNVector3(0, 0.6, 0); dieNode.eulerAngles = SCNVector3(Float(dieValue) * 0.18, Float(dieValue) * 0.29, 0); sceneGraph.rootNode.addChildNode(dieNode)
    }

    private func addPool(to sceneGraph: SCNScene) {
        let cloth = SCNBox(width: 5.8, height: 0.16, length: 3.3, chamferRadius: 0.08); cloth.firstMaterial?.diffuse.contents = UIColor(red: 0.02, green: 0.33, blue: 0.24, alpha: 1); let clothNode = SCNNode(geometry: cloth); clothNode.position.y = 0.1; sceneGraph.rootNode.addChildNode(clothNode)
        for index in 0..<12 { let ball = SCNSphere(radius: 0.14); ball.firstMaterial?.diffuse.contents = index == 0 ? UIColor.white : [UIColor.systemRed, .systemYellow, .systemBlue, .systemOrange][index % 4]; let node = SCNNode(geometry: ball); node.position = SCNVector3(Float(index % 4 - 1) * 0.38, 0.34, Float(index / 4 - 1) * 0.36); sceneGraph.rootNode.addChildNode(node) }
    }

    private func addCards(to sceneGraph: SCNScene) { for index in 0..<5 { let card = SCNBox(width: 0.82, height: 0.05, length: 1.18, chamferRadius: 0.05); card.firstMaterial?.diffuse.contents = UIColor.white; let node = SCNNode(geometry: card); node.position = SCNVector3(Float(index - 2) * 0.92, 0.16, 0); node.eulerAngles.y = Float(index - 2) * 0.13; sceneGraph.rootNode.addChildNode(node) } }
}

private struct WapiIOSArcadeGame: View {
    @Environment(\.dismiss) private var dismiss
    let game: WapiIOSGameLaunch
    @Binding var score: Int
    @Binding var streak: Int
    @State private var answer: String?
    @State private var round = 1

    private var prompt: String {
        switch game.name {
        case "Duel WAPI": return "Quel outil WAPI permet de parler immédiatement avec un contact ?"
        case "Mots & idées": return "Quel espace rassemble vos émissions et podcasts ?"
        default: return "Quel espace WAPI permet de diffuser en direct ?"
        }
    }
    private var options: [String] {
        switch game.name {
        case "Duel WAPI": return ["Messages", "Marché", "Profil"]
        case "Mots & idées": return ["Actus", "Radio", "Business"]
        default: return ["Le Live", "Le Marché", "Les Services"]
        }
    }
    private var correct: String {
        switch game.name {
        case "Duel WAPI": return "Messages"
        case "Mots & idées": return "Radio"
        default: return "Le Live"
        }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(colors: [Color.whappyInk, Color.whappyBlue.opacity(0.82)], startPoint: .topLeading, endPoint: .bottomTrailing).ignoresSafeArea()
                VStack(spacing: 18) {
                    HStack { Text("MANCHE \(round)").font(.caption.bold()).foregroundStyle(.white.opacity(0.7)); Spacer(); Text("\(score) XP").font(.headline.bold()).foregroundStyle(.white) }
                    VStack(alignment: .leading, spacing: 9) {
                        Text(game.name.uppercased()).font(.caption.bold()).foregroundStyle(.cyan)
                        Text(prompt).font(.system(size: 27, weight: .black, design: .rounded)).foregroundStyle(.white)
                        Text(game.subtitle).font(.footnote).foregroundStyle(.white.opacity(0.66))
                    }.frame(maxWidth: .infinity, alignment: .leading)
                    ForEach(options, id: \.self) { option in
                        Button {
                            guard answer == nil else { return }
                            answer = option
                            if option == correct { score += 25; streak += 1; UINotificationFeedbackGenerator().notificationOccurred(.success); WapiSounds.gameReward() }
                            else { UINotificationFeedbackGenerator().notificationOccurred(.error); WapiSounds.gameMove() }
                        } label: {
                            HStack { Text(option).font(.headline); Spacer(); if answer == option { Image(systemName: option == correct ? "checkmark.circle.fill" : "xmark.circle.fill") } }
                                .foregroundStyle(answer == option ? .white : Color.whappyInk)
                                .padding(17)
                                .background(answer == option ? (option == correct ? Color.green : Color.red) : Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 17))
                        }.buttonStyle(.plain)
                    }
                    if let answer {
                        Text(answer == correct ? "Bonne réponse · +25 XP" : "La bonne réponse était : \(correct)").font(.headline).foregroundStyle(.white)
                        Button { round += 1; self.answer = nil; WapiSounds.gameMove(); WapiSounds.haptic(.light) } label: { Label("Manche suivante", systemImage: "play.fill").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent).tint(.white).foregroundStyle(Color.whappyBlue)
                    }
                    Spacer()
                }.padding(20)
            }
            .navigationTitle(game.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button { dismiss() } label: { Label("Quitter", systemImage: "chevron.left") }.foregroundStyle(.white) } }
            .toolbarBackground(Color.whappyInk, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
        }
    }
}

private struct KingQiIOSQuestion {
    let category: String
    let difficulty: String
    let prompt: String
    let options: [String]
    let answer: Int
}

private let kingQiIOSQuestions = [
    KingQiIOSQuestion(category: "GÉOGRAPHIE", difficulty: "FACILE", prompt: "Quelle est la capitale du Japon ?", options: ["Séoul", "Tokyo", "Pékin", "Bangkok"], answer: 1),
    KingQiIOSQuestion(category: "SCIENCES", difficulty: "FACILE", prompt: "Quelle est la formule chimique de l'eau ?", options: ["CO2", "O2", "H2O", "NaCl"], answer: 2),
    KingQiIOSQuestion(category: "LOGIQUE", difficulty: "FACILE", prompt: "Quel est le premier nombre premier ?", options: ["0", "1", "2", "3"], answer: 2),
    KingQiIOSQuestion(category: "GÉOGRAPHIE", difficulty: "MOYEN", prompt: "Quel est le plus grand océan du monde ?", options: ["Atlantique", "Indien", "Arctique", "Pacifique"], answer: 3),
    KingQiIOSQuestion(category: "SCIENCES", difficulty: "MOYEN", prompt: "Quel processus permet aux plantes de transformer la lumière en énergie ?", options: ["Respiration", "Photosynthèse", "Fermentation", "Osmose"], answer: 1),
    KingQiIOSQuestion(category: "TECHNOLOGIE", difficulty: "EXPERT", prompt: "Que signifie l'acronyme GPS ?", options: ["Global Positioning System", "General Public Signal", "Geo Personal Service", "Global Phone Sync"], answer: 0),
    KingQiIOSQuestion(category: "HISTOIRE", difficulty: "EXPERT", prompt: "Dans quelle civilisation les pyramides de Gizeh ont-elles été construites ?", options: ["Romaine", "Maya", "Égyptienne", "Perse"], answer: 2),
    KingQiIOSQuestion(category: "GÉOGRAPHIE", difficulty: "EXPERT", prompt: "Quel fleuve traverse Brazzaville et Kinshasa ?", options: ["Nil", "Congo", "Niger", "Zambèze"], answer: 1),
    KingQiIOSQuestion(category: "HISTOIRE", difficulty: "FACILE", prompt: "Quel mur historique se trouve en Chine ?", options: ["Mur d'Hadrien", "Grande Muraille", "Mur des Lamentations", "Mur de Berlin"], answer: 1),
    KingQiIOSQuestion(category: "LANGUES", difficulty: "FACILE", prompt: "Quel mot signifie « bonjour » en espagnol ?", options: ["Ciao", "Hello", "Hola", "Olá"], answer: 2),
    KingQiIOSQuestion(category: "NATURE", difficulty: "FACILE", prompt: "Quel animal est le plus grand mammifère du monde ?", options: ["Éléphant", "Baleine bleue", "Girafe", "Requin-baleine"], answer: 1),
    KingQiIOSQuestion(category: "SPORT", difficulty: "FACILE", prompt: "Combien de joueurs une équipe de football aligne-t-elle sur le terrain ?", options: ["9", "10", "11", "12"], answer: 2),
    KingQiIOSQuestion(category: "LOGIQUE", difficulty: "MOYEN", prompt: "Quel nombre complète la suite : 3, 6, 12, 24, ... ?", options: ["36", "42", "48", "54"], answer: 2),
    KingQiIOSQuestion(category: "SCIENCES", difficulty: "MOYEN", prompt: "Quelle planète est connue comme la planète rouge ?", options: ["Mars", "Vénus", "Jupiter", "Mercure"], answer: 0),
    KingQiIOSQuestion(category: "GÉOGRAPHIE", difficulty: "MOYEN", prompt: "Sur quel continent se trouve le Congo ?", options: ["Asie", "Afrique", "Europe", "Amérique du Sud"], answer: 1),
    KingQiIOSQuestion(category: "CULTURE", difficulty: "MOYEN", prompt: "Combien de cordes possède une guitare classique ?", options: ["4", "5", "6", "7"], answer: 2),
    KingQiIOSQuestion(category: "TECHNOLOGIE", difficulty: "MOYEN", prompt: "Quel composant stocke temporairement les données d'un téléphone ?", options: ["RAM", "Écran", "Micro", "Haut-parleur"], answer: 0),
    KingQiIOSQuestion(category: "LANGUES", difficulty: "MOYEN", prompt: "Quelle langue est majoritaire au Brésil ?", options: ["Espagnol", "Portugais", "Français", "Anglais"], answer: 1),
    KingQiIOSQuestion(category: "HISTOIRE", difficulty: "MOYEN", prompt: "Quelle ville était ensevelie par le Vésuve en 79 ?", options: ["Pompéi", "Athènes", "Carthage", "Rome"], answer: 0),
    KingQiIOSQuestion(category: "SPORT", difficulty: "MOYEN", prompt: "Combien de cases compte un échiquier ?", options: ["36", "49", "64", "81"], answer: 2),
    KingQiIOSQuestion(category: "NATURE", difficulty: "MOYEN", prompt: "Quel gaz les plantes absorbent-elles principalement ?", options: ["Oxygène", "Dioxyde de carbone", "Hélium", "Azote"], answer: 1),
    KingQiIOSQuestion(category: "LOGIQUE", difficulty: "EXPERT", prompt: "Si 5 machines produisent 5 pièces en 5 minutes, combien de minutes faut-il à 100 machines pour produire 100 pièces ?", options: ["5", "20", "100", "500"], answer: 0),
    KingQiIOSQuestion(category: "SCIENCES", difficulty: "EXPERT", prompt: "Quelle unité mesure une fréquence ?", options: ["Watt", "Pascal", "Hertz", "Joule"], answer: 2),
    KingQiIOSQuestion(category: "GÉOGRAPHIE", difficulty: "EXPERT", prompt: "Quel détroit sépare l'Europe et l'Afrique ?", options: ["Béring", "Gibraltar", "Malacca", "Ormuz"], answer: 1),
    KingQiIOSQuestion(category: "TECHNOLOGIE", difficulty: "EXPERT", prompt: "Quelle pratique chiffre des données sans pouvoir les modifier ?", options: ["Hachage", "Compression", "Indexation", "Cache"], answer: 0),
    KingQiIOSQuestion(category: "CULTURE", difficulty: "EXPERT", prompt: "Qui a écrit « Le Petit Prince » ?", options: ["Victor Hugo", "Albert Camus", "Antoine de Saint-Exupéry", "Jules Verne"], answer: 2),
    KingQiIOSQuestion(category: "AFRIQUE", difficulty: "EXPERT", prompt: "Quel fleuve est le deuxième plus long d'Afrique après le Nil ?", options: ["Congo", "Niger", "Zambèze", "Orange"], answer: 0),
]

private enum KingQiIOSSection { case home, solo, online }

private struct KingQiIOSView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WhappyStore
    @State private var section: KingQiIOSSection = .home
    @State private var credits = 0
    @State private var trophies = 0
    @State private var victories = 0

    var body: some View {
        NavigationStack {
            Group {
                switch section {
                case .home: home
                case .solo: KingQiIOSSoloView { section = .home }
                case .online: KingQiIOSOnlineView(onExit: { section = .home }, onStartLive: { store.selectedTab = .live; dismiss() })
                }
            }
            .background(Color.whappyBackground.ignoresSafeArea())
            .toolbar { ToolbarItem(placement: .topBarLeading) { if section == .home { Button("Fermer") { dismiss() } } } }
        }
        .task { loadProfile() }
    }

    private var home: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 15) {
                VStack(alignment: .leading, spacing: 14) {
                    HStack { Text("♛").font(.system(size: 40)).frame(width: 58, height: 58).background(Color.yellow).clipShape(Circle()); VStack(alignment: .leading) { Text("KING QI").font(.system(size: 31, weight: .black)); Text("La connaissance devient un spectacle.").font(.caption).foregroundStyle(.white.opacity(0.7)) } }
                    HStack { kingStat("CRÉDITS", "\(credits)"); kingStat("TROPHÉES", "\(trophies)"); kingStat("NIVEAU", "National") }
                }.foregroundStyle(.white).padding(22).frame(maxWidth: .infinity, alignment: .leading).background(Color(red: 0.03, green: 0.11, blue: 0.25)).clipShape(RoundedRectangle(cornerRadius: 28))
                KingQiIOSPlayerProfileCard(displayName: Auth.auth().currentUser?.displayName ?? Auth.auth().currentUser?.phoneNumber ?? "Joueur WAPI", photoURL: Auth.auth().currentUser?.photoURL, victories: victories, trophies: trophies)
                Text("Choisissez votre arène").font(.title2.bold())
                KingQiIOSMode(title: "SOLO IA", subtitle: "La voix King QI pose les questions. Répondez au micro ou touchez une barre.", icon: "mic.fill", color: .orange) { section = .solo }
                KingQiIOSMode(title: "DUEL WAPI", subtitle: "Deux écrans, une manche synchronisée et un seul champion.", icon: "bolt.horizontal.fill", color: .red) { section = .online }
                KingQiIOSMode(title: "TOURNOI AVEC CONTACTS", subtitle: "Créez un code, invitez 2 à 4 proches et gagnez des trophées.", icon: "person.3.fill", color: .whappyBlue) { section = .online }
                KingQiIOSMode(title: "KING QI EN DIRECT", subtitle: "Créez l'arène puis associez-la à votre direct WAPI.", icon: "video.fill", color: .red) { section = .online }
                Text("Les crédits King QI sont promotionnels, non achetables et non convertibles en argent. Les mises réelles restent verrouillées jusqu’aux autorisations légales et au KYC.").font(.footnote).foregroundStyle(.secondary).padding().background(Color.yellow.opacity(0.12)).clipShape(RoundedRectangle(cornerRadius: 18))
            }.padding()
        }
    }

    private func kingStat(_ title: String, _ value: String) -> some View { VStack(alignment: .leading) { Text(title).font(.system(size: 9, weight: .bold)).foregroundStyle(.white.opacity(0.55)); Text(value).font(.subheadline.bold()).lineLimit(1) }.padding(10).frame(maxWidth: .infinity, alignment: .leading).background(.white.opacity(0.09)).clipShape(RoundedRectangle(cornerRadius: 13)) }

    private func loadProfile() {
        guard Auth.auth().currentUser != nil else { return }
        Functions.functions(region: "europe-west1").httpsCallable("kingQiGetProfile").call { result, _ in
            guard let data = result?.data as? [String: Any] else { return }
            credits = data["credits"] as? Int ?? (data["credits"] as? NSNumber)?.intValue ?? 0
            trophies = data["trophies"] as? Int ?? (data["trophies"] as? NSNumber)?.intValue ?? 0
            victories = data["victories"] as? Int ?? (data["victories"] as? NSNumber)?.intValue ?? 0
        }
    }
}

private struct KingQiIOSPlayerProfileCard: View {
    let displayName: String
    let photoURL: URL?
    let victories: Int
    let trophies: Int

    var body: some View {
        HStack(spacing: 12) {
            if let photoURL {
                WapiCachedRemoteImage(url: photoURL) { InitialsAvatar(text: displayName, size: 48) }.frame(width: 48, height: 48).clipShape(Circle())
            } else {
                InitialsAvatar(text: displayName.isEmpty ? "W" : displayName, size: 48)
            }
            VStack(alignment: .leading, spacing: 3) {
                Text("PROFIL JOUEUR · KING QI").font(.caption2.bold()).foregroundStyle(Color.whappyBlue)
                Text(displayName.isEmpty ? "Joueur WAPI" : displayName).font(.headline.bold())
                Text("\(victories) victoire\(victories == 1 ? "" : "s") · \(trophies) coupe\(trophies == 1 ? "" : "s")").font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Text("♛").font(.system(size: 28)).foregroundStyle(.yellow)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white)
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.whappyBlue.opacity(0.14)))
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

private struct KingQiIOSMode: View {
    let title: String; let subtitle: String; let icon: String; let color: Color; let action: () -> Void
    var body: some View { Button(action: action) { HStack(spacing: 13) { Image(systemName: icon).font(.title2).foregroundStyle(color).frame(width: 54, height: 54).background(color.opacity(0.12)).clipShape(RoundedRectangle(cornerRadius: 17)); VStack(alignment: .leading, spacing: 4) { Text(title).font(.headline).foregroundStyle(Color.whappyInk); Text(subtitle).font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.leading) }; Spacer(); Image(systemName: "chevron.right").foregroundStyle(color) }.padding(15).background(.white).clipShape(RoundedRectangle(cornerRadius: 20)) }.buttonStyle(.plain) }
}

@MainActor
private final class KingQiSpeechController: ObservableObject {
    @Published var transcript = ""
    @Published var listening = false
    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "fr-FR"))
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    func start() {
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async { if status == .authorized { self.begin() } }
        }
    }

    private func begin() {
        stop(); transcript = ""
        let request = SFSpeechAudioBufferRecognitionRequest(); request.shouldReportPartialResults = true; self.request = request
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.record, mode: .measurement, options: .duckOthers)
        try? session.setActive(true, options: .notifyOthersOnDeactivation)
        let node = engine.inputNode; let format = node.outputFormat(forBus: 0)
        node.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in request.append(buffer) }
        engine.prepare(); try? engine.start(); listening = true
        task = recognizer?.recognitionTask(with: request) { [weak self] result, error in
            guard let self else { return }
            if let result { self.transcript = result.bestTranscription.formattedString }
            if error != nil || result?.isFinal == true { self.stop() }
        }
    }

    func stop() {
        if engine.isRunning { engine.stop(); engine.inputNode.removeTap(onBus: 0) }
        request?.endAudio(); task?.cancel(); request = nil; task = nil; listening = false
    }
}

private struct KingQiIOSSoloView: View {
    let onExit: () -> Void
    @StateObject private var speech = KingQiSpeechController()
    @State private var questions = kingQiIOSQuestions.shuffled()
    @State private var index = 0
    @State private var selected: Int?
    @State private var score = 0
    @State private var seconds = 15
    @State private var finished = false
    private let voice = AVSpeechSynthesizer()
    private var question: KingQiIOSQuestion { questions[min(index, questions.count - 1)] }

    var body: some View {
        Group {
            if finished { result }
            else { ScrollView { VStack(spacing: 14) {
                HStack { Button("Quitter", action: onExit).buttonStyle(.bordered); Spacer(); Text("\(index + 1)/\(questions.count)").bold(); Text("\(seconds)").font(.headline.bold()).frame(width: 48, height: 48).background(seconds <= 5 ? Color.red : Color.yellow).clipShape(Circle()).foregroundStyle(seconds <= 5 ? .white : Color.whappyInk) }
                VStack(alignment: .leading, spacing: 14) { HStack { Text(question.category).font(.caption.bold()).foregroundStyle(.yellow); Spacer(); Text(question.difficulty).font(.caption.bold()).foregroundStyle(.white.opacity(0.6)) }; Text(question.prompt).font(.system(size: 25, weight: .black)).foregroundStyle(.white); Text("SCORE  \(score)").font(.caption.bold()).foregroundStyle(.white.opacity(0.65)) }.padding(22).frame(maxWidth: .infinity, alignment: .leading).background(Color(red: 0.03, green: 0.11, blue: 0.25)).clipShape(RoundedRectangle(cornerRadius: 28))
                ForEach(question.options.indices, id: \.self) { option in kingAnswer(option) }
                Button { speech.start() } label: { Label(speech.listening ? "Je vous écoute…" : "Répondre avec ma voix", systemImage: "mic.fill").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent).tint(speech.listening ? .red : .whappyBlue).disabled(selected != nil)
                if let selected { Text(selected == question.answer ? "Bonne réponse" : "Réponse : \(question.options[question.answer])").font(.headline).foregroundStyle(selected == question.answer ? .green : .red); Button(index == questions.count - 1 ? "Voir mon résultat" : "Question suivante") { if index == questions.count - 1 { finished = true } else { index += 1 } }.buttonStyle(.borderedProminent).tint(Color.whappyInk).frame(maxWidth: .infinity) }
            }.padding() } }
        }
        .onAppear { beginQuestion() }
        .onChange(of: index) { _, _ in beginQuestion() }
        .onChange(of: speech.transcript) { _, value in matchVoice(value) }
        .onDisappear { speech.stop(); voice.stopSpeaking(at: .immediate) }
    }

    private func kingAnswer(_ option: Int) -> some View {
        let revealed = selected != nil
        let color: Color = revealed && option == question.answer ? .green : (selected == option ? .red : Color.gray.opacity(0.22))
        return Button { choose(option) } label: { HStack { Text(["A", "B", "C", "D"][option]).font(.headline.bold()).foregroundStyle(.white).frame(width: 38, height: 38).background(revealed ? color : Color.whappyInk).clipShape(RoundedRectangle(cornerRadius: 11)); Text(question.options[option]).font(.headline).foregroundStyle(Color.whappyInk); Spacer() }.padding(13).background(revealed && option == question.answer ? Color.green.opacity(0.1) : .white).overlay(RoundedRectangle(cornerRadius: 17).stroke(color, lineWidth: 1.5)).clipShape(RoundedRectangle(cornerRadius: 17)) }.buttonStyle(.plain).disabled(revealed)
    }

    private var result: some View { ZStack { Color(red: 0.03, green: 0.11, blue: 0.25).ignoresSafeArea(); VStack(spacing: 14) { Text("🏆").font(.system(size: 86)); Text("GRAND CHAMPION").font(.largeTitle).fontWeight(.black).foregroundStyle(.yellow); Text("\(score) points").foregroundStyle(.white.opacity(0.75)); Button("Rejouer") { questions.shuffle(); index = 0; score = 0; finished = false; beginQuestion() }.buttonStyle(.borderedProminent).tint(.yellow).foregroundStyle(Color.whappyInk); Button("Retour aux jeux", action: onExit).foregroundStyle(.white) } } }

    private func beginQuestion() {
        selected = nil; seconds = 15; speech.transcript = ""
        voice.stopSpeaking(at: .immediate); let utterance = AVSpeechUtterance(string: question.prompt); utterance.voice = AVSpeechSynthesisVoice(language: "fr-FR"); voice.speak(utterance)
        Task { while seconds > 0 && selected == nil && !finished { try? await Task.sleep(for: .seconds(1)); if selected == nil { seconds -= 1 } }; if seconds == 0 && selected == nil { selected = -1 } }
    }

    private func choose(_ option: Int) { guard selected == nil else { return }; selected = option; speech.stop(); if option == question.answer { score += 500 + seconds * 25 } }
    private func matchVoice(_ value: String) { let heard = normalized(value); guard selected == nil, !heard.isEmpty else { return }; if let option = question.options.firstIndex(where: { let candidate = normalized($0); return heard.contains(candidate) || candidate.contains(heard) }) { choose(option) } }
    private func normalized(_ value: String) -> String { value.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current).lowercased().components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }.joined(separator: " ") }
}

private struct KingQiIOSOnlineView: View {
    let onExit: () -> Void
    let onStartLive: () -> Void
    @State private var code = ""
    @State private var entry = 10
    @State private var maxPlayers = 2
    @State private var visibility = "private"
    @State private var roomID = ""
    @State private var room: [String: Any] = [:]
    @State private var busy = false
    @State private var error: String?
    @State private var listener: ListenerRegistration?
    private let functions = Functions.functions(region: "europe-west1")

    var body: some View { ScrollView { VStack(spacing: 14) {
        HStack { Button("Retour", action: onExit).buttonStyle(.bordered); Spacer(); Text("KING QI SOCIAL").font(.headline.bold()) }
        if roomID.isEmpty { setup } else { roomView }
        if let error { Text(error).font(.footnote).foregroundStyle(.red) }
    }.padding() }.onDisappear { listener?.remove() } }

    private var setup: some View { Group {
        VStack(alignment: .leading, spacing: 10) { Text(maxPlayers == 2 ? "Créer un duel" : "Créer un tournoi").font(.title2.bold()); Text(maxPlayers == 2 ? "Affrontez un contact dans une arène à deux joueurs." : "Crédits promotionnels par joueur · jusqu’à quatre écrans.").font(.caption).foregroundStyle(.secondary); Picker("Format", selection: $maxPlayers) { Text("Duel · 2 joueurs").tag(2); Text("Tournoi · 4 joueurs").tag(4) }.pickerStyle(.segmented); HStack { ForEach([0, 10, 25, 50], id: \.self) { value in Button("\(value)") { entry = value }.buttonStyle(.borderedProminent).tint(entry == value ? .yellow : .gray) } }; Picker("Diffusion", selection: $visibility) { Text("Contacts").tag("private"); Text("Direct WAPI").tag("live") }.pickerStyle(.segmented); Button { call("kingQiCreateTournament", ["entryCredits": entry, "maxPlayers": maxPlayers, "visibility": visibility]) { watch($0["roomId"] as? String ?? "") } } label: { Label(maxPlayers == 2 ? "Créer le duel" : "Créer le tournoi", systemImage: "play.fill").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent).tint(Color.whappyInk) }.padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 22))
        Text("OU REJOINDRE").font(.caption.bold()).foregroundStyle(.secondary)
        TextField("Code à 6 caractères", text: $code).textInputAutocapitalization(.characters).autocorrectionDisabled().padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 15)).onChange(of: code) { _, value in code = String(value.filter { $0.isLetter || $0.isNumber }.prefix(6)).uppercased() }
        Button("Rejoindre l'arène") { call("kingQiJoinTournament", ["code": code]) { watch($0["roomId"] as? String ?? "") } }.buttonStyle(.borderedProminent).disabled(code.count != 6 || busy)
    } }

    private var roomView: some View {
        let status = room["status"] as? String ?? "waiting"
        let playerIDs = room["playerIds"] as? [String] ?? []
        let names = room["playerNames"] as? [String: String] ?? [:]
        let photos = room["playerPhotos"] as? [String: String] ?? [:]
        let scores = room["scores"] as? [String: NSNumber] ?? [:]
        let answeredIDs = room["answeredIds"] as? [String] ?? []
        let uid = Auth.auth().currentUser?.uid ?? ""
        return VStack(spacing: 12) {
            Text("CODE  \(room["code"] as? String ?? "")").font(.title2.bold())
            let capacity = (room["maxPlayers"] as? NSNumber)?.intValue ?? (room["maxPlayers"] as? Int ?? maxPlayers)
            Text(status == "waiting" ? "\(playerIDs.count)/\(capacity) joueurs dans l’arène" : status == "finished" ? (capacity == 2 ? "Duel terminé" : "Tournoi terminé") : "King QI en cours").font(.title.bold()).foregroundStyle(.white).padding().frame(maxWidth: .infinity).background(Color.whappyInk).clipShape(RoundedRectangle(cornerRadius: 22))
            KingQiIOSPlayerStage(playerIDs: playerIDs, names: names, photos: photos, scores: scores, answeredIDs: answeredIDs, currentUserID: uid, capacity: capacity)
            if status == "waiting", room["hostId"] as? String == uid { Button("Démarrer King QI") { call("kingQiStartTournament", ["roomId": roomID]) }.buttonStyle(.borderedProminent).disabled(playerIDs.count < 2 || busy) }
            if status == "waiting", room["hostId"] as? String == uid, room["visibility"] as? String == "live" { Button { call("kingQiPrepareLive", ["roomId": roomID]) { _ in onStartLive() } } label: { Label("Ouvrir mon direct WAPI", systemImage: "video.fill") }.buttonStyle(.bordered) }
            if status == "playing", let question = room["currentQuestion"] as? [String: Any], let options = question["options"] as? [String] { Text(question["text"] as? String ?? "Question").font(.title2.bold()); ForEach(options.indices, id: \.self) { option in Button(options[option]) { call("kingQiSubmitAnswer", ["roomId": roomID, "optionIndex": option]) }.buttonStyle(.bordered).frame(maxWidth: .infinity) }; Button("Manche suivante") { call("kingQiAdvanceTournament", ["roomId": roomID]) }.buttonStyle(.borderedProminent) }
            if status == "finished" { Text(((room["winners"] as? [String])?.contains(uid) == true) ? "🏆 GRAND CHAMPION" : "Tournoi terminé").font(.largeTitle).fontWeight(.black).foregroundStyle(.orange) }
        }
    }

    private func watch(_ id: String) { guard !id.isEmpty else { return }; roomID = id; listener?.remove(); listener = Firestore.firestore().collection("kingQiRooms").document(id).addSnapshotListener { snapshot, failure in if let failure { error = wapiUserFacingError(failure, action: "La synchronisation King QI") } else { room = snapshot?.data() ?? [:] } } }
    private func call(_ name: String, _ data: [String: Any], completion: @escaping ([String: Any]) -> Void = { _ in }) { guard !busy else { return }; busy = true; error = nil; functions.httpsCallable(name).call(data) { result, failure in busy = false; if let failure { error = wapiUserFacingError(failure, action: "King QI") } else { completion(result?.data as? [String: Any] ?? [:]) } } }
}

private struct KingQiIOSPlayerStage: View {
    let playerIDs: [String]
    let names: [String: String]
    let photos: [String: String]
    let scores: [String: NSNumber]
    let answeredIDs: [String]
    let currentUserID: String
    let capacity: Int

    private var slots: [String?] { (0..<min(max(capacity, 2), 4)).map { index in playerIDs.indices.contains(index) ? playerIDs[index] : nil } }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(capacity == 2 ? "DUEL · 2 ÉCRANS" : "ARÈNE · 4 ÉCRANS")
                .font(.caption2.bold())
                .foregroundStyle(Color.whappyBlue)
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                ForEach(Array(slots.enumerated()), id: \.offset) { _, player in
                    let name = player.flatMap { names[$0] } ?? "En attente"
                    let score = player.flatMap { scores[$0]?.intValue } ?? 0
                    let answered = player.map { answeredIDs.contains($0) } ?? false
                    HStack(spacing: 8) {
                        if let player, let photo = photos[player], !photo.isEmpty {
                            WapiCachedRemoteImage(url: URL(string: photo)) { InitialsAvatar(text: name, size: 40) }.frame(width: 40, height: 40).clipShape(Circle())
                        } else {
                            InitialsAvatar(text: name, size: 40)
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(name).font(.caption.bold()).lineLimit(1)
                            Text("\(score) pts").font(.subheadline.bold()).foregroundStyle(Color.whappyBlue)
                            Text(player == nil ? "En attente" : answered ? "Réponse reçue" : "Réfléchit…")
                                .font(.caption2)
                                .foregroundStyle(answered ? .green : .secondary)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(9)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(player == currentUserID ? Color.whappyBlue.opacity(0.10) : .white)
                    .overlay(RoundedRectangle(cornerRadius: 17).stroke(player == currentUserID ? Color.whappyBlue.opacity(0.35) : Color.gray.opacity(0.12)))
                    .clipShape(RoundedRectangle(cornerRadius: 17))
                }
            }
        }
    }
}

private struct StatPill: View {
    let title: String; let value: String
    var body: some View { VStack(alignment: .leading, spacing: 2) { Text(title.uppercased()).font(.caption2.bold()).foregroundStyle(.white.opacity(0.65)); Text(value).font(.subheadline.bold()).foregroundStyle(.white) }.padding(.horizontal, 11).padding(.vertical, 8).background(.white.opacity(0.13)).clipShape(RoundedRectangle(cornerRadius: 10)) }
}

struct ServicesView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var requestType: ServiceKind?; @State private var message: String?
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("PAIEMENTS WAPI").font(.caption.bold()).foregroundStyle(Color.whappyBlue)
                    Text(store.walletTransactions.isEmpty ? "Fournisseur à connecter" : "\(store.walletBalance.formatted()) FCFA").font(.largeTitle.bold()).foregroundStyle(.white)
                    Text(store.walletTransactions.isEmpty ? "Aucun solde fictif : les paiements apparaîtront uniquement après confirmation d’un fournisseur sécurisé." : "Solde calculé à partir des transactions confirmées.").font(.caption).foregroundStyle(.white.opacity(0.72))
                    if !store.walletTransactions.isEmpty {
                        ShareLink(item: URL(string: "https://whappy.chat/pay/\(whappyFounderPaySlug)")!) { Label("Partager mon lien", systemImage: "qrcode") }.buttonStyle(.bordered)
                    }
                }.padding(22).frame(maxWidth: .infinity, alignment: .leading).background(Color.whappyInk).clipShape(RoundedRectangle(cornerRadius: 26))
                if let message { Label(message, systemImage: "checkmark.circle.fill").foregroundStyle(.green).padding(12).frame(maxWidth: .infinity, alignment: .leading).background(.white).clipShape(RoundedRectangle(cornerRadius: 14)) }
                Text("Services à la demande").font(.title3.bold())
                HStack { ServiceButton(title: "Transport", icon: "car.fill") { requestType = .transport }; ServiceButton(title: "Livraison", icon: "shippingbox.fill") { requestType = .delivery }; ServiceButton(title: "Assistance", icon: "cross.case.fill") { requestType = .help } }
                NavigationLink { OrdersView() } label: { Label("Mes commandes et livraisons", systemImage: "shippingbox.and.arrow.backward.fill").frame(maxWidth: .infinity, alignment: .leading).padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 16)) }
                NavigationLink { BusinessWorkspaceView() } label: { Label(store.business == nil ? "Créer mon espace Business" : "Piloter \(store.business?.name ?? "mon activité")", systemImage: "briefcase.fill").frame(maxWidth: .infinity, alignment: .leading).padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 16)) }
                if !store.serviceRequests.isEmpty { Text("Demandes récentes").font(.title3.bold()); ForEach(store.serviceRequests.prefix(5)) { request in HStack { Image(systemName: "checkmark.circle.fill").foregroundStyle(.green); VStack(alignment: .leading) { Text(request.type).font(.headline); Text(request.details).font(.caption).foregroundStyle(.secondary).lineLimit(2) }; Spacer(); Text(request.status).font(.caption.bold()).foregroundStyle(.orange) }.padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 16)) } }
                if !store.walletTransactions.isEmpty { Text("Historique").font(.title3.bold()); ForEach(store.walletTransactions) { transaction in HStack { Image(systemName: transaction.amount >= 0 ? "arrow.down.circle.fill" : "arrow.up.circle.fill").foregroundStyle(transaction.amount >= 0 ? .green : Color.whappyBlue); VStack(alignment: .leading) { Text(transaction.label).font(.headline); Text(transaction.date, style: .date).font(.caption).foregroundStyle(.secondary) }; Spacer(); Text("\(transaction.amount > 0 ? "+" : "")\(transaction.amount.formatted()) FCFA").font(.subheadline.bold()) }.padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 16)) } }
            }.padding()
        }
        .background(Color.whappyBackground).navigationTitle("Services")
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

private struct ServiceRequestView: View {
    @Environment(\.dismiss) private var dismiss; @EnvironmentObject private var store: WhappyStore; let type: String; let onResult: (String) -> Void; @State private var details = ""
    var body: some View { NavigationStack { Form { Section(type) { TextField(type == "Transport" ? "Départ et destination" : "Décrivez votre besoin", text: $details, axis: .vertical).lineLimit(3...6) }; Section { Text("La demande est enregistrée sur l’appareil et reste visible dans Services.").font(.footnote).foregroundStyle(.secondary) } }.navigationTitle("Demande").toolbar { ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Envoyer") { store.createServiceRequest(type: type, details: details); onResult("Demande \(type) enregistrée."); dismiss() }.disabled(details.count < 5) } } } }
}

private struct OrdersView: View {
    @EnvironmentObject private var store: WhappyStore
    var body: some View { List { if store.orders.isEmpty { ContentUnavailableView("Aucune commande", systemImage: "shippingbox", description: Text("Validez un panier dans le Marché.")) } else { ForEach(store.orders) { order in Section(order.reference) { Text(order.lines.map { "\($0.quantity) × \($0.listing.title)" }.joined(separator: "\n")); Label(order.delivery, systemImage: "mappin.and.ellipse"); Label(order.status, systemImage: "clock.fill").foregroundStyle(.orange) } } } }.navigationTitle("Commandes") }
}

private struct BusinessWorkspaceView: View {
    @EnvironmentObject private var store: WhappyStore

    private var businessMessages: [Conversation] { store.conversations.filter { $0.profileType == "business" } }
    private var unreadClients: Int { businessMessages.filter(\.unread).count }
    private var profileCompletion: Int {
        guard let business = store.business else { return 0 }
        let values = [business.logoURL, business.name, business.category, business.bio, business.city, business.phone, business.website]
        return values.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }.count * 100 / values.count
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                businessHero

                HStack(spacing: 10) {
                    BusinessMetricIOS(title: "Clients", value: "\(businessMessages.count)", detail: unreadClients == 0 ? "À jour" : "\(unreadClients) nouveau(x)")
                    BusinessMetricIOS(title: "Commandes", value: "\(store.orders.count)", detail: "Données réelles")
                    BusinessMetricIOS(title: "Profil", value: "\(profileCompletion) %", detail: profileCompletion == 100 ? "Complet" : "À compléter")
                }

                Text("Piloter l’activité").font(.title3.weight(.bold)).foregroundStyle(Color.whappyInk)

                Button {
                    store.switchAccount(business: true)
                    store.selectedTab = .messages
                } label: {
                    BusinessOperationIOS(icon: "bubble.left.and.bubble.right.fill", title: "Messagerie Business", detail: "Répondre aux clients au nom de l’entreprise", badge: unreadClients > 0 ? "\(unreadClients)" : nil)
                }
                .buttonStyle(.plain)

                NavigationLink { BusinessSaleRoomManagerIOS() } label: {
                    BusinessOperationIOS(icon: "person.3.sequence.fill", title: "Ventes privées", detail: "Présenter les produits en direct dans tout WAPI")
                }
                .buttonStyle(.plain)

                NavigationLink { OrdersView() } label: {
                    BusinessOperationIOS(icon: "shippingbox.fill", title: "Commandes", detail: "Suivre les achats et les livraisons réelles")
                }
                .buttonStyle(.plain)

                NavigationLink { BusinessCampaignIOSView() } label: {
                    BusinessOperationIOS(icon: "megaphone.fill", title: "WAPI Ads", detail: "Préparer une campagne ciblée par région")
                }
                .buttonStyle(.plain)

                NavigationLink { BusinessEditorView() } label: {
                    BusinessOperationIOS(icon: "building.2.crop.circle.fill", title: "Identité professionnelle", detail: "Logo, activité, ville, présentation et contacts")
                }
                .buttonStyle(.plain)

                Text("Le compte personnel et le compte Business utilisent la même connexion WAPI, mais gardent une identité publique, une messagerie et des opérations séparées.")
                    .font(.footnote)
                    .foregroundStyle(WapiColor.secondaryText)
                    .padding(.horizontal, 4)
            }
            .padding(WapiSpacing.screen)
        }
        .background(Color.whappyBackground.ignoresSafeArea())
        .navigationTitle("Business")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var businessHero: some View {
        ZStack(alignment: .topTrailing) {
            LinearGradient(colors: [Color.whappyInk, WapiColor.deepBlue, Color.whappyBlue], startPoint: .topLeading, endPoint: .bottomTrailing)
            Circle().fill(.white.opacity(0.08)).frame(width: 190, height: 190).offset(x: 65, y: -85)
            VStack(alignment: .leading, spacing: 15) {
                HStack(spacing: 12) {
                    Group {
                        if let business = store.business, let url = URL(string: business.logoURL), !business.logoURL.isEmpty {
                            WapiCachedRemoteImage(url: url) { InitialsAvatar(text: business.name, size: 58) }
                        } else { InitialsAvatar(text: store.business?.name ?? "Business", size: 58) }
                    }
                    .frame(width: 58, height: 58)
                    .clipShape(RoundedRectangle(cornerRadius: 17, style: .continuous))
                    VStack(alignment: .leading, spacing: 3) {
                        Text("ESPACE BUSINESS").font(.system(size: 9, weight: .black)).tracking(1).foregroundStyle(WapiColor.sky)
                        Text(store.business?.name ?? "Créez votre entreprise").font(.title2.weight(.black)).foregroundStyle(.white).lineLimit(1)
                        Text(store.business.map { "\($0.category) · \($0.city)" } ?? "Identité, ventes et clients séparés").font(.caption).foregroundStyle(.white.opacity(0.7))
                    }
                }
                NavigationLink { BusinessEditorView() } label: {
                    Label(store.business == nil ? "Configurer mon Business" : "Gérer l’identité professionnelle", systemImage: store.business == nil ? "plus" : "pencil")
                        .font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk)
                        .frame(maxWidth: .infinity).frame(height: 44).background(.white).clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
            }
            .padding(20)
        }
        .clipShape(RoundedRectangle(cornerRadius: 27, style: .continuous))
    }
}

private struct BusinessMetricIOS: View {
    let title: String
    let value: String
    let detail: String
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title.uppercased()).font(.system(size: 8, weight: .black)).foregroundStyle(WapiColor.secondaryText).lineLimit(1)
            Text(value).font(.headline.weight(.black)).foregroundStyle(Color.whappyInk).lineLimit(1)
            Text(detail).font(.system(size: 8, weight: .medium)).foregroundStyle(WapiColor.secondaryText).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(11)
        .background(.white)
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
    }
}

private struct BusinessOperationIOS: View {
    let icon: String
    let title: String
    let detail: String
    var badge: String? = nil
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 19, weight: .semibold)).foregroundStyle(Color.whappyBlue)
                .frame(width: 44, height: 44).background(WapiColor.blueMist).clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk)
                Text(detail).font(.caption).foregroundStyle(WapiColor.secondaryText).lineLimit(2)
            }
            Spacer()
            if let badge { Text(badge).font(.caption2.weight(.black)).foregroundStyle(.white).padding(.horizontal, 8).padding(.vertical, 5).background(Color.whappyBlue).clipShape(Capsule()) }
            Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(WapiColor.secondaryText)
        }
        .padding(14).background(.white).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

private struct BusinessCampaignIOSView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var title = ""
    @State private var creative = ""
    @State private var city = "Brazzaville"
    @State private var dailyBudget = "1000"
    @State private var days = "3"
    @State private var saving = false
    @State private var result: String?

    var body: some View {
        Form {
            Section("Campagne") {
                TextField("Nom de la campagne", text: $title)
                TextField("Message publicitaire", text: $creative, axis: .vertical).lineLimit(3...6)
                TextField("Ville ou région ciblée", text: $city)
            }
            Section("Budget contrôlé") {
                TextField("Budget quotidien (FCFA)", text: $dailyBudget).keyboardType(.numberPad)
                TextField("Nombre de jours", text: $days).keyboardType(.numberPad)
                if let budget = Int(dailyBudget), let duration = Int(days) {
                    Text("Budget total prévu : \((budget * duration).formatted()) FCFA").font(.footnote).foregroundStyle(.secondary)
                }
            }
            Section {
                Button(saving ? "Préparation…" : "Préparer la campagne") { createCampaign() }
                    .disabled(!isValid || saving || store.business == nil)
                if let result { Text(result).foregroundStyle(result.hasPrefix("Campagne") ? .green : .red) }
                Text("La campagne est enregistrée avec le statut « paiement requis ». Elle ne peut pas dépenser d’argent sans validation explicite.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("WAPI Ads")
    }

    private var isValid: Bool {
        title.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2 &&
        creative.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2 &&
        (Int(dailyBudget) ?? 0) >= 500 && (1...90).contains(Int(days) ?? 0)
    }

    private func createCampaign() {
        guard let uid = store.firebaseUserID, let business = store.business, !business.remoteID.isEmpty,
              let budget = Int(dailyBudget), let duration = Int(days), isValid else {
            result = "Complétez d’abord le profil Business et les champs de la campagne."
            return
        }
        saving = true; result = nil
        Firestore.firestore().collection("adCampaigns").addDocument(data: [
            "ownerId": uid, "pageId": business.remoteID, "pageName": business.name,
            "objective": "messages", "placement": "inbox", "destination": "message",
            "title": title.trimmingCharacters(in: .whitespacesAndNewlines),
            "creative": creative.trimmingCharacters(in: .whitespacesAndNewlines),
            "cta": "Contacter", "audience": "Utilisateurs WAPI de la région", "city": city,
            "phone": business.phone, "link": business.website, "dailyBudget": budget, "days": duration,
            "totalBudget": budget * duration, "estimatedReach": max(120, (budget / 500) * duration * 120),
            "status": "pending_payment", "createdAt": FieldValue.serverTimestamp(), "updatedAt": FieldValue.serverTimestamp(),
        ]) { error in
            saving = false
            result = error == nil ? "Campagne préparée. Validation du paiement requise." : wapiUserFacingError(error!, action: "La campagne Business")
        }
    }
}

private struct BusinessEditorView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var name = ""
    @State private var category = "Commerce"
    @State private var bio = ""
    @State private var city = "Brazzaville"
    @State private var phone = ""
    @State private var website = ""
    @State private var saved = false
    @State private var logoItem: PhotosPickerItem?
    @State private var selectedLogo: UIImage?

    var body: some View {
        Form {
            Section("Identité professionnelle") {
                Text("Ce profil Business est distinct de votre compte personnel. Il peut utiliser le même numéro WAPI, mais possède ses propres messages, catalogue et statistiques.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                HStack(spacing: 13) {
                    businessLogo
                        .frame(width: 64, height: 64)
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    VStack(alignment: .leading, spacing: 5) {
                        Text(store.business == nil ? "Créez votre page puis choisissez son logo." : "Le logo est public et propre à votre page Business.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        PhotosPicker(selection: $logoItem, matching: .images) {
                            Label("Changer le logo", systemImage: "photo.on.rectangle.angled")
                        }
                        .disabled(store.business == nil || store.firebaseBusy)
                    }
                }
                .padding(.vertical, 4)
                TextField("Nom de l’entreprise", text: $name)
                TextField("Activité", text: $category)
                TextField("Ville ou région", text: $city)
                TextField("Numéro Business", text: $phone).keyboardType(.phonePad)
                TextField("Site ou catalogue", text: $website).keyboardType(.URL)
            }
            Section("Présentation") {
                TextField("Que proposez-vous aux clients ?", text: $bio, axis: .vertical).lineLimit(3...6)
            }
            Section("Commerce social") {
                NavigationLink { BusinessSaleRoomManagerIOS() } label: {
                    Label("Salons de vente", systemImage: "person.3.sequence.fill")
                }
                Text("WIA peut préparer vos réponses clients, vos descriptions et vos relances. Les campagnes sont gérées dans Business Ads.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Section {
                Button(saved ? "Enregistré" : (store.business == nil ? "Créer le profil Business" : "Enregistrer les modifications")) {
                    store.saveBusiness(name: name, category: category, bio: bio, city: city, phone: phone, website: website)
                    saved = true
                }
                .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).count < 2 || store.firebaseBusy)
                .frame(maxWidth: .infinity)
            }
        }
        .navigationTitle("Business")
        .onAppear {
            if let business = store.business {
                name = business.name; category = business.category; bio = business.bio
                city = business.city; phone = business.phone; website = business.website
            }
        }
        .onChange(of: logoItem) { _, item in
            guard let item else { return }
            Task {
                defer { logoItem = nil }
                guard let data = try? await item.loadTransferable(type: Data.self),
                      let image = UIImage(data: data),
                      let normalized = makeWapiGroupPhotoData(image) else {
                    store.firebaseMessage = "Cette image ne peut pas être utilisée comme logo Business."
                    return
                }
                selectedLogo = image
                store.uploadBusinessLogo(normalized)
            }
        }
    }

    @ViewBuilder
    private var businessLogo: some View {
        if let selectedLogo {
            Image(uiImage: selectedLogo).resizable().scaledToFill()
        } else if let url = URL(string: store.business?.logoURL ?? ""), !url.absoluteString.isEmpty {
            WapiCachedRemoteImage(url: url) { InitialsAvatar(text: String(name.prefix(2)).uppercased(), size: 64) }
        } else {
            InitialsAvatar(text: String(name.prefix(2)).uppercased(), size: 64)
        }
    }
}

private struct BusinessSaleProductIOS: Identifiable {
    let id: String; let title: String; let description: String; let price: Int; let originalPrice: Int; let stock: Int; let sold: Int; let status: String
}

private struct BusinessSaleRoomIOS: Identifiable {
    let id: String; let ownerID: String; let pageID: String; let pageName: String; let pageCity: String; let title: String; let description: String; let visibility: String; let status: String; let code: String; let viewers: Int; let reservations: Int; let products: [BusinessSaleProductIOS]
}

@MainActor
private final class BusinessSaleRoomsIOSStore: ObservableObject {
    @Published var rooms: [BusinessSaleRoomIOS] = []
    @Published var loading = false
    @Published var message: String?
    private let functions = Functions.functions(region: "europe-west1")
    private let firestore = Firestore.firestore()

    func load() async {
        loading = true; defer { loading = false }
        do {
            let result = try await call("listVisibleBusinessSaleRooms", [:])
            rooms = Self.parse(result)
            message = nil
        } catch where Self.canReadDirectly(error) {
            do {
                rooms = try await loadFromFirestore()
                message = nil
            } catch {
                message = Self.failureMessage(error)
            }
        } catch {
            message = Self.failureMessage(error)
        }
    }

    func join(_ room: BusinessSaleRoomIOS) async { guard room.status == "live" else { return }; do { _ = try await call("joinBusinessSaleRoom", ["roomId": room.id]) } catch { message = Self.failureMessage(error) } }
    func reserve(_ room: BusinessSaleRoomIOS, product: BusinessSaleProductIOS) async -> Bool { do { _ = try await call("reserveBusinessSaleProduct", ["roomId": room.id, "dealId": product.id, "quantity": 1]); message = "Réservation confirmée. La boutique a reçu votre commande."; await load(); return true } catch { message = Self.failureMessage(error); return false } }
    func end(_ room: BusinessSaleRoomIOS) async { do { _ = try await call("endBusinessSaleRoom", ["roomId": room.id]); await load() } catch { message = Self.failureMessage(error) } }
    func create(pageID: String, title: String, description: String, worldwide: Bool, duration: Int, dealIDs: [String]) async -> Bool { do { _ = try await call("createBusinessSaleRoom", ["pageId": pageID, "title": title, "description": description, "visibility": worldwide ? "public" : "contacts", "durationMinutes": duration, "dealIds": dealIDs, "startNow": true]); message = "Salon publié dans le Marché WAPI."; await load(); return true } catch { message = Self.failureMessage(error); return false } }

    private func call(_ name: String, _ data: [String: Any]) async throws -> [String: Any] {
        try await withCheckedThrowingContinuation { continuation in functions.httpsCallable(name).call(data) { result, error in if let error { continuation.resume(throwing: error) } else { continuation.resume(returning: result?.data as? [String: Any] ?? [:]) } } }
    }

    private func loadFromFirestore() async throws -> [BusinessSaleRoomIOS] {
        var documents: [String: QueryDocumentSnapshot] = [:]
        let publicSnapshot = try await firestore.collection("businessSaleRooms")
            .whereField("visibility", isEqualTo: "public")
            .limit(to: 100)
            .getDocuments()
        publicSnapshot.documents.forEach { documents[$0.documentID] = $0 }
        if let userID = Auth.auth().currentUser?.uid, !userID.isEmpty {
            let ownSnapshot = try await firestore.collection("businessSaleRooms")
                .whereField("ownerId", isEqualTo: userID)
                .limit(to: 100)
                .getDocuments()
            ownSnapshot.documents.forEach { documents[$0.documentID] = $0 }
        }
        let now = Date()
        let visible = documents.values.filter { document in
            guard ["scheduled", "live"].contains(document.get("status") as? String ?? "") else { return false }
            return ((document.get("endsAt") as? Timestamp)?.dateValue() ?? .distantFuture) > now
        }
        let dealIDs = Array(Set(visible.flatMap { $0.get("dealIds") as? [String] ?? [] }))
        var deals: [String: QueryDocumentSnapshot] = [:]
        for offset in stride(from: 0, to: dealIDs.count, by: 30) {
            let end = min(offset + 30, dealIDs.count)
            let chunk = Array(dealIDs[offset..<end])
            guard !chunk.isEmpty else { continue }
            let snapshot = try await firestore.collection("businessDeals")
                .whereField(FieldPath.documentID(), in: chunk)
                .getDocuments()
            snapshot.documents.forEach { deals[$0.documentID] = $0 }
        }
        return visible.map { document in
            let products = (document.get("dealIds") as? [String] ?? []).compactMap { dealID -> BusinessSaleProductIOS? in
                guard let deal = deals[dealID] else { return nil }
                return BusinessSaleProductIOS(
                    id: deal.documentID,
                    title: deal.get("title") as? String ?? "Produit WAPI",
                    description: deal.get("description") as? String ?? "",
                    price: (deal.get("dealPrice") as? NSNumber)?.intValue ?? 0,
                    originalPrice: (deal.get("originalPrice") as? NSNumber)?.intValue ?? 0,
                    stock: (deal.get("stock") as? NSNumber)?.intValue ?? 0,
                    sold: (deal.get("sold") as? NSNumber)?.intValue ?? 0,
                    status: deal.get("status") as? String ?? "active"
                )
            }
            return BusinessSaleRoomIOS(
                id: document.documentID,
                ownerID: document.get("ownerId") as? String ?? "",
                pageID: document.get("pageId") as? String ?? "",
                pageName: document.get("pageName") as? String ?? "WAPI Business",
                pageCity: document.get("pageCity") as? String ?? "",
                title: document.get("title") as? String ?? "Vente privée WAPI",
                description: document.get("description") as? String ?? "",
                visibility: document.get("visibility") as? String ?? "public",
                status: document.get("status") as? String ?? "scheduled",
                code: document.get("code") as? String ?? "",
                viewers: (document.get("viewerCount") as? NSNumber)?.intValue ?? 0,
                reservations: (document.get("reservationCount") as? NSNumber)?.intValue ?? 0,
                products: products
            )
        }.sorted { left, right in
            if left.status != right.status { return left.status == "live" }
            return left.title.localizedCaseInsensitiveCompare(right.title) == .orderedAscending
        }
    }

    private static func canReadDirectly(_ error: Error) -> Bool {
        guard let code = FunctionsErrorCode(rawValue: (error as NSError).code) else { return false }
        return [.notFound, .unavailable, .deadlineExceeded].contains(code)
    }

    private static func failureMessage(_ error: Error) -> String {
        guard let code = FunctionsErrorCode(rawValue: (error as NSError).code) else {
            return "Les ventes privées sont momentanément indisponibles. Touchez Actualiser."
        }
        switch code {
        case .notFound:
            return "La mise à jour Ventes privées n’est pas encore active sur le serveur WAPI."
        case .unauthenticated:
            return "Reconnectez-vous à WAPI pour accéder aux ventes privées."
        case .permissionDenied:
            return "Votre compte n’est pas autorisé à effectuer cette opération."
        case .failedPrecondition:
            return "Cette vente n’est plus disponible."
        case .unavailable, .deadlineExceeded:
            return "Connexion momentanément indisponible. Touchez Actualiser."
        default:
            return "Les ventes privées sont momentanément indisponibles. Touchez Actualiser."
        }
    }

    private static func parse(_ root: [String: Any]) -> [BusinessSaleRoomIOS] {
        (root["rooms"] as? [[String: Any]] ?? []).compactMap { value in
            guard let id = value["id"] as? String else { return nil }
            let products = (value["products"] as? [[String: Any]] ?? []).compactMap { product -> BusinessSaleProductIOS? in guard let productID = product["id"] as? String else { return nil }; return BusinessSaleProductIOS(id: productID, title: product["title"] as? String ?? "Produit WAPI", description: product["description"] as? String ?? "", price: (product["price"] as? NSNumber)?.intValue ?? 0, originalPrice: (product["originalPrice"] as? NSNumber)?.intValue ?? 0, stock: (product["stock"] as? NSNumber)?.intValue ?? 0, sold: (product["sold"] as? NSNumber)?.intValue ?? 0, status: product["status"] as? String ?? "active") }
            return BusinessSaleRoomIOS(id: id, ownerID: value["ownerId"] as? String ?? "", pageID: value["pageId"] as? String ?? "", pageName: value["pageName"] as? String ?? "WAPI Business", pageCity: value["pageCity"] as? String ?? "", title: value["title"] as? String ?? "Vente privée WAPI", description: value["description"] as? String ?? "", visibility: value["visibility"] as? String ?? "public", status: value["status"] as? String ?? "scheduled", code: value["code"] as? String ?? "", viewers: (value["viewerCount"] as? NSNumber)?.intValue ?? 0, reservations: (value["reservationCount"] as? NSNumber)?.intValue ?? 0, products: products)
        }
    }
}

private struct BusinessSaleRoomsIOSRail: View {
    @StateObject private var model = BusinessSaleRoomsIOSStore()
    @State private var selected: BusinessSaleRoomIOS?
    var body: some View {
        VStack(alignment: .leading, spacing: 11) {
            HStack { Image(systemName: "globe.europe.africa.fill").foregroundStyle(Color.whappyBlue).frame(width: 42, height: 42).background(Color.whappyBlue.opacity(0.1)).clipShape(RoundedRectangle(cornerRadius: 13)); VStack(alignment: .leading) { Text("Ventes privées en direct").font(.headline); Text("Visibles dans tout WAPI · aucun numéro requis").font(.caption2).foregroundStyle(.secondary) }; Spacer(); if model.loading { ProgressView() } else { Button { Task { await model.load() } } label: { Image(systemName: "arrow.clockwise") } } }
            if !model.loading, model.rooms.isEmpty { Text("Aucune vente ouverte pour le moment.").font(.footnote).foregroundStyle(.secondary) }
            ScrollView(.horizontal, showsIndicators: false) { HStack(spacing: 10) { ForEach(model.rooms.prefix(12)) { room in Button { selected = room } label: { VStack(alignment: .leading, spacing: 6) { HStack { Text(room.status == "live" ? "● EN COURS" : "BIENTÔT").font(.caption2.bold()).foregroundStyle(room.status == "live" ? .red : .yellow); Spacer(); Text(room.visibility == "public" ? "MONDIAL" : "CONTACTS").font(.caption2.bold()).foregroundStyle(.white.opacity(0.65)) }; Text(room.title).font(.headline).foregroundStyle(.white).lineLimit(2); Text("\(room.pageName) · \(room.products.count) produit(s)").font(.caption).foregroundStyle(.white.opacity(0.65)); Text("\(room.viewers) visiteurs").font(.caption2).foregroundStyle(.cyan) }.padding().frame(width: 238, alignment: .leading).background(Color(red: 0.03, green: 0.11, blue: 0.25)).clipShape(RoundedRectangle(cornerRadius: 19)) }.buttonStyle(.plain) } } }
            if let message = model.message { Text(message).font(.caption).foregroundStyle(message.hasPrefix("Réservation") ? .green : .red) }
        }.padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 22)).task { await model.load() }.sheet(item: $selected) { BusinessSaleRoomDetailIOS(room: $0, model: model) }
    }
}

private struct BusinessSaleRoomDetailIOS: View {
    @Environment(\.dismiss) private var dismiss
    let room: BusinessSaleRoomIOS
    @ObservedObject var model: BusinessSaleRoomsIOSStore
    @State private var busyID: String?
    var body: some View { NavigationStack { ScrollView { VStack(alignment: .leading, spacing: 14) {
        VStack(alignment: .leading, spacing: 8) { Text(room.status == "live" ? "● EN COURS" : "PROGRAMMÉE").font(.caption.bold()).foregroundStyle(.yellow); Text(room.title).font(.largeTitle.bold()).foregroundStyle(.white); if !room.description.isEmpty { Text(room.description).foregroundStyle(.white.opacity(0.72)) }; Text("\(room.viewers) visiteurs · \(room.reservations) réservations").font(.caption).foregroundStyle(.white.opacity(0.65)) }.padding(22).frame(maxWidth: .infinity, alignment: .leading).background(Color(red: 0.03, green: 0.11, blue: 0.25)).clipShape(RoundedRectangle(cornerRadius: 26))
        Text("Sélection de \(room.pageName)").font(.title2.bold())
        ForEach(room.products) { product in VStack(alignment: .leading, spacing: 8) { Text(product.title).font(.headline); if !product.description.isEmpty { Text(product.description).font(.caption).foregroundStyle(.secondary) }; HStack { Text("\(product.price.formatted()) FCFA").font(.title3.bold()).foregroundStyle(Color.whappyBlue); Spacer(); Text("\(max(0, product.stock - product.sold)) restant(s)").font(.caption).foregroundStyle(.secondary) }; Button { busyID = product.id; Task { _ = await model.reserve(room, product: product); busyID = nil } } label: { if busyID == product.id { ProgressView().frame(maxWidth: .infinity) } else { Label("Réserver maintenant", systemImage: "bag.badge.plus").frame(maxWidth: .infinity) } }.buttonStyle(.borderedProminent).disabled(room.status != "live" || product.status != "active" || product.sold >= product.stock || busyID != nil) }.padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 18)) }
    }.padding() }.background(Color.whappyBackground).navigationTitle(room.pageName).toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Fermer") { dismiss() } } }.task { await model.join(room) } } }
}

private struct BusinessSalePageIOS: Identifiable { let id: String; let name: String }
private struct BusinessSaleDealIOS: Identifiable { let id: String; let pageID: String; let title: String; let price: Int; let available: Int }

private struct BusinessSaleRoomManagerIOS: View {
    @StateObject private var model = BusinessSaleRoomsIOSStore()
    @State private var pages: [BusinessSalePageIOS] = []
    @State private var deals: [BusinessSaleDealIOS] = []
    @State private var creating = false
    private var mine: [BusinessSaleRoomIOS] { model.rooms.filter { $0.ownerID == Auth.auth().currentUser?.uid } }
    var body: some View { List {
        Section { Text("Une vente privée est un événement commercial limité dans le temps. En mode mondial, tous les comptes WAPI peuvent la découvrir sans connaître votre numéro.").font(.footnote).foregroundStyle(.secondary); Button { creating = true } label: { Label("Créer un salon de vente", systemImage: "plus.circle.fill") }.disabled(pages.isEmpty || deals.isEmpty) }
        Section("Mes salons") { if model.loading { ProgressView() } else if mine.isEmpty { ContentUnavailableView("Aucun salon", systemImage: "storefront", description: Text("Ajoutez des produits puis ouvrez votre première vente.")) } else { ForEach(mine) { room in VStack(alignment: .leading, spacing: 6) { HStack { Text(room.status == "live" ? "● EN COURS" : room.status.uppercased()).font(.caption.bold()).foregroundStyle(room.status == "live" ? .red : .secondary); Spacer(); Text(room.visibility == "public" ? "MONDIAL" : "CONTACTS").font(.caption2.bold()).foregroundStyle(Color.whappyBlue) }; Text(room.title).font(.headline); Text("\(room.viewers) visiteurs · \(room.reservations) réservations · \(room.code)").font(.caption).foregroundStyle(.secondary); if room.status != "ended" { Button("Terminer") { Task { await model.end(room) } }.buttonStyle(.bordered) } } } } }
        if let message = model.message { Section { Text(message).foregroundStyle(.secondary) } }
    }.navigationTitle("Salons de vente").task { await loadAssets(); await model.load() }.sheet(isPresented: $creating) { BusinessCreateSaleRoomIOS(model: model, pages: pages, deals: deals) { creating = false } } }

    private func loadAssets() async {
        guard let uid = Auth.auth().currentUser?.uid else { return }
        do { let pageDocs = try await Firestore.firestore().collection("businessPages").whereField("ownerId", isEqualTo: uid).getDocuments(); pages = pageDocs.documents.map { BusinessSalePageIOS(id: $0.documentID, name: $0.get("name") as? String ?? "WAPI Business") }; let dealDocs = try await Firestore.firestore().collection("businessDeals").whereField("ownerId", isEqualTo: uid).getDocuments(); deals = dealDocs.documents.compactMap { doc in let status = doc.get("status") as? String ?? ""; guard ["active", "paused"].contains(status) else { return nil }; let stock = (doc.get("stock") as? NSNumber)?.intValue ?? 0; let sold = (doc.get("sold") as? NSNumber)?.intValue ?? 0; return BusinessSaleDealIOS(id: doc.documentID, pageID: doc.get("pageId") as? String ?? "", title: doc.get("title") as? String ?? "Produit WAPI", price: (doc.get("dealPrice") as? NSNumber)?.intValue ?? 0, available: max(0, stock - sold)) } } catch { model.message = wapiUserFacingError(error, action: "Le chargement de votre catalogue Business") }
    }
}

private struct BusinessCreateSaleRoomIOS: View {
    @ObservedObject var model: BusinessSaleRoomsIOSStore
    let pages: [BusinessSalePageIOS]; let deals: [BusinessSaleDealIOS]; let onClosed: () -> Void
    @State private var pageID = ""; @State private var title = ""; @State private var description = ""; @State private var duration = 60; @State private var worldwide = true; @State private var selected: Set<String> = []; @State private var busy = false
    var body: some View { NavigationStack { Form { Section("Page Business") { Picker("Page", selection: $pageID) { ForEach(pages) { Text($0.name).tag($0.id) } }; TextField("Nom de la vente", text: $title); TextField("Présentation", text: $description, axis: .vertical).lineLimit(2...5); Stepper("Durée : \(duration) minutes", value: $duration, in: 15...10_080, step: 15); Toggle("Visible dans tout WAPI", isOn: $worldwide); Text(worldwide ? "Aucun numéro de téléphone nécessaire." : "Réservé aux contacts de la boutique.").font(.footnote).foregroundStyle(.secondary) }; Section("Produits") { ForEach(deals.filter { $0.pageID == pageID }) { deal in Button { if selected.contains(deal.id) { selected.remove(deal.id) } else { selected.insert(deal.id) } } label: { HStack { Image(systemName: selected.contains(deal.id) ? "checkmark.circle.fill" : "circle"); VStack(alignment: .leading) { Text(deal.title); Text("\(deal.price.formatted()) FCFA · \(deal.available) disponible(s)").font(.caption).foregroundStyle(.secondary) } } } } } }.navigationTitle("Nouvelle vente").onAppear { if pageID.isEmpty { pageID = pages.first?.id ?? "" } }.toolbar { ToolbarItem(placement: .cancellationAction) { Button("Annuler", action: onClosed).disabled(busy) }; ToolbarItem(placement: .confirmationAction) { Button("Ouvrir") { busy = true; Task { if await model.create(pageID: pageID, title: title, description: description, worldwide: worldwide, duration: duration, dealIDs: Array(selected)) { onClosed() }; busy = false } }.disabled(pageID.isEmpty || selected.isEmpty || busy) } } } }
}

struct ProfileView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var zoomedPhoto: ZoomPhoto?
    @State private var profilePhotoItem: PhotosPickerItem?
    @State private var profilePhoto: UIImage?
    @State private var showAccountSwitcher = false

    private var signedInPhone: String { Auth.auth().currentUser?.phoneNumber ?? "" }
    private var founder: Bool { isWhappyFounderPhone(signedInPhone) }
    private var displayName: String {
        if founder { return whappyFounderName }
        let value = Auth.auth().currentUser?.displayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return value.isEmpty ? "Compte WAPI" : value
    }
    private var activeDisplayName: String { store.activeBusinessMode ? (store.business?.name ?? displayName) : displayName }
    private var profileInitials: String {
        displayName.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
    }

    private static let profilePhotoKey = "whappy-ios-profile-photo-path"

    private func profilePhotoPath() -> String? {
        UserDefaults.standard.string(forKey: Self.profilePhotoKey)
    }

    private func saveProfilePhoto(_ image: UIImage) {
        guard let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        let target = directory.appendingPathComponent("whappy-profile-photo.jpg")
        guard let data = image.jpegData(compressionQuality: 0.9) else { return }
        try? data.write(to: target, options: .atomic)
        UserDefaults.standard.set(target.path, forKey: Self.profilePhotoKey)
        profilePhoto = image
        store.uploadFirebaseProfilePhoto(data)
    }

    private func loadProfilePhoto() {
        if let path = profilePhotoPath() {
            profilePhoto = UIImage(contentsOfFile: path)
        }
        if profilePhoto == nil, let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
            let target = directory.appendingPathComponent("whappy-profile-photo.jpg")
            profilePhoto = UIImage(contentsOfFile: target.path)
            if profilePhoto != nil {
                UserDefaults.standard.set(target.path, forKey: Self.profilePhotoKey)
            }
        }
    }

    var body: some View {
        List {
            Section {
                HStack(spacing: 16) {
                    Button { showAccountSwitcher = true } label: { profileHeaderAvatar }.buttonStyle(.plain)
                    VStack(alignment: .leading) {
                        HStack(spacing: 5) {
                            Text(activeDisplayName).font(.title3.bold())
                            if store.firebaseProfileVerified || founder {
                                Image(systemName: "checkmark.seal.fill")
                                    .foregroundStyle(Color.wapiVerified)
                                    .accessibilityLabel("Compte certifié")
                            }
                        }
                        Text(founder ? whappyFounderBusinessName + " · " + whappyFounderBadgeLabel : (store.activeBusinessMode ? "Compte Business · espace professionnel" : (store.firebaseProfileVerified ? "Compte WAPI vérifié" : "Compte personnel WAPI")))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }.padding(.vertical, 8)
            }
            Section("Identité active") {
                Button { showAccountSwitcher = true } label: {
                    Label(store.activeBusinessMode ? "Gérer le compte Business" : "Choisir un compte Business", systemImage: store.activeBusinessMode ? "briefcase.fill" : "person.2.badge.plus")
                }
                Text("Le compte personnel et le compte Business partagent le numéro, mais gardent des profils, messages, appels et outils séparés.")
                    .font(.footnote).foregroundStyle(.secondary)
                if founder {
                    NavigationLink { FounderDashboardIOSView() } label: { Label("Tableau Fondateur", systemImage: "chart.xyaxis.line") }
                }
            }
            Section("Votre activité") { NavigationLink { MyWhappyLinkView() } label: { Label("Mon code et mon lien WAPI", systemImage: "qrcode") }; NavigationLink { BusinessWorkspaceView() } label: { Label("Espace Business", systemImage: "storefront.fill") }; NavigationLink { OrdersView() } label: { Label("Mes commandes", systemImage: "shippingbox.fill") }; Button { store.selectedTab = .services } label: { Label("Mon portefeuille", systemImage: "wallet.pass.fill") } }
            Section("Réglages") {
                NavigationLink { WapiSettingsHubView() } label: { Label("Centre des réglages WAPI", systemImage: "slider.horizontal.3") }
                Text("Notifications, langue, confidentialité, stockage et aide dans un espace unique.").font(.footnote).foregroundStyle(.secondary)
            }
            Section { Text("WAPI iOS · application native").foregroundStyle(.secondary) }
        }.navigationTitle("Profil")
            .onAppear(perform: loadProfilePhoto)
            .onChange(of: profilePhotoItem) { _, item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self),
                       let image = UIImage(data: data) {
                        saveProfilePhoto(image)
                    }
                    profilePhotoItem = nil
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    PhotosPicker(selection: $profilePhotoItem, matching: .images) {
                        Image(systemName: "photo.circle.fill")
                    }
                }
            }
            .fullScreenCover(item: $zoomedPhoto) { photo in
                ZoomablePhotoViewer(image: photo.image) { zoomedPhoto = nil }
            }
            .sheet(isPresented: $showAccountSwitcher) { AccountSwitcherIOSView() }
    }
}

private extension ProfileView {
    var profileHeaderAvatar: some View {
        Group {
            if store.activeBusinessMode, let url = URL(string: store.business?.logoURL ?? ""), !url.absoluteString.isEmpty {
                WapiCachedRemoteImage(url: url) { InitialsAvatar(text: profileInitials.isEmpty ? "WA" : profileInitials, size: 62) }
            } else if let photo = profilePhoto {
                Image(uiImage: photo)
                    .resizable()
                    .scaledToFill()
            } else {
                InitialsAvatar(text: profileInitials.isEmpty ? "WA" : profileInitials, size: 62)
            }
        }
        .frame(width: 62, height: 62)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Color.whappyBlue.opacity(0.14), lineWidth: 1))
        .onTapGesture {
            if let photo = profilePhoto {
                zoomedPhoto = ZoomPhoto(image: photo)
            }
        }
    }
}

private struct AccountSwitcherIOSView: View {
    @EnvironmentObject private var store: WhappyStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Comptes WAPI") {
                    Button {
                        store.switchAccount(business: false)
                        dismiss()
                    } label: {
                        AccountSwitcherIOSRow(
                            title: Auth.auth().currentUser?.displayName?.isEmpty == false ? Auth.auth().currentUser?.displayName ?? "Compte personnel" : "Compte personnel",
                            subtitle: Auth.auth().currentUser?.phoneNumber ?? "Identité personnelle",
                            icon: "person.crop.circle.fill",
                            photoURL: Auth.auth().currentUser?.photoURL?.absoluteString ?? "",
                            active: !store.activeBusinessMode,
                        )
                    }.buttonStyle(.plain)
                    if let business = store.business {
                        Button {
                            store.switchAccount(business: true)
                            dismiss()
                        } label: {
                            AccountSwitcherIOSRow(title: business.name, subtitle: "Business · \(business.category)", icon: "briefcase.fill", photoURL: business.logoURL, active: store.activeBusinessMode)
                        }.buttonStyle(.plain)
                    }
                }
                Section {
                    NavigationLink { BusinessEditorView() } label: { Label(store.business == nil ? "Créer mon compte Business" : "Modifier mon compte Business", systemImage: "plus.circle.fill") }
                } footer: {
                    Text("Chaque contexte possède sa propre identité publique. Les actions commerciales sont publiées au nom de la page Business, jamais au nom du profil personnel.")
                }
            }
            .navigationTitle("Changer de compte")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } } }
        }
    }
}

private struct AccountSwitcherIOSRow: View {
    let title: String
    let subtitle: String
    let icon: String
    let photoURL: String
    let active: Bool

    var body: some View {
        HStack(spacing: 12) {
            Group {
                if let url = URL(string: photoURL), !photoURL.isEmpty {
                    WapiCachedRemoteImage(url: url) { accountIcon }
                } else {
                    accountIcon
                }
            }
            .frame(width: 42, height: 42)
            .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
            VStack(alignment: .leading, spacing: 3) { Text(title).font(.headline); Text(subtitle).font(.caption).foregroundStyle(.secondary) }
            Spacer()
            if active { Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.whappyBlue) }
        }.padding(.vertical, 5)
    }

    private var accountIcon: some View {
        Image(systemName: icon)
            .font(.title2)
            .foregroundStyle(Color.whappyBlue)
            .frame(width: 42, height: 42)
            .background(Color.whappyBlue.opacity(0.10), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
    }
}

private struct FounderDashboardIOSView: View {
    @State private var loading = true
    @State private var error: String?
    @State private var values: [String: String] = [:]

    var body: some View {
        List {
            Section {
                if loading { ProgressView("Chargement des données WAPI…") }
                else if let error { Label(error, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.orange) }
                else {
                    FounderIOSMetric(title: "Utilisateurs WAPI", value: values["users"] ?? "0", detail: "Comptes WAPI enregistrés")
                    FounderIOSMetric(title: "CA encaissé", value: values["paidRevenue"] ?? "0 XAF", detail: "Notifications de paiement marquées payées")
                    FounderIOSMetric(title: "Installations actives", value: values["activeInstallations"] ?? "0", detail: "Appareils WAPI avec jeton actif")
                    FounderIOSMetric(title: "Pages Business", value: values["businessPages"] ?? "0", detail: "Pages réellement créées")
                    FounderIOSMetric(title: "Stories publiées", value: values["stories"] ?? "0", detail: "Stories présentes dans le cloud")
                    FounderIOSMetric(title: "Lives actifs", value: values["activeLives"] ?? "0", detail: "Sessions actuellement en direct")
                }
            } header: { Text("Données opérationnelles") }
            Section("Stores") {
                Label(values["playStore"] == "true" ? "Google Play connecté" : "Google Play non connecté", systemImage: "play.rectangle.fill")
                Label(values["appStore"] == "true" ? "App Store connecté" : "App Store non connecté", systemImage: "apple.logo")
                Text("Les téléchargements de store restent à zéro tant que les consoles officielles ne sont pas reliées. Aucun chiffre local n’est présenté comme réel.").font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Tableau Fondateur")
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        loading = true; error = nil
        do {
            let result = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[String: Any], Error>) in
                Functions.functions(region: "europe-west1").httpsCallable("getFounderDashboard").call { result, error in
                    if let error { continuation.resume(throwing: error) }
                    else { continuation.resume(returning: result?.data as? [String: Any] ?? [:]) }
                }
            }
            let stores = result["storeIntegrations"] as? [String: Any] ?? [:]
            values = [
                "users": Self.count(result["users"]),
                "paidRevenue": "\(Self.count(result["paidRevenue"])) XAF",
                "activeInstallations": Self.count(result["activeInstallations"]),
                "businessPages": Self.count(result["businessPages"]),
                "stories": Self.count(result["stories"]),
                "activeLives": Self.count(result["activeLives"]),
                "playStore": String(describing: stores["playStore"] as? Bool ?? false),
                "appStore": String(describing: stores["appStore"] as? Bool ?? false),
            ]
        } catch let failure { error = wapiUserFacingError(failure, action: "Le chargement du tableau fondateur") }
        loading = false
    }

    private static func count(_ value: Any?) -> String {
        if let value = value as? NSNumber { return value.intValue.formatted() }
        if let value = value as? Int { return value.formatted() }
        if let value = value as? Int64 { return value.formatted() }
        return "0"
    }
}

private struct FounderIOSMetric: View {
    let title: String
    let value: String
    let detail: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.headline)
            Text(value).font(.system(size: 27, weight: .black, design: .rounded)).foregroundStyle(Color.whappyBlue)
            Text(detail).font(.caption).foregroundStyle(.secondary)
        }.padding(.vertical, 4)
    }
}

private struct MyWhappyLinkView: View {
    @State private var countryCode = "+242"
    @State private var phone = ""
    private var displayName: String {
        let value = Auth.auth().currentUser?.displayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return value.isEmpty ? "Compte WAPI" : value
    }
    private var profilePhoto: UIImage? {
        guard let path = UserDefaults.standard.string(forKey: "whappy-ios-profile-photo-path") else { return nil }
        return UIImage(contentsOfFile: path)
    }
    private var normalized: String? { WhappyPhoneCountry.normalize(phone, selectedCode: countryCode) }
    private var link: URL? { normalized.flatMap { value in URL(string: "https://whappy.chat/contact/\(value.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? value)") } }
    private var qrImage: UIImage? {
        guard let normalized else { return nil }
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data("whappy://contact/\(normalized)".utf8)
        filter.correctionLevel = "H"
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 10, y: 10)), let cgImage = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                VStack(spacing: 10) {
                    if let profilePhoto { Image(uiImage: profilePhoto).resizable().scaledToFill().frame(width: 72, height: 72).clipShape(Circle()) }
                    else { InitialsAvatar(text: String(displayName.prefix(2)).uppercased(), size: 72) }
                    Text(displayName).font(.title3.bold()).foregroundStyle(Color.whappyInk)
                    Text("Identité personnelle WAPI").font(.caption).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity).padding(20)
                .background(.white, in: RoundedRectangle(cornerRadius: 24))

                VStack(alignment: .leading, spacing: 12) {
                    Text("NUMÉRO DU COMPTE").font(.caption2.bold()).foregroundStyle(Color.whappyBlue)
                    Picker("Pays", selection: $countryCode) { ForEach(WhappyPhoneCountry.supported) { country in Text("\(country.flag) \(country.name)  \(country.code)").tag(country.code) } }
                    TextField("Numéro", text: $phone).keyboardType(.phonePad).textFieldStyle(.roundedBorder)
                    if let normalized { Label(normalized, systemImage: "checkmark.seal.fill").font(.footnote.bold()).foregroundStyle(Color.whappyBlue) }
                }.padding(18).background(.white, in: RoundedRectangle(cornerRadius: 22))

                if let qrImage, let link {
                    VStack(spacing: 13) {
                        Text("Scannez pour m’ajouter").font(.headline).foregroundStyle(Color.whappyInk)
                        ZStack {
                            Image(uiImage: qrImage).interpolation(.none).resizable().scaledToFit()
                            Text("W").font(.system(size: 24, weight: .black)).foregroundStyle(.white)
                                .frame(width: 52, height: 52).background(Color.whappyBlue, in: RoundedRectangle(cornerRadius: 15))
                                .padding(5).background(.white, in: RoundedRectangle(cornerRadius: 18))
                        }
                        .frame(width: 238, height: 238).padding(14)
                        .background(.white, in: RoundedRectangle(cornerRadius: 24))
                        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.whappyBlue.opacity(0.16), lineWidth: 8))
                        Text(normalized ?? "").font(.subheadline.bold()).foregroundStyle(Color.whappyInk)
                        ShareLink(item: "Ajoutez-moi sur WAPI\nwhappy://contact/\(normalized ?? "")\n\(link.absoluteString)") { Label("Partager mon contact", systemImage: "square.and.arrow.up").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent)
                    }.padding(20).frame(maxWidth: .infinity).background(.white, in: RoundedRectangle(cornerRadius: 26))
                }
            }.padding()
        }
        .background(Color.whappyBackground)
        .navigationTitle("Mon code WAPI")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            guard phone.isEmpty, let current = Auth.auth().currentUser?.phoneNumber else { return }
            let digits = current.filter(\.isNumber)
            if let country = WhappyPhoneCountry.supported.sorted(by: { $0.code.count > $1.code.count }).first(where: { digits.hasPrefix($0.code.dropFirst()) }) {
                countryCode = country.code
                phone = String(digits.dropFirst(country.code.count - 1))
            }
        }
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

private struct WapiSettingsHubView: View {
    @EnvironmentObject private var store: WhappyStore

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("CENTRE WAPI").font(.caption.weight(.black)).tracking(1).foregroundStyle(Color.whappyBlue)
                    Text("Réglages clairs, contrôle réel.").font(.title2.weight(.black)).foregroundStyle(Color.whappyInk)
                    Text("Chaque réglage agit sur l’application native et reste lié à votre compte ou à cet appareil selon sa nature.").font(.footnote).foregroundStyle(.secondary)
                }.padding(20).frame(maxWidth: .infinity, alignment: .leading).background(LinearGradient(colors: [Color.whappyInk, Color.whappyInk.opacity(0.88)], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 24))
                    .foregroundStyle(.white)
                WapiSettingsCard(title: "Notifications", subtitle: "Messages et appels même lorsque WAPI est fermé", icon: "bell.badge.fill") {
                    Toggle("Autoriser les notifications", isOn: $store.notificationsEnabled).tint(Color.whappyBlue)
                }
                WapiSettingsCard(title: "Langue et région", subtitle: "Détection automatique ou choix manuel", icon: "globe") {
                    NavigationLink { WapiLanguageSettingsView() } label: { Label(store.interfaceLanguage.label, systemImage: "character.bubble") }
                }
                WapiSettingsCard(title: "Confidentialité", subtitle: "Contrôler les contacts et les protections", icon: "lock.shield.fill") {
                    NavigationLink { PrivacySettingsView() } label: { Label("Ouvrir la confidentialité", systemImage: "arrow.up.right") }
                }
                WapiSettingsCard(title: "Stockage et données", subtitle: "Médias, cache et économie réseau", icon: "internaldrive.fill") {
                    NavigationLink { DataSettingsView() } label: { Label("Gérer le stockage", systemImage: "arrow.up.right") }
                }
                WapiSettingsCard(title: "Assistance", subtitle: "Aide et diagnostic WAPI", icon: "questionmark.circle.fill") {
                    NavigationLink { InfoView(title: "Aide", message: "Utilisez Messages pour discuter, Marché pour acheter ou vendre, Live pour diffuser, et Services pour demander une prestation.", icon: "questionmark.circle.fill") } label: { Label("Ouvrir l’aide", systemImage: "arrow.up.right") }
                }
            }.padding()
        }.background(Color.whappyBackground).navigationTitle("Réglages WAPI").navigationBarTitleDisplayMode(.inline)
    }
}

private struct WapiSettingsCard<Content: View>: View {
    let title: String
    let subtitle: String
    let icon: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 11) {
                Image(systemName: icon).foregroundStyle(Color.whappyBlue).frame(width: 38, height: 38).background(Color.whappyBlue.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 2) { Text(title).font(.headline); Text(subtitle).font(.caption).foregroundStyle(.secondary) }
            }
            content.padding(.top, 2)
        }.padding(16).frame(maxWidth: .infinity, alignment: .leading).background(.white, in: RoundedRectangle(cornerRadius: 20)).overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.black.opacity(0.05)))
    }
}

private struct WapiLanguageSettingsView: View {
    @EnvironmentObject private var store: WhappyStore

    var body: some View {
        Form {
            Section("Langue de WAPI") {
                Picker("Langue", selection: $store.interfaceLanguage) {
                    ForEach(WapiInterfaceLanguage.allCases) { language in
                        Text(language.automaticSummary).tag(language)
                    }
                }
                .pickerStyle(.inline)
            }
            Section {
                Text("Automatique utilise la langue et la région de votre iPhone. Une région anglophone ouvre WAPI en anglais ; vous pouvez toujours imposer votre préférence ici.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Section("Contenus") {
                Label("Les messages se traduisent indépendamment avec Lingwap.", systemImage: "character.bubble")
                    .font(.footnote)
            }
        }
        .navigationTitle("Langue")
    }
}

private struct DataSettingsView: View {
    @EnvironmentObject private var store: WhappyStore
    @AppStorage("wapi.sounds.enabled") private var soundsEnabled = true
    @AppStorage("wapi.typing.sounds.enabled") private var typingSoundsEnabled = true

    var body: some View {
        Form {
            Section("Réseau") {
                Toggle("Économiseur de données", isOn: $store.dataSaverEnabled)
                Text("Réduit le chargement automatique des médias lorsque votre connexion est limitée.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            Section("Sons et vibrations") {
                Toggle("Sons WAPI", isOn: $soundsEnabled)
                Toggle("Son de saisie", isOn: $typingSoundsEnabled).disabled(!soundsEnabled)
                Text("Les sons restent courts et suivent le volume de votre appareil. Les appels et les notifications conservent leurs alertes système.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            Section("Stockage") {
                Label("Photos et notes vocales conservées dans WAPI", systemImage: "folder.fill")
                Text("Les contenus sont supprimés avec l’application depuis les réglages iOS.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Stockage et données")
    }
}

private struct ActivityCenterView: View {
    @Environment(\.dismiss) private var dismiss; @EnvironmentObject private var store: WhappyStore
    var body: some View { NavigationStack { List { if store.unreadCount > 0 { Button { store.selectedTab = .messages; dismiss() } label: { Label("\(store.unreadCount) message(s) non lu(s)", systemImage: "message.badge.fill") } }; if !store.orders.isEmpty { Button { store.selectedTab = .services; dismiss() } label: { Label("\(store.orders.count) commande(s) à suivre", systemImage: "shippingbox.fill") } }; ForEach(store.liveRooms.filter(\.live)) { room in Button { store.selectedTab = .live; dismiss() } label: { Label("En direct : \(room.title)", systemImage: "dot.radiowaves.left.and.right") } }; if store.unreadCount == 0 && store.orders.isEmpty && store.liveRooms.filter(\.live).isEmpty { ContentUnavailableView("Tout est à jour", systemImage: "checkmark.circle", description: Text("Les nouvelles activités apparaîtront ici.")) } }.navigationTitle("Activité").toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fermer") { dismiss() } } } } }
}

final class WapiImageMemoryCache {
    static let shared = WapiImageMemoryCache()
    private let images = NSCache<NSURL, UIImage>()
    private init() { images.totalCostLimit = 48 * 1024 * 1024 }
    func image(for url: URL) -> UIImage? { images.object(forKey: url as NSURL) }
    func insert(_ image: UIImage, for url: URL, byteCount: Int) { images.setObject(image, forKey: url as NSURL, cost: max(byteCount, 1)) }
}

/// Keeps the last valid frame while Firestore refreshes a profile URL and
/// shares decoded avatars between Messages, Calls, Stories and Business.
struct WapiCachedRemoteImage<Placeholder: View>: View {
    let url: URL?
    var contentMode: ContentMode = .fill
    @ViewBuilder let placeholder: () -> Placeholder
    @State private var image: UIImage?

    init(url: URL?, contentMode: ContentMode = .fill, @ViewBuilder placeholder: @escaping () -> Placeholder) {
        self.url = url
        self.contentMode = contentMode
        self.placeholder = placeholder
        _image = State(initialValue: url.flatMap(WapiImageMemoryCache.shared.image(for:)))
    }

    var body: some View {
        Group {
            if let image { Image(uiImage: image).resizable().aspectRatio(contentMode: contentMode) }
            else { placeholder() }
        }
        .task(id: url?.absoluteString) {
            guard let url else { return }
            if let cached = WapiImageMemoryCache.shared.image(for: url) { image = cached; return }
            var request = URLRequest(url: url, cachePolicy: .returnCacheDataElseLoad, timeoutInterval: 15)
            request.setValue("image/*", forHTTPHeaderField: "Accept")
            guard let (data, response) = try? await URLSession.shared.data(for: request),
                  data.count <= 20 * 1024 * 1024,
                  ((response as? HTTPURLResponse)?.statusCode ?? 200) < 400,
                  let decoded = UIImage(data: data) else { return }
            WapiImageMemoryCache.shared.insert(decoded, for: url, byteCount: data.count)
            image = decoded
        }
    }
}

struct InitialsAvatar: View {
    let text: String; var size: CGFloat = 48
    var body: some View { ZStack { Circle().fill(Color.whappyBlue.opacity(0.14)); Text(text).font(.system(size: size * 0.32, weight: .bold)).foregroundStyle(Color.whappyBlue) }.frame(width: size, height: size) }
}
