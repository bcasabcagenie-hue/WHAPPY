import AVFoundation
import AVKit
import AudioToolbox
import CoreImage.CIFilterBuiltins
import CryptoKit
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

    init() {
        WapiChrome.install()
    }

    private func ui(_ french: String, _ english: String, _ lingala: String) -> String {
        store.interfaceLanguage.text(french, english, lingala)
    }

    var body: some View {
        TabView(selection: $store.selectedTab) {
            NavigationStack { MessagesView() }
                .tabItem { Label(ui("Messages", "Messages", "Nsango"), systemImage: "message.fill") }.badge(store.unreadCount).tag(WhappyTab.messages)
            NavigationStack { CallsView() }
                .tabItem { Label(ui("Appels", "Calls", "Mabéle"), systemImage: "phone.fill") }.tag(WhappyTab.calls)
            NavigationStack {
                if store.activeBusinessMode { BusinessWorkspaceView() }
                else { UpdatesView() }
            }
                .tabItem {
                    Label(
                        store.activeBusinessMode ? ui("Business", "Business", "Mombongo") : ui("Actus", "Updates", "Sango"),
                        systemImage: store.activeBusinessMode ? "briefcase.fill" : "sparkles"
                    )
                }
                .tag(WhappyTab.actus)
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
            store.rememberRecentSpace(tab)
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
    @EnvironmentObject private var store: WhappyStore
    let subtitle: String
    @State private var showActivity = false

    var body: some View {
        HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 5) {
                        Text("WAPI")
                            .font(.system(size: 23, weight: .bold))
                            .tracking(-0.65)
                            .foregroundStyle(Color.whappyInk)
                        Circle().fill(WapiColor.sky).frame(width: 6, height: 6)
                        if store.activeBusinessMode {
                            Text("BUSINESS")
                                .font(.system(size: 7, weight: .bold))
                                .tracking(0.55)
                                .foregroundStyle(WapiColor.deepBlue)
                                .padding(.horizontal, 7).padding(.vertical, 3)
                                .background(WapiColor.blueMist)
                                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                        }
                    }
                    Text(store.activeBusinessMode ? (store.business?.name ?? "Compte professionnel") : subtitle)
                        .font(.system(size: 9, weight: store.activeBusinessMode ? .semibold : .regular))
                        .foregroundStyle(WapiColor.secondaryText)
                        .lineLimit(1)
                }
                Spacer()
                Button { showActivity = true } label: {
                    Image(systemName: "bell.fill")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(WapiColor.night)
                        .frame(width: 42, height: 42)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                }
                .buttonStyle(WapiPressableButtonStyle())
        }
        .frame(height: 64)
        .padding(.horizontal, WapiSpacing.screen)
        .background(WapiColor.canvas)
        .sheet(isPresented: $showActivity) { ActivityCenterView() }
    }
}

struct HomeView: View {
    @EnvironmentObject private var store: WhappyStore
    @StateObject private var sponsoredAds = WapiSponsoredAdsIOSStore()
    @State private var composingMoment = false
    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                BrandHeader(subtitle: "Connecté")
                Button { store.selectedTab = .messages } label: {
                    HStack(spacing: 13) {
                        Image(systemName: "message.fill")
                            .foregroundStyle(.white)
                            .frame(width: 46, height: 46)
                            .background(LinearGradient(colors: [WapiColor.sky, WapiColor.blue, WapiColor.violet], startPoint: .topLeading, endPoint: .bottomTrailing))
                            .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Messages").font(.headline).foregroundStyle(Color.whappyInk)
                            Text("Toutes vos discussions").font(.caption).foregroundStyle(WapiColor.secondaryText)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(WapiColor.secondaryText)
                    }
                    .padding(15)
                    .wapiFlowSurface()
                }.buttonStyle(WapiPressableButtonStyle())

                if let campaign = sponsoredAds.campaigns.first {
                    WapiSponsoredCampaignIOSCard(
                        campaign: campaign,
                        onOpen: {
                            sponsoredAds.recordClick(campaign)
                            store.selectedTab = .market
                        },
                        onDismiss: { sponsoredAds.dismiss(campaign) }
                    )
                }

                Text("Découvrir").font(.subheadline.weight(.semibold)).foregroundStyle(Color.whappyInk)
                LazyVGrid(columns: columns, spacing: 12) {
                    ActionCard(title: "Marché", subtitle: "\(store.listings.count) offres", icon: "storefront.fill", color: .whappyBlue) { store.selectedTab = .market }
                    ActionCard(title: "En direct", subtitle: "\(store.liveRooms.filter(\.live).count) lives", icon: "video.fill", color: .red) { store.selectedTab = .live }
                    ActionCard(title: "Messages", subtitle: "\(store.unreadCount) nouveau", icon: "message.fill", color: .purple) { store.selectedTab = .messages }
                    ActionCard(title: "Services", subtitle: "Wallet et demandes", icon: "wallet.pass.fill", color: .orange) { store.selectedTab = .services }
                    ActionCard(title: "Jeux", subtitle: "Défis et duels", icon: "bolt.fill", color: .yellow) { store.selectedTab = .games }
                }
                HStack { Text("Publications").font(.subheadline.weight(.semibold)).foregroundStyle(Color.whappyInk); Spacer(); Button { composingMoment = true } label: { Label("Publier", systemImage: "plus.circle.fill") } }
                ForEach(store.moments) { moment in
                    VStack(alignment: .leading, spacing: 8) { HStack { InitialsAvatar(text: "CB", size: 38); VStack(alignment: .leading) { Text("Vous").font(.headline); Text(moment.createdAt, style: .relative).font(.caption).foregroundStyle(.secondary) } }; Text(moment.title).font(.title3.bold()).foregroundStyle(Color.whappyInk); Text(moment.text).foregroundStyle(.secondary) }.frame(maxWidth: .infinity, alignment: .leading).padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 18))
                }
                Text("Près de vous").font(.subheadline.weight(.semibold)).foregroundStyle(Color.whappyInk)
                ForEach(store.listings.prefix(2)) { listing in
                    NavigationLink(value: listing) { ListingRow(listing: listing) }.buttonStyle(WapiPressableButtonStyle())
                }
                Text("Services").font(.subheadline.weight(.semibold)).foregroundStyle(Color.whappyInk)
                VStack(spacing: 9) {
                    ActionCard(title: "Radio & podcasts", subtitle: "Émissions, chaînes et écoute continue", icon: "dot.radiowaves.left.and.right", color: .whappyBlue) { store.selectedTab = .actus }
                    ActionCard(title: "Jumeau numérique", subtitle: "Votre identité, vos consentements et vos créations", icon: "sparkles", color: .purple) { store.selectedTab = .profile }
                    ActionCard(title: "Lives en cours", subtitle: store.liveRooms.filter(\.live).isEmpty ? "Soyez le premier à démarrer" : "\(store.liveRooms.filter(\.live).count) direct(s) à rejoindre", icon: "video.fill", color: .red) { store.selectedTab = .live }
                }
            }.padding(.horizontal, WapiSpacing.screen).padding(.bottom, 24)
        }
        .background(Color.whappyBackground)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: Listing.self) { ListingDetailView(listing: $0) }
        .sheet(isPresented: $composingMoment) { MomentComposerView() }
        .onAppear { sponsoredAds.start() }
        .onDisappear { sponsoredAds.stop() }
    }
}

private struct WapiSponsoredCampaignIOS: Identifiable {
    let id: String
    let ownerID: String
    let pageName: String
    let title: String
    let creative: String
    let cta: String
    let city: String
    let countryCode: String
    let rankReasons: [String]
    let rankingEngine: String
    let rankingVersion: String
}

@MainActor
private final class WapiSponsoredAdsIOSStore: ObservableObject {
    @Published var campaigns: [WapiSponsoredCampaignIOS] = []
    private var loading = false

    func start() {
        guard !loading else { return }
        loading = true
        Functions.functions(region: "europe-west1").httpsCallable("getPersonalizedAds").call([
            "placement": "inbox",
            "limit": 12,
            "localeCountry": Locale.current.region?.identifier.uppercased() ?? "",
        ]) { [weak self] result, _ in
            Task { @MainActor in
                guard let self else { return }
                self.loading = false
                let payload = result?.data as? [String: Any] ?? [:]
                let values = (payload["ads"] as? [[String: Any]] ?? []).compactMap { data -> WapiSponsoredCampaignIOS? in
                    let id = data["id"] as? String ?? ""
                    let title = data["title"] as? String ?? ""
                    let creative = data["creative"] as? String ?? ""
                    guard !id.isEmpty, !title.isEmpty, !creative.isEmpty else { return nil }
                    return WapiSponsoredCampaignIOS(
                        id: id,
                        ownerID: data["ownerId"] as? String ?? "",
                        pageName: data["pageName"] as? String ?? "Business WAPI",
                        title: title,
                        creative: creative,
                        cta: data["cta"] as? String ?? "Découvrir",
                        city: data["city"] as? String ?? "",
                        countryCode: data["countryCode"] as? String ?? "",
                        rankReasons: (data["rankReasons"] as? [String] ?? []).filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty },
                        rankingEngine: data["rankingEngine"] as? String ?? "ELEPHANT",
                        rankingVersion: data["rankingVersion"] as? String ?? "1.0"
                    )
                }
                self.campaigns = values
                if let first = values.first { self.recordImpression(first) }
            }
        }
    }

    func stop() {}

    func recordClick(_ campaign: WapiSponsoredCampaignIOS) { record(campaign, type: "click") }

    func dismiss(_ campaign: WapiSponsoredCampaignIOS) {
        campaigns.removeAll { $0.id == campaign.id }
        record(campaign, type: "dismiss")
    }

    private func recordImpression(_ campaign: WapiSponsoredCampaignIOS) { record(campaign, type: "impression") }

    private func record(_ campaign: WapiSponsoredCampaignIOS, type: String) {
        guard let userID = Auth.auth().currentUser?.uid, !userID.isEmpty, campaign.ownerID != userID else { return }
        Functions.functions(region: "europe-west1").httpsCallable("recordAdBehavior").call([
            "campaignId": campaign.id,
            "type": type,
        ]) { _, _ in }
    }
}

private struct WapiSponsoredCampaignIOSCard: View {
    let campaign: WapiSponsoredCampaignIOS
    let onOpen: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: "megaphone.fill")
                    .foregroundStyle(.white)
                    .frame(width: 38, height: 38)
                    .background(LinearGradient(colors: [WapiColor.sky, WapiColor.blue], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(campaign.pageName).font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk)
                    Text("SPONSORISÉ · \(campaign.city.isEmpty ? "DIFFUSION NATIONALE" : campaign.city.uppercased())")
                        .font(.system(size: 8, weight: .bold)).tracking(0.45).foregroundStyle(WapiColor.secondaryText)
                }
                Spacer()
                Text("\(campaign.rankingEngine) · ANNONCE").font(.system(size: 8, weight: .bold)).foregroundStyle(Color.whappyBlue)
                    .padding(.horizontal, 8).padding(.vertical, 5).background(WapiColor.blueMist).clipShape(Capsule())
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(WapiColor.secondaryText)
                        .frame(width: 32, height: 32)
                        .background(WapiColor.blueMist.opacity(0.65))
                        .clipShape(Circle())
                }
                .accessibilityLabel("Masquer cette publicité")
            }
            Text(campaign.title).font(.title3.weight(.bold)).foregroundStyle(Color.whappyInk)
            Text(campaign.creative).font(.subheadline).foregroundStyle(WapiColor.secondaryText).lineLimit(3)
            if !campaign.rankReasons.isEmpty {
                Label("Pourquoi cette publicité ? " + campaign.rankReasons.prefix(2).joined(separator: " · "), systemImage: "info.circle.fill")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(WapiColor.secondaryText)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 7)
                    .background(WapiColor.blueMist.opacity(0.65))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
            Button(action: onOpen) {
                HStack {
                    Spacer()
                    Text(campaign.cta.uppercased() + "  ›").font(.caption.weight(.bold)).foregroundStyle(Color.whappyBlue)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .background(.white)
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(WapiColor.blue.opacity(0.16)))
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: WapiShadow.color, radius: 8, y: 4)
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
                HStack {
                    Image(systemName: icon)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(color)
                        .frame(width: 40, height: 40)
                        .background(color.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                    Spacer()
                    Image(systemName: "arrow.up.right")
                        .font(.caption.bold())
                        .foregroundStyle(WapiColor.secondaryText)
                }
                Text(title).font(.system(.headline, design: .rounded).weight(.semibold)).foregroundStyle(Color.whappyInk)
                Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .wapiFlowSurface()
        }.buttonStyle(WapiPressableButtonStyle())
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
                            .font(.system(size: 27, weight: .semibold))
                            .foregroundStyle(Color.whappyInk)
                        Text("Stories, directs et chaînes")
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
                    .buttonStyle(WapiPressableButtonStyle())
                    .accessibilityLabel("Créer un direct")
                }

                updatesSectionTitle("Stories", subtitle: "Visibles pendant 24 h")
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 14) {
                        let ownStories = storyGroups.first(where: { $0.first?.authorID == store.firebaseUserID }) ?? []
                        let confirmedOwnStories = ownStories.filter { !store.pendingStoryIDs.contains($0.id) }
                        Button {
                            if let latest = confirmedOwnStories.last { selectedStory = latest }
                            else if !ownStories.isEmpty { return }
                            else { storyComposerPresented = true }
                        } label: {
                            WapiStoryCircle(story: confirmedOwnStories.last, title: "Ma Story", isOwn: true, hasUnseen: false, showAdd: ownStories.isEmpty)
                        }
                        .buttonStyle(WapiPressableButtonStyle())
                        ForEach(Array(storyGroups.filter { $0.first?.authorID != store.firebaseUserID }.enumerated()), id: \.offset) { _, group in
                            if let latest = group.last {
                                Button { selectedStory = latest } label: {
                                    WapiStoryCircle(story: latest, title: latest.authorName, isOwn: false, hasUnseen: group.contains(where: { !$0.viewed }), showAdd: false)
                                }
                                .buttonStyle(WapiPressableButtonStyle())
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }

                updatesSectionTitle("En direct", subtitle: activeLives.isEmpty ? "Aucun direct pour le moment" : "\(activeLives.count) diffusion\(activeLives.count > 1 ? "s" : "") maintenant")
                if activeLives.isEmpty {
                    Button { store.selectedTab = .live } label: {
                        updatesRow(icon: "video.fill", title: "Démarrer un direct", subtitle: "Entrez en direct en un geste", accent: .red)
                    }.buttonStyle(WapiPressableButtonStyle())
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(activeLives) { room in
                                Button { store.selectedTab = .live } label: {
                                    VStack(alignment: .leading, spacing: 8) {
                                        HStack {
                                            Text("● EN DIRECT").font(.caption2.weight(.bold)).foregroundStyle(.red)
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
                                }.buttonStyle(WapiPressableButtonStyle())
                            }
                        }.padding(.vertical, 4)
                    }
                }

                updatesSectionTitle("Chaînes", subtitle: "Chaînes suivies")
                VStack(spacing: 2) {
                    ForEach(followedChannels.prefix(4)) { channel in
                        NavigationLink(value: channel) {
                            updatesRow(
                                icon: channel.subscribed ? "dot.radiowaves.left.and.right" : "megaphone.fill",
                                title: channel.name,
                                subtitle: channel.posts.last?.text ?? channel.description,
                                accent: .whappyBlue
                            )
                        }.buttonStyle(WapiPressableButtonStyle())
                    }
                    if followedChannels.isEmpty {
                        Button { store.selectedTab = .messages; store.pendingSearch = "" } label: {
                            updatesRow(icon: "dot.radiowaves.left.and.right", title: "Découvrir les chaînes", subtitle: "Suivez uniquement ce qui vous intéresse", accent: .whappyBlue)
                        }.buttonStyle(WapiPressableButtonStyle())
                    }
                }
                .padding(5)
                .wapiPanel()

                updatesSectionTitle("Découvrir", subtitle: "Services WAPI")
                VStack(spacing: 2) {
                    Button { store.selectedTab = .live } label: { updatesRow(icon: "video.fill", title: "Lives", subtitle: "Voir et créer des directs", accent: .red) }.buttonStyle(WapiPressableButtonStyle())
                    Button { store.selectedTab = .games } label: { updatesRow(icon: "gamecontroller.fill", title: "Jeux", subtitle: "Parties, défis et tournois", accent: .indigo) }.buttonStyle(WapiPressableButtonStyle())
                    Button { store.selectedTab = .market } label: { updatesRow(icon: "storefront.fill", title: "Près de vous", subtitle: "Boutiques et offres locales", accent: .green) }.buttonStyle(WapiPressableButtonStyle())
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
        HStack(spacing: 10) {
            Capsule()
                .fill(LinearGradient(colors: [WapiColor.sky, WapiColor.blue, WapiColor.violet], startPoint: .top, endPoint: .bottom))
                .frame(width: 4, height: 34)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(.headline, design: .rounded).weight(.semibold)).foregroundStyle(Color.whappyInk)
                Text(subtitle).font(.caption2).foregroundStyle(WapiColor.secondaryText)
            }
        }.padding(.top, 5)
    }

    private func updatesRow(icon: String, title: String, subtitle: String, accent: Color) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(accent)
                .frame(width: 43, height: 43)
                .background(LinearGradient(colors: [accent.opacity(0.16), WapiColor.blueMist], startPoint: .topLeading, endPoint: .bottomTrailing))
                .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk).lineLimit(1)
                Text(subtitle).font(.caption).foregroundStyle(WapiColor.secondaryText).lineLimit(1)
            }
            Spacer()
            Image(systemName: "arrow.right")
                .font(.caption.weight(.bold))
                .foregroundStyle(accent.opacity(0.78))
                .frame(width: 30, height: 30)
                .background(accent.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
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
                        .font(.system(size: 28, weight: .bold, design: .rounded))
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
                        .buttonStyle(WapiPressableButtonStyle())
                        Button { audioImporterPresented = true } label: {
                            Label("Audio", systemImage: "waveform")
                                .font(.headline)
                                .foregroundStyle(Color.whappyBlue)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 15)
                                .background(Color.whappyBlue.opacity(0.10))
                                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }
                        .buttonStyle(WapiPressableButtonStyle())
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
                WapiCachedRemoteImage(url: url, contentMode: .fit) {
                    ProgressView().tint(.white)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
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
                    Button { dismiss() } label: { Image(systemName: "xmark").font(.headline).foregroundStyle(.white).padding(10).background(.black.opacity(0.35)).clipShape(Circle()) }.buttonStyle(WapiPressableButtonStyle())
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
    @State private var recentAppsExpanded = false
    @State private var recentPullReady = true
    private var filtered: [Conversation] { store.accountConversations.filter { "\($0.name) \($0.lastMessage) \($0.phoneNumber)".matchesWhappySearch(search) } }
    private var filteredChannels: [WhappyChannel] { store.channels.filter { "\($0.name) \($0.description) \($0.category) \($0.ownerName)".matchesWhappySearch(search) }.sorted { ($0.subscribed ? 1 : 0, $0.memberCount) > ($1.subscribed ? 1 : 0, $1.memberCount) } }

    var body: some View {
        ZStack {
        VStack(spacing: 0) {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(section == 0 ? (store.activeBusinessMode ? "Messages Business" : "Messages") : "Chaînes")
                        .font(.system(size: 27, weight: .semibold))
                        .foregroundStyle(Color.whappyInk)
                    Text(section == 0 ? (store.activeBusinessMode ? "Clients et équipe" : "Discussions") : "Chaînes suivies")
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
                        .background(WapiColor.night)
                        .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                }
                .buttonStyle(WapiPressableButtonStyle())
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
                                Text("BUSINESS").font(.system(size: 8, weight: .bold)).foregroundStyle(WapiColor.sky)
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
                    .clipShape(RoundedRectangle(cornerRadius: WapiRadius.panel, style: .continuous))
                }
                .buttonStyle(WapiPressableButtonStyle())
                .padding(.horizontal, WapiSpacing.screen)
                .padding(.bottom, 10)
            }

            HStack(spacing: 4) {
                messageSectionButton("Discussions", value: 0)
                messageSectionButton("Chaînes", value: 1)
            }
            .padding(4)
            .background(WapiColor.secondarySurface.opacity(0.82))
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
                    }.buttonStyle(WapiPressableButtonStyle())
                }
            }
            .padding(.horizontal, 14)
            .frame(height: 50)
            .background(WapiColor.surface)
            .clipShape(RoundedRectangle(cornerRadius: WapiRadius.control, style: .continuous))
            .padding(.horizontal, WapiSpacing.screen)
            .padding(.vertical, 10)

            if section == 0 {
                if filtered.isEmpty {
                    ScrollView {
                        VStack(spacing: 18) {
                            recentPullReader
                            ContentUnavailableView(
                                search.isEmpty ? "Aucune conversation" : "Aucun résultat",
                                systemImage: "message",
                                description: Text(search.isEmpty ? "Ajoutez un contact pour commencer à discuter sur WAPI." : "Essayez un autre nom ou contenu récent.")
                            )
                            .padding(.top, 70)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, WapiSpacing.screen)
                    }
                    .coordinateSpace(name: "wapiMessagesPull")
                    .onPreferenceChange(WapiMessagePullOffsetKey.self, perform: handleRecentPull)
                    .scrollBounceBehavior(.always)
                } else {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            recentPullReader
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Conversations").font(.headline).foregroundStyle(Color.whappyInk)
                                    Text("\(filtered.count) échange\(filtered.count > 1 ? "s" : "")")
                                        .font(.caption2).foregroundStyle(WapiColor.secondaryText)
                                }
                                Spacer()
                                Text("WAPI PRIVÉ")
                                    .font(.caption2.weight(.bold))
                                    .foregroundStyle(Color.whappyBlue)
                            }
                            .padding(.horizontal, WapiSpacing.screen)
                            .padding(.vertical, 8)

                            ForEach(filtered) { conversation in
                                VStack(spacing: 0) {
                                    NavigationLink(value: conversation) {
                                        WapiConversationRow(conversation: conversation)
                                    }
                                    .buttonStyle(WapiPressableButtonStyle())
                                    .contextMenu {
                                        Button { store.toggleUnread(conversation) } label: {
                                            Label(conversation.unread ? "Marquer comme lu" : "Marquer non lu", systemImage: conversation.unread ? "envelope.open" : "envelope.badge")
                                        }
                                        Button(role: .destructive) { store.deleteConversation(conversation) } label: {
                                            Label("Supprimer", systemImage: "trash")
                                        }
                                    }
                                    if conversation.id != filtered.last?.id {
                                        Divider()
                                            .padding(.leading, WapiSpacing.screen + 58)
                                    }
                                }
                            }
                        }
                        .padding(.bottom, 24)
                    }
                    .coordinateSpace(name: "wapiMessagesPull")
                    .onPreferenceChange(WapiMessagePullOffsetKey.self, perform: handleRecentPull)
                    .scrollIndicators(.hidden)
                    .scrollBounceBehavior(.always)
                }
            } else {
                ChannelDirectoryView(channels: filteredChannels)
            }
        }
        .background(Color.whappyBackground.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: Conversation.self) { conversation in ConversationView(conversationID: conversation.id).onAppear { store.markRead(conversation) } }
        .navigationDestination(for: WhappyChannel.self) { channel in ChannelView(channelID: channel.id) }
        .navigationDestination(
            isPresented: Binding(
                get: { store.pendingConversationID != nil },
                set: { presented in if !presented { store.pendingConversationID = nil } }
            )
        ) {
            if let conversationID = store.pendingConversationID {
                ConversationView(conversationID: conversationID)
                    .onAppear {
                        if let conversation = store.accountConversations.first(where: { $0.id == conversationID }) {
                            store.markRead(conversation)
                        }
                    }
            }
        }
        .sheet(isPresented: $composing) { NewConversationView(initialPhone: linkedPhone) }
        .fullScreenCover(isPresented: $creatingChannel) { NewChannelView() }
        .sheet(item: $linkedChannel) { channel in NavigationStack { ChannelView(channelID: channel.id) } }
        .onAppear { consumePendingLinks() }
        .onChange(of: store.pendingContactPhone) { _, _ in consumePendingLinks() }
        .onChange(of: store.pendingChannelID) { _, _ in consumePendingLinks() }
        .onChange(of: store.pendingSearch) { _, _ in consumePendingLinks() }
        }
        .fullScreenCover(isPresented: $recentAppsExpanded) {
            WapiInlineRecentAppsIOS { recentAppsExpanded = false }
                .environmentObject(store)
        }
    }

    private var recentPullReader: some View {
        GeometryReader { proxy in
            Color.clear.preference(
                key: WapiMessagePullOffsetKey.self,
                value: proxy.frame(in: .named("wapiMessagesPull")).minY
            )
        }
        .frame(height: 0)
    }

    private func handleRecentPull(_ offset: CGFloat) {
        guard section == 0, search.isEmpty else { recentPullReady = true; return }
        // One full-screen presentation at the threshold. No inline panel and
        // no waiting for the ScrollView bounce animation to finish.
        if offset <= 4 { recentPullReady = true }
        guard recentPullReady, !recentAppsExpanded, offset >= 64 else { return }
        recentPullReady = false
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        withAnimation(.snappy(duration: 0.20)) { recentAppsExpanded = true }
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
        .buttonStyle(WapiPressableButtonStyle())
    }

    private func consumePendingLinks() {
        if let phone = store.pendingContactPhone { linkedPhone = phone; composing = true; section = 0; store.pendingContactPhone = nil }
        if let id = store.pendingChannelID, let channel = store.channels.first(where: { $0.id == id }) { linkedChannel = channel; section = 1; store.pendingChannelID = nil }
        if let query = store.pendingSearch { search = query; section = 1; store.pendingSearch = nil }
    }
}

private struct WapiMessagePullOffsetKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

/// Full-screen recent spaces revealed by the Messages scroll itself. The
/// surface occupies the complete WAPI viewport and closes with an upward drag.
private struct WapiInlineRecentAppsIOS: View {
    @EnvironmentObject private var store: WhappyStore
    let onClose: () -> Void
    @State private var closeOffset: CGFloat = 0
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 3)

    private struct Item: Identifiable {
        let id: String
        let title: String
        let icon: String
        let tab: WhappyTab
    }

    private let items = [
        Item(id: "live", title: "Direct", icon: "video.fill", tab: .live),
        Item(id: "actus", title: "Actus", icon: "sparkles", tab: .actus),
        Item(id: "wia", title: "WIA", icon: "wand.and.stars", tab: .wia),
        Item(id: "games", title: "Jeux", icon: "gamecontroller.fill", tab: .games),
        Item(id: "market", title: "Marché", icon: "storefront.fill", tab: .market),
        Item(id: "services", title: "Services", icon: "wallet.pass.fill", tab: .services),
        Item(id: "profile", title: "Jumeau", icon: "person.crop.circle.badge.checkmark", tab: .profile),
        Item(id: "home", title: "Accueil", icon: "house.fill", tab: .home),
    ]

    private var recents: [Item] {
        let values = store.recentSpaces.compactMap { tab in items.first(where: { $0.tab == tab }) }
        return Array((values + items).reduce(into: [Item]()) { result, item in
            if !result.contains(where: { $0.id == item.id }) { result.append(item) }
        }.prefix(6))
    }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.03, green: 0.09, blue: 0.15), Color(red: 0.04, green: 0.18, blue: 0.27)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            VStack(spacing: 18) {
            HStack(spacing: 10) {
                Image(systemName: "clock.arrow.circlepath")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(WapiColor.sky)
                    .frame(width: 34, height: 34)
                    .background(.white.opacity(0.10))
                    .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text("Récents WAPI").font(.headline.weight(.bold)).foregroundStyle(.white)
                    Text("Reprenez instantanément là où vous étiez").font(.caption2).foregroundStyle(.white.opacity(0.62))
                }
                Spacer()
                Button(action: onClose) {
                    Image(systemName: "xmark").font(.caption.bold()).foregroundStyle(.white.opacity(0.72)).frame(width: 32, height: 32)
                }
                .buttonStyle(WapiPressableButtonStyle())
            }
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(recents) { item in
                    Button {
                        onClose()
                        store.rememberRecentSpace(item.tab)
                        store.selectedTab = item.tab
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: item.icon)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(.white)
                                .frame(width: 32, height: 32)
                                .background(Color.whappyBlue)
                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                            Text(item.title).font(.caption2.weight(.bold)).foregroundStyle(.white).lineLimit(1)
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 9)
                        .frame(height: 58)
                        .background(.white.opacity(0.09))
                        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(.white.opacity(0.10)))
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    .buttonStyle(WapiPressableButtonStyle())
                }
            }
            Spacer(minLength: 12)
            Label("Glissez vers le haut pour revenir aux messages", systemImage: "chevron.up")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white.opacity(0.62))
            Capsule().fill(.white.opacity(0.34)).frame(width: 46, height: 5)
                .padding(.bottom, 8)
            }
            .padding(.horizontal, WapiSpacing.screen)
            .padding(.top, 18)
        }
        .contentShape(Rectangle())
        .offset(y: closeOffset)
        .opacity(1 - min(abs(closeOffset) / 420, 0.22))
        .gesture(DragGesture(minimumDistance: 18)
            .onChanged { value in
                guard value.translation.height < 0 else { return }
                closeOffset = value.translation.height * 0.45
            }
            .onEnded { value in
                if value.translation.height < -88 {
                    onClose()
                } else {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.84)) { closeOffset = 0 }
                }
            }
        )
    }
}

/// Pulling down from the top of Messages reveals recent WAPI spaces, then the
/// complete app directory. The drawer is shared with the explicit Apps entry.
private struct WapiRecentAppsDrawerIOS: View {
    @EnvironmentObject private var store: WhappyStore
    @Environment(\.dismiss) private var dismiss
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 3)

    private struct Item: Identifiable {
        let id: String
        let title: String
        let icon: String
        let tab: WhappyTab
    }

    private let items = [
        Item(id: "live", title: "Direct", icon: "video.fill", tab: .live),
        Item(id: "actus", title: "Actus", icon: "sparkles", tab: .actus),
        Item(id: "wia", title: "WIA", icon: "wand.and.stars", tab: .wia),
        Item(id: "games", title: "Jeux", icon: "gamecontroller.fill", tab: .games),
        Item(id: "market", title: "Marché", icon: "storefront.fill", tab: .market),
        Item(id: "services", title: "Services", icon: "wallet.pass.fill", tab: .services),
        Item(id: "profile", title: "Jumeau", icon: "person.crop.circle.badge.checkmark", tab: .profile),
        Item(id: "home", title: "Accueil", icon: "house.fill", tab: .home),
    ]

    private var recents: [Item] {
        let values = store.recentSpaces.compactMap { tab in items.first(where: { $0.tab == tab }) }
        return values.isEmpty ? Array(items.prefix(3)) : Array(values.prefix(6))
    }

    var body: some View {
        NavigationStack {
        ScrollView {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 9) {
                Image(systemName: "clock.arrow.circlepath")
                    .foregroundStyle(Color.whappyBlue)
                    .frame(width: 30, height: 30)
                    .background(Color.whappyBlue.opacity(0.10))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text("Récents").font(.title2.weight(.bold)).foregroundStyle(Color.whappyInk)
                    Text("Vos derniers espaces WAPI").font(.caption).foregroundStyle(WapiColor.secondaryText)
                }
            }
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(recents) { appButton($0, recent: true) }
            }

            Text("Toutes les apps WAPI").font(.headline).foregroundStyle(Color.whappyInk).padding(.top, 3)
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(items) { appButton($0, recent: false) }
            }
        }
        .padding(18)
        }
        .background(WapiColor.canvas)
        .navigationTitle("WAPI Apps")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fermer") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private func appButton(_ item: Item, recent: Bool) -> some View {
        Button {
            store.rememberRecentSpace(item.tab)
            store.selectedTab = item.tab
            dismiss()
        } label: {
            VStack(spacing: 8) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: item.icon)
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(Color.whappyBlue)
                        .frame(width: 42, height: 42)
                        .background(Color.whappyBlue.opacity(0.10))
                        .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                    if recent { Circle().fill(Color.green).frame(width: 7, height: 7).offset(x: 2, y: -2) }
                }
                Text(item.title).font(.caption.weight(.semibold)).foregroundStyle(Color.whappyInk).lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 11)
            .background(.white)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(WapiPressableButtonStyle())
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
                    if conversation.displaysVerifiedBadge {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.caption)
                            .foregroundStyle(Color.wapiVerified)
                            .accessibilityLabel("Compte certifié")
                    }
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
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 4)
                            .background(Color.whappyBlue)
                            .clipShape(Capsule())
                    }
                }
            }
        }
        .padding(.horizontal, WapiSpacing.screen)
        .padding(.vertical, 11)
        .background(conversation.unread ? WapiColor.unreadSurface : Color.white)
        .overlay(alignment: .leading) {
            if conversation.unread {
                Capsule()
                    .fill(Color.whappyBlue)
                    .frame(width: 3, height: 32)
                    .padding(.leading, 3)
            }
        }
        .contentShape(Rectangle())
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
                Section("Identité publique") {
                    WapiEditorTextFieldIOS(title: "Nom de la chaîne", text: $name)
                    WapiEditorTextFieldIOS(title: "Description", text: $description, limit: 300, multiline: true)
                }
                Section("Catégorie") { Picker("Catégorie", selection: $category) { ForEach(categories, id: \.self) { Text($0) } } }
                Section { Label("Vous seul pourrez publier. Les abonnés pourront suivre et réagir.", systemImage: "shield.checkered").font(.footnote).foregroundStyle(.secondary) }
            }
            .navigationTitle("Nouvelle chaîne").navigationBarTitleDisplayMode(.inline)
            .scrollDismissesKeyboard(.interactively)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } } }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                WapiEditorActionIOS(title: "Créer la chaîne", busy: false,
                    enabled: name.trimmingCharacters(in: .whitespacesAndNewlines).count >= 3 && description.trimmingCharacters(in: .whitespacesAndNewlines).count >= 10 && description.count <= 300) {
                    store.createChannel(name: name, description: description, category: category); dismiss()
                }
            }
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
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 5) {
                        Text("Coordonnées").font(.title3.weight(.semibold)).foregroundStyle(Color.whappyInk)
                        Text("Retrouvez une personne et ouvrez une discussion.").font(.footnote).foregroundStyle(.secondary)
                    }

                    VStack(spacing: 0) {
                        TextField("Nom du contact", text: $name)
                            .textContentType(.name)
                            .padding(.horizontal, 15)
                            .frame(height: 54)
                        Divider().padding(.leading, 15)
                        HStack(spacing: 10) {
                            Picker("Pays", selection: $countryCode) {
                                ForEach(WhappyPhoneCountry.supported) { country in
                                    Text("\(country.flag) \(country.name)  \(country.code)").tag(country.code)
                                }
                            }
                            .labelsHidden()
                            .frame(width: 104, alignment: .leading)
                            Divider().frame(height: 28)
                            TextField("Numéro de téléphone", text: $phone)
                                .keyboardType(.phonePad)
                                .textContentType(.telephoneNumber)
                                .onChange(of: phone) { _, value in
                                    if case .contact(let scannedPhone) = WhappyDeepLink.parse(value) {
                                        applyPhoneForEditing(scannedPhone)
                                        return
                                    }
                                    let clean = String(value.filter(\.isNumber).prefix(15))
                                    if clean != value { phone = clean }
                                }
                        }
                        .padding(.horizontal, 15)
                        .frame(height: 56)
                    }
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(WapiColor.line.opacity(0.8)))

                    if let normalizedPhone {
                        Label("Numéro reconnu  ·  \(normalizedPhone)", systemImage: "checkmark.circle.fill")
                            .font(.footnote.weight(.semibold)).foregroundStyle(Color.whappyBlue)
                    } else if !phone.isEmpty {
                        Label("Vérifiez l’indicatif et le numéro", systemImage: "exclamationmark.circle.fill")
                            .font(.footnote).foregroundStyle(.orange)
                    }

                    Button {
                        if let normalizedPhone {
                            store.createConversation(name: name, phone: normalizedPhone)
                            dismiss()
                        }
                    } label: {
                        Text("Ajouter et ouvrir la discussion")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.whappyBlue)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).count < 2 || normalizedPhone == nil)

                    Text("AUTRES MOYENS").font(.caption2.weight(.bold)).tracking(0.5).foregroundStyle(.secondary).padding(.top, 4)
                    Button { scanning = true } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "qrcode.viewfinder")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundStyle(Color.whappyBlue)
                                .frame(width: 40, height: 40)
                                .background(WapiColor.blueMist)
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Scanner un code QR").font(.subheadline.weight(.semibold)).foregroundStyle(Color.whappyInk)
                                Text("Ouvrir directement un profil WAPI").font(.caption2).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(.secondary)
                        }
                        .padding(.horizontal, 12)
                        .frame(height: 64)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(WapiColor.line.opacity(0.8)))
                    }
                    .buttonStyle(WapiPressableButtonStyle())
                }
                .padding(.horizontal, WapiSpacing.screen)
                .padding(.vertical, 20)
            }
            .background(Color.whappyBackground)
            .navigationTitle("Ajouter un contact")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
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

struct WhappyScannerSheet: View {
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

private let wapiVoicePlaybackRequested = Notification.Name("wapi.voice.playbackRequested")

private struct WapiAudioMessagePlayer: View {
    let path: String
    @Environment(\.scenePhase) private var scenePhase
    @State private var playbackID = UUID()
    @State private var player: AVPlayer?
    @State private var currentTime: Double = 0
    @State private var duration: Double = 1
    @State private var rate: Float = 1
    @State private var playing = false
    @State private var wantsPlayback = false
    @State private var scrubbing = false
    @State private var seeking = false
    @State private var loading = false
    @State private var playbackError = false

    private var mediaURL: URL? {
        if let remote = URL(string: path), remote.scheme == "http" || remote.scheme == "https" { return remote }
        let local = URL(fileURLWithPath: path)
        return FileManager.default.fileExists(atPath: local.path) ? local : nil
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                Button { togglePlayback() } label: {
                    Image(systemName: wantsPlayback ? "pause.fill" : playbackError ? "arrow.clockwise" : "play.fill")
                        .font(.body.weight(.bold))
                        .frame(width: 44, height: 44)
                        .background(Color.white.opacity(0.18), in: Circle())
                }.accessibilityLabel(wantsPlayback ? "Mettre en pause" : playbackError ? "Réessayer la lecture" : "Lire la note vocale")
                Text(playbackError ? "Lecture indisponible" : loading ? "Chargement…" : "Note vocale")
                    .font(.subheadline.weight(.semibold))
                Spacer(minLength: 4)
                Menu {
                    ForEach([Float(1), Float(1.5), Float(2)], id: \.self) { value in
                        Button("\(value == floor(value) ? String(format: "%.0f", value) : String(format: "%.1f", value))×") {
                            rate = value
                            if wantsPlayback { player?.rate = value }
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
                    scrubbing = editing
                    if !editing { seek() }
                }).disabled(player?.currentItem?.status != .readyToPlay || playbackError)
                    .accessibilityLabel("Position dans la note vocale")
                Text("\(timeLabel(currentTime)) / \(timeLabel(duration > 1 ? duration : 0))")
                    .font(.caption2.monospacedDigit())
                    .opacity(0.72)
            }
        }
        .onDisappear(perform: pause)
        .onChange(of: scenePhase) { _, phase in if phase != .active { pause() } }
        .onChange(of: path) { _, _ in pause(); player = nil; currentTime = 0; duration = 1; playbackError = false }
        .onReceive(NotificationCenter.default.publisher(for: wapiVoicePlaybackRequested)) { notification in
            if notification.object as? UUID != playbackID { pause() }
        }
        .onReceive(NotificationCenter.default.publisher(for: AVAudioSession.interruptionNotification)) { _ in pause() }
        .onReceive(NotificationCenter.default.publisher(for: AVAudioSession.routeChangeNotification)) { notification in
            if notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue { pause() }
        }
        .onReceive(NotificationCenter.default.publisher(for: .AVPlayerItemDidPlayToEndTime)) { notification in
            guard let item = notification.object as? AVPlayerItem, item === player?.currentItem else { return }
            pause(); currentTime = duration
        }
        .onReceive(Timer.publish(every: 0.25, on: .main, in: .common).autoconnect()) { _ in
            guard let player else { return }
            if player.currentItem?.status == .failed { pause(); playbackError = true; return }
            loading = wantsPlayback && player.timeControlStatus == .waitingToPlayAtSpecifiedRate
            playing = player.timeControlStatus == .playing
            guard !scrubbing && !seeking else { return }
            let seconds = player.currentTime().seconds
            if seconds.isFinite { currentTime = min(max(seconds, 0), max(duration, 1)) }
        }
    }

    private func prepare() {
        guard player == nil else { return }
        guard let mediaURL else { playbackError = true; wantsPlayback = false; return }
        let item = AVPlayerItem(url: mediaURL)
        let next = AVPlayer(playerItem: item)
        next.actionAtItemEnd = .pause
        item.audioTimePitchAlgorithm = .timeDomain
        player = next
        Task {
            let loadedDuration = try? await item.asset.load(.duration)
            guard let seconds = loadedDuration?.seconds, seconds.isFinite, seconds > 0 else { return }
            await MainActor.run { if player === next { duration = seconds } }
        }
    }

    private func pause() {
        player?.pause(); wantsPlayback = false; playing = false; loading = false
    }

    private func togglePlayback() {
        if wantsPlayback { pause(); return }
        if playbackError { player = nil; playbackError = false }
        prepare()
        guard let player else { return }
        NotificationCenter.default.post(name: wapiVoicePlaybackRequested, object: playbackID)
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch { playbackError = true; return }
        wantsPlayback = true
        if currentTime >= duration - 0.05 { seek(to: 0) }
        else { player.playImmediately(atRate: rate) }
    }

