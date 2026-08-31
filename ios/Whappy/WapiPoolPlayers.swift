import SwiftUI
import PhotosUI
import FirebaseAuth
import FirebaseFunctions

private let poolIcons = [("cue", "🎱"), ("crown", "👑"), ("fox", "🦊"), ("lion", "🦁"), ("robot", "🤖")]

struct WapiPoolPlayer: Codable {
    var displayName = "Vous"
    var photoUrl = ""
    var avatarIcon = "cue"
    var avatarMode = "account"
    var points = 0
    var victories = 0
    var defeats = 0
    var bestRun = 0
    init(name: String = "Vous", icon: String = "cue") { displayName = name; avatarIcon = icon }
    init(_ raw: [String: Any]) {
        displayName = raw["displayName"] as? String ?? "Vous"
        photoUrl = raw["photoUrl"] as? String ?? ""
        avatarIcon = raw["avatarIcon"] as? String ?? "cue"
        avatarMode = raw["avatarMode"] as? String ?? "account"
        points = raw["points"] as? Int ?? 0; victories = raw["victories"] as? Int ?? 0
        defeats = raw["defeats"] as? Int ?? 0; bestRun = raw["bestRun"] as? Int ?? 0
    }
}

@MainActor final class WapiPoolProfileStore: ObservableObject {
    @Published var player = WapiPoolPlayer()
    @Published var verified = false
    @Published var failure: String?
    @Published var localBest = 0
    @Published var localVictories = 0
    @Published var localDefeats = 0
    @Published var localCups = 0
    private let uid: String
    private var cacheKey: String { "wapi.pool.profile.\(uid)" }
    init() {
        let user = Auth.auth().currentUser
        uid = user?.uid ?? ""
        player.displayName = user?.displayName ?? "Vous"
        player.photoUrl = user?.photoURL?.absoluteString ?? ""
        if let data = UserDefaults.standard.data(forKey: cacheKey), let cached = try? JSONDecoder().decode(WapiPoolPlayer.self, from: data) { player = cached }
        localBest = UserDefaults.standard.integer(forKey: cacheKey + ".localBest")
        localVictories = UserDefaults.standard.integer(forKey: cacheKey + ".localVictories")
        localDefeats = UserDefaults.standard.integer(forKey: cacheKey + ".localDefeats")
        localCups = UserDefaults.standard.integer(forKey: cacheKey + ".localCups")
    }
    var signedIn: Bool { !uid.isEmpty }
    func load() async {
        guard signedIn else { return }
        do { try await accept(Functions.functions(region: "europe-west1").httpsCallable("getGameProfile").call(["gameId": "billard"])) }
        catch { failure = "Profil en ligne indisponible. Votre record local reste conservé." }
    }
    private func accept(_ response: HTTPSCallableResult) throws {
        guard let raw = response.data as? [String: Any], let profile = raw["profile"] as? [String: Any] else {
            throw NSError(domain: "WapiPool", code: 1, userInfo: [NSLocalizedDescriptionKey: "Profil non enregistré."])
        }
        player = WapiPoolPlayer(profile); verified = true; failure = nil
        if let data = try? JSONEncoder().encode(player) { UserDefaults.standard.set(data, forKey: cacheKey) }
    }
    func save(name: String, mode: String, icon: String, image: Data?) async throws {
        var payload: [String: Any] = ["gameId": "billard", "displayName": name.trimmingCharacters(in: .whitespacesAndNewlines), "avatarMode": mode, "avatarIcon": icon]
        if mode == "photo", let image { payload["photoBase64"] = image.base64EncodedString() }
        try await accept(Functions.functions(region: "europe-west1").httpsCallable("saveGameProfile").call(payload))
    }
    func recordPractice(_ points: Int) {
        guard points > localBest else { return }
        localBest = points; UserDefaults.standard.set(points, forKey: cacheKey + ".localBest")
    }
    func recordPracticeResult(_ points: Int, won: Bool) {
        recordPractice(points)
        if won { localVictories += 1 } else { localDefeats += 1 }
        UserDefaults.standard.set(localVictories, forKey: cacheKey + ".localVictories")
        UserDefaults.standard.set(localDefeats, forKey: cacheKey + ".localDefeats")
    }
    func awardLocalCup() {
        localCups += 1
        UserDefaults.standard.set(localCups, forKey: cacheKey + ".localCups")
    }
}

