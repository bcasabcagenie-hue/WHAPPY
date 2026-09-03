import SwiftUI
import PhotosUI
import FirebaseAuth
import FirebaseFunctions
import CoreImage.CIFilterBuiltins

enum WapiCommercePrice {
    static func fraction(_ currency: String) -> Int { ["EUR", "USD"].contains(currency) ? 2 : 0 }
    static func input(_ amount: Int64, _ currency: String) -> String {
        let value = Decimal(amount) / (fraction(currency) == 2 ? 100 : 1)
        return NSDecimalNumber(decimal: value).stringValue
    }
    static func minor(_ input: String, _ currency: String) -> Int64? {
        let value = input.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")
        guard value.range(of: #"^[0-9]+(?:\.[0-9]{1,2})?$"#, options: .regularExpression) != nil,
              let decimal = Decimal(string: value, locale: Locale(identifier: "en_US_POSIX")) else { return nil }
        var scaled = decimal * (fraction(currency) == 2 ? 100 : 1)
        var integral = Decimal(); NSDecimalRound(&integral, &scaled, 0, .plain)
        guard integral == scaled, scaled >= 0, scaled <= 100_000_000 else { return nil }
        return NSDecimalNumber(decimal: scaled).int64Value
    }
}

struct WapiCommerceRecord: Identifiable {
    let data: [String: Any]
    var id: String { string("id") }
    func string(_ key: String) -> String { data[key] as? String ?? "" }
    func number(_ key: String) -> Int64 { (data[key] as? NSNumber)?.int64Value ?? 0 }
    func bool(_ key: String) -> Bool { data[key] as? Bool ?? false }
    var catalogueCategory: String { let value = string("category").trimmingCharacters(in: .whitespacesAndNewlines); return value.isEmpty ? "Autres" : value }
    var date: Date { Date(timeIntervalSince1970: Double(number("startsAt")) / 1000) }
    var price: String {
        let fraction = ["EUR", "USD"].contains(string("currency")) ? 100.0 : 1
        return (Double(number("priceMinor")) / fraction).formatted() + " " + string("currency")
    }
}

@MainActor final class WapiCommerceModel: ObservableObject {
    @Published var rows: [WapiCommerceRecord] = []
    @Published var pages: [WapiCommerceRecord] = []
    @Published var page = WapiCommerceRecord(data: [:])
    @Published var products: [WapiCommerceRecord] = []
    @Published var invoiceSummary = WapiCommerceRecord(data: [:])
    @Published var owner = false
    @Published var busy = false
    @Published var error: String?
    @Published var cursor: String?
    private var generation = 0
    func invalidate() { generation += 1; busy = true; rows = []; products = []; invoiceSummary = WapiCommerceRecord(data: [:]); cursor = nil; page = WapiCommerceRecord(data: [:]); owner = false }
    static func call(_ action: String, _ data: [String: Any] = [:]) async throws -> [String: Any] {
        var input = data; input["action"] = action
        let result = try await Functions.functions(region: "europe-west1").httpsCallable("wapiCommerce").call(input)
        guard let result = result.data as? [String: Any] else { throw NSError(domain: "WAPI", code: 1, userInfo: [NSLocalizedDescriptionKey: "Réponse WAPI invalide."]) }
        return result
    }
    func load(_ route: String, pageID: String, search: String, city: String, append: Bool = false) async {
        generation += 1; let request = generation; busy = true; error = nil
        do {
            var input: [String: Any] = ["pageId": pageID, "search": search, "city": city]
            if append, let cursor { input["cursor"] = cursor }
            let result = try await Self.call(route, input)
            guard request == generation, !Task.isCancelled else { return }
            let key = route == "directory" ? "stores" : route == "storefront" ? "products" : route == "events" ? "events" : route == "billing" ? "invoices" : "tickets"
            let records = (result[key] as? [[String: Any]] ?? []).map { WapiCommerceRecord(data: $0) }
            var seen = Set<String>()
            rows = (append ? rows + records : records).filter { seen.insert($0.id).inserted }
            page = WapiCommerceRecord(data: result["page"] as? [String: Any] ?? [:]); owner = result["owner"] as? Bool ?? false
            if route == "billing" {
                products = (result["products"] as? [[String: Any]] ?? []).map { WapiCommerceRecord(data: $0) }
                invoiceSummary = WapiCommerceRecord(data: result["summary"] as? [String: Any] ?? [:])
            }
            cursor = result["nextCursor"] as? String
        } catch { if request == generation { self.error = Self.message(error) } }
        if request == generation { busy = false }
    }
    func loadPages() async {
        do { pages = (try await Self.call("ownPages")["pages"] as? [[String: Any]] ?? []).map { WapiCommerceRecord(data: $0) } }
        catch { self.error = Self.message(error) }
    }
    static func message(_ error: Error) -> String {
        let ns = error as NSError
        if ns.domain == FunctionsErrorDomain && ns.code == FunctionsErrorCode.notFound.rawValue { return "Ce service n’est pas encore disponible sur le serveur. Aucune modification n’a été enregistrée." }
        return wapiUserFacingError(error, action: "L’opération WAPI")
    }
}

struct WapiCommerceLaunchersIOS: View {
    var body: some View {
        HStack(spacing: 12) {
            NavigationLink { WapiCommerceView(initialRoute: "directory") } label: { tile("Boutiques & menus", "Adresses · catalogues", "storefront") }
            NavigationLink { WapiCommerceView(initialRoute: "events") } label: { tile("Ticketbulk", "Événements · billets", "ticket") }
        }.buttonStyle(.plain)
    }
    private func tile(_ title: String, _ subtitle: String, _ icon: String) -> some View {
        VStack(alignment: .leading, spacing: 10) { Image(systemName: icon).font(.title2).foregroundStyle(Color.whappyBlue); Text(title).font(.headline); Text(subtitle).font(.caption).foregroundStyle(.secondary) }
            .frame(maxWidth: .infinity, alignment: .leading).padding(16).background(.white, in: RoundedRectangle(cornerRadius: 20))
    }
}

struct WapiCommerceView: View {
    let initialRoute: String
    @EnvironmentObject private var store: WhappyStore
    @StateObject private var model = WapiCommerceModel()
    @State private var search = ""
    @State private var city = ""
    @State private var pageID = ""
    @State private var route = ""
    @State private var editor: String?
    @State private var product = WapiCommerceRecord(data: [:])
    @State private var ticket: WapiCommerceRecord?
    @State private var conversationID: UUID?
    @State private var contacting = false
    @State private var mutationBusy = false
    @State private var catalogueSearch = ""
    @State private var catalogueCategory = ""
    @State private var availableOnly = false
    @State private var checkEventID = ""
    @State private var eventSearch = ""
    @State private var eventCategory = ""
    @State private var eventAvailabilityOnly = false
    private var current: String { route.isEmpty ? initialRoute : route }
    private var ticketbulk: Bool { current == "events" || current == "myTickets" }
    private var loadKey: String { "\(current)|\(pageID)|\(search)|\(city)" }
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                if model.busy { ProgressView().frame(maxWidth: .infinity) }
                if let error = model.error { Text(error).foregroundStyle(.red); Button("Réessayer") { Task { await reload() } } }
                if current == "directory" { directory }
                else if current == "storefront" { storefront }
                else if current == "events" { events }
                else if current == "billing" { billing }
                else { tickets }
                if model.cursor != nil { Button("Charger la suite") { Task { await model.load(current, pageID: pageID, search: search, city: city, append: true) } }.disabled(model.busy) }
            }.padding(16)
        }
        .background(ticketbulk ? Color(red: 0.94, green: 0.97, blue: 0.98) : Color.whappyBackground)
        .navigationTitle(ticketbulk ? "TicketBulk" : current == "storefront" ? model.page.string("name") : current == "billing" ? "Facturation Business" : "Boutiques & menus")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { if current == "storefront" { route = "directory" } else if current == "myTickets" { route = "events" } else { Task { await reload() } } } label: { Image(systemName: current == "storefront" || current == "myTickets" ? "square.grid.2x2" : "arrow.clockwise") } } }
        .task { await model.loadPages() }
        .task(id: loadKey) {
            model.invalidate()
            if current == "directory" && (!search.isEmpty || !city.isEmpty) { try? await Task.sleep(for: .milliseconds(300)); if Task.isCancelled { return } }
            await reload()
        }
        .fullScreenCover(isPresented: Binding(get: { editor != nil }, set: { if !$0 { editor = nil } })) {
            WapiCommerceEditorIOS(kind: editor ?? "product", page: model.page, product: product, invoiceProducts: model.products, pages: model.pages, checkEventID: checkEventID) { editor = nil; Task { await reload() } }
        }
        .sheet(item: $ticket) { WapiTicketIOS(ticket: $0) { ticket = nil; Task { await reload() } } }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if ticketbulk {
                HStack(spacing: 0) {
                    ticketTab("Découvrir", "safari", selected: current == "events") { route = "events" }
                    ticketTab("Mes billets", "ticket", selected: current == "myTickets") { route = "myTickets" }
                    ticketTab("Organiser", "plus.circle.fill", selected: false) { editor = "event" }
                }.padding(.vertical, 8).background(.ultraThinMaterial)
            }
        }
        .onChange(of: pageID) { _, _ in catalogueSearch = ""; catalogueCategory = ""; availableOnly = false }
        .navigationDestination(item: $conversationID) { ConversationView(conversationID: $0) }
    }
    private func reload() async { await model.load(current, pageID: pageID, search: search, city: city) }
    private func ticketTab(_ title: String, _ symbol: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) { VStack(spacing: 3) { Image(systemName: symbol); Text(title).font(.caption2.weight(.semibold)) }.frame(maxWidth: .infinity).foregroundStyle(selected ? Color.whappyBlue : Color.secondary) }.buttonStyle(.plain)
    }
    private var directory: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Trouvez votre prochaine adresse").font(.title2.bold())
            TextField("Restaurant, boutique, activité…", text: $search).textFieldStyle(.roundedBorder)
            TextField("Ville · toutes si vide", text: $city).textFieldStyle(.roundedBorder)
            if !model.pages.isEmpty {
                Text("MES VITRINES").font(.caption).foregroundStyle(.secondary)
                ForEach(model.pages) { page in Button { pageID = page.id; route = "storefront" } label: { Label(page.string("name"), systemImage: "pencil") } }
            }
            ForEach(model.rows) { page in
                Button { pageID = page.id; route = "storefront" } label: {
                    HStack(spacing: 14) {
                        CommercePhoto(url: page.string("logoUrl"), size: 64, symbol: "storefront")
                        VStack(alignment: .leading, spacing: 5) { Text(page.string("name")).font(.headline); Text(page.string("category")).foregroundStyle(Color.whappyBlue); Text(page.string("city")).font(.caption).foregroundStyle(.secondary) }
                        Spacer(); Image(systemName: "chevron.right")
                    }.padding(16).background(.white, in: RoundedRectangle(cornerRadius: 20))
                }.buttonStyle(.plain)
            }
            if !model.busy && model.rows.isEmpty && model.error == nil { Text("Aucun établissement dans ces résultats. Changez de ville ou poursuivez la recherche.").foregroundStyle(.secondary) }
        }
    }
    private var storefront: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 14) {
                CommercePhoto(url: model.page.string("logoUrl"), size: 64, symbol: "storefront")
                VStack(alignment: .leading, spacing: 5) {
                    Text(model.page.string("name")).font(.title2.bold())
                    Text("\(model.page.string("category")) · \(model.page.string("city"))").font(.subheadline).foregroundStyle(Color.whappyBlue)
                }
            }
            Text(model.page.string("bio")); Text(model.page.string("address")).foregroundStyle(.secondary); Text(model.page.string("hours")).foregroundStyle(.secondary)
            if model.owner {
                Text(model.page.bool("published") ? "Vitrine visible dans Marketplace" : "Vitrine privée · à publier").font(.caption).foregroundStyle(Color.whappyBlue)
                HStack { Button("Régler la vitrine") { editor = "store" }; Spacer(); Button("Ajouter un article") { product = WapiCommerceRecord(data: [:]); editor = "product" }; Spacer(); Button("Facturation") { route = "billing" } }
            } else if !model.page.id.isEmpty {
                Button(contacting ? "Ouverture…" : "Écrire à l’établissement") { Task { await contact() } }.buttonStyle(.borderedProminent).disabled(contacting)
            }
            TextField("Rechercher dans le catalogue", text: $catalogueSearch).textFieldStyle(.roundedBorder)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    categoryChip("", title: "Tout", count: matchingProducts.count)
                    ForEach(Array(Set(model.rows.map(\.catalogueCategory))).sorted(), id: \.self) { name in
                        categoryChip(name, title: name, count: matchingProducts.filter { $0.catalogueCategory == name }.count)
                    }
                }
            }
            Toggle("Disponibles uniquement", isOn: $availableOnly).font(.subheadline).tint(Color.whappyBlue)
            ForEach(Array(Set(filteredProducts.map(\.catalogueCategory))).sorted(), id: \.self) { category in
                Text(category).font(.title3.bold())
                ForEach(filteredProducts.filter { $0.catalogueCategory == category }) { item in
                    Button { product = item; editor = model.owner ? "product" : "detail" } label: {
                        HStack(spacing: 14) {
                            CommercePhoto(url: item.string("imageUrl"), size: 84, symbol: "fork.knife")
                            VStack(alignment: .leading, spacing: 6) { Text(item.string("name")).font(.headline); Text(item.string("description")).font(.caption).lineLimit(3).foregroundStyle(.secondary); Text(item.price).foregroundStyle(Color.whappyBlue).bold(); if !item.bool("available") { Text("Indisponible").font(.caption) } }; Spacer()
                        }.padding(14).background(.white, in: RoundedRectangle(cornerRadius: 18))
                    }.buttonStyle(.plain)
                }
            }
            if !model.busy && filteredProducts.isEmpty && model.error == nil {
                Text(!model.rows.isEmpty ? "Aucun article ne correspond à ces filtres." : model.owner ? "Ajoutez vos plats, produits ou prestations." : "Cet établissement prépare son catalogue.").foregroundStyle(.secondary)
                if !model.rows.isEmpty { Button("Réinitialiser les filtres") { catalogueSearch = ""; catalogueCategory = ""; availableOnly = false } }
            }
        }
    }
    private var matchingProducts: [WapiCommerceRecord] { model.rows.filter { (!availableOnly || $0.bool("available")) && "\($0.string("name")) \($0.catalogueCategory) \($0.string("description"))".matchesWhappySearch(catalogueSearch) } }
    private var filteredProducts: [WapiCommerceRecord] { matchingProducts.filter { catalogueCategory.isEmpty || $0.catalogueCategory == catalogueCategory } }
    private func categoryChip(_ value: String, title: String, count: Int) -> some View {
        Button { catalogueCategory = catalogueCategory == value ? "" : value } label: {
            Text("\(title) · \(count)").font(.subheadline.weight(.semibold))
                .padding(.horizontal, 14).frame(minHeight: 44)
                .foregroundStyle(catalogueCategory == value ? Color.white : Color.primary)
                .background(catalogueCategory == value ? Color.whappyBlue : Color.white, in: Capsule())
        }.buttonStyle(.plain).accessibilityAddTraits(catalogueCategory == value ? [.isSelected] : [])
    }
    private var events: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 11) { Image(systemName: "ticket.fill").font(.title2).foregroundStyle(.cyan); VStack(alignment: .leading, spacing: 3) { Text("TICKETBULK").font(.headline.weight(.heavy)); Text("Événements, billets et contrôle d’entrée").font(.caption).foregroundStyle(.white.opacity(0.72)) } }
                HStack { Button("Mes billets") { route = "myTickets" }.buttonStyle(.bordered); Spacer(); Button("Créer un événement") { editor = "event" }.buttonStyle(.borderedProminent) }
            }.padding(18).foregroundStyle(.white).background(Color(red: 0.03, green: 0.16, blue: 0.28), in: RoundedRectangle(cornerRadius: 24))
            TextField("Rechercher un concert, une formation, un lieu…", text: $eventSearch).textFieldStyle(.roundedBorder)
            let eventCategories = Array(Set(model.rows.map { $0.string("category").isEmpty ? "Événement" : $0.string("category") })).sorted()
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    eventChip("", title: "Tous", count: filteredEvents.count)
                    ForEach(eventCategories, id: \.self) { label in eventChip(label, title: label, count: filteredEvents.filter { ($0.string("category").isEmpty ? "Événement" : $0.string("category")) == label }.count) }
                }
            }
            Toggle("Places disponibles uniquement", isOn: $eventAvailabilityOnly).font(.subheadline).tint(Color.whappyBlue)
            ForEach(filteredEvents) { event in
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 12) {
                        CommercePhoto(url: event.string("posterUrl"), size: 78, symbol: "ticket")
                        VStack(alignment: .leading, spacing: 4) { Text(event.string("category").isEmpty ? "ÉVÉNEMENT" : event.string("category").uppercased()).font(.caption.weight(.heavy)).foregroundStyle(Color.whappyBlue); Text(event.string("title")).font(.title3.bold()).lineLimit(2); Text(event.date.formatted(date: .abbreviated, time: .shortened)).font(.caption).foregroundStyle(.secondary) }
                    }
                    Text("\(event.string("venue")) · \(event.string("organizer"))").font(.subheadline).foregroundStyle(.secondary)
                    if !event.string("description").isEmpty { Text(event.string("description")).font(.subheadline).lineLimit(3) }
                    let remaining = max(0, event.number("capacity") - event.number("reserved")); let occupancy = event.number("capacity") > 0 ? Double(event.number("reserved")) / Double(event.number("capacity")) : 0
                    ProgressView(value: min(1, occupancy)).tint(remaining == 0 ? .red : .green)
                    HStack { Text("\(event.string("ticketLabel").isEmpty ? "Accès général" : event.string("ticketLabel")) · gratuit").font(.caption.weight(.semibold)).foregroundStyle(Color.whappyBlue); Spacer(); Text("\(remaining) / \(event.number("capacity")) places").font(.caption).foregroundStyle(.secondary) }
                    if event.string("ownerId") == Auth.auth().currentUser?.uid {
                        HStack { Label("\(event.number("reserved")) billets · \(event.number("checkedIn")) entrées", systemImage: "person.2.fill").font(.caption.bold()); Spacer(); Button("Scanner") { checkEventID = event.id; editor = "check" } }
                            .padding(10).background(Color.green.opacity(0.10), in: RoundedRectangle(cornerRadius: 13))
                        if event.string("status") == "published" { Button("Fermer les inscriptions") { Task { await mutate { _ = try await WapiCommerceModel.call("closeEvent", ["eventId": event.id]); await reload() } } }.disabled(mutationBusy) }
                    }
                    let hasTicket = ["issued", "used"].contains(event.string("myTicketStatus"))
                    let reservable = event.string("status") == "published" && event.date > Date() && event.number("reserved") < event.number("capacity")
                    if hasTicket || reservable { Button { Task { await mutate { let result = try await WapiCommerceModel.call("reserveTicket", ["eventId": event.id]); if let data = result["ticket"] as? [String: Any] { ticket = WapiCommerceRecord(data: data) }; await reload() } } } label: { Label(hasTicket ? "Afficher mon billet" : "Réserver mon billet", systemImage: hasTicket ? "qrcode" : "ticket.fill") }.buttonStyle(.borderedProminent).frame(maxWidth: .infinity).disabled(mutationBusy) }
                    else { Text(event.number("reserved") >= event.number("capacity") ? "Complet" : "Inscriptions fermées").foregroundStyle(.secondary) }
                }.padding(20).frame(maxWidth: .infinity, alignment: .leading).background(.white, in: RoundedRectangle(cornerRadius: 22))
            }
            if !model.busy && model.rows.isEmpty && model.error == nil { Text("Pas d’événement dans cette sélection. Créez le vôtre avec votre page Business.").foregroundStyle(.secondary) }
            else if !model.busy && filteredEvents.isEmpty && model.error == nil { Text("Aucun événement ne correspond à ces filtres.").foregroundStyle(.secondary); Button("Réinitialiser") { eventSearch = ""; eventCategory = ""; eventAvailabilityOnly = false } }
        }
    }
    private var filteredEvents: [WapiCommerceRecord] {
        model.rows.filter { event in
            let remaining = max(0, event.number("capacity") - event.number("reserved"))
            let category = event.string("category").isEmpty ? "Événement" : event.string("category")
            let content = "\(event.string("title")) \(event.string("description")) \(event.string("venue")) \(category) \(event.string("organizer"))"
            return (eventSearch.isEmpty || content.matchesWhappySearch(eventSearch)) && (eventCategory.isEmpty || category == eventCategory) && (!eventAvailabilityOnly || remaining > 0)
        }
    }
    private func eventChip(_ value: String, title: String, count: Int) -> some View {
        Button { eventCategory = eventCategory == value ? "" : value } label: {
            Text("\(title) · \(count)").font(.subheadline.weight(.semibold)).padding(.horizontal, 14).frame(minHeight: 42)
                .foregroundStyle(eventCategory == value ? Color.white : Color.primary).background(eventCategory == value ? Color.whappyBlue : Color.white, in: Capsule())
        }.buttonStyle(.plain).accessibilityAddTraits(eventCategory == value ? [.isSelected] : [])
    }
    private var billing: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Suivi des créances").font(.title2.bold())
                Text("À encaisser : \(model.invoiceSummary.number("outstandingMinor").formatted()) XAF").foregroundStyle(Color.whappyBlue).bold()
                Text("Échues : \(model.invoiceSummary.number("overdueMinor").formatted()) XAF").foregroundStyle(.red)
                Text("WAPI prépare la facture et la relance ; l’encaissement reste sous votre contrôle.").font(.caption).foregroundStyle(.secondary)
                Button("Créer une facture") { editor = "invoice" }.buttonStyle(.borderedProminent).disabled(model.products.isEmpty)
                if model.products.isEmpty { Text("Ajoutez d’abord un article disponible à votre catalogue.").font(.caption).foregroundStyle(.secondary) }
            }.padding(20).background(.white, in: RoundedRectangle(cornerRadius: 22))
            ForEach(model.rows) { invoice in
                let currency = invoice.string("currency")
                let divisor: Double = ["EUR", "USD"].contains(currency) ? 100 : 1
                let balance = Double(invoice.number("balanceMinor")) / divisor
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(invoice.string("customerName")).font(.headline)
                            Text(invoice.string("number")).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text("\(balance.formatted()) \(currency)").foregroundStyle(Color.whappyBlue).bold()
                    }
                    Text(invoice.string("status") == "overdue" ? "Échéance dépassée" : "Échéance : \(Date(timeIntervalSince1970: Double(invoice.number("dueAt")) / 1000).formatted(date: .abbreviated, time: .omitted))").font(.caption).foregroundStyle(invoice.string("status") == "overdue" ? .red : .secondary)
                    Button("Préparer une relance") {
                        Task {
                            await mutate {
                                let result = try await WapiCommerceModel.call("prepareInvoiceReminder", ["invoiceId": invoice.id, "tone": "courtois"])
                                model.error = result["notice"] as? String ?? "Relance prête à relire."
                            }
                        }
                    }
                    .disabled(mutationBusy)
                }.padding(18).background(.white, in: RoundedRectangle(cornerRadius: 18))
            }
            if !model.busy && model.rows.isEmpty && model.error == nil { Text("Aucune facture émise pour cette page.").foregroundStyle(.secondary) }
        }
    }
    private var tickets: some View {
        VStack(alignment: .leading, spacing: 16) {
            ForEach(model.rows) { item in Button { ticket = item } label: {
                HStack(spacing: 12) { CommercePhoto(url: item.string("posterUrl"), size: 58, symbol: "ticket"); VStack(alignment: .leading, spacing: 5) { Text(item.string("title")).font(.headline); Text(item.date.formatted(date: .abbreviated, time: .shortened)).font(.caption).foregroundStyle(.secondary); Text(item.string("status") == "used" ? "Billet déjà validé" : item.string("status") == "cancelled" ? "Réservation annulée" : "QR prêt à présenter").font(.caption.weight(.semibold)).foregroundStyle(Color.whappyBlue) }; Spacer(); Image(systemName: "qrcode").foregroundStyle(Color.whappyBlue) }.frame(maxWidth: .infinity, alignment: .leading).padding(15).background(.white, in: RoundedRectangle(cornerRadius: 20))
            }.buttonStyle(.plain) }
            if !model.busy && model.rows.isEmpty && model.error == nil { Text("Vos billets réservés seront conservés ici.").foregroundStyle(.secondary) }
        }
    }
    private func mutate(_ operation: () async throws -> Void) async {
        guard !mutationBusy else { return }; mutationBusy = true; defer { mutationBusy = false }
        do { try await operation() } catch { model.error = WapiCommerceModel.message(error) }
    }
    private func contact() async {
        contacting = true; defer { contacting = false }
        do {
            let result = try await WapiCommerceModel.call("contactStorefront", ["pageId": model.page.id])
            guard let remoteID = result["conversationId"] as? String else { return }
            // The regular messaging listener is the source of truth for the route.
            for _ in 0..<20 {
                if let conversation = store.conversations.first(where: { $0.remoteID == remoteID }) { conversationID = conversation.id; return }
                try await Task.sleep(for: .milliseconds(150))
            }
            model.error = "La discussion a été créée. Retrouvez-la dans Messages après synchronisation."
        } catch { model.error = WapiCommerceModel.message(error) }
    }
}