    private func seek(to seconds: Double? = nil) {
        let value = min(max(seconds ?? currentTime, 0), duration)
        guard let target = player else { return }
        seeking = true
        target.seek(to: CMTime(seconds: value, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero) { finished in
            DispatchQueue.main.async {
                guard player === target else { return }
                seeking = false
                if finished && wantsPlayback { target.playImmediately(atRate: rate) }
            }
        }
        currentTime = value
    }

    private func timeLabel(_ seconds: Double) -> String {
        guard seconds.isFinite else { return "0:00" }
        return String(format: "%d:%02d", Int(seconds) / 60, Int(seconds) % 60)
    }
}

private struct WapiConversationViewportKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

struct ConversationView: View {
    @EnvironmentObject private var store: WhappyStore
    let conversationID: UUID
    @State private var draft = ""
    @FocusState private var composerFocused: Bool
    @State private var attachmentsExpanded = false
    @State private var followsLatest = true
    @State private var bottomVisible = true
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
                                    let senderPhotoURL = conversation?.groupMembers.first(where: { $0.uid == message.senderID })?.photoURL
                                        ?? conversation?.photoURL
                                    Group {
                                        if let photoURL = senderPhotoURL, let url = URL(string: photoURL), !photoURL.isEmpty {
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
                                        .buttonStyle(WapiPressableButtonStyle())
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
                                        }.buttonStyle(WapiPressableButtonStyle())
                                    } else if message.kind == "image", let path = message.mediaPath {
                                        imageMessage(path)
                                    } else if message.kind == "audio", let path = message.mediaPath {
                                        WapiAudioMessagePlayer(path: path)
                                    } else if message.kind == "video", let path = message.mediaPath, let url = mediaURL(path) {
                                        VideoPlayer(player: AVPlayer(url: url)).frame(width: 220, height: 150).clipShape(RoundedRectangle(cornerRadius: 12))
                                    } else if message.kind == "document", let path = message.mediaPath, let url = mediaURL(path) {
                                        Link(destination: url) { Label(message.mediaName ?? "Document Waphsare", systemImage: "doc.richtext.fill") }.buttonStyle(WapiPressableButtonStyle())
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
                                        Button { if editingMessage != nil { draft = UserDefaults.standard.string(forKey: draftKey) ?? ""; editingMessage = nil }; replyTo = message } label: { Label("Répondre", systemImage: "arrowshape.turn.up.left") }
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
                        Color.clear.frame(height: 1).id("conversation-bottom")
                            .onAppear { bottomVisible = true }
                            .onDisappear { bottomVisible = false }
                    }.padding(.horizontal, 12).padding(.vertical, 8)
                }
                .scrollDismissesKeyboard(.interactively)
                .simultaneousGesture(DragGesture().onChanged { _ in followsLatest = false }.onEnded { _ in followsLatest = bottomVisible })
                .background(GeometryReader { geometry in Color.clear.preference(key: WapiConversationViewportKey.self, value: geometry.size.height) })
                .onPreferenceChange(WapiConversationViewportKey.self) { _ in
                    if followsLatest { proxy.scrollTo("conversation-bottom", anchor: .bottom) }
                }
                .onAppear { proxy.scrollTo("conversation-bottom", anchor: .bottom) }
                .onChange(of: composerFocused) { _, focused in
                    if focused && bottomVisible { followsLatest = true; proxy.scrollTo("conversation-bottom", anchor: .bottom) }
                }
                .onChange(of: conversation?.messages.last?.id) { _, _ in
                    if followsLatest { withAnimation(.easeOut(duration: 0.18)) { proxy.scrollTo("conversation-bottom", anchor: .bottom) } }
                }
            }
            if let replyTo { HStack { Image(systemName: "arrowshape.turn.up.left.fill").foregroundStyle(Color.whappyBlue); VStack(alignment: .leading) { Text("Répondre").font(.caption.bold()).foregroundStyle(Color.whappyBlue); Text(replyTo.text).font(.caption).lineLimit(1) }; Spacer(); Button { self.replyTo = nil } label: { Image(systemName: "xmark.circle.fill") } }.padding(.horizontal).padding(.vertical, 8).background(Color.whappyBlue.opacity(0.08)) }
            if let editingMessage { HStack { Image(systemName: "pencil.circle.fill").foregroundStyle(.orange); VStack(alignment: .leading) { Text("Modifier le message").font(.caption.bold()).foregroundStyle(.orange); Text(editingMessage.text).font(.caption).lineLimit(1) }; Spacer(); Button { draft = UserDefaults.standard.string(forKey: draftKey) ?? ""; self.editingMessage = nil } label: { Image(systemName: "xmark.circle.fill") } }.padding(.horizontal).padding(.vertical, 8).background(Color.orange.opacity(0.08)) }
            if let voiceDraftURL {
                HStack(spacing: 12) {
                    Image(systemName: "waveform.circle.fill").font(.title2).foregroundStyle(Color.whappyBlue)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Note vocale prête").font(.subheadline.bold())
                        Text("Écoutez-la avant de l’envoyer").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button { playAudio(voiceDraftURL.path) } label: { Image(systemName: player?.isPlaying == true ? "pause.circle.fill" : "play.circle.fill").font(.title2) }.buttonStyle(WapiPressableButtonStyle())
                    Button(role: .destructive) { try? FileManager.default.removeItem(at: voiceDraftURL); self.voiceDraftURL = nil } label: { Image(systemName: "trash.circle.fill").font(.title2) }.buttonStyle(WapiPressableButtonStyle())
                    Button { sendVoiceDraft() } label: { Image(systemName: "arrow.up.circle.fill").font(.system(size: 34)) }.buttonStyle(WapiPressableButtonStyle())
                }.padding(.horizontal).padding(.vertical, 9).background(Color.whappyBlue.opacity(0.08))
            }
            HStack(spacing: 10) {
                Button { withAnimation(.easeOut(duration: 0.18)) { attachmentsExpanded.toggle() }; if attachmentsExpanded { composerFocused = false } } label: {
                    Image(systemName: attachmentsExpanded ? "xmark.circle" : "plus.circle").font(.title2).frame(width: 36, height: 44)
                }.accessibilityLabel(attachmentsExpanded ? "Fermer les pièces jointes" : "Ajouter une pièce jointe").disabled(recording)
                if recording {
                    Button { toggleRecordingPause() } label: { Image(systemName: recordingPaused ? "play.circle.fill" : "pause.circle.fill").font(.title2).foregroundStyle(Color.whappyBlue) }
                }
                TextField("Votre message", text: $draft, axis: .vertical)
                    .lineLimit(1...4)
                    .focused($composerFocused)
                    .textFieldStyle(.roundedBorder)
                    .onChange(of: draft) { oldValue, newValue in
                        if newValue.count > oldValue.count { WapiSounds.typing() }
                    }
                if recording || (draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && editingMessage == nil) {
                    Button { attachmentsExpanded = false; toggleRecording() } label: { Image(systemName: recording ? "stop.circle.fill" : "mic.circle.fill").font(.system(size: 34)).foregroundStyle(recording ? .red : Color.whappyBlue).frame(width: 44, height: 44) }.disabled(voiceDraftURL != nil).accessibilityLabel(recording ? "Arrêter la note vocale" : "Enregistrer une note vocale")
                } else {
                    Button { submitDraft() } label: { Image(systemName: editingMessage == nil ? "arrow.up.circle.fill" : "checkmark.circle.fill").font(.system(size: 34)).frame(width: 44, height: 44) }.disabled(recording || draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty).accessibilityLabel("Envoyer le message")
                }
            }.padding(.horizontal).padding(.vertical, 10).background(.bar)
            if attachmentsExpanded {
                HStack(spacing: 24) {
                    PhotosPicker(selection: $photoItem, matching: .any(of: [.images, .videos])) { Label("Galerie", systemImage: "photo.on.rectangle") }
                    Button { attachmentsExpanded = false; importingDocument = true } label: { Label("Fichier", systemImage: "doc") }
                    Button { attachmentsExpanded = false; showingEmojiPicker = true } label: { Label("Emoji", systemImage: "face.smiling") }
                }.font(.caption.weight(.semibold)).labelStyle(.titleAndIcon).frame(maxWidth: .infinity).padding(.vertical, 16).background(.bar)
            }
            HStack {
                Button { nextMediaIsViewOnce.toggle() } label: {
                    Label(nextMediaIsViewOnce ? "1 vue activée" : "Média", systemImage: nextMediaIsViewOnce ? "eye.fill" : "plus.circle")
                        .font(.caption.weight(.semibold))
                }.buttonStyle(WapiPressableButtonStyle()).foregroundStyle(nextMediaIsViewOnce ? Color.whappyBlue : .secondary)
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
                        directCallRoute = store.directCallRoute(
                            peerID: conversation.peerUID,
                            peerName: conversation.name,
                            peerPhotoURL: conversation.photoURL ?? "",
                            video: false,
                            targetBusinessPageID: conversation.businessPageID
                        )
                    } label: { Image(systemName: "phone.fill") }
                    Button {
                        WapiSounds.callStarted()
                        directCallRoute = store.directCallRoute(
                            peerID: conversation.peerUID,
                            peerName: conversation.name,
                            peerPhotoURL: conversation.photoURL ?? "",
                            video: true,
                            targetBusinessPageID: conversation.businessPageID
                        )
                    } label: { Image(systemName: "video.fill") }
                    Button { profileConversation = conversation } label: { Image(systemName: "person.crop.circle") }
                }
            }
        }
        .fullScreenCover(item: $directCallRoute) { route in WapiDirectCallRoom(route: route) { directCallRoute = nil } }
        .fullScreenCover(item: $groupCallRoute) { route in WapiGroupCallRoom(route: route) { groupCallRoute = nil } }
        .sheet(item: $profileConversation) { WapiContactProfileView(conversation: $0) }
        .fullScreenCover(item: $groupSettingsConversation) { WapiGroupSettingsView(conversation: $0) }
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
        let wasEditing = editingMessage != nil
        if let editingMessage { store.editMessage(editingMessage.id, in: conversationID, text: value); self.editingMessage = nil }
        else { store.send(value, to: conversationID, replyTo: replyTo) }
        WapiSounds.sent()
        draft = wasEditing ? UserDefaults.standard.string(forKey: draftKey) ?? "" : ""
        replyTo = nil
        if !wasEditing { UserDefaults.standard.removeObject(forKey: draftKey) }
    }

    private func messageDayLabel(_ date: Date) -> String {
        if Calendar.current.isDateInToday(date) { return "Aujourd’hui" }
        if Calendar.current.isDateInYesterday(date) { return "Hier" }
        return date.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(Locale(identifier: "fr_FR"))).capitalized
    }

    @ViewBuilder
    private func imageMessage(_ path: String) -> some View {
        if let image = UIImage(contentsOfFile: path) {
            Button {
                zoomedPhoto = ZoomPhoto(image: image)
            } label: {
                Image(uiImage: image)
                    .resizable().scaledToFill().frame(width: 190, height: 150)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .contentShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(WapiPressableButtonStyle())
            .accessibilityLabel("Ouvrir la photo")
        } else if let url = mediaURL(path) {
            WapiCachedRemoteImage(url: url) {
                ZStack {
                    WapiColor.blueMist
                    ProgressView().tint(Color.whappyBlue)
                }
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
                    NotificationCenter.default.post(name: wapiVoicePlaybackRequested, object: UUID())
                    player?.stop(); remotePlayer?.pause()
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
        let duration = (try? AVAudioPlayer(contentsOf: voiceDraftURL).duration) ?? 0
        store.sendMedia(
            kind: "audio",
            path: voiceDraftURL.path,
            to: conversationID,
            mediaName: voiceDraftURL.lastPathComponent,
            durationSeconds: min(max(Int(duration.rounded(.up)), 0), 600),
            viewOnce: nextMediaIsViewOnce
        )
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
                    WapiCachedRemoteImage(url: url, contentMode: .fit) {
                        ProgressView().tint(Color.whappyBlue)
                    }
                    .padding()
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
                    HStack(spacing: 4) {
                        Text(conversation.name)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.whappyInk)
                            .lineLimit(1)
                        if conversation.displaysVerifiedBadge {
                            Image(systemName: "checkmark.seal.fill")
                                .font(.caption2)
                                .foregroundStyle(Color.wapiVerified)
                        }
                    }
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
        .buttonStyle(WapiPressableButtonStyle())
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
    var onMessage: (() -> Void)? = nil
    @EnvironmentObject private var store: WhappyStore
    @Environment(\.dismiss) private var dismiss
    @State private var directCallRoute: WapiDirectCallRoute?
    @State private var showPhoto = false

    private var profileLabel: String {
        conversation.profileType == "business"
            ? (conversation.businessPageName?.isEmpty == false ? conversation.businessPageName! : "Compte Business")
            : "Compte personnel WAPI"
    }

    private var presenceLabel: String {
        if conversation.peerIsOnline == true { return "En ligne maintenant" }
        if let date = conversation.peerLastSeenAt {
            return "Vu \(date.formatted(date: .abbreviated, time: .shortened))"
        }
        return "Présence privée"
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    VStack(spacing: 13) {
                        Button { if conversation.photoURL?.isEmpty == false { showPhoto = true } } label: {
                            ZStack(alignment: .bottomTrailing) {
                                ConversationAvatar(conversation: conversation)
                                    .frame(width: 112, height: 112)
                                    .clipShape(RoundedRectangle(cornerRadius: 34, style: .continuous))
                                    .overlay(RoundedRectangle(cornerRadius: 34, style: .continuous).stroke(.white.opacity(0.92), lineWidth: 4))
                                    .shadow(color: WapiColor.deepBlue.opacity(0.23), radius: 16, y: 8)
                                if conversation.photoURL?.isEmpty == false {
                                    Image(systemName: "arrow.up.left.and.arrow.down.right")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundStyle(.white)
                                        .frame(width: 31, height: 31)
                                        .background(WapiColor.deepBlue)
                                        .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
                                        .overlay(RoundedRectangle(cornerRadius: 11, style: .continuous).stroke(.white, lineWidth: 3))
                                }
                            }
                        }
                        .buttonStyle(WapiPressableButtonStyle())

                        VStack(spacing: 5) {
                            HStack(spacing: 6) {
                                Text(conversation.name)
                                    .font(.system(size: 28, weight: .semibold))
                                    .foregroundStyle(Color.whappyInk)
                                    .multilineTextAlignment(.center)
                                    .lineLimit(2)
                                    .minimumScaleFactor(0.84)
                                if conversation.displaysVerifiedBadge {
                                    Image(systemName: "checkmark.seal.fill")
                                        .font(.title3)
                                        .foregroundStyle(Color.wapiVerified)
                                        .accessibilityLabel("Compte certifié")
                                }
                            }
                            .fixedSize(horizontal: false, vertical: true)
                            Text(profileLabel.uppercased())
                                .font(.system(size: 10, weight: .bold)).tracking(0.7)
                                .foregroundStyle(conversation.profileType == "business" ? WapiColor.violet : WapiColor.deepBlue)
                            Label(presenceLabel, systemImage: conversation.peerIsOnline == true ? "circle.fill" : "clock.fill")
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(conversation.peerIsOnline == true ? Color.green : WapiColor.secondaryText)
                        }

                        if conversation.photoURL?.isEmpty == false {
                            Text("Touchez la photo pour l’ouvrir en plein écran")
                                .font(.caption2).foregroundStyle(WapiColor.secondaryText)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 26)
                    .background(
                        LinearGradient(
                            colors: [Color.white, WapiColor.blueMist.opacity(0.78), WapiColor.violet.opacity(0.10)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 30, style: .continuous).stroke(WapiColor.sky.opacity(0.24), lineWidth: 1))

                    HStack(spacing: 10) {
                        profileAction("Message", icon: "message.fill", emphasized: false) {
                            dismiss()
                            DispatchQueue.main.async { onMessage?() }
                        }
                        profileAction("Audio", icon: "phone.fill", emphasized: false) { beginCall(video: false) }
                        profileAction("Vidéo", icon: "video.fill", emphasized: true) { beginCall(video: true) }
                    }

                    VStack(alignment: .leading, spacing: 0) {
                        Text("INFORMATIONS")
                            .font(.system(size: 10, weight: .bold)).tracking(0.8)
                            .foregroundStyle(WapiColor.secondaryText)
                            .padding(.bottom, 12)
                        if !conversation.phoneNumber.isEmpty {
                            profileInfo("Numéro", value: conversation.phoneNumber, icon: "phone.badge.checkmark")
                            Divider().padding(.leading, 44)
                        }
                        profileInfo("Identifiant WAPI", value: conversation.peerUID ?? "Protégé", icon: "person.text.rectangle")
                        Divider().padding(.leading, 44)
                        profileInfo("Confidentialité", value: "Selon les choix de ce compte", icon: "lock.shield.fill")
                    }
                    .padding(17)
                    .wapiPanel()

                    HStack(spacing: 13) {
                        Image(systemName: "sparkles.rectangle.stack.fill")
                            .font(.title3).foregroundStyle(WapiColor.deepBlue)
                            .frame(width: 46, height: 46)
                            .background(WapiColor.blueMist)
                            .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Activité publique").font(.headline).foregroundStyle(Color.whappyInk)
                            Text("Stories, chaînes, radios et lives partagés apparaîtront ici selon la confidentialité du compte.")
                                .font(.caption).foregroundStyle(WapiColor.secondaryText)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(17)
                    .wapiFlowSurface()
                }
                .padding(WapiSpacing.screen)
                .padding(.bottom, 24)
            }
            .background(Color.whappyBackground.ignoresSafeArea())
            .navigationTitle("Profil WAPI")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } } }
        }
        .fullScreenCover(isPresented: $showPhoto) {
            WapiRemotePhotoViewer(
                url: conversation.photoURL.flatMap(URL.init(string:)),
                name: conversation.name,
                initials: conversation.initials
            ) { showPhoto = false }
        }
        .fullScreenCover(item: $directCallRoute) { route in
            WapiDirectCallRoom(route: route) { directCallRoute = nil }
        }
    }

    private func beginCall(video: Bool) {
        guard let peerID = conversation.peerUID, !peerID.isEmpty else { return }
        WapiSounds.callStarted()
        directCallRoute = store.directCallRoute(
            peerID: peerID,
            peerName: conversation.name,
            peerPhotoURL: conversation.photoURL ?? "",
            video: video,
            targetBusinessPageID: conversation.businessPageID
        )
    }

    private func profileAction(_ title: String, icon: String, emphasized: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 19, weight: .semibold))
                Text(title).font(.caption.weight(.semibold))
            }
            .foregroundStyle(emphasized ? Color.white : WapiColor.deepBlue)
            .frame(maxWidth: .infinity)
            .frame(height: 76)
            .background {
                if emphasized {
                    LinearGradient(colors: [WapiColor.sky, WapiColor.blue, WapiColor.violet], startPoint: .topLeading, endPoint: .bottomTrailing)
                } else {
                    WapiColor.blueMist
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 21, style: .continuous))
        }
        .buttonStyle(WapiPressableButtonStyle())
        .disabled(title != "Message" && (conversation.peerUID?.isEmpty != false))
        .opacity(title != "Message" && (conversation.peerUID?.isEmpty != false) ? 0.48 : 1)
    }

    private func profileInfo(_ title: String, value: String, icon: String) -> some View {
        HStack(spacing: 13) {
            Image(systemName: icon).font(.system(size: 16, weight: .semibold)).foregroundStyle(WapiColor.deepBlue).frame(width: 30)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.caption).foregroundStyle(WapiColor.secondaryText)
                Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(Color.whappyInk).lineLimit(2)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 9)
    }
}

