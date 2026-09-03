package com.whappy.chat

import android.Manifest
import android.content.Intent
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Calendar
import java.util.UUID

internal typealias CommerceRecord = Map<String, Any?>
internal typealias WapiCommerceGateway = suspend (String, CommerceRecord) -> CommerceRecord
// Save only the small editor snapshot, never catalog lists, bitmaps or base64 data.
private val commerceRecordSaver = mapSaver<CommerceRecord>(
    save = { record -> record.filterValues { it is String || it is Boolean || it is Number }.mapValues { it.value!! } },
    restore = { it }
)
private fun CommerceRecord.string(key: String) = this[key] as? String ?: ""
private fun CommerceRecord.number(key: String) = (this[key] as? Number)?.toLong() ?: 0L
@Suppress("UNCHECKED_CAST")
private fun CommerceRecord.records(key: String) = (this[key] as? List<*>)?.mapNotNull { it as? CommerceRecord }.orEmpty()
@Suppress("UNCHECKED_CAST")
private suspend fun commerce(action: String, data: CommerceRecord = emptyMap()): CommerceRecord =
    FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("wapiCommerce")
        .call(data + ("action" to action)).await().data as? CommerceRecord ?: error("Réponse WAPI invalide.")
private fun commerceError(error: Throwable) = when ((error as? FirebaseFunctionsException)?.code) {
    FirebaseFunctionsException.Code.NOT_FOUND -> "Ce service n’est pas encore disponible sur le serveur. Aucune modification n’a été enregistrée."
    FirebaseFunctionsException.Code.UNAVAILABLE -> "Connexion indisponible. Réessayez : vos informations restent à l’écran."
    FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Connectez-vous à WAPI pour continuer."
    else -> error.message ?: "L’action n’a pas abouti. Réessayez."
}
private fun dateLabel(value: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(java.util.Date(value))

/** Full-screen commerce dialogs own their window; the activity's icon tint is not inherited reliably. */
@Composable
private fun CommerceSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (previousStatus != null) controller?.isAppearanceLightStatusBars = previousStatus
            if (previousNavigation != null) controller?.isAppearanceLightNavigationBars = previousNavigation
        }
    }
}

@Composable
internal fun WapiCommerceLaunchers(onContact: (WhappyBusinessPage) -> Unit, onOpenBusiness: (() -> Unit)? = null) {
    var module by rememberSaveable { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("directory" to "Boutiques & menus", "events" to "Ticketbulk").forEach { (route, title) ->
            Card(onClick = { module = route }, modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (route == "events") Icons.Rounded.ConfirmationNumber else Icons.Rounded.Storefront, null, tint = WhappyBlue)
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(if (route == "events") "Événements · mes billets" else "Adresses · catalogues", fontSize = 12.sp, color = WhappyMuted)
                }
            }
        }
    }
    module?.let { WapiCommerceApp(it, { module = null }, onContact, onOpenBusiness = onOpenBusiness) }
}