struct CommercePhoto: View {
    let url: String; let size: CGFloat; let symbol: String
    var body: some View {
        Group { if let source = URL(string: url), !url.isEmpty { WapiCachedRemoteImage(url: source) { Image(systemName: symbol).font(.title).foregroundStyle(Color.whappyBlue) } } else { Image(systemName: symbol).font(.title).foregroundStyle(Color.whappyBlue).frame(maxWidth: .infinity, maxHeight: .infinity).background(Color.whappyBlue.opacity(0.06)) } }
            .frame(width: size, height: size).clipped().clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct WapiCommerceEditorIOS: View {
    let kind: String; let page: WapiCommerceRecord; let product: WapiCommerceRecord; let invoiceProducts: [WapiCommerceRecord]; let pages: [WapiCommerceRecord]; let checkEventID: String; let saved: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""; @State private var description = ""; @State private var category = "Menu"; @State private var ticketLabel = "Accès général"
    @State private var amount = ""; @State private var currency = "XAF"; @State private var address = ""; @State private var hours = ""
    @State private var available = true; @State private var selectedPage = ""; @State private var capacity = "100"
    @State private var date = Date().addingTimeInterval(86400); @State private var stableID = UUID().uuidString.lowercased()
    @State private var doorsAt = Date().addingTimeInterval(82800); @State private var addDoorsAt = false
    @State private var agenda = ""; @State private var contact = ""; @State private var terms = ""
    @State private var customerName = ""; @State private var invoiceNote = ""; @State private var invoiceDueAt = Date().addingTimeInterval(7 * 86400); @State private var invoiceItems: [String: Int] = [:]
    @State private var photo: PhotosPickerItem?; @State private var photoData: Data?; @State private var busy = false; @State private var message: String?
    @State private var scanning = false
    @State private var initialized = false
    @State private var baseline: [String] = []
    @State private var confirmDiscard = false
    private var snapshot: [String] { [name, description, category, ticketLabel, amount, currency, address, hours, String(available), selectedPage, capacity, String(date.timeIntervalSince1970), String(doorsAt.timeIntervalSince1970), String(addDoorsAt), agenda, contact, terms, photoData == nil ? "" : "photo", customerName, invoiceNote, String(invoiceDueAt.timeIntervalSince1970), invoiceItems.description] }
    private var hasEdits: Bool { initialized && ["product", "store", "event", "invoice"].contains(kind) && baseline != snapshot }
    private func requestClose() { guard !busy else { return }; if hasEdits { confirmDiscard = true } else { dismiss() } }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if ["product", "event"].contains(kind) {
                        if kind == "event" {
                            if pages.isEmpty { Text("Créez d’abord une page dans Business pour organiser un événement.").foregroundStyle(.red) }
                            Picker("Organisé par", selection: $selectedPage) { ForEach(pages) { Text($0.string("name")).tag($0.id) } }
                        }
                        field(kind == "event" ? "Nom de l’événement" : "Nom de l’article", $name)
                        TextField("Description", text: $description, axis: .vertical).lineLimit(3...8).textFieldStyle(.roundedBorder)
                    }
                    if ["product", "event"].contains(kind) {
                        if let photoData, let preview = UIImage(data: photoData) { Image(uiImage: preview).resizable().scaledToFill().frame(maxWidth: .infinity).frame(height: 180).clipped().clipShape(RoundedRectangle(cornerRadius: 16)).accessibilityLabel(kind == "event" ? "Affiche de l’événement sélectionnée" : "Photo de l’article sélectionnée") }
                        else if !product.string(kind == "event" ? "posterUrl" : "imageUrl").isEmpty { CommercePhoto(url: product.string(kind == "event" ? "posterUrl" : "imageUrl"), size: 140, symbol: "photo") }
                        PhotosPicker(selection: $photo, matching: .images) { Label(photoData == nil ? (kind == "event" ? "Ajouter l’affiche" : "Choisir la photo") : (kind == "event" ? "Affiche prête · changer" : "Photo prête · changer"), systemImage: "photo") }.disabled(busy)
                        field(kind == "event" ? "Catégorie · concert, sport, formation…" : "Rubrique · plats, desserts, services…", $category)
                        if kind == "product" {
                            field("Prix · \(currency)", $amount).keyboardType(.decimalPad)
                            Picker("Devise", selection: $currency) { ForEach(["XAF", "XOF", "EUR", "USD", "CDF"], id: \.self) { Text($0) } }.pickerStyle(.segmented)
                        }
                    }
                    if ["store", "event"].contains(kind) { field(kind == "event" ? "Lieu et adresse" : "Adresse de l’établissement", $address) }
                    if kind == "store" { field("Horaires d’ouverture", $hours) }
                    if ["store", "product"].contains(kind) { Toggle(kind == "store" ? "Publier dans Marketplace" : "Disponible", isOn: $available) }
                    if kind == "event" {
                        DatePicker("Date et heure", selection: $date, in: Date()..., displayedComponents: [.date, .hourAndMinute])
                        Toggle("Ajouter l’heure d’ouverture", isOn: $addDoorsAt).tint(Color.whappyBlue)
                        if addDoorsAt { DatePicker("Ouverture", selection: $doorsAt, in: Date()...date, displayedComponents: [.date, .hourAndMinute]) }
                        field("Nombre de places", $capacity).keyboardType(.numberPad)
                        field("Nom du billet · Accès général, VIP…", $ticketLabel)
                        TextField("Programme · facultatif", text: $agenda, axis: .vertical).lineLimit(3...8).textFieldStyle(.roundedBorder)
                        field("Contact organisateur · facultatif", $contact)
                        TextField("Consignes d’accès · facultatif", text: $terms, axis: .vertical).lineLimit(2...6).textFieldStyle(.roundedBorder)
                        Label("Un QR unique est généré pour chaque réservation. Le paiement sera disponible lorsque Mobile Money sera configuré.", systemImage: "qrcode").font(.caption).foregroundStyle(.green)
                    }
                    if kind == "invoice" {
                        field("Client ou entreprise", $customerName)
                        TextField("Note · facultative", text: $invoiceNote, axis: .vertical).lineLimit(2...5).textFieldStyle(.roundedBorder)
                        DatePicker("Échéance", selection: $invoiceDueAt, in: Date().addingTimeInterval(60)..., displayedComponents: .date)
                        Text("Articles facturés").font(.headline)
                        ForEach(invoiceProducts.filter { $0.bool("available") }) { item in
                            let quantity = invoiceItems[item.string("productId"), default: 0]
                            HStack {
                                VStack(alignment: .leading) { Text(item.string("name")).font(.subheadline.weight(.semibold)); Text(item.price).font(.caption).foregroundStyle(.secondary) }
                                Spacer()
                                Button { if quantity <= 1 { invoiceItems.removeValue(forKey: item.string("productId")) } else { invoiceItems[item.string("productId")] = quantity - 1 } } label: { Image(systemName: "minus.circle") }.disabled(quantity == 0 || busy)
                                Text("\(quantity)").monospacedDigit().frame(minWidth: 22)
                                Button { invoiceItems[item.string("productId")] = min(999, quantity + 1) } label: { Image(systemName: "plus.circle") }.disabled(busy)
                            }.padding(12).background(Color.whappyBlue.opacity(0.06), in: RoundedRectangle(cornerRadius: 14))
                        }
                        Text("Une relance sera préparée pour validation : WAPI ne l’envoie et n’encaisse jamais automatiquement.").font(.caption).foregroundStyle(.secondary)
                    }
                    if kind == "check" {
                        Button { scanning = true } label: { Label("Scanner le QR du billet", systemImage: "qrcode.viewfinder") }
                        field("Code Ticketbulk", $name)
                        Button("Coller un code") { name = UIPasteboard.general.string ?? "" }
                        Text("La validation consomme une entrée. Un billet déjà validé ne peut pas entrer une seconde fois.").foregroundStyle(.secondary)
                    }
                    if kind == "detail" { CommercePhoto(url: product.string("imageUrl"), size: 260, symbol: "fork.knife"); Text(product.string("description")); Text(product.price).font(.title2.bold()).foregroundStyle(Color.whappyBlue) }
                    if let message { Text(message).foregroundStyle(message.hasPrefix("Entrée validée") ? .green : .red) }
                }.padding(22).disabled(busy)
            }
            .navigationTitle(kind == "event" ? "Créer un événement" : kind == "store" ? "Votre vitrine" : kind == "invoice" ? "Nouvelle facture" : kind == "check" ? "Contrôle d’entrée" : kind == "detail" ? product.string("name") : "Article du catalogue")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Fermer", action: requestClose).disabled(busy) } }
            .safeAreaInset(edge: .bottom) {
                if kind != "detail" { Button(busy ? "Enregistrement…" : kind == "check" ? "Valider l’entrée" : kind == "event" ? "Publier l’événement" : kind == "invoice" ? "Émettre la facture" : "Enregistrer") { Task { await save() } }
                    .buttonStyle(.borderedProminent).controlSize(.large).frame(maxWidth: .infinity).padding().background(.bar).disabled(busy || (kind == "event" && selectedPage.isEmpty) || (kind == "invoice" && (customerName.trimmingCharacters(in: .whitespacesAndNewlines).count < 2 || invoiceItems.isEmpty))) }
            }
        }
        .interactiveDismissDisabled(busy || hasEdits)
        .confirmationDialog("Quitter sans enregistrer ?", isPresented: $confirmDiscard, titleVisibility: .visible) {
            Button("Abandonner", role: .destructive) { dismiss() }
            Button("Continuer à modifier", role: .cancel) {}
        } message: { Text("Vos modifications n’ont pas encore été enregistrées. Continuez pour les conserver.") }
        .onAppear {
            guard !initialized else { return } // Gallery/scanner return must not reset the form.
            name = product.string("name"); description = product.string("description"); category = product.string("category").isEmpty ? (kind == "event" ? "Culture · spectacle" : "Menu") : product.string("category"); ticketLabel = product.string("ticketLabel").isEmpty ? "Accès général" : product.string("ticketLabel"); currency = product.string("currency").isEmpty ? "XAF" : product.string("currency"); amount = product.id.isEmpty ? "" : WapiCommercePrice.input(product.number("priceMinor"), currency); address = page.string("address"); hours = page.string("hours"); available = kind == "store" ? page.bool("published") : product.id.isEmpty || product.bool("available"); selectedPage = pages.first?.id ?? ""; if !product.string("productId").isEmpty { stableID = product.string("productId") }
            baseline = snapshot; initialized = true
        }
        .onChange(of: photo) { _, item in
            guard let item, !busy else { return }
            busy = true
            Task {
            defer { busy = false }
            do {
                guard let data = try await item.loadTransferable(type: Data.self), let image = UIImage(data: data) else { throw NSError(domain: "WAPI", code: 1, userInfo: [NSLocalizedDescriptionKey: "Photo illisible."]) }
                let factor = min(1, 1400 / max(image.size.width, image.size.height))
                let size = CGSize(width: image.size.width * factor, height: image.size.height * factor)
                let format = UIGraphicsImageRendererFormat(); format.scale = 1
                photoData = UIGraphicsImageRenderer(size: size, format: format).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }.jpegData(compressionQuality: 0.85)
            } catch { message = WapiCommerceModel.message(error) }
        } }
        .sheet(isPresented: $scanning) { WhappyScannerSheet { name = $0; scanning = false } }
    }
    private func field(_ title: String, _ value: Binding<String>) -> some View { TextField(title, text: value).textFieldStyle(.roundedBorder).autocorrectionDisabled() }
    @MainActor private func save() async {
        guard !busy else { return }
        busy = true; message = nil; defer { busy = false }
        do {
            if kind == "store" { _ = try await WapiCommerceModel.call("saveStorefront", ["pageId": page.id, "address": address, "hours": hours, "published": available]) }
            else if kind == "product" {
                guard let price = WapiCommercePrice.minor(amount, currency) else { message = "Saisissez un prix valide, par exemple 12,50 en EUR ou 5500 en XAF."; return }
                var input: [String: Any] = ["pageId": page.id, "productId": stableID, "name": name, "description": description, "category": category, "priceMinor": price, "currency": currency, "available": available]
                if let photoData { input["photoBase64"] = photoData.base64EncodedString() }
                _ = try await WapiCommerceModel.call("saveProduct", input)
            } else if kind == "event" { var input: [String: Any] = ["eventId": stableID, "pageId": selectedPage, "title": name, "description": description, "category": category, "ticketLabel": ticketLabel, "venue": address, "startsAt": Int64(date.timeIntervalSince1970 * 1000), "agenda": agenda, "contact": contact, "terms": terms, "capacity": Int(capacity) ?? 0]; if addDoorsAt { input["doorsAt"] = Int64(doorsAt.timeIntervalSince1970 * 1000) }; if let photoData { input["posterBase64"] = photoData.base64EncodedString() }; _ = try await WapiCommerceModel.call("createEvent", input) }
            else if kind == "invoice" { _ = try await WapiCommerceModel.call("createInvoice", ["invoiceId": stableID, "pageId": page.id, "customerName": customerName, "note": invoiceNote, "dueAt": Int64(invoiceDueAt.timeIntervalSince1970 * 1000), "items": invoiceItems.map { ["productId": $0.key, "quantity": $0.value] }]) }
            else if kind == "check" { let result = try await WapiCommerceModel.call("checkTicket", ["code": name, "eventId": checkEventID]); message = result["status"] as? String == "accepted" ? "Entrée validée · \(result["title"] ?? "")" : "Billet déjà utilisé · entrée refusée"; return }
            saved(); dismiss()
        } catch { message = WapiCommerceModel.message(error) }
    }
}