private struct PoolAvatarIOS: View {
    let player: WapiPoolPlayer
    var size: CGFloat = 40
    var body: some View {
        ZStack {
            Color(red: 0.07, green: 0.20, blue: 0.34)
            Text(poolIcons.first(where: { $0.0 == player.avatarIcon })?.1 ?? "🎱").font(.system(size: size * 0.52))
            if player.avatarMode != "icon", let url = URL(string: player.photoUrl), !player.photoUrl.isEmpty {
                AsyncImage(url: url) { image in image.resizable().scaledToFill() } placeholder: { Color.clear }
            }
        }.frame(width: size, height: size).clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private struct PoolPlayerScoreIOS: View {
    let player: WapiPoolPlayer
    let group: Int
    let remaining: Set<Int>
    let active: Bool
    let seconds: Int
    private var ids: [Int] { remaining.filter { WapiPoolRules.group(of: $0) == group && group != 0 }.sorted() }
    var body: some View {
        HStack(spacing: 8) {
            PoolAvatarIOS(player: player)
            VStack(alignment: .leading, spacing: 3) {
                Text(player.displayName).font(.subheadline.weight(.semibold)).lineLimit(1)
                Text(group == 0 ? "Groupe à attribuer" : "\(ids.count) restantes · \(group == 1 ? "pleines" : "rayées")").font(.system(size: 10)).lineLimit(1).minimumScaleFactor(0.75).foregroundStyle(.white.opacity(0.75))
                if active {
                    HStack(spacing: 5) {
                        ProgressView(value: Double(min(max(seconds, 0), 30)), total: 30).tint(Color(red: 0.28, green: 0.89, blue: 0.57)).frame(height: 4)
                        Text("\(max(seconds, 0)) s").font(.system(size: 9, weight: .bold)).foregroundStyle(Color(red: 0.56, green: 0.95, blue: 0.72))
                    }
                }
                if group != 0 {
                    HStack(spacing: 3) {
                        ForEach(ids.isEmpty ? [8] : ids, id: \.self) { id in
                            PoolScoreBallIOS(id: id)
                        }
                    }
                }
            }.frame(maxWidth: .infinity, alignment: .leading)
        }.padding(9).foregroundStyle(.white).background(Color(red: 0.025, green: 0.10, blue: 0.18).opacity(0.96))
            .clipShape(RoundedRectangle(cornerRadius: 14)).overlay(RoundedRectangle(cornerRadius: 14).stroke(active ? Color.cyan : .white.opacity(0.2), lineWidth: active ? 2 : 1))
    }
}

/// The score uses miniature textured pool balls — including the coloured
/// stripe and number plate — so it matches the remaining balls on the table.
private struct PoolScoreBallIOS: View {
    let id: Int
    private var color: Color {
        switch (id - 1) % 8 {
        case 0: .yellow; case 1: Color(red: 0.12, green: 0.36, blue: 0.82)
        case 2: .red; case 3: .purple; case 4: .orange; case 5: .green
        case 6: Color(red: 0.48, green: 0.08, blue: 0.10); default: .black
        }
    }
    var body: some View {
        ZStack {
            Circle().fill(.black.opacity(0.5)).offset(y: 1)
            Circle().fill(color)
            if id >= 9 { Rectangle().fill(.white).frame(height: 6) }
            Circle().fill(.white).frame(width: 9, height: 9)
            Text("\(id)").font(.system(size: 6, weight: .black)).foregroundStyle(Color(red: 0.04, green: 0.12, blue: 0.20))
            Circle().stroke(.white.opacity(0.45), lineWidth: 0.6)
        }.frame(width: 17, height: 17).clipShape(Circle())
    }
}

private struct PoolProfileEditorIOS: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var store: WapiPoolProfileStore
    @State private var name: String
    @State private var icon: String
    @State private var mode: String
    @State private var image: Data?
    @State private var selected: PhotosPickerItem?
    @State private var busy = false
    @State private var error: String?
    init(store: WapiPoolProfileStore) {
        self.store = store; _name = State(initialValue: store.player.displayName)
        _icon = State(initialValue: store.player.avatarIcon); _mode = State(initialValue: store.player.avatarMode)
    }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    HStack(spacing: 18) {
                        if let image, let uiImage = UIImage(data: image), mode == "photo" { Image(uiImage: uiImage).resizable().scaledToFill().frame(width: 72, height: 72).clipShape(RoundedRectangle(cornerRadius: 18)) }
                        else { PoolAvatarIOS(player: mode == "icon" ? WapiPoolPlayer(name: name, icon: icon) : store.player, size: 72) }
                        PhotosPicker(selection: $selected, matching: .images) { Label("Choisir une photo", systemImage: "photo") }.disabled(busy)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Pseudo joueur").font(.headline)
                        TextField("Votre pseudo", text: $name).textFieldStyle(.roundedBorder).disabled(busy)
                    }
                    HStack {
                        ForEach(poolIcons, id: \.0) { id, emoji in
                            Button { icon = id; mode = "icon" } label: {
                                Text(emoji).font(.title2).frame(maxWidth: .infinity).padding(.vertical, 10)
                                    .background(mode == "icon" && icon == id ? Color.whappyBlue.opacity(0.15) : Color.gray.opacity(0.07)).clipShape(RoundedRectangle(cornerRadius: 12))
                            }.disabled(busy)
                        }
                    }
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Résultats en ligne").font(.title3.bold())
                        Text("\(store.player.points) points · \(store.player.victories) victoires · \(store.player.defeats) défaites")
                        Text("Meilleure série : \(store.player.bestRun) billes")
                        Text(store.verified ? "Validés par le serveur WAPI." : "Dernier résultat conservé. Les points locaux ne comptent pas pour le classement.").font(.caption).foregroundStyle(.secondary)
                    }
                    Divider()
                    Text("Record sur cet appareil : \(store.localBest) points").font(.headline)
                    Text("Les entraînements ne rapportent pas d’argent. Aucun paiement de dotation n’est activé.").font(.footnote).foregroundStyle(.secondary)
                    if let issue = error ?? store.failure { Text(issue).font(.footnote).foregroundStyle(.red) }
                }.padding(24)
            }.background(Color(.systemBackground)).navigationTitle("Profil · Billard").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fermer") { dismiss() }.disabled(busy) } }
                .safeAreaInset(edge: .bottom) {
                    Button {
                        busy = true; error = nil
                        Task {
                            do { try await store.save(name: name, mode: mode, icon: icon, image: image); dismiss() }
                            catch { self.error = "Profil non enregistré. Réessayez ; votre saisie est conservée." }
                            busy = false
                        }
                    } label: { HStack { if busy { ProgressView().tint(.white) }; Text("Enregistrer le profil").bold() }.frame(maxWidth: .infinity).padding(10) }
                        .buttonStyle(.borderedProminent).disabled(busy || !store.signedIn || !(2...40).contains(name.trimmingCharacters(in: .whitespacesAndNewlines).count)).padding().background(.regularMaterial)
                }
                .onChange(of: selected) { _, value in
                    Task {
                        guard let raw = try? await value?.loadTransferable(type: Data.self), let photo = UIImage(data: raw) else { return }
                        let side: CGFloat = 512
                        let scale = max(side / photo.size.width, side / photo.size.height)
                        let size = CGSize(width: photo.size.width * scale, height: photo.size.height * scale)
                        let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = true
                        image = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { _ in
                            UIColor.white.setFill(); UIRectFill(CGRect(x: 0, y: 0, width: side, height: side))
                            photo.draw(in: CGRect(x: (side - size.width) / 2, y: (side - size.height) / 2, width: size.width, height: size.height))
                        }.jpegData(compressionQuality: 0.88)
                        mode = "photo"
                    }
                }
        }.interactiveDismissDisabled(busy)
    }
}