/** Each mini-app owns a full-screen route, not a form placed over the marketplace. */
@Composable
internal fun WapiCommerceApp(initialRoute: String, onClose: () -> Unit, onContact: (WhappyBusinessPage) -> Unit, onOpenBusiness: (() -> Unit)? = null, gateway: WapiCommerceGateway = ::commerce) {
    var route by rememberSaveable { mutableStateOf(initialRoute) }
    var pageId by rememberSaveable { mutableStateOf("") }
    var editorPage by rememberSaveable(stateSaver = commerceRecordSaver) { mutableStateOf<CommerceRecord>(emptyMap()) }
    var response by remember { mutableStateOf<CommerceRecord>(emptyMap()) }
    var ownPages by remember { mutableStateOf(emptyList<CommerceRecord>()) }
    var search by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var businessGate by rememberSaveable { mutableStateOf(false) }
    var editingProduct by rememberSaveable(stateSaver = commerceRecordSaver) { mutableStateOf<CommerceRecord>(emptyMap()) }
    var ticket by remember { mutableStateOf<CommerceRecord?>(null) }
    var eventDetails by remember { mutableStateOf<CommerceRecord?>(null) }
    var checkEventId by rememberSaveable { mutableStateOf("") }
    var eventSearch by rememberSaveable { mutableStateOf("") }
    var eventCategory by rememberSaveable { mutableStateOf("") }
    var eventAvailabilityOnly by rememberSaveable { mutableStateOf(false) }
    var catalogueSearch by rememberSaveable(pageId) { mutableStateOf("") }
    var catalogueCategory by rememberSaveable(pageId) { mutableStateOf("") }
    var availableOnly by rememberSaveable(pageId) { mutableStateOf(false) }
    val generation = remember { WapiRequestGeneration() }
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    fun openEventEditor() { if (ownPages.isEmpty()) businessGate = true else editor = "event" }
    fun back() { if (route == initialRoute) onClose() else { route = initialRoute; response = emptyMap() } }
    suspend fun load(append: Boolean = false) {
        val request = generation.begin()
        val requestedRoute = route
        busy = true; error = null
        try {
            val result = gateway(requestedRoute, mapOf("pageId" to pageId, "search" to search, "city" to city, "cursor" to if (append) response["nextCursor"] else null))
            if (!generation.accepts(request) || requestedRoute != route) return
            @Suppress("UNCHECKED_CAST")
            if (requestedRoute == "storefront" && editor == null) editorPage = result["page"] as? CommerceRecord ?: emptyMap()
            response = if (append) {
                val key = if (requestedRoute == "events") "events" else "stores"
                result + (key to (response.records(key) + result.records(key)).distinctBy { it.string("id") })
            } else result
        } catch (failure: CancellationException) { throw failure }
        catch (failure: Throwable) { if (generation.accepts(request)) error = commerceError(failure) }
        finally { if (generation.accepts(request)) busy = false }
    }
    LaunchedEffect(Unit) { try { ownPages = gateway("ownPages", emptyMap()).records("pages") } catch (e: Exception) { error = commerceError(e) } }
    LaunchedEffect(route, pageId, search, city, revision) {
        generation.begin() // Invalidate immediately, including the debounce interval.
        busy = true
        response = emptyMap()
        if (route == "directory" && (search.isNotBlank() || city.isNotBlank())) delay(300)
        load()
    }
    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CommerceSystemBars()
        val ticketbulk = route == "events" || route == "myTickets"
        Surface(Modifier.fillMaxSize(), color = if (ticketbulk) Color(0xFFF1F6F8) else Color(0xFFF5F7FA)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().background(if (ticketbulk) Color(0xFF061D2D) else Color.White).padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = if (ticketbulk) Color.White else Color.Unspecified) }
                    Column(Modifier.weight(1f)) {
                        Text(when(route) { "events", "myTickets" -> "TicketBulk"; "storefront" -> "La boutique"; "billing" -> "Facturation Business"; else -> "Marketplace" }, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = if (ticketbulk) Color.White else Color.Unspecified)
                        Text(when(route) { "events" -> "Découvrir"; "myTickets" -> "Mes accès"; "billing" -> "Factures, créances et relances à valider"; else -> "Les établissements de WAPI" }, color = if (ticketbulk) Color(0xFF9AC4D9) else WhappyMuted, fontSize = 12.sp)
                    }
                    IconButton(enabled = !busy, onClick = { revision++ }) { Icon(Icons.Rounded.Refresh, "Actualiser", tint = if (ticketbulk) Color.White else Color.Unspecified) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = if (ticketbulk) 12.dp else 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    error?.let { value -> item { Text(value, color = MaterialTheme.colorScheme.error); TextButton(onClick = { revision++ }) { Text("Réessayer") } } }
                    when(route) {
                        "directory" -> {
                            item { Text("Trouvez votre prochaine adresse", fontWeight = FontWeight.Bold, fontSize = 24.sp) }
                            item { OutlinedTextField(search, { search = it.take(100) }, Modifier.fillMaxWidth(), placeholder = { Text("Restaurant, boutique, activité…") }, singleLine = true, shape = RoundedCornerShape(16.dp), leadingIcon = { Icon(Icons.Rounded.Search, null) }) }
                            item { OutlinedTextField(city, { city = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Ville · toutes si vide") }, singleLine = true, shape = RoundedCornerShape(16.dp)) }
                            if (ownPages.isNotEmpty()) item {
                                Text("MES VITRINES", color = WhappyMuted, fontSize = 11.sp)
                                ownPages.forEach { page -> TextButton(onClick = { pageId = page.string("id"); route = "storefront" }) { Icon(Icons.Rounded.Edit, null); Text("  ${page.string("name")}") } }
                            }
                            items(response.records("stores"), key = { it.string("id") }) { page ->
                                Card(onClick = { pageId = page.string("id"); route = "storefront" }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        UserAvatar(page.string("logoUrl"), page.string("name"), 64.dp, shape = RoundedCornerShape(16.dp))
                                        Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(page.string("name"), fontWeight = FontWeight.Bold); Text(page.string("category"), color = WhappyBlue); Text(page.string("city"), fontSize = 12.sp, color = WhappyMuted) }
                                        Icon(Icons.Rounded.ChevronRight, "Voir le catalogue")
                                    }
                                }
                            }
                            if (!busy && error == null && response.records("stores").isEmpty()) item { Text("Aucun établissement dans ces résultats. Vous pouvez changer de ville ou poursuivre la recherche.", color = WhappyMuted) }
                        }
                        "storefront" -> {
                            @Suppress("UNCHECKED_CAST") val page = response["page"] as? CommerceRecord ?: emptyMap()
                            val owner = response["owner"] == true
                            item {
                                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        UserAvatar(page.string("logoUrl"), page.string("name"), 64.dp, shape = RoundedCornerShape(18.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(page.string("name"), fontWeight = FontWeight.Bold, fontSize = 23.sp)
                                            Text("${page.string("category")} · ${page.string("city")}", color = WhappyBlue, fontSize = 13.sp)
                                        }
                                    }
                                    if (page.string("bio").isNotBlank()) Text(page.string("bio"))
                                    if (page.string("address").isNotBlank()) Text(page.string("address"), color = WhappyMuted)
                                    if (page.string("hours").isNotBlank()) Text(page.string("hours"), color = WhappyMuted)
                                    if (owner) {
                                        Text(if (page["published"] == true) "Vitrine visible dans Marketplace" else "Vitrine privée · à publier", color = WhappyBlue)
                                        Row {
                                            TextButton(onClick = { editor = "store" }) { Text("Régler la vitrine") }
                                            TextButton(onClick = { editingProduct = emptyMap(); editor = "product" }) { Text("Ajouter un article") }
                                            TextButton(onClick = { route = "billing" }) { Text("Facturation") }
                                        }
                                    } else if (page.isNotEmpty()) Button(onClick = {
                                        onClose(); onContact(WhappyBusinessPage(page.string("id"), page.string("name"), page.string("handle"), page.string("category"), page.string("bio"), page.string("city"), page.string("ownerId"), phone = page.string("phone"), logoUrl = page.string("logoUrl"), verified = page["verified"] == true))
                                    }) { Text("Écrire à l’établissement") }
                                }
                            }
                            item { OutlinedTextField(catalogueSearch, { catalogueSearch = it.take(100) }, Modifier.fillMaxWidth(), placeholder = { Text("Rechercher dans le catalogue") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp)) }
                            val allProducts = response.records("products")
                            val matchingProducts = allProducts.filter { WapiCatalogFilter.matches(it, catalogueSearch, availableOnly = availableOnly) }
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item { FilterChip(selected = catalogueCategory.isEmpty(), onClick = { catalogueCategory = "" }, label = { Text("Tout · ${matchingProducts.size}") }) }
                                    items(allProducts.map { WapiCatalogFilter.category(it) }.distinct().sorted()) { name ->
                                        FilterChip(selected = catalogueCategory == name, onClick = { catalogueCategory = if (catalogueCategory == name) "" else name }, label = { Text("$name · ${matchingProducts.count { WapiCatalogFilter.category(it) == name }}") })
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Disponibles uniquement", Modifier.weight(1f), color = WhappyMuted, fontSize = 13.sp)
                                    Switch(checked = availableOnly, onCheckedChange = { availableOnly = it }, modifier = Modifier.semantics { contentDescription = "Filtrer les articles disponibles" })
                                }
                            }
                            val categories = matchingProducts.filter { catalogueCategory.isEmpty() || WapiCatalogFilter.category(it) == catalogueCategory }.groupBy { WapiCatalogFilter.category(it) }
                            categories.forEach { (category, products) ->
                                item { Text(category, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                                items(products, key = { it.string("id") }) { product ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), onClick = { if (owner) { editingProduct = product; editor = "product" } else { editingProduct = product; editor = "detail" } }) {
                                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            UserAvatar(product.string("imageUrl"), product.string("name"), 82.dp, shape = RoundedCornerShape(14.dp))
                                            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                Text(product.string("name"), fontWeight = FontWeight.Bold)
                                                Text(product.string("description"), maxLines = 3, color = WhappyMuted, fontSize = 12.sp)
                                                Text(commercePrice(product.number("priceMinor"), product.string("currency")), color = WhappyBlue, fontWeight = FontWeight.Bold)
                                                if (product["available"] != true) Text("Indisponible", color = WhappyMuted, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            if (!busy && categories.isEmpty() && error == null) item {
                                Text(if (allProducts.isNotEmpty()) "Aucun article ne correspond à ces filtres." else if (owner) "Ajoutez vos plats, produits ou prestations : ils apparaîtront ici." else "Cet établissement prépare son catalogue.", color = WhappyMuted)
                                if (allProducts.isNotEmpty()) TextButton(onClick = { catalogueSearch = ""; catalogueCategory = ""; availableOnly = false }) { Text("Réinitialiser les filtres") }
                            }
                        }
                        "events" -> {
                            item {
                                Surface(color = Color(0xFF082A45), shape = RoundedCornerShape(24.dp)) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.ConfirmationNumber, null, tint = Color(0xFF7CE5FF), modifier = Modifier.size(30.dp))
                                            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                                                Text("TICKETBULK", color = Color.White, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                                                Text("Vos événements, billets et entrées au même endroit", color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
                                            }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Button(onClick = { route = "myTickets" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .16f))) { Text("Mes billets") }
                                            Button(onClick = ::openEventEditor, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0BA6D9))) { Text("Créer") }
                                        }
                                    }
                                }
                            }
                            val allEvents = response.records("events")
                            val eventCategories = allEvents.map { it.string("category").ifBlank { "Événement" } }.distinct().sorted()
                            val visibleEvents = allEvents.filter { event ->
                                val remaining = (event.number("capacity") - event.number("reserved")).coerceAtLeast(0)
                                val haystack = "${event.string("title")} ${event.string("description")} ${event.string("venue")} ${event.string("category")} ${event.string("organizer")}".lowercase()
                                (eventSearch.isBlank() || haystack.contains(eventSearch.trim().lowercase())) &&
                                    (eventCategory.isBlank() || event.string("category").ifBlank { "Événement" } == eventCategory) &&
                                    (!eventAvailabilityOnly || remaining > 0L)
                            }
                            item { OutlinedTextField(eventSearch, { eventSearch = it.take(100) }, Modifier.fillMaxWidth(), placeholder = { Text("Rechercher un concert, une formation, un lieu…") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp)) }
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item { FilterChip(selected = eventCategory.isBlank(), onClick = { eventCategory = "" }, label = { Text("Tous · ${visibleEvents.size}") }) }
                                    items(eventCategories) { label -> FilterChip(selected = eventCategory == label, onClick = { eventCategory = if (eventCategory == label) "" else label }, label = { Text(label) }) }
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Places disponibles uniquement", color = WhappyMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Switch(eventAvailabilityOnly, { eventAvailabilityOnly = it })
                                }
                            }
                            items(visibleEvents, key = { it.string("id") }) { event ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(23.dp)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            UserAvatar(event.string("posterUrl"), event.string("title"), 76.dp, shape = RoundedCornerShape(18.dp))
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(event.string("category").ifBlank { "ÉVÉNEMENT" }.uppercase(), color = WhappyBlue, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = .7.sp)
                                                Text(event.string("title"), fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                                Text(dateLabel(event.number("startsAt")), color = WhappyMuted, fontSize = 12.sp)
                                            }
                                        }
                                        Text("${event.string("venue")} · ${event.string("organizer")}", color = WhappyMuted, fontSize = 13.sp)
                                        if (event.string("description").isNotBlank()) Text(event.string("description"), maxLines = 3, fontSize = 13.sp)
                                        TextButton(onClick = { eventDetails = event }, modifier = Modifier.align(Alignment.Start)) { Icon(Icons.Rounded.Info, null, modifier = Modifier.size(17.dp)); Text(" Détails de l’événement") }
                                        val remaining = (event.number("capacity") - event.number("reserved")).coerceAtLeast(0)
                                        val occupancy = if (event.number("capacity") <= 0) 0f else event.number("reserved").toFloat() / event.number("capacity").toFloat()
                                        LinearProgressIndicator(occupancy.coerceIn(0f, 1f), Modifier.fillMaxWidth().height(6.dp), color = if (remaining == 0L) Color(0xFFC34040) else Color(0xFF0AA87C), trackColor = Color(0xFFE6EDF1))
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${event.string("ticketLabel").ifBlank { "Accès général" }} · gratuit", color = WhappyBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                            Text("$remaining / ${event.number("capacity")} places", color = WhappyMuted, fontSize = 12.sp)
                                        }
                                        if (event.string("ownerId") == uid) {
                                            Surface(color = Color(0xFFEAF8F4), shape = RoundedCornerShape(14.dp)) { Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Rounded.Groups, null, tint = Color(0xFF087D62)); Text(" ${event.number("reserved")} billets · ${event.number("checkedIn")} entrées", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                                TextButton(onClick = { checkEventId = event.string("id"); editor = "check" }) { Text("Scanner") }
                                            } }
                                            if (event.string("status") == "published") TextButton(enabled = !busy, onClick = {
                                                scope.launch { busy = true; try { gateway("closeEvent", mapOf("eventId" to event.string("id"))); revision++ } catch (e: Exception) { error = commerceError(e) } finally { busy = false } }
                                            }) { Text("Fermer les inscriptions") }
                                        }
                                        val hasTicket = event.string("myTicketStatus") in listOf("issued", "used")
                                        val reservable = event.string("status") == "published" && event.number("startsAt") > System.currentTimeMillis() && event.number("reserved") < event.number("capacity")
                                        if (hasTicket || reservable) Button(enabled = !busy, onClick = {
                                            scope.launch { busy = true; try { @Suppress("UNCHECKED_CAST") val result = gateway("reserveTicket", mapOf("eventId" to event.string("id")))["ticket"] as? CommerceRecord; ticket = result; revision++ } catch (e: Exception) { error = commerceError(e) } finally { busy = false } }
                                        }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(if (hasTicket) Icons.Rounded.QrCode2 else Icons.Rounded.ConfirmationNumber, null); Text(if (hasTicket) "  Afficher mon billet" else "  Réserver mon billet") }
                                        else Text(if (event.number("reserved") >= event.number("capacity")) "Complet" else "Inscriptions fermées", color = WhappyMuted)
                                    }
                                }
                            }
                            if (!busy && error == null && allEvents.isEmpty()) item { Text("Pas d’événement dans cette sélection. Créez le vôtre avec votre page Business.") }
                            else if (!busy && error == null && visibleEvents.isEmpty()) item { Text("Aucun événement ne correspond à ces filtres.", color = WhappyMuted); TextButton(onClick = { eventSearch = ""; eventCategory = ""; eventAvailabilityOnly = false }) { Text("Réinitialiser") } }
                        }
                        "myTickets" -> {
                            items(response.records("tickets"), key = { it.string("id") }) { item ->
                                Card(onClick = { ticket = item }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        UserAvatar(item.string("posterUrl"), item.string("title"), 58.dp, shape = RoundedCornerShape(15.dp))
                                        Column(Modifier.weight(1f)) { Text(item.string("title"), fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(dateLabel(item.number("startsAt")), color = WhappyMuted, fontSize = 12.sp); Text(when(item.string("status")) { "used" -> "Billet déjà validé"; "cancelled" -> "Réservation annulée"; else -> "QR prêt à présenter" }, color = WhappyBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                                        Icon(Icons.Rounded.QrCode2, null, tint = WhappyBlue)
                                    }
                                }
                            }
                            if (!busy && response.records("tickets").isEmpty() && error == null) item { Text("Vos billets réservés seront conservés ici.", color = WhappyMuted) }
                        }
                        "billing" -> {
                            val summary = response["summary"] as? CommerceRecord ?: emptyMap()
                            item {
                                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Suivi des créances", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text("À encaisser : ${commercePrice(summary.number("outstandingMinor"), "XAF")}", color = WhappyBlue, fontWeight = FontWeight.Bold)
                                    Text("Échues : ${commercePrice(summary.number("overdueMinor"), "XAF")}", color = MaterialTheme.colorScheme.error)
                                    Text("Les encaissements ne sont jamais effectués automatiquement par WAPI.", color = WhappyMuted, fontSize = 12.sp)
                                    Button(onClick = { editor = "invoice" }, enabled = response.records("products").isNotEmpty()) { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, null); Text(" Créer une facture") }
                                    if (response.records("products").isEmpty()) Text("Ajoutez d’abord un article à votre catalogue pour créer une facture exacte.", color = WhappyMuted, fontSize = 12.sp)
                                }
                            }
                            items(response.records("invoices"), key = { it.string("id") }) { invoice ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(invoice.string("customerName"), fontWeight = FontWeight.Bold); Text(invoice.string("number"), color = WhappyMuted, fontSize = 12.sp) }; Text(commercePrice(invoice.number("balanceMinor"), invoice.string("currency")), color = WhappyBlue, fontWeight = FontWeight.Bold) }
                                        Text(if (invoice.string("status") == "overdue") "Échéance dépassée" else "Échéance : ${dateLabel(invoice.number("dueAt"))}", color = if (invoice.string("status") == "overdue") MaterialTheme.colorScheme.error else WhappyMuted, fontSize = 12.sp)
                                        TextButton(enabled = !busy, onClick = { scope.launch { busy = true; try { val reminder = gateway("prepareInvoiceReminder", mapOf("invoiceId" to invoice.string("id"), "tone" to "courtois"))["reminder"] as? CommerceRecord; val draft = reminder?.string("message").orEmpty(); if (draft.isNotBlank()) error = "Relance prête à relire : $draft" } catch (e: Exception) { error = commerceError(e) } finally { busy = false } } }) { Text("Préparer une relance") }
                                    }
                                }
                            }
                            if (!busy && response.records("invoices").isEmpty() && error == null) item { Text("Aucune facture émise pour cette page. Les factures resteront ici, avec leurs échéances.", color = WhappyMuted) }
                        }
                    }
                    if (response["nextCursor"] != null) item { OutlinedButton(enabled = !busy, onClick = { scope.launch { load(true) } }) { Text("Charger la suite") } }
                    item { Spacer(Modifier.height(16.dp)) }
                }
                if (ticketbulk) NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    NavigationBarItem(selected = route == "events", onClick = { if (route != "events") { route = "events"; response = emptyMap() } }, icon = { Icon(Icons.Rounded.Explore, null) }, label = { Text("Découvrir") })
                    NavigationBarItem(selected = route == "myTickets", onClick = { if (route != "myTickets") { route = "myTickets"; response = emptyMap() } }, icon = { Icon(Icons.Rounded.ConfirmationNumber, null) }, label = { Text("Mes billets") })
                    NavigationBarItem(selected = false, onClick = ::openEventEditor, icon = { Icon(Icons.Rounded.AddCircle, null) }, label = { Text("Organiser") })
                }
            }
        }
        editor?.let { kind ->
            WapiCommerceEditor(kind, pageId, editorPage, editingProduct, response.records("products"), ownPages, { editor = null }, { editor = null; revision++ }, gateway, checkEventId)
        }
        ticket?.let { WapiTicketCard(it, gateway, { ticket = null; revision++ }) { ticket = null } }
        eventDetails?.let { event ->
            WapiEventDetailCard(event, onClose = { eventDetails = null }, onReserve = {
                scope.launch {
                    busy = true; error = null
                    try { @Suppress("UNCHECKED_CAST") val result = gateway("reserveTicket", mapOf("eventId" to event.string("id")))["ticket"] as? CommerceRecord; ticket = result; eventDetails = null; revision++ }
                    catch (failure: Throwable) { error = commerceError(failure) }
                    finally { busy = false }
                }
            })
        }
        if (businessGate) AlertDialog(
            onDismissRequest = { businessGate = false },
            icon = { Icon(Icons.Rounded.BusinessCenter, null, tint = WhappyBlue) },
            title = { Text("Créez votre identité organisateur") },
            text = { Text("TicketBulk organise les événements depuis une page Business WAPI. Créez-la une seule fois : vos billets, contacts et statistiques resteront séparés de votre profil personnel.") },
            confirmButton = { TextButton(onClick = { businessGate = false; onClose(); onOpenBusiness?.invoke() }) { Text(if (onOpenBusiness != null) "Créer mon Business" else "Fermer") } },
            dismissButton = { TextButton(onClick = { businessGate = false }) { Text("Plus tard") } },
        )
    }
}

