import SwiftUI
import UIKit

struct ContentView: View {
    @EnvironmentObject private var store: WhappyStore

    var body: some View {
        TabView(selection: $store.selectedTab) {
            NavigationStack { HomeView() }
                .tabItem { Label("Accueil", systemImage: "house.fill") }
                .tag(WhappyTab.home)

            NavigationStack { MessagesView() }
                .tabItem { Label("Messages", systemImage: "message.fill") }
                .badge(store.unreadCount)
                .tag(WhappyTab.messages)

            NavigationStack { MarketView() }
                .tabItem { Label("Marché", systemImage: "storefront.fill") }
                .tag(WhappyTab.market)

            NavigationStack { LiveView() }
                .tabItem { Label("Live", systemImage: "video.fill") }
                .tag(WhappyTab.live)

            NavigationStack { ProfileView() }
                .tabItem { Label("Profil", systemImage: "person.crop.circle.fill") }
                .tag(WhappyTab.profile)
        }
        .background(Color.whappyBackground)
    }
}

private struct BrandHeader: View {
    let title: String
    let subtitle: String

    var body: some View {
        HStack(spacing: 12) {
            Image("WhappyMark")
                .resizable()
                .scaledToFit()
                .frame(width: 46, height: 46)
                .clipShape(RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.title2.bold()).foregroundStyle(Color.whappyInk)
                Text(subtitle).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button(action: {}) { Image(systemName: "bell.fill") }
                .buttonStyle(.bordered)
                .clipShape(Circle())
        }
    }
}

struct HomeView: View {
    @EnvironmentObject private var store: WhappyStore

    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                BrandHeader(title: "WHAPPY", subtitle: "Vos opportunités, maintenant")

                ZStack(alignment: .bottomLeading) {
                    LinearGradient(colors: [.whappyBlue, Color(red: 0, green: 0.37, blue: 0.62)], startPoint: .topLeading, endPoint: .bottomTrailing)
                    Circle().fill(.white.opacity(0.12)).frame(width: 170).offset(x: 220, y: -50)
                    VStack(alignment: .leading, spacing: 9) {
                        Label("WHAPPY PULSE", systemImage: "waveform.path.ecg")
                            .font(.caption.bold()).foregroundStyle(.white.opacity(0.85))
                        Text("Tout ce qui bouge\nautour de vous.")
                            .font(.system(size: 30, weight: .black, design: .rounded)).foregroundStyle(.white)
                        Text("Explorez, échangez, vendez et discutez.")
                            .font(.subheadline).foregroundStyle(.white.opacity(0.82))
                    }.padding(24)
                }
                .frame(height: 220)
                .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))

                Text("Explorer").font(.title3.bold()).foregroundStyle(Color.whappyInk)
                LazyVGrid(columns: columns, spacing: 12) {
                    ActionCard(title: "Marché", subtitle: "\(store.listings.count) offres", icon: "storefront.fill", color: .whappyBlue) { store.selectedTab = .market }
                    ActionCard(title: "En direct", subtitle: "\(store.liveRooms.count) lives", icon: "video.fill", color: .red) { store.selectedTab = .live }
                    ActionCard(title: "Messages", subtitle: "\(store.unreadCount) nouveau", icon: "message.fill", color: .purple) { store.selectedTab = .messages }
                    ActionCard(title: "Business", subtitle: "Gérez votre activité", icon: "briefcase.fill", color: .orange) {}
                }

                Text("Tendances près de vous").font(.title3.bold()).foregroundStyle(Color.whappyInk)
                ForEach(store.listings.prefix(2)) { listing in
                    ListingRow(listing: listing)
                }
            }
            .padding()
        }
        .background(Color.whappyBackground)
        .toolbar(.hidden, for: .navigationBar)
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
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(.white)
            .clipShape(RoundedRectangle(cornerRadius: 18))
        }
        .buttonStyle(.plain)
    }
}

struct MessagesView: View {
    @EnvironmentObject private var store: WhappyStore

    var body: some View {
        List(store.conversations) { conversation in
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
        }
        .listStyle(.plain)
        .navigationTitle("Messages")
        .navigationDestination(for: Conversation.self) { conversation in
            ConversationView(conversationID: conversation.id)
                .onAppear { store.markRead(conversation) }
        }
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button(action: {}) { Image(systemName: "square.and.pencil") } } }
    }
}