/// A dedicated full-screen pool module: player identity, the live board and
/// controls are independent from the application's navigation and other games.
struct WapiIOSPoolArena: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var profile = WapiPoolProfileStore()
    @State private var editor = false
    @State private var mode = "training"
    @State private var session = UUID()
    @State private var angle: Float = 0
    @State private var power = 55.0
    @State private var side: Float = 0
    @State private var follow: Float = 0
    @State private var revision = 0
    @State private var moving = false
    @State private var humanTurn = true
    @State private var humanGroup = 0
    @State private var aiGroup = 0
    @State private var remaining = Set(1...15)
    @State private var before = Set(1...15)
    @State private var cueInHand = false
    @State private var placementValid = false
    @State private var winner: String?
    @State private var localScore = 0
    @State private var shots = 0
    @State private var aiTask: Task<Void, Never>?
    @State private var notice = ""
    @State private var fineAimStart: Float?
    @AppStorage("wapi.pool.table.theme") private var tableTheme = "competitionBlue"
    @AppStorage("wapi.pool.cue.style") private var cueStyle = "maple"
    @State private var cupActive = false
    @State private var cupWins = 0
    @State private var turnSeconds = 30
    @State private var opening = true
    private var canPlay: Bool { !moving && humanTurn && winner == nil && !editor }

    var body: some View {
        ZStack {
            Color(red: 0.01, green: 0.035, blue: 0.08).ignoresSafeArea()
            WapiIOS3DTabletop(scene: "Billard WAPI", dieValue: 1, poolAngle: angle, poolPower: Int(power), poolSideSpin: side, poolFollowSpin: follow, poolShotRevision: revision,
                             poolTableTheme: tableTheme, poolCueStyle: cueStyle,
                             cueInHand: cueInHand, poolInputEnabled: canPlay,
                             onPoolAim: { angle = $0 }, onPoolPlacement: { placementValid = $0 },
                             onPoolOutcome: settle, onPoolRemaining: { remaining = $0 }).id(session)
                .padding(.top, 72).padding(.bottom, 46)
            HStack {
                Spacer()
                VStack(spacing: 10) {
                    ForEach(0..<9) { tick in
                        Capsule().fill(tick == 4 ? Color.cyan : .white.opacity(0.35)).frame(width: tick == 4 ? 24 : 16, height: tick == 4 ? 3 : 1)
                    }
                }.frame(width: 38, height: 142).background(.black.opacity(0.8)).clipShape(Capsule())
                    .gesture(DragGesture(minimumDistance: 0).onChanged { gesture in
                        guard canPlay, !cueInHand else { return }
                        if fineAimStart == nil { fineAimStart = angle }
                        let raw = (fineAimStart ?? angle) + Float(gesture.translation.height) * 0.003
                        angle = atan2(sin(raw), cos(raw))
                    }.onEnded { _ in fineAimStart = nil })
                    .opacity(canPlay && !cueInHand ? 1 : 0.4).accessibilityLabel("Visée précise")
            }.padding(.trailing, 10)
            HStack {
                WapiIOSPoolVerticalPowerRail(power: $power, enabled: canPlay && !cueInHand, onRelease: strike)
                    .frame(width: 64, height: 270)
                Spacer()
            }.padding(.leading, 12)
            VStack(spacing: 8) {
                HStack(spacing: 10) {
                    Button { dismiss() } label: { Image(systemName: "chevron.left").font(.headline).frame(width: 36, height: 44) }.tint(.white).accessibilityLabel("Quitter le billard")
                    Button { editor = true } label: { PoolPlayerScoreIOS(player: profile.player, group: humanGroup, remaining: remaining, active: humanTurn && winner == nil, seconds: humanTurn ? turnSeconds : 30) }.buttonStyle(.plain).disabled(moving || !humanTurn)
                    VStack(spacing: 2) {
                        Text(winner ?? (cueInHand ? "Blanche en main" : moving ? "Tir en cours" : humanTurn ? "À vous" : "Tour IA")).font(.caption.bold())
                        Text("\(localScore) pts · local").font(.system(size: 10))
                    }.foregroundStyle(.white).frame(maxWidth: 105)
                    PoolPlayerScoreIOS(player: WapiPoolPlayer(name: mode == "ai" ? "IA · Billard" : "Entraînement", icon: mode == "ai" ? "robot" : "cue"), group: aiGroup, remaining: remaining, active: !humanTurn && winner == nil, seconds: !humanTurn ? turnSeconds : 30)
                }.padding(.horizontal, 12)
                Spacer(minLength: 0)
                if !notice.isEmpty { Text(notice).font(.caption).foregroundStyle(.white).padding(8).background(.black.opacity(0.85)).clipShape(Capsule()).allowsHitTesting(false) }
                HStack(spacing: 10) {
                    Menu {
                        Button("Entraînement") { reset("training") }
                        Button("Contre l’IA") { reset("ai") }
                        Button(cupActive ? "Abandonner la Coupe IA" : "Lancer la Coupe IA") { cupActive.toggle(); cupWins = 0; reset("ai") }
                        Menu("Tapis") {
                            Button("Bleu compétition") { tableTheme = "competitionBlue" }
                            Button("Bleu nuit") { tableTheme = "navy" }
                            Button("Vert tournoi") { tableTheme = "emerald" }
                        }
                        Menu("Queue") {
                            Button("Érable") { cueStyle = "maple" }
                            Button("Noyer") { cueStyle = "walnut" }
                            Button("Carbone") { cueStyle = "carbon" }
                        }
                        Button("Recommencer") { reset(mode) }
                        Button("Mon profil et mes records") { editor = true }
                    } label: { Image(systemName: "line.3.horizontal").font(.title3).frame(width: 44, height: 44) }.tint(.white).disabled(moving || !humanTurn)
                    WapiIOSPoolSpinPad(sideSpin: $side, followSpin: $follow, enabled: canPlay && !cueInHand).frame(width: 46, height: 46)
                    if cueInHand {
                        Button {
                            cueInHand = false; WapiSounds.haptic(.light); notice = ""
                        } label: { Label("Poser la blanche", systemImage: "hand.draw.fill").frame(maxWidth: .infinity) }
                            .buttonStyle(.borderedProminent).disabled(!canPlay || !placementValid)
                    } else {
                        if (mode == "training" || shots == 0) && winner == nil {
                            Button { cueInHand = true; placementValid = false; notice = "Déplacez la main derrière la ligne, puis posez la blanche." } label: { Image(systemName: "hand.draw.fill").frame(width: 44, height: 44) }.tint(.white).disabled(!canPlay).accessibilityLabel("Placer la blanche")
                        }
                    }
                }.padding(.horizontal, 16).padding(.bottom, 4)
            }
            if let winner {
                PoolVictoryOverlayIOS(winner: winner == "Victoire", player: profile.player, score: localScore, victories: profile.localVictories, defeats: profile.localDefeats, cups: profile.localCups, onReplay: { reset(mode) })
            }
            if opening { WapiPoolOpeningIOS() }
        }.task {
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--wapi-pool-glove-test") { cueInHand = true }
            #endif
            await profile.load()
            try? await Task.sleep(for: .milliseconds(650))
            opening = false
        }
            .task(id: "\(mode)-\(humanTurn)-\(moving)-\(cueInHand)-\(winner ?? "none")-\(shots)") {
                guard mode == "ai", humanTurn, !moving, !cueInHand, winner == nil else { turnSeconds = 30; return }
                turnSeconds = 30
                for seconds in stride(from: 29, through: 0, by: -1) {
                    try? await Task.sleep(for: .seconds(1))
                    guard humanTurn, !moving, !cueInHand, winner == nil else { return }
                    turnSeconds = seconds
                }
                guard humanTurn, !moving, winner == nil else { return }
                notice = "Temps écoulé · tour de l’IA."
                humanTurn = false
            }
            .fullScreenCover(isPresented: $editor) { PoolProfileEditorIOS(store: profile) }
            .onDisappear { aiTask?.cancel() }
    }
    private func reset(_ nextMode: String) {
        aiTask?.cancel(); mode = nextMode; session = UUID(); revision = 0; shots = 0
        moving = false; humanTurn = true; humanGroup = 0; aiGroup = 0
        remaining = Set(1...15); before = remaining; cueInHand = false; winner = nil; localScore = 0; turnSeconds = 30
        angle = 0; power = 55; side = 0; follow = 0; notice = ""
    }
    private func strike() {
        guard canPlay, !cueInHand, !remaining.isEmpty else { return }
        before = remaining; moving = true; revision += 1; shots += 1; notice = ""
    }
    private func settle(_ outcome: WapiPoolShotOutcome) {
        guard moving else { return }
        moving = false; remaining = outcome.remaining
        let pocketNotice = outcome.pocketed.sorted().map { "Bille \($0) empochée" }.joined(separator: " · ")
        if humanTurn { localScore += outcome.pocketed.count * 100; profile.recordPractice(localScore) }
        if mode == "training" {
            cueInHand = outcome.scratched || outcome.firstContact == nil
            placementValid = false
            if remaining.isEmpty { winner = "Table terminée"; WapiSounds.haptic(.heavy) }
            else { notice = pocketNotice }
            return
        }
        let result = WapiPoolRules.resolve(before: before, shooterGroup: humanTurn ? humanGroup : aiGroup, opponentGroup: humanTurn ? aiGroup : humanGroup,
                                          first: outcome.firstContact, scratched: outcome.scratched, pocketed: outcome.pocketed)
        if humanTurn { humanGroup = result.shooterGroup; aiGroup = result.opponentGroup }
        else { aiGroup = result.shooterGroup; humanGroup = result.opponentGroup }
        if let won = result.shooterWon {
            let playerWon = humanTurn ? won : !won
            winner = playerWon ? "Victoire" : "Défaite"
            profile.recordPracticeResult(localScore, won: playerWon)
            if playerWon, cupActive {
                cupWins += 1
                if cupWins >= 3 { profile.awardLocalCup(); cupWins = 0; cupActive = false }
            } else if !playerWon, cupActive { cupWins = 0 }
            notice = "Partie locale terminée · aucun point en ligne attribué."
            return
        }
        if !result.keepTurn { humanTurn.toggle() }
        cueInHand = result.foul && humanTurn; placementValid = false
        if result.foul { notice = [pocketNotice, "Faute · bille en main pour le joueur suivant."].filter { !$0.isEmpty }.joined(separator: " · ") }
        else { notice = pocketNotice }
        guard !humanTurn else { return }
        let shot = outcome.aiShots[aiGroup] ?? (0, 45)
        let currentSession = session
        aiTask = Task { @MainActor in
            do { try await Task.sleep(for: .milliseconds(650)) } catch { return }
            guard !Task.isCancelled, session == currentSession, winner == nil else { return }
            angle = shot.0; power = Double(shot.1); side = 0; follow = 0
            before = remaining; moving = true; revision += 1; shots += 1
        }
    }
}