private struct WapiTicketIOS: View {
    let ticket: WapiCommerceRecord
    let cancelled: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var confirmCancel = false
    @State private var busy = false
    @State private var error: String?
    private var valid: Bool { ticket.string("status") == "issued" }
    private var qr: UIImage? {
        let filter = CIFilter.qrCodeGenerator(); filter.message = Data(ticket.string("code").utf8)
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 8, y: 8)), let cg = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    HStack(spacing: 10) { Image(systemName: "ticket.fill").foregroundStyle(.cyan); VStack(alignment: .leading, spacing: 2) { Text("TICKETBULK").font(.headline.weight(.heavy)); Text("Billet personnel · QR sécurisé").font(.caption).foregroundStyle(.white.opacity(0.72)) }; Spacer() }
                        .padding(16).foregroundStyle(.white).background(Color(red: 0.03, green: 0.16, blue: 0.28), in: RoundedRectangle(cornerRadius: 18))
                    Text(ticket.string("title")).font(.title.bold())
                    Text(ticket.string("ticketLabel").isEmpty ? "Accès général" : ticket.string("ticketLabel")).font(.subheadline.bold()).foregroundStyle(Color.whappyBlue)
                    Text(ticket.date.formatted(date: .abbreviated, time: .shortened)); Text(ticket.string("venue"))
                    if !valid { Text(ticket.string("status") == "used" ? "Billet déjà utilisé" : "Réservation annulée").foregroundStyle(.secondary) }
                    else if let qr { Image(uiImage: qr).interpolation(.none).resizable().scaledToFit().frame(width: 230, height: 230).padding(15).background(.white, in: RoundedRectangle(cornerRadius: 20)).overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.gray.opacity(0.18))) }
                    if valid {
                        Text("Ne partagez pas ce QR : il donne accès à votre place.").font(.caption).foregroundStyle(.secondary)
                        Button("Copier mon code") { UIPasteboard.general.string = ticket.string("code") }
                        if ticket.date > Date() { Button("Annuler ma réservation", role: .destructive) { confirmCancel = true }.disabled(busy) }
                    }
                    if busy { ProgressView() }
                    if let error { Text(error).foregroundStyle(.red) }
                }.padding(24)
            }.toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fermer") { dismiss() }.disabled(busy) } }
        }
        .interactiveDismissDisabled(busy)
        .confirmationDialog("Libérer votre place ?", isPresented: $confirmCancel, titleVisibility: .visible) {
            Button("Annuler la réservation", role: .destructive) { Task {
                busy = true; error = nil; defer { busy = false }
                do { _ = try await WapiCommerceModel.call("cancelTicket", ["code": ticket.string("code")]); cancelled(); dismiss() }
                catch { self.error = WapiCommerceModel.message(error) }
            } }
            Button("Garder le billet", role: .cancel) {}
        } message: { Text("Votre billet sera annulé. Vous pourrez réserver à nouveau s’il reste des places.") }
    }
}