private struct ConversationView: View {
    @EnvironmentObject private var store: WhappyStore
    let conversationID: UUID
    @State private var draft = ""
    @State private var callMode: CallMode?

    private var conversation: Conversation? { store.conversations.first { $0.id == conversationID } }

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(conversation?.messages ?? []) { message in
                            HStack {
                                if message.mine { Spacer(minLength: 65) }
                                Text(message.text)
                                    .padding(.horizontal, 14).padding(.vertical, 10)
                                    .background(message.mine ? Color.whappyBlue : Color(.secondarySystemBackground))
                                    .foregroundStyle(message.mine ? .white : Color.whappyInk)
                                    .clipShape(RoundedRectangle(cornerRadius: 17))
                                if !message.mine { Spacer(minLength: 65) }
                            }.id(message.id)
                        }
                    }.padding()
                }
                .onChange(of: conversation?.messages.count) { _, _ in
                    if let id = conversation?.messages.last?.id { withAnimation { proxy.scrollTo(id) } }
                }
            }
            HStack(spacing: 10) {
                TextField("Votre message", text: $draft, axis: .vertical)
                    .textFieldStyle(.roundedBorder)
                Button {
                    store.send(draft, to: conversationID)
                    draft = ""
                } label: {
                    Image(systemName: "arrow.up.circle.fill").font(.system(size: 34))
                }.disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }.padding().background(.bar)
        }
        .navigationTitle(conversation?.name ?? "Discussion")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { callMode = .audio } label: {
                    Label("Appel audio", systemImage: CallMode.audio.systemImage)
                }
                Button { callMode = .video } label: {
                    Label("Appel vidéo", systemImage: CallMode.video.systemImage)
                }
            }
        }
        .sheet(item: $callMode) { mode in
            if let conversation {
                SystemCallView(contact: conversation, mode: mode)
            }
        }
    }
}

private struct SystemCallView: View {
    @Environment(\.dismiss) private var dismiss
    let contact: Conversation
    let mode: CallMode
    @State private var error: String?

    private var destination: URL? {
        let number = contact.phoneNumber.replacingOccurrences(of: " ", with: "")
        let scheme = mode == .audio ? "tel://" : "facetime://"
        return URL(string: scheme + number)
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 22) {
                Spacer()
                ZStack {
                    Circle().fill(Color.whappyBlue.opacity(0.14)).frame(width: 122, height: 122)
                    Image(systemName: mode.systemImage).font(.system(size: 44, weight: .semibold)).foregroundStyle(Color.whappyBlue)
                }
                Text(contact.name).font(.title.bold()).foregroundStyle(Color.whappyInk)
                Text(mode == .audio ? "Appel audio sécurisé" : "Appel vidéo")
                    .font(.subheadline).foregroundStyle(.secondary)
                Text(contact.phoneNumber).font(.callout).foregroundStyle(.secondary)

                Button {
                    startCall()
                } label: {
                    Label(mode == .audio ? "Appeler" : "Démarrer FaceTime", systemImage: mode.systemImage)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(mode == .audio ? .green : .whappyBlue)
                .controlSize(.large)
                .padding(.top, 10)

                Text(mode == .audio
                    ? "L’appel s’ouvre dans l’app Téléphone de votre iPhone."
                    : "L’appel vidéo s’ouvre dans FaceTime si votre contact est disponible.")
                    .multilineTextAlignment(.center)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 30)
                if let error {
                    Text(error).font(.footnote).foregroundStyle(.red).multilineTextAlignment(.center)
                }
                Spacer()
            }
            .padding(28)
            .background(Color.whappyBackground)
            .navigationTitle(mode.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fermer") { dismiss() }
                }
            }
        }
    }

    private func startCall() {
        guard let destination, UIApplication.shared.canOpenURL(destination) else {
            error = "Cet appel doit être lancé depuis un iPhone configuré pour Téléphone ou FaceTime."
            return
        }
        UIApplication.shared.open(destination)
    }
}

struct MarketView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var search = ""

    private var filtered: [Listing] {
        guard !search.isEmpty else { return store.listings }
        return store.listings.filter { $0.title.localizedCaseInsensitiveContains(search) || $0.place.localizedCaseInsensitiveContains(search) }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                ForEach(filtered) { ListingRow(listing: $0) }
            }.padding()
        }
        .background(Color.whappyBackground)
        .navigationTitle("Marché")
        .searchable(text: $search, prompt: "Produit, service ou quartier")
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button(action: {}) { Label("Vendre", systemImage: "plus") } } }
    }
}