private struct WapiRemotePhotoViewer: View {
    let url: URL?
    let name: String
    let initials: String
    let onDismiss: () -> Void
    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            WapiCachedRemoteImage(url: url, contentMode: .fit) {
                InitialsAvatar(text: initials, size: 170)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .scaleEffect(scale)
            .offset(offset)
            .gesture(
                MagnificationGesture()
                    .onChanged { value in scale = min(max(lastScale * value, 1), 5) }
                    .onEnded { _ in
                        lastScale = scale
                        if scale <= 1.01 { offset = .zero; lastOffset = .zero }
                    }
            )
            .simultaneousGesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        guard scale > 1 else { return }
                        offset = CGSize(width: lastOffset.width + value.translation.width, height: lastOffset.height + value.translation.height)
                    }
                    .onEnded { _ in lastOffset = offset }
            )
            .simultaneousGesture(
                TapGesture(count: 2).onEnded {
                    withAnimation(.spring(response: 0.26, dampingFraction: 0.88)) {
                        if scale > 1.05 {
                            scale = 1; lastScale = 1; offset = .zero; lastOffset = .zero
                        } else {
                            scale = 2.4; lastScale = 2.4
                        }
                    }
                }
            )

            VStack {
                HStack {
                    Button { onDismiss() } label: {
                        Image(systemName: "xmark").font(.system(size: 17, weight: .bold)).foregroundStyle(.white)
                            .frame(width: 44, height: 44).background(.black.opacity(0.46)).clipShape(Circle())
                    }
                    Spacer()
                    Text(name).font(.headline).foregroundStyle(.white).lineLimit(1)
                    Spacer()
                    Color.clear.frame(width: 44, height: 44)
                }
                .padding(.horizontal, 16).padding(.top, 8)
                Spacer()
                Text("Pincez ou touchez deux fois pour zoomer")
                    .font(.caption).foregroundStyle(.white.opacity(0.76))
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(.black.opacity(0.42)).clipShape(Capsule())
                    .padding(.bottom, 22)
            }
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
                        WapiEditorTextFieldIOS(title: "Nom du groupe", text: $name)
                        Text("Chaque modification est enregistrée dans la conversation pour informer les membres.").font(.footnote).foregroundStyle(.secondary)
                    }.disabled(store.firebaseBusy)
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
            .scrollDismissesKeyboard(.interactively)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() }.disabled(store.firebaseBusy) }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if canEdit {
                    WapiEditorActionIOS(title: "Enregistrer", busy: store.firebaseBusy,
                        enabled: hasChanges && (2...80).contains(name.trimmingCharacters(in: .whitespacesAndNewlines).count)) { save() }
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
    // 768 px is visually sharp for an avatar and keeps server-authoritative
    // uploads reliable during Wi-Fi/cellular handovers.
    let canvasSide: CGFloat = 768
    guard image.size.width > 0, image.size.height > 0 else { return nil }
    let scale = max(canvasSide / image.size.width, canvasSide / image.size.height)
    let drawSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
    let origin = CGPoint(x: (canvasSide - drawSize.width) / 2, y: (canvasSide - drawSize.height) / 2)
    let format = UIGraphicsImageRendererFormat()
    format.scale = 1
    format.opaque = true
    return UIGraphicsImageRenderer(size: CGSize(width: canvasSide, height: canvasSide), format: format)
        .jpegData(withCompressionQuality: 0.82) { _ in
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
    @State private var selectedProfile: Conversation?
    @State private var unavailableMessage: String?

    private var accountCalls: [CallRecord] {
        store.calls.filter { call in
            if store.activeBusinessMode {
                return call.profileType == "business" && (store.activeBusinessRemoteID.isEmpty || call.businessPageID == store.activeBusinessRemoteID)
            }
            return call.profileType != "business"
        }
    }

    private var accountConversations: [Conversation] { store.accountConversations }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 13) {
                    Image(systemName: "phone.fill")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 48, height: 48)
                        .background(LinearGradient(colors: [WapiColor.sky, WapiColor.blue, WapiColor.violet], startPoint: .topLeading, endPoint: .bottomTrailing))
                        .clipShape(RoundedRectangle(cornerRadius: 17, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(store.activeBusinessMode ? "Appels Business" : "Appels")
                            .font(.system(size: 27, weight: .semibold))
                            .foregroundStyle(Color.whappyInk)
                        Text(store.activeBusinessMode ? (store.business?.name ?? "Identité professionnelle") : "Audio et vidéo, simplement").font(.caption).foregroundStyle(WapiColor.secondaryText)
                    }
                    Spacer()
                    Text(store.activeBusinessMode ? "ESPACE SÉPARÉ" : "WEBRTC")
                        .font(.system(size: 8, weight: .bold)).tracking(0.5)
                        .foregroundStyle(WapiColor.deepBlue)
                        .padding(.horizontal, 9).padding(.vertical, 6)
                        .background(.white.opacity(0.84)).clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
                }
                .padding(18)
                .wapiFlowSurface(radius: 26)

                HStack(spacing: 10) {
                    Capsule().fill(LinearGradient(colors: [WapiColor.sky, WapiColor.blue, WapiColor.violet], startPoint: .top, endPoint: .bottom)).frame(width: 4, height: 34)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Récents").font(.headline.weight(.semibold)).foregroundStyle(Color.whappyInk)
                        Text(accountCalls.isEmpty ? "Aucun appel pour le moment" : "Reprendre une conversation en un geste").font(.caption2).foregroundStyle(WapiColor.secondaryText)
                    }
                }

                if accountCalls.isEmpty {
                    ContentUnavailableView("Aucun appel récent", systemImage: "phone", description: Text("Vos appels audio et vidéo apparaîtront ici."))
                        .frame(maxWidth: .infinity).padding(.vertical, 50).wapiPanel()
                } else {
                    ForEach(accountCalls) { call in
                        let profile = conversation(for: call)
                        HStack(spacing: 12) {
                            Button { if let profile { selectedProfile = profile } } label: {
                                ZStack(alignment: .bottomTrailing) {
                                    Group {
                                        if let profile { ConversationAvatar(conversation: profile) }
                                        else { InitialsAvatar(text: String(call.name.prefix(2)).uppercased(), size: 50) }
                                    }
                                    .frame(width: 50, height: 50)
                                    .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                                    Image(systemName: call.missed ? "phone.down.fill" : call.mode.systemImage)
                                        .font(.system(size: 8, weight: .bold)).foregroundStyle(.white)
                                        .frame(width: 17, height: 17)
                                        .background(call.missed ? Color.red : Color.green)
                                        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                                }
                            }
                            .buttonStyle(WapiPressableButtonStyle())
                            .disabled(profile == nil)
                            VStack(alignment: .leading, spacing: 3) {
                                Button { if let profile { selectedProfile = profile } } label: {
                                    HStack(spacing: 4) {
                                        Text(call.name).font(.headline.weight(.semibold)).foregroundStyle(Color.whappyInk).lineLimit(1)
                                        if profile?.displaysVerifiedBadge == true {
                                            Image(systemName: "checkmark.seal.fill").font(.caption).foregroundStyle(Color.wapiVerified)
                                        }
                                    }
                                }
                                .buttonStyle(WapiPressableButtonStyle())
                                .disabled(profile == nil)
                                Text(call.missed ? "Appel manqué" : "Appel WAPI").font(.caption2.weight(.bold)).foregroundStyle(call.missed ? .red : Color.whappyBlue)
                                Text(call.date, style: .relative).font(.caption2).foregroundStyle(WapiColor.secondaryText)
                            }
                            Spacer()
                            Button { start(call, video: false) } label: {
                                Image(systemName: "phone.fill").font(.system(size: 15, weight: .semibold)).foregroundStyle(WapiColor.deepBlue).frame(width: 40, height: 40).background(WapiColor.blueMist).clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                            }.buttonStyle(WapiPressableButtonStyle())
                            Button { start(call, video: true) } label: {
                                Image(systemName: "video.fill").font(.system(size: 15, weight: .semibold)).foregroundStyle(.white).frame(width: 40, height: 40).background(LinearGradient(colors: [WapiColor.blue, WapiColor.violet], startPoint: .topLeading, endPoint: .bottomTrailing)).clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                            }.buttonStyle(WapiPressableButtonStyle())
                        }
                        .padding(14)
                        .wapiPanel()
                    }
                }
            }
            .padding(WapiSpacing.screen)
            .padding(.bottom, 24)
        }
        .background(Color.whappyBackground.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .sheet(item: $selectedProfile) { profile in
            WapiContactProfileView(conversation: profile) {
                store.pendingConversationID = profile.id
                store.selectedTab = .messages
            }
        }
        .fullScreenCover(item: $directCallRoute) { route in WapiDirectCallRoom(route: route) { directCallRoute = nil } }
        .alert("Appel WAPI indisponible", isPresented: Binding(get: { unavailableMessage != nil }, set: { if !$0 { unavailableMessage = nil } })) { Button("Fermer", role: .cancel) {} } message: { Text(unavailableMessage ?? "") }
    }

    private func start(_ call: CallRecord, video: Bool) {
        guard let conversation = conversation(for: call), conversation.peerUID != nil else {
            unavailableMessage = "Ce contact doit avoir un compte WAPI actif pour un appel WAPI."
            return
        }
        WapiSounds.callStarted()
        directCallRoute = store.directCallRoute(
            peerID: conversation.peerUID,
            peerName: conversation.name,
            peerPhotoURL: conversation.photoURL ?? "",
            video: video,
            targetBusinessPageID: conversation.businessPageID
        )
    }

    private func conversation(for call: CallRecord) -> Conversation? {
        accountConversations.first {
            (!$0.phoneNumber.isEmpty && $0.phoneNumber == call.phoneNumber) ||
            ($0.name.caseInsensitiveCompare(call.name) == .orderedSame && $0.peerUID != nil)
        }
    }
}

struct MarketView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var search = ""
    @State private var selling = false
    @State private var showingCart = false
    @State private var marketplace: [Listing] = []
    @State private var loading = false
    @State private var serverMessage: String?
    private var filtered: [Listing] {
        let all = (marketplace + store.listings).reduce(into: [UUID: Listing]()) { $0[$1.id] = $1 }.values
        return all.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
            .filter { ("\($0.title) \($0.place) \($0.seller) " + ($0.description ?? "")).matchesWhappySearch(search) }
    }

    var body: some View {
        ScrollView { LazyVStack(spacing: 12) {
            WapiCommerceLaunchersIOS(); BusinessSaleRoomsIOSRail()
            HStack {
                VStack(alignment: .leading, spacing: 3) { Text("Annonces Business").font(.title3.bold()); Text("Photos, troc, emplois et enchères — visibles par tous les comptes WAPI.").font(.caption).foregroundStyle(.secondary) }
                Spacer()
                if loading { ProgressView() }
            }.frame(maxWidth: .infinity, alignment: .leading).padding(.top, 4)
            if let serverMessage { Label(serverMessage, systemImage: "info.circle").font(.footnote).foregroundStyle(Color.whappyBlue).frame(maxWidth: .infinity, alignment: .leading).padding(12).background(Color.whappyBlue.opacity(0.08), in: RoundedRectangle(cornerRadius: 16)) }
            ForEach(filtered) { listing in NavigationLink(value: listing) { ListingRow(listing: listing) }.buttonStyle(WapiPressableButtonStyle()) }
            if !loading && filtered.isEmpty { ContentUnavailableView("Aucune annonce", systemImage: "storefront", description: Text("Les annonces Business actives s’afficheront ici.")) }
        }.padding() }
            .background(Color.whappyBackground).navigationTitle("Marché").searchable(text: $search, prompt: "Produit, service ou quartier")
            .navigationDestination(for: Listing.self) { ListingDetailView(listing: $0) }
            .toolbar { ToolbarItemGroup(placement: .topBarTrailing) { Button { showingCart = true } label: { Label("Panier", systemImage: "cart.fill").badge(store.cartCount) }; Button { selling = true } label: { Label("Publier", systemImage: "plus") } } }
            .task { await loadMarketplace() }
            .fullScreenCover(isPresented: $selling) { SellListingView() }.sheet(isPresented: $showingCart) { CartView() }
    }

    @MainActor private func loadMarketplace() async {
        loading = true; defer { loading = false }
        do {
            let result = try await WapiCommerceModel.call("marketplaceListings")
            marketplace = (result["listings"] as? [[String: Any]] ?? []).compactMap { data in
                guard let rawID = data["id"] as? String, let id = UUID(uuidString: rawID) else { return nil }
                let mode = data["mode"] as? String ?? "sale"
                return Listing(id: id, title: data["title"] as? String ?? "Annonce", price: data["priceText"] as? String ?? "À discuter", place: data["place"] as? String ?? "", seller: data["pageName"] as? String ?? "Business WAPI", icon: mode == "trade" ? "arrow.triangle.2.circlepath" : mode == "job" ? "briefcase.fill" : mode == "auction" ? "hammer.fill" : "shippingbox.fill", acceptsTrade: (data["acceptsOffers"] as? Bool ?? false) || mode == "trade", photoURL: (data["photoUrls"] as? [String])?.first, description: data["description"] as? String, mode: mode, businessPageID: data["pageId"] as? String, boostStatus: data["boostStatus"] as? String)
            }
            serverMessage = nil
        } catch { serverMessage = WapiCommerceModel.message(error) }
    }
}

private struct ListingRow: View {
    let listing: Listing
    var body: some View {
        HStack(spacing: 16) {
            CommercePhoto(url: listing.photoURL ?? "", size: 82, symbol: listing.icon)
            VStack(alignment: .leading, spacing: 5) { Text(listing.title).font(.headline).foregroundStyle(Color.whappyInk).lineLimit(2); Text(listing.price).font(.subheadline.bold()).foregroundStyle(listing.acceptsTrade ? .orange : Color.whappyBlue); Label("\(listing.place) · \(listing.seller)", systemImage: "mappin.and.ellipse").font(.caption).foregroundStyle(.secondary).lineLimit(1); if listing.mode == "trade" { Text("TROC ACCEPTÉ").font(.caption2.bold()).foregroundStyle(.orange) }; if listing.boostStatus == "active" { Text("SPONSORISÉ").font(.caption2.bold()).foregroundStyle(Color.whappyBlue) } }
            Spacer()
            if listing.saved { Image(systemName: "bookmark.fill").foregroundStyle(Color.whappyBlue) }
        }.padding(12).background(.white).clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

private struct ListingDetailView: View {
    @EnvironmentObject private var store: WhappyStore
    let listing: Listing
    @State private var added = false
    @State private var responding = false
    @State private var responseMessage: String?
    private var action: (title: String, kind: String, message: String) {
        switch listing.mode {
        case "trade": return ("Proposer un troc", "trade", "Je souhaite vous proposer un échange pour cette annonce.")
        case "auction": return ("Enchérir", "bid", "Je souhaite placer une enchère sur cette annonce.")
        case "job": return ("Postuler", "apply", "Je souhaite postuler à cette offre.")
        case "service": return ("Discuter du service", "message", "Je souhaite échanger au sujet de ce service.")
        default: return ("Faire une offre", "offer", "Je souhaite vous faire une offre pour cette annonce.")
        }
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                CommercePhoto(url: listing.photoURL ?? "", size: 300, symbol: listing.icon).frame(maxWidth: .infinity)
                Text(listing.title).font(.title.bold()).foregroundStyle(Color.whappyInk)
                Text(listing.price).font(.title3.bold()).foregroundStyle(listing.acceptsTrade ? .orange : Color.whappyBlue)
                Label("\(listing.place) · publié par \(listing.seller)", systemImage: "mappin.and.ellipse").foregroundStyle(.secondary)
                if let description = listing.description, !description.isEmpty { Text(description).foregroundStyle(Color.whappyInk) }
                if let responseMessage { Label(responseMessage, systemImage: "checkmark.circle").font(.footnote).foregroundStyle(Color.whappyBlue) }
                Button { Task { await respond() } } label: { Label(responding ? "Envoi…" : action.title, systemImage: listing.mode == "auction" ? "hammer.fill" : "bubble.left.and.bubble.right.fill").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent).controlSize(.large).disabled(responding || listing.businessPageID == nil)
                if listing.mode == "sale" || listing.mode == "auction" { Button { store.addToCart(listing); added = true } label: { Label(added ? "Ajouté à ma sélection" : "Conserver dans ma sélection", systemImage: added ? "checkmark.circle.fill" : "cart.badge.plus") }.buttonStyle(.bordered).frame(maxWidth: .infinity) }
                Button { store.toggleSaved(listing) } label: { Label(listing.saved ? "Retirer des favoris" : "Mettre une étoile", systemImage: listing.saved ? "star.slash" : "star") }.buttonStyle(.bordered).frame(maxWidth: .infinity)
                Text("Aucun paiement n’est débité par WAPI pour le moment. Les options Mobile Money s’activeront uniquement après configuration du moyen régional de la page Business.").font(.caption).foregroundStyle(.secondary)
            }.padding()
        }.background(Color.whappyBackground).navigationTitle("Annonce").navigationBarTitleDisplayMode(.inline)
    }

    @MainActor private func respond() async {
        guard listing.businessPageID != nil else { responseMessage = "Cette annonce doit être actualisée avant de recevoir des réponses."; return }
        responding = true; defer { responding = false }
        do {
            var payload: [String: Any] = ["listingId": listing.id.uuidString.lowercased(), "kind": action.kind]
            if ["offer", "trade", "bid", "apply"].contains(action.kind) { payload["offerText"] = action.message } else { payload["note"] = action.message }
            _ = try await WapiCommerceModel.call("respondToMarketplaceListing", payload)
            responseMessage = "Votre demande a été transmise au Business. Vous pourrez poursuivre l’échange dès sa réponse."
        } catch { responseMessage = WapiCommerceModel.message(error) }
    }
}

private struct SellListingView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var store: WhappyStore
    @State private var title = ""; @State private var price = ""; @State private var place = "Brazzaville"; @State private var description = ""; @State private var category = "Autre"; @State private var mode = "sale"
    @State private var photoItem: PhotosPickerItem?; @State private var photoData: Data?; @State private var busy = false; @State private var error: String?
    private var pageID: String { store.activeBusinessRemoteID.isEmpty ? (store.business?.remoteID ?? "") : store.activeBusinessRemoteID }
    var body: some View {
        NavigationStack {
            Group {
                if pageID.isEmpty {
                    ContentUnavailableView("Compte Business requis", systemImage: "briefcase.fill", description: Text("Tout le monde peut consulter les annonces. Pour publier, créez puis activez votre page Business WAPI."))
                } else {
                    Form {
                        Section("Photo") { PhotosPicker(selection: $photoItem, matching: .images) { HStack { Image(systemName: photoData == nil ? "photo.badge.plus" : "checkmark.circle.fill"); Text(photoData == nil ? "Choisir une photo dans la galerie" : "Photo prête à publier") } }; Text("L’image est recadrée et compressée sur l’appareil avant l’envoi.").font(.caption).foregroundStyle(.secondary) }
                        Section("Type d’annonce") { Picker("Action attendue", selection: $mode) { Text("Vendre").tag("sale"); Text("Troc").tag("trade"); Text("Enchère").tag("auction"); Text("Emploi").tag("job"); Text("Service").tag("service") }.pickerStyle(.segmented) }
                        Section("Votre annonce") { WapiEditorTextFieldIOS(title: "Titre", text: $title, limit: 120); WapiEditorTextFieldIOS(title: "Description", text: $description, limit: 1200); WapiEditorTextFieldIOS(title: "Catégorie", text: $category, limit: 60); WapiEditorTextFieldIOS(title: mode == "trade" ? "Échange souhaité" : mode == "job" ? "Contrat ou rémunération" : "Prix / base d’enchère", text: $price, limit: 120); WapiEditorTextFieldIOS(title: "Lieu", text: $place, limit: 120) }
                        if let error { Section { Text(error).foregroundStyle(.red) } }
                    }
                    .task(id: photoItem) { guard let data = try? await photoItem?.loadTransferable(type: Data.self), let image = UIImage(data: data) else { return }; photoData = makeWapiGroupPhotoData(image) }
                }
            }
            .navigationTitle("Nouvelle annonce").navigationBarTitleDisplayMode(.inline)
            .scrollDismissesKeyboard(.interactively)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }; if pageID.isEmpty { ToolbarItem(placement: .confirmationAction) { Button("Créer mon Business") { store.selectedTab = .profile; dismiss() } } } }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if !pageID.isEmpty { WapiEditorActionIOS(title: "Publier l’annonce", busy: busy, enabled: title.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2 && !busy) { Task { await publish() } } }
            }
        }
    }

    @MainActor private func publish() async {
        busy = true; error = nil; defer { busy = false }
        do {
            var payload: [String: Any] = ["listingId": UUID().uuidString.lowercased(), "pageId": pageID, "title": title.trimmingCharacters(in: .whitespacesAndNewlines), "priceText": price.trimmingCharacters(in: .whitespacesAndNewlines), "place": place.trimmingCharacters(in: .whitespacesAndNewlines), "mode": mode, "description": description.trimmingCharacters(in: .whitespacesAndNewlines), "category": category.trimmingCharacters(in: .whitespacesAndNewlines), "acceptsOffers": true]
            if mode == "trade" { payload["tradeWish"] = price.trimmingCharacters(in: .whitespacesAndNewlines) }
            if let photoData { payload["photoBase64s"] = [photoData.base64EncodedString()] }
            _ = try await WapiCommerceModel.call("createMarketplaceListing", payload)
            dismiss()
        } catch { self.error = WapiCommerceModel.message(error) }
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
                    Section { Button("Enregistrer ma sélection") { reference = store.checkout(delivery: delivery) }.disabled(delivery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty).frame(maxWidth: .infinity); Text("Sélection locale : elle n’est pas transmise au vendeur. Contactez l’établissement depuis sa vitrine.").font(.caption).foregroundStyle(.secondary) }
                }
                if let reference { Section { Label("Sélection \(reference) conservée sur cet appareil", systemImage: "checkmark.circle").foregroundStyle(.secondary) } }
            }.navigationTitle("Panier").toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() } } }
        }
    }
}