private struct WapiPoolOpeningIOS: View {
    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(red: 0.008, green: 0.035, blue: 0.09), Color(red: 0.015, green: 0.23, blue: 0.42)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            VStack(spacing: 16) {
                ZStack {
                    Circle().fill(.black).frame(width: 92, height: 92).overlay(Circle().stroke(Color.cyan.opacity(0.8), lineWidth: 3))
                    Text("8").font(.system(size: 47, weight: .black)).foregroundStyle(.white)
                }.shadow(color: .cyan.opacity(0.5), radius: 18)
                Text("WAPI POOL").font(.system(size: 28, weight: .black, design: .rounded)).tracking(2).foregroundStyle(.white)
                Text("Mise en place de la table").font(.subheadline).foregroundStyle(.white.opacity(0.72))
                ProgressView().tint(.cyan).scaleEffect(1.15)
            }
        }.accessibilityLabel("Wapi Pool, chargement de la table")
    }
}

private struct WapiIOSPoolVerticalPowerRail: View {
    @Binding var power: Double
    let enabled: Bool
    let onRelease: () -> Void
    @State private var startPower: Double?

    private var force: Color {
        power >= 82 ? Color(red: 1, green: 0.34, blue: 0.42) : power >= 58 ? .orange : .cyan
    }

    var body: some View {
        GeometryReader { proxy in
            let h = max(proxy.size.height, 1)
            ZStack {
                RoundedRectangle(cornerRadius: 25).fill(Color(red: 0.015, green: 0.055, blue: 0.14).opacity(0.94))
                RoundedRectangle(cornerRadius: 25).stroke(force.opacity(enabled ? 0.62 : 0.20), lineWidth: 1)
                Capsule().fill(.black.opacity(0.48)).frame(width: 17).padding(.vertical, 31)
                VStack(spacing: 0) {
                    Spacer(minLength: 31)
                    Capsule().fill(LinearGradient(colors: [force, force.opacity(0.18)], startPoint: .bottom, endPoint: .top))
                        .frame(width: 6, height: max(9, (h - 62) * power / 100))
                    Spacer(minLength: 31)
                }
                VStack {
                    Text("\(Int(power))%").font(.system(size: 10, weight: .black)).foregroundStyle(.white)
                    Spacer()
                    Rectangle().fill(LinearGradient(colors: [Color(red: 0.92, green: 0.77, blue: 0.52), Color(red: 0.42, green: 0.12, blue: 0.04)], startPoint: .top, endPoint: .bottom))
                        .frame(width: 9, height: 92).clipShape(Capsule())
                    Text("TIREZ").font(.system(size: 8, weight: .bold)).tracking(0.7).foregroundStyle(.white.opacity(0.72))
                }.padding(.vertical, 9)
            }
            .contentShape(RoundedRectangle(cornerRadius: 25))
            .gesture(DragGesture(minimumDistance: 5).onChanged { value in
                guard enabled else { return }
                if startPower == nil { startPower = power }
                power = min(100, max(10, Double(value.translation.height / max(h * 0.62, 1) * 100)))
            }.onEnded { value in
                guard enabled, value.translation.height > 8 else { if let startPower { power = startPower }; startPower = nil; return }
                startPower = nil; onRelease()
            })
            .opacity(enabled ? 1 : 0.48)
            .accessibilityLabel("Puissance du tir")
            .accessibilityValue("\(Int(power)) pour cent")
            .accessibilityHint("Tirez vers le bas puis relâchez pour frapper")
        }
    }
}