private struct ListingRow: View {
    let listing: Listing

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 16).fill(Color.whappyBlue.opacity(0.1))
                Image(systemName: listing.icon).font(.system(size: 30)).foregroundStyle(Color.whappyBlue)
            }.frame(width: 82, height: 82)
            VStack(alignment: .leading, spacing: 5) {
                Text(listing.title).font(.headline).foregroundStyle(Color.whappyInk).lineLimit(2)
                Text(listing.price).font(.subheadline.bold()).foregroundStyle(listing.acceptsTrade ? .orange : Color.whappyBlue)
                Label("\(listing.place) · \(listing.seller)", systemImage: "mappin.and.ellipse")
                    .font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
        }
        .padding(12).background(.white).clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

struct LiveView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var showingStudio = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                ForEach(store.liveRooms) { room in
                    ZStack(alignment: .bottomLeading) {
                        LinearGradient(colors: [.black.opacity(0.35), .black.opacity(0.88)], startPoint: .top, endPoint: .bottom)
                        Image(systemName: room.icon).font(.system(size: 74)).foregroundStyle(.white.opacity(0.3)).frame(maxWidth: .infinity, maxHeight: .infinity)
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text("LIVE").font(.caption2.bold()).padding(.horizontal, 8).padding(.vertical, 4).background(.red).clipShape(Capsule())
                                Label(room.viewers.formatted(), systemImage: "eye.fill").font(.caption)
                            }
                            Text(room.title).font(.title3.bold())
                            Text("\(room.host) · \(room.category)").font(.caption).foregroundStyle(.white.opacity(0.75))
                        }.foregroundStyle(.white).padding(18)
                    }
                    .frame(height: 230).clipShape(RoundedRectangle(cornerRadius: 24))
                }
            }.padding()
        }
        .background(Color.whappyBackground)
        .navigationTitle("En direct")
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { showingStudio = true } label: { Label("Créer", systemImage: "video.badge.plus") } } }
        .sheet(isPresented: $showingStudio) { LiveStudioView() }
    }
}

private struct LiveStudioView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var category = "Business"

    var body: some View {
        NavigationStack {
            Form {
                Section("Votre direct") {
                    TextField("Titre", text: $title)
                    Picker("Catégorie", selection: $category) {
                        ForEach(["Business", "Mode", "Tech", "Maison"], id: \.self) { Text($0) }
                    }
                }
                Section { Label("La caméra et le microphone seront demandés au démarrage.", systemImage: "lock.shield.fill") }
            }
            .navigationTitle("Studio Live")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Continuer") { dismiss() }.disabled(title.isEmpty) }
            }
        }
    }
}

struct ProfileView: View {
    var body: some View {
        List {
            Section {
                HStack(spacing: 16) {
                    InitialsAvatar(text: "CB", size: 62)
                    VStack(alignment: .leading) {
                        Text("Cyril Bokilo").font(.title3.bold())
                        Text("Compte WHAPPY").font(.subheadline).foregroundStyle(.secondary)
                    }
                }.padding(.vertical, 8)
            }
            Section("Votre activité") {
                Label("Ma boutique", systemImage: "storefront.fill")
                Label("Mes commandes", systemImage: "shippingbox.fill")
                Label("Mon Double", systemImage: "sparkles")
            }
            Section("Réglages") {
                Label("Notifications", systemImage: "bell.badge.fill")
                Label("Confidentialité et sécurité", systemImage: "lock.shield.fill")
                Label("Aide", systemImage: "questionmark.circle.fill")
            }
            Section { Text("WHAPPY iOS 1.0.0").foregroundStyle(.secondary) }
        }
        .navigationTitle("Profil")
    }
}

private struct InitialsAvatar: View {
    let text: String
    var size: CGFloat = 48

    var body: some View {
        ZStack {
            Circle().fill(Color.whappyBlue.opacity(0.14))
            Text(text).font(.system(size: size * 0.32, weight: .bold)).foregroundStyle(Color.whappyBlue)
        }.frame(width: size, height: size)
    }
}