private struct LegacyLiveView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var showingStudio = false
    var body: some View {
        ScrollView { LazyVStack(spacing: 16) { ForEach(store.liveRooms) { room in NavigationLink(value: room) { LiveCard(room: room) }.buttonStyle(WapiPressableButtonStyle()) } }.padding() }
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
    private let games = [("King QI", "Quiz vocal · duels · trophées · direct", "crown.fill"), ("Ludo WAPI", "Plateau 3D · dés animés · amis", "dice.fill"), ("Wapi Pool", "Table 3D · visée tactile · tournoi", "circle.grid.cross.fill"), ("Échecs WAPI", "Échiquier 3D · IA et duels", "checkerboard.rectangle"), ("Jeu de dames", "Pions 3D · dames couronnées · IA", "circle.hexagongrid.fill"), ("Cartes WAPI", "Tables privées · amis · tournoi", "suit.club.fill"), ("Poker WAPI", "Salon privé · jetons non monétaires", "suit.spade.fill"), ("Défi du jour", "Quiz rapide · 60 secondes", "bolt.fill"), ("Mots & idées", "Trouvez la solution ensemble", "sparkles")]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 9) {
                    Label("WHAPPY PLAY", systemImage: "bolt.fill").font(.caption.bold()).foregroundStyle(Color.whappyBlue)
                    Text("Jouez. Progressez.\nRestez connecté.").font(.system(size: 30, weight: .bold, design: .rounded)).foregroundStyle(.white)
                    Text("Des mini-jeux à lancer seul ou avec votre communauté.").foregroundStyle(.white.opacity(0.8))
                    HStack { StatPill(title: "Série", value: "\(streak) jour\(streak > 1 ? "s" : "")"); StatPill(title: "Score", value: "\(score) XP") }
                }.padding(22).frame(maxWidth: .infinity, alignment: .leading).background(LinearGradient(colors: [.whappyInk, .whappyBlue.opacity(0.75)], startPoint: .topLeading, endPoint: .bottomTrailing)).clipShape(RoundedRectangle(cornerRadius: 26))
                Text("Choisir un jeu").font(.title3.bold()).foregroundStyle(Color.whappyInk)
                ForEach(games, id: \.0) { game in
                    Button { launchedGame = WapiIOSGameLaunch(name: game.0, subtitle: game.1); WapiSounds.gameMove(); WapiSounds.haptic(.medium) } label: {
                        HStack(spacing: 13) { Image(systemName: game.2).font(.title2).foregroundStyle(Color.whappyBlue).frame(width: 48, height: 48).background(Color.whappyBlue.opacity(0.1)).clipShape(RoundedRectangle(cornerRadius: 14)); VStack(alignment: .leading) { Text(game.0).font(.headline).foregroundStyle(Color.whappyInk); Text(game.1).font(.caption).foregroundStyle(.secondary) }; Spacer(); Text("JOUER  ›").font(.caption.bold()).foregroundStyle(Color.whappyBlue) }.padding(14).background(.white).clipShape(RoundedRectangle(cornerRadius: 18))
                    }.buttonStyle(WapiPressableButtonStyle())
                }
            }.padding()
        }.background(Color.whappyBackground).navigationTitle("Jeux")
            .fullScreenCover(item: $launchedGame) { game in
                Group {
                    if game.name == "King QI" { KingQiIOSView() }
                    else if game.name == "Wapi Pool" { WapiIOSPoolArena() }
                    else if ["Ludo WAPI", "Échecs WAPI", "Jeu de dames", "Cartes WAPI", "Poker WAPI"].contains(game.name) { WapiIOSTabletopGame(game: game, score: $score, streak: $streak) }
                    else { WapiIOSArcadeGame(game: game, score: $score, streak: $streak) }
                }
                .onAppear { WapiOrientation.request(.landscape) }
                .onDisappear { WapiOrientation.request(.portrait) }
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
    @State private var poolMode = "training"
    @State private var poolPower = 55.0
    @State private var poolAngle: Float = 0
    @State private var poolSideSpin: Float = 0
    @State private var poolFollowSpin: Float = 0
    @State private var poolShotRevision = 0
    @State private var poolShotLocked = false
    @State private var poolAwaitingAI = false
    @State private var poolSession = UUID()
    @State private var poolAITask: Task<Void, Never>?

    private var primaryAction: String {
        switch game.name {
        case "Ludo WAPI": return "Lancer le dé"
        case "Wapi Pool": return "Frapper"
        case "Échecs WAPI", "Jeu de dames": return "Jouer le coup"
        default: return "Distribuer"
        }
    }

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color.whappyInk, Color.whappyBlue.opacity(0.78), Color.black], startPoint: .topLeading, endPoint: .bottomTrailing).ignoresSafeArea()
            WapiIOS3DTabletop(scene: game.name, dieValue: dieValue, poolAngle: poolAngle, poolPower: Int(poolPower), poolSideSpin: poolSideSpin, poolFollowSpin: poolFollowSpin, poolShotRevision: poolShotRevision, onPoolSettled: poolSettled).id(poolSession).ignoresSafeArea()
            LinearGradient(colors: [Color.black.opacity(0.72), .clear, .clear, Color.black.opacity(0.82)], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
                .allowsHitTesting(false)
            VStack(spacing: 12) {
                HStack {
                    Button { dismiss() } label: { Label("Quitter", systemImage: "chevron.left").font(.subheadline.bold()) }
                        .buttonStyle(.borderedProminent).tint(.black.opacity(0.58))
                    Spacer()
                    VStack(spacing: 1) { Text(game.name.uppercased()).font(.caption.bold()).foregroundStyle(.cyan); Text("MANCHE \(turn)").font(.headline.bold()).foregroundStyle(.white) }
                    Spacer()
                    Text("\(score) XP").font(.headline.bold()).foregroundStyle(.white).padding(.horizontal, 12).padding(.vertical, 8).background(.black.opacity(0.46)).clipShape(Capsule())
                }.padding(.horizontal)
                Spacer()
                Text(status).font(.footnote.weight(.semibold)).foregroundStyle(.white).multilineTextAlignment(.center).padding(.horizontal, 14).padding(.vertical, 9).background(.black.opacity(0.52)).clipShape(Capsule()).padding(.horizontal)
                if game.name == "Wapi Pool" {
                    VStack(spacing: 9) {
                        HStack(spacing: 8) {
                            poolModeButton("Entraînement", value: "training", icon: "scope")
                            poolModeButton("Contre l’IA", value: "ai", icon: "brain.head.profile")
                            Button("Rejouer") { resetPool() }.buttonStyle(.bordered).tint(.white)
                            Spacer()
                            Text("ANGLE \(Int(poolAngle * 180 / .pi))°").font(.caption2.bold()).foregroundStyle(.white.opacity(0.7))
                        }
                        HStack(spacing: 10) {
                            Button { poolAngle -= 0.07 } label: { Image(systemName: "rotate.left.fill") }.buttonStyle(.bordered).tint(.white).disabled(poolShotLocked)
                            WapiIOSPoolPowerRail(power: $poolPower, enabled: !poolShotLocked, onRelease: strikePool)
                                .frame(maxWidth: .infinity)
                            Text("\(Int(poolPower)) %").font(.caption.bold()).foregroundStyle(.white).frame(width: 48)
                            Button { poolAngle += 0.07 } label: { Image(systemName: "rotate.right.fill") }.buttonStyle(.bordered).tint(.white).disabled(poolShotLocked)
                        }
                        HStack(spacing: 12) {
                            WapiIOSPoolSpinPad(sideSpin: $poolSideSpin, followSpin: $poolFollowSpin, enabled: !poolShotLocked)
                                .frame(width: 76, height: 76)
                            VStack(alignment: .leading, spacing: 5) {
                                Text("POINT D’IMPACT").font(.caption2.bold()).tracking(0.8).foregroundStyle(.white.opacity(0.62))
                                Text(poolFollowSpin > 0.18 ? "Suivi" : poolFollowSpin < -0.18 ? "Rétro" : abs(poolSideSpin) > 0.18 ? "Effet latéral" : "Centre")
                                    .font(.subheadline.bold()).foregroundStyle(.white)
                                Text("G/D \(Int(poolSideSpin * 100)) · R/S \(Int(poolFollowSpin * 100))")
                                    .font(.caption2.monospacedDigit()).foregroundStyle(.cyan.opacity(0.82))
                            }
                            Spacer()
                            Button {
                                poolSideSpin = 0; poolFollowSpin = 0
                                WapiSounds.haptic(.light)
                            } label: { Image(systemName: "scope").font(.title3.bold()) }
                                .buttonStyle(.bordered).tint(.white).disabled(poolShotLocked)
                        }
                    }
                    .padding(.horizontal)
                } else {
                HStack(spacing: 12) {
                    Button { status = "Mode entraînement avec IA sélectionné."; WapiSounds.gameMove(); WapiSounds.haptic(.medium) } label: { Label("IA", systemImage: "brain.head.profile") }.buttonStyle(.bordered).tint(.white)
                    Button {
                        turn += 1; dieValue = Int.random(in: 1...6); score += 10; streak += 1
                        status = game.name == "Ludo WAPI" ? "Dé : \(dieValue). Choisissez un pion à déplacer." : game.name == "Wapi Pool" ? "Coup joué : ajustez la visée avec un glissement sur la table." : "Coup validé. À l’adversaire."
                        UINotificationFeedbackGenerator().notificationOccurred(.success)
                        if game.name == "Ludo WAPI" { WapiSounds.gameDice() }
                        else if game.name == "Wapi Pool" { WapiSounds.gamePool() }
                        else if game.name == "Cartes WAPI" || game.name == "Poker WAPI" { WapiSounds.gameCard() }
                        else { WapiSounds.gameMove() }
                    } label: { Label(primaryAction, systemImage: game.name == "Ludo WAPI" ? "dice.fill" : "play.fill").frame(minWidth: 150) }.buttonStyle(.borderedProminent).tint(Color.whappyBlue)
                    Button { status = "Salon en ligne prêt : invitez vos contacts WAPI avec le bouton Partager." } label: { Image(systemName: "person.2.fill") }.buttonStyle(.bordered).tint(.white)
                }
                }
            }.padding(.top, 10)
        }
        .onDisappear { poolAITask?.cancel() }
    }

    private func resetPool() {
        poolAITask?.cancel(); poolSession = UUID()
        poolShotLocked = false; poolAwaitingAI = false; poolShotRevision = 0
        poolAngle = 0; poolPower = 55; poolSideSpin = 0; poolFollowSpin = 0
        status = "Nouvelle table · à vous de jouer."
    }

    private func poolModeButton(_ title: String, value: String, icon: String) -> some View {
        Button {
            poolMode = value
            resetPool()
            status = value == "training" ? "Entraînement libre · coups illimités." : "IA activée · elle joue après chacun de vos tirs."
        } label: { Label(title, systemImage: icon).font(.caption.bold()) }
            .buttonStyle(.borderedProminent).tint(poolMode == value ? Color.whappyBlue : Color.black.opacity(0.48))
            .disabled(poolShotLocked)
    }

    private func strikePool() {
        guard !poolShotLocked else { return }
        poolShotLocked = true
        poolShotRevision += 1; turn += 1
        poolAwaitingAI = poolMode == "ai"
        status = "Tir en mouvement · collisions, bandes et effets sont calculés en temps réel."
        WapiSounds.haptic(.medium)
    }

    private func poolSettled(_ angle: Float, _ power: Int) {
        guard poolShotLocked else { return }
        if power == 0 { poolAwaitingAI = false; status = "Table terminée · touchez Rejouer pour une nouvelle partie."; return }
        if poolAwaitingAI {
            poolAwaitingAI = false
            status = "L’IA prépare son tir…"
            let session = poolSession
            poolAITask = Task { @MainActor in
                do { try await Task.sleep(for: .milliseconds(550)) } catch { return }
                guard !Task.isCancelled, session == poolSession else { return }
                poolAngle = angle; poolPower = Double(power)
                poolSideSpin = 0; poolFollowSpin = 0; poolShotRevision += 1
                status = "L’IA joue · attendez l’arrêt des boules."
            }
        } else {
            poolShotLocked = false
            status = "À vous · visez, tirez la queue et relâchez."
        }
    }
}

/** A physical pull-and-release cue rail. There is no arcade strike button:
 the player pulls the cue, reads the force colour, then releases naturally. */
struct WapiIOSPoolPowerRail: View {
    @Binding var power: Double
    let enabled: Bool
    let onRelease: () -> Void
    @GestureState private var pulling = false
    @State private var originalPower: Double?
    @State private var cancelled = false
    @State private var committed = false

    private var forceColor: Color {
        power >= 82 ? Color(red: 1, green: 0.34, blue: 0.42) : power >= 58 ? Color.orange : Color(red: 0.12, green: 0.62, blue: 1)
    }

    var body: some View {
        GeometryReader { proxy in
            let width = max(proxy.size.width, 1)
            let travel = width * 0.86
            let tipX = max(12, min(travel, travel * power / 100))
            ZStack(alignment: .leading) {
                Capsule().fill(Color(red: 0.015, green: 0.055, blue: 0.14).opacity(0.94))
                Capsule().stroke(forceColor.opacity(pulling ? 0.90 : 0.42), lineWidth: pulling ? 2 : 1)
                Capsule().fill(Color.black.opacity(0.52)).frame(height: 13).padding(.horizontal, 10)
                Capsule().fill(LinearGradient(colors: [forceColor.opacity(0.35), forceColor], startPoint: .leading, endPoint: .trailing))
                    .frame(width: max(12, tipX), height: 6).padding(.leading, 10)
                Capsule().fill(LinearGradient(colors: [Color(red: 0.92, green: 0.77, blue: 0.52), Color(red: 0.46, green: 0.15, blue: 0.055)], startPoint: .leading, endPoint: .trailing))
                    .frame(width: max(30, width * 0.44), height: 8)
                    .offset(x: min(width * 0.48, tipX), y: 0)
                    .shadow(color: forceColor.opacity(pulling ? 0.60 : 0.18), radius: pulling ? 8 : 3)
                Circle().fill(forceColor).frame(width: 15, height: 15).offset(x: tipX + 4)
                    .overlay(Circle().stroke(.white.opacity(0.85), lineWidth: 2).frame(width: 15, height: 15).offset(x: tipX + 4))
            }
            .contentShape(Capsule())
            .gesture(
                DragGesture(minimumDistance: 6)
                    .updating($pulling) { _, state, _ in state = enabled }
                    .onChanged { value in
                        guard enabled else { return }
                        if originalPower == nil { originalPower = power; cancelled = false; committed = false }
                        if value.location.y < -23 || value.location.y > proxy.size.height + 23 { cancelled = true }
                        power = min(100, max(10, Double(value.translation.width / max(width * 0.65, 1) * 100)))
                    }
                    .onEnded { value in
                        let fraction = value.translation.width / max(width * 0.65, 1)
                        guard enabled, !cancelled, fraction >= 0.04 else { restorePower(); return }
                        committed = true
                        originalPower = nil
                        onRelease()
                    }
            )
            .onChange(of: pulling) { _, active in
                if !active { DispatchQueue.main.async { if !pulling && !committed { restorePower() } } }
            }
            .onChange(of: enabled) { _, active in if !active && !committed { restorePower() } }
            .onDisappear { if !committed { restorePower() } }
            .opacity(enabled ? 1 : 0.55)
            .accessibilityLabel("Puissance du tir")
            .accessibilityValue("\(Int(power)) pour cent")
            .accessibilityHint("Tirez puis relâchez pour jouer")
        }
        .frame(height: 46)
    }

    private func restorePower() {
        if let originalPower { power = originalPower }
        originalPower = nil
    }
}

struct WapiIOSPoolSpinPad: View {
    @Binding var sideSpin: Float
    @Binding var followSpin: Float
    let enabled: Bool

    var body: some View {
        GeometryReader { proxy in
            let diameter = min(proxy.size.width, proxy.size.height)
            let radius = diameter * 0.39
            let centre = CGPoint(x: proxy.size.width / 2, y: proxy.size.height / 2)
            ZStack {
                Circle().fill(Color.black.opacity(0.62))
                Circle().stroke(Color.white.opacity(0.20), lineWidth: 1)
                Circle().fill(LinearGradient(colors: [.white, Color(white: 0.84)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .padding(diameter * 0.10)
                    .shadow(color: .black.opacity(0.45), radius: 5, y: 3)
                Path { path in
                    path.move(to: CGPoint(x: centre.x - radius, y: centre.y)); path.addLine(to: CGPoint(x: centre.x + radius, y: centre.y))
                    path.move(to: CGPoint(x: centre.x, y: centre.y - radius)); path.addLine(to: CGPoint(x: centre.x, y: centre.y + radius))
                }.stroke(Color.black.opacity(0.16), lineWidth: 1)
                Circle().fill(Color.red).frame(width: 12, height: 12)
                    .overlay(Circle().fill(.white.opacity(0.72)).frame(width: 3, height: 3).offset(x: -2, y: -2))
                    .position(x: centre.x + CGFloat(sideSpin) * radius * 0.72, y: centre.y - CGFloat(followSpin) * radius * 0.72)
            }
            .contentShape(Circle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { value in
                guard enabled else { return }
                var x = Float((value.location.x - centre.x) / max(radius, 1))
                var y = Float((centre.y - value.location.y) / max(radius, 1))
                let length = sqrt(x * x + y * y)
                if length > 1 { x /= length; y /= length }
                sideSpin = x; followSpin = y
            })
            .opacity(enabled ? 1 : 0.55)
            .accessibilityLabel("Point d’impact sur la blanche")
            .accessibilityValue("Latéral \(Int(sideSpin * 100)), vertical \(Int(followSpin * 100))")
        }
    }
}

struct WapiIOS3DTabletop: UIViewRepresentable {
    let scene: String
    let dieValue: Int
    let poolAngle: Float
    let poolPower: Int
    let poolSideSpin: Float
    let poolFollowSpin: Float
    let poolShotRevision: Int
    var poolTableTheme = "competitionBlue"
    var poolCueStyle = "maple"
    var onPoolSettled: (Float, Int) -> Void = { _, _ in }
    var cueInHand = false
    var poolInputEnabled = false
    var onPoolAim: (Float) -> Void = { _ in }
    var onPoolPlacement: (Bool) -> Void = { _ in }
    var onPoolOutcome: (WapiPoolShotOutcome) -> Void = { _ in }
    var onPoolRemaining: (Set<Int>) -> Void = { _ in }

    final class Coordinator: NSObject, SCNSceneRendererDelegate {
        var currentScene = "" {
            didSet { if currentScene != oldValue { lastViewport = .zero } }
        }
        var currentDieValue = 1
        var currentPoolShotRevision = 0
        private var lastViewport = CGSize.zero
        var onPoolSettled: (Float, Int) -> Void = { _, _ in }
        var waitingForRest = false
        var restStarted: TimeInterval?
        var framesSinceShot = 0
        var active = true
        var cueInHand = false
        var poolInputEnabled = false
        var onPoolAim: (Float) -> Void = { _ in }
        var onPoolPlacement: (Bool) -> Void = { _ in }
        var onPoolOutcome: (WapiPoolShotOutcome) -> Void = { _ in }
        var onPoolRemaining: (Set<Int>) -> Void = { _ in }
        var lastRemaining = Set(1...15)

        @objc func touchPool(_ gesture: UIGestureRecognizer) {
            guard active, poolInputEnabled, !waitingForRest, currentScene == "Billard WAPI",
                  let view = gesture.view as? SCNView, let scene = view.scene,
                  let cue = scene.rootNode.childNode(withName: "wapi.pool.ball.0", recursively: false) else { return }
            let point = gesture.location(in: view)
            let near = view.unprojectPoint(SCNVector3(Float(point.x), Float(point.y), 0))
            let far = view.unprojectPoint(SCNVector3(Float(point.x), Float(point.y), 1))
            let dy = far.y - near.y
            guard abs(dy) > 0.0001 else { return }
            let t = (Float(0.40) - near.y) / dy
            guard t > 0 else { return }
            let x = near.x + (far.x - near.x) * t
            let z = near.z + (far.z - near.z) * t
            if cueInHand {
                let valid = WapiGameSceneKit.placePoolCue(in: scene, x: min(-0.45, max(-2.94, x)), z: min(1.46, max(-1.46, z)))
                onPoolPlacement(valid)
            } else { onPoolAim(atan2(z - cue.position.z, x - cue.position.x)) }
        }

        func renderer(_ renderer: SCNSceneRenderer, didSimulatePhysicsAtTime time: TimeInterval) {
            guard active, currentScene == "Billard WAPI", waitingForRest, let scene = renderer.scene else { return }
            let remaining = WapiGameSceneKit.poolRemaining(in: scene)
            if remaining != lastRemaining {
                lastRemaining = remaining
                DispatchQueue.main.async { if self.active { self.onPoolRemaining(remaining) } }
            }
            framesSinceShot += 1
            guard framesSinceShot > 15 else { return }
            let moving = scene.rootNode.childNodes.filter { $0.name?.hasPrefix("wapi.pool.ball.") == true }.contains { node in
                let velocity = node.physicsBody?.velocity ?? SCNVector3Zero
                return velocity.x * velocity.x + velocity.z * velocity.z > 0.0016 || node.hasActions
            }
            if moving { restStarted = nil; return }
            if restStarted == nil { restStarted = time }
            guard time - (restStarted ?? time) >= 0.35 else { return }
            waitingForRest = false; restStarted = nil
            let shot = WapiGameSceneKit.nextPoolPracticeShot(in: scene)
            let outcome = WapiGameSceneKit.poolOutcome(in: scene)
            DispatchQueue.main.async { if self.active { self.onPoolSettled(shot.0, shot.1); self.onPoolOutcome(outcome) } }
        }

        func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
            guard ["Billard WAPI", "Échecs WAPI", "Jeu de dames"].contains(currentScene), let scene = renderer.scene else { return }
            let viewport = renderer.currentViewport.size
            guard viewport != lastViewport, viewport.width > 0, viewport.height > 0 else { return }
            lastViewport = viewport
            if currentScene == "Billard WAPI" { WapiGameSceneKit.framePoolCamera(in: scene, viewport: viewport) }
            else { WapiGameSceneKit.frameStrategyCamera(in: scene, viewport: viewport) }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> SCNView {
        // SceneKit can fall back to legacy APIs when created without an
        // explicit backend. Use Metal on supported iPhones and iPads.
        let view = SCNView(
            frame: .zero,
            options: [SCNView.Option.preferredRenderingAPI.rawValue: SCNRenderingAPI.metal.rawValue]
        )
        view.scene = WapiGameSceneKit.makeScene(named: scene, dieValue: dieValue)
        view.allowsCameraControl = scene != "Billard WAPI"
        view.autoenablesDefaultLighting = false
        view.backgroundColor = UIColor(red: 0.015, green: 0.05, blue: 0.11, alpha: 1)
        view.antialiasingMode = .multisampling4X
        view.preferredFramesPerSecond = 60
        view.rendersContinuously = true
        view.isJitteringEnabled = scene != "Billard WAPI"
        view.delegate = context.coordinator
        if scene == "Billard WAPI" {
            view.addGestureRecognizer(UIPanGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.touchPool(_:))))
            view.addGestureRecognizer(UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.touchPool(_:))))
        }
        context.coordinator.currentScene = scene
        context.coordinator.currentDieValue = dieValue
        context.coordinator.currentPoolShotRevision = poolShotRevision
        if scene == "Billard WAPI" {
            WapiGameSceneKit.applyPoolAppearance(in: view.scene, tableTheme: poolTableTheme, cueStyle: poolCueStyle)
            WapiGameSceneKit.setPoolCue(in: view.scene, angle: poolAngle, power: poolPower, sideSpin: poolSideSpin, followSpin: poolFollowSpin)
        }
        return view
    }

    static func dismantleUIView(_ view: SCNView, coordinator: Coordinator) {
        coordinator.active = false
        coordinator.waitingForRest = false
        view.delegate = nil
        view.isPlaying = false
        view.scene?.rootNode.enumerateChildNodes { node, _ in node.removeAllActions() }
    }