private fun commercePrice(amount: Long, currency: String): String {
    val value = if (currency in listOf("EUR", "USD")) amount / 100.0 else amount.toDouble()
    return java.text.NumberFormat.getNumberInstance().format(value) + " " + currency
}

/** Native event detail screen: discovery stays light while all practical information is one tap away. */
@Composable
private fun WapiEventDetailCard(event: CommerceRecord, onClose: () -> Unit, onReserve: () -> Unit) {
    val context = LocalContext.current
    val favorites = remember(context) { context.getSharedPreferences("ticketbulk_favorites", android.content.Context.MODE_PRIVATE) }
    val eventId = event.string("id")
    var saved by remember(eventId) { mutableStateOf(favorites.getBoolean(eventId, false)) }
    val hasTicket = event.string("myTicketStatus") in listOf("issued", "used")
    val remaining = (event.number("capacity") - event.number("reserved")).coerceAtLeast(0)
    val reservable = event.string("status") == "published" && event.number("startsAt") > System.currentTimeMillis() && remaining > 0
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CommerceSystemBars()
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF1F6F8)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().background(Color(0xFF061D2D)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = Color.White) }
                    Text("Détails", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        saved = !saved; favorites.edit().putBoolean(eventId, saved).apply()
                    }) { Icon(if (saved) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (saved) "Retirer des favoris" else "Ajouter aux favoris", tint = if (saved) Color(0xFFFFC13B) else Color.White) }
                    IconButton(onClick = {
                        val text = "${event.string("title")}\n${dateLabel(event.number("startsAt"))}\n${event.string("venue")}\nDécouvrir sur WAPI TicketBulk"
                        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Partager l’événement")) }
                    }) { Icon(Icons.Rounded.Share, "Partager", tint = Color.White) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    UserAvatar(event.string("posterUrl"), event.string("title"), 180.dp, shape = RoundedCornerShape(28.dp))
                    Text(event.string("category").ifBlank { "ÉVÉNEMENT" }.uppercase(), color = WhappyBlue, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Text(event.string("title"), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.CalendarMonth, null, tint = WhappyBlue); Text("  ${dateLabel(event.number("startsAt"))}", fontWeight = FontWeight.SemiBold) }
                        if (event.number("doorsAt") > 0L) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.DoorFront, null, tint = WhappyBlue); Text("  Ouverture : ${dateLabel(event.number("doorsAt"))}") }
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.LocationOn, null, tint = WhappyBlue); Text("  ${event.string("venue")}") }
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Business, null, tint = WhappyBlue); Text("  ${event.string("organizer")}") }
                    } }
                    if (event.string("description").isNotBlank()) SectionText("À propos", event.string("description"))
                    if (event.string("agenda").isNotBlank()) SectionText("Programme", event.string("agenda"))
                    if (event.string("terms").isNotBlank()) SectionText("Consignes d’accès", event.string("terms"))
                    if (event.string("contact").isNotBlank()) SectionText("Contact", event.string("contact"))
                    Surface(color = Color(0xFFE4F7F0), shape = RoundedCornerShape(18.dp)) { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ConfirmationNumber, null, tint = Color(0xFF087D62)); Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(event.string("ticketLabel").ifBlank { "Accès général" }, fontWeight = FontWeight.Bold); Text("Gratuit · QR personnel à présenter", color = Color(0xFF176451), fontSize = 12.sp) }; Text("$remaining places", color = Color(0xFF087D62), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } }
                }
                val action = when { hasTicket -> "BILLET DÉJÀ RÉSERVÉ"; reservable -> "RÉSERVER MON BILLET"; remaining == 0L -> "ÉVÉNEMENT COMPLET"; else -> "INSCRIPTIONS FERMÉES" }
                Button(enabled = reservable, onClick = onReserve, modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.QrCode2, null); Text("  $action", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SectionText(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(text, color = WhappyMuted, lineHeight = 20.sp) }
}