private struct PoolVictoryOverlayIOS: View {
    let winner: Bool
    let player: WapiPoolPlayer
    let score: Int
    let victories: Int
    let defeats: Int
    let cups: Int
    let onReplay: () -> Void
    var body: some View {
        VStack(spacing: 10) {
            if winner { Text("✦  ✦  ✦").foregroundStyle(.yellow).font(.title2) }
            PoolAvatarIOS(player: player, size: 62)
            Text(winner ? "Victoire" : "Défaite").font(.title.bold()).foregroundStyle(.white)
            Text("\(score) points · \(victories) victoires · \(defeats) défaites").font(.caption).foregroundStyle(.white.opacity(0.78))
            if winner { Text("+250 XP de partie").font(.caption.bold()).foregroundStyle(.yellow) }
            if cups > 0 { Text("\(cups) coupe(s) IA locale(s)").font(.caption).foregroundStyle(.yellow) }
            Text("Aucun gain d’argent ni classement officiel n’est simulé.").font(.caption2).multilineTextAlignment(.center).foregroundStyle(.white.opacity(0.65))
            Button("Rejouer", action: onReplay).buttonStyle(.borderedProminent)
        }
        .padding(24).frame(maxWidth: 350).background(.ultraThinMaterial).clipShape(RoundedRectangle(cornerRadius: 28))
        .overlay(RoundedRectangle(cornerRadius: 28).stroke(winner ? .yellow.opacity(0.7) : .white.opacity(0.25)))
    }
}