    func updateUIView(_ view: SCNView, context: Context) {
        context.coordinator.onPoolSettled = onPoolSettled
        context.coordinator.cueInHand = cueInHand
        context.coordinator.poolInputEnabled = poolInputEnabled
        context.coordinator.onPoolAim = onPoolAim
        context.coordinator.onPoolPlacement = onPoolPlacement
        context.coordinator.onPoolOutcome = onPoolOutcome
        context.coordinator.onPoolRemaining = onPoolRemaining
        if context.coordinator.currentScene != scene {
            view.scene = WapiGameSceneKit.makeScene(named: scene, dieValue: dieValue)
            view.allowsCameraControl = scene != "Billard WAPI"
            view.isJitteringEnabled = scene != "Billard WAPI"
            context.coordinator.currentScene = scene
            context.coordinator.currentDieValue = dieValue
        } else if context.coordinator.currentDieValue != dieValue {
            WapiGameSceneKit.animateDie(in: view.scene, value: dieValue)
            context.coordinator.currentDieValue = dieValue
        }
        if scene == "Billard WAPI" {
            WapiGameSceneKit.applyPoolAppearance(in: view.scene, tableTheme: poolTableTheme, cueStyle: poolCueStyle)
            if context.coordinator.currentPoolShotRevision != poolShotRevision {
                WapiGameSceneKit.strikePool(in: view.scene, angle: poolAngle, power: poolPower, sideSpin: poolSideSpin, followSpin: poolFollowSpin)
                context.coordinator.currentPoolShotRevision = poolShotRevision
                context.coordinator.waitingForRest = true
                context.coordinator.framesSinceShot = 0
                context.coordinator.restStarted = nil
            } else if !context.coordinator.waitingForRest {
                WapiGameSceneKit.setPoolCue(in: view.scene, angle: poolAngle, power: poolPower, sideSpin: poolSideSpin, followSpin: poolFollowSpin)
                if let scene = view.scene { WapiGameSceneKit.setPoolPlacementHand(in: scene, visible: cueInHand) }
            }
        }
    }
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
                        Text(prompt).font(.system(size: 27, weight: .bold, design: .rounded)).foregroundStyle(.white)
                        Text(game.subtitle).font(.footnote).foregroundStyle(.white.opacity(0.66))
                    }.frame(maxWidth: .infinity, alignment: .leading)
                    ForEach(options, id: \.self) { option in
                        Button {
                            guard answer == nil else { return }
                            answer = option
                            if option == correct { score += 25; streak += 1; UINotificationFeedbackGenerator().notificationOccurred(.success); WapiSounds.gameReward() }
                            else { WapiSounds.gameInvalid() }
                        } label: {
                            HStack { Text(option).font(.headline); Spacer(); if answer == option { Image(systemName: option == correct ? "checkmark.circle.fill" : "xmark.circle.fill") } }
                                .foregroundStyle(answer == option ? .white : Color.whappyInk)
                                .padding(17)
                                .background(answer == option ? (option == correct ? Color.green : Color.red) : Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 17))
                        }.buttonStyle(WapiPressableButtonStyle())
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
    KingQiIOSQuestion(category: "GÉOGRAPHIE", difficulty: "FACILE", prompt: "Quelle est la capitale du Canada ?", options: ["Toronto", "Vancouver", "Ottawa", "Montréal"], answer: 2),
    KingQiIOSQuestion(category: "SCIENCES", difficulty: "FACILE", prompt: "Quelle est la plus grande planète du système solaire ?", options: ["Mars", "Jupiter", "Saturne", "Neptune"], answer: 1),
    KingQiIOSQuestion(category: "LOGIQUE", difficulty: "FACILE", prompt: "Combien font 9 multiplié par 8 ?", options: ["64", "70", "72", "81"], answer: 2),
    KingQiIOSQuestion(category: "AFRIQUE", difficulty: "MOYEN", prompt: "Dans quelle ville se trouve le siège de l'Union africaine ?", options: ["Nairobi", "Addis-Abeba", "Le Caire", "Dakar"], answer: 1),
    KingQiIOSQuestion(category: "TECHNOLOGIE", difficulty: "MOYEN", prompt: "Quel est le système de numération fondé sur zéro et un ?", options: ["Décimal", "Binaire", "Hexadécimal", "Romain"], answer: 1),
    KingQiIOSQuestion(category: "CULTURE", difficulty: "MOYEN", prompt: "Qui a écrit « Les Misérables » ?", options: ["Victor Hugo", "Émile Zola", "Molière", "Alexandre Dumas"], answer: 0),
    KingQiIOSQuestion(category: "SCIENCES", difficulty: "MOYEN", prompt: "Quel métal porte le symbole chimique Au ?", options: ["Argent", "Aluminium", "Or", "Cuivre"], answer: 2),
    KingQiIOSQuestion(category: "SPORT", difficulty: "MOYEN", prompt: "Combien de joueurs d'une équipe de basket sont sur le terrain ?", options: ["Cinq", "Six", "Sept", "Huit"], answer: 0),
    KingQiIOSQuestion(category: "SCIENCES", difficulty: "EXPERT", prompt: "Combien de chromosomes possède normalement une cellule humaine ?", options: ["23", "44", "46", "48"], answer: 2),
    KingQiIOSQuestion(category: "TECHNOLOGIE", difficulty: "EXPERT", prompt: "Que signifie HTTP ?", options: ["HyperText Transfer Protocol", "High Transfer Text Process", "Hosted Terminal Transport Program", "Hybrid Text Transmission Port"], answer: 0),
    KingQiIOSQuestion(category: "AFRIQUE", difficulty: "EXPERT", prompt: "Quelle militante kényane fut la première Africaine à recevoir le prix Nobel de la paix ?", options: ["Miriam Makeba", "Wangari Maathai", "Ellen Johnson Sirleaf", "Graça Machel"], answer: 1),
    KingQiIOSQuestion(category: "LOGIQUE", difficulty: "EXPERT", prompt: "Quelle valeur approche le mieux le nombre pi ?", options: ["2,14", "2,72", "3,14", "4,13"], answer: 2),
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
                    HStack { Text("♛").font(.system(size: 40)).frame(width: 58, height: 58).background(Color.yellow).clipShape(Circle()); VStack(alignment: .leading) { Text("KING QI").font(.system(size: 31, weight: .bold)); Text("La connaissance devient un spectacle.").font(.caption).foregroundStyle(.white.opacity(0.7)) } }
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
    var body: some View { Button(action: action) { HStack(spacing: 13) { Image(systemName: icon).font(.title2).foregroundStyle(color).frame(width: 54, height: 54).background(color.opacity(0.12)).clipShape(RoundedRectangle(cornerRadius: 17)); VStack(alignment: .leading, spacing: 4) { Text(title).font(.headline).foregroundStyle(Color.whappyInk); Text(subtitle).font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.leading) }; Spacer(); Image(systemName: "chevron.right").foregroundStyle(color) }.padding(15).background(.white).clipShape(RoundedRectangle(cornerRadius: 20)) }.buttonStyle(WapiPressableButtonStyle()) }
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
    @State private var questions = Array(kingQiIOSQuestions.shuffled().prefix(15))
    @State private var index = 0
    @State private var selected: Int?
    @State private var score = 0
    @State private var seconds = 15
    @State private var finished = false
    @State private var roundToken = UUID()
    private let voice = AVSpeechSynthesizer()
    private var question: KingQiIOSQuestion { questions[min(index, questions.count - 1)] }

    var body: some View {
        Group {
            if finished { result }
            else { ScrollView { VStack(spacing: 14) {
                HStack { Button("Quitter", action: onExit).buttonStyle(.bordered); Spacer(); Text("\(index + 1)/\(questions.count)").bold(); Text("\(seconds)").font(.headline.bold()).frame(width: 48, height: 48).background(seconds <= 5 ? Color.red : Color.yellow).clipShape(Circle()).foregroundStyle(seconds <= 5 ? .white : Color.whappyInk) }
                VStack(alignment: .leading, spacing: 14) { HStack { Text(question.category).font(.caption.bold()).foregroundStyle(.yellow); Spacer(); Text(question.difficulty).font(.caption.bold()).foregroundStyle(.white.opacity(0.6)) }; Text(question.prompt).font(.system(size: 25, weight: .bold)).foregroundStyle(.white); Text("SCORE  \(score)").font(.caption.bold()).foregroundStyle(.white.opacity(0.65)) }.padding(22).frame(maxWidth: .infinity, alignment: .leading).background(Color(red: 0.03, green: 0.11, blue: 0.25)).clipShape(RoundedRectangle(cornerRadius: 28))
                ForEach(question.options.indices, id: \.self) { option in kingAnswer(option) }
                Button { speech.start() } label: { Label(speech.listening ? "Je vous écoute…" : "Répondre avec ma voix", systemImage: "mic.fill").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent).tint(speech.listening ? .red : .whappyBlue).disabled(selected != nil)
                if let selected { Text(selected == question.answer ? "Bonne réponse" : "Réponse : \(question.options[question.answer])").font(.headline).foregroundStyle(selected == question.answer ? .green : .red); ProgressView().tint(Color.whappyInk); Text(index == questions.count - 1 ? "Résultat dans un instant…" : "Question suivante automatique…").font(.caption.bold()).foregroundStyle(.secondary) }
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
        return Button { choose(option) } label: { HStack { Text(["A", "B", "C", "D"][option]).font(.headline.bold()).foregroundStyle(.white).frame(width: 38, height: 38).background(revealed ? color : Color.whappyInk).clipShape(RoundedRectangle(cornerRadius: 11)); Text(question.options[option]).font(.headline).foregroundStyle(Color.whappyInk); Spacer() }.padding(13).background(revealed && option == question.answer ? Color.green.opacity(0.1) : .white).overlay(RoundedRectangle(cornerRadius: 17).stroke(color, lineWidth: 1.5)).clipShape(RoundedRectangle(cornerRadius: 17)) }.buttonStyle(WapiPressableButtonStyle()).disabled(revealed)
    }

    private var result: some View { ZStack { Color(red: 0.03, green: 0.11, blue: 0.25).ignoresSafeArea(); VStack(spacing: 14) { Text("🏆").font(.system(size: 86)); Text("GRAND CHAMPION").font(.largeTitle).fontWeight(.black).foregroundStyle(.yellow); Text("\(score) points").foregroundStyle(.white.opacity(0.75)); Button("Rejouer") { questions = Array(kingQiIOSQuestions.shuffled().prefix(15)); index = 0; score = 0; finished = false; beginQuestion() }.buttonStyle(.borderedProminent).tint(.yellow).foregroundStyle(Color.whappyInk); Button("Retour aux jeux", action: onExit).foregroundStyle(.white) } } }

    private func beginQuestion() {
        let token = UUID(); roundToken = token; selected = nil; seconds = 15; speech.transcript = ""
        voice.stopSpeaking(at: .immediate); let utterance = AVSpeechUtterance(string: question.prompt); utterance.voice = AVSpeechSynthesisVoice(language: "fr-FR"); voice.speak(utterance)
        Task { @MainActor in
            while roundToken == token && seconds > 0 && selected == nil && !finished {
                try? await Task.sleep(for: .seconds(1))
                if roundToken == token && selected == nil {
                    seconds -= 1
                    if (1...5).contains(seconds) { WapiSounds.gameQuizTick(urgent: seconds <= 3) }
                }
            }
            if roundToken == token && seconds == 0 && selected == nil { selected = -1; WapiSounds.gameQuizWrong(); advanceAutomatically(token) }
        }
    }

    private func choose(_ option: Int) { guard selected == nil else { return }; let token = roundToken; selected = option; speech.stop(); if option == question.answer { score += 500 + seconds * 25; WapiSounds.gameQuizCorrect() } else { WapiSounds.gameQuizWrong() }; advanceAutomatically(token) }
    private func advanceAutomatically(_ token: UUID) { Task { @MainActor in try? await Task.sleep(for: .milliseconds(1_650)); guard roundToken == token, selected != nil, !finished else { return }; if index == questions.count - 1 { finished = true } else { index += 1 } } }
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
    @State private var onlineSeconds = 20
    @State private var autoAdvancedQuestionID = ""
    private let functions = Functions.functions(region: "europe-west1")

    var body: some View { ScrollView { VStack(spacing: 14) {
        HStack { Button("Retour", action: onExit).buttonStyle(.bordered); Spacer(); Text("KING QI SOCIAL").font(.headline.bold()) }
        if roomID.isEmpty { setup } else { roomView }
        if let error { Text(error).font(.footnote).foregroundStyle(.red) }
    }.padding() }.task(id: roundClockKey) { await runRoundClock() }.onDisappear { listener?.remove() } }

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
        let answered = answeredIDs.contains(uid)
        return VStack(spacing: 12) {
            Text("CODE  \(room["code"] as? String ?? "")").font(.title2.bold())
            let capacity = (room["maxPlayers"] as? NSNumber)?.intValue ?? (room["maxPlayers"] as? Int ?? maxPlayers)
            Text(status == "waiting" ? "\(playerIDs.count)/\(capacity) joueurs dans l’arène" : status == "finished" ? (capacity == 2 ? "Duel terminé" : "Tournoi terminé") : "King QI en cours").font(.title.bold()).foregroundStyle(.white).padding().frame(maxWidth: .infinity).background(Color.whappyInk).clipShape(RoundedRectangle(cornerRadius: 22))
            KingQiIOSPlayerStage(playerIDs: playerIDs, names: names, photos: photos, scores: scores, answeredIDs: answeredIDs, currentUserID: uid, capacity: capacity)
            if status == "waiting", room["hostId"] as? String == uid { Button("Démarrer King QI") { call("kingQiStartTournament", ["roomId": roomID]) }.buttonStyle(.borderedProminent).disabled(playerIDs.count < 2 || busy) }
            if status == "waiting", room["hostId"] as? String == uid, room["visibility"] as? String == "live" { Button { call("kingQiPrepareLive", ["roomId": roomID]) { _ in onStartLive() } } label: { Label("Ouvrir mon direct WAPI", systemImage: "video.fill") }.buttonStyle(.bordered) }
            if status == "playing", let question = room["currentQuestion"] as? [String: Any], let options = question["options"] as? [String] {
                HStack { Text(question["category"] as? String ?? "KING QI").font(.caption.bold()).foregroundStyle(Color.whappyBlue); Spacer(); ZStack { Circle().stroke(Color.gray.opacity(0.2), lineWidth: 5); Circle().trim(from: 0, to: min(max(Double(onlineSeconds) / 20, 0), 1)).stroke(onlineSeconds <= 5 ? Color.red : Color.orange, style: StrokeStyle(lineWidth: 5, lineCap: .round)).rotationEffect(.degrees(-90)); Text("\(onlineSeconds)").font(.headline.bold()).foregroundStyle(onlineSeconds <= 5 ? .red : Color.whappyInk) }.frame(width: 50, height: 50) }
                Text(question["text"] as? String ?? "Question").font(.title2.bold())
                ForEach(options.indices, id: \.self) { option in Button(options[option]) { call("kingQiSubmitAnswer", ["roomId": roomID, "optionIndex": option]) { result in if result["correct"] as? Bool == true { WapiSounds.gameQuizCorrect() } else { WapiSounds.gameQuizWrong() } } }.buttonStyle(.bordered).frame(maxWidth: .infinity).disabled(answered || busy) }
                Text(answered ? "Réponse verrouillée · prochaine question automatique" : "Répondez avant la fin du chronomètre").font(.caption.bold()).foregroundStyle(answered ? .green : .secondary)
            }
            if status == "finished" { Text(((room["winners"] as? [String])?.contains(uid) == true) ? "🏆 GRAND CHAMPION" : "Tournoi terminé").font(.largeTitle).fontWeight(.black).foregroundStyle(.orange) }
        }
    }

    private var roundClockKey: String {
        let status = room["status"] as? String ?? ""
        let question = room["currentQuestion"] as? [String: Any]
        let questionID = question?["id"] as? String ?? ""
        let deadline = (room["roundDeadline"] as? Timestamp)?.seconds ?? 0
        let answeredCount = (room["answeredIds"] as? [String])?.count ?? 0
        return "\(status)-\(questionID)-\(deadline)-\(answeredCount)"
    }

    @MainActor
    private func runRoundClock() async {
        guard room["status"] as? String == "playing",
              let question = room["currentQuestion"] as? [String: Any],
              let questionID = question["id"] as? String,
              !questionID.isEmpty,
              let deadline = room["roundDeadline"] as? Timestamp else { return }
        let players = room["playerIds"] as? [String] ?? []
        let answered = room["answeredIds"] as? [String] ?? []
        let hostID = room["hostId"] as? String ?? ""
        let uid = Auth.auth().currentUser?.uid ?? ""
        var previous = Int.max
        while !Task.isCancelled {
            let remaining = max(0, Int(ceil(deadline.dateValue().timeIntervalSinceNow)))
            if previous != remaining {
                onlineSeconds = remaining
                previous = remaining
                if (1...5).contains(remaining) { WapiSounds.gameQuizTick(urgent: remaining <= 3) }
            }
            if remaining == 0 || (!players.isEmpty && answered.count >= players.count) {
                if hostID == uid && autoAdvancedQuestionID != questionID {
                    autoAdvancedQuestionID = questionID
                    try? await Task.sleep(for: .milliseconds(900))
                    call("kingQiAdvanceTournament", ["roomId": roomID])
                }
                return
            }
            try? await Task.sleep(for: .milliseconds(250))
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
    var body: some View { Button(action: action) { VStack(spacing: 9) { Image(systemName: icon).font(.title2); Text(title).font(.caption.bold()) }.frame(maxWidth: .infinity).padding(.vertical, 18).background(.white).clipShape(RoundedRectangle(cornerRadius: 16)) }.buttonStyle(WapiPressableButtonStyle()) }
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
    @StateObject private var adMetrics = WapiBusinessAdMetricsIOSStore()
    @StateObject private var campaigns = WapiBusinessCampaignsIOSStore()

    private var businessMessages: [Conversation] { store.conversations.filter { $0.profileType == "business" } }
    private var recentBusinessMessages: [Conversation] {
        Array(businessMessages.sorted { ($0.messages.last?.sentAt ?? .distantPast) > ($1.messages.last?.sentAt ?? .distantPast) }.prefix(3))
    }
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

                businessQuickActions

                Text("Vue d’ensemble").font(.title3.weight(.bold)).foregroundStyle(Color.whappyInk)
                HStack(spacing: 10) {
                    BusinessMetricIOS(title: "Clients", value: "\(businessMessages.count)", detail: unreadClients == 0 ? "À jour" : "\(unreadClients) nouveau(x)")
                    BusinessMetricIOS(title: "Commandes", value: "\(store.orders.count)", detail: "Données réelles")
                    BusinessMetricIOS(title: "Profil", value: "\(profileCompletion) %", detail: profileCompletion == 100 ? "Complet" : "À compléter")
                }
                HStack(spacing: 10) {
                    BusinessMetricIOS(title: "Vues Ads", value: "\(adMetrics.impressions)", detail: "Impressions réelles")
                    BusinessMetricIOS(title: "Clics Ads", value: "\(adMetrics.clicks)", detail: String(format: "Taux %.1f %%", adMetrics.clickThroughRate))
                }

                if !campaigns.items.isEmpty {
                    Text("Campagnes récentes").font(.title3.weight(.bold)).foregroundStyle(Color.whappyInk)
                    ForEach(campaigns.items.prefix(4)) { campaign in
                        WapiBusinessCampaignRowIOS(campaign: campaign, busy: campaigns.busyID == campaign.id) { action in
                            campaigns.update(campaign, action: action)
                        }
                    }
                }

                businessRecentClients
                WapiCommerceLaunchersIOS()

                Text("Centre de gestion").font(.title3.weight(.bold)).foregroundStyle(Color.whappyInk)
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                    NavigationLink { BusinessSaleRoomManagerIOS() } label: {
                        BusinessControlTileIOS(icon: "dot.radiowaves.left.and.right", title: "Vente en direct", detail: "Publique ou privée", tint: .orange)
                    }
                    NavigationLink { OrdersView() } label: {
                        BusinessControlTileIOS(icon: "shippingbox.fill", title: "Commandes", detail: "\(store.orders.count) à suivre", tint: .purple)
                    }
                    NavigationLink { BusinessCampaignIOSView() } label: {
                        BusinessControlTileIOS(icon: "megaphone.fill", title: "WAPI Ads", detail: "Ciblage régional", tint: .blue)
                    }
                    NavigationLink { BusinessEditorView() } label: {
                        BusinessControlTileIOS(icon: "building.2.crop.circle.fill", title: "Identité", detail: "Profil à \(profileCompletion) %", tint: .green)
                    }
                    Button { store.selectedTab = .wia } label: {
                        BusinessControlTileIOS(icon: "sparkles", title: "WIA Business", detail: "Ventes et contenus", tint: .indigo)
                    }
                    NavigationLink { MarketView() } label: {
                        BusinessControlTileIOS(icon: "tag.fill", title: "Catalogue", detail: "Produits et offres", tint: .cyan)
                    }
                }
                .buttonStyle(WapiPressableButtonStyle())

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
        .onAppear { adMetrics.start() }
        .onAppear { campaigns.start() }
        .onDisappear { adMetrics.stop(); campaigns.stop() }
    }

    private var businessQuickActions: some View {
        HStack(spacing: 8) {
            BusinessQuickActionIOS(icon: "bubble.left.fill", title: "Clients", badge: unreadClients > 0 ? "\(unreadClients)" : nil) {
                store.switchAccount(business: true)
                store.selectedTab = .messages
            }
            BusinessQuickActionIOS(icon: "phone.fill", title: "Appels") {
                store.switchAccount(business: true)
                store.selectedTab = .calls
            }
        }
    }

    private var businessRecentClients: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Clients récents").font(.headline.weight(.bold)).foregroundStyle(Color.whappyInk)
                    Text("Messagerie de votre identité Business").font(.caption2).foregroundStyle(WapiColor.secondaryText)
                }
                Spacer()
                Button("Tout voir") {
                    store.switchAccount(business: true)
                    store.selectedTab = .messages
                }
                .font(.caption.weight(.bold))
            }
            .padding(.horizontal, 2)

            if businessMessages.isEmpty {
                Button {
                    store.switchAccount(business: true)
                    store.selectedTab = .messages
                } label: {
                    HStack(spacing: 11) {
                        Image(systemName: "bubble.left.and.bubble.right.fill")
                            .font(.system(size: 17, weight: .semibold)).foregroundStyle(Color.whappyBlue)
                            .frame(width: 42, height: 42).background(WapiColor.blueMist).clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Votre boîte clients est prête").font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk)
                            Text("Les nouvelles demandes apparaîtront ici.").font(.caption2).foregroundStyle(WapiColor.secondaryText)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(WapiColor.secondaryText)
                    }
                    .padding(12).background(.white).clipShape(RoundedRectangle(cornerRadius: 19, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 19, style: .continuous).stroke(WapiColor.line, lineWidth: 0.8))
                }
                .buttonStyle(WapiPressableButtonStyle())
            } else {
                VStack(spacing: 0) {
                    ForEach(recentBusinessMessages.indices, id: \.self) { index in
                        let conversation = recentBusinessMessages[index]
                        Button {
                            store.switchAccount(business: true)
                            store.pendingConversationID = conversation.id
                            store.selectedTab = .messages
                        } label: {
                            HStack(spacing: 11) {
                                ConversationAvatar(conversation: conversation)
                                    .frame(width: 45, height: 45)
                                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(conversation.name).font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk).lineLimit(1)
                                    Text(conversation.lastMessage.isEmpty ? "Nouvelle conversation" : conversation.lastMessage)
                                        .font(.caption).foregroundStyle(conversation.unread ? Color.whappyInk : WapiColor.secondaryText).lineLimit(1)
                                }
                                Spacer()
                                if conversation.unread { Circle().fill(Color.whappyBlue).frame(width: 9, height: 9) }
                                Image(systemName: "chevron.right").font(.caption2.bold()).foregroundStyle(WapiColor.secondaryText)
                            }
                            .padding(.horizontal, 12).padding(.vertical, 9)
                        }
                        .buttonStyle(WapiPressableButtonStyle())
                        if index < recentBusinessMessages.count - 1 { Divider().padding(.leading, 68) }
                    }
                }
                .background(.white).clipShape(RoundedRectangle(cornerRadius: 19, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 19, style: .continuous).stroke(WapiColor.line, lineWidth: 0.8))
            }
        }
    }

    private var businessHero: some View {
        ZStack(alignment: .topLeading) {
            Color.white
            RoundedRectangle(cornerRadius: 2).fill(Color.whappyBlue).frame(width: 5, height: 84)
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
                        HStack(spacing: 7) {
                            Text("COMPTE BUSINESS").font(.system(size: 9, weight: .bold)).tracking(0.6).foregroundStyle(WapiColor.deepBlue)
                            Text("SÉPARÉ").font(.system(size: 7, weight: .bold)).foregroundStyle(.green)
                                .padding(.horizontal, 7).padding(.vertical, 3).background(Color.green.opacity(0.10)).clipShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
                        }
                        Text(store.business?.name ?? "Créez votre entreprise").font(.title2.weight(.bold)).foregroundStyle(Color.whappyInk).lineLimit(1)
                        Text(store.business.map { "\($0.category) · \($0.city)" } ?? "Identité, appels et clients professionnels").font(.caption).foregroundStyle(WapiColor.secondaryText)
                    }
                }
                NavigationLink { BusinessEditorView() } label: {
                    Label(store.business == nil ? "Configurer mon Business" : "Gérer l’identité professionnelle", systemImage: store.business == nil ? "plus" : "pencil")
                        .font(.subheadline.weight(.bold)).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).frame(height: 44).background(WapiColor.ink).clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(WapiPressableButtonStyle())
            }
            .padding(20)
        }
        .clipShape(RoundedRectangle(cornerRadius: WapiRadius.hero, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: WapiRadius.hero, style: .continuous).stroke(WapiColor.line, lineWidth: 0.9))
    }
}