@Composable
private fun WapiCommerceEditor(kind: String, pageId: String, page: CommerceRecord, product: CommerceRecord, invoiceProducts: List<CommerceRecord>, pages: List<CommerceRecord>, close: () -> Unit, saved: () -> Unit, gateway: WapiCommerceGateway, checkEventId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var name by rememberSaveable { mutableStateOf(product.string("name")) }
    var description by rememberSaveable { mutableStateOf(product.string("description")) }
    var category by rememberSaveable { mutableStateOf(product.string("category").ifBlank { if (kind == "event") "Culture · spectacle" else "Menu" }) }
    var ticketLabel by rememberSaveable { mutableStateOf("Accès général") }
    var amount by rememberSaveable { mutableStateOf(if (product.isEmpty()) "" else WapiCommercePolicy.priceInput(product.number("priceMinor"), product.string("currency"))) }
    var currency by rememberSaveable { mutableStateOf(product.string("currency").ifBlank { "XAF" }) }
    var address by rememberSaveable { mutableStateOf(page.string("address")) }
    var hours by rememberSaveable { mutableStateOf(page.string("hours")) }
    var available by rememberSaveable { mutableStateOf(if (kind == "store") page["published"] == true else product["available"] != false) }
    var selectedPage by rememberSaveable { mutableStateOf(pages.firstOrNull()?.string("id").orEmpty()) }
    var capacity by rememberSaveable { mutableStateOf("100") }
    var date by rememberSaveable { mutableLongStateOf(System.currentTimeMillis() + 86_400_000L) }
    var doorsAt by rememberSaveable { mutableLongStateOf(0L) }
    var agenda by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var terms by rememberSaveable { mutableStateOf("") }
    var customerName by rememberSaveable { mutableStateOf("") }
    var invoiceNote by rememberSaveable { mutableStateOf("") }
    var invoiceDueAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis() + 7 * 86_400_000L) }
    var invoiceItems by remember { mutableStateOf(emptyMap<String, Int>()) }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var photoSelection by rememberSaveable { mutableIntStateOf(0) }
    var photo by remember { mutableStateOf<String?>(null) }
    val photoPreview by produceState<Bitmap?>(null, photo) {
        value = withContext(Dispatchers.IO) { photo?.let { Base64.decode(it, Base64.NO_WRAP).let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } } }
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var scan by remember { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    fun snapshot() = listOf(name, description, category, ticketLabel, amount, currency, address, hours, available.toString(), selectedPage, capacity, date.toString(), doorsAt.toString(), agenda, contact, terms, photoUri.orEmpty(), customerName, invoiceNote, invoiceDueAt.toString(), invoiceItems.toString())
    val baseline = rememberSaveable { snapshot() }
    fun requestClose() { if (!busy) { if (kind in listOf("product", "store", "event") && baseline != snapshot()) confirmDiscard = true else close() } }
    val stableId = rememberSaveable { product.string("productId").ifBlank { UUID.randomUUID().toString() } }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            photo = null; busy = true; message = null
            photoUri = uri.toString()
            photoSelection++ // Re-selecting the same URI must retry a failed decode.
        }
    }
    // Re-decode the granted gallery URI after recreation, without storing a large image in Bundle.
    LaunchedEffect(photoUri, photoSelection) {
        val uri = photoUri?.let(android.net.Uri::parse) ?: return@LaunchedEffect
        busy = true; photo = null
        try {
            photo = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1400).coerceAtLeast(1) }
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        val scale = minOf(1f, 1400f / maxOf(info.size.width, info.size.height))
                        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) } ?: error("Photo illisible")
                val out = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out); bitmap.recycle()
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { message = "La photo n’est plus accessible. Choisissez-la à nouveau ; votre saisie est conservée." }
        finally { busy = false }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> if (allowed) scan = true else message = "Autorisez la caméra pour lire les billets." }
    val qrPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                name = withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                    val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1600).coerceAtLeast(1) }
                    val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) } ?: error("Image illisible")
                    try {
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        val source = com.google.zxing.RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
                        com.google.zxing.qrcode.QRCodeReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))).text
                    } finally { bitmap.recycle() }
                }
            } catch (_: Exception) { message = "Aucun QR lisible dans cette image. Choisissez une photo nette du billet." }
            finally { busy = false }
        }
    }
    fun save() {
        if (busy || (photoUri != null && photo == null)) return
        busy = true
        scope.launch { message = null; try {
            when (kind) {
                "store" -> gateway("saveStorefront", mapOf("pageId" to pageId, "address" to address, "hours" to hours, "published" to available))
                "product" -> gateway("saveProduct", mapOf("pageId" to pageId, "productId" to stableId, "name" to name, "description" to description, "category" to category, "priceMinor" to (WapiCommercePolicy.priceMinor(amount, currency) ?: error("Saisissez un prix valide, par exemple 12,50 en EUR ou 5500 en XAF.")), "currency" to currency, "available" to available, "photoBase64" to photo))
                "event" -> gateway("createEvent", mapOf("eventId" to stableId, "pageId" to selectedPage, "title" to name, "description" to description, "category" to category, "ticketLabel" to ticketLabel, "venue" to address, "startsAt" to date, "doorsAt" to doorsAt.takeIf { it > 0L }, "agenda" to agenda, "contact" to contact, "terms" to terms, "capacity" to (capacity.toIntOrNull() ?: 0), "posterBase64" to photo))
                "invoice" -> gateway("createInvoice", mapOf("pageId" to pageId, "invoiceId" to stableId, "customerName" to customerName, "note" to invoiceNote, "dueAt" to invoiceDueAt, "items" to invoiceItems.map { (productId, quantity) -> mapOf("productId" to productId, "quantity" to quantity) }))
                "check" -> { val result = gateway("checkTicket", mapOf("code" to name, "eventId" to checkEventId)); message = if (result["status"] == "accepted") "Entrée validée · ${result["title"]}" else "Billet déjà utilisé · entrée refusée"; return@launch }
            }
            saved()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = commerceError(e) } finally { busy = false } }
    }
    Dialog(onDismissRequest = ::requestClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CommerceSystemBars()
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(enabled = !busy, onClick = ::requestClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }; Text(when(kind) { "event" -> "Créer un événement"; "store" -> "Votre vitrine"; "invoice" -> "Nouvelle facture"; "check" -> "Contrôle d’entrée"; "detail" -> product.string("name"); else -> "Article du catalogue" }, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (kind in listOf("product", "event")) {
                        if (kind == "event") {
                            Text("Organisé par", color = WhappyMuted)
                            if (pages.isEmpty()) Text("Créez d’abord une page dans Business pour organiser un événement.", color = MaterialTheme.colorScheme.error)
                            pages.forEach { item -> FilterChip(enabled = !busy, selected = selectedPage == item.string("id"), onClick = { selectedPage = item.string("id") }, label = { Text(item.string("name")) }) }
                        }
                        key("editor-name") { OutlinedTextField(name, { name = it.take(100) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(if (kind == "event") "Nom de l’événement" else "Nom de l’article") }, singleLine = true) }
                        key("editor-description") { OutlinedTextField(description, { description = it.take(1000) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Description") }, minLines = 3) }
                    }
                    if (kind in listOf("product", "event")) {
                        key("editor-photo") {
                            photoPreview?.let { Image(it.asImageBitmap(), if (kind == "event") "Affiche de l’événement sélectionnée" else "Photo de l’article sélectionnée", Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop) }
                                ?: if (product.string(if (kind == "event") "posterUrl" else "imageUrl").isNotBlank()) UserAvatar(product.string(if (kind == "event") "posterUrl" else "imageUrl"), name, 140.dp, shape = RoundedCornerShape(16.dp)) else Unit
                        }
                        OutlinedButton(enabled = !busy, onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Rounded.Photo, null); Text(if (photo == null) if (kind == "event") " Ajouter l’affiche" else " Choisir la photo" else if (kind == "event") " Affiche prête · changer" else " Photo prête · changer") }
                        // Independent state keys prevent image-loading branches from restoring
                        // the price's horizontal text scroller into the multiline category field.
                        key("editor-category") { OutlinedTextField(category, { category = it.take(60) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(if (kind == "event") "Catégorie · concert, sport, formation…" else "Rubrique · plats, desserts, services…") }) }
                        if (kind == "product") {
                            key("editor-price") { OutlinedTextField(amount, { amount = it.take(14) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Prix · $currency") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) }
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { listOf("XAF", "XOF", "EUR", "USD", "CDF").forEachIndexed { index, unit -> SegmentedButton(enabled = !busy, selected = currency == unit, onClick = { currency = unit }, shape = SegmentedButtonDefaults.itemShape(index, 5), icon = {}) { Text(unit, fontSize = 11.sp) } } }
                        }
                    }
                    if (kind in listOf("store", "event")) key("editor-address") { OutlinedTextField(address, { address = it.take(160) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(if (kind == "event") "Lieu et adresse" else "Adresse de l’établissement") }) }
                    if (kind == "store") key("editor-hours") { OutlinedTextField(hours, { hours = it.take(200) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Horaires d’ouverture") }, placeholder = { Text("Lun–sam · 09:00–20:00") }) }
                    if (kind in listOf("product", "store")) Row(verticalAlignment = Alignment.CenterVertically) { Text(if (kind == "store") "Publier dans Marketplace" else "Disponible", Modifier.weight(1f)); Switch(available, { available = it }, enabled = !busy) }
                    if (kind == "event") {
                        OutlinedButton(enabled = !busy, onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = date }
                            DatePickerDialog(context, { _, y, m, d -> cal.set(y, m, d); TimePickerDialog(context, { _, h, min -> cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); date = cal.timeInMillis }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show() }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        }) { Text(dateLabel(date)) }
                        OutlinedButton(enabled = !busy, onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = if (doorsAt > 0L) doorsAt else date - 3_600_000L }
                            DatePickerDialog(context, { _, y, m, d -> cal.set(y, m, d); TimePickerDialog(context, { _, h, min -> cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); doorsAt = cal.timeInMillis.coerceAtMost(date) }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show() }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        }) { Icon(Icons.Rounded.DoorFront, null); Text(if (doorsAt > 0L) " Ouverture : ${dateLabel(doorsAt)}" else " Ajouter l’heure d’ouverture") }
                        key("editor-capacity") { OutlinedTextField(capacity, { capacity = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Nombre de places") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                        key("editor-ticket-label") { OutlinedTextField(ticketLabel, { ticketLabel = it.take(60) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Nom du billet · Accès général, VIP…") }, singleLine = true) }
                        key("editor-agenda") { OutlinedTextField(agenda, { agenda = it.take(1800) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Programme · facultatif") }, minLines = 3) }
                        key("editor-contact") { OutlinedTextField(contact, { contact = it.take(120) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Contact organisateur · facultatif") }, singleLine = true) }
                        key("editor-terms") { OutlinedTextField(terms, { terms = it.take(800) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Consignes d’accès · facultatif") }, minLines = 2) }
                        Surface(color = Color(0xFFEAF8F4), shape = RoundedCornerShape(15.dp)) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.QrCode2, null, tint = Color(0xFF087D62)); Text(" Un QR unique sera généré pour chaque réservation. Le paiement pourra être ajouté lorsque Mobile Money sera configuré.", color = Color(0xFF176451), fontSize = 12.sp) } }
                    }
                    if (kind == "invoice") {
                        key("invoice-customer") { OutlinedTextField(customerName, { customerName = it.take(100) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Client ou entreprise") }, singleLine = true) }
                        key("invoice-note") { OutlinedTextField(invoiceNote, { invoiceNote = it.take(800) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Note · facultative") }, minLines = 2) }
                        OutlinedButton(enabled = !busy, onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = invoiceDueAt }
                            DatePickerDialog(context, { _, y, m, d -> cal.set(y, m, d); invoiceDueAt = cal.timeInMillis }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        }) { Text("Échéance : ${dateLabel(invoiceDueAt)}") }
                        Text("Articles facturés", fontWeight = FontWeight.Bold)
                        invoiceProducts.filter { it["available"] == true }.forEach { item ->
                            val qty = invoiceItems[item.string("productId")] ?: 0
                            Row(Modifier.fillMaxWidth().background(Color(0xFFF5F7FA), RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(item.string("name"), fontWeight = FontWeight.SemiBold); Text(commercePrice(item.number("priceMinor"), item.string("currency")), color = WhappyMuted, fontSize = 12.sp) }
                                IconButton(enabled = !busy && qty > 0, onClick = { invoiceItems = invoiceItems.toMutableMap().apply { if (qty <= 1) remove(item.string("productId")) else put(item.string("productId"), qty - 1) } }) { Icon(Icons.Rounded.Remove, "Retirer") }
                                Text(qty.toString(), fontWeight = FontWeight.Bold)
                                IconButton(enabled = !busy && qty < 999, onClick = { invoiceItems = invoiceItems.toMutableMap().apply { put(item.string("productId"), qty + 1) } }) { Icon(Icons.Rounded.Add, "Ajouter") }
                            }
                        }
                        Text("La facture est une créance à suivre. Le paiement et l’envoi d’une relance restent sous votre contrôle.", color = WhappyMuted, fontSize = 12.sp)
                    }
                    if (kind == "check") {
                        Button(enabled = !busy, onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) { Text("Scanner le QR du billet") }
                        key("editor-ticket") { OutlinedTextField(name, { name = it.take(500) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Code Ticketbulk") }, minLines = 2) }
                        TextButton(enabled = !busy, onClick = { name = clipboard.getText()?.text.orEmpty().take(500) }) { Text("Coller un code") }
                        Text("La validation consomme une entrée. Un billet déjà validé ne peut pas entrer une seconde fois.", color = WhappyMuted)
                    }
                    if (kind == "detail") {
                        UserAvatar(product.string("imageUrl"), product.string("name"), 220.dp, shape = RoundedCornerShape(20.dp))
                        Text(product.string("description")); Text(commercePrice(product.number("priceMinor"), product.string("currency")), color = WhappyBlue, fontSize = 23.sp)
                    }
                    message?.let { Text(it, color = if (it.startsWith("Entrée validée")) Color(0xFF167A43) else MaterialTheme.colorScheme.error) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (kind != "detail") Button(enabled = !busy && (photoUri == null || photo != null) && (kind != "event" || selectedPage.isNotBlank()) && (kind != "invoice" || customerName.trim().length >= 2 && invoiceItems.isNotEmpty()), onClick = ::save, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp), shape = RoundedCornerShape(16.dp)) { Text(if (busy) "Enregistrement…" else if (kind == "check") "Valider l’entrée" else if (kind == "event") "Publier l’événement" else if (kind == "invoice") "Émettre la facture" else "Enregistrer") }
            }
        }
        if (scan) WapiQrScannerDialog(onDismiss = { scan = false }, onResult = { name = it; scan = false }, onPickImage = { scan = false; qrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
        if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("Quitter sans enregistrer ?") }, text = { Text("Vos modifications n’ont pas encore été enregistrées. Continuez pour les conserver.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; close() }) { Text("Abandonner", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Continuer à modifier") } })
    }
}

@Composable
private fun WapiTicketCard(ticket: CommerceRecord, gateway: WapiCommerceGateway, cancelled: () -> Unit, close: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var confirmCancel by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = ticket.string("status") == "issued"
    val qr = remember(ticket.string("code")) {
        val matrix = QRCodeWriter().encode(ticket.string("code"), BarcodeFormat.QR_CODE, 480, 480)
        Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888).apply { setPixels(IntArray(480 * 480) { if (matrix[it % 480, it / 480]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }, 0, 480, 0, 0, 480, 480) }
    }
    Dialog(onDismissRequest = { if (!busy) close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(22.dp), shape = RoundedCornerShape(28.dp), color = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = Color(0xFF082A45), shape = RoundedCornerShape(18.dp)) { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ConfirmationNumber, null, tint = Color(0xFF7CE5FF)); Column(Modifier.padding(start = 10.dp)) { Text("TICKETBULK", color = Color.White, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp); Text("Billet personnel · QR sécurisé", color = Color.White.copy(alpha = .7f), fontSize = 11.sp) }
                } }
                Text(ticket.string("title"), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(ticket.string("ticketLabel").ifBlank { "Accès général" }, color = WhappyBlue, fontWeight = FontWeight.Bold)
                Text(dateLabel(ticket.number("startsAt"))); Text(ticket.string("venue"))
                if (!valid) Text(if (ticket.string("status") == "used") "Billet déjà utilisé" else "Réservation annulée", color = WhappyMuted)
                else {
                    Surface(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFDDE7ED))) { Image(qr.asImageBitmap(), "QR personnel du billet", Modifier.padding(14.dp).size(220.dp)) }
                    Text("Ne partagez pas ce QR : il donne accès à votre place.", fontSize = 12.sp, color = WhappyMuted)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(ticket.string("code"))) }) { Text("Copier mon code") }
                    if (ticket.number("startsAt") > System.currentTimeMillis()) TextButton(enabled = !busy, onClick = { confirmCancel = true }) { Text("Annuler ma réservation", color = MaterialTheme.colorScheme.error) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(enabled = !busy, onClick = close) { Text("Fermer") }
            }
        }
        if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false }, title = { Text("Libérer votre place ?") }, text = { Text("Votre billet sera annulé. Vous pourrez réserver à nouveau s’il reste des places.") }, dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Garder le billet") } }, confirmButton = {
            TextButton(onClick = { confirmCancel = false; scope.launch {
                busy = true; error = null
                try { gateway("cancelTicket", mapOf("code" to ticket.string("code"))); cancelled() }
                catch (failure: Exception) { error = commerceError(failure) }
                finally { busy = false }
            } }) { Text("Annuler la réservation") }
        })
    }
}