@MainActor
private final class WapiBusinessAdMetricsIOSStore: ObservableObject {
    @Published var impressions = 0
    @Published var clicks = 0
    private var listener: ListenerRegistration?
    var clickThroughRate: Double { impressions == 0 ? 0 : Double(clicks) * 100 / Double(impressions) }

    func start() {
        guard listener == nil, let ownerID = Auth.auth().currentUser?.uid else { return }
        listener = Firestore.firestore().collection("adEvents")
            .whereField("ownerId", isEqualTo: ownerID)
            .limit(to: 5_000)
            .addSnapshotListener { [weak self] snapshot, _ in
                let types = snapshot?.documents.compactMap { $0.get("type") as? String } ?? []
                Task { @MainActor in
                    self?.impressions = types.filter { $0 == "impression" }.count
                    self?.clicks = types.filter { $0 == "click" }.count
                }
            }
    }

    func stop() { listener?.remove(); listener = nil }
}

private struct WapiBusinessCampaignIOS: Identifiable {
    let id: String
    let title: String
    let status: String
    let city: String
    let totalBudget: Int
    let impressions: Int
    let clicks: Int
    let deliveryMode: String
    let targetImpressions: Int
    let dailyDeliveryCap: Int
}

@MainActor
private final class WapiBusinessCampaignsIOSStore: ObservableObject {
    @Published var items: [WapiBusinessCampaignIOS] = []
    @Published var busyID: String?
    private var listener: ListenerRegistration?

    func start() {
        guard listener == nil, let ownerID = Auth.auth().currentUser?.uid else { return }
        listener = Firestore.firestore().collection("adCampaigns").whereField("ownerId", isEqualTo: ownerID).limit(to: 100)
            .addSnapshotListener { [weak self] snapshot, _ in
                let values = snapshot?.documents.map { document in
                    WapiBusinessCampaignIOS(
                        id: document.documentID,
                        title: document.get("title") as? String ?? "Campagne WAPI",
                        status: document.get("status") as? String ?? "pending_payment",
                        city: document.get("city") as? String ?? "",
                        totalBudget: (document.get("totalBudget") as? NSNumber)?.intValue ?? 0,
                        impressions: (document.get("impressionCount") as? NSNumber)?.intValue ?? 0,
                        clicks: (document.get("clickCount") as? NSNumber)?.intValue ?? 0,
                        deliveryMode: document.get("deliveryMode") as? String ?? "budget",
                        targetImpressions: (document.get("targetImpressions") as? NSNumber)?.intValue ?? 0,
                        dailyDeliveryCap: (document.get("dailyDeliveryCap") as? NSNumber)?.intValue ?? 0
                    )
                } ?? []
                Task { @MainActor in self?.items = values }
            }
    }

    func stop() { listener?.remove(); listener = nil }

    func update(_ campaign: WapiBusinessCampaignIOS, action: String) {
        guard busyID == nil else { return }
        busyID = campaign.id
        Functions.functions(region: "europe-west1").httpsCallable("updateAdCampaignDelivery").call([
            "campaignId": campaign.id,
            "action": action,
        ]) { [weak self] _, _ in Task { @MainActor in self?.busyID = nil } }
    }
}

private struct WapiBusinessCampaignRowIOS: View {
    let campaign: WapiBusinessCampaignIOS
    let busy: Bool
    let onAction: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack {
                Text(campaign.title).font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk)
                Spacer()
                Text(statusLabel).font(.system(size: 8, weight: .bold)).foregroundStyle(statusColor)
                    .padding(.horizontal, 8).padding(.vertical, 5).background(statusColor.opacity(0.10)).clipShape(Capsule())
            }
            Text("\(campaign.totalBudget.formatted()) FCFA · \(campaign.city.isEmpty ? "Zone WAPI" : campaign.city)").font(.caption).foregroundStyle(WapiColor.secondaryText)
            if campaign.deliveryMode == "impressions", campaign.targetImpressions > 0 {
                VStack(alignment: .leading, spacing: 5) {
                    Text("\(min(campaign.impressions, campaign.targetImpressions)) / \(campaign.targetImpressions) diffusions livrées")
                        .font(.caption.weight(.semibold)).foregroundStyle(Color.whappyBlue)
                    ProgressView(value: Double(min(campaign.impressions, campaign.targetImpressions)), total: Double(campaign.targetImpressions)).tint(Color.whappyBlue)
                    Text("Reste \(max(0, campaign.targetImpressions - campaign.impressions).formatted()) · cadence \(max(1, campaign.dailyDeliveryCap).formatted())/jour")
                        .font(.caption2).foregroundStyle(WapiColor.secondaryText)
                }
            } else {
                Text("\(campaign.impressions) impressions · \(campaign.clicks) clics").font(.caption.weight(.semibold)).foregroundStyle(Color.whappyBlue)
            }
            if campaign.status == "active" || campaign.status == "paused" {
                HStack {
                    Button(campaign.status == "active" ? "Mettre en pause" : "Reprendre") { onAction(campaign.status == "active" ? "pause" : "resume") }.buttonStyle(.borderedProminent).disabled(busy)
                    Button("Terminer", role: .destructive) { onAction("complete") }.buttonStyle(.bordered).disabled(busy)
                }
            }
        }
        .padding(14).wapiPanel(radius: 18)
    }

    private var statusLabel: String {
        switch campaign.status { case "active": return "EN DIFFUSION"; case "paused": return "EN PAUSE"; case "pending_payment": return "PAIEMENT REQUIS"; case "rejected": return "REFUSÉE"; default: return campaign.status.uppercased() }
    }
    private var statusColor: Color { campaign.status == "active" ? .green : campaign.status == "rejected" ? .red : .orange }
}

private struct BusinessQuickActionIOS: View {
    let icon: String
    let title: String
    var badge: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: icon).font(.system(size: 17, weight: .semibold)).foregroundStyle(WapiColor.deepBlue)
                    .frame(width: 38, height: 38).background(WapiColor.blueMist).clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                Text(title).font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk)
                Spacer()
                if let badge { Text(badge).font(.caption2.bold()).foregroundStyle(.white).padding(6).background(Color.whappyBlue).clipShape(Circle()) }
                Image(systemName: "arrow.up.right").font(.caption.bold()).foregroundStyle(WapiColor.secondaryText)
            }
            .padding(11)
            .frame(maxWidth: .infinity)
            .background(.white)
            .clipShape(RoundedRectangle(cornerRadius: WapiRadius.panel, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: WapiRadius.panel, style: .continuous).stroke(WapiColor.line, lineWidth: 0.8))
        }
        .buttonStyle(WapiPressableButtonStyle())
    }
}

private struct BusinessControlTileIOS: View {
    let icon: String
    let title: String
    let detail: String
    let tint: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 11) {
            Image(systemName: icon).font(.system(size: 18, weight: .semibold)).foregroundStyle(tint)
                .frame(width: 42, height: 42).background(tint.opacity(0.11)).clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk).lineLimit(1)
                Text(detail).font(.caption2.weight(.medium)).foregroundStyle(WapiColor.secondaryText).lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 108, alignment: .leading)
        .padding(14)
        .background(.white)
        .clipShape(RoundedRectangle(cornerRadius: WapiRadius.panel, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: WapiRadius.panel, style: .continuous).stroke(WapiColor.line, lineWidth: 0.8))
    }
}

private struct BusinessMetricIOS: View {
    let title: String
    let value: String
    let detail: String
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Capsule().fill(LinearGradient(colors: [WapiColor.sky, WapiColor.blue, WapiColor.violet], startPoint: .leading, endPoint: .trailing)).frame(width: 26, height: 3).padding(.bottom, 4)
            Text(title.uppercased()).font(.system(size: 8, weight: .bold)).foregroundStyle(WapiColor.secondaryText).lineLimit(1)
            Text(value).font(.headline.weight(.semibold)).foregroundStyle(Color.whappyInk).lineLimit(1)
            Text(detail).font(.system(size: 8, weight: .medium)).foregroundStyle(WapiColor.secondaryText).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(11)
        .background(LinearGradient(colors: [WapiColor.blueMist, Color.white], startPoint: .topLeading, endPoint: .bottomTrailing))
        .clipShape(RoundedRectangle(cornerRadius: 17, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 17, style: .continuous).stroke(WapiColor.sky.opacity(0.20), lineWidth: 0.8))
    }
}

private struct BusinessOperationIOS: View {
    let icon: String
    let title: String
    let detail: String
    var badge: String? = nil
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 19, weight: .semibold)).foregroundStyle(WapiColor.deepBlue)
                .frame(width: 46, height: 46).background(LinearGradient(colors: [WapiColor.blueMist, WapiColor.violet.opacity(0.09)], startPoint: .topLeading, endPoint: .bottomTrailing)).clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk)
                Text(detail).font(.caption).foregroundStyle(WapiColor.secondaryText).lineLimit(2)
            }
            Spacer()
            if let badge { Text(badge).font(.caption2.weight(.bold)).foregroundStyle(.white).padding(.horizontal, 8).padding(.vertical, 5).background(Color.whappyBlue).clipShape(Capsule()) }
            Image(systemName: "arrow.right").font(.caption.bold()).foregroundStyle(WapiColor.deepBlue)
                .frame(width: 30, height: 30).background(WapiColor.blueMist).clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .padding(14).wapiPanel(radius: 20)
    }
}

private struct BusinessCampaignIOSView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var title = ""
    @State private var creative = ""
    @State private var objective = "messages"
    @State private var placement = "inbox"
    @State private var destination = "message"
    @State private var audience = "Utilisateurs WAPI de la région"
    @State private var city = "Brazzaville"
    @State private var countryCode = Locale.current.region?.identifier ?? "CG"
    @State private var targetImpressions = "10000"
    @State private var days = "3"
    @State private var saving = false
    @State private var result: String?
    private let twoColumns = [GridItem(.flexible(), spacing: 9), GridItem(.flexible(), spacing: 9)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                VStack(alignment: .leading, spacing: 7) {
                    Text("ELEPHANT · WAPI ADS").font(.system(size: 10, weight: .bold)).tracking(0.7).foregroundStyle(Color.whappyBlue)
                    Text("Nouvelle campagne").font(.title2.weight(.bold)).foregroundStyle(Color.whappyInk)
                    Text("Construisez votre campagne étape par étape. Aucun paiement ne part sans votre confirmation.")
                        .font(.subheadline).foregroundStyle(WapiColor.secondaryText)
                }

                adSection(number: "1", title: "Identité et objectif", detail: "Choisissez qui parle et le résultat attendu") {
                    HStack(spacing: 11) {
                        if let business = store.business, let url = URL(string: business.logoURL), !business.logoURL.isEmpty {
                            WapiCachedRemoteImage(url: url) { InitialsAvatar(text: business.name, size: 46) }
                                .frame(width: 46, height: 46).clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        } else { InitialsAvatar(text: store.business?.name ?? "Business", size: 46).clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous)) }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(store.business?.name ?? "Profil Business requis").font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk)
                            Text("Page à promouvoir").font(.caption).foregroundStyle(WapiColor.secondaryText)
                        }
                        Spacer()
                        Image(systemName: "checkmark.seal.fill").foregroundStyle(Color.whappyBlue)
                    }
                    .padding(14).wapiPanel(radius: 18)

                    LazyVGrid(columns: twoColumns, spacing: 9) {
                        adChoice("Visibilité", icon: "eye.fill", value: "reach", selection: $objective)
                        adChoice("Messages", icon: "bubble.left.fill", value: "messages", selection: $objective)
                        adChoice("Trafic", icon: "arrow.up.right", value: "traffic", selection: $objective)
                        adChoice("Ventes", icon: "cart.fill", value: "sales", selection: $objective)
                    }
                }

                adSection(number: "2", title: "Création", detail: "Un message lisible et adapté au mobile") {
                    VStack(spacing: 10) {
                        WapiAdTextFieldIOS(title: "Nom de la campagne", text: $title)
                        WapiAdTextFieldIOS(title: "Message publicitaire", text: $creative, multiline: true)
                    }
                    WapiAdCreativePreviewIOS(
                        pageName: store.business?.name ?? "Business WAPI",
                        title: title,
                        message: creative,
                        city: city,
                        action: destination == "message" ? "Envoyer un message" : "Découvrir"
                    )
                }

                adSection(number: "3", title: "Diffusion", detail: "Emplacement et action après le clic") {
                    Text("EMPLACEMENT").font(.system(size: 9, weight: .bold)).foregroundStyle(WapiColor.secondaryText)
                    LazyVGrid(columns: twoColumns, spacing: 9) {
                        adChoice("Story", icon: "circle.dashed.inset.filled", value: "profile_story", selection: $placement)
                        adChoice("Découverte", icon: "sparkles", value: "inbox", selection: $placement)
                        adChoice("Marché", icon: "storefront.fill", value: "market", selection: $placement)
                        adChoice("Direct", icon: "video.fill", value: "live", selection: $placement)
                    }
                    Text("ACTION APRÈS LE CLIC").font(.system(size: 9, weight: .bold)).foregroundStyle(WapiColor.secondaryText).padding(.top, 3)
                    LazyVGrid(columns: twoColumns, spacing: 9) {
                        adChoice("Écrire", icon: "message.fill", value: "message", selection: $destination)
                        adChoice("Voir la page", icon: "person.crop.rectangle", value: "page", selection: $destination)
                        adChoice("Appeler", icon: "phone.fill", value: "call", selection: $destination)
                        adChoice("Ouvrir le lien", icon: "link", value: "website", selection: $destination)
                    }
                }

                adSection(number: "4", title: "Audience et diffusions", detail: "Commandez un volume réel d’affichages") {
                    WapiAdTextFieldIOS(title: "Audience", text: $audience)
                    WapiAdTextFieldIOS(title: "Ville ou région ciblée", text: $city)
                    WapiAdTextFieldIOS(title: "Code pays (ex. CG, FR)", text: $countryCode)
                        .textInputAutocapitalization(.characters).autocorrectionDisabled()
                    Text("PACKS DE DIFFUSION").font(.system(size: 9, weight: .bold)).foregroundStyle(WapiColor.secondaryText)
                    LazyVGrid(columns: twoColumns, spacing: 9) {
                        adChoice("1K", icon: "eye.fill", value: "1000", selection: $targetImpressions)
                        adChoice("5K", icon: "eye.fill", value: "5000", selection: $targetImpressions)
                        adChoice("10K", icon: "eye.fill", value: "10000", selection: $targetImpressions)
                        adChoice("50K", icon: "eye.fill", value: "50000", selection: $targetImpressions)
                    }
                    HStack(spacing: 9) {
                        WapiAdTextFieldIOS(title: "Diffusions", text: $targetImpressions, keyboard: .numberPad)
                        WapiAdTextFieldIOS(title: "Durée en jours", text: $days, keyboard: .numberPad)
                    }
                    Text("2 500 FCFA par tranche de 1 000 diffusions. La campagne s’arrête automatiquement au quota.")
                        .font(.caption).foregroundStyle(WapiColor.secondaryText)
                    Text("Cadence prévue : \(dailyDeliveryCap.formatted()) diffusions par jour.")
                        .font(.caption.weight(.semibold)).foregroundStyle(Color.whappyBlue)
                }

                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "lock.shield.fill").foregroundStyle(Color.orange)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Paiement sous votre contrôle").font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk)
                        Text("La campagne reste en attente jusqu’à votre validation sécurisée. Aucun débit automatique.")
                            .font(.caption).foregroundStyle(WapiColor.secondaryText)
                    }
                }
                .padding(14).background(Color.orange.opacity(0.09)).clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                if let result {
                    Text(result).font(.footnote.weight(.semibold)).foregroundStyle(result.hasPrefix("Campagne") ? .green : .red)
                        .padding(12).frame(maxWidth: .infinity, alignment: .leading).background((result.hasPrefix("Campagne") ? Color.green : Color.red).opacity(0.08)).clipShape(RoundedRectangle(cornerRadius: 14))
                }
            }
            .padding(.horizontal, WapiSpacing.screen).padding(.top, 12).padding(.bottom, 110)
        }
        .background(Color.whappyBackground.ignoresSafeArea())
        .navigationTitle("WAPI Ads").navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            HStack(spacing: 14) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("DIFFUSIONS · BUDGET").font(.system(size: 8, weight: .bold)).foregroundStyle(WapiColor.secondaryText)
                    Text("\(totalBudget.formatted()) FCFA").font(.headline.weight(.bold)).foregroundStyle(Color.whappyInk)
                }
                Spacer()
                Button(saving ? "Préparation…" : "Continuer") { createCampaign() }
                    .buttonStyle(.borderedProminent).controlSize(.large)
                    .disabled(!isValid || saving || store.business == nil)
            }
            .padding(.horizontal, WapiSpacing.screen).padding(.vertical, 11).background(.ultraThinMaterial)
        }
    }

    private var totalBudget: Int {
        let volume = max(0, Int(targetImpressions) ?? 0)
        return ((volume + 999) / 1_000) * 2_500
    }

    private var dailyDeliveryCap: Int {
        let duration = max(1, Int(days) ?? 1)
        let volume = max(0, Int(targetImpressions) ?? 0)
        return (volume + duration - 1) / duration
    }

    @ViewBuilder
    private func adSection<Content: View>(number: String, title: String, detail: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Text(number).font(.caption.weight(.bold)).foregroundStyle(.white).frame(width: 32, height: 32).background(Color.whappyBlue).clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).font(.headline.weight(.bold)).foregroundStyle(Color.whappyInk)
                    Text(detail).font(.caption2).foregroundStyle(WapiColor.secondaryText)
                }
            }
            content()
        }
    }

    private func adChoice(_ label: String, icon: String, value: String, selection: Binding<String>) -> some View {
        Button { selection.wrappedValue = value } label: {
            HStack(spacing: 8) {
                Image(systemName: icon).font(.caption.weight(.bold))
                Text(label).font(.caption.weight(.semibold)).lineLimit(1)
                Spacer(minLength: 0)
                if selection.wrappedValue == value { Image(systemName: "checkmark.circle.fill").font(.caption) }
            }
            .foregroundStyle(selection.wrappedValue == value ? Color.whappyBlue : Color.whappyInk)
            .padding(.horizontal, 11).frame(height: 46)
            .background(selection.wrappedValue == value ? WapiColor.blueMist : Color.white)
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(selection.wrappedValue == value ? Color.whappyBlue.opacity(0.5) : WapiColor.line, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }.buttonStyle(WapiPressableButtonStyle())
    }

    private var isValid: Bool {
        title.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2 &&
        creative.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2 &&
        (1_000...20_000_000).contains(Int(targetImpressions) ?? 0) && (1...90).contains(Int(days) ?? 0)
    }

    private func createCampaign() {
        guard store.firebaseUserID != nil, let business = store.business, !business.remoteID.isEmpty,
              let volume = Int(targetImpressions), let duration = Int(days), isValid else {
            result = "Complétez d’abord le profil Business et les champs de la campagne."
            return
        }
        saving = true; result = nil
        Functions.functions(region: "europe-west1").httpsCallable("createAdCampaign").call([
            "pageId": business.remoteID,
            "objective": objective, "placement": placement, "destination": destination,
            "title": title.trimmingCharacters(in: .whitespacesAndNewlines),
            "creative": creative.trimmingCharacters(in: .whitespacesAndNewlines),
            "cta": destination == "message" ? "Envoyer un message" : "Découvrir",
            "audience": audience.trimmingCharacters(in: .whitespacesAndNewlines), "city": city,
            "countryCode": String(countryCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased().prefix(2)),
            "targetImpressions": volume, "days": duration,
        ]) { _, error in
            saving = false
            result = error == nil ? "Campagne préparée. Validation du paiement requise." : wapiUserFacingError(error!, action: "La campagne Business")
        }
    }
}

private struct WapiAdTextFieldIOS: View {
    let title: String
    @Binding var text: String
    var multiline = false
    var keyboard: UIKeyboardType = .default

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title.uppercased()).font(.system(size: 9, weight: .bold)).foregroundStyle(WapiColor.secondaryText)
            if multiline {
                TextField("Rédigez un message clair", text: $text, axis: .vertical).lineLimit(4...7)
                    .padding(13).background(Color.white).clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 15, style: .continuous).stroke(WapiColor.line))
            } else {
                TextField(title, text: $text).keyboardType(keyboard)
                    .padding(.horizontal, 13).frame(height: 50).background(Color.white).clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 15, style: .continuous).stroke(WapiColor.line))
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private struct WapiAdCreativePreviewIOS: View {
    let pageName: String
    let title: String
    let message: String
    let city: String
    let action: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("APERÇU UTILISATEUR").font(.system(size: 9, weight: .bold)).tracking(0.7).foregroundStyle(WapiColor.secondaryText)
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 9) {
                    InitialsAvatar(text: pageName, size: 38).clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(pageName).font(.subheadline.weight(.bold)).foregroundStyle(Color.whappyInk)
                        Text("SPONSORISÉ" + (city.isEmpty ? "" : " · \(city.uppercased())"))
                            .font(.system(size: 8, weight: .bold)).foregroundStyle(WapiColor.secondaryText)
                    }
                }
                Text(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Votre titre apparaîtra ici" : title)
                    .font(.headline.weight(.bold)).foregroundStyle(Color.whappyInk)
                Text(message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Prévisualisez ici le message que les utilisateurs WAPI verront." : message)
                    .font(.subheadline).foregroundStyle(WapiColor.secondaryText).lineLimit(4)
                HStack { Spacer(); Text(action.uppercased() + "  ›").font(.caption.weight(.bold)).foregroundStyle(Color.whappyBlue) }
            }
            .padding(14).wapiPanel(radius: 18)
        }
    }
}

private struct BusinessEditorView: View {
    @EnvironmentObject private var store: WhappyStore
    @State private var name = ""
    @State private var category = ""
    @State private var bio = ""
    @State private var city = ""
    @State private var phone = ""
    @State private var website = ""
    @State private var saved = false
    @State private var saving = false
    @State private var loaded = false
    @State private var saveError: String?
    @State private var logoItem: PhotosPickerItem?
    @State private var selectedLogo: UIImage?

    private var valid: Bool {
        (2...80).contains(name.trimmingCharacters(in: .whitespacesAndNewlines).count) &&
        !category.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        Form {
            Section {
                HStack(spacing: 16) {
                    businessLogo.frame(width: 72, height: 72)
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    VStack(alignment: .leading, spacing: 7) {
                        Text(store.business == nil ? "Votre entreprise" : name).font(.headline)
                        if store.business != nil {
                            PhotosPicker(selection: $logoItem, matching: .images) {
                                Label("Changer le logo", systemImage: "photo")
                            }.disabled(store.firebaseBusy || saving)
                        } else {
                            Text("Ajoutez votre logo après la création du profil.").font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                }.padding(.vertical, 8)
            } footer: {
                Text("Une identité professionnelle distincte. Votre compte personnel reste inchangé.")
            }
            Section("Identité publique") {
                WapiEditorTextFieldIOS(title: "Nom de l’entreprise", text: $name)
                WapiEditorTextFieldIOS(title: "Activité", text: $category)
                WapiEditorTextFieldIOS(title: "Présentation", text: $bio, limit: 400, multiline: true)
            }.disabled(saving || store.firebaseBusy)
            Section {
                WapiEditorTextFieldIOS(title: "Ville ou région", text: $city)
                WapiEditorTextFieldIOS(title: "Téléphone professionnel", text: $phone, limit: 30, keyboard: .phonePad)
                WapiEditorTextFieldIOS(title: "Site ou catalogue · facultatif", text: $website, limit: 180, keyboard: .URL)
            } header: { Text("Coordonnées publiques") }
              footer: { Text("Ces informations permettent à vos clients de vous retrouver.") }
            .disabled(saving || store.firebaseBusy)
            if store.business != nil {
                Section("Commerce social") {
                    NavigationLink { BusinessSaleRoomManagerIOS() } label: {
                        Label("Salons de vente", systemImage: "person.3.sequence.fill")
                    }
                }
            }
            if let saveError {
                Section { Label(saveError, systemImage: "exclamationmark.circle").font(.footnote).foregroundStyle(.red) }
            } else if saved {
                Section { Label("Profil enregistré", systemImage: "checkmark.circle.fill").foregroundStyle(Color.whappyBlue) }
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle(store.business == nil ? "Créer un profil Business" : "Profil Business")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            WapiEditorActionIOS(title: store.business == nil ? "Créer le profil" : "Enregistrer",
                busy: saving || store.firebaseBusy, enabled: valid) {
                saving = true; saved = false; saveError = nil
                store.saveBusiness(name: name, category: category, bio: bio, city: city, phone: phone, website: website) { success in
                    saving = false
                    saved = success
                    if !success { saveError = store.firebaseMessage ?? "Le profil n’a pas pu être enregistré. Votre saisie est conservée." }
                }
            }
        }
        .onChange(of: [name, category, bio, city, phone, website]) { _, _ in saved = false; saveError = nil }
        .onAppear {
            guard !loaded else { return }
            loaded = true
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
            ScrollView(.horizontal, showsIndicators: false) { HStack(spacing: 10) { ForEach(model.rooms.prefix(12)) { room in Button { selected = room } label: { VStack(alignment: .leading, spacing: 6) { HStack { Text(room.status == "live" ? "● EN COURS" : "BIENTÔT").font(.caption2.bold()).foregroundStyle(room.status == "live" ? .red : .yellow); Spacer(); Text(room.visibility == "public" ? "MONDIAL" : "CONTACTS").font(.caption2.bold()).foregroundStyle(.white.opacity(0.65)) }; Text(room.title).font(.headline).foregroundStyle(.white).lineLimit(2); Text("\(room.pageName) · \(room.products.count) produit(s)").font(.caption).foregroundStyle(.white.opacity(0.65)); Text("\(room.viewers) visiteurs").font(.caption2).foregroundStyle(.cyan) }.padding().frame(width: 238, alignment: .leading).background(Color(red: 0.03, green: 0.11, blue: 0.25)).clipShape(RoundedRectangle(cornerRadius: 19)) }.buttonStyle(WapiPressableButtonStyle()) } } }
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
                    Button { showAccountSwitcher = true } label: { profileHeaderAvatar }.buttonStyle(WapiPressableButtonStyle())
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
        }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(WapiColor.canvas)
            .navigationTitle("Profil")
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
                    }.buttonStyle(WapiPressableButtonStyle())
                    if let business = store.business {
                        Button {
                            store.switchAccount(business: true)
                            dismiss()
                        } label: {
                            AccountSwitcherIOSRow(title: business.name, subtitle: "Business · \(business.category)", icon: "briefcase.fill", photoURL: business.logoURL, active: store.activeBusinessMode)
                        }.buttonStyle(WapiPressableButtonStyle())
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

private struct FounderAdReviewIOS: Identifiable {
    let id: String
    let pageName: String
    let title: String
    let creative: String
    let city: String
    let countryCode: String
    let totalBudget: Int
    let targetImpressions: Int
    let days: Int
}

private struct FounderDashboardIOSView: View {
    @State private var loading = true
    @State private var error: String?
    @State private var values: [String: String] = [:]
    @State private var pendingReviews: [FounderAdReviewIOS] = []
    @State private var selectedReview: FounderAdReviewIOS?
    @State private var selectedReviewAction = "approve"

    var body: some View {
        List {
            Section {
                if loading { ProgressView("Chargement des données WAPI…") }
                else if let error {
                    Label(error, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                    Button("Réessayer la connexion") { Task { await load() } }
                }
                else {
                    FounderIOSMetric(title: "Utilisateurs WAPI", value: values["users"] ?? "0", detail: "Comptes WAPI enregistrés")
                    FounderIOSMetric(title: "CA encaissé", value: values["paidRevenue"] ?? "0 XAF", detail: "Notifications de paiement marquées payées")
                    FounderIOSMetric(title: "Installations actives", value: values["activeInstallations"] ?? "0", detail: "Appareils WAPI avec jeton actif")
                    FounderIOSMetric(title: "Téléchargements stores", value: values["officialDownloads"] ?? "Non relié", detail: "Uniquement les données officielles connectées")
                    FounderIOSMetric(title: "Pages Business", value: values["businessPages"] ?? "0", detail: "Pages réellement créées")
                    FounderIOSMetric(title: "Commandes payées", value: values["paidOrders"] ?? "0", detail: "Paiements confirmés par le serveur")
                    FounderIOSMetric(title: "Stories publiées", value: values["stories"] ?? "0", detail: "Stories présentes dans le cloud")
                    FounderIOSMetric(title: "Lives actifs", value: values["activeLives"] ?? "0", detail: "Sessions actuellement en direct")
                }
            } header: { Text("Données opérationnelles") }
            if !loading && error == nil {
                Section("Business · facturation") {
                    FounderIOSMetric(title: "Factures émises", value: values["issuedInvoices"] ?? "0", detail: "Factures créées par les comptes Business")
                    FounderIOSMetric(title: "Créances ouvertes", value: values["outstandingReceivables"] ?? "0 XAF", detail: "Factures émises, distinctes du CA encaissé")
                    Text("Une facture n’est jamais présentée comme un encaissement avant confirmation du paiement.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("WAPI Ads") {
                    FounderIOSMetric(title: "Campagnes", value: values["adCampaigns"] ?? "0", detail: "Campagnes enregistrées dans WAPI Cloud")
                    FounderIOSMetric(title: "En diffusion", value: values["activeAdCampaigns"] ?? "0", detail: "Campagnes actives")
                    FounderIOSMetric(title: "En attente", value: values["pendingAdCampaigns"] ?? "0", detail: "Validation ou paiement requis")
                    FounderIOSMetric(title: "Impressions", value: values["adImpressions"] ?? "0", detail: "Affichages réellement comptabilisés")
                    FounderIOSMetric(title: "Clics", value: values["adClicks"] ?? "0", detail: "Actions réellement comptabilisées")
                }
                if !pendingReviews.isEmpty {
                    Section("Validations WAPI Ads") {
                        ForEach(pendingReviews) { campaign in
                            VStack(alignment: .leading, spacing: 8) {
                                Text(campaign.pageName).font(.headline)
                                Text(campaign.title).font(.subheadline.weight(.semibold))
                                if !campaign.creative.isEmpty { Text(campaign.creative).font(.caption).foregroundStyle(.secondary).lineLimit(3) }
                                Text("\(campaign.targetImpressions > 0 ? "\(campaign.targetImpressions.formatted()) diffusions · " : "")\(campaign.totalBudget.formatted()) FCFA · \(campaign.days) jour(s) · \(campaign.city.isEmpty ? campaign.countryCode : campaign.city)")
                                    .font(.caption2).foregroundStyle(.secondary)
                                HStack {
                                    Button("Refuser", role: .destructive) { selectedReviewAction = "reject"; selectedReview = campaign }
                                    Spacer()
                                    Button("Vérifier et activer") { selectedReviewAction = "approve"; selectedReview = campaign }.buttonStyle(.borderedProminent)
                                }
                            }.padding(.vertical, 5)
                        }
                    }
                }
            }
            Section("Stores") {
                Label(values["playStore"] == "true" ? "Google Play connecté" : "Google Play non connecté", systemImage: "play.rectangle.fill")
                Label(values["appStore"] == "true" ? "App Store connecté" : "App Store non connecté", systemImage: "apple.logo")
                Text("Les téléchargements de store restent à zéro tant que les consoles officielles ne sont pas reliées. Aucun chiffre local n’est présenté comme réel.").font(.footnote).foregroundStyle(.secondary)
            }
            if let refreshed = values["generatedAt"], !refreshed.isEmpty {
                Section { Text("Dernière synchronisation : \(refreshed) · source WAPI Cloud").font(.footnote).foregroundStyle(.secondary) }
            }
        }
        .navigationTitle("Tableau Fondateur")
        .task { await load() }
        .refreshable { await load() }
        .sheet(item: $selectedReview) { review in
            FounderAdReviewSheetIOS(review: review, action: selectedReviewAction) {
                Task { await load() }
            }
        }
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
            let ads = result["ads"] as? [String: Any] ?? [:]
            let billing = result["billing"] as? [String: Any] ?? [:]
            pendingReviews = (result["pendingAdReviews"] as? [[String: Any]] ?? []).compactMap { item in
                guard let id = item["id"] as? String, !id.isEmpty else { return nil }
                return FounderAdReviewIOS(
                    id: id,
                    pageName: item["pageName"] as? String ?? "Business WAPI",
                    title: item["title"] as? String ?? "Campagne WAPI",
                    creative: item["creative"] as? String ?? "",
                    city: item["city"] as? String ?? "",
                    countryCode: item["countryCode"] as? String ?? "",
                    totalBudget: (item["totalBudget"] as? NSNumber)?.intValue ?? 0,
                    targetImpressions: (item["targetImpressions"] as? NSNumber)?.intValue ?? 0,
                    days: (item["days"] as? NSNumber)?.intValue ?? 1
                )
            }
            let generatedAt = (result["generatedAt"] as? NSNumber).map { Date(timeIntervalSince1970: $0.doubleValue / 1_000) }
            values = [
                "users": Self.count(result["users"]),
                "paidRevenue": "\(Self.count(result["paidRevenue"])) XAF",
                "activeInstallations": Self.count(result["activeInstallations"]),
                "officialDownloads": stores["totalDownloads"] is NSNumber ? Self.count(stores["totalDownloads"]) : "Non relié",
                "businessPages": Self.count(result["businessPages"]),
                "paidOrders": Self.count(result["paidOrders"]),
                "issuedInvoices": Self.count(billing["issuedInvoices"]),
                "outstandingReceivables": "\(Self.count(billing["outstandingReceivables"])) XAF",
                "stories": Self.count(result["stories"]),
                "activeLives": Self.count(result["activeLives"]),
                "adCampaigns": Self.count(ads["campaigns"]),
                "activeAdCampaigns": Self.count(ads["activeCampaigns"]),
                "pendingAdCampaigns": Self.count(ads["pendingCampaigns"]),
                "adImpressions": Self.count(ads["impressions"]),
                "adClicks": Self.count(ads["clicks"]),
                "playStore": String(describing: stores["playStore"] as? Bool ?? false),
                "appStore": String(describing: stores["appStore"] as? Bool ?? false),
                "generatedAt": generatedAt?.formatted(date: .abbreviated, time: .shortened) ?? "",
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
            Text(value).font(.system(size: 27, weight: .bold, design: .rounded)).foregroundStyle(Color.whappyBlue)
            Text(detail).font(.caption).foregroundStyle(.secondary)
        }.padding(.vertical, 4)
    }
}

private struct FounderAdReviewSheetIOS: View {
    @Environment(\.dismiss) private var dismiss
    let review: FounderAdReviewIOS
    let action: String
    let onComplete: () -> Void
    @State private var paymentReference = ""
    @State private var reviewNote = ""
    @State private var busy = false
    @State private var error: String?

    private var approving: Bool { action == "approve" }

    var body: some View {
        NavigationStack {
            Form {
                Section("Campagne") {
                    LabeledContent("Business", value: review.pageName)
                    LabeledContent("Campagne", value: review.title)
                    LabeledContent("Budget", value: "\(review.totalBudget.formatted()) FCFA")
                    if review.targetImpressions > 0 {
                        LabeledContent("Diffusions", value: review.targetImpressions.formatted())
                    }
                    LabeledContent("Durée", value: "\(review.days) jour(s)")
                }
                Section(approving ? "Paiement contrôlé" : "Décision") {
                    if approving {
                        TextField("Référence de paiement vérifiée", text: $paymentReference).textInputAutocapitalization(.characters)
                        Text("La diffusion démarre uniquement après validation de cette référence.").font(.footnote).foregroundStyle(.secondary)
                    } else {
                        TextField("Motif du refus", text: $reviewNote, axis: .vertical).lineLimit(2...5)
                    }
                }
                if let error { Section { Label(error, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.red) } }
            }
            .navigationTitle(approving ? "Activer la campagne" : "Refuser la campagne")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() }.disabled(busy) }
                ToolbarItem(placement: .confirmationAction) {
                    Button(approving ? "Activer" : "Refuser", role: approving ? nil : .destructive) { submit() }
                        .disabled(busy || (approving && paymentReference.trimmingCharacters(in: .whitespacesAndNewlines).count < 6))
                }
            }
        }
    }

    private func submit() {
        busy = true; error = nil
        Functions.functions(region: "europe-west1").httpsCallable("reviewAdCampaign").call([
            "campaignId": review.id,
            "action": action,
            "paymentReference": paymentReference.trimmingCharacters(in: .whitespacesAndNewlines),
            "reviewNote": reviewNote.trimmingCharacters(in: .whitespacesAndNewlines),
        ]) { _, failure in
            Task { @MainActor in
                busy = false
                if let failure { error = wapiUserFacingError(failure, action: "La validation WAPI Ads") }
                else { dismiss(); onComplete() }
            }
        }
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
                            Text("W").font(.system(size: 24, weight: .bold)).foregroundStyle(.white)
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
                    Text("PARAMÈTRES").font(.caption.weight(.semibold)).foregroundStyle(Color.whappyBlue)
                    Text("Réglages clairs, contrôle réel.").font(.title2.weight(.bold)).foregroundStyle(Color.whappyInk)
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
    @AppStorage("wapi.typing.sounds.enabled") private var typingSoundsEnabled = false
    @AppStorage("wapi.call.end.sounds.enabled") private var callEndSoundsEnabled = true

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
                Toggle("Son de fin d’appel", isOn: $callEndSoundsEnabled).disabled(!soundsEnabled)
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

/// Persistent, bounded media cache shared by every native iOS surface.
/// Images remain immediately available after navigation or an application restart.
private actor WapiImageDiskCache {
    static let shared = WapiImageDiskCache()
    private let directory: URL
    private let maximumBytes: Int64 = 96 * 1024 * 1024

    private init() {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        directory = base.appendingPathComponent("wapi-images-v1", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    func data(for remoteURL: URL) -> Data? {
        let file = cacheURL(for: remoteURL)
        guard let attributes = try? FileManager.default.attributesOfItem(atPath: file.path),
              let modified = attributes[.modificationDate] as? Date,
              Date().timeIntervalSince(modified) < 30 * 24 * 60 * 60,
              let data = try? Data(contentsOf: file, options: [.mappedIfSafe]),
              !data.isEmpty else { return nil }
        return data
    }

    func store(_ data: Data, for remoteURL: URL) {
        guard !data.isEmpty else { return }
        try? data.write(to: cacheURL(for: remoteURL), options: .atomic)
        pruneIfNeeded()
    }

    private func cacheURL(for remoteURL: URL) -> URL {
        let digest = SHA256.hash(data: Data(remoteURL.absoluteString.utf8))
            .map { String(format: "%02x", $0) }
            .joined()
        return directory.appendingPathComponent(digest).appendingPathExtension("img")
    }

    private func pruneIfNeeded() {
        let keys: Set<URLResourceKey> = [.fileSizeKey, .contentModificationDateKey, .isRegularFileKey]
        guard let files = try? FileManager.default.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: Array(keys),
            options: [.skipsHiddenFiles]
        ) else { return }
        let entries = files.compactMap { url -> (URL, Int64, Date)? in
            guard let values = try? url.resourceValues(forKeys: keys), values.isRegularFile == true else { return nil }
            return (url, Int64(values.fileSize ?? 0), values.contentModificationDate ?? .distantPast)
        }
        var total = entries.reduce(Int64(0)) { $0 + $1.1 }
        guard total > maximumBytes else { return }
        for entry in entries.sorted(by: { $0.2 < $1.2 }) where total > maximumBytes * 3 / 4 {
            if (try? FileManager.default.removeItem(at: entry.0)) != nil { total -= entry.1 }
        }
    }
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
            if let data = await WapiImageDiskCache.shared.data(for: url), let decoded = UIImage(data: data) {
                WapiImageMemoryCache.shared.insert(decoded, for: url, byteCount: data.count)
                image = decoded
                return
            }
            var request = URLRequest(url: url, cachePolicy: .returnCacheDataElseLoad, timeoutInterval: 15)
            request.setValue("image/*", forHTTPHeaderField: "Accept")
            let dataSaver = UserDefaults.standard.bool(forKey: "dataSaverEnabled")
            let maximumBytes = (dataSaver ? 8 : 20) * 1024 * 1024
            guard let (data, response) = try? await URLSession.shared.data(for: request),
                  data.count <= maximumBytes,
                  ((response as? HTTPURLResponse)?.statusCode ?? 200) < 400,
                  let decoded = UIImage(data: data) else { return }
            await WapiImageDiskCache.shared.store(data, for: url)
            WapiImageMemoryCache.shared.insert(decoded, for: url, byteCount: data.count)
            image = decoded
        }
    }
}

struct InitialsAvatar: View {
    let text: String; var size: CGFloat = 48
    var body: some View { ZStack { Circle().fill(Color.whappyBlue.opacity(0.14)); Text(text).font(.system(size: size * 0.32, weight: .bold)).foregroundStyle(Color.whappyBlue) }.frame(width: size, height: size) }
}
